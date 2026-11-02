-- Persist selected published vehicle identity without changing own-customer authorization.
ALTER TABLE public.customer_garage_vehicles
  ADD COLUMN IF NOT EXISTS chassis_code TEXT,
  ADD COLUMN IF NOT EXISTS model_slug TEXT,
  ADD COLUMN IF NOT EXISTS vehicle_master_id TEXT;

COMMENT ON COLUMN public.customer_garage_vehicles.vehicle_master_id IS
  'Opaque published catalogue vehicle key; text, not an assumed UUID.';

ALTER TABLE public.customer_garage_vehicles DROP CONSTRAINT IF EXISTS garage_vin_or_fitment;
ALTER TABLE public.customer_garage_vehicles ADD CONSTRAINT garage_vin_or_fitment CHECK (
  vin IS NOT NULL OR make IS NOT NULL OR model IS NOT NULL OR generation IS NOT NULL
  OR engine IS NOT NULL OR chassis_code IS NOT NULL OR model_slug IS NOT NULL
  OR vehicle_master_id IS NOT NULL
);

-- Keep one defaulted RPC signature so PostgREST can resolve old named payloads.
DROP FUNCTION IF EXISTS public.upsert_customer_garage_vehicle(UUID,TEXT,TEXT,TEXT,TEXT,TEXT,BOOLEAN);
CREATE OR REPLACE FUNCTION public.upsert_customer_garage_vehicle(
  p_id UUID DEFAULT NULL,
  p_make TEXT DEFAULT NULL,
  p_model TEXT DEFAULT NULL,
  p_generation TEXT DEFAULT NULL,
  p_engine TEXT DEFAULT NULL,
  p_vin TEXT DEFAULT NULL,
  p_is_primary BOOLEAN DEFAULT false,
  p_chassis_code TEXT DEFAULT NULL,
  p_model_slug TEXT DEFAULT NULL,
  p_vehicle_master_id TEXT DEFAULT NULL
)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
  v_customer UUID := public._current_customer_id();
  v_id UUID;
BEGIN
  IF v_customer IS NULL THEN RAISE EXCEPTION 'customer profile required'; END IF;
  -- Serialize primary selection for this customer; never lock or mutate a peer's garage.
  PERFORM 1 FROM public.customers WHERE id = v_customer FOR UPDATE;
  IF p_id IS NOT NULL THEN
    PERFORM 1 FROM public.customer_garage_vehicles
      WHERE id = p_id AND customer_id = v_customer FOR UPDATE;
    IF NOT FOUND THEN RAISE EXCEPTION 'garage vehicle not found'; END IF;
  END IF;
  IF COALESCE(p_is_primary, false) THEN
    UPDATE public.customer_garage_vehicles SET is_primary = false, updated_at = now()
      WHERE customer_id = v_customer AND is_primary AND (p_id IS NULL OR id <> p_id);
  END IF;
  IF p_id IS NOT NULL THEN
    UPDATE public.customer_garage_vehicles SET
      make = COALESCE(p_make, make), model = COALESCE(p_model, model),
      generation = COALESCE(p_generation, generation), engine = COALESCE(p_engine, engine),
      vin = COALESCE(p_vin, vin), is_primary = COALESCE(p_is_primary, is_primary),
      chassis_code = COALESCE(NULLIF(btrim(p_chassis_code), ''), chassis_code),
      model_slug = COALESCE(NULLIF(btrim(p_model_slug), ''), model_slug),
      vehicle_master_id = COALESCE(NULLIF(btrim(p_vehicle_master_id), ''), vehicle_master_id),
      updated_at = now()
    WHERE id = p_id AND customer_id = v_customer RETURNING id INTO v_id;
  ELSE
    INSERT INTO public.customer_garage_vehicles(
      customer_id, make, model, generation, engine, vin, is_primary,
      chassis_code, model_slug, vehicle_master_id
    ) VALUES (
      v_customer, p_make, p_model, p_generation, p_engine, p_vin, COALESCE(p_is_primary, false),
      NULLIF(btrim(p_chassis_code), ''), NULLIF(btrim(p_model_slug), ''),
      NULLIF(btrim(p_vehicle_master_id), '')
    ) RETURNING id INTO v_id;
  END IF;
  RETURN v_id;
END;
$$;
REVOKE ALL ON FUNCTION public.upsert_customer_garage_vehicle(UUID,TEXT,TEXT,TEXT,TEXT,TEXT,BOOLEAN,TEXT,TEXT,TEXT)
  FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.upsert_customer_garage_vehicle(UUID,TEXT,TEXT,TEXT,TEXT,TEXT,BOOLEAN,TEXT,TEXT,TEXT)
  TO authenticated, service_role;
