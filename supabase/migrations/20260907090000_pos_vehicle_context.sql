-- Vehicle-scoped POS discovery + immutable sale vehicle context.
-- Nissan-only selector: model -> generation/chassis -> engine. No parallel cart authority.

ALTER TABLE public.pos_carts
  ADD COLUMN IF NOT EXISTS vehicle_model_slug TEXT,
  ADD COLUMN IF NOT EXISTS vehicle_model_name TEXT,
  ADD COLUMN IF NOT EXISTS vehicle_generation TEXT,
  ADD COLUMN IF NOT EXISTS vehicle_chassis_code TEXT,
  ADD COLUMN IF NOT EXISTS vehicle_engine_code TEXT;

ALTER TABLE public.pos_quotations
  ADD COLUMN IF NOT EXISTS vehicle_model_slug TEXT,
  ADD COLUMN IF NOT EXISTS vehicle_model_name TEXT,
  ADD COLUMN IF NOT EXISTS vehicle_generation TEXT,
  ADD COLUMN IF NOT EXISTS vehicle_chassis_code TEXT,
  ADD COLUMN IF NOT EXISTS vehicle_engine_code TEXT;

ALTER TABLE public.sales_invoices
  ADD COLUMN IF NOT EXISTS vehicle_model_slug TEXT,
  ADD COLUMN IF NOT EXISTS vehicle_model_name TEXT,
  ADD COLUMN IF NOT EXISTS vehicle_generation TEXT,
  ADD COLUMN IF NOT EXISTS vehicle_chassis_code TEXT,
  ADD COLUMN IF NOT EXISTS vehicle_engine_code TEXT;

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
  v_chassis text := NULLIF(trim(p_chassis_code), '');
  v_engine text := NULLIF(trim(p_engine_code), '');
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
  END IF;

  UPDATE public.pos_carts
  SET vehicle_model_slug = v_model_slug,
      vehicle_model_name = CASE WHEN v_model_slug IS NULL THEN NULL ELSE NULLIF(trim(p_model_name), '') END,
      vehicle_generation = CASE WHEN v_model_slug IS NULL THEN NULL ELSE NULLIF(trim(p_generation), '') END,
      vehicle_chassis_code = CASE WHEN v_model_slug IS NULL THEN NULL ELSE v_chassis END,
      vehicle_engine_code = CASE WHEN v_model_slug IS NULL THEN NULL ELSE v_engine END,
      updated_at = now()
  WHERE id = p_cart_id;
  RETURN p_cart_id;
END;
$$;

CREATE OR REPLACE FUNCTION public.search_pos_vehicle_spares(
  p_model_slug text,
  p_chassis_code text,
  p_engine_code text,
  p_query text,
  p_limit integer DEFAULT 50
)
RETURNS jsonb
LANGUAGE plpgsql
STABLE
SECURITY INVOKER
SET search_path = public
AS $$
DECLARE
  v_query text := trim(COALESCE(p_query, ''));
  v_limit integer := GREATEST(1, LEAST(COALESCE(p_limit, 50), 100));
  v_results jsonb;
BEGIN
  PERFORM public._require_sales_staff();
  IF NOT EXISTS (
    SELECT 1 FROM public.catalog_variants v
    WHERE v.maker_slug = 'nissan'
      AND v.model_slug = trim(p_model_slug)
      AND v.chassis_code = trim(p_chassis_code)
      AND v.engine_code = trim(p_engine_code)
  ) THEN
    RAISE EXCEPTION 'vehicle selection is not present in Nissan catalog';
  END IF;

  SELECT COALESCE(jsonb_agg(row_to_json(t)::jsonb ORDER BY t.oem_part_number), '[]'::jsonb)
  INTO v_results
  FROM (
    SELECT DISTINCT ON (pf.oem_part_number)
      'part'::text AS type,
      pf.oem_part_number,
      si.description,
      pf.pnc_code,
      pc.category_name,
      pc.subcategory_name,
      pf.chassis_code,
      pf.engine_code
    FROM public.part_fitment pf
    LEFT JOIN public.stock_items si ON si.oem_part_number = pf.oem_part_number
    LEFT JOIN public.pnc_categories pc ON pc.pnc_code = pf.pnc_code
    WHERE pf.chassis_code = trim(p_chassis_code)
      AND (pf.engine_code IS NULL OR pf.engine_code = trim(p_engine_code))
      AND (
        v_query = ''
        OR pf.oem_part_number ILIKE '%' || v_query || '%'
        OR COALESCE(si.description, '') ILIKE '%' || v_query || '%'
        OR COALESCE(pf.pnc_code, '') ILIKE '%' || v_query || '%'
        OR COALESCE(pc.category_name, '') ILIKE '%' || v_query || '%'
        OR COALESCE(pc.subcategory_name, '') ILIKE '%' || v_query || '%'
        OR COALESCE(pf.superseded_by, '') ILIKE '%' || v_query || '%'
      )
    ORDER BY pf.oem_part_number, (pf.engine_code = trim(p_engine_code)) DESC
    LIMIT v_limit
  ) t;

  RETURN jsonb_build_object(
    'mode', 'part',
    'query', v_query,
    'vehicle', jsonb_build_object(
      'model_slug', trim(p_model_slug),
      'chassis_code', trim(p_chassis_code),
      'engine_code', trim(p_engine_code)
    ),
    'results', v_results
  );
END;
$$;

CREATE OR REPLACE FUNCTION public._sync_pos_quotation_vehicle_from_cart()
RETURNS trigger LANGUAGE plpgsql SET search_path = public AS $$
BEGIN
  IF NEW.source_cart_id IS NOT NULL AND NEW.vehicle_model_slug IS NULL THEN
    SELECT c.vehicle_model_slug, c.vehicle_model_name, c.vehicle_generation,
           c.vehicle_chassis_code, c.vehicle_engine_code
    INTO NEW.vehicle_model_slug, NEW.vehicle_model_name, NEW.vehicle_generation,
         NEW.vehicle_chassis_code, NEW.vehicle_engine_code
    FROM public.pos_carts c WHERE c.id = NEW.source_cart_id;
  END IF;
  RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS pos_quotations_vehicle_snapshot ON public.pos_quotations;
CREATE TRIGGER pos_quotations_vehicle_snapshot
BEFORE INSERT OR UPDATE ON public.pos_quotations
FOR EACH ROW EXECUTE FUNCTION public._sync_pos_quotation_vehicle_from_cart();

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
        updated_at = now()
    WHERE id = NEW.converted_cart_id;
  END IF;
  RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS pos_quotations_converted_cart_vehicle ON public.pos_quotations;
CREATE TRIGGER pos_quotations_converted_cart_vehicle
AFTER UPDATE OF converted_cart_id ON public.pos_quotations
FOR EACH ROW EXECUTE FUNCTION public._sync_converted_cart_vehicle_from_quote();

CREATE OR REPLACE FUNCTION public._sync_sales_invoice_vehicle_from_cart()
RETURNS trigger LANGUAGE plpgsql SET search_path = public AS $$
BEGIN
  IF NEW.cart_id IS NOT NULL AND NEW.vehicle_model_slug IS NULL THEN
    SELECT c.vehicle_model_slug, c.vehicle_model_name, c.vehicle_generation,
           c.vehicle_chassis_code, c.vehicle_engine_code
    INTO NEW.vehicle_model_slug, NEW.vehicle_model_name, NEW.vehicle_generation,
         NEW.vehicle_chassis_code, NEW.vehicle_engine_code
    FROM public.pos_carts c WHERE c.id = NEW.cart_id;
  END IF;
  RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS sales_invoices_vehicle_snapshot ON public.sales_invoices;
CREATE TRIGGER sales_invoices_vehicle_snapshot
BEFORE INSERT OR UPDATE ON public.sales_invoices
FOR EACH ROW EXECUTE FUNCTION public._sync_sales_invoice_vehicle_from_cart();

REVOKE ALL ON FUNCTION public.set_pos_cart_vehicle(uuid, text, text, text, text, text) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.search_pos_vehicle_spares(text, text, text, text, integer) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.set_pos_cart_vehicle(uuid, text, text, text, text, text) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.search_pos_vehicle_spares(text, text, text, text, integer) TO authenticated, service_role;

COMMENT ON FUNCTION public.set_pos_cart_vehicle IS
  'Persist/clear Nissan model-generation-engine context on an open POS cart; validated against catalog hierarchy.';
COMMENT ON FUNCTION public.search_pos_vehicle_spares IS
  'Fitment-scoped POS spare search for selected Nissan model/chassis/engine.';


-- Preserve selected vehicle when an offline sale is replayed later.
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
  'Idempotent offline POS replay with optional Nissan vehicle snapshot; cash only; price conflict if list price drifts > 0.05.';

REVOKE ALL ON FUNCTION public.replay_offline_pos_sale(UUID, JSONB) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.replay_offline_pos_sale(UUID, JSONB)
  TO authenticated, service_role;

-- Enrich POS order/return history with the immutable invoice vehicle snapshot.
DROP FUNCTION IF EXISTS public.list_pos_recent_invoices(text, integer);
CREATE FUNCTION public.list_pos_recent_invoices(
  p_query text DEFAULT NULL,
  p_limit integer DEFAULT 50
)
RETURNS TABLE (
  id uuid,
  document_number text,
  customer_id uuid,
  customer_name text,
  total numeric,
  currency public.currency_code,
  posted_at timestamptz,
  vehicle_model_name text,
  vehicle_generation text,
  vehicle_chassis_code text,
  vehicle_engine_code text
)
LANGUAGE plpgsql
STABLE
SECURITY INVOKER
SET search_path = public
AS $$
DECLARE
  v_query text := NULLIF(trim(p_query), '');
BEGIN
  PERFORM public._require_sales_staff();

  RETURN QUERY
  SELECT
    inv.id,
    inv.document_number,
    inv.customer_id,
    c.display_name,
    inv.total,
    inv.currency,
    COALESCE(inv.posted_at, inv.created_at),
    inv.vehicle_model_name,
    inv.vehicle_generation,
    inv.vehicle_chassis_code,
    inv.vehicle_engine_code
  FROM public.sales_invoices inv
  LEFT JOIN public.customers c ON c.id = inv.customer_id
  WHERE inv.doc_type = 'invoice'
    AND inv.status = 'posted'
    AND (
      v_query IS NULL
      OR inv.document_number ILIKE '%' || v_query || '%'
      OR inv.id::text = v_query
      OR c.display_name ILIKE '%' || v_query || '%'
      OR c.email ILIKE '%' || v_query || '%'
      OR c.phone_e164 ILIKE '%' || v_query || '%'
      OR inv.vehicle_model_name ILIKE '%' || v_query || '%'
      OR inv.vehicle_chassis_code ILIKE '%' || v_query || '%'
      OR inv.vehicle_engine_code ILIKE '%' || v_query || '%'
    )
  ORDER BY COALESCE(inv.posted_at, inv.created_at) DESC
  LIMIT GREATEST(1, LEAST(p_limit, 200));
END;
$$;
REVOKE ALL ON FUNCTION public.list_pos_recent_invoices(text, integer) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.list_pos_recent_invoices(text, integer) TO authenticated, service_role;
