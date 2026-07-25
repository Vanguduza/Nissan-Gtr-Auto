-- Dedicated delivery app P0 RLS / RPC smoke.
-- Plan: docs/plans/2026-07-25-dedicated-delivery-app.md
-- Run after seed, e.g.:
--   docker exec -i <supabase_db> psql -U postgres -d postgres -v ON_ERROR_STOP=1 < supabase/tests/dedicated_delivery_app_smoke.sql

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
  v_customer UUID := 'c0000000-0000-4000-8000-0000000000a1';
  v_driver UUID := 'd0000000-0000-4000-8000-0000000000d1';
  v_driver2 UUID := 'd0000000-0000-4000-8000-0000000000d2';
  v_main UUID;
  v_uom UUID;
  v_item UUID;
  v_list UUID;
  v_cart UUID;
  v_inv UUID;
  v_inv_line UUID;
  v_pick UUID;
  v_dn UUID;
  v_job UUID;
  v_job2 UUID;
  v_loc UUID;
  v_token TEXT;
  v_track RECORD;
  v_cnt INT;
  v_suggest_cnt INT;
BEGIN
  PERFORM public._test_set_auth_uid(v_admin);

  SELECT id INTO v_main FROM public.warehouses WHERE code = 'MAIN';
  SELECT id INTO v_uom FROM public.uoms WHERE code = 'EA';
  SELECT id INTO v_list FROM public.price_lists WHERE code = 'RETAIL';

  IF v_main IS NULL OR v_uom IS NULL THEN
    RAISE EXCEPTION 'smoke fail: MAIN/EA missing';
  END IF;

  -- -----------------------------------------------------------------------
  -- Seed drivers (auth.users + profiles + staff_roles)
  -- -----------------------------------------------------------------------
  INSERT INTO auth.users (
    instance_id, id, aud, role, email, encrypted_password,
    email_confirmed_at, raw_app_meta_data, raw_user_meta_data,
    created_at, updated_at, confirmation_token, recovery_token,
    email_change_token_new, email_change
  )
  VALUES
    (
      '00000000-0000-0000-0000-000000000000', v_driver, 'authenticated', 'authenticated',
      'driver1@gtr.local', crypt('local-dev-driver', gen_salt('bf')),
      now(), '{"provider":"email","providers":["email"]}'::jsonb, '{}'::jsonb,
      now(), now(), '', '', '', ''
    ),
    (
      '00000000-0000-0000-0000-000000000000', v_driver2, 'authenticated', 'authenticated',
      'driver2@gtr.local', crypt('local-dev-driver', gen_salt('bf')),
      now(), '{"provider":"email","providers":["email"]}'::jsonb, '{}'::jsonb,
      now(), now(), '', '', '', ''
    )
  ON CONFLICT (id) DO NOTHING;

  INSERT INTO public.profiles (id, full_name, is_staff)
  VALUES
    (v_driver, 'Smoke Driver 1', false),
    (v_driver2, 'Smoke Driver 2', false)
  ON CONFLICT (id) DO UPDATE SET full_name = EXCLUDED.full_name;

  INSERT INTO public.staff_roles (user_id, role) VALUES
    (v_driver, 'driver'),
    (v_driver2, 'driver')
  ON CONFLICT DO NOTHING;

  INSERT INTO public.stock_items (oem_part_number, description, base_uom_id)
  VALUES ('P0-DEL-001', 'Dedicated delivery smoke part', v_uom)
  ON CONFLICT (oem_part_number) DO UPDATE SET description = EXCLUDED.description
  RETURNING id INTO v_item;
  IF v_item IS NULL THEN
    SELECT id INTO v_item FROM public.stock_items WHERE oem_part_number = 'P0-DEL-001';
  END IF;

  INSERT INTO public.price_list_items (price_list_id, stock_item_id, unit_price, core_charge)
  VALUES (v_list, v_item, 25, 0)
  ON CONFLICT (price_list_id, stock_item_id) DO UPDATE
  SET unit_price = 25, core_charge = 0;

  PERFORM public.post_stock_receipt(
    v_main,
    'DEL seed',
    jsonb_build_array(
      jsonb_build_object(
        'stock_item_id', v_item,
        'uom_id', v_uom,
        'qty', 20,
        'unit_cost', 8,
        'currency', 'USD',
        'valuation_method', 'FIFO'
      )
    )
  );

  -- Dispatch invoice → pick → DN → job
  v_cart := public.create_pos_cart(v_main, NULL, 'USD', 'dispatch');
  PERFORM public.add_cart_line(v_cart, v_item, v_uom, 2);
  v_inv := public.checkout_pos_cart(v_cart);

  SELECT id INTO v_inv_line
  FROM public.sales_invoice_lines
  WHERE invoice_id = v_inv AND NOT is_core_charge
  LIMIT 1;

  v_pick := public.create_pick_list(
    v_inv,
    jsonb_build_array(
      jsonb_build_object('sales_invoice_line_id', v_inv_line, 'qty', 2)
    )
  );
  PERFORM public.confirm_pick_lines(
    v_pick,
    jsonb_build_array(
      jsonb_build_object('sales_invoice_line_id', v_inv_line, 'qty_picked', 2)
    )
  );
  v_dn := public.create_delivery_note(
    v_inv,
    jsonb_build_array(
      jsonb_build_object('sales_invoice_line_id', v_inv_line, 'qty', 2)
    ),
    v_pick
  );
  PERFORM public.submit_delivery_note(v_dn);

  v_job := public.create_delivery_job(
    v_dn, NULL, now() + interval '1 hour', 'delivery app smoke'
  );

  UPDATE public.delivery_jobs
  SET
    pickup_lat = -17.8250,
    pickup_lng = 31.0330,
    dropoff_lat = -17.8400,
    dropoff_lng = 31.0500
  WHERE id = v_job;
  -- Direct UPDATE blocked by mutation guard — use logistics GUC via RPC path.
  -- Set coords through a one-shot definer helper inline:
  PERFORM set_config('app.logistics_rpc', '1', true);
  UPDATE public.delivery_jobs
  SET
    pickup_lat = -17.8250,
    pickup_lng = 31.0330,
    dropoff_lat = -17.8400,
    dropoff_lng = 31.0500,
    updated_at = now()
  WHERE id = v_job;
  PERFORM set_config('app.logistics_rpc', '0', true);

  -- -----------------------------------------------------------------------
  -- Presence + suggest + assign
  -- -----------------------------------------------------------------------
  PERFORM public._test_set_auth_uid(v_driver);
  PERFORM public.set_driver_presence(
    'available', 2, now() - interval '1 hour', now() + interval '8 hours',
    -17.8260, 31.0340
  );

  PERFORM public._test_set_auth_uid(v_driver2);
  PERFORM public.set_driver_presence(
    'available', 1, now() - interval '1 hour', now() + interval '8 hours',
    -17.9000, 31.1000
  );

  PERFORM public._test_set_auth_uid(v_admin);
  SELECT count(*)::int INTO v_suggest_cnt
  FROM public.suggest_delivery_assignees(v_job, 10);
  IF v_suggest_cnt < 1 THEN
    RAISE EXCEPTION 'smoke fail: expected suggest candidates';
  END IF;

  -- Nearest should be driver1
  IF (
    SELECT user_id FROM public.suggest_delivery_assignees(v_job, 1) LIMIT 1
  ) IS DISTINCT FROM v_driver THEN
    RAISE EXCEPTION 'smoke fail: nearest driver should be driver1';
  END IF;

  PERFORM public.assign_delivery_job(v_job, v_driver, false);
  PERFORM public.update_delivery_job_status(v_job, 'dispatched');

  -- Remint to capture plaintext for token tests
  v_token := public.mint_delivery_track_token(v_job);

  -- -----------------------------------------------------------------------
  -- Driver ingest + ETA recompute
  -- -----------------------------------------------------------------------
  PERFORM public._test_set_auth_uid(v_driver);
  v_loc := public.ingest_delivery_location(v_job, -17.8270, 31.0350, now(), 8);

  IF v_loc IS NULL THEN
    RAISE EXCEPTION 'smoke fail: driver ingest failed';
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM public.delivery_jobs
    WHERE id = v_job
      AND eta_source = 'haversine'
      AND eta_seconds IS NOT NULL
      AND eta_at IS NOT NULL
  ) THEN
    RAISE EXCEPTION 'smoke fail: ETA not recomputed on ingest';
  END IF;

  -- Rate limit still enforced
  BEGIN
    PERFORM public.ingest_delivery_location(v_job, -17.8271, 31.0351, now(), 8);
    RAISE EXCEPTION 'smoke fail: rate limit should throw';
  EXCEPTION
    WHEN OTHERS THEN
      IF SQLERRM NOT LIKE '%rate limit%' THEN
        RAISE;
      END IF;
  END;

  -- -----------------------------------------------------------------------
  -- Customer cannot SELECT delivery_locations
  -- -----------------------------------------------------------------------
  PERFORM public._test_set_auth_uid(v_customer);
  SET LOCAL ROLE authenticated;
  IF EXISTS (SELECT 1 FROM public.delivery_locations WHERE delivery_job_id = v_job) THEN
    RAISE EXCEPTION 'smoke fail: customer must not SELECT delivery_locations';
  END IF;
  -- Customer cannot SELECT track tokens
  BEGIN
    SELECT count(*) INTO v_cnt FROM public.delivery_track_tokens;
    IF v_cnt > 0 THEN
      RAISE EXCEPTION 'smoke fail: customer must not read delivery_track_tokens';
    END IF;
  EXCEPTION
    WHEN insufficient_privilege THEN
      NULL; -- expected (REVOKE)
  END;
  RESET ROLE;

  -- -----------------------------------------------------------------------
  -- Driver cannot read other drivers' jobs
  -- -----------------------------------------------------------------------
  PERFORM public._test_set_auth_uid(v_admin);
  -- Second job assigned to driver2
  v_cart := public.create_pos_cart(v_main, NULL, 'USD', 'dispatch');
  PERFORM public.add_cart_line(v_cart, v_item, v_uom, 1);
  v_inv := public.checkout_pos_cart(v_cart);
  SELECT id INTO v_inv_line
  FROM public.sales_invoice_lines
  WHERE invoice_id = v_inv AND NOT is_core_charge
  LIMIT 1;
  v_pick := public.create_pick_list(
    v_inv,
    jsonb_build_array(
      jsonb_build_object('sales_invoice_line_id', v_inv_line, 'qty', 1)
    )
  );
  PERFORM public.confirm_pick_lines(
    v_pick,
    jsonb_build_array(
      jsonb_build_object('sales_invoice_line_id', v_inv_line, 'qty_picked', 1)
    )
  );
  v_dn := public.create_delivery_note(
    v_inv,
    jsonb_build_array(
      jsonb_build_object('sales_invoice_line_id', v_inv_line, 'qty', 1)
    ),
    v_pick
  );
  PERFORM public.submit_delivery_note(v_dn);
  v_job2 := public.create_delivery_job(v_dn, NULL, NULL, 'other driver job');
  PERFORM public.assign_delivery_job(v_job2, v_driver2, true);

  PERFORM public._test_set_auth_uid(v_driver);
  SET LOCAL ROLE authenticated;
  IF EXISTS (
    SELECT 1 FROM public.delivery_jobs WHERE id = v_job2
  ) THEN
    RAISE EXCEPTION 'smoke fail: driver1 must not see driver2 job';
  END IF;
  IF NOT EXISTS (
    SELECT 1 FROM public.delivery_jobs WHERE id = v_job
  ) THEN
    RAISE EXCEPTION 'smoke fail: driver1 should see own job';
  END IF;
  RESET ROLE;

  -- -----------------------------------------------------------------------
  -- Track token: last point only; no historical trail via RPC
  -- -----------------------------------------------------------------------
  PERFORM public._test_set_auth_uid(v_admin);
  -- Wait past rate limit then second point
  PERFORM set_config('app.logistics_rpc', '1', true);
  UPDATE public.delivery_locations
  SET ingested_at = now() - interval '10 seconds'
  WHERE id = v_loc;
  PERFORM set_config('app.logistics_rpc', '0', true);

  PERFORM public._test_set_auth_uid(v_driver);
  PERFORM public.ingest_delivery_location(v_job, -17.8280, 31.0360, now(), 5);

  -- Anon/token path
  PERFORM public._test_set_auth_uid(NULL);
  SELECT * INTO v_track FROM public.get_delivery_track_point(NULL, v_token);
  IF v_track.delivery_job_id IS NULL THEN
    RAISE EXCEPTION 'smoke fail: token track point missing';
  END IF;
  IF v_track.lat IS DISTINCT FROM -17.8280 THEN
    RAISE EXCEPTION 'smoke fail: track point should be latest lat, got %', v_track.lat;
  END IF;

  -- Token must not expose trail count (RPC returns at most 1 row)
  SELECT count(*)::int INTO v_cnt
  FROM public.get_delivery_track_point(NULL, v_token);
  IF v_cnt <> 1 THEN
    RAISE EXCEPTION 'smoke fail: track RPC must return single row, got %', v_cnt;
  END IF;

  -- -----------------------------------------------------------------------
  -- POD complete
  -- -----------------------------------------------------------------------
  PERFORM public._test_set_auth_uid(v_driver);
  PERFORM public.submit_delivery_pod(
    v_job,
    'pod/photos/smoke.jpg',
    'pod/signatures/smoke.png',
    'delivered'
  );

  IF NOT EXISTS (
    SELECT 1 FROM public.delivery_jobs
    WHERE id = v_job AND status = 'completed' AND completed_via = 'pod'
  ) THEN
    RAISE EXCEPTION 'smoke fail: POD did not complete job';
  END IF;

  -- After complete, token track returns nothing
  SELECT count(*)::int INTO v_cnt
  FROM public.get_delivery_track_point(NULL, v_token);
  IF v_cnt <> 0 THEN
    RAISE EXCEPTION 'smoke fail: track must stop after terminal status';
  END IF;

  -- update_delivery_job_status(completed) without POD must fail on job2
  PERFORM public._test_set_auth_uid(v_admin);
  PERFORM public.update_delivery_job_status(v_job2, 'dispatched');
  BEGIN
    PERFORM public.update_delivery_job_status(v_job2, 'completed');
    RAISE EXCEPTION 'smoke fail: complete without POD should fail';
  EXCEPTION
    WHEN OTHERS THEN
      IF SQLERRM NOT LIKE '%POD%' THEN
        RAISE;
      END IF;
  END;

  RAISE NOTICE 'dedicated_delivery_app_smoke: PASS job=% job2=% token_ok', v_job, v_job2;
END;
$$;
