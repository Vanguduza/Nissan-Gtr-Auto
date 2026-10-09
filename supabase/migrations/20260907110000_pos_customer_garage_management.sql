-- Operator POS customer + garage management.
-- Exposes only non-sensitive commercial/contact profile fields; credit/identity fields remain outside this surface.

ALTER TABLE public.customers
  ADD COLUMN IF NOT EXISTS customer_kind TEXT NOT NULL DEFAULT 'individual',
  ADD COLUMN IF NOT EXISTS business_name TEXT;

ALTER TABLE public.customers
  DROP CONSTRAINT IF EXISTS customers_customer_kind_check;
ALTER TABLE public.customers
  ADD CONSTRAINT customers_customer_kind_check
  CHECK (customer_kind IN ('individual', 'business'));

ALTER TABLE public.customer_garage_vehicles
  ADD COLUMN IF NOT EXISTS model_slug TEXT,
  ADD COLUMN IF NOT EXISTS chassis_code TEXT;

CREATE INDEX IF NOT EXISTS customer_garage_fitment_idx
  ON public.customer_garage_vehicles (customer_id, model_slug, chassis_code, engine);

ALTER TABLE public.sales_invoices
  ADD COLUMN IF NOT EXISTS customer_display_name TEXT,
  ADD COLUMN IF NOT EXISTS customer_business_name TEXT;

CREATE OR REPLACE FUNCTION public._snapshot_sales_invoice_customer()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_customer public.customers%ROWTYPE;
BEGIN
  IF NEW.customer_id IS NULL THEN
    NEW.customer_display_name := NULL;
    NEW.customer_business_name := NULL;
    RETURN NEW;
  END IF;

  SELECT * INTO v_customer FROM public.customers WHERE id = NEW.customer_id;
  IF FOUND THEN
    NEW.customer_display_name := v_customer.display_name;
    NEW.customer_business_name := v_customer.business_name;
    NEW.customer_phone_e164 := COALESCE(NEW.customer_phone_e164, v_customer.phone_e164);
    NEW.customer_email := COALESCE(NEW.customer_email, v_customer.email);
    NEW.customer_whatsapp_e164 := COALESCE(NEW.customer_whatsapp_e164, v_customer.whatsapp_e164);
  END IF;
  RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_sales_invoice_customer_snapshot ON public.sales_invoices;
CREATE TRIGGER trg_sales_invoice_customer_snapshot
BEFORE INSERT OR UPDATE OF customer_id ON public.sales_invoices
FOR EACH ROW EXECUTE FUNCTION public._snapshot_sales_invoice_customer();

CREATE OR REPLACE FUNCTION public.list_pos_customers(
  p_query TEXT DEFAULT NULL,
  p_limit INTEGER DEFAULT 30
)
RETURNS TABLE (
  id UUID,
  display_name TEXT,
  customer_kind TEXT,
  business_name TEXT,
  email TEXT,
  phone_e164 TEXT,
  whatsapp_e164 TEXT
)
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_query TEXT := NULLIF(trim(COALESCE(p_query, '')), '');
BEGIN
  PERFORM public._require_sales_staff();
  RETURN QUERY
  SELECT c.id, c.display_name, c.customer_kind, c.business_name, c.email, c.phone_e164, c.whatsapp_e164
  FROM public.customers c
  WHERE v_query IS NULL
     OR c.id::text = v_query
     OR c.display_name ILIKE '%' || v_query || '%'
     OR c.business_name ILIKE '%' || v_query || '%'
     OR c.email ILIKE '%' || v_query || '%'
     OR c.phone_e164 ILIKE '%' || v_query || '%'
     OR c.whatsapp_e164 ILIKE '%' || v_query || '%'
  ORDER BY COALESCE(NULLIF(c.business_name, ''), c.display_name), c.display_name
  LIMIT GREATEST(1, LEAST(COALESCE(p_limit, 30), 100));
END;
$$;

CREATE OR REPLACE FUNCTION public.create_pos_customer(
  p_customer_kind TEXT,
  p_display_name TEXT,
  p_business_name TEXT DEFAULT NULL,
  p_email TEXT DEFAULT NULL,
  p_phone_e164 TEXT DEFAULT NULL,
  p_whatsapp_e164 TEXT DEFAULT NULL
)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_id UUID;
  v_kind TEXT := lower(trim(COALESCE(p_customer_kind, 'individual')));
  v_name TEXT := NULLIF(trim(COALESCE(p_display_name, '')), '');
  v_business TEXT := NULLIF(trim(COALESCE(p_business_name, '')), '');
BEGIN
  PERFORM public._require_sales_staff();
  IF v_kind NOT IN ('individual', 'business') THEN RAISE EXCEPTION 'invalid customer kind'; END IF;
  IF v_name IS NULL THEN RAISE EXCEPTION 'customer display name required'; END IF;
  IF v_kind = 'business' AND v_business IS NULL THEN RAISE EXCEPTION 'business name required'; END IF;

  INSERT INTO public.customers (
    customer_kind, display_name, business_name, email, phone_e164, whatsapp_e164
  ) VALUES (
    v_kind, v_name, v_business,
    NULLIF(trim(COALESCE(p_email, '')), ''),
    NULLIF(trim(COALESCE(p_phone_e164, '')), ''),
    NULLIF(trim(COALESCE(p_whatsapp_e164, '')), '')
  ) RETURNING id INTO v_id;
  RETURN v_id;
END;
$$;

CREATE OR REPLACE FUNCTION public.update_pos_customer(
  p_customer_id UUID,
  p_customer_kind TEXT,
  p_display_name TEXT,
  p_business_name TEXT DEFAULT NULL,
  p_email TEXT DEFAULT NULL,
  p_phone_e164 TEXT DEFAULT NULL,
  p_whatsapp_e164 TEXT DEFAULT NULL
)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_kind TEXT := lower(trim(COALESCE(p_customer_kind, 'individual')));
  v_name TEXT := NULLIF(trim(COALESCE(p_display_name, '')), '');
  v_business TEXT := NULLIF(trim(COALESCE(p_business_name, '')), '');
BEGIN
  PERFORM public._require_sales_staff();
  IF v_kind NOT IN ('individual', 'business') THEN RAISE EXCEPTION 'invalid customer kind'; END IF;
  IF v_name IS NULL THEN RAISE EXCEPTION 'customer display name required'; END IF;
  IF v_kind = 'business' AND v_business IS NULL THEN RAISE EXCEPTION 'business name required'; END IF;

  UPDATE public.customers SET
    customer_kind = v_kind,
    display_name = v_name,
    business_name = v_business,
    email = NULLIF(trim(COALESCE(p_email, '')), ''),
    phone_e164 = NULLIF(trim(COALESCE(p_phone_e164, '')), ''),
    whatsapp_e164 = NULLIF(trim(COALESCE(p_whatsapp_e164, '')), ''),
    updated_at = now()
  WHERE id = p_customer_id;
  IF NOT FOUND THEN RAISE EXCEPTION 'customer not found'; END IF;
  RETURN p_customer_id;
END;
$$;

CREATE OR REPLACE FUNCTION public.list_pos_customer_garage(p_customer_id UUID)
RETURNS TABLE (
  id UUID,
  customer_id UUID,
  make TEXT,
  model_slug TEXT,
  model TEXT,
  generation TEXT,
  chassis_code TEXT,
  engine TEXT,
  vin TEXT,
  is_primary BOOLEAN
)
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
  PERFORM public._require_sales_staff();
  RETURN QUERY
  SELECT g.id, g.customer_id, g.make, g.model_slug, g.model, g.generation,
         g.chassis_code, g.engine, g.vin, g.is_primary
  FROM public.customer_garage_vehicles g
  WHERE g.customer_id = p_customer_id
  ORDER BY g.is_primary DESC, g.updated_at DESC, g.created_at DESC;
END;
$$;

CREATE OR REPLACE FUNCTION public.upsert_pos_customer_garage_vehicle(
  p_customer_id UUID,
  p_vehicle_id UUID DEFAULT NULL,
  p_model_slug TEXT DEFAULT NULL,
  p_make TEXT DEFAULT 'Nissan',
  p_model TEXT DEFAULT NULL,
  p_generation TEXT DEFAULT NULL,
  p_chassis_code TEXT DEFAULT NULL,
  p_engine TEXT DEFAULT NULL,
  p_vin TEXT DEFAULT NULL,
  p_is_primary BOOLEAN DEFAULT FALSE
)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_id UUID := COALESCE(p_vehicle_id, gen_random_uuid());
BEGIN
  PERFORM public._require_sales_staff();
  IF NOT EXISTS (SELECT 1 FROM public.customers WHERE id = p_customer_id) THEN
    RAISE EXCEPTION 'customer not found';
  END IF;
  IF NULLIF(trim(COALESCE(p_model_slug, '')), '') IS NULL
     OR NULLIF(trim(COALESCE(p_model, '')), '') IS NULL
     OR NULLIF(trim(COALESCE(p_generation, '')), '') IS NULL
     OR NULLIF(trim(COALESCE(p_chassis_code, '')), '') IS NULL
     OR NULLIF(trim(COALESCE(p_engine, '')), '') IS NULL THEN
    RAISE EXCEPTION 'canonical model/generation/chassis/engine required';
  END IF;

  IF p_is_primary THEN
    UPDATE public.customer_garage_vehicles SET is_primary = FALSE, updated_at = now()
    WHERE customer_id = p_customer_id AND is_primary;
  END IF;

  INSERT INTO public.customer_garage_vehicles (
    id, customer_id, make, model_slug, model, generation, chassis_code, engine, vin, is_primary
  ) VALUES (
    v_id, p_customer_id, NULLIF(trim(COALESCE(p_make, '')), ''), trim(p_model_slug), trim(p_model),
    trim(p_generation), trim(p_chassis_code), trim(p_engine), NULLIF(trim(COALESCE(p_vin, '')), ''), p_is_primary
  )
  ON CONFLICT (id) DO UPDATE SET
    make = EXCLUDED.make,
    model_slug = EXCLUDED.model_slug,
    model = EXCLUDED.model,
    generation = EXCLUDED.generation,
    chassis_code = EXCLUDED.chassis_code,
    engine = EXCLUDED.engine,
    vin = EXCLUDED.vin,
    is_primary = EXCLUDED.is_primary,
    updated_at = now()
  WHERE public.customer_garage_vehicles.customer_id = p_customer_id;

  IF NOT FOUND THEN RAISE EXCEPTION 'garage vehicle not found for customer'; END IF;
  RETURN v_id;
END;
$$;

CREATE OR REPLACE FUNCTION public.set_pos_cart_customer(p_cart_id UUID, p_customer_id UUID DEFAULT NULL)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
  PERFORM public._require_sales_staff();
  IF p_customer_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM public.customers WHERE id = p_customer_id) THEN
    RAISE EXCEPTION 'customer not found';
  END IF;
  UPDATE public.pos_carts
  SET customer_id = p_customer_id, updated_at = now()
  WHERE id = p_cart_id AND status = 'open';
  IF NOT FOUND THEN RAISE EXCEPTION 'open cart not found'; END IF;
  RETURN p_cart_id;
END;
$$;

REVOKE ALL ON FUNCTION public.list_pos_customers(TEXT, INTEGER) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.create_pos_customer(TEXT, TEXT, TEXT, TEXT, TEXT, TEXT) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.update_pos_customer(UUID, TEXT, TEXT, TEXT, TEXT, TEXT, TEXT) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.list_pos_customer_garage(UUID) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.upsert_pos_customer_garage_vehicle(UUID, UUID, TEXT, TEXT, TEXT, TEXT, TEXT, TEXT, TEXT, BOOLEAN) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.set_pos_cart_customer(UUID, UUID) FROM PUBLIC;

GRANT EXECUTE ON FUNCTION public.list_pos_customers(TEXT, INTEGER) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.create_pos_customer(TEXT, TEXT, TEXT, TEXT, TEXT, TEXT) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.update_pos_customer(UUID, TEXT, TEXT, TEXT, TEXT, TEXT, TEXT) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.list_pos_customer_garage(UUID) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.upsert_pos_customer_garage_vehicle(UUID, UUID, TEXT, TEXT, TEXT, TEXT, TEXT, TEXT, TEXT, BOOLEAN) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.set_pos_cart_customer(UUID, UUID) TO authenticated, service_role;

COMMENT ON FUNCTION public.list_pos_customers IS 'Sales-staff POS customer search over non-sensitive contact/business fields.';
COMMENT ON FUNCTION public.upsert_pos_customer_garage_vehicle IS 'Sales-staff garage maintenance with canonical EPC fitment identifiers.';
COMMENT ON FUNCTION public.set_pos_cart_customer IS 'Changes named customer on an open POS cart without recreating sale lines.';
