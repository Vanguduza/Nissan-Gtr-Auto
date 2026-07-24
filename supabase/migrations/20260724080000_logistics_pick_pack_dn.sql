-- Phase 10: pick/pack, delivery notes (DN-from-invoice), delivery jobs, GPS trail schema
-- Policy: DN-from-invoice; fulfillment_mode immediate|dispatch
-- Exclusions: no ZIMRA, no browser/HTML5 geolocation

-- ---------------------------------------------------------------------------
-- Enums + naming series
-- ---------------------------------------------------------------------------
CREATE TYPE public.fulfillment_mode AS ENUM ('immediate', 'dispatch');
CREATE TYPE public.pick_list_status AS ENUM ('draft', 'done', 'cancelled');
CREATE TYPE public.delivery_note_status AS ENUM ('draft', 'submitted', 'cancelled');
CREATE TYPE public.delivery_job_status AS ENUM (
  'pending', 'dispatched', 'completed', 'failed'
);

INSERT INTO public.naming_series (prefix, description, pad_length) VALUES
  ('PL-', 'Pick list', 5),
  ('DN-', 'Delivery note', 5),
  ('DJ-', 'Delivery job', 5)
ON CONFLICT (prefix) DO NOTHING;

-- ---------------------------------------------------------------------------
-- Cart / invoice fulfillment_mode
-- ---------------------------------------------------------------------------
ALTER TABLE public.pos_carts
  ADD COLUMN IF NOT EXISTS fulfillment_mode public.fulfillment_mode NOT NULL DEFAULT 'immediate';

ALTER TABLE public.sales_invoices
  ADD COLUMN IF NOT EXISTS fulfillment_mode public.fulfillment_mode NOT NULL DEFAULT 'immediate';

-- ---------------------------------------------------------------------------
-- Pick lists
-- ---------------------------------------------------------------------------
CREATE TABLE public.pick_lists (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  document_number TEXT,
  sales_invoice_id UUID NOT NULL REFERENCES public.sales_invoices (id) ON DELETE RESTRICT,
  warehouse_id UUID NOT NULL REFERENCES public.warehouses (id),
  status public.pick_list_status NOT NULL DEFAULT 'draft',
  created_by UUID REFERENCES auth.users (id),
  confirmed_at TIMESTAMPTZ,
  cancelled_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX pick_lists_invoice_idx ON public.pick_lists (sales_invoice_id);
CREATE INDEX pick_lists_status_idx ON public.pick_lists (status);

CREATE TABLE public.pick_list_lines (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  pick_list_id UUID NOT NULL REFERENCES public.pick_lists (id) ON DELETE CASCADE,
  sales_invoice_line_id UUID NOT NULL REFERENCES public.sales_invoice_lines (id) ON DELETE RESTRICT,
  stock_item_id UUID NOT NULL REFERENCES public.stock_items (id),
  uom_id UUID NOT NULL REFERENCES public.uoms (id),
  qty_requested NUMERIC(18, 3) NOT NULL CHECK (qty_requested > 0),
  qty_base_requested NUMERIC(18, 3) NOT NULL CHECK (qty_base_requested > 0),
  qty_picked NUMERIC(18, 3) NOT NULL DEFAULT 0 CHECK (qty_picked >= 0),
  qty_base_picked NUMERIC(18, 3) NOT NULL DEFAULT 0 CHECK (qty_base_picked >= 0),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (pick_list_id, sales_invoice_line_id)
);

CREATE INDEX pick_list_lines_pick_idx ON public.pick_list_lines (pick_list_id);
CREATE INDEX pick_list_lines_inv_line_idx ON public.pick_list_lines (sales_invoice_line_id);

-- ---------------------------------------------------------------------------
-- Delivery notes
-- ---------------------------------------------------------------------------
CREATE TABLE public.delivery_notes (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  document_number TEXT,
  sales_invoice_id UUID NOT NULL REFERENCES public.sales_invoices (id) ON DELETE RESTRICT,
  pick_list_id UUID REFERENCES public.pick_lists (id) ON DELETE SET NULL,
  warehouse_id UUID NOT NULL REFERENCES public.warehouses (id),
  currency public.currency_code NOT NULL DEFAULT 'USD',
  exchange_rate_applied NUMERIC(18, 8) NOT NULL DEFAULT 1,
  status public.delivery_note_status NOT NULL DEFAULT 'draft',
  stock_entry_id UUID REFERENCES public.stock_entries (id),
  cogs_journal_entry_id UUID REFERENCES public.journal_entries (id),
  reverse_journal_entry_id UUID REFERENCES public.journal_entries (id),
  created_by UUID REFERENCES auth.users (id),
  submitted_by UUID REFERENCES auth.users (id),
  submitted_at TIMESTAMPTZ,
  cancelled_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX delivery_notes_invoice_idx ON public.delivery_notes (sales_invoice_id);
CREATE INDEX delivery_notes_status_idx ON public.delivery_notes (status);

CREATE TABLE public.delivery_note_lines (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  delivery_note_id UUID NOT NULL REFERENCES public.delivery_notes (id) ON DELETE CASCADE,
  sales_invoice_line_id UUID NOT NULL REFERENCES public.sales_invoice_lines (id) ON DELETE RESTRICT,
  stock_item_id UUID NOT NULL REFERENCES public.stock_items (id),
  uom_id UUID NOT NULL REFERENCES public.uoms (id),
  qty NUMERIC(18, 3) NOT NULL CHECK (qty > 0),
  qty_base NUMERIC(18, 3) NOT NULL CHECK (qty_base > 0),
  unit_cost NUMERIC(18, 4) NOT NULL DEFAULT 0 CHECK (unit_cost >= 0),
  currency public.currency_code NOT NULL DEFAULT 'USD',
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (delivery_note_id, sales_invoice_line_id)
);

CREATE INDEX delivery_note_lines_dn_idx ON public.delivery_note_lines (delivery_note_id);
CREATE INDEX delivery_note_lines_inv_line_idx ON public.delivery_note_lines (sales_invoice_line_id);

-- ---------------------------------------------------------------------------
-- Delivery jobs + GPS trail (bridge ingest only)
-- ---------------------------------------------------------------------------
CREATE TABLE public.delivery_jobs (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  document_number TEXT,
  delivery_note_id UUID NOT NULL REFERENCES public.delivery_notes (id) ON DELETE RESTRICT,
  assignee_user_id UUID REFERENCES auth.users (id),
  status public.delivery_job_status NOT NULL DEFAULT 'pending',
  eta_at TIMESTAMPTZ,
  notes TEXT,
  created_by UUID REFERENCES auth.users (id),
  dispatched_at TIMESTAMPTZ,
  completed_at TIMESTAMPTZ,
  failed_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX delivery_jobs_dn_idx ON public.delivery_jobs (delivery_note_id);
CREATE INDEX delivery_jobs_assignee_idx ON public.delivery_jobs (assignee_user_id);
CREATE INDEX delivery_jobs_status_idx ON public.delivery_jobs (status);

CREATE TABLE public.delivery_locations (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  delivery_job_id UUID NOT NULL REFERENCES public.delivery_jobs (id) ON DELETE CASCADE,
  lat DOUBLE PRECISION NOT NULL CHECK (lat BETWEEN -90 AND 90),
  lng DOUBLE PRECISION NOT NULL CHECK (lng BETWEEN -180 AND 180),
  accuracy_m NUMERIC(12, 2),
  recorded_at TIMESTAMPTZ NOT NULL,
  ingested_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  source TEXT NOT NULL DEFAULT 'bridge' CHECK (source = 'bridge')
);

CREATE INDEX delivery_locations_job_recorded_idx
  ON public.delivery_locations (delivery_job_id, recorded_at DESC);
CREATE INDEX delivery_locations_ingested_idx
  ON public.delivery_locations (ingested_at);

-- Realtime for dispatcher maps (bridge-fed points only)
DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM pg_publication WHERE pubname = 'supabase_realtime') THEN
    BEGIN
      ALTER PUBLICATION supabase_realtime ADD TABLE public.delivery_locations;
    EXCEPTION
      WHEN duplicate_object THEN NULL;
    END;
  END IF;
END;
$$;

-- ---------------------------------------------------------------------------
-- Auth helpers
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public._require_logistics_staff()
RETURNS void
LANGUAGE plpgsql
STABLE
AS $$
BEGIN
  IF NOT (
    auth.role() = 'service_role'
    OR public.has_staff_role(
      ARRAY['admin', 'warehouse', 'dispatcher', 'sales']::public.staff_role[]
    )
  ) THEN
    RAISE EXCEPTION 'warehouse, dispatcher, sales, or admin role required';
  END IF;
END;
$$;

CREATE OR REPLACE FUNCTION public._require_dispatcher_staff()
RETURNS void
LANGUAGE plpgsql
STABLE
AS $$
BEGIN
  IF NOT (
    auth.role() = 'service_role'
    OR public.has_staff_role(
      ARRAY['admin', 'warehouse', 'dispatcher']::public.staff_role[]
    )
  ) THEN
    RAISE EXCEPTION 'dispatcher, warehouse, or admin role required';
  END IF;
END;
$$;

CREATE OR REPLACE FUNCTION public._logistics_rpc_active()
RETURNS boolean
LANGUAGE sql
STABLE
AS $$
  SELECT COALESCE(current_setting('app.logistics_rpc', true), '') = '1';
$$;

CREATE OR REPLACE FUNCTION public._logistics_begin_rpc()
RETURNS void
LANGUAGE sql
AS $$
  SELECT set_config('app.logistics_rpc', '1', true);
$$;

-- Open qty for an invoice line: ordered − fulfilled − draft DN reserved
CREATE OR REPLACE FUNCTION public._invoice_line_open_qty_base(p_invoice_line_id UUID)
RETURNS NUMERIC
LANGUAGE sql
STABLE
SET search_path = public
AS $$
  SELECT GREATEST(
    COALESCE(sil.qty_base, 0) - COALESCE(sil.qty_fulfilled, 0) - COALESCE((
      SELECT SUM(dnl.qty_base)
      FROM public.delivery_note_lines dnl
      JOIN public.delivery_notes dn ON dn.id = dnl.delivery_note_id
      WHERE dnl.sales_invoice_line_id = sil.id
        AND dn.status = 'draft'
    ), 0),
    0
  )
  FROM public.sales_invoice_lines sil
  WHERE sil.id = p_invoice_line_id;
$$;

-- ---------------------------------------------------------------------------
-- Cart create: optional fulfillment_mode
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.create_pos_cart(
  p_warehouse_id UUID,
  p_customer_id UUID DEFAULT NULL,
  p_currency public.currency_code DEFAULT 'USD',
  p_fulfillment_mode public.fulfillment_mode DEFAULT 'immediate'
)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_id UUID;
BEGIN
  PERFORM public._require_sales_staff();
  INSERT INTO public.pos_carts (
    document_number, customer_id, warehouse_id, currency, fulfillment_mode, created_by
  )
  VALUES (
    public.next_series_value('CART-'),
    p_customer_id,
    p_warehouse_id,
    p_currency,
    COALESCE(p_fulfillment_mode, 'immediate'),
    auth.uid()
  )
  RETURNING id INTO v_id;
  RETURN v_id;
END;
$$;

-- ---------------------------------------------------------------------------
-- Checkout: immediate issues stock; dispatch defers FIFO + COGS
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.checkout_pos_cart(p_cart_id UUID)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_cart public.pos_carts%ROWTYPE;
  v_cust public.customers%ROWTYPE;
  v_inv UUID;
  v_line RECORD;
  v_subtotal NUMERIC := 0;
  v_total NUMERIC := 0;
  v_journal UUID;
  v_lines JSONB := '[]'::jsonb;
  v_rev NUMERIC := 0;
  v_core NUMERIC := 0;
  v_cogs NUMERIC := 0;
  v_unit_cost NUMERIC;
  v_large NUMERIC := 1000;
  v_fulfill public.fulfillment_mode;
  v_qty_fulfilled NUMERIC;
BEGIN
  PERFORM public._require_sales_staff();
  SELECT * INTO v_cart FROM public.pos_carts WHERE id = p_cart_id FOR UPDATE;
  IF NOT FOUND OR v_cart.status <> 'open' THEN
    RAISE EXCEPTION 'open cart not found';
  END IF;

  v_fulfill := COALESCE(v_cart.fulfillment_mode, 'immediate');

  IF v_cart.customer_id IS NOT NULL THEN
    SELECT * INTO v_cust FROM public.customers WHERE id = v_cart.customer_id;
    IF v_cust.credit_hold THEN
      INSERT INTO public.sales_invoices (
        doc_type, status, customer_id, warehouse_id, currency,
        exchange_rate_applied, cart_id, fulfillment_mode,
        customer_phone_e164, customer_email, customer_whatsapp_e164
      )
      VALUES (
        'invoice', 'on_hold', v_cart.customer_id, v_cart.warehouse_id, v_cart.currency,
        v_cart.exchange_rate_applied, p_cart_id, v_fulfill,
        v_cust.phone_e164, v_cust.email, v_cust.whatsapp_e164
      )
      RETURNING id INTO v_inv;

      PERFORM public.emit_domain_event(
        'order_on_hold',
        'invoice:hold:' || v_inv::text,
        jsonb_build_object('invoice_id', v_inv, 'reason', 'credit_hold')
      );
      RETURN v_inv;
    END IF;
  END IF;

  SELECT COALESCE(SUM(line_total), 0) INTO v_subtotal
  FROM public.pos_cart_lines WHERE cart_id = p_cart_id;
  v_total := v_subtotal;

  IF v_cart.customer_id IS NOT NULL
     AND v_cust.credit_limit > 0
     AND (v_cust.open_balance + v_total) > v_cust.credit_limit THEN
    INSERT INTO public.sales_invoices (
      doc_type, status, customer_id, warehouse_id, currency,
      exchange_rate_applied, cart_id, subtotal, total, fulfillment_mode,
      customer_phone_e164, customer_email, customer_whatsapp_e164
    )
    VALUES (
      'invoice', 'on_hold', v_cart.customer_id, v_cart.warehouse_id, v_cart.currency,
      v_cart.exchange_rate_applied, p_cart_id, v_subtotal, v_total, v_fulfill,
      v_cust.phone_e164, v_cust.email, v_cust.whatsapp_e164
    )
    RETURNING id INTO v_inv;

    PERFORM public.emit_domain_event(
      'order_on_hold',
      'invoice:limit:' || v_inv::text,
      jsonb_build_object('invoice_id', v_inv, 'reason', 'credit_limit')
    );
    RETURN v_inv;
  END IF;

  INSERT INTO public.sales_invoices (
    doc_type, status, document_number, customer_id, warehouse_id, currency,
    exchange_rate_applied, subtotal, total, cart_id, fulfillment_mode,
    customer_phone_e164, customer_email, customer_whatsapp_e164,
    posted_by, posted_at
  )
  VALUES (
    'invoice', 'draft', public.next_series_value('SINV-'),
    v_cart.customer_id, v_cart.warehouse_id, v_cart.currency,
    v_cart.exchange_rate_applied, v_subtotal, v_total, p_cart_id, v_fulfill,
    v_cust.phone_e164, v_cust.email, v_cust.whatsapp_e164,
    auth.uid(), now()
  )
  RETURNING id INTO v_inv;

  PERFORM public.emit_domain_event(
    'order_received',
    'invoice:received:' || v_inv::text,
    jsonb_build_object(
      'invoice_id', v_inv,
      'fulfillment_mode', v_fulfill::text
    )
  );

  FOR v_line IN
    SELECT * FROM public.pos_cart_lines WHERE cart_id = p_cart_id ORDER BY is_core_charge, created_at
  LOOP
    -- immediate: fulfill at checkout; dispatch: defer physical lines
    IF v_line.is_core_charge THEN
      v_qty_fulfilled := 0;
    ELSIF v_fulfill = 'immediate' THEN
      v_qty_fulfilled := v_line.qty_base;
    ELSE
      v_qty_fulfilled := 0;
    END IF;

    INSERT INTO public.sales_invoice_lines (
      invoice_id, stock_item_id, parent_line_id, is_core_charge, uom_id,
      qty, qty_base, unit_price, line_total, qty_fulfilled
    )
    VALUES (
      v_inv, v_line.stock_item_id, NULL, v_line.is_core_charge, v_line.uom_id,
      v_line.qty, v_line.qty_base, v_line.unit_price, v_line.line_total, v_qty_fulfilled
    );

    IF v_line.is_core_charge THEN
      v_core := v_core + v_line.line_total;
    ELSE
      v_rev := v_rev + v_line.line_total;

      IF v_fulfill = 'immediate' THEN
        SELECT unit_cost INTO v_unit_cost
        FROM public.stock_levels
        WHERE stock_item_id = v_line.stock_item_id AND warehouse_id = v_cart.warehouse_id;

        PERFORM public._consume_fifo_batches(
          v_line.stock_item_id, v_cart.warehouse_id, v_line.qty_base
        );
        PERFORM public._adjust_stock_level(
          v_line.stock_item_id, v_cart.warehouse_id, -v_line.qty_base,
          'FIFO', COALESCE(v_unit_cost, 0), v_cart.currency
        );
        v_cogs := v_cogs + COALESCE(v_unit_cost, 0) * v_line.qty_base;
      END IF;
    END IF;
  END LOOP;

  -- Journals: DR Cash/AR, CR Revenue; optional core; COGS only when immediate
  v_lines := jsonb_build_array(
    jsonb_build_object(
      'account_code', CASE WHEN v_cart.customer_id IS NULL THEN '1100' ELSE '1200' END,
      'debit', v_total, 'credit', 0, 'currency', v_cart.currency
    ),
    jsonb_build_object(
      'account_code', '4100',
      'debit', 0, 'credit', v_rev, 'currency', v_cart.currency
    )
  );
  IF v_core > 0 THEN
    v_lines := v_lines || jsonb_build_array(
      jsonb_build_object(
        'account_code', '4200',
        'debit', 0, 'credit', v_core, 'currency', v_cart.currency
      )
    );
  END IF;
  IF v_cogs > 0 THEN
    v_lines := v_lines || jsonb_build_array(
      jsonb_build_object(
        'account_code', '5100',
        'debit', round(v_cogs, 2), 'credit', 0, 'currency', v_cart.currency
      ),
      jsonb_build_object(
        'account_code', '1300',
        'debit', 0, 'credit', round(v_cogs, 2), 'currency', v_cart.currency
      )
    );
  END IF;

  v_journal := public.post_journal_entry(
    CURRENT_DATE,
    format('Sale %s', (SELECT document_number FROM public.sales_invoices WHERE id = v_inv)),
    v_cart.currency,
    v_cart.exchange_rate_applied,
    v_lines
  );

  UPDATE public.sales_invoices
  SET status = 'posted', journal_entry_id = v_journal, posted_at = now()
  WHERE id = v_inv;

  UPDATE public.pos_carts SET status = 'checked_out', updated_at = now() WHERE id = p_cart_id;

  IF v_cart.customer_id IS NOT NULL THEN
    UPDATE public.customers
    SET open_balance = open_balance + v_total, updated_at = now()
    WHERE id = v_cart.customer_id;
  END IF;

  PERFORM public.enqueue_customer_receipts(v_inv);

  IF v_total >= v_large THEN
    PERFORM public.emit_domain_event(
      'large_order',
      'invoice:large:' || v_inv::text,
      jsonb_build_object('invoice_id', v_inv, 'total', v_total)
    );
  END IF;

  RETURN v_inv;
END;
$$;

-- ---------------------------------------------------------------------------
-- Pick list RPCs
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.create_pick_list(
  p_sales_invoice_id UUID,
  p_lines JSONB DEFAULT NULL
)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_inv public.sales_invoices%ROWTYPE;
  v_pick UUID;
  v_line RECORD;
  v_elem JSONB;
  v_inv_line UUID;
  v_qty NUMERIC;
  v_qty_base NUMERIC;
  v_open NUMERIC;
  v_any BOOLEAN := false;
BEGIN
  PERFORM public._logistics_begin_rpc();
  PERFORM public._require_logistics_staff();

  SELECT * INTO v_inv
  FROM public.sales_invoices
  WHERE id = p_sales_invoice_id AND doc_type = 'invoice' AND status = 'posted'
  FOR UPDATE;
  IF NOT FOUND THEN
    RAISE EXCEPTION 'posted sales invoice required';
  END IF;
  IF v_inv.fulfillment_mode <> 'dispatch' THEN
    RAISE EXCEPTION 'pick lists only for dispatch fulfillment invoices';
  END IF;

  INSERT INTO public.pick_lists (
    document_number, sales_invoice_id, warehouse_id, status, created_by
  )
  VALUES (
    public.next_series_value('PL-'),
    p_sales_invoice_id,
    v_inv.warehouse_id,
    'draft',
    auth.uid()
  )
  RETURNING id INTO v_pick;

  IF p_lines IS NULL OR jsonb_typeof(p_lines) <> 'array' OR jsonb_array_length(p_lines) = 0 THEN
    FOR v_line IN
      SELECT *
      FROM public.sales_invoice_lines
      WHERE invoice_id = p_sales_invoice_id
        AND NOT is_core_charge
        AND qty_base > qty_fulfilled
    LOOP
      v_open := public._invoice_line_open_qty_base(v_line.id);
      IF v_open <= 0 THEN
        CONTINUE;
      END IF;
      v_any := true;
      INSERT INTO public.pick_list_lines (
        pick_list_id, sales_invoice_line_id, stock_item_id, uom_id,
        qty_requested, qty_base_requested
      )
      VALUES (
        v_pick, v_line.id, v_line.stock_item_id, v_line.uom_id,
        v_open, v_open
      );
    END LOOP;
  ELSE
    FOR v_elem IN SELECT * FROM jsonb_array_elements(p_lines)
    LOOP
      v_inv_line := (v_elem ->> 'sales_invoice_line_id')::uuid;
      v_qty := (v_elem ->> 'qty')::numeric;
      IF v_inv_line IS NULL OR v_qty IS NULL OR v_qty <= 0 THEN
        RAISE EXCEPTION 'pick line requires sales_invoice_line_id and qty > 0';
      END IF;

      SELECT * INTO v_line
      FROM public.sales_invoice_lines
      WHERE id = v_inv_line AND invoice_id = p_sales_invoice_id;
      IF NOT FOUND THEN
        RAISE EXCEPTION 'invoice line % not on invoice', v_inv_line;
      END IF;
      IF v_line.is_core_charge THEN
        RAISE EXCEPTION 'core-charge lines cannot be picked';
      END IF;

      v_qty_base := public.convert_to_base_uom(v_line.stock_item_id, v_line.uom_id, v_qty);
      v_open := public._invoice_line_open_qty_base(v_inv_line);
      IF v_qty_base > v_open THEN
        RAISE EXCEPTION 'cannot pick more than open qty (open=%, requested=%)', v_open, v_qty_base;
      END IF;

      v_any := true;
      INSERT INTO public.pick_list_lines (
        pick_list_id, sales_invoice_line_id, stock_item_id, uom_id,
        qty_requested, qty_base_requested
      )
      VALUES (
        v_pick, v_inv_line, v_line.stock_item_id, v_line.uom_id,
        v_qty, v_qty_base
      );
    END LOOP;
  END IF;

  IF NOT v_any THEN
    RAISE EXCEPTION 'no open qty to pick on invoice %', p_sales_invoice_id;
  END IF;

  RETURN v_pick;
END;
$$;

CREATE OR REPLACE FUNCTION public.confirm_pick_lines(
  p_pick_list_id UUID,
  p_lines JSONB
)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_pick public.pick_lists%ROWTYPE;
  v_elem JSONB;
  v_line_id UUID;
  v_inv_line UUID;
  v_qty NUMERIC;
  v_qty_base NUMERIC;
  v_pl public.pick_list_lines%ROWTYPE;
  v_open NUMERIC;
  v_other NUMERIC;
BEGIN
  PERFORM public._logistics_begin_rpc();
  PERFORM public._require_logistics_staff();

  SELECT * INTO v_pick FROM public.pick_lists WHERE id = p_pick_list_id FOR UPDATE;
  IF NOT FOUND THEN
    RAISE EXCEPTION 'pick list not found';
  END IF;
  IF v_pick.status <> 'draft' THEN
    RAISE EXCEPTION 'only draft pick lists can be confirmed (status=%)', v_pick.status;
  END IF;

  IF p_lines IS NULL OR jsonb_typeof(p_lines) <> 'array' OR jsonb_array_length(p_lines) = 0 THEN
    RAISE EXCEPTION 'confirm_pick_lines requires lines array';
  END IF;

  FOR v_elem IN SELECT * FROM jsonb_array_elements(p_lines)
  LOOP
    v_line_id := NULLIF(v_elem ->> 'pick_list_line_id', '')::uuid;
    v_inv_line := NULLIF(v_elem ->> 'sales_invoice_line_id', '')::uuid;
    v_qty := (v_elem ->> 'qty_picked')::numeric;
    IF v_qty IS NULL OR v_qty < 0 THEN
      RAISE EXCEPTION 'qty_picked required (>= 0)';
    END IF;

    IF v_line_id IS NOT NULL THEN
      SELECT * INTO v_pl FROM public.pick_list_lines WHERE id = v_line_id AND pick_list_id = p_pick_list_id;
    ELSIF v_inv_line IS NOT NULL THEN
      SELECT * INTO v_pl
      FROM public.pick_list_lines
      WHERE pick_list_id = p_pick_list_id AND sales_invoice_line_id = v_inv_line;
    ELSE
      RAISE EXCEPTION 'pick_list_line_id or sales_invoice_line_id required';
    END IF;

    IF NOT FOUND THEN
      RAISE EXCEPTION 'pick list line not found';
    END IF;

    v_qty_base := public.convert_to_base_uom(v_pl.stock_item_id, v_pl.uom_id, v_qty);

    -- Open vs invoice fulfilled (ignore this pick's prior qty_picked)
    SELECT GREATEST(
      sil.qty_base - sil.qty_fulfilled - COALESCE((
        SELECT SUM(pll.qty_base_picked)
        FROM public.pick_list_lines pll
        JOIN public.pick_lists pl ON pl.id = pll.pick_list_id
        WHERE pll.sales_invoice_line_id = v_pl.sales_invoice_line_id
          AND pl.status IN ('draft', 'done')
          AND pll.id <> v_pl.id
      ), 0),
      0
    )
    INTO v_open
    FROM public.sales_invoice_lines sil
    WHERE sil.id = v_pl.sales_invoice_line_id;

    IF v_qty_base > v_open THEN
      RAISE EXCEPTION 'over-pick denied: open=% requested=% for line %',
        v_open, v_qty_base, v_pl.sales_invoice_line_id;
    END IF;

    IF v_qty_base > v_pl.qty_base_requested THEN
      RAISE EXCEPTION 'qty_picked exceeds qty_requested on pick line';
    END IF;

    UPDATE public.pick_list_lines
    SET qty_picked = v_qty, qty_base_picked = v_qty_base
    WHERE id = v_pl.id;
  END LOOP;

  UPDATE public.pick_lists
  SET status = 'done', confirmed_at = now(), updated_at = now()
  WHERE id = p_pick_list_id;

  RETURN p_pick_list_id;
END;
$$;

-- ---------------------------------------------------------------------------
-- Delivery note RPCs
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.create_delivery_note(
  p_sales_invoice_id UUID,
  p_lines JSONB,
  p_pick_list_id UUID DEFAULT NULL
)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_inv public.sales_invoices%ROWTYPE;
  v_dn UUID;
  v_elem JSONB;
  v_inv_line UUID;
  v_qty NUMERIC;
  v_qty_base NUMERIC;
  v_line public.sales_invoice_lines%ROWTYPE;
  v_open NUMERIC;
  v_pick public.pick_lists%ROWTYPE;
  v_any BOOLEAN := false;
BEGIN
  PERFORM public._logistics_begin_rpc();
  PERFORM public._require_logistics_staff();

  SELECT * INTO v_inv
  FROM public.sales_invoices
  WHERE id = p_sales_invoice_id AND doc_type = 'invoice' AND status = 'posted'
  FOR UPDATE;
  IF NOT FOUND THEN
    RAISE EXCEPTION 'posted sales invoice required';
  END IF;
  IF v_inv.fulfillment_mode <> 'dispatch' THEN
    RAISE EXCEPTION 'delivery notes blocked for immediate fulfillment invoices (no double-issue)';
  END IF;

  IF p_pick_list_id IS NOT NULL THEN
    SELECT * INTO v_pick FROM public.pick_lists WHERE id = p_pick_list_id;
    IF NOT FOUND OR v_pick.sales_invoice_id <> p_sales_invoice_id THEN
      RAISE EXCEPTION 'pick list must belong to invoice';
    END IF;
    IF v_pick.status = 'cancelled' THEN
      RAISE EXCEPTION 'cancelled pick list cannot create DN';
    END IF;
  END IF;

  IF p_lines IS NULL OR jsonb_typeof(p_lines) <> 'array' OR jsonb_array_length(p_lines) = 0 THEN
    RAISE EXCEPTION 'create_delivery_note requires lines array';
  END IF;

  INSERT INTO public.delivery_notes (
    document_number, sales_invoice_id, pick_list_id, warehouse_id,
    currency, exchange_rate_applied, status, created_by
  )
  VALUES (
    public.next_series_value('DN-'),
    p_sales_invoice_id,
    p_pick_list_id,
    v_inv.warehouse_id,
    v_inv.currency,
    v_inv.exchange_rate_applied,
    'draft',
    auth.uid()
  )
  RETURNING id INTO v_dn;

  FOR v_elem IN SELECT * FROM jsonb_array_elements(p_lines)
  LOOP
    v_inv_line := (v_elem ->> 'sales_invoice_line_id')::uuid;
    v_qty := (v_elem ->> 'qty')::numeric;
    IF v_inv_line IS NULL OR v_qty IS NULL OR v_qty <= 0 THEN
      RAISE EXCEPTION 'DN line requires sales_invoice_line_id and qty > 0';
    END IF;

    SELECT * INTO v_line
    FROM public.sales_invoice_lines
    WHERE id = v_inv_line AND invoice_id = p_sales_invoice_id;
    IF NOT FOUND THEN
      RAISE EXCEPTION 'invoice line % not on invoice', v_inv_line;
    END IF;
    IF v_line.is_core_charge THEN
      RAISE EXCEPTION 'core-charge lines cannot ship on DN';
    END IF;

    v_qty_base := public.convert_to_base_uom(v_line.stock_item_id, v_line.uom_id, v_qty);
    v_open := public._invoice_line_open_qty_base(v_inv_line);
    IF v_qty_base > v_open THEN
      RAISE EXCEPTION 'DN qty exceeds open qty (open=%, requested=%)', v_open, v_qty_base;
    END IF;

    v_any := true;
    INSERT INTO public.delivery_note_lines (
      delivery_note_id, sales_invoice_line_id, stock_item_id, uom_id,
      qty, qty_base, currency
    )
    VALUES (
      v_dn, v_inv_line, v_line.stock_item_id, v_line.uom_id,
      v_qty, v_qty_base, v_inv.currency
    );
  END LOOP;

  IF NOT v_any THEN
    RAISE EXCEPTION 'DN requires at least one line';
  END IF;

  RETURN v_dn;
END;
$$;

CREATE OR REPLACE FUNCTION public.submit_delivery_note(p_delivery_note_id UUID)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_dn public.delivery_notes%ROWTYPE;
  v_inv public.sales_invoices%ROWTYPE;
  v_line RECORD;
  v_open NUMERIC;
  v_unit_cost NUMERIC;
  v_currency public.currency_code;
  v_cogs NUMERIC := 0;
  v_entry UUID;
  v_journal UUID;
BEGIN
  PERFORM public._logistics_begin_rpc();
  PERFORM public._require_logistics_staff();

  SELECT * INTO v_dn FROM public.delivery_notes WHERE id = p_delivery_note_id FOR UPDATE;
  IF NOT FOUND THEN
    RAISE EXCEPTION 'delivery note not found';
  END IF;
  IF v_dn.status <> 'draft' THEN
    RAISE EXCEPTION 'only draft DNs can be submitted (status=%)', v_dn.status;
  END IF;

  SELECT * INTO v_inv
  FROM public.sales_invoices
  WHERE id = v_dn.sales_invoice_id
  FOR UPDATE;
  IF v_inv.fulfillment_mode <> 'dispatch' THEN
    RAISE EXCEPTION 'submit blocked: immediate invoices must not re-issue stock via DN';
  END IF;
  IF v_inv.status <> 'posted' OR v_inv.doc_type <> 'invoice' THEN
    RAISE EXCEPTION 'posted invoice required';
  END IF;

  FOR v_line IN
    SELECT * FROM public.delivery_note_lines WHERE delivery_note_id = p_delivery_note_id
  LOOP
    -- Open excluding this draft DN's own reservation
    SELECT GREATEST(
      sil.qty_base - sil.qty_fulfilled - COALESCE((
        SELECT SUM(dnl.qty_base)
        FROM public.delivery_note_lines dnl
        JOIN public.delivery_notes dn ON dn.id = dnl.delivery_note_id
        WHERE dnl.sales_invoice_line_id = sil.id
          AND dn.status = 'draft'
          AND dn.id <> p_delivery_note_id
      ), 0),
      0
    )
    INTO v_open
    FROM public.sales_invoice_lines sil
    WHERE sil.id = v_line.sales_invoice_line_id;

    IF v_line.qty_base > v_open THEN
      RAISE EXCEPTION 'DN submit over-ship denied: open=% ship=%', v_open, v_line.qty_base;
    END IF;

    SELECT unit_cost, currency INTO v_unit_cost, v_currency
    FROM public.stock_levels
    WHERE stock_item_id = v_line.stock_item_id AND warehouse_id = v_dn.warehouse_id;

    UPDATE public.delivery_note_lines
    SET unit_cost = COALESCE(v_unit_cost, 0),
        currency = COALESCE(v_currency, v_dn.currency)
    WHERE id = v_line.id;

    PERFORM public._consume_fifo_batches(
      v_line.stock_item_id, v_dn.warehouse_id, v_line.qty_base
    );
    PERFORM public._adjust_stock_level(
      v_line.stock_item_id, v_dn.warehouse_id, -v_line.qty_base,
      'FIFO', COALESCE(v_unit_cost, 0), COALESCE(v_currency, v_dn.currency)
    );

    v_cogs := v_cogs + COALESCE(v_unit_cost, 0) * v_line.qty_base;

    UPDATE public.sales_invoice_lines
    SET qty_fulfilled = qty_fulfilled + v_line.qty_base
    WHERE id = v_line.sales_invoice_line_id;
  END LOOP;

  -- Stock entry header for audit trail (levels already adjusted above)
  INSERT INTO public.stock_entries (
    entry_type, status, from_warehouse_id, notes, created_by,
    posted_at, document_number
  )
  VALUES (
    'issue', 'posted', v_dn.warehouse_id,
    format('DN %s', COALESCE(v_dn.document_number, p_delivery_note_id::text)),
    auth.uid(), now(), public.next_series_value('ISS-')
  )
  RETURNING id INTO v_entry;

  FOR v_line IN
    SELECT * FROM public.delivery_note_lines WHERE delivery_note_id = p_delivery_note_id
  LOOP
    INSERT INTO public.stock_entry_lines (
      stock_entry_id, stock_item_id, uom_id, qty, qty_base,
      unit_cost, currency, valuation_method
    )
    VALUES (
      v_entry, v_line.stock_item_id, v_line.uom_id, v_line.qty, v_line.qty_base,
      v_line.unit_cost, v_line.currency, 'FIFO'
    );
  END LOOP;

  IF v_cogs > 0 THEN
    v_journal := public._post_journal_entry_inventory(
      CURRENT_DATE,
      format('COGS DN %s', COALESCE(v_dn.document_number, p_delivery_note_id::text)),
      v_dn.currency,
      v_dn.exchange_rate_applied,
      jsonb_build_array(
        jsonb_build_object(
          'account_code', '5100',
          'debit', round(v_cogs, 2), 'credit', 0, 'currency', v_dn.currency
        ),
        jsonb_build_object(
          'account_code', '1300',
          'debit', 0, 'credit', round(v_cogs, 2), 'currency', v_dn.currency
        )
      )
    );
  END IF;

  UPDATE public.delivery_notes
  SET
    status = 'submitted',
    stock_entry_id = v_entry,
    cogs_journal_entry_id = v_journal,
    submitted_by = auth.uid(),
    submitted_at = now(),
    updated_at = now()
  WHERE id = p_delivery_note_id;

  RETURN p_delivery_note_id;
END;
$$;

CREATE OR REPLACE FUNCTION public.cancel_delivery_note(p_delivery_note_id UUID)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_dn public.delivery_notes%ROWTYPE;
  v_line RECORD;
  v_batch_code TEXT;
  v_rev UUID;
BEGIN
  PERFORM public._logistics_begin_rpc();
  PERFORM public._require_logistics_staff();

  SELECT * INTO v_dn FROM public.delivery_notes WHERE id = p_delivery_note_id FOR UPDATE;
  IF NOT FOUND THEN
    RAISE EXCEPTION 'delivery note not found';
  END IF;

  IF v_dn.status = 'draft' THEN
    UPDATE public.delivery_notes
    SET status = 'cancelled', cancelled_at = now(), updated_at = now()
    WHERE id = p_delivery_note_id;
    RETURN p_delivery_note_id;
  END IF;

  IF v_dn.status <> 'submitted' THEN
    RAISE EXCEPTION 'only draft/submitted DNs can be cancelled (status=%)', v_dn.status;
  END IF;

  IF public.is_period_locked(CURRENT_DATE) THEN
    RAISE EXCEPTION 'accounting period is locked for today';
  END IF;

  FOR v_line IN
    SELECT * FROM public.delivery_note_lines WHERE delivery_note_id = p_delivery_note_id
  LOOP
    v_batch_code := public.next_series_value('BATCH-');
    INSERT INTO public.stock_batches (
      batch_code, stock_item_id, warehouse_id, valuation_method,
      unit_cost, currency, qty_on_hand
    )
    VALUES (
      v_batch_code,
      v_line.stock_item_id,
      v_dn.warehouse_id,
      'FIFO',
      v_line.unit_cost,
      v_line.currency,
      v_line.qty_base
    );

    PERFORM public._adjust_stock_level(
      v_line.stock_item_id,
      v_dn.warehouse_id,
      v_line.qty_base,
      'FIFO',
      v_line.unit_cost,
      v_line.currency
    );

    UPDATE public.sales_invoice_lines
    SET qty_fulfilled = GREATEST(qty_fulfilled - v_line.qty_base, 0)
    WHERE id = v_line.sales_invoice_line_id;
  END LOOP;

  IF v_dn.cogs_journal_entry_id IS NOT NULL THEN
    v_rev := public._reverse_journal_inventory(
      v_dn.cogs_journal_entry_id,
      format('Cancel DN %s', COALESCE(v_dn.document_number, p_delivery_note_id::text))
    );
  END IF;

  UPDATE public.delivery_notes
  SET
    status = 'cancelled',
    reverse_journal_entry_id = v_rev,
    cancelled_at = now(),
    updated_at = now()
  WHERE id = p_delivery_note_id;

  RETURN p_delivery_note_id;
END;
$$;

-- ---------------------------------------------------------------------------
-- Delivery jobs + GPS ingest
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.create_delivery_job(
  p_delivery_note_id UUID,
  p_assignee_user_id UUID DEFAULT NULL,
  p_eta_at TIMESTAMPTZ DEFAULT NULL,
  p_notes TEXT DEFAULT NULL
)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_dn public.delivery_notes%ROWTYPE;
  v_job UUID;
BEGIN
  PERFORM public._logistics_begin_rpc();
  PERFORM public._require_dispatcher_staff();

  SELECT * INTO v_dn FROM public.delivery_notes WHERE id = p_delivery_note_id;
  IF NOT FOUND THEN
    RAISE EXCEPTION 'delivery note not found';
  END IF;
  IF v_dn.status <> 'submitted' THEN
    RAISE EXCEPTION 'delivery job requires submitted DN (status=%)', v_dn.status;
  END IF;

  INSERT INTO public.delivery_jobs (
    document_number, delivery_note_id, assignee_user_id, status,
    eta_at, notes, created_by
  )
  VALUES (
    public.next_series_value('DJ-'),
    p_delivery_note_id,
    p_assignee_user_id,
    'pending',
    p_eta_at,
    p_notes,
    auth.uid()
  )
  RETURNING id INTO v_job;

  RETURN v_job;
END;
$$;

CREATE OR REPLACE FUNCTION public.update_delivery_job_status(
  p_delivery_job_id UUID,
  p_status public.delivery_job_status
)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_job public.delivery_jobs%ROWTYPE;
  v_evt TEXT;
BEGIN
  PERFORM public._logistics_begin_rpc();
  PERFORM public._require_dispatcher_staff();

  SELECT * INTO v_job FROM public.delivery_jobs WHERE id = p_delivery_job_id FOR UPDATE;
  IF NOT FOUND THEN
    RAISE EXCEPTION 'delivery job not found';
  END IF;
  IF v_job.status IN ('completed', 'failed') THEN
    RAISE EXCEPTION 'terminal delivery job cannot change status (status=%)', v_job.status;
  END IF;
  IF p_status = 'pending' THEN
    RAISE EXCEPTION 'cannot revert job to pending';
  END IF;

  UPDATE public.delivery_jobs
  SET
    status = p_status,
    dispatched_at = CASE
      WHEN p_status = 'dispatched' THEN COALESCE(dispatched_at, now())
      ELSE dispatched_at
    END,
    completed_at = CASE WHEN p_status = 'completed' THEN now() ELSE completed_at END,
    failed_at = CASE WHEN p_status = 'failed' THEN now() ELSE failed_at END,
    updated_at = now()
  WHERE id = p_delivery_job_id;

  v_evt := CASE p_status
    WHEN 'dispatched' THEN 'delivery_dispatched'
    WHEN 'completed' THEN 'delivery_completed'
    WHEN 'failed' THEN 'delivery_failed'
    ELSE NULL
  END;

  IF v_evt IS NOT NULL THEN
    PERFORM public.emit_domain_event(
      v_evt,
      'delivery_job:' || p_status::text || ':' || p_delivery_job_id::text,
      jsonb_build_object(
        'delivery_job_id', p_delivery_job_id,
        'delivery_note_id', v_job.delivery_note_id,
        'status', p_status::text
      )
    );
  END IF;

  RETURN p_delivery_job_id;
END;
$$;

CREATE OR REPLACE FUNCTION public.ingest_delivery_location(
  p_delivery_job_id UUID,
  p_lat DOUBLE PRECISION,
  p_lng DOUBLE PRECISION,
  p_recorded_at TIMESTAMPTZ DEFAULT now(),
  p_accuracy_m NUMERIC DEFAULT NULL
)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_job public.delivery_jobs%ROWTYPE;
  v_id UUID;
BEGIN
  PERFORM public._logistics_begin_rpc();

  IF NOT (
    auth.role() = 'service_role'
    OR public.has_staff_role(
      ARRAY['admin', 'warehouse', 'dispatcher']::public.staff_role[]
    )
  ) THEN
    RAISE EXCEPTION 'bridge/service or dispatcher/warehouse/admin required for GPS ingest';
  END IF;

  SELECT * INTO v_job FROM public.delivery_jobs WHERE id = p_delivery_job_id;
  IF NOT FOUND THEN
    RAISE EXCEPTION 'delivery job not found';
  END IF;
  IF v_job.status IN ('completed', 'failed') THEN
    RAISE EXCEPTION 'cannot ingest locations for terminal job';
  END IF;

  IF EXISTS (
    SELECT 1
    FROM public.delivery_locations
    WHERE delivery_job_id = p_delivery_job_id
      AND ingested_at > now() - interval '5 seconds'
  ) THEN
    RAISE EXCEPTION 'ingest rate limit: wait ~5s between points';
  END IF;

  INSERT INTO public.delivery_locations (
    delivery_job_id, lat, lng, accuracy_m, recorded_at, source
  )
  VALUES (
    p_delivery_job_id, p_lat, p_lng, p_accuracy_m,
    COALESCE(p_recorded_at, now()), 'bridge'
  )
  RETURNING id INTO v_id;

  RETURN v_id;
END;
$$;

-- Retention: purge trail points older than N days (default 90)
CREATE OR REPLACE FUNCTION public.purge_delivery_locations(
  p_older_than INTERVAL DEFAULT interval '90 days'
)
RETURNS INT
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_count INT;
BEGIN
  IF NOT (
    auth.role() = 'service_role'
    OR public.has_staff_role(ARRAY['admin']::public.staff_role[])
  ) THEN
    RAISE EXCEPTION 'admin or service_role required to purge delivery locations';
  END IF;

  DELETE FROM public.delivery_locations
  WHERE ingested_at < now() - p_older_than;

  GET DIAGNOSTICS v_count = ROW_COUNT;
  RETURN v_count;
END;
$$;

-- ---------------------------------------------------------------------------
-- RLS
-- ---------------------------------------------------------------------------
ALTER TABLE public.pick_lists ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.pick_list_lines ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.delivery_notes ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.delivery_note_lines ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.delivery_jobs ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.delivery_locations ENABLE ROW LEVEL SECURITY;

CREATE POLICY pick_lists_staff_select ON public.pick_lists FOR SELECT TO authenticated
  USING (
    public.has_staff_role(
      ARRAY['admin', 'warehouse', 'dispatcher', 'sales']::public.staff_role[]
    )
  );

CREATE POLICY pick_lists_staff_write ON public.pick_lists FOR ALL TO authenticated
  USING (
    public.has_staff_role(ARRAY['admin', 'warehouse']::public.staff_role[])
  )
  WITH CHECK (
    public.has_staff_role(ARRAY['admin', 'warehouse']::public.staff_role[])
  );

CREATE POLICY pick_list_lines_staff_select ON public.pick_list_lines FOR SELECT TO authenticated
  USING (
    public.has_staff_role(
      ARRAY['admin', 'warehouse', 'dispatcher', 'sales']::public.staff_role[]
    )
  );

CREATE POLICY pick_list_lines_staff_write ON public.pick_list_lines FOR ALL TO authenticated
  USING (
    public.has_staff_role(ARRAY['admin', 'warehouse']::public.staff_role[])
  )
  WITH CHECK (
    public.has_staff_role(ARRAY['admin', 'warehouse']::public.staff_role[])
  );

CREATE POLICY delivery_notes_staff_select ON public.delivery_notes FOR SELECT TO authenticated
  USING (
    public.has_staff_role(
      ARRAY['admin', 'warehouse', 'dispatcher', 'sales']::public.staff_role[]
    )
  );

CREATE POLICY delivery_notes_staff_write ON public.delivery_notes FOR ALL TO authenticated
  USING (
    public.has_staff_role(ARRAY['admin', 'warehouse', 'dispatcher']::public.staff_role[])
  )
  WITH CHECK (
    public.has_staff_role(ARRAY['admin', 'warehouse', 'dispatcher']::public.staff_role[])
  );

CREATE POLICY delivery_note_lines_staff_select ON public.delivery_note_lines FOR SELECT TO authenticated
  USING (
    public.has_staff_role(
      ARRAY['admin', 'warehouse', 'dispatcher', 'sales']::public.staff_role[]
    )
  );

CREATE POLICY delivery_note_lines_staff_write ON public.delivery_note_lines FOR ALL TO authenticated
  USING (
    public.has_staff_role(ARRAY['admin', 'warehouse', 'dispatcher']::public.staff_role[])
  )
  WITH CHECK (
    public.has_staff_role(ARRAY['admin', 'warehouse', 'dispatcher']::public.staff_role[])
  );

CREATE POLICY delivery_jobs_staff_select ON public.delivery_jobs FOR SELECT TO authenticated
  USING (
    public.has_staff_role(
      ARRAY['admin', 'warehouse', 'dispatcher']::public.staff_role[]
    )
    OR assignee_user_id = auth.uid()
  );

CREATE POLICY delivery_jobs_staff_write ON public.delivery_jobs FOR ALL TO authenticated
  USING (
    public.has_staff_role(
      ARRAY['admin', 'warehouse', 'dispatcher']::public.staff_role[]
    )
  )
  WITH CHECK (
    public.has_staff_role(
      ARRAY['admin', 'warehouse', 'dispatcher']::public.staff_role[]
    )
  );

-- GPS trail: dispatcher|warehouse|admin only (no broad customer read in this slice)
CREATE POLICY delivery_locations_staff_select ON public.delivery_locations FOR SELECT TO authenticated
  USING (
    public.has_staff_role(
      ARRAY['admin', 'warehouse', 'dispatcher']::public.staff_role[]
    )
  );

CREATE POLICY delivery_locations_staff_insert ON public.delivery_locations FOR INSERT TO authenticated
  WITH CHECK (
    public.has_staff_role(
      ARRAY['admin', 'warehouse', 'dispatcher']::public.staff_role[]
    )
  );

-- No UPDATE/DELETE policies for locations (append-only via ingest; purge is SECURITY DEFINER)

-- ---------------------------------------------------------------------------
-- Grants
-- ---------------------------------------------------------------------------
REVOKE ALL ON FUNCTION public._require_logistics_staff() FROM PUBLIC;
REVOKE ALL ON FUNCTION public._require_dispatcher_staff() FROM PUBLIC;
REVOKE ALL ON FUNCTION public._logistics_rpc_active() FROM PUBLIC;
REVOKE ALL ON FUNCTION public._logistics_begin_rpc() FROM PUBLIC;
REVOKE ALL ON FUNCTION public._invoice_line_open_qty_base(UUID) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.create_pick_list(UUID, JSONB) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.confirm_pick_lines(UUID, JSONB) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.create_delivery_note(UUID, JSONB, UUID) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.submit_delivery_note(UUID) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.cancel_delivery_note(UUID) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.create_delivery_job(UUID, UUID, TIMESTAMPTZ, TEXT) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.update_delivery_job_status(UUID, public.delivery_job_status) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.ingest_delivery_location(UUID, DOUBLE PRECISION, DOUBLE PRECISION, TIMESTAMPTZ, NUMERIC) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.purge_delivery_locations(INTERVAL) FROM PUBLIC;

GRANT EXECUTE ON FUNCTION public.create_pos_cart(UUID, UUID, public.currency_code, public.fulfillment_mode)
  TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.checkout_pos_cart(UUID) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.create_pick_list(UUID, JSONB) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.confirm_pick_lines(UUID, JSONB) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.create_delivery_note(UUID, JSONB, UUID) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.submit_delivery_note(UUID) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.cancel_delivery_note(UUID) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.create_delivery_job(UUID, UUID, TIMESTAMPTZ, TEXT)
  TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.update_delivery_job_status(UUID, public.delivery_job_status)
  TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.ingest_delivery_location(
  UUID, DOUBLE PRECISION, DOUBLE PRECISION, TIMESTAMPTZ, NUMERIC
) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.purge_delivery_locations(INTERVAL) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public._invoice_line_open_qty_base(UUID) TO authenticated, service_role;
