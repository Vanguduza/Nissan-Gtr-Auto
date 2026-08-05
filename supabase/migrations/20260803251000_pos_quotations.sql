-- Tablet POS quotations — create / send / convert (distinct from supplier RFQ quotes).
-- No ledger post on create/send. Convert loads lines into a live POS cart. No ZIMRA.

DO $$ BEGIN
  CREATE TYPE public.pos_quotation_status AS ENUM (
    'draft',
    'issued',
    'sent',
    'converted',
    'cancelled',
    'expired'
  );
EXCEPTION
  WHEN duplicate_object THEN NULL;
END $$;

CREATE TABLE IF NOT EXISTS public.pos_quotations (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  document_number TEXT UNIQUE,
  customer_id UUID REFERENCES public.customers (id),
  warehouse_id UUID NOT NULL REFERENCES public.warehouses (id),
  currency public.currency_code NOT NULL DEFAULT 'USD',
  exchange_rate_applied NUMERIC(18, 8) NOT NULL DEFAULT 1,
  status public.pos_quotation_status NOT NULL DEFAULT 'draft',
  valid_until DATE,
  notes TEXT,
  source_cart_id UUID REFERENCES public.pos_carts (id),
  converted_cart_id UUID REFERENCES public.pos_carts (id),
  converted_invoice_id UUID REFERENCES public.sales_invoices (id),
  sent_at TIMESTAMPTZ,
  sent_channel TEXT,
  sent_contact TEXT,
  created_by UUID REFERENCES auth.users (id),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS pos_quotations_status_idx
  ON public.pos_quotations (status, created_at DESC);
CREATE INDEX IF NOT EXISTS pos_quotations_customer_idx
  ON public.pos_quotations (customer_id);
CREATE INDEX IF NOT EXISTS pos_quotations_created_by_idx
  ON public.pos_quotations (created_by);

CREATE TABLE IF NOT EXISTS public.pos_quotation_lines (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  quotation_id UUID NOT NULL REFERENCES public.pos_quotations (id) ON DELETE CASCADE,
  stock_item_id UUID NOT NULL REFERENCES public.stock_items (id),
  parent_line_id UUID REFERENCES public.pos_quotation_lines (id) ON DELETE CASCADE,
  is_core_charge BOOLEAN NOT NULL DEFAULT false,
  uom_id UUID NOT NULL REFERENCES public.uoms (id),
  qty NUMERIC(18, 3) NOT NULL CHECK (qty > 0),
  qty_base NUMERIC(18, 3) NOT NULL CHECK (qty_base > 0),
  unit_price NUMERIC(18, 4) NOT NULL CHECK (unit_price >= 0),
  line_total NUMERIC(18, 2) NOT NULL CHECK (line_total >= 0),
  sort_order INT NOT NULL DEFAULT 0,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS pos_quotation_lines_quote_idx
  ON public.pos_quotation_lines (quotation_id);

ALTER TABLE public.pos_quotations ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.pos_quotation_lines ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS pos_quotations_staff ON public.pos_quotations;
CREATE POLICY pos_quotations_staff ON public.pos_quotations
  FOR ALL TO authenticated
  USING (public.has_staff_role(ARRAY['admin', 'sales', 'finance']::public.staff_role[]))
  WITH CHECK (public.has_staff_role(ARRAY['admin', 'sales', 'finance']::public.staff_role[]));

DROP POLICY IF EXISTS pos_quotation_lines_staff ON public.pos_quotation_lines;
CREATE POLICY pos_quotation_lines_staff ON public.pos_quotation_lines
  FOR ALL TO authenticated
  USING (
    EXISTS (
      SELECT 1 FROM public.pos_quotations q
      WHERE q.id = quotation_id
        AND public.has_staff_role(ARRAY['admin', 'sales', 'finance']::public.staff_role[])
    )
  )
  WITH CHECK (
    EXISTS (
      SELECT 1 FROM public.pos_quotations q
      WHERE q.id = quotation_id
        AND public.has_staff_role(ARRAY['admin', 'sales', 'finance']::public.staff_role[])
    )
  );

CREATE OR REPLACE FUNCTION public.create_pos_quotation_from_cart(
  p_cart_id UUID,
  p_valid_until DATE DEFAULT NULL,
  p_notes TEXT DEFAULT NULL
)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_cart public.pos_carts%ROWTYPE;
  v_quote_id UUID;
  v_doc TEXT;
  v_line_map JSONB := '{}'::jsonb;
  v_src public.pos_cart_lines%ROWTYPE;
  v_new_id UUID;
  v_parent UUID;
BEGIN
  PERFORM public._require_cart_mutate(p_cart_id);
  SELECT * INTO v_cart FROM public.pos_carts WHERE id = p_cart_id FOR UPDATE;
  IF NOT FOUND THEN
    RAISE EXCEPTION 'cart not found: %', p_cart_id;
  END IF;
  IF v_cart.status <> 'open' THEN
    RAISE EXCEPTION 'only open carts can become quotations (got %)', v_cart.status;
  END IF;
  IF NOT EXISTS (
    SELECT 1 FROM public.pos_cart_lines WHERE cart_id = p_cart_id AND is_core_charge = false
  ) THEN
    RAISE EXCEPTION 'quotation requires at least one non-core line';
  END IF;

  v_doc := public.next_series_value('QT-');

  INSERT INTO public.pos_quotations (
    document_number, customer_id, warehouse_id, currency, exchange_rate_applied,
    status, valid_until, notes, source_cart_id, created_by
  ) VALUES (
    v_doc, v_cart.customer_id, v_cart.warehouse_id, v_cart.currency, v_cart.exchange_rate_applied,
    'issued', p_valid_until, p_notes, p_cart_id, auth.uid()
  )
  RETURNING id INTO v_quote_id;

  -- Copy parent (non-core) lines first, then core children.
  FOR v_src IN
    SELECT * FROM public.pos_cart_lines
    WHERE cart_id = p_cart_id AND parent_line_id IS NULL
    ORDER BY created_at
  LOOP
    INSERT INTO public.pos_quotation_lines (
      quotation_id, stock_item_id, is_core_charge, uom_id,
      qty, qty_base, unit_price, line_total, sort_order
    ) VALUES (
      v_quote_id, v_src.stock_item_id, v_src.is_core_charge, v_src.uom_id,
      v_src.qty, v_src.qty_base, v_src.unit_price, v_src.line_total, 0
    )
    RETURNING id INTO v_new_id;
    v_line_map := v_line_map || jsonb_build_object(v_src.id::text, v_new_id::text);
  END LOOP;

  FOR v_src IN
    SELECT * FROM public.pos_cart_lines
    WHERE cart_id = p_cart_id AND parent_line_id IS NOT NULL
    ORDER BY created_at
  LOOP
    v_parent := NULLIF(v_line_map ->> v_src.parent_line_id::text, '')::UUID;
    INSERT INTO public.pos_quotation_lines (
      quotation_id, stock_item_id, parent_line_id, is_core_charge, uom_id,
      qty, qty_base, unit_price, line_total, sort_order
    ) VALUES (
      v_quote_id, v_src.stock_item_id, v_parent, v_src.is_core_charge, v_src.uom_id,
      v_src.qty, v_src.qty_base, v_src.unit_price, v_src.line_total, 1
    );
  END LOOP;

  PERFORM public._log_pos_action(
    'quotation_created',
    'pos_quotations',
    v_quote_id,
    jsonb_build_object('cart_id', p_cart_id),
    jsonb_build_object('document_number', v_doc, 'status', 'issued'),
    p_notes
  );

  RETURN v_quote_id;
END;
$$;

CREATE OR REPLACE FUNCTION public.send_pos_quotation(
  p_quotation_id UUID,
  p_channel TEXT,
  p_contact TEXT DEFAULT NULL
)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_q public.pos_quotations%ROWTYPE;
  v_channel TEXT;
BEGIN
  PERFORM public._require_sales_staff();
  v_channel := lower(trim(COALESCE(p_channel, '')));
  IF v_channel NOT IN ('print', 'email', 'sms', 'whatsapp') THEN
    RAISE EXCEPTION 'channel must be print|email|sms|whatsapp';
  END IF;

  SELECT * INTO v_q FROM public.pos_quotations WHERE id = p_quotation_id FOR UPDATE;
  IF NOT FOUND THEN
    RAISE EXCEPTION 'quotation not found: %', p_quotation_id;
  END IF;
  IF v_q.status NOT IN ('issued', 'sent') THEN
    RAISE EXCEPTION 'only issued/sent quotations can be sent (got %)', v_q.status;
  END IF;
  IF v_channel <> 'print'
     AND nullif(trim(COALESCE(p_contact, '')), '') IS NULL THEN
    RAISE EXCEPTION 'contact required for channel %', v_channel;
  END IF;

  UPDATE public.pos_quotations
  SET
    status = 'sent',
    sent_at = now(),
    sent_channel = v_channel,
    sent_contact = nullif(trim(COALESCE(p_contact, '')), ''),
    updated_at = now()
  WHERE id = p_quotation_id;

  PERFORM public._log_pos_action(
    'quotation_sent',
    'pos_quotations',
    p_quotation_id,
    jsonb_build_object('status', v_q.status),
    jsonb_build_object('status', 'sent', 'channel', v_channel, 'contact', p_contact),
    NULL
  );

  RETURN p_quotation_id;
END;
$$;

CREATE OR REPLACE FUNCTION public.convert_pos_quotation_to_cart(
  p_quotation_id UUID
)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_q public.pos_quotations%ROWTYPE;
  v_cart_id UUID;
  v_src public.pos_quotation_lines%ROWTYPE;
  v_line_map JSONB := '{}'::jsonb;
  v_new_id UUID;
  v_parent UUID;
BEGIN
  PERFORM public._require_sales_staff();
  SELECT * INTO v_q FROM public.pos_quotations WHERE id = p_quotation_id FOR UPDATE;
  IF NOT FOUND THEN
    RAISE EXCEPTION 'quotation not found: %', p_quotation_id;
  END IF;
  IF v_q.status NOT IN ('issued', 'sent') THEN
    RAISE EXCEPTION 'quotation cannot be converted (status=%)', v_q.status;
  END IF;
  IF v_q.valid_until IS NOT NULL AND v_q.valid_until < CURRENT_DATE THEN
    UPDATE public.pos_quotations
    SET status = 'expired', updated_at = now()
    WHERE id = p_quotation_id;
    RAISE EXCEPTION 'quotation expired on %', v_q.valid_until;
  END IF;
  IF v_q.converted_cart_id IS NOT NULL THEN
    RAISE EXCEPTION 'quotation already converted';
  END IF;

  INSERT INTO public.pos_carts (
    customer_id, warehouse_id, currency, exchange_rate_applied,
    status, created_by
  ) VALUES (
    v_q.customer_id, v_q.warehouse_id, v_q.currency, v_q.exchange_rate_applied,
    'open', auth.uid()
  )
  RETURNING id INTO v_cart_id;

  FOR v_src IN
    SELECT * FROM public.pos_quotation_lines
    WHERE quotation_id = p_quotation_id AND parent_line_id IS NULL
    ORDER BY sort_order, created_at
  LOOP
    INSERT INTO public.pos_cart_lines (
      cart_id, stock_item_id, is_core_charge, uom_id,
      qty, qty_base, unit_price, line_total
    ) VALUES (
      v_cart_id, v_src.stock_item_id, v_src.is_core_charge, v_src.uom_id,
      v_src.qty, v_src.qty_base, v_src.unit_price, v_src.line_total
    )
    RETURNING id INTO v_new_id;
    v_line_map := v_line_map || jsonb_build_object(v_src.id::text, v_new_id::text);
  END LOOP;

  FOR v_src IN
    SELECT * FROM public.pos_quotation_lines
    WHERE quotation_id = p_quotation_id AND parent_line_id IS NOT NULL
    ORDER BY sort_order, created_at
  LOOP
    v_parent := NULLIF(v_line_map ->> v_src.parent_line_id::text, '')::UUID;
    INSERT INTO public.pos_cart_lines (
      cart_id, stock_item_id, parent_line_id, is_core_charge, uom_id,
      qty, qty_base, unit_price, line_total
    ) VALUES (
      v_cart_id, v_src.stock_item_id, v_parent, v_src.is_core_charge, v_src.uom_id,
      v_src.qty, v_src.qty_base, v_src.unit_price, v_src.line_total
    );
  END LOOP;

  UPDATE public.pos_quotations
  SET
    status = 'converted',
    converted_cart_id = v_cart_id,
    updated_at = now()
  WHERE id = p_quotation_id;

  PERFORM public._log_pos_action(
    'quotation_converted',
    'pos_quotations',
    p_quotation_id,
    jsonb_build_object('status', v_q.status),
    jsonb_build_object('status', 'converted', 'cart_id', v_cart_id),
    NULL
  );

  RETURN v_cart_id;
END;
$$;

CREATE OR REPLACE FUNCTION public.list_pos_quotations(
  p_status public.pos_quotation_status DEFAULT NULL,
  p_limit INT DEFAULT 50
)
RETURNS TABLE (
  id UUID,
  document_number TEXT,
  customer_id UUID,
  warehouse_id UUID,
  currency public.currency_code,
  status public.pos_quotation_status,
  valid_until DATE,
  sent_channel TEXT,
  created_at TIMESTAMPTZ,
  line_count BIGINT,
  total NUMERIC
)
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
  PERFORM public._require_sales_staff();
  RETURN QUERY
  SELECT
    q.id,
    q.document_number,
    q.customer_id,
    q.warehouse_id,
    q.currency,
    q.status,
    q.valid_until,
    q.sent_channel,
    q.created_at,
    COUNT(l.id)::BIGINT AS line_count,
    COALESCE(SUM(l.line_total) FILTER (WHERE NOT l.is_core_charge), 0)::NUMERIC AS total
  FROM public.pos_quotations q
  LEFT JOIN public.pos_quotation_lines l ON l.quotation_id = q.id
  WHERE (p_status IS NULL OR q.status = p_status)
  GROUP BY q.id
  ORDER BY q.created_at DESC
  LIMIT GREATEST(1, LEAST(COALESCE(p_limit, 50), 200));
END;
$$;

REVOKE ALL ON FUNCTION public.create_pos_quotation_from_cart(UUID, DATE, TEXT) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.send_pos_quotation(UUID, TEXT, TEXT) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.convert_pos_quotation_to_cart(UUID) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.list_pos_quotations(public.pos_quotation_status, INT) FROM PUBLIC;

GRANT EXECUTE ON FUNCTION public.create_pos_quotation_from_cart(UUID, DATE, TEXT)
  TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.send_pos_quotation(UUID, TEXT, TEXT)
  TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.convert_pos_quotation_to_cart(UUID)
  TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.list_pos_quotations(public.pos_quotation_status, INT)
  TO authenticated, service_role;
