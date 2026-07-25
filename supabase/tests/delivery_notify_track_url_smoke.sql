-- Out-for-delivery notify: absolute track URL + fail-closed SMS outbox.
-- Requires migrations through 20260725140000_delivery_notify_absolute_track_url.
-- Run e.g.:
--   docker exec -i <supabase_db> psql -U postgres -d postgres -v ON_ERROR_STOP=1 < supabase/tests/delivery_notify_track_url_smoke.sql

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
  v_cust_user UUID := 'c0000000-0000-4000-8000-0000000000b1';
  v_main UUID;
  v_uom UUID;
  v_item UUID;
  v_list UUID;
  v_cust UUID;
  v_cart UUID;
  v_inv UUID;
  v_inv_line UUID;
  v_pick UUID;
  v_dn UUID;
  v_job_abs UUID;
  v_job_rel UUID;
  v_body TEXT;
BEGIN
  PERFORM public._test_set_auth_uid(v_admin);

  SELECT id INTO v_main FROM public.warehouses WHERE code = 'MAIN';
  SELECT id INTO v_uom FROM public.uoms WHERE code = 'EA';
  SELECT id INTO v_list FROM public.price_lists WHERE code = 'RETAIL';
  IF v_main IS NULL OR v_uom IS NULL THEN
    RAISE EXCEPTION 'smoke fail: MAIN/EA missing';
  END IF;

  INSERT INTO auth.users (
    instance_id, id, aud, role, email, encrypted_password,
    email_confirmed_at, raw_app_meta_data, raw_user_meta_data,
    created_at, updated_at, confirmation_token, recovery_token,
    email_change_token_new, email_change
  )
  VALUES (
    '00000000-0000-0000-0000-000000000000', v_cust_user, 'authenticated', 'authenticated',
    'notify-cust@gtr.local', crypt('local-dev-cust', gen_salt('bf')),
    now(), '{"provider":"email","providers":["email"]}'::jsonb, '{}'::jsonb,
    now(), now(), '', '', '', ''
  )
  ON CONFLICT (id) DO NOTHING;

  INSERT INTO public.profiles (id, full_name, is_staff)
  VALUES (v_cust_user, 'Notify Smoke Customer', false)
  ON CONFLICT (id) DO UPDATE SET full_name = EXCLUDED.full_name;

  INSERT INTO public.stock_items (oem_part_number, description, base_uom_id)
  VALUES ('P0-NOTIFY-001', 'Notify track URL smoke part', v_uom)
  ON CONFLICT (oem_part_number) DO UPDATE SET description = EXCLUDED.description
  RETURNING id INTO v_item;
  IF v_item IS NULL THEN
    SELECT id INTO v_item FROM public.stock_items WHERE oem_part_number = 'P0-NOTIFY-001';
  END IF;

  INSERT INTO public.price_list_items (price_list_id, stock_item_id, unit_price, core_charge)
  VALUES (v_list, v_item, 15, 0)
  ON CONFLICT (price_list_id, stock_item_id) DO UPDATE
  SET unit_price = 15, core_charge = 0;

  PERFORM public.post_stock_receipt(
    v_main,
    'NOTIFY seed',
    jsonb_build_array(
      jsonb_build_object(
        'stock_item_id', v_item,
        'uom_id', v_uom,
        'qty', 10,
        'unit_cost', 5,
        'currency', 'USD',
        'valuation_method', 'FIFO'
      )
    )
  );

  INSERT INTO public.customers (
    display_name, profile_id, phone_e164, currency
  )
  VALUES (
    'Notify Track Customer', v_cust_user, '+263771400001', 'USD'
  )
  RETURNING id INTO v_cust;

  -- -----------------------------------------------------------------------
  -- Job A: PUBLIC_SITE_URL mirrored as GUC → absolute track URL in SMS
  -- -----------------------------------------------------------------------
  PERFORM set_config('app.public_site_url', 'https://example.test/', true);

  v_cart := public.create_pos_cart(v_main, v_cust, 'USD', 'dispatch');
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
  v_job_abs := public.create_delivery_job(
    v_dn, NULL, now() + interval '1 hour', 'notify abs url'
  );
  PERFORM public.update_delivery_job_status(v_job_abs, 'dispatched');

  SELECT body INTO v_body
  FROM public.sms_outbox
  WHERE event_code = 'delivery_out_for_delivery'
    AND recipient_user_id = v_cust_user
    AND body LIKE '%https://example.test/track/%'
  ORDER BY created_at DESC
  LIMIT 1;

  IF v_body IS NULL THEN
    RAISE EXCEPTION 'smoke fail: expected absolute track URL in sms_outbox (PUBLIC_SITE_URL / app.public_site_url)';
  END IF;
  IF v_body LIKE '%https://example.test//track/%' THEN
    RAISE EXCEPTION 'smoke fail: double slash in absolute track URL: %', v_body;
  END IF;

  -- -----------------------------------------------------------------------
  -- Job B: GUC cleared → relative /track/{token} OK
  -- -----------------------------------------------------------------------
  PERFORM set_config('app.public_site_url', '', true);

  v_cart := public.create_pos_cart(v_main, v_cust, 'USD', 'dispatch');
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
  v_job_rel := public.create_delivery_job(
    v_dn, NULL, now() + interval '1 hour', 'notify rel url'
  );
  PERFORM public.update_delivery_job_status(v_job_rel, 'dispatched');

  SELECT body INTO v_body
  FROM public.sms_outbox
  WHERE event_code = 'delivery_out_for_delivery'
    AND recipient_user_id = v_cust_user
    AND body LIKE '%Track: /track/%'
    AND body NOT LIKE '%https://%'
  ORDER BY created_at DESC
  LIMIT 1;

  IF v_body IS NULL THEN
    RAISE EXCEPTION 'smoke fail: expected relative /track/ URL when PUBLIC_SITE_URL unset';
  END IF;

  -- -----------------------------------------------------------------------
  -- Fail closed: empty token / missing job must not raise
  -- -----------------------------------------------------------------------
  BEGIN
    PERFORM public._notify_out_for_delivery(v_job_abs, '');
    PERFORM public._notify_out_for_delivery(
      '00000000-0000-4000-8000-0000000000ff'::uuid,
      'dead-token'
    );
  EXCEPTION
    WHEN OTHERS THEN
      RAISE EXCEPTION 'smoke fail: _notify_out_for_delivery must fail closed, got: %', SQLERRM;
  END;

  RAISE NOTICE 'delivery_notify_track_url_smoke: OK';
END;
$$;
