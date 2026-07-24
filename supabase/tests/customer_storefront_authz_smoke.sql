-- Customer storefront AuthZ smoke (postgres via docker exec).
-- Own vs peer denial; staff POS still works; ContiPay/Paynow customer create; no ZIMRA.

CREATE OR REPLACE FUNCTION public._test_set_auth_uid(p_uid UUID)
RETURNS void
LANGUAGE plpgsql
AS $$
BEGIN
  PERFORM set_config('request.jwt.claim.sub', p_uid::text, true);
  PERFORM set_config(
    'request.jwt.claims',
    json_build_object('sub', p_uid::text, 'role', 'authenticated')::text,
    true
  );
  PERFORM set_config('request.jwt.claim.role', 'authenticated', true);
END;
$$;

CREATE OR REPLACE FUNCTION public._test_set_service_role(p_uid UUID DEFAULT NULL)
RETURNS void
LANGUAGE plpgsql
AS $$
BEGIN
  IF p_uid IS NOT NULL THEN
    PERFORM set_config('request.jwt.claim.sub', p_uid::text, true);
  END IF;
  PERFORM set_config(
    'request.jwt.claims',
    json_build_object(
      'sub', COALESCE(current_setting('request.jwt.claim.sub', true), ''),
      'role', 'service_role'
    )::text,
    true
  );
  PERFORM set_config('request.jwt.claim.role', 'service_role', true);
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
  v_cust_b UUID;
  v_cart UUID;
  v_cart_b UUID;
  v_inv UUID;
  v_inv_b UUID;
  v_line UUID;
  v_core_cnt INT;
  v_intent_cp UUID;
  v_intent_pn UUID;
  v_pe UUID;
  v_garage UUID;
  v_order JSONB;
  v_seen INT;
  v_ext TEXT;
BEGIN
  -- Customer auth users (non-staff)
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

  INSERT INTO public.stock_items (oem_part_number, description, base_uom_id, reorder_point, reorder_qty)
  VALUES ('P-SF-AUTHZ-001', 'Storefront authz part', v_uom, 50, 10)
  ON CONFLICT (oem_part_number) DO UPDATE
  SET description = EXCLUDED.description,
      base_uom_id = COALESCE(public.stock_items.base_uom_id, EXCLUDED.base_uom_id)
  RETURNING id INTO v_item;
  IF v_item IS NULL THEN
    SELECT id INTO v_item FROM public.stock_items WHERE oem_part_number = 'P-SF-AUTHZ-001';
  END IF;

  INSERT INTO public.price_list_items (price_list_id, stock_item_id, unit_price, core_charge)
  VALUES (v_list, v_item, 25, 5)
  ON CONFLICT (price_list_id, stock_item_id) DO UPDATE
  SET unit_price = 25, core_charge = 5;

  PERFORM public.post_stock_receipt(
    v_main,
    'SF authz seed',
    jsonb_build_array(
      jsonb_build_object(
        'stock_item_id', v_item, 'uom_id', v_uom, 'qty', 40,
        'unit_cost', 8, 'currency', 'USD', 'valuation_method', 'FIFO'
      )
    )
  );

  INSERT INTO public.customers (display_name, currency, profile_id)
  VALUES ('Storefront Customer A', 'USD', v_cust_a_user)
  RETURNING id INTO v_cust_a;

  INSERT INTO public.customers (display_name, currency, profile_id)
  VALUES ('Storefront Customer B', 'USD', v_cust_b_user)
  RETURNING id INTO v_cust_b;

  -- -----------------------------------------------------------------------
  -- 1) Customer A: cart → core-charge split → checkout → own invoice
  -- -----------------------------------------------------------------------
  PERFORM public._test_set_auth_uid(v_cust_a_user);

  v_cart := public.create_customer_cart(
    v_main, 'USD'::public.currency_code, 'immediate'::public.fulfillment_mode, 1
  );
  IF NOT EXISTS (
    SELECT 1 FROM public.pos_carts
    WHERE id = v_cart AND channel = 'storefront' AND customer_id = v_cust_a
      AND currency = 'USD' AND exchange_rate_applied = 1
  ) THEN
    RAISE EXCEPTION 'smoke fail: customer cart channel/currency missing';
  END IF;

  v_line := public.add_customer_cart_line(v_cart, v_item, v_uom, 2);
  SELECT count(*) INTO v_core_cnt
  FROM public.pos_cart_lines
  WHERE cart_id = v_cart AND is_core_charge;
  IF v_core_cnt <> 1 THEN
    RAISE EXCEPTION 'smoke fail: expected 1 core-charge line got %', v_core_cnt;
  END IF;

  v_inv := public.checkout_customer_cart(v_cart);
  IF NOT EXISTS (
    SELECT 1 FROM public.sales_invoices
    WHERE id = v_inv AND customer_id = v_cust_a AND status = 'posted'
      AND total = 60 AND currency = 'USD' AND exchange_rate_applied = 1
  ) THEN
    RAISE EXCEPTION 'smoke fail: customer checkout invoice missing/wrong total';
  END IF;

  v_order := public.get_customer_order(v_inv);
  IF (v_order ->> 'invoice_id')::uuid IS DISTINCT FROM v_inv
     OR (v_order ->> 'amount_open')::numeric <> 60 THEN
    RAISE EXCEPTION 'smoke fail: get_customer_order bad payload %', v_order;
  END IF;

  -- RLS: customer A sees own invoice
  SET LOCAL ROLE authenticated;
  SELECT count(*) INTO v_seen FROM public.sales_invoices WHERE id = v_inv;
  IF v_seen <> 1 THEN
    RAISE EXCEPTION 'smoke fail: customer A cannot SELECT own invoice';
  END IF;
  RESET ROLE;

  -- -----------------------------------------------------------------------
  -- 2) Peer denial (customer B)
  -- -----------------------------------------------------------------------
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

  BEGIN
    PERFORM public.add_customer_cart_line(v_cart, v_item, v_uom, 1);
    RAISE EXCEPTION 'smoke fail: peer mutate cart A allowed';
  EXCEPTION
    WHEN OTHERS THEN
      IF SQLERRM LIKE 'smoke fail:%' THEN RAISE; END IF;
      IF SQLERRM NOT ILIKE '%not authorized%' AND SQLERRM NOT ILIKE '%open cart%' THEN
        RAISE EXCEPTION 'smoke fail: unexpected peer cart error: %', SQLERRM;
      END IF;
  END;

  SET LOCAL ROLE authenticated;
  SELECT count(*) INTO v_seen FROM public.sales_invoices WHERE id = v_inv;
  IF v_seen <> 0 THEN
    RAISE EXCEPTION 'smoke fail: peer SELECT on A invoice allowed';
  END IF;
  RESET ROLE;

  -- B own unpaid invoice for intent denial cross-check
  v_cart_b := public.create_customer_cart(v_main, 'USD', 'immediate', 1);
  PERFORM public.add_customer_cart_line(v_cart_b, v_item, v_uom, 1);
  v_inv_b := public.checkout_customer_cart(v_cart_b);

  -- -----------------------------------------------------------------------
  -- 3) Customer A intents on own invoice; denied on B; settle service-only
  -- -----------------------------------------------------------------------
  PERFORM public._test_set_auth_uid(v_cust_a_user);

  v_ext := 'SF-CP-' || v_inv::text;
  v_intent_cp := public.create_customer_contipay_intent(
    v_inv, 'ecocash'::public.contipay_method, v_ext, 60, 'ZIG', 1800, 30
  );
  IF NOT EXISTS (
    SELECT 1 FROM public.contipay_payment_intents
    WHERE id = v_intent_cp AND customer_id = v_cust_a AND amount = 60
      AND currency = 'USD' AND exchange_rate_applied = 1
      AND (metadata ->> 'sales_invoice_id') = v_inv::text
  ) THEN
    RAISE EXCEPTION 'smoke fail: ContiPay customer intent missing currency metadata';
  END IF;

  v_intent_pn := public.create_customer_paynow_intent(
    v_inv, 'ecocash'::public.paynow_method, 'SF-PN-' || v_inv::text, 60
  );
  IF NOT EXISTS (
    SELECT 1 FROM public.paynow_payment_intents
    WHERE id = v_intent_pn AND customer_id = v_cust_a AND amount = 60
  ) THEN
    RAISE EXCEPTION 'smoke fail: Paynow customer intent missing';
  END IF;

  BEGIN
    PERFORM public.create_customer_contipay_intent(
      v_inv_b, 'visa'::public.contipay_method, 'SF-CP-PEER-' || v_inv_b::text
    );
    RAISE EXCEPTION 'smoke fail: ContiPay on peer invoice allowed';
  EXCEPTION
    WHEN OTHERS THEN
      IF SQLERRM LIKE 'smoke fail:%' THEN RAISE; END IF;
      IF SQLERRM NOT ILIKE '%not authorized%' THEN
        RAISE EXCEPTION 'smoke fail: unexpected peer ContiPay error: %', SQLERRM;
      END IF;
  END;

  BEGIN
    PERFORM public.create_customer_paynow_intent(
      v_inv_b, 'visa'::public.paynow_method, 'SF-PN-PEER-' || v_inv_b::text
    );
    RAISE EXCEPTION 'smoke fail: Paynow on peer invoice allowed';
  EXCEPTION
    WHEN OTHERS THEN
      IF SQLERRM LIKE 'smoke fail:%' THEN RAISE; END IF;
      IF SQLERRM NOT ILIKE '%not authorized%' THEN
        RAISE EXCEPTION 'smoke fail: unexpected peer Paynow error: %', SQLERRM;
      END IF;
  END;

  -- Customer must not settle
  BEGIN
    SET LOCAL ROLE authenticated;
    PERFORM public.mark_contipay_settled(
      v_ext, 'hash-sf-' || v_inv::text, 'prov-sf',
      jsonb_build_array(jsonb_build_object('sales_invoice_id', v_inv, 'amount', 60)),
      'ZIG', 1800, 30, true, NULL
    );
    RAISE EXCEPTION 'smoke fail: customer settle ContiPay allowed';
  EXCEPTION
    WHEN OTHERS THEN
      IF SQLERRM LIKE 'smoke fail:%' THEN RAISE; END IF;
      -- permission denied or staff gate
  END;
  RESET ROLE;

  PERFORM public._test_set_service_role(v_admin);
  v_pe := public.mark_contipay_settled(
    v_ext, 'hash-sf-' || v_inv::text, 'prov-sf',
    jsonb_build_array(jsonb_build_object('sales_invoice_id', v_inv, 'amount', 60)),
    'ZIG', 1800, 30, true, NULL
  );
  IF v_pe IS NULL THEN
    RAISE EXCEPTION 'smoke fail: service settle ContiPay null';
  END IF;
  -- Idempotent second settle
  PERFORM public.mark_contipay_settled(
    v_ext, 'hash-sf-' || v_inv::text, 'prov-sf',
    jsonb_build_array(jsonb_build_object('sales_invoice_id', v_inv, 'amount', 60)),
    'ZIG', 1800, 30, true, NULL
  );

  -- -----------------------------------------------------------------------
  -- 4) Garage own vs peer
  -- -----------------------------------------------------------------------
  PERFORM public._test_set_auth_uid(v_cust_a_user);
  v_garage := public.upsert_customer_garage_vehicle(
    NULL, 'Nissan', 'Navara', 'D40', 'YD25', 'JN1XXXXSF001', true
  );
  IF NOT EXISTS (
    SELECT 1 FROM public.customer_garage_vehicles
    WHERE id = v_garage AND customer_id = v_cust_a AND is_primary
  ) THEN
    RAISE EXCEPTION 'smoke fail: garage insert missing';
  END IF;

  PERFORM public._test_set_auth_uid(v_cust_b_user);
  SET LOCAL ROLE authenticated;
  SELECT count(*) INTO v_seen FROM public.customer_garage_vehicles WHERE id = v_garage;
  IF v_seen <> 0 THEN
    RAISE EXCEPTION 'smoke fail: peer garage SELECT allowed';
  END IF;
  RESET ROLE;

  -- -----------------------------------------------------------------------
  -- 5) Staff POS still works
  -- -----------------------------------------------------------------------
  PERFORM public._test_set_auth_uid(v_admin);
  v_cart := public.create_pos_cart(
    v_main, v_cust_a, 'USD'::public.currency_code, 'immediate'::public.fulfillment_mode
  );
  IF NOT EXISTS (
    SELECT 1 FROM public.pos_carts WHERE id = v_cart AND channel = 'pos'
  ) THEN
    RAISE EXCEPTION 'smoke fail: staff cart channel not pos';
  END IF;
  PERFORM public.add_cart_line(v_cart, v_item, v_uom, 1);
  v_inv := public.checkout_pos_cart(v_cart);
  IF NOT EXISTS (
    SELECT 1 FROM public.sales_invoices WHERE id = v_inv AND status = 'posted'
  ) THEN
    RAISE EXCEPTION 'smoke fail: staff POS checkout failed';
  END IF;

  v_intent_cp := public.create_contipay_intent(
    'SF-STAFF-CP-' || v_inv::text,
    'ecocash', 25, 'USD', 1, v_cust_a
  );
  IF v_intent_cp IS NULL THEN
    RAISE EXCEPTION 'smoke fail: staff ContiPay intent null';
  END IF;

  -- -----------------------------------------------------------------------
  -- 6) Exclusion: no ZIMRA markers in this smoke path
  -- -----------------------------------------------------------------------
  IF EXISTS (
    SELECT 1 FROM public.sales_invoices
    WHERE id = v_inv
      AND (
        COALESCE(document_number, '') ILIKE '%zimra%'
        OR COALESCE(document_number, '') ILIKE '%fdms%'
        OR COALESCE(document_number, '') ILIKE '%fiscal%'
      )
  ) THEN
    RAISE EXCEPTION 'smoke fail: ZIMRA/FDMS marker on invoice';
  END IF;

  RAISE NOTICE 'customer_storefront_authz_smoke PASS';
END;
$$;
