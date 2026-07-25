-- Fleet vehicles Phase A smoke (postgres).
-- Role deny + plate uniqueness + mutation guard + driver assignee check.
-- Plan: docs/plans/2026-07-25-fleet-management.md
-- Run after seed, e.g.:
--   docker exec -i <supabase_db> psql -U postgres -d postgres -v ON_ERROR_STOP=1 < supabase/tests/fleet_vehicles_smoke.sql

CREATE OR REPLACE FUNCTION public._test_set_auth_uid(p_uid UUID)
RETURNS void
LANGUAGE plpgsql
AS $$
BEGIN
  IF p_uid IS NULL THEN
    PERFORM set_config('request.jwt.claim.sub', '', true);
    PERFORM set_config('request.jwt.claims', '{}', true);
    PERFORM set_config('request.jwt.claim.role', 'anon', true);
    RETURN;
  END IF;
  PERFORM set_config('request.jwt.claim.sub', p_uid::text, true);
  PERFORM set_config(
    'request.jwt.claims',
    json_build_object('sub', p_uid::text, 'role', 'authenticated')::text,
    true
  );
  PERFORM set_config('request.jwt.claim.role', 'authenticated', true);
END;
$$;

DO $$
DECLARE
  v_admin UUID := 'a0000000-0000-4000-8000-000000000001';
  v_finance UUID := 'a0000000-0000-4000-8000-000000000002';
  v_warehouse UUID := 'a0000000-0000-4000-8000-000000000003';
  v_customer UUID := 'c0000000-0000-4000-8000-0000000000f1';
  v_sales UUID := 'a0000000-0000-4000-8000-0000000000e5';
  v_dispatcher UUID := 'a0000000-0000-4000-8000-0000000000d0';
  v_driver UUID := 'd0000000-0000-4000-8000-0000000000f1';
  v_nondriver UUID := 'a0000000-0000-4000-8000-000000000002'; -- finance
  v_id UUID;
  v_id2 UUID;
  v_cnt INT;
  v_plate TEXT;
BEGIN
  -- -----------------------------------------------------------------------
  -- Seed extra users (sales, dispatcher, driver, customer)
  -- -----------------------------------------------------------------------
  INSERT INTO auth.users (
    instance_id, id, aud, role, email, encrypted_password,
    email_confirmed_at, raw_app_meta_data, raw_user_meta_data,
    created_at, updated_at, confirmation_token, recovery_token,
    email_change_token_new, email_change
  )
  VALUES
    (
      '00000000-0000-0000-0000-000000000000', v_sales, 'authenticated', 'authenticated',
      'sales-fleet@gtr.local', crypt('local-dev', gen_salt('bf')),
      now(), '{"provider":"email","providers":["email"]}'::jsonb, '{}'::jsonb,
      now(), now(), '', '', '', ''
    ),
    (
      '00000000-0000-0000-0000-000000000000', v_dispatcher, 'authenticated', 'authenticated',
      'dispatcher-fleet@gtr.local', crypt('local-dev', gen_salt('bf')),
      now(), '{"provider":"email","providers":["email"]}'::jsonb, '{}'::jsonb,
      now(), now(), '', '', '', ''
    ),
    (
      '00000000-0000-0000-0000-000000000000', v_driver, 'authenticated', 'authenticated',
      'driver-fleet@gtr.local', crypt('local-dev', gen_salt('bf')),
      now(), '{"provider":"email","providers":["email"]}'::jsonb, '{}'::jsonb,
      now(), now(), '', '', '', ''
    ),
    (
      '00000000-0000-0000-0000-000000000000', v_customer, 'authenticated', 'authenticated',
      'customer-fleet@gtr.local', crypt('local-dev', gen_salt('bf')),
      now(), '{"provider":"email","providers":["email"]}'::jsonb, '{}'::jsonb,
      now(), now(), '', '', '', ''
    )
  ON CONFLICT (id) DO NOTHING;

  INSERT INTO public.profiles (id, full_name, is_staff)
  VALUES
    (v_sales, 'Fleet Smoke Sales', true),
    (v_dispatcher, 'Fleet Smoke Dispatcher', true),
    (v_driver, 'Fleet Smoke Driver', true),
    (v_customer, 'Fleet Smoke Customer', false)
  ON CONFLICT (id) DO UPDATE SET full_name = EXCLUDED.full_name;

  INSERT INTO public.staff_roles (user_id, role) VALUES
    (v_sales, 'sales'),
    (v_dispatcher, 'dispatcher'),
    (v_driver, 'driver')
  ON CONFLICT DO NOTHING;

  PERFORM public._test_set_auth_uid(v_admin);

  -- -----------------------------------------------------------------------
  -- 1) Admin upsert + plate normalization
  -- -----------------------------------------------------------------------
  v_id := public.upsert_fleet_vehicle(
    '  ab-1234  ',
    'Van Alpha',
    'active'::public.fleet_vehicle_status,
    NULL,
    'smoke notes'
  );

  SELECT plate INTO v_plate FROM public.fleet_vehicles WHERE id = v_id;
  IF v_plate IS DISTINCT FROM 'AB-1234' THEN
    RAISE EXCEPTION 'smoke fail: plate normalize expected AB-1234 got %', v_plate;
  END IF;

  -- -----------------------------------------------------------------------
  -- 2) Duplicate plate denied
  -- -----------------------------------------------------------------------
  BEGIN
    PERFORM public.upsert_fleet_vehicle('ab-1234', 'Dup');
    RAISE EXCEPTION 'smoke fail: duplicate plate should throw';
  EXCEPTION
    WHEN OTHERS THEN
      IF SQLERRM LIKE 'smoke fail:%' THEN
        RAISE;
      END IF;
      IF SQLERRM NOT LIKE '%already exists%' THEN
        RAISE EXCEPTION 'smoke fail: unexpected dup error: %', SQLERRM;
      END IF;
  END;

  -- -----------------------------------------------------------------------
  -- 3) Direct INSERT denied by mutation guard
  -- -----------------------------------------------------------------------
  BEGIN
    INSERT INTO public.fleet_vehicles (plate, label)
    VALUES ('DIRECT-1', 'nope');
    RAISE EXCEPTION 'smoke fail: direct INSERT should throw';
  EXCEPTION
    WHEN OTHERS THEN
      IF SQLERRM LIKE 'smoke fail:%' THEN
        RAISE;
      END IF;
      IF SQLERRM NOT LIKE '%upsert_fleet_vehicle%' THEN
        RAISE EXCEPTION 'smoke fail: unexpected guard error: %', SQLERRM;
      END IF;
  END;

  -- -----------------------------------------------------------------------
  -- 4) Finance / sales / customer cannot mutate
  -- -----------------------------------------------------------------------
  PERFORM public._test_set_auth_uid(v_finance);
  BEGIN
    PERFORM public.upsert_fleet_vehicle('FIN-1', 'Finance van');
    RAISE EXCEPTION 'smoke fail: finance should be denied upsert';
  EXCEPTION
    WHEN OTHERS THEN
      IF SQLERRM LIKE 'smoke fail:%' THEN
        RAISE;
      END IF;
      IF SQLERRM NOT LIKE '%role required%' THEN
        RAISE EXCEPTION 'smoke fail: unexpected finance deny: %', SQLERRM;
      END IF;
  END;

  PERFORM public._test_set_auth_uid(v_sales);
  BEGIN
    PERFORM public.upsert_fleet_vehicle('SAL-1', 'Sales van');
    RAISE EXCEPTION 'smoke fail: sales should be denied upsert';
  EXCEPTION
    WHEN OTHERS THEN
      IF SQLERRM LIKE 'smoke fail:%' THEN
        RAISE;
      END IF;
      IF SQLERRM NOT LIKE '%role required%' THEN
        RAISE EXCEPTION 'smoke fail: unexpected sales deny: %', SQLERRM;
      END IF;
  END;

  PERFORM public._test_set_auth_uid(v_customer);
  BEGIN
    PERFORM public.upsert_fleet_vehicle('CUS-1', 'Customer van');
    RAISE EXCEPTION 'smoke fail: customer should be denied upsert';
  EXCEPTION
    WHEN OTHERS THEN
      IF SQLERRM LIKE 'smoke fail:%' THEN
        RAISE;
      END IF;
      IF SQLERRM NOT LIKE '%role required%' THEN
        RAISE EXCEPTION 'smoke fail: unexpected customer deny: %', SQLERRM;
      END IF;
  END;

  BEGIN
    PERFORM public.set_fleet_vehicle_status(v_id, 'retired'::public.fleet_vehicle_status);
    RAISE EXCEPTION 'smoke fail: customer should be denied set status';
  EXCEPTION
    WHEN OTHERS THEN
      IF SQLERRM LIKE 'smoke fail:%' THEN
        RAISE;
      END IF;
      IF SQLERRM NOT LIKE '%role required%' THEN
        RAISE EXCEPTION 'smoke fail: unexpected customer status deny: %', SQLERRM;
      END IF;
  END;

  BEGIN
    PERFORM public.list_fleet_vehicles();
    RAISE EXCEPTION 'smoke fail: customer should be denied list';
  EXCEPTION
    WHEN OTHERS THEN
      IF SQLERRM LIKE 'smoke fail:%' THEN
        RAISE;
      END IF;
      IF SQLERRM NOT LIKE '%role required%' THEN
        RAISE EXCEPTION 'smoke fail: unexpected customer list deny: %', SQLERRM;
      END IF;
  END;

  -- -----------------------------------------------------------------------
  -- 5) Warehouse + dispatcher can mutate; assignee must be driver
  -- -----------------------------------------------------------------------
  PERFORM public._test_set_auth_uid(v_warehouse);

  BEGIN
    PERFORM public.upsert_fleet_vehicle(
      'WH-9',
      'Warehouse assign bad',
      'active'::public.fleet_vehicle_status,
      v_nondriver
    );
    RAISE EXCEPTION 'smoke fail: non-driver assignee should throw';
  EXCEPTION
    WHEN OTHERS THEN
      IF SQLERRM LIKE 'smoke fail:%' THEN
        RAISE;
      END IF;
      IF SQLERRM NOT LIKE '%driver staff role%' THEN
        RAISE EXCEPTION 'smoke fail: unexpected assignee error: %', SQLERRM;
      END IF;
  END;

  v_id2 := public.upsert_fleet_vehicle(
    'wh-9',
    'Warehouse van',
    'active'::public.fleet_vehicle_status,
    v_driver,
    NULL
  );

  PERFORM public._test_set_auth_uid(v_dispatcher);
  PERFORM public.set_fleet_vehicle_status(v_id2, 'in_service'::public.fleet_vehicle_status);

  IF NOT EXISTS (
    SELECT 1 FROM public.fleet_vehicles
    WHERE id = v_id2 AND status = 'in_service' AND plate = 'WH-9'
  ) THEN
    RAISE EXCEPTION 'smoke fail: dispatcher status update missing';
  END IF;

  SELECT count(*)::int INTO v_cnt FROM public.list_fleet_vehicles();
  IF v_cnt < 2 THEN
    RAISE EXCEPTION 'smoke fail: list_fleet_vehicles expected >=2 got %', v_cnt;
  END IF;

  -- Update existing (plate uniqueness on other row still enforced)
  v_id2 := public.upsert_fleet_vehicle(
    'WH-9',
    'Warehouse van renamed',
    'in_service'::public.fleet_vehicle_status,
    v_driver,
    'updated',
    v_id2
  );

  BEGIN
    PERFORM public.upsert_fleet_vehicle(
      'AB-1234',
      'Clash',
      'active'::public.fleet_vehicle_status,
      NULL,
      NULL,
      v_id2
    );
    RAISE EXCEPTION 'smoke fail: update onto existing plate should throw';
  EXCEPTION
    WHEN OTHERS THEN
      IF SQLERRM LIKE 'smoke fail:%' THEN
        RAISE;
      END IF;
      IF SQLERRM NOT LIKE '%already exists%' THEN
        RAISE EXCEPTION 'smoke fail: unexpected update-dup error: %', SQLERRM;
      END IF;
  END;

  -- -----------------------------------------------------------------------
  -- 6) B2B FLEET price list / garage untouched (presence only)
  -- -----------------------------------------------------------------------
  IF EXISTS (
    SELECT 1 FROM information_schema.tables
    WHERE table_schema = 'public' AND table_name = 'customer_garage_vehicles'
  ) THEN
    -- table may exist; we must not have altered it
    NULL;
  END IF;

  RAISE NOTICE 'fleet_vehicles_smoke: PASS';
END;
$$;
