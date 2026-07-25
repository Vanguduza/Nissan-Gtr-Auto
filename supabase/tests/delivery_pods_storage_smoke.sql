-- delivery-pods Storage RLS smoke.
-- Run after seed + dedicated delivery migrations, e.g.:
--   docker exec -i <supabase_db> psql -U postgres -d postgres -v ON_ERROR_STOP=1 < supabase/tests/delivery_pods_storage_smoke.sql

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
  v_photo TEXT;
  v_sig TEXT;
  v_obj UUID;
  v_cnt INT;
BEGIN
  IF NOT EXISTS (SELECT 1 FROM storage.buckets WHERE id = 'delivery-pods' AND NOT public) THEN
    RAISE EXCEPTION 'smoke fail: delivery-pods private bucket missing';
  END IF;

  PERFORM public._test_set_auth_uid(v_admin);

  SELECT id INTO v_main FROM public.warehouses WHERE code = 'MAIN';
  SELECT id INTO v_uom FROM public.uoms WHERE code = 'EA';
  SELECT id INTO v_list FROM public.price_lists WHERE code = 'RETAIL';

  INSERT INTO auth.users (
    instance_id, id, aud, role, email, encrypted_password,
    email_confirmed_at, raw_app_meta_data, raw_user_meta_data,
    created_at, updated_at, confirmation_token, recovery_token,
    email_change_token_new, email_change
  )
  VALUES
    (
      '00000000-0000-0000-0000-000000000000', v_driver, 'authenticated', 'authenticated',
      'driver1-pod@gtr.local', crypt('local-dev-driver', gen_salt('bf')),
      now(), '{"provider":"email","providers":["email"]}'::jsonb, '{}'::jsonb,
      now(), now(), '', '', '', ''
    ),
    (
      '00000000-0000-0000-0000-000000000000', v_driver2, 'authenticated', 'authenticated',
      'driver2-pod@gtr.local', crypt('local-dev-driver', gen_salt('bf')),
      now(), '{"provider":"email","providers":["email"]}'::jsonb, '{}'::jsonb,
      now(), now(), '', '', '', ''
    )
  ON CONFLICT (id) DO NOTHING;

  INSERT INTO public.profiles (id, full_name, is_staff)
  VALUES (v_driver, 'POD Driver 1', false), (v_driver2, 'POD Driver 2', false)
  ON CONFLICT (id) DO UPDATE SET full_name = EXCLUDED.full_name;

  INSERT INTO public.staff_roles (user_id, role) VALUES
    (v_driver, 'driver'),
    (v_driver2, 'driver')
  ON CONFLICT DO NOTHING;

  INSERT INTO public.stock_items (oem_part_number, description, base_uom_id)
  VALUES ('P0-POD-STOR', 'POD storage smoke part', v_uom)
  ON CONFLICT (oem_part_number) DO UPDATE SET description = EXCLUDED.description
  RETURNING id INTO v_item;
  IF v_item IS NULL THEN
    SELECT id INTO v_item FROM public.stock_items WHERE oem_part_number = 'P0-POD-STOR';
  END IF;

  INSERT INTO public.price_list_items (price_list_id, stock_item_id, unit_price, core_charge)
  VALUES (v_list, v_item, 10, 0)
  ON CONFLICT (price_list_id, stock_item_id) DO UPDATE SET unit_price = 10;

  PERFORM public.post_stock_receipt(
    v_main,
    'POD stor seed',
    jsonb_build_array(
      jsonb_build_object(
        'stock_item_id', v_item, 'uom_id', v_uom, 'qty', 5,
        'unit_cost', 3, 'currency', 'USD', 'valuation_method', 'FIFO'
      )
    )
  );

  v_cart := public.create_pos_cart(v_main, NULL, 'USD', 'dispatch');
  PERFORM public.add_cart_line(v_cart, v_item, v_uom, 1);
  v_inv := public.checkout_pos_cart(v_cart);
  SELECT id INTO v_inv_line FROM public.sales_invoice_lines
  WHERE invoice_id = v_inv AND NOT is_core_charge LIMIT 1;
  v_pick := public.create_pick_list(
    v_inv,
    jsonb_build_array(jsonb_build_object('sales_invoice_line_id', v_inv_line, 'qty', 1))
  );
  PERFORM public.confirm_pick_lines(
    v_pick,
    jsonb_build_array(jsonb_build_object('sales_invoice_line_id', v_inv_line, 'qty_picked', 1))
  );
  v_dn := public.create_delivery_note(
    v_inv,
    jsonb_build_array(jsonb_build_object('sales_invoice_line_id', v_inv_line, 'qty', 1)),
    v_pick
  );
  PERFORM public.submit_delivery_note(v_dn);
  v_job := public.create_delivery_job(v_dn, NULL, NULL, 'pod storage smoke');
  PERFORM public.assign_delivery_job(v_job, v_driver, true);
  PERFORM public.update_delivery_job_status(v_job, 'dispatched');

  v_photo := v_job::text || '/photo.jpg';
  v_sig := v_job::text || '/signature.png';

  -- Path helper
  IF public._delivery_pod_job_id_from_path(v_photo) IS DISTINCT FROM v_job THEN
    RAISE EXCEPTION 'smoke fail: path parse';
  END IF;

  -- Assigned driver can INSERT
  PERFORM public._test_set_auth_uid(v_driver);
  SET LOCAL ROLE authenticated;
  INSERT INTO storage.objects (bucket_id, name, owner, owner_id, metadata)
  VALUES (
    'delivery-pods', v_photo, v_driver, v_driver::text,
    jsonb_build_object('mimetype', 'image/jpeg', 'size', 12)
  )
  RETURNING id INTO v_obj;
  IF v_obj IS NULL THEN
    RAISE EXCEPTION 'smoke fail: driver insert photo denied';
  END IF;
  INSERT INTO storage.objects (bucket_id, name, owner, owner_id, metadata)
  VALUES (
    'delivery-pods', v_sig, v_driver, v_driver::text,
    jsonb_build_object('mimetype', 'image/png', 'size', 8)
  );
  RESET ROLE;

  -- Other driver cannot INSERT under this job
  PERFORM public._test_set_auth_uid(v_driver2);
  SET LOCAL ROLE authenticated;
  BEGIN
    INSERT INTO storage.objects (bucket_id, name, owner, owner_id, metadata)
    VALUES (
      'delivery-pods', v_job::text || '/photo2.jpg', v_driver2, v_driver2::text,
      '{}'::jsonb
    );
    RAISE EXCEPTION 'smoke fail: other driver insert should fail';
  EXCEPTION
    WHEN insufficient_privilege OR check_violation OR OTHERS THEN
      IF SQLERRM LIKE 'smoke fail:%' THEN RAISE; END IF;
  END;
  RESET ROLE;

  -- Customer cannot SELECT
  PERFORM public._test_set_auth_uid(v_customer);
  SET LOCAL ROLE authenticated;
  SELECT count(*)::int INTO v_cnt
  FROM storage.objects
  WHERE bucket_id = 'delivery-pods' AND name = v_photo;
  IF v_cnt <> 0 THEN
    RAISE EXCEPTION 'smoke fail: customer must not SELECT POD objects';
  END IF;
  RESET ROLE;

  -- Admin/dispatcher can SELECT
  PERFORM public._test_set_auth_uid(v_admin);
  SET LOCAL ROLE authenticated;
  SELECT count(*)::int INTO v_cnt
  FROM storage.objects
  WHERE bucket_id = 'delivery-pods' AND name IN (v_photo, v_sig);
  IF v_cnt < 2 THEN
    RAISE EXCEPTION 'smoke fail: admin should SELECT POD objects (got %)', v_cnt;
  END IF;
  RESET ROLE;

  -- Assigned driver cannot SELECT (staff-only read per policy)
  PERFORM public._test_set_auth_uid(v_driver);
  SET LOCAL ROLE authenticated;
  SELECT count(*)::int INTO v_cnt
  FROM storage.objects
  WHERE bucket_id = 'delivery-pods' AND name = v_photo;
  IF v_cnt <> 0 THEN
    RAISE EXCEPTION 'smoke fail: driver SELECT should be denied (staff-only)';
  END IF;
  RESET ROLE;

  RAISE NOTICE 'delivery_pods_storage_smoke: PASS job=% photo=%', v_job, v_photo;
END;
$$;
