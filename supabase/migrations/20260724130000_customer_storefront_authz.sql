-- Customer storefront AuthZ: own cart/invoice SELECT, customer cart RPCs,
-- ContiPay/Paynow create-intent for own unpaid invoices, My Garage.
-- Settle remains service_role/webhook. No ZIMRA / payroll tax / HTML5 QR.
-- Reuses Phase 5 pos_carts — no parallel cart schema.

-- ---------------------------------------------------------------------------
-- Channel + helpers
-- ---------------------------------------------------------------------------
CREATE TYPE public.cart_channel AS ENUM ('pos', 'storefront');

ALTER TABLE public.pos_carts
  ADD COLUMN IF NOT EXISTS channel public.cart_channel NOT NULL DEFAULT 'pos';

CREATE INDEX IF NOT EXISTS pos_carts_customer_open_idx
  ON public.pos_carts (customer_id, status)
  WHERE status = 'open';

CREATE OR REPLACE FUNCTION public._current_customer_id()
RETURNS UUID
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
  SELECT c.id
  FROM public.customers c
  WHERE c.profile_id = auth.uid()
  ORDER BY c.created_at ASC
  LIMIT 1;
$$;

CREATE OR REPLACE FUNCTION public._storefront_rpc_active()
RETURNS boolean
LANGUAGE sql
STABLE
AS $$
  SELECT COALESCE(current_setting('app.storefront_rpc', true), '') = '1';
$$;

CREATE OR REPLACE FUNCTION public._storefront_rpc_enter()
RETURNS void
LANGUAGE plpgsql
AS $$
BEGIN
  PERFORM set_config('app.storefront_rpc', '1', true);
END;
$$;

CREATE OR REPLACE FUNCTION public._assert_customer_owns_open_cart(p_cart_id UUID)
RETURNS void
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_cust UUID := public._current_customer_id();
BEGIN
  IF v_cust IS NULL THEN
    RAISE EXCEPTION 'customer profile required';
  END IF;
  IF NOT EXISTS (
    SELECT 1
    FROM public.pos_carts c
    WHERE c.id = p_cart_id
      AND c.customer_id = v_cust
      AND c.channel = 'storefront'
      AND c.status = 'open'
  ) THEN
    RAISE EXCEPTION 'not authorized for this storefront cart';
  END IF;
END;
$$;

CREATE OR REPLACE FUNCTION public._assert_customer_owns_invoice(p_invoice_id UUID)
RETURNS public.sales_invoices
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_cust UUID := public._current_customer_id();
  v_inv public.sales_invoices%ROWTYPE;
BEGIN
  IF v_cust IS NULL THEN
    RAISE EXCEPTION 'customer profile required';
  END IF;
  SELECT * INTO v_inv
  FROM public.sales_invoices
  WHERE id = p_invoice_id;
  IF NOT FOUND OR v_inv.customer_id IS DISTINCT FROM v_cust THEN
    RAISE EXCEPTION 'not authorized for this invoice';
  END IF;
  RETURN v_inv;
END;
$$;

-- Staff path unchanged; storefront RPCs set app.storefront_rpc and own-cart check.
CREATE OR REPLACE FUNCTION public._require_cart_mutate(p_cart_id UUID)
RETURNS void
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
  IF public._storefront_rpc_active() THEN
    PERFORM public._assert_customer_owns_open_cart(p_cart_id);
    RETURN;
  END IF;
  PERFORM public._require_sales_staff();
END;
$$;

-- Patch kit-aware add/checkout gates without forking bodies.
DO $$
DECLARE
  v_def TEXT;
BEGIN
  SELECT pg_get_functiondef(p.oid) INTO v_def
  FROM pg_proc p
  JOIN pg_namespace n ON n.oid = p.pronamespace
  WHERE n.nspname = 'public' AND p.proname = 'add_cart_line'
  LIMIT 1;

  IF position('PERFORM public._require_cart_mutate(p_cart_id);' IN v_def) = 0 THEN
    IF v_def IS NULL OR position('PERFORM public._require_sales_staff();' IN v_def) = 0 THEN
      RAISE EXCEPTION 'add_cart_line patch failed: sales staff gate not found';
    END IF;
    v_def := replace(
      v_def,
      'PERFORM public._require_sales_staff();',
      'PERFORM public._require_cart_mutate(p_cart_id);'
    );
    EXECUTE v_def;
  END IF;

  SELECT pg_get_functiondef(p.oid) INTO v_def
  FROM pg_proc p
  JOIN pg_namespace n ON n.oid = p.pronamespace
  WHERE n.nspname = 'public' AND p.proname = 'checkout_pos_cart'
  LIMIT 1;

  IF position('PERFORM public._require_cart_mutate(p_cart_id);' IN v_def) = 0 THEN
    IF v_def IS NULL OR position('PERFORM public._require_sales_staff();' IN v_def) = 0 THEN
      RAISE EXCEPTION 'checkout_pos_cart patch failed: sales staff gate not found';
    END IF;
    v_def := replace(
      v_def,
      'PERFORM public._require_sales_staff();',
      'PERFORM public._require_cart_mutate(p_cart_id);'
    );
    EXECUTE v_def;
  END IF;
END;
$$;

-- Allow storefront checkout to emit order_* domain events (still blocks raw customer calls).
CREATE OR REPLACE FUNCTION public.emit_domain_event(
  p_event_code TEXT,
  p_dedupe_key TEXT,
  p_payload JSONB DEFAULT '{}'::jsonb,
  p_actor_user_id UUID DEFAULT auth.uid(),
  p_message_body TEXT DEFAULT NULL
)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_event_id UUID;
  v_body TEXT;
  v_desc TEXT;
BEGIN
  IF auth.role() = 'authenticated'
     AND NOT public.is_staff()
     AND NOT public._storefront_rpc_active() THEN
    RAISE EXCEPTION 'Only staff or service role may emit domain events';
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM public.sms_event_catalog c
    WHERE c.code = p_event_code AND c.is_active
  ) THEN
    RAISE EXCEPTION 'Unknown or inactive event_code: %', p_event_code;
  END IF;

  INSERT INTO public.domain_events (event_code, dedupe_key, payload, actor_user_id)
  VALUES (p_event_code, p_dedupe_key, COALESCE(p_payload, '{}'::jsonb), p_actor_user_id)
  ON CONFLICT (event_code, dedupe_key) DO NOTHING
  RETURNING id INTO v_event_id;

  IF v_event_id IS NULL THEN
    SELECT id INTO v_event_id
    FROM public.domain_events
    WHERE event_code = p_event_code AND dedupe_key = p_dedupe_key;
  END IF;

  SELECT description INTO v_desc FROM public.sms_event_catalog WHERE code = p_event_code;
  v_body := COALESCE(
    p_message_body,
    format('GTR Auto: %s (%s)', v_desc, p_event_code)
  );

  INSERT INTO public.sms_outbox (
    domain_event_id, event_code, recipient_user_id, phone_e164, body
  )
  SELECT
    v_event_id,
    p_event_code,
    p.user_id,
    p.phone_e164,
    v_body
  FROM public.manager_sms_preferences p
  WHERE p.event_code = p_event_code
    AND p.enabled = true
  ON CONFLICT (domain_event_id, recipient_user_id) DO NOTHING;

  RETURN v_event_id;
END;
$$;

-- Storefront checkout posts sale JE via shared checkout_pos_cart path.
DO $$
DECLARE
  v_def TEXT;
  v_old TEXT :=
    'IF NOT (
    auth.role() = ''service_role''
    OR public.has_staff_role(ARRAY[''admin'', ''finance'']::public.staff_role[])
  ) THEN
    RAISE EXCEPTION ''finance or admin role required'';
  END IF;';
  v_new TEXT :=
    'IF NOT (
    auth.role() = ''service_role''
    OR public.has_staff_role(ARRAY[''admin'', ''finance'']::public.staff_role[])
    OR public._storefront_rpc_active()
  ) THEN
    RAISE EXCEPTION ''finance or admin role required'';
  END IF;';
BEGIN
  SELECT pg_get_functiondef(p.oid) INTO v_def
  FROM pg_proc p
  JOIN pg_namespace n ON n.oid = p.pronamespace
  WHERE n.nspname = 'public' AND p.proname = 'create_journal_draft'
  LIMIT 1;

  IF position('OR public._storefront_rpc_active()' IN v_def) = 0 THEN
    IF position('finance or admin role required' IN v_def) = 0 THEN
      RAISE EXCEPTION 'create_journal_draft patch failed: finance gate not found';
    END IF;
    -- Normalize whitespace-insensitive replace via regex-like fixed form from live def
    v_def := replace(
      v_def,
      'OR public.has_staff_role(ARRAY[''admin'', ''finance'']::public.staff_role[])
  ) THEN
    RAISE EXCEPTION ''finance or admin role required'';',
      'OR public.has_staff_role(ARRAY[''admin'', ''finance'']::public.staff_role[])
    OR public._storefront_rpc_active()
  ) THEN
    RAISE EXCEPTION ''finance or admin role required'';'
    );
    IF position('OR public._storefront_rpc_active()' IN v_def) = 0 THEN
      RAISE EXCEPTION 'create_journal_draft patch failed: replace miss';
    END IF;
    EXECUTE v_def;
  END IF;
END;
$$;

-- ---------------------------------------------------------------------------
-- Customer cart RPCs (force ownership; reuse add/checkout)
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.create_customer_cart(
  p_warehouse_id UUID,
  p_currency public.currency_code DEFAULT 'USD',
  p_fulfillment_mode public.fulfillment_mode DEFAULT 'dispatch',
  p_exchange_rate NUMERIC DEFAULT 1
)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_cust UUID := public._current_customer_id();
  v_id UUID;
  v_rate NUMERIC;
BEGIN
  IF v_cust IS NULL THEN
    RAISE EXCEPTION 'customer profile required';
  END IF;
  IF p_warehouse_id IS NULL THEN
    RAISE EXCEPTION 'warehouse_id required';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM public.warehouses WHERE id = p_warehouse_id) THEN
    RAISE EXCEPTION 'warehouse not found';
  END IF;

  v_rate := CASE
    WHEN p_currency = 'USD' THEN COALESCE(p_exchange_rate, 1)
    ELSE p_exchange_rate
  END;
  IF v_rate IS NULL OR v_rate <= 0 THEN
    RAISE EXCEPTION 'exchange_rate_applied must be > 0';
  END IF;

  INSERT INTO public.pos_carts (
    document_number, customer_id, warehouse_id, currency, exchange_rate_applied,
    fulfillment_mode, channel, created_by
  )
  VALUES (
    public.next_series_value('CART-'),
    v_cust,
    p_warehouse_id,
    p_currency,
    v_rate,
    COALESCE(p_fulfillment_mode, 'dispatch'),
    'storefront',
    auth.uid()
  )
  RETURNING id INTO v_id;

  RETURN v_id;
END;
$$;

CREATE OR REPLACE FUNCTION public.add_customer_cart_line(
  p_cart_id UUID,
  p_stock_item_id UUID,
  p_uom_id UUID,
  p_qty NUMERIC
)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
  PERFORM public._storefront_rpc_enter();
  PERFORM public._assert_customer_owns_open_cart(p_cart_id);
  RETURN public.add_cart_line(p_cart_id, p_stock_item_id, p_uom_id, p_qty);
END;
$$;

CREATE OR REPLACE FUNCTION public.checkout_customer_cart(p_cart_id UUID)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
  PERFORM public._storefront_rpc_enter();
  PERFORM public._assert_customer_owns_open_cart(p_cart_id);
  RETURN public.checkout_pos_cart(p_cart_id);
END;
$$;

-- ---------------------------------------------------------------------------
-- Order status (customer-safe summary — no assignee/GPS)
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.get_customer_order(p_invoice_id UUID)
RETURNS JSONB
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_inv public.sales_invoices%ROWTYPE;
  v_dn_status public.delivery_note_status;
  v_pick_status public.pick_list_status;
BEGIN
  v_inv := public._assert_customer_owns_invoice(p_invoice_id);

  SELECT dn.status INTO v_dn_status
  FROM public.delivery_notes dn
  WHERE dn.sales_invoice_id = v_inv.id
  ORDER BY dn.created_at DESC
  LIMIT 1;

  SELECT pl.status INTO v_pick_status
  FROM public.pick_lists pl
  WHERE pl.sales_invoice_id = v_inv.id
  ORDER BY pl.created_at DESC
  LIMIT 1;

  RETURN jsonb_build_object(
    'invoice_id', v_inv.id,
    'document_number', v_inv.document_number,
    'doc_type', v_inv.doc_type,
    'status', v_inv.status,
    'fulfillment_mode', v_inv.fulfillment_mode,
    'currency', v_inv.currency,
    'exchange_rate_applied', v_inv.exchange_rate_applied,
    'subtotal', v_inv.subtotal,
    'total', v_inv.total,
    'amount_paid', v_inv.amount_paid,
    'amount_open', v_inv.total - v_inv.amount_paid,
    'cart_id', v_inv.cart_id,
    'posted_at', v_inv.posted_at,
    'pick_list_status', v_pick_status,
    'delivery_note_status', v_dn_status
  );
END;
$$;

-- ---------------------------------------------------------------------------
-- Customer payment intents (own unpaid invoices only; settle stays service)
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.create_customer_contipay_intent(
  p_sales_invoice_id UUID,
  p_method public.contipay_method,
  p_external_ref TEXT DEFAULT NULL,
  p_amount NUMERIC DEFAULT NULL,
  p_settlement_currency public.currency_code DEFAULT NULL,
  p_settlement_amount NUMERIC DEFAULT NULL,
  p_settlement_exchange_rate NUMERIC DEFAULT NULL,
  p_metadata JSONB DEFAULT '{}'::jsonb
)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_inv public.sales_invoices%ROWTYPE;
  v_open NUMERIC;
  v_amount NUMERIC;
  v_ref TEXT;
  v_id UUID;
BEGIN
  v_inv := public._assert_customer_owns_invoice(p_sales_invoice_id);

  IF v_inv.doc_type <> 'invoice' OR v_inv.status <> 'posted' THEN
    RAISE EXCEPTION 'only posted invoices can receive payment intents';
  END IF;

  v_open := v_inv.total - v_inv.amount_paid;
  IF v_open <= 0 THEN
    RAISE EXCEPTION 'invoice has no open balance';
  END IF;

  v_amount := COALESCE(p_amount, v_open);
  IF v_amount IS NULL OR v_amount <= 0 THEN
    RAISE EXCEPTION 'amount must be > 0';
  END IF;
  IF v_amount > v_open THEN
    RAISE EXCEPTION 'amount % exceeds invoice open %', v_amount, v_open;
  END IF;

  v_ref := COALESCE(nullif(trim(p_external_ref), ''), 'CP-CUST-' || v_inv.id::text || '-' || gen_random_uuid()::text);

  PERFORM public._payments_rpc_enter();

  INSERT INTO public.contipay_payment_intents (
    external_ref, method, customer_id, amount, currency, exchange_rate_applied,
    settlement_currency, settlement_amount, settlement_exchange_rate,
    metadata, created_by
  )
  VALUES (
    v_ref,
    p_method,
    v_inv.customer_id,
    v_amount,
    v_inv.currency,
    v_inv.exchange_rate_applied,
    p_settlement_currency,
    p_settlement_amount,
    p_settlement_exchange_rate,
    COALESCE(p_metadata, '{}'::jsonb) || jsonb_build_object(
      'sales_invoice_id', v_inv.id,
      'channel', 'storefront'
    ),
    auth.uid()
  )
  RETURNING id INTO v_id;

  RETURN v_id;
END;
$$;

CREATE OR REPLACE FUNCTION public.create_customer_paynow_intent(
  p_sales_invoice_id UUID,
  p_method public.paynow_method,
  p_external_ref TEXT DEFAULT NULL,
  p_amount NUMERIC DEFAULT NULL,
  p_settlement_currency public.currency_code DEFAULT NULL,
  p_settlement_amount NUMERIC DEFAULT NULL,
  p_settlement_exchange_rate NUMERIC DEFAULT NULL,
  p_metadata JSONB DEFAULT '{}'::jsonb
)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_inv public.sales_invoices%ROWTYPE;
  v_open NUMERIC;
  v_amount NUMERIC;
  v_ref TEXT;
  v_id UUID;
BEGIN
  v_inv := public._assert_customer_owns_invoice(p_sales_invoice_id);

  IF v_inv.doc_type <> 'invoice' OR v_inv.status <> 'posted' THEN
    RAISE EXCEPTION 'only posted invoices can receive payment intents';
  END IF;

  v_open := v_inv.total - v_inv.amount_paid;
  IF v_open <= 0 THEN
    RAISE EXCEPTION 'invoice has no open balance';
  END IF;

  v_amount := COALESCE(p_amount, v_open);
  IF v_amount IS NULL OR v_amount <= 0 THEN
    RAISE EXCEPTION 'amount must be > 0';
  END IF;
  IF v_amount > v_open THEN
    RAISE EXCEPTION 'amount % exceeds invoice open %', v_amount, v_open;
  END IF;

  v_ref := COALESCE(nullif(trim(p_external_ref), ''), 'PN-CUST-' || v_inv.id::text || '-' || gen_random_uuid()::text);

  PERFORM public._payments_rpc_enter();

  INSERT INTO public.paynow_payment_intents (
    external_ref, method, customer_id, amount, currency, exchange_rate_applied,
    settlement_currency, settlement_amount, settlement_exchange_rate,
    metadata, created_by
  )
  VALUES (
    v_ref,
    p_method,
    v_inv.customer_id,
    v_amount,
    v_inv.currency,
    v_inv.exchange_rate_applied,
    p_settlement_currency,
    p_settlement_amount,
    p_settlement_exchange_rate,
    COALESCE(p_metadata, '{}'::jsonb) || jsonb_build_object(
      'sales_invoice_id', v_inv.id,
      'channel', 'storefront'
    ),
    auth.uid()
  )
  RETURNING id INTO v_id;

  RETURN v_id;
END;
$$;

-- ---------------------------------------------------------------------------
-- My Garage (sticky fitment)
-- ---------------------------------------------------------------------------
CREATE TABLE public.customer_garage_vehicles (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  customer_id UUID NOT NULL REFERENCES public.customers (id) ON DELETE CASCADE,
  make TEXT,
  model TEXT,
  generation TEXT,
  engine TEXT,
  vin TEXT,
  is_primary BOOLEAN NOT NULL DEFAULT false,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT garage_vin_or_fitment CHECK (
    vin IS NOT NULL
    OR make IS NOT NULL
    OR model IS NOT NULL
    OR generation IS NOT NULL
    OR engine IS NOT NULL
  )
);

CREATE INDEX customer_garage_customer_idx
  ON public.customer_garage_vehicles (customer_id, created_at DESC);

CREATE UNIQUE INDEX customer_garage_one_primary_idx
  ON public.customer_garage_vehicles (customer_id)
  WHERE is_primary;

COMMENT ON TABLE public.customer_garage_vehicles IS
  'Customer My Garage sticky fitment. Own-row RLS; no staff assignee fields.';

CREATE OR REPLACE FUNCTION public.upsert_customer_garage_vehicle(
  p_id UUID DEFAULT NULL,
  p_make TEXT DEFAULT NULL,
  p_model TEXT DEFAULT NULL,
  p_generation TEXT DEFAULT NULL,
  p_engine TEXT DEFAULT NULL,
  p_vin TEXT DEFAULT NULL,
  p_is_primary BOOLEAN DEFAULT false
)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_cust UUID := public._current_customer_id();
  v_id UUID;
BEGIN
  IF v_cust IS NULL THEN
    RAISE EXCEPTION 'customer profile required';
  END IF;

  IF COALESCE(p_is_primary, false) THEN
    UPDATE public.customer_garage_vehicles
    SET is_primary = false, updated_at = now()
    WHERE customer_id = v_cust AND is_primary
      AND (p_id IS NULL OR id IS DISTINCT FROM p_id);
  END IF;

  IF p_id IS NOT NULL THEN
    UPDATE public.customer_garage_vehicles
    SET
      make = COALESCE(p_make, make),
      model = COALESCE(p_model, model),
      generation = COALESCE(p_generation, generation),
      engine = COALESCE(p_engine, engine),
      vin = COALESCE(p_vin, vin),
      is_primary = COALESCE(p_is_primary, is_primary),
      updated_at = now()
    WHERE id = p_id AND customer_id = v_cust
    RETURNING id INTO v_id;

    IF v_id IS NULL THEN
      RAISE EXCEPTION 'garage vehicle not found';
    END IF;
    RETURN v_id;
  END IF;

  INSERT INTO public.customer_garage_vehicles (
    customer_id, make, model, generation, engine, vin, is_primary
  )
  VALUES (
    v_cust, p_make, p_model, p_generation, p_engine, p_vin, COALESCE(p_is_primary, false)
  )
  RETURNING id INTO v_id;

  RETURN v_id;
END;
$$;

CREATE OR REPLACE FUNCTION public.delete_customer_garage_vehicle(p_id UUID)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_cust UUID := public._current_customer_id();
BEGIN
  IF v_cust IS NULL THEN
    RAISE EXCEPTION 'customer profile required';
  END IF;
  DELETE FROM public.customer_garage_vehicles
  WHERE id = p_id AND customer_id = v_cust;
  IF NOT FOUND THEN
    RAISE EXCEPTION 'garage vehicle not found';
  END IF;
END;
$$;

-- ---------------------------------------------------------------------------
-- RLS: customer SELECT on own carts/invoices/intents; garage own-row
-- ---------------------------------------------------------------------------
ALTER TABLE public.customer_garage_vehicles ENABLE ROW LEVEL SECURITY;

CREATE POLICY carts_customer_select ON public.pos_carts
  FOR SELECT TO authenticated
  USING (
    customer_id IS NOT NULL
    AND customer_id = public._current_customer_id()
  );

CREATE POLICY cart_lines_customer_select ON public.pos_cart_lines
  FOR SELECT TO authenticated
  USING (
    EXISTS (
      SELECT 1
      FROM public.pos_carts c
      WHERE c.id = pos_cart_lines.cart_id
        AND c.customer_id = public._current_customer_id()
    )
  );

CREATE POLICY invoices_customer_select ON public.sales_invoices
  FOR SELECT TO authenticated
  USING (
    customer_id IS NOT NULL
    AND customer_id = public._current_customer_id()
  );

CREATE POLICY invoice_lines_customer_select ON public.sales_invoice_lines
  FOR SELECT TO authenticated
  USING (
    EXISTS (
      SELECT 1
      FROM public.sales_invoices i
      WHERE i.id = sales_invoice_lines.invoice_id
        AND i.customer_id = public._current_customer_id()
    )
  );

CREATE POLICY contipay_intents_customer_select ON public.contipay_payment_intents
  FOR SELECT TO authenticated
  USING (
    customer_id IS NOT NULL
    AND customer_id = public._current_customer_id()
  );

CREATE POLICY paynow_intents_customer_select ON public.paynow_payment_intents
  FOR SELECT TO authenticated
  USING (
    customer_id IS NOT NULL
    AND customer_id = public._current_customer_id()
  );

CREATE POLICY garage_customer_all ON public.customer_garage_vehicles
  FOR ALL TO authenticated
  USING (customer_id = public._current_customer_id())
  WITH CHECK (customer_id = public._current_customer_id());

CREATE POLICY garage_staff_select ON public.customer_garage_vehicles
  FOR SELECT TO authenticated
  USING (public.has_staff_role(ARRAY['admin', 'sales']::public.staff_role[]));

-- Grants: new table SELECT (+ DML for garage own-row policies)
GRANT SELECT ON TABLE public.customer_garage_vehicles TO authenticated, service_role;
GRANT INSERT, UPDATE, DELETE ON TABLE public.customer_garage_vehicles TO authenticated;
GRANT INSERT, UPDATE, DELETE ON TABLE public.customer_garage_vehicles TO service_role;

-- ---------------------------------------------------------------------------
-- EXECUTE grants / revokes
-- ---------------------------------------------------------------------------
REVOKE ALL ON FUNCTION public._current_customer_id() FROM PUBLIC;
REVOKE ALL ON FUNCTION public._storefront_rpc_active() FROM PUBLIC;
REVOKE ALL ON FUNCTION public._storefront_rpc_enter() FROM PUBLIC;
REVOKE ALL ON FUNCTION public._assert_customer_owns_open_cart(UUID) FROM PUBLIC;
REVOKE ALL ON FUNCTION public._assert_customer_owns_invoice(UUID) FROM PUBLIC;
REVOKE ALL ON FUNCTION public._require_cart_mutate(UUID) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.create_customer_cart(UUID, public.currency_code, public.fulfillment_mode, NUMERIC) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.add_customer_cart_line(UUID, UUID, UUID, NUMERIC) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.checkout_customer_cart(UUID) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.get_customer_order(UUID) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.create_customer_contipay_intent(
  UUID, public.contipay_method, TEXT, NUMERIC,
  public.currency_code, NUMERIC, NUMERIC, JSONB
) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.create_customer_paynow_intent(
  UUID, public.paynow_method, TEXT, NUMERIC,
  public.currency_code, NUMERIC, NUMERIC, JSONB
) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.upsert_customer_garage_vehicle(
  UUID, TEXT, TEXT, TEXT, TEXT, TEXT, BOOLEAN
) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.delete_customer_garage_vehicle(UUID) FROM PUBLIC;

GRANT EXECUTE ON FUNCTION public._current_customer_id() TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.create_customer_cart(UUID, public.currency_code, public.fulfillment_mode, NUMERIC)
  TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.add_customer_cart_line(UUID, UUID, UUID, NUMERIC)
  TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.checkout_customer_cart(UUID) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.get_customer_order(UUID) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.create_customer_contipay_intent(
  UUID, public.contipay_method, TEXT, NUMERIC,
  public.currency_code, NUMERIC, NUMERIC, JSONB
) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.create_customer_paynow_intent(
  UUID, public.paynow_method, TEXT, NUMERIC,
  public.currency_code, NUMERIC, NUMERIC, JSONB
) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.upsert_customer_garage_vehicle(
  UUID, TEXT, TEXT, TEXT, TEXT, TEXT, BOOLEAN
) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.delete_customer_garage_vehicle(UUID) TO authenticated, service_role;

-- Helpers stay internal (no grant to authenticated beyond _current_customer_id for RLS)
REVOKE ALL ON FUNCTION public._storefront_rpc_enter() FROM authenticated;
REVOKE ALL ON FUNCTION public._assert_customer_owns_open_cart(UUID) FROM authenticated;
REVOKE ALL ON FUNCTION public._assert_customer_owns_invoice(UUID) FROM authenticated;
REVOKE ALL ON FUNCTION public._require_cart_mutate(UUID) FROM authenticated;
