-- Smoke: get_customer_order returns active_delivery_job_id for owning customer.
-- Peer denial; no delivery_locations SELECT; null after terminal job.
-- Run after seed, e.g.:
--   docker exec -i <supabase_db> psql -U postgres -d postgres -v ON_ERROR_STOP=1 \
--     < supabase/tests/customer_order_active_delivery_job_smoke.sql

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
  v_cust_a_user UUID := 'c0000000-0000-4000-8000-0000000000a1';
  v_cust_b_user UUID := 'c0000000-0000-4000-8000-0000000000b2';
  v_main UUID;
  v_uom UUID;
  v_list UUID;
  v_item UUID;
  v_cust_a UUID;
  v_cart UUID;
  v_inv UUID;
  v_inv_line UUID;
  v_pick UUID;
  v_dn UUID;
  v_job_pending UUID;
  v_job_dispatched UUID;
  v_order JSONB;
  v_loc_cnt INT;
BEGIN
  INSERT INTO auth.users (
    instance_id, id, aud, role, email, encrypted_password, email_confirmed_at,
    raw_app_meta_data, raw_user_meta_data, created_at, updated_at,
    confirmation_token, recovery_token, email_change_token_new, email_change
  )
  VALUES
    (
      '00000000-0000-0000-0000-000000000000', v_cust_a_user,
      'authenticated', 'authenticated', 'storefront-a@gtr.local',
      crypt('local-dev-customer', gen_salt('bf')), now(),
      '{"provider":"email","providers":["email"]}'::jsonb,
      '{"full_name":"Storefront A"}'::jsonb, now(), now(), '', '', '', ''
    ),
    (
      '00000000-0000-0000-0000-000000000000', v_cust_b_user,
      'authenticated', 'authenticated', 'storefront-b@gtr.local',
      crypt('local-dev-customer', gen_salt('bf')), now(),
      '{"provider":"email","providers":["email"]}'::jsonb,
      '{"full_name":"Storefront B"}'::jsonb, now(), now(), '', '', '', ''
    )
  ON CONFLICT (id) DO NOTHING;

  INSERT INTO public.profiles (id, full_name, is_staff)
  VALUES
    (v_cust_a_user, 'Storefront A', false),
    (v_cust_b_user, 'Storefront B', false)
  ON CONFLICT (id) DO UPDATE SET full_name = EXCLUDED.full_name, is_staff = false;

  PERFORM public._test_set_auth_uid(v_admin);

  SELECT id INTO v_main FROM public.warehouses WHERE code = 'MAIN';
  SELECT id INTO v_uom FROM public.uoms WHERE code = 'EA';
  SELECT id INTO v_list FROM public.price_lists WHERE code = 'RETAIL';
  IF v_main IS NULL OR v_uom IS NULL OR v_list IS NULL THEN
    RAISE EXCEPTION 'smoke fail: seed MAIN/EA/RETAIL missing';
  END IF;

  INSERT INTO public.stock_items (oem_part_number, description, base_uom_id)
  VALUES ('P-SF-TRACK-001', 'Storefront track order part', v_uom)
  ON CONFLICT (oem_part_number) DO UPDATE SET description = EXCLUDED.description
  RETURNING id INTO v_item;
  IF v_item IS NULL THEN
    SELECT id INTO v_item FROM public.stock_items WHERE oem_part_number = 'P-SF-TRACK-001';
  END IF;

  INSERT INTO public.price_list_items (price_list_id, stock_item_id, unit_price, core_charge)
  VALUES (v_list, v_item, 20, 0)
  ON CONFLICT (price_list_id, stock_item_id) DO UPDATE
  SET unit_price = 20, core_charge = 0;

  PERFORM public.post_stock_receipt(
    v_main,
    'SF track seed',
    jsonb_build_array(
      jsonb_build_object(
        'stock_item_id', v_item, 'uom_id', v_uom, 'qty', 20,
        'unit_cost', 5, 'currency', 'USD', 'valuation_method', 'FIFO'
      )
    )
  );

  SELECT id INTO v_cust_a FROM public.customers WHERE profile_id = v_cust_a_user
  ORDER BY created_at ASC LIMIT 1;
  IF v_cust_a IS NULL THEN
    INSERT INTO public.customers (display_name, currency, profile_id)
    VALUES ('Storefront Customer A', 'USD', v_cust_a_user)
    RETURNING id INTO v_cust_a;
  END IF;

  -- Customer A checkout (dispatch)
  PERFORM public._test_set_auth_uid(v_cust_a_user);
  v_cart := public.create_customer_cart(
    v_main, 'USD'::public.currency_code, 'dispatch'::public.fulfillment_mode, 1
  );
  PERFORM public.add_customer_cart_line(v_cart, v_item, v_uom, 2);
  v_inv := public.checkout_customer_cart(v_cart);

  -- No job yet → null
  v_order := public.get_customer_order(v_inv);
  IF v_order ? 'active_delivery_job_id' IS FALSE THEN
    RAISE EXCEPTION 'smoke fail: active_delivery_job_id key missing %', v_order;
  END IF;
  IF (v_order ->> 'active_delivery_job_id') IS NOT NULL THEN
    RAISE EXCEPTION 'smoke fail: expected null active job before DN/job';
  END IF;

  -- Staff: pick → DN → two jobs (pending + dispatched); prefer dispatched
  PERFORM public._test_set_auth_uid(v_admin);
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

  v_job_pending := public.create_delivery_job(v_dn, NULL, NULL, 'pending preference');
  v_job_dispatched := public.create_delivery_job(v_dn, NULL, NULL, 'dispatched preference');
  PERFORM public.update_delivery_job_status(v_job_dispatched, 'dispatched');

  PERFORM public._test_set_auth_uid(v_cust_a_user);
  v_order := public.get_customer_order(v_inv);
  IF (v_order ->> 'active_delivery_job_id')::uuid IS DISTINCT FROM v_job_dispatched THEN
    RAISE EXCEPTION 'smoke fail: expected dispatched job % got %',
      v_job_dispatched, v_order ->> 'active_delivery_job_id';
  END IF;

  -- Customer must not SELECT GPS trail
  SET LOCAL ROLE authenticated;
  SELECT count(*)::int INTO v_loc_cnt FROM public.delivery_locations;
  IF v_loc_cnt <> 0 THEN
    RAISE EXCEPTION 'smoke fail: customer SELECT delivery_locations leaked % rows', v_loc_cnt;
  END IF;
  RESET ROLE;

  -- Peer denial
  PERFORM public._test_set_auth_uid(v_cust_b_user);
  BEGIN
    PERFORM public.get_customer_order(v_inv);
    RAISE EXCEPTION 'smoke fail: peer get_customer_order allowed';
  EXCEPTION
    WHEN OTHERS THEN
      IF SQLERRM LIKE 'smoke fail:%' THEN RAISE; END IF;
      IF SQLERRM NOT ILIKE '%not authorized%' THEN
        RAISE EXCEPTION 'smoke fail: unexpected peer order error: %', SQLERRM;
      END IF;
  END;

  -- Terminal → null
  PERFORM public._test_set_auth_uid(v_admin);
  PERFORM public.fail_delivery_job(
    v_job_dispatched,
    'refused'::public.delivery_failure_reason,
    'customer refused',
    false
  );
  PERFORM public.fail_delivery_job(
    v_job_pending,
    'other'::public.delivery_failure_reason,
    'cancelled after peer job failed',
    false
  );

  PERFORM public._test_set_auth_uid(v_cust_a_user);
  v_order := public.get_customer_order(v_inv);
  IF (v_order ->> 'active_delivery_job_id') IS NOT NULL THEN
    RAISE EXCEPTION 'smoke fail: active job should be null after terminal %', v_order;
  END IF;

  RAISE NOTICE 'customer_order_active_delivery_job_smoke: PASS inv=%', v_inv;
END;
$$;
