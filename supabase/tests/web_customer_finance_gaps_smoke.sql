-- Smoke: bank recon grants, customer addresses/profile UPDATE, customer return CN.
-- Run as postgres after migrations 20260724170000 + 20260724171000.
-- Does not db-reset. Reuses auth helper pattern from customer_storefront_authz_smoke.

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

DO $$
DECLARE
  v_admin UUID := 'a0000000-0000-4000-8000-000000000001';
  v_finance UUID := 'a0000000-0000-4000-8000-000000000004';
  v_cust_user UUID := 'c0000000-0000-4000-8000-0000000000c1';
  v_peer_user UUID := 'c0000000-0000-4000-8000-0000000000c2';
  v_main UUID;
  v_quar UUID;
  v_uom UUID;
  v_item UUID;
  v_cust UUID;
  v_peer UUID;
  v_inv UUID;
  v_cn UUID;
  v_addr UUID;
  v_stmt UUID;
  v_line UUID;
  v_match UUID;
  v_jel UUID;
  v_updated UUID;
BEGIN
  SELECT id INTO v_main FROM public.warehouses WHERE code = 'MAIN' LIMIT 1;
  SELECT id INTO v_quar FROM public.warehouses WHERE is_quarantine AND is_active ORDER BY code LIMIT 1;
  SELECT id INTO v_uom FROM public.uoms WHERE code = 'EA' LIMIT 1;
  IF v_main IS NULL OR v_quar IS NULL OR v_uom IS NULL THEN
    RAISE EXCEPTION 'smoke fail: MAIN/QUAR/EA required';
  END IF;

  -- Finance user (role already seeded in some envs; ensure)
  INSERT INTO auth.users (
    instance_id, id, aud, role, email, encrypted_password, email_confirmed_at,
    raw_app_meta_data, raw_user_meta_data, created_at, updated_at,
    confirmation_token, recovery_token, email_change_token_new, email_change
  )
  VALUES
    (
      '00000000-0000-0000-0000-000000000000', v_finance,
      'authenticated', 'authenticated', 'finance-bank@gtr.local',
      crypt('local-dev', gen_salt('bf')), now(),
      '{"provider":"email","providers":["email"]}'::jsonb,
      '{"full_name":"Finance Bank"}'::jsonb, now(), now(), '', '', '', ''
    ),
    (
      '00000000-0000-0000-0000-000000000000', v_cust_user,
      'authenticated', 'authenticated', 'cust-return@gtr.local',
      crypt('local-dev', gen_salt('bf')), now(),
      '{"provider":"email","providers":["email"]}'::jsonb,
      '{"full_name":"Cust Return"}'::jsonb, now(), now(), '', '', '', ''
    ),
    (
      '00000000-0000-0000-0000-000000000000', v_peer_user,
      'authenticated', 'authenticated', 'cust-peer@gtr.local',
      crypt('local-dev', gen_salt('bf')), now(),
      '{"provider":"email","providers":["email"]}'::jsonb,
      '{"full_name":"Cust Peer"}'::jsonb, now(), now(), '', '', '', ''
    )
  ON CONFLICT (id) DO NOTHING;

  INSERT INTO public.profiles (id, full_name, is_staff)
  VALUES
    (v_finance, 'Finance Bank', true),
    (v_cust_user, 'Cust Return', false),
    (v_peer_user, 'Cust Peer', false)
  ON CONFLICT (id) DO UPDATE
  SET full_name = EXCLUDED.full_name, is_staff = EXCLUDED.is_staff;

  INSERT INTO public.staff_roles (user_id, role)
  VALUES (v_finance, 'finance')
  ON CONFLICT DO NOTHING;

  INSERT INTO public.stock_items (oem_part_number, description, base_uom_id)
  VALUES ('SMOKE-RET-001', 'Return smoke part', v_uom)
  ON CONFLICT (oem_part_number) DO UPDATE SET description = EXCLUDED.description
  RETURNING id INTO v_item;
  IF v_item IS NULL THEN
    SELECT id INTO v_item FROM public.stock_items WHERE oem_part_number = 'SMOKE-RET-001';
  END IF;

  INSERT INTO public.customers (display_name, currency, profile_id, phone_e164, email)
  VALUES ('Return Cust', 'USD', v_cust_user, '+263771000001', 'cust-return@gtr.local')
  ON CONFLICT DO NOTHING;
  SELECT id INTO v_cust FROM public.customers WHERE profile_id = v_cust_user LIMIT 1;
  IF v_cust IS NULL THEN
    INSERT INTO public.customers (display_name, currency, profile_id, phone_e164, email)
    VALUES ('Return Cust', 'USD', v_cust_user, '+263771000001', 'cust-return@gtr.local')
    RETURNING id INTO v_cust;
  END IF;

  SELECT id INTO v_peer FROM public.customers WHERE profile_id = v_peer_user LIMIT 1;
  IF v_peer IS NULL THEN
    INSERT INTO public.customers (display_name, currency, profile_id)
    VALUES ('Peer Cust', 'USD', v_peer_user)
    RETURNING id INTO v_peer;
  END IF;

  -- Seed stock for quarantine return path
  PERFORM public._adjust_stock_level(v_item, v_main, 10, 'FIFO', 25, 'USD');

  -- Staff posts a sale for customer (as admin)
  PERFORM public._test_set_auth_uid(v_admin);
  PERFORM set_config('role', 'authenticated', true);

  DECLARE
    v_cart UUID;
  BEGIN
    v_cart := public.create_pos_cart(v_main, v_cust, 'USD');
    PERFORM public.add_cart_line(v_cart, v_item, v_uom, 2);
    v_inv := public.checkout_pos_cart(v_cart);
  END;

  -- ---------------------------------------------------------------------------
  -- Bank recon DML as finance (must not be permission denied for table)
  -- ---------------------------------------------------------------------------
  PERFORM public._test_set_auth_uid(v_finance);
  PERFORM set_config('role', 'authenticated', true);

  INSERT INTO public.bank_statements (
    account_code, currency, statement_date, opening_balance, closing_balance, created_by
  )
  VALUES ('1100', 'USD', CURRENT_DATE, 0, 100, v_finance)
  RETURNING id INTO v_stmt;

  INSERT INTO public.bank_statement_lines (statement_id, line_date, description, amount)
  VALUES (v_stmt, CURRENT_DATE, 'smoke deposit', 100)
  RETURNING id INTO v_line;

  SELECT jel.id INTO v_jel
  FROM public.journal_entry_lines jel
  JOIN public.journal_entries je ON je.id = jel.journal_entry_id
  WHERE jel.account_code = '1100'
  ORDER BY je.posted_at DESC NULLS LAST
  LIMIT 1;

  IF v_jel IS NOT NULL THEN
    INSERT INTO public.bank_recon_matches (statement_line_id, journal_entry_line_id, matched_by)
    VALUES (v_line, v_jel, v_finance)
    RETURNING id INTO v_match;

    UPDATE public.bank_statement_lines
    SET status = 'matched'
    WHERE id = v_line AND status = 'open';
  END IF;

  -- Customer cannot insert bank statements
  PERFORM public._test_set_auth_uid(v_cust_user);
  BEGIN
    INSERT INTO public.bank_statements (
      account_code, currency, statement_date, opening_balance, closing_balance
    ) VALUES ('1100', 'USD', CURRENT_DATE, 0, 1);
    RAISE EXCEPTION 'smoke fail: customer inserted bank_statements';
  EXCEPTION
    WHEN insufficient_privilege THEN NULL;
    WHEN OTHERS THEN
      IF SQLERRM LIKE 'smoke fail:%' THEN RAISE; END IF;
      -- RLS violation also OK
      NULL;
  END;

  -- ---------------------------------------------------------------------------
  -- Own profile UPDATE + privileged column guard
  -- ---------------------------------------------------------------------------
  UPDATE public.customers
  SET phone_e164 = '+263771000099', sms_receipts = false, updated_at = now()
  WHERE id = v_cust AND profile_id = v_cust_user;

  BEGIN
    UPDATE public.customers SET credit_limit = 99999 WHERE id = v_cust;
    RAISE EXCEPTION 'smoke fail: customer changed credit_limit';
  EXCEPTION
    WHEN OTHERS THEN
      IF SQLERRM LIKE 'smoke fail:%' THEN RAISE; END IF;
  END;

  v_updated := public.update_own_customer_profile(
    p_display_name := 'Return Cust Updated',
    p_email := 'cust-return-updated@gtr.local',
    p_sms_receipts := true
  );
  IF v_updated IS DISTINCT FROM v_cust THEN
    RAISE EXCEPTION 'smoke fail: update_own_customer_profile id mismatch';
  END IF;

  -- ---------------------------------------------------------------------------
  -- Addresses own CRUD; peer denied
  -- ---------------------------------------------------------------------------
  v_addr := public.upsert_customer_address(
    NULL, 'Home', '12 Smoke St', NULL, 'Harare', 'Harare', NULL, 'Zimbabwe', true
  );

  PERFORM public._test_set_auth_uid(v_peer_user);
  BEGIN
    UPDATE public.customer_addresses SET line1 = 'Hacked' WHERE id = v_addr;
    IF FOUND THEN
      RAISE EXCEPTION 'smoke fail: peer updated address';
    END IF;
  EXCEPTION
    WHEN OTHERS THEN
      IF SQLERRM LIKE 'smoke fail:%' THEN RAISE; END IF;
  END;

  PERFORM public._test_set_auth_uid(v_cust_user);
  PERFORM public.delete_customer_address(v_addr);
  v_addr := public.upsert_customer_address(
    NULL, 'Home', '12 Smoke St', NULL, 'Harare', 'Harare', NULL, 'Zimbabwe', true
  );

  -- ---------------------------------------------------------------------------
  -- Customer return → quarantine CN; peer denied; staff RPC still staff-gated
  -- ---------------------------------------------------------------------------
  SELECT jsonb_agg(jsonb_build_object(
    'stock_item_id', sil.stock_item_id,
    'uom_id', sil.uom_id,
    'qty', 1,
    'unit_price', 1
  )) INTO STRICT /* reuse one physical line */
    -- built below
    FROM public.sales_invoice_lines sil
  WHERE false;

  DECLARE
    v_lines JSONB;
    v_wh UUID;
  BEGIN
    SELECT jsonb_build_array(jsonb_build_object(
      'stock_item_id', sil.stock_item_id,
      'uom_id', sil.uom_id,
      'qty', 1,
      'unit_price', 999
    ))
    INTO v_lines
    FROM public.sales_invoice_lines sil
    WHERE sil.invoice_id = v_inv AND sil.is_core_charge = false
    LIMIT 1;

    IF v_lines IS NULL THEN
      RAISE EXCEPTION 'smoke fail: no invoice lines';
    END IF;

    v_cn := public.post_customer_return_credit_note(v_inv, v_lines);

    SELECT warehouse_id INTO v_wh FROM public.sales_invoices WHERE id = v_cn;
    IF v_wh IS DISTINCT FROM v_quar THEN
      RAISE EXCEPTION 'smoke fail: credit note warehouse not quarantine';
    END IF;

    -- Peer cannot return against this invoice
    PERFORM public._test_set_auth_uid(v_peer_user);
    BEGIN
      PERFORM public.request_customer_return(v_inv, v_lines);
      RAISE EXCEPTION 'smoke fail: peer returned foreign invoice';
    EXCEPTION
      WHEN OTHERS THEN
        IF SQLERRM LIKE 'smoke fail:%' THEN RAISE; END IF;
    END;
  END;

  -- Direct staff RPC still requires sales staff
  PERFORM public._test_set_auth_uid(v_cust_user);
  BEGIN
    PERFORM public.post_return_credit_note(
      v_inv,
      '[{"stock_item_id":"' || v_item::text || '","uom_id":"' || v_uom::text || '","qty":1,"unit_price":1}]'::jsonb
    );
    RAISE EXCEPTION 'smoke fail: customer called staff post_return_credit_note';
  EXCEPTION
    WHEN OTHERS THEN
      IF SQLERRM LIKE 'smoke fail:%' THEN RAISE; END IF;
  END;

  RAISE NOTICE 'web_customer_finance_gaps_smoke: PASS inv=% cn=% stmt=% addr=%',
    v_inv, v_cn, v_stmt, v_addr;
END;
$$;
