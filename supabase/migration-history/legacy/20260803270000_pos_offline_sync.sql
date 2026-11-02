-- Offline POS sync: catalog/stock snapshot pull + idempotent sale replay.
-- SQLCipher tablet cache is client-side; this migration is the server SoR.
-- No ZIMRA. Ledger via existing checkout_pos_cart / settle_invoice_tenders only.
-- Privilege: sales|finance|admin staff; never caches manager approval tokens.

-- ---------------------------------------------------------------------------
-- Idempotency receipts (append-only; no UPDATE of invoice linkage)
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.pos_offline_sale_receipts (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  client_sale_id UUID NOT NULL UNIQUE,
  invoice_id UUID NOT NULL REFERENCES public.sales_invoices (id),
  actor_user_id UUID NOT NULL REFERENCES auth.users (id),
  device_id TEXT,
  warehouse_id UUID REFERENCES public.warehouses (id),
  currency public.currency_code NOT NULL DEFAULT 'USD',
  payload_hash TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS pos_offline_sale_receipts_actor_idx
  ON public.pos_offline_sale_receipts (actor_user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS pos_offline_sale_receipts_invoice_idx
  ON public.pos_offline_sale_receipts (invoice_id);

COMMENT ON TABLE public.pos_offline_sale_receipts IS
  'Idempotency store for tablet offline POS replay. Append-only; duplicate client_sale_id returns existing invoice.';

ALTER TABLE public.pos_offline_sale_receipts ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS pos_offline_sale_receipts_select ON public.pos_offline_sale_receipts;
CREATE POLICY pos_offline_sale_receipts_select ON public.pos_offline_sale_receipts
  FOR SELECT TO authenticated
  USING (
    actor_user_id = auth.uid()
    OR public.has_staff_role(ARRAY['admin', 'finance']::public.staff_role[])
  );

-- Inserts only via SECURITY DEFINER replay RPC (no direct client INSERT).
DROP POLICY IF EXISTS pos_offline_sale_receipts_insert ON public.pos_offline_sale_receipts;
CREATE POLICY pos_offline_sale_receipts_insert ON public.pos_offline_sale_receipts
  FOR INSERT TO authenticated
  WITH CHECK (false);

REVOKE ALL ON TABLE public.pos_offline_sale_receipts FROM PUBLIC;
GRANT SELECT ON TABLE public.pos_offline_sale_receipts TO authenticated, service_role;

-- ---------------------------------------------------------------------------
-- Pull: retail list price + on-hand qty for one warehouse
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.pull_pos_offline_snapshot(p_warehouse_id UUID)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_list UUID;
  v_currency public.currency_code;
  v_items JSONB;
BEGIN
  PERFORM public._require_payments_staff();

  IF p_warehouse_id IS NULL THEN
    RAISE EXCEPTION 'warehouse_id required';
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM public.warehouses w
    WHERE w.id = p_warehouse_id AND w.is_active AND NOT w.is_quarantine
  ) THEN
    RAISE EXCEPTION 'warehouse not found or not saleable';
  END IF;

  SELECT pl.id, pl.currency INTO v_list, v_currency
  FROM public.price_lists pl
  WHERE pl.is_default AND pl.is_active
  LIMIT 1;

  IF v_list IS NULL THEN
    SELECT pl.id, pl.currency INTO v_list, v_currency
    FROM public.price_lists pl
    WHERE pl.code = 'RETAIL' AND pl.is_active
    LIMIT 1;
  END IF;

  IF v_list IS NULL THEN
    RAISE EXCEPTION 'no active default/RETAIL price list';
  END IF;

  SELECT COALESCE(jsonb_agg(row_to_json(x)::jsonb ORDER BY x.oem_part_number), '[]'::jsonb)
  INTO v_items
  FROM (
    SELECT
      si.id AS stock_item_id,
      si.oem_part_number,
      si.description,
      si.base_uom_id AS uom_id,
      COALESCE(pli.unit_price, 0)::numeric AS unit_price,
      COALESCE(pli.core_charge, 0)::numeric AS core_charge,
      COALESCE(sl.quantity, 0)::numeric AS saleable_qty,
      COALESCE(sl.currency, v_currency) AS currency
    FROM public.stock_items si
    JOIN public.price_list_items pli
      ON pli.stock_item_id = si.id AND pli.price_list_id = v_list
    LEFT JOIN public.stock_levels sl
      ON sl.stock_item_id = si.id AND sl.warehouse_id = p_warehouse_id
    WHERE si.base_uom_id IS NOT NULL
      AND pli.unit_price >= 0
  ) x;

  RETURN jsonb_build_object(
    'warehouse_id', p_warehouse_id,
    'pulled_at', now(),
    'price_list_id', v_list,
    'currency', v_currency,
    'items', v_items
  );
END;
$$;

COMMENT ON FUNCTION public.pull_pos_offline_snapshot(UUID) IS
  'Staff pull of retail catalog + warehouse qty for encrypted tablet offline POS cache.';

REVOKE ALL ON FUNCTION public.pull_pos_offline_snapshot(UUID) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.pull_pos_offline_snapshot(UUID)
  TO authenticated, service_role;

-- ---------------------------------------------------------------------------
-- Replay: idempotent offline cash sale → existing cart/checkout SoR
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.replay_offline_pos_sale(
  p_client_sale_id UUID,
  p_payload JSONB
)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_existing UUID;
  v_warehouse UUID;
  v_currency public.currency_code;
  v_rate NUMERIC;
  v_device TEXT;
  v_lines JSONB;
  v_tenders JSONB;
  v_line JSONB;
  v_tender JSONB;
  v_cart UUID;
  v_inv UUID;
  v_stock UUID;
  v_uom UUID;
  v_qty NUMERIC;
  v_expected NUMERIC;
  v_price NUMERIC;
  v_hash TEXT;
  v_email TEXT;
  v_wa TEXT;
  v_phone TEXT;
  v_amt NUMERIC;
  v_tender_code TEXT;
BEGIN
  PERFORM public._require_payments_staff();

  IF p_client_sale_id IS NULL THEN
    RAISE EXCEPTION 'client_sale_id required';
  END IF;
  IF p_payload IS NULL OR jsonb_typeof(p_payload) <> 'object' THEN
    RAISE EXCEPTION 'payload object required';
  END IF;

  SELECT r.invoice_id INTO v_existing
  FROM public.pos_offline_sale_receipts r
  WHERE r.client_sale_id = p_client_sale_id;

  IF v_existing IS NOT NULL THEN
    RETURN v_existing;
  END IF;

  v_warehouse := (p_payload->>'warehouse_id')::uuid;
  IF v_warehouse IS NULL THEN
    RAISE EXCEPTION 'warehouse_id required in payload';
  END IF;

  v_currency := COALESCE(
    (p_payload->>'currency')::public.currency_code,
    'USD'::public.currency_code
  );
  v_rate := COALESCE((p_payload->>'exchange_rate')::numeric, 1);
  IF v_rate IS NULL OR v_rate <= 0 THEN
    RAISE EXCEPTION 'exchange_rate must be > 0';
  END IF;

  v_device := NULLIF(trim(COALESCE(p_payload->>'device_id', '')), '');
  v_lines := p_payload->'lines';
  v_tenders := p_payload->'tenders';

  IF v_lines IS NULL OR jsonb_typeof(v_lines) <> 'array' OR jsonb_array_length(v_lines) < 1 THEN
    RAISE EXCEPTION 'lines required';
  END IF;
  IF v_tenders IS NULL OR jsonb_typeof(v_tenders) <> 'array' OR jsonb_array_length(v_tenders) < 1 THEN
    RAISE EXCEPTION 'tenders required';
  END IF;

  -- Offline MVP: cash tender only (live rails stay online-only).
  FOR v_tender IN SELECT * FROM jsonb_array_elements(v_tenders)
  LOOP
    v_tender_code := lower(trim(COALESCE(v_tender->>'tender', '')));
    IF v_tender_code IS DISTINCT FROM 'cash' THEN
      RAISE EXCEPTION 'offline_tender_not_allowed: % (cash only)', v_tender_code;
    END IF;
    v_amt := (v_tender->>'amount')::numeric;
    IF v_amt IS NULL OR v_amt <= 0 THEN
      RAISE EXCEPTION 'tender amount must be > 0';
    END IF;
  END LOOP;

  v_email := NULLIF(trim(COALESCE(p_payload->>'receipt_email', '')), '');
  v_wa := NULLIF(trim(COALESCE(p_payload->>'receipt_whatsapp_e164', '')), '');
  v_phone := NULLIF(trim(COALESCE(p_payload->>'receipt_phone_e164', '')), '');

  v_hash := encode(
    extensions.digest(convert_to(p_payload::text, 'UTF8'), 'sha256'),
    'hex'
  );

  v_cart := public.create_pos_cart(
    v_warehouse,
    NULL, -- walk-in; checkout may bind receipt contacts
    v_currency,
    'immediate'::public.fulfillment_mode
  );

  IF v_cart IS NULL THEN
    RAISE EXCEPTION 'create_pos_cart failed';
  END IF;

  UPDATE public.pos_carts
  SET exchange_rate_applied = v_rate, updated_at = now()
  WHERE id = v_cart;

  FOR v_line IN SELECT * FROM jsonb_array_elements(v_lines)
  LOOP
    v_stock := (v_line->>'stock_item_id')::uuid;
    v_uom := (v_line->>'uom_id')::uuid;
    v_qty := (v_line->>'qty')::numeric;
    v_expected := (v_line->>'expected_unit_price')::numeric;

    IF v_stock IS NULL OR v_uom IS NULL THEN
      RAISE EXCEPTION 'line stock_item_id and uom_id required';
    END IF;
    IF v_qty IS NULL OR v_qty <= 0 THEN
      RAISE EXCEPTION 'line qty must be > 0';
    END IF;

    SELECT r.unit_price INTO v_price
    FROM public.resolve_item_price(NULL, v_stock) r;

    IF v_expected IS NOT NULL AND abs(COALESCE(v_price, 0) - v_expected) > 0.05 THEN
      RAISE EXCEPTION 'offline_price_conflict: item % expected % got %',
        v_stock, v_expected, v_price;
    END IF;

    PERFORM public.add_cart_line(v_cart, v_stock, v_uom, v_qty);
  END LOOP;

  v_inv := public.checkout_pos_cart_with_tenders(
    v_cart,
    v_tenders,
    v_email,
    v_wa,
    v_phone
  );

  INSERT INTO public.pos_offline_sale_receipts (
    client_sale_id,
    invoice_id,
    actor_user_id,
    device_id,
    warehouse_id,
    currency,
    payload_hash
  ) VALUES (
    p_client_sale_id,
    v_inv,
    auth.uid(),
    v_device,
    v_warehouse,
    v_currency,
    v_hash
  );

  RETURN v_inv;
EXCEPTION
  WHEN unique_violation THEN
    -- Concurrent duplicate replay — return the winner's invoice.
    SELECT r.invoice_id INTO v_existing
    FROM public.pos_offline_sale_receipts r
    WHERE r.client_sale_id = p_client_sale_id;
    IF v_existing IS NOT NULL THEN
      RETURN v_existing;
    END IF;
    RAISE;
END;
$$;

COMMENT ON FUNCTION public.replay_offline_pos_sale(UUID, JSONB) IS
  'Idempotent offline POS sale replay into cart/checkout SoR. Cash tender only; price conflict if list price drifts > 0.05.';

REVOKE ALL ON FUNCTION public.replay_offline_pos_sale(UUID, JSONB) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.replay_offline_pos_sale(UUID, JSONB)
  TO authenticated, service_role;
