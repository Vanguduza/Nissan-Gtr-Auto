-- Webhook-shaped settle: p_allocations NULL must still allocate from
-- intent metadata.sales_invoice_id (ContiPay/Paynow edge path).
-- Requires seed MAIN/EA/RETAIL + migrations through 20260803110000.

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
  v_cust_user UUID := 'c0000000-0000-4000-8000-0000000000w1';
  v_main UUID;
  v_uom UUID;
  v_list UUID;
  v_item UUID;
  v_cust UUID;
  v_cart UUID;
  v_inv UUID;
  v_ext TEXT;
  v_intent UUID;
  v_pe UUID;
  v_paid NUMERIC;
  v_helper JSONB;
BEGIN
  -- Helper unit checks (no DB side effects)
  v_helper := public._allocations_from_intent_metadata(
    NULL,
    jsonb_build_object('sales_invoice_id', '11111111-1111-4111-8111-111111111111'),
    42.5
  );
  IF v_helper IS DISTINCT FROM jsonb_build_array(
    jsonb_build_object(
      'sales_invoice_id', '11111111-1111-4111-8111-111111111111'::uuid,
      'amount', 42.5
    )
  ) THEN
    RAISE EXCEPTION 'smoke fail: helper did not build allocation from metadata %', v_helper;
  END IF;

  v_helper := public._allocations_from_intent_metadata(
    jsonb_build_array(jsonb_build_object('sales_invoice_id', '22222222-2222-4222-8222-222222222222', 'amount', 10)),
    jsonb_build_object('sales_invoice_id', '11111111-1111-4111-8111-111111111111'),
    99
  );
  IF (v_helper -> 0 ->> 'amount')::numeric <> 10 THEN
    RAISE EXCEPTION 'smoke fail: helper should prefer explicit allocations';
  END IF;

  IF public._allocations_from_intent_metadata(NULL, '{}'::jsonb, 10) IS NOT NULL THEN
    RAISE EXCEPTION 'smoke fail: helper should return null without sales_invoice_id';
  END IF;

  INSERT INTO auth.users (
    instance_id, id, aud, role, email, encrypted_password, email_confirmed_at,
    raw_app_meta_data, raw_user_meta_data, created_at, updated_at,
    confirmation_token, recovery_token, email_change_token_new, email_change
  )
  VALUES (
    '00000000-0000-0000-0000-000000000000', v_cust_user,
    'authenticated', 'authenticated', 'webhook-alloc@gtr.local',
    crypt('local-dev-customer', gen_salt('bf')), now(),
    '{"provider":"email","providers":["email"]}'::jsonb,
    '{"full_name":"Webhook Alloc"}'::jsonb, now(), now(), '', '', '', ''
  )
  ON CONFLICT (id) DO NOTHING;

  INSERT INTO public.profiles (id, full_name, is_staff)
  VALUES (v_cust_user, 'Webhook Alloc', false)
  ON CONFLICT (id) DO UPDATE SET full_name = EXCLUDED.full_name, is_staff = false;

  PERFORM public._test_set_auth_uid(v_admin);

  SELECT id INTO v_main FROM public.warehouses WHERE code = 'MAIN';
  SELECT id INTO v_uom FROM public.uoms WHERE code = 'EA';
  SELECT id INTO v_list FROM public.price_lists WHERE code = 'RETAIL';
  IF v_main IS NULL OR v_uom IS NULL OR v_list IS NULL THEN
    RAISE EXCEPTION 'smoke fail: seed MAIN/EA/RETAIL missing';
  END IF;

  INSERT INTO public.stock_items (oem_part_number, description, base_uom_id, reorder_point, reorder_qty)
  VALUES ('P-WH-ALLOC-001', 'Webhook alloc part', v_uom, 50, 10)
  ON CONFLICT (oem_part_number) DO UPDATE
  SET description = EXCLUDED.description,
      base_uom_id = COALESCE(public.stock_items.base_uom_id, EXCLUDED.base_uom_id)
  RETURNING id INTO v_item;
  IF v_item IS NULL THEN
    SELECT id INTO v_item FROM public.stock_items WHERE oem_part_number = 'P-WH-ALLOC-001';
  END IF;

  INSERT INTO public.price_list_items (price_list_id, stock_item_id, unit_price, core_charge)
  VALUES (v_list, v_item, 40, 0)
  ON CONFLICT (price_list_id, stock_item_id) DO UPDATE
  SET unit_price = 40, core_charge = 0;

  PERFORM public.post_stock_receipt(
    v_main,
    'webhook alloc seed',
    jsonb_build_array(
      jsonb_build_object(
        'stock_item_id', v_item, 'uom_id', v_uom, 'qty', 20,
        'unit_cost', 10, 'currency', 'USD', 'valuation_method', 'FIFO'
      )
    )
  );

  SELECT id INTO v_cust FROM public.customers WHERE profile_id = v_cust_user
  ORDER BY created_at ASC LIMIT 1;
  IF v_cust IS NULL THEN
    INSERT INTO public.customers (display_name, currency, profile_id)
    VALUES ('Webhook Alloc Customer', 'USD', v_cust_user)
    RETURNING id INTO v_cust;
  END IF;

  -- ContiPay: customer intent → settle with NULL allocations (webhook shape)
  PERFORM public._test_set_auth_uid(v_cust_user);
  v_cart := public.create_customer_cart(v_main, 'USD', 'immediate', 1);
  PERFORM public.add_customer_cart_line(v_cart, v_item, v_uom, 1);
  v_inv := public.checkout_customer_cart(v_cart);

  v_ext := 'WH-CP-NULL-' || v_inv::text;
  v_intent := public.create_customer_contipay_intent(
    v_inv, 'ecocash'::public.contipay_method, v_ext, 40
  );
  IF NOT EXISTS (
    SELECT 1 FROM public.contipay_payment_intents
    WHERE id = v_intent AND (metadata ->> 'sales_invoice_id') = v_inv::text
  ) THEN
    RAISE EXCEPTION 'smoke fail: ContiPay intent missing sales_invoice_id metadata';
  END IF;

  PERFORM public._test_set_service_role(v_admin);
  v_pe := public.mark_contipay_settled(
    v_ext,
    'hash-wh-cp-null-' || v_inv::text,
    'prov-wh-cp',
    NULL, -- webhook path
    NULL, NULL, NULL,
    true,
    NULL
  );
  IF v_pe IS NULL THEN
    RAISE EXCEPTION 'smoke fail: ContiPay null-allocation settle returned null';
  END IF;

  SELECT amount_paid INTO v_paid FROM public.sales_invoices WHERE id = v_inv;
  IF v_paid <> 40 THEN
    RAISE EXCEPTION 'smoke fail: ContiPay null-allocation paid=% expected 40', v_paid;
  END IF;

  IF NOT EXISTS (
    SELECT 1
    FROM public.payment_allocations pa
    WHERE pa.payment_entry_id = v_pe
      AND pa.sales_invoice_id = v_inv
      AND pa.amount = 40
  ) THEN
    RAISE EXCEPTION 'smoke fail: ContiPay allocation row missing after null settle';
  END IF;

  -- Paynow: same webhook-shaped null allocations
  PERFORM public._test_set_auth_uid(v_cust_user);
  v_cart := public.create_customer_cart(v_main, 'USD', 'immediate', 1);
  PERFORM public.add_customer_cart_line(v_cart, v_item, v_uom, 1);
  v_inv := public.checkout_customer_cart(v_cart);

  v_ext := 'WH-PN-NULL-' || v_inv::text;
  v_intent := public.create_customer_paynow_intent(
    v_inv, 'ecocash'::public.paynow_method, v_ext, 40
  );

  PERFORM public._test_set_service_role(v_admin);
  v_pe := public.mark_paynow_settled(
    v_ext,
    'hash-wh-pn-null-' || v_inv::text,
    'prov-wh-pn',
    NULL,
    NULL, NULL, NULL,
    true,
    NULL
  );
  IF v_pe IS NULL THEN
    RAISE EXCEPTION 'smoke fail: Paynow null-allocation settle returned null';
  END IF;

  SELECT amount_paid INTO v_paid FROM public.sales_invoices WHERE id = v_inv;
  IF v_paid <> 40 THEN
    RAISE EXCEPTION 'smoke fail: Paynow null-allocation paid=% expected 40', v_paid;
  END IF;

  RAISE NOTICE 'payment_intent_webhook_auto_allocate_smoke ok';
END;
$$;
