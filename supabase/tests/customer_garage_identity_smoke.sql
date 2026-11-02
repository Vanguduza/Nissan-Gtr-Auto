-- Local seeded database only; this test rolls every fixture back.
BEGIN;
DO $$
DECLARE
  v_a UUID := 'c0000000-0000-4000-8000-0000000000a1';
  v_b UUID := 'c0000000-0000-4000-8000-0000000000b2';
  v_id UUID;
  v_legacy UUID;
  v_denied BOOLEAN := false;
BEGIN
  PERFORM set_config('request.jwt.claims', jsonb_build_object('sub',v_a,'role','authenticated')::TEXT, true);
  PERFORM set_config('request.jwt.claim.sub', v_a::TEXT, true);
  PERFORM set_config('request.jwt.claim.role', 'authenticated', true);
  v_id := public.upsert_customer_garage_vehicle(p_make=>'Nissan',p_model=>'Navara',
    p_is_primary=>true,p_chassis_code=>'D40',p_model_slug=>'navara-d40',p_vehicle_master_id=>'published:navara/d40');
  IF NOT EXISTS(SELECT 1 FROM public.customer_garage_vehicles WHERE id=v_id
    AND chassis_code='D40' AND model_slug='navara-d40' AND vehicle_master_id='published:navara/d40'
    AND customer_id=public._current_customer_id()) THEN
    RAISE EXCEPTION 'garage identity/ownership did not round-trip';
  END IF;
  -- Old clients omit all new fields; no ambiguous defaulted overload may remain.
  v_legacy := public.upsert_customer_garage_vehicle(NULL,'Nissan','X-Trail','T31','MR20',NULL,true);
  IF (SELECT count(*) FROM public.customer_garage_vehicles
      WHERE customer_id=public._current_customer_id() AND is_primary) <> 1 THEN
    RAISE EXCEPTION 'primary vehicle was not transferred atomically';
  END IF;
  PERFORM public.upsert_customer_garage_vehicle(p_id=>v_id,p_vehicle_master_id=>'published:updated');
  IF NOT EXISTS(SELECT 1 FROM public.customer_garage_vehicles WHERE id=v_id
      AND chassis_code='D40' AND model_slug='navara-d40' AND vehicle_master_id='published:updated') THEN
    RAISE EXCEPTION 'partial garage identity update lost fields';
  END IF;
  PERFORM set_config('request.jwt.claims', jsonb_build_object('sub',v_b,'role','authenticated')::TEXT, true);
  PERFORM set_config('request.jwt.claim.sub', v_b::TEXT, true);
  BEGIN
    PERFORM public.upsert_customer_garage_vehicle(p_id=>v_id,p_vehicle_master_id=>'peer-overwrite');
  EXCEPTION WHEN OTHERS THEN
    IF SQLERRM <> 'garage vehicle not found' THEN RAISE; END IF;
    v_denied := true;
  END;
  IF NOT v_denied THEN RAISE EXCEPTION 'peer garage update was allowed'; END IF;
  IF (SELECT vehicle_master_id FROM public.customer_garage_vehicles WHERE id=v_id) <> 'published:updated' THEN
    RAISE EXCEPTION 'peer update changed identity';
  END IF;
  IF has_function_privilege('anon',
      'public.upsert_customer_garage_vehicle(uuid,text,text,text,text,text,boolean,text,text,text)','EXECUTE') THEN
    RAISE EXCEPTION 'anonymous garage RPC execution is allowed';
  END IF;
  RAISE NOTICE 'customer_garage_identity_smoke ok';
END $$;
ROLLBACK;
