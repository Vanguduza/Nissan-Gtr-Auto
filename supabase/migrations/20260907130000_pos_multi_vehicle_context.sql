-- Preserve all vehicle fitment contexts used during one sale while retaining the existing
-- single active vehicle columns for fast search/filter UI and backwards compatibility.

ALTER TABLE public.pos_carts
  ADD COLUMN IF NOT EXISTS vehicle_contexts JSONB NOT NULL DEFAULT '[]'::jsonb;
ALTER TABLE public.pos_quotations
  ADD COLUMN IF NOT EXISTS vehicle_contexts JSONB NOT NULL DEFAULT '[]'::jsonb;
ALTER TABLE public.sales_invoices
  ADD COLUMN IF NOT EXISTS vehicle_contexts JSONB NOT NULL DEFAULT '[]'::jsonb;

ALTER TABLE public.pos_carts DROP CONSTRAINT IF EXISTS pos_carts_vehicle_contexts_array_check;
ALTER TABLE public.pos_carts ADD CONSTRAINT pos_carts_vehicle_contexts_array_check
  CHECK (jsonb_typeof(vehicle_contexts) = 'array');
ALTER TABLE public.pos_quotations DROP CONSTRAINT IF EXISTS pos_quotations_vehicle_contexts_array_check;
ALTER TABLE public.pos_quotations ADD CONSTRAINT pos_quotations_vehicle_contexts_array_check
  CHECK (jsonb_typeof(vehicle_contexts) = 'array');
ALTER TABLE public.sales_invoices DROP CONSTRAINT IF EXISTS sales_invoices_vehicle_contexts_array_check;
ALTER TABLE public.sales_invoices ADD CONSTRAINT sales_invoices_vehicle_contexts_array_check
  CHECK (jsonb_typeof(vehicle_contexts) = 'array');

CREATE OR REPLACE FUNCTION public.set_pos_cart_vehicle(
  p_cart_id uuid,
  p_model_slug text DEFAULT NULL,
  p_model_name text DEFAULT NULL,
  p_generation text DEFAULT NULL,
  p_chassis_code text DEFAULT NULL,
  p_engine_code text DEFAULT NULL
)
RETURNS uuid
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_model_slug text := NULLIF(trim(p_model_slug), '');
  v_model_name text := NULLIF(trim(p_model_name), '');
  v_generation text := NULLIF(trim(p_generation), '');
  v_chassis text := NULLIF(trim(p_chassis_code), '');
  v_engine text := NULLIF(trim(p_engine_code), '');
  v_context jsonb;
BEGIN
  PERFORM public._require_sales_staff();
  IF NOT EXISTS (SELECT 1 FROM public.pos_carts WHERE id = p_cart_id AND status = 'open') THEN
    RAISE EXCEPTION 'open cart not found';
  END IF;

  IF v_model_slug IS NOT NULL THEN
    IF v_chassis IS NULL OR v_engine IS NULL THEN
      RAISE EXCEPTION 'model, generation/chassis and engine are required together';
    END IF;
    IF NOT EXISTS (
      SELECT 1
      FROM public.catalog_models m
      JOIN public.catalog_variants v
        ON v.maker_slug = m.maker_slug AND v.model_slug = m.slug
      WHERE m.maker_slug = 'nissan'
        AND m.slug = v_model_slug
        AND v.chassis_code = v_chassis
        AND v.engine_code = v_engine
    ) THEN
      RAISE EXCEPTION 'selected Nissan model/generation/engine is not in the catalog';
    END IF;

    v_context := jsonb_build_object(
      'model_slug', v_model_slug,
      'model_name', v_model_name,
      'generation', v_generation,
      'chassis_code', v_chassis,
      'engine_code', v_engine
    );
  END IF;

  UPDATE public.pos_carts c
  SET vehicle_model_slug = v_model_slug,
      vehicle_model_name = CASE WHEN v_model_slug IS NULL THEN NULL ELSE v_model_name END,
      vehicle_generation = CASE WHEN v_model_slug IS NULL THEN NULL ELSE v_generation END,
      vehicle_chassis_code = CASE WHEN v_model_slug IS NULL THEN NULL ELSE v_chassis END,
      vehicle_engine_code = CASE WHEN v_model_slug IS NULL THEN NULL ELSE v_engine END,
      vehicle_contexts = CASE
        WHEN v_context IS NULL THEN c.vehicle_contexts
        WHEN EXISTS (
          SELECT 1 FROM jsonb_array_elements(COALESCE(c.vehicle_contexts, '[]'::jsonb)) e
          WHERE e->>'model_slug' = v_model_slug
            AND e->>'chassis_code' = v_chassis
            AND e->>'engine_code' = v_engine
        ) THEN c.vehicle_contexts
        ELSE COALESCE(c.vehicle_contexts, '[]'::jsonb) || jsonb_build_array(v_context)
      END,
      updated_at = now()
  WHERE c.id = p_cart_id;

  RETURN p_cart_id;
END;
$$;

CREATE OR REPLACE FUNCTION public._sync_pos_quotation_vehicle_from_cart()
RETURNS trigger LANGUAGE plpgsql SET search_path = public AS $$
BEGIN
  IF NEW.source_cart_id IS NOT NULL THEN
    SELECT c.vehicle_model_slug, c.vehicle_model_name, c.vehicle_generation,
           c.vehicle_chassis_code, c.vehicle_engine_code, c.vehicle_contexts
    INTO NEW.vehicle_model_slug, NEW.vehicle_model_name, NEW.vehicle_generation,
         NEW.vehicle_chassis_code, NEW.vehicle_engine_code, NEW.vehicle_contexts
    FROM public.pos_carts c WHERE c.id = NEW.source_cart_id;
  END IF;
  RETURN NEW;
END;
$$;

CREATE OR REPLACE FUNCTION public._sync_converted_cart_vehicle_from_quote()
RETURNS trigger LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
BEGIN
  IF NEW.converted_cart_id IS NOT NULL
     AND NEW.converted_cart_id IS DISTINCT FROM OLD.converted_cart_id THEN
    UPDATE public.pos_carts
    SET vehicle_model_slug = NEW.vehicle_model_slug,
        vehicle_model_name = NEW.vehicle_model_name,
        vehicle_generation = NEW.vehicle_generation,
        vehicle_chassis_code = NEW.vehicle_chassis_code,
        vehicle_engine_code = NEW.vehicle_engine_code,
        vehicle_contexts = COALESCE(NEW.vehicle_contexts, '[]'::jsonb),
        updated_at = now()
    WHERE id = NEW.converted_cart_id;
  END IF;
  RETURN NEW;
END;
$$;

CREATE OR REPLACE FUNCTION public._sync_sales_invoice_vehicle_from_cart()
RETURNS trigger LANGUAGE plpgsql SET search_path = public AS $$
BEGIN
  IF NEW.cart_id IS NOT NULL THEN
    SELECT c.vehicle_model_slug, c.vehicle_model_name, c.vehicle_generation,
           c.vehicle_chassis_code, c.vehicle_engine_code, c.vehicle_contexts
    INTO NEW.vehicle_model_slug, NEW.vehicle_model_name, NEW.vehicle_generation,
         NEW.vehicle_chassis_code, NEW.vehicle_engine_code, NEW.vehicle_contexts
    FROM public.pos_carts c WHERE c.id = NEW.cart_id;
  END IF;
  RETURN NEW;
END;
$$;

REVOKE ALL ON FUNCTION public.set_pos_cart_vehicle(uuid, text, text, text, text, text) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.set_pos_cart_vehicle(uuid, text, text, text, text, text) TO authenticated, service_role;

COMMENT ON COLUMN public.pos_carts.vehicle_contexts IS
  'Ordered unique Nissan fitment contexts used while shopping this cart; clearing active filter does not erase history.';
COMMENT ON COLUMN public.sales_invoices.vehicle_contexts IS
  'Immutable sale snapshot of all vehicle fitment contexts used during the originating cart.';
COMMENT ON FUNCTION public.set_pos_cart_vehicle IS
  'Set/clear active Nissan fitment while append-preserving unique multi-vehicle sale history.';


-- Offline replay override: restore full multi-vehicle history before checkout.
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
  v_vehicle JSONB;
  v_vehicle_contexts JSONB;
  v_vehicle_context JSONB;
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
  v_vehicle := p_payload->'vehicle';
  v_vehicle_contexts := COALESCE(p_payload->'vehicle_contexts', '[]'::jsonb);
  IF jsonb_typeof(v_vehicle_contexts) <> 'array' THEN
    RAISE EXCEPTION 'vehicle_contexts must be an array';
  END IF;

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

  -- Rebuild the full vehicle history before applying the final active vehicle. The
  -- set_pos_cart_vehicle function validates every context against the Nissan EPC.
  FOR v_vehicle_context IN SELECT * FROM jsonb_array_elements(v_vehicle_contexts)
  LOOP
    IF jsonb_typeof(v_vehicle_context) = 'object'
       AND NULLIF(trim(COALESCE(v_vehicle_context->>'model_slug', '')), '') IS NOT NULL THEN
      PERFORM public.set_pos_cart_vehicle(
        v_cart,
        v_vehicle_context->>'model_slug',
        v_vehicle_context->>'model_name',
        v_vehicle_context->>'generation',
        v_vehicle_context->>'chassis_code',
        v_vehicle_context->>'engine_code'
      );
    END IF;
  END LOOP;

  IF v_vehicle IS NOT NULL AND jsonb_typeof(v_vehicle) = 'object'
     AND NULLIF(trim(COALESCE(v_vehicle->>'model_slug', '')), '') IS NOT NULL THEN
    PERFORM public.set_pos_cart_vehicle(
      v_cart,
      v_vehicle->>'model_slug',
      v_vehicle->>'model_name',
      v_vehicle->>'generation',
      v_vehicle->>'chassis_code',
      v_vehicle->>'engine_code'
    );
  END IF;

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
  'Idempotent offline POS replay with complete Nissan multi-vehicle history; cash only; validated against current EPC and price authority.';

REVOKE ALL ON FUNCTION public.replay_offline_pos_sale(UUID, JSONB) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.replay_offline_pos_sale(UUID, JSONB) TO authenticated, service_role;
