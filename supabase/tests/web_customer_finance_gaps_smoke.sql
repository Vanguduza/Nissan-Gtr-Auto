-- Smoke: bank recon grants, customer addresses/profile UPDATE, customer return CN.
-- Run as postgres after migrations 20260724170000 + 20260724171000.
-- Does not db-reset.

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
  v_finance UUID := 'a0000000-0000-4000-8000-000000000002'; -- seed finance
  v_cust_user UUID := 'c0000000-0000-4000-8000-0000000000c1';
  v_peer_user UUID := 'c0000000-0000-4000-8000-0000000000c2';
  v_main UUID;
  v_quar UUID;
  v_uom UUID;
  v_list UUID;
  v_item UUID;
  v_cust UUID;
  v_peer UUID;
  v_cart UUID;
  v_inv UUID;
  v_cn UUID;
  v_addr UUID;
  v_stmt UUID;
  v_line UUID;
  v_match UUID;
  v_jel UUID;
  v_updated UUID;
  v_lines JSONB;
  v_wh UUID;
  v_seen INT;
BEGIN
  SELECT id INTO v_main FROM public.warehouses WHERE code = 'MAIN' LIMIT 1;
  SELECT id INTO v_quar FROM public.warehouses WHERE is_quarantine AND is_active ORDER BY code LIMIT 1;
  SELECT id INTO v_uom FROM public.uoms WHERE code = 'EA' LIMIT 1;
  SELECT id INTO v_list FROM public.price_lists WHERE code = 'RETAIL' LIMIT 1;
  IF v_main IS NULL OR v_quar IS NULL OR v_uom IS NULL OR v_list IS NULL THEN
    RAISE EXCEPTION 'smoke fail: MAIN/QUAR/EA/RETAIL required';
  END IF;

  INSERT INTO auth.users (
    instance_id, id, aud, role, email, encrypted_password, email_confirmed_at,
    raw_app_meta_data, raw_user_meta_data, created_at, updated_at,
    confirmation_token, recovery_token, email_change_token_new, email_change
  )
  VALUES
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
    (v_cust_user, 'Cust Return', false),
    (v_peer_user, 'Cust Peer', false)
  ON CONFLICT (id) DO UPDATE
  SET full_name = EXCLUDED.full_name, is_staff = false;

  INSERT INTO public.stock_items (oem_part_number, description, base_uom_id)
  VALUES ('SMOKE-RET-001', 'Return smoke part', v_uom)
  ON CONFLICT (oem_part_number) DO UPDATE
  SET description = EXCLUDED.description,
      base_uom_id = COALESCE(public.stock_items.base_uom_id, EXCLUDED.base_uom_id)
  RETURNING id INTO v_item;
  IF v_item IS NULL THEN
    SELECT id INTO v_item FROM public.stock_items WHERE oem_part_number = 'SMOKE-RET-001';
  END IF;

  INSERT INTO public.price_list_items (price_list_id, stock_item_id, unit_price, core_charge)
  VALUES (v_list, v_item, 25, 0)
  ON CONFLICT (price_list_id, stock_item_id) DO UPDATE
  SET unit_price = 25, core_charge = 0;

  SELECT id INTO v_cust FROM public.customers WHERE profile_id = v_cust_user LIMIT 1;
  IF v_cust IS NULL THEN
    INSERT INTO public.customers (display_name, currency, profile_id, phone_e164, email, price_list_id)
    VALUES ('Return Cust', 'USD', v_cust_user, '+263771000001', 'cust-return@gtr.local', v_list)
    RETURNING id INTO v_cust;
  END IF;

  SELECT id INTO v_peer FROM public.customers WHERE profile_id = v_peer_user LIMIT 1;
  IF v_peer IS NULL THEN
    INSERT INTO public.customers (display_name, currency, profile_id)
    VALUES ('Peer Cust', 'USD', v_peer_user)
    RETURNING id INTO v_peer;
  END IF;

  -- Staff seeds stock + posts sale
  PERFORM public._test_set_auth_uid(v_admin);
  PERFORM set_config('role', 'authenticated', true);

  PERFORM public.post_stock_receipt(
    v_main,
    'return smoke seed',
    jsonb_build_array(
      jsonb_build_object(
        'stock_item_id', v_item, 'uom_id', v_uom, 'qty', 20,
        'unit_cost', 8, 'currency', 'USD', 'valuation_method', 'FIFO'
      )
    )
  );

  v_cart := public.create_pos_cart(v_main, v_cust, 'USD'::public.currency_code);
  PERFORM public.add_cart_line(v_cart, v_item, v_uom, 2);
  v_inv := public.checkout_pos_cart(v_cart);

  -- Bank recon DML as finance (RLS + table grants)
  PERFORM public._test_set_auth_uid(v_finance);
  BEGIN
    SET LOCAL ROLE authenticated;
    INSERT INTO public.bank_statements (
      account_code, currency, statement_date, opening_balance, closing_balance, created_by
    )
    VALUES ('1100', 'USD', CURRENT_DATE, 0, 100, v_finance)
    RETURNING id INTO v_stmt;

    INSERT INTO public.bank_statement_lines (statement_id, line_date, description, amount)
    VALUES (v_stmt, CURRENT_DATE, 'smoke deposit', 100)
    RETURNING id INTO v_line;
    RESET ROLE;
  EXCEPTION
    WHEN OTHERS THEN
      RESET ROLE;
      RAISE;
  END;

  SELECT jel.id INTO v_jel
  FROM public.journal_entry_lines jel
  WHERE jel.account_code = '1100'
  ORDER BY jel.id DESC
  LIMIT 1;

  IF v_jel IS NOT NULL THEN
    PERFORM public._test_set_auth_uid(v_finance);
    BEGIN
      SET LOCAL ROLE authenticated;
      INSERT INTO public.bank_recon_matches (statement_line_id, journal_entry_line_id, matched_by)
      VALUES (v_line, v_jel, v_finance)
      RETURNING id INTO v_match;

      UPDATE public.bank_statement_lines
      SET status = 'matched'
      WHERE id = v_line AND status = 'open';
      RESET ROLE;
    EXCEPTION
      WHEN OTHERS THEN
        RESET ROLE;
        RAISE;
    END;
  END IF;

  -- Customer blocked from bank_statements
  PERFORM public._test_set_auth_uid(v_cust_user);
  BEGIN
    SET LOCAL ROLE authenticated;
    INSERT INTO public.bank_statements (
      account_code, currency, statement_date, opening_balance, closing_balance
    ) VALUES ('1100', 'USD', CURRENT_DATE, 0, 1);
    RESET ROLE;
    RAISE EXCEPTION 'smoke fail: customer inserted bank_statements';
  EXCEPTION
    WHEN OTHERS THEN
      RESET ROLE;
      IF SQLERRM LIKE 'smoke fail:%' THEN RAISE; END IF;
  END;

  -- Own profile UPDATE
  PERFORM public._test_set_auth_uid(v_cust_user);
  BEGIN
    SET LOCAL ROLE authenticated;
    UPDATE public.customers
    SET phone_e164 = '+263771000099', sms_receipts = false, updated_at = now()
    WHERE id = v_cust AND profile_id = v_cust_user;
    RESET ROLE;
  EXCEPTION
    WHEN OTHERS THEN
      RESET ROLE;
      RAISE;
  END;

  BEGIN
    SET LOCAL ROLE authenticated;
    UPDATE public.customers SET credit_limit = 99999 WHERE id = v_cust;
    RESET ROLE;
    RAISE EXCEPTION 'smoke fail: customer changed credit_limit';
  EXCEPTION
    WHEN OTHERS THEN
      RESET ROLE;
      IF SQLERRM LIKE 'smoke fail:%' THEN RAISE; END IF;
  END;

  PERFORM public._test_set_auth_uid(v_cust_user);
  v_updated := public.update_own_customer_profile(
    p_display_name := 'Return Cust Updated',
    p_email := 'cust-return-updated@gtr.local',
    p_sms_receipts := true
  );
  IF v_updated IS DISTINCT FROM v_cust THEN
    RAISE EXCEPTION 'smoke fail: update_own_customer_profile id mismatch';
  END IF;

  -- Addresses
  PERFORM public._test_set_auth_uid(v_cust_user);
  v_addr := public.upsert_customer_address(
    NULL, 'Home', '12 Smoke St', NULL, 'Harare', 'Harare', NULL, 'Zimbabwe', true
  );

  PERFORM public._test_set_auth_uid(v_peer_user);
  BEGIN
    SET LOCAL ROLE authenticated;
    SELECT count(*) INTO v_seen
    FROM public.customer_addresses
    WHERE id = v_addr;
    RESET ROLE;
  EXCEPTION
    WHEN OTHERS THEN
      RESET ROLE;
      RAISE;
  END;
  IF v_seen <> 0 THEN
    RAISE EXCEPTION 'smoke fail: peer can see foreign address';
  END IF;

  PERFORM public._test_set_auth_uid(v_cust_user);
  PERFORM public.delete_customer_address(v_addr);
  v_addr := public.upsert_customer_address(
    NULL, 'Home', '12 Smoke St', NULL, 'Harare', 'Harare', NULL, 'Zimbabwe', true
  );

  -- Customer return → quarantine
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

  PERFORM public._test_set_auth_uid(v_peer_user);
  BEGIN
    PERFORM public.request_customer_return(v_inv, v_lines);
    RAISE EXCEPTION 'smoke fail: peer returned foreign invoice';
  EXCEPTION
    WHEN OTHERS THEN
      IF SQLERRM LIKE 'smoke fail:%' THEN RAISE; END IF;
  END;

  -- Staff RPC remains gated for customers
  PERFORM public._test_set_auth_uid(v_cust_user);
  BEGIN
    PERFORM public.post_return_credit_note(v_inv, v_lines);
    RAISE EXCEPTION 'smoke fail: customer called staff post_return_credit_note';
  EXCEPTION
    WHEN OTHERS THEN
      IF SQLERRM LIKE 'smoke fail:%' THEN RAISE; END IF;
  END;

  RAISE NOTICE 'web_customer_finance_gaps_smoke: PASS inv=% cn=% stmt=% addr=%',
    v_inv, v_cn, v_stmt, v_addr;
END;
$$;
