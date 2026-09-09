-- Customer garage parity with the canonical fitment columns introduced by POS.
-- Keeps customer own-row authorization unchanged while accepting the canonical chassis/model slug.
CREATE OR REPLACE FUNCTION public.upsert_customer_garage_vehicle(
  p_id UUID DEFAULT NULL,
  p_make TEXT DEFAULT NULL,
  p_model TEXT DEFAULT NULL,
  p_generation TEXT DEFAULT NULL,
  p_engine TEXT DEFAULT NULL,
  p_vin TEXT DEFAULT NULL,
  p_is_primary BOOLEAN DEFAULT false,
  p_chassis_code TEXT DEFAULT NULL,
  p_model_slug TEXT DEFAULT NULL
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
  IF v_cust IS NULL THEN RAISE EXCEPTION 'customer profile required'; END IF;
  IF COALESCE(p_is_primary, false) THEN
    UPDATE public.customer_garage_vehicles SET is_primary=false, updated_at=now()
    WHERE customer_id=v_cust AND is_primary AND (p_id IS NULL OR id IS DISTINCT FROM p_id);
  END IF;
  IF p_id IS NOT NULL THEN
    UPDATE public.customer_garage_vehicles SET
      make=COALESCE(p_make,make), model=COALESCE(p_model,model),
      generation=COALESCE(p_generation,generation), chassis_code=COALESCE(p_chassis_code,chassis_code),
      model_slug=COALESCE(p_model_slug,model_slug), engine=COALESCE(p_engine,engine),
      vin=COALESCE(p_vin,vin), is_primary=COALESCE(p_is_primary,is_primary), updated_at=now()
    WHERE id=p_id AND customer_id=v_cust RETURNING id INTO v_id;
    IF v_id IS NULL THEN RAISE EXCEPTION 'garage vehicle not found'; END IF;
    RETURN v_id;
  END IF;
  INSERT INTO public.customer_garage_vehicles
    (customer_id,make,model,generation,chassis_code,model_slug,engine,vin,is_primary)
  VALUES
    (v_cust,p_make,p_model,p_generation,p_chassis_code,p_model_slug,p_engine,p_vin,COALESCE(p_is_primary,false))
  RETURNING id INTO v_id;
  RETURN v_id;
END;
$$;
REVOKE ALL ON FUNCTION public.upsert_customer_garage_vehicle(UUID,TEXT,TEXT,TEXT,TEXT,TEXT,BOOLEAN,TEXT,TEXT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.upsert_customer_garage_vehicle(UUID,TEXT,TEXT,TEXT,TEXT,TEXT,BOOLEAN,TEXT,TEXT) TO authenticated, service_role;
