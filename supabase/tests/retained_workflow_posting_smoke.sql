-- Real cashier deposit/manager refund, context restoration and ledger isolation.
-- The full native simulation separately covers returns, card recovery/refunds,
-- delivery COD and driver cash shortage/recovery with their original actors.
BEGIN;
DO $$
DECLARE
  v_cashier uuid := gen_random_uuid();
  v_manager uuid := gen_random_uuid();
  v_driver uuid := gen_random_uuid();
  v_peer_driver uuid := gen_random_uuid();
  v_outsider uuid := gen_random_uuid();
  v_main uuid;
  v_uom uuid;
  v_item uuid;
  v_customer uuid;
  v_grade uuid;
  v_request uuid;
  v_till uuid;
  v_deposit jsonb;
  v_refund jsonb;
  v_journal uuid;
  v_refund_journal uuid;
  v_signature text;
  v_setting text;
  v_sql text;
  v_error text;
  v_actor uuid;
  v_invoice uuid;
  v_note uuid;
  v_job uuid;
  v_cash_request uuid := gen_random_uuid();
  v_collection jsonb;
  v_expected text[] := ARRAY['caller-pos','caller-sales','caller-payments','caller-delivery'];
  v_current text[];
  v_count integer;
BEGIN
  PERFORM set_config('request.jwt.claim.role', 'service_role', true);
  SELECT id INTO STRICT v_main FROM public.warehouses WHERE code = 'MAIN';
  SELECT id INTO STRICT v_uom FROM public.uoms WHERE code = 'EA';
  INSERT INTO auth.users(id,email) VALUES
    (v_cashier, v_cashier::text || '@posting-scope.test'),
    (v_manager, v_manager::text || '@posting-scope.test'),
    (v_driver, v_driver::text || '@posting-scope.test'),
    (v_peer_driver, v_peer_driver::text || '@posting-scope.test'),
    (v_outsider, v_outsider::text || '@posting-scope.test');
  INSERT INTO public.profiles(id,is_staff) VALUES(v_cashier,true),(v_manager,true)
    ON CONFLICT(id) DO UPDATE SET is_staff = true;
  INSERT INTO public.staff_roles(user_id,role) VALUES(v_cashier,'sales'),(v_manager,'sales');
  INSERT INTO public.staff_roles(user_id,role) VALUES(v_driver,'driver'),(v_peer_driver,'driver');
  SELECT id INTO STRICT v_grade FROM public.hr_grades WHERE code = 'C1';
  INSERT INTO public.employees(user_id,employee_code,full_name,grade_id,status)
    VALUES(v_cashier,'SCOPE-C-' || left(v_cashier::text,8),'Scope Cashier',v_grade,'active');
  SELECT id INTO STRICT v_grade FROM public.hr_grades WHERE code = 'A1';
  INSERT INTO public.employees(user_id,employee_code,full_name,grade_id,status)
    VALUES(v_manager,'SCOPE-M-' || left(v_manager::text,8),'Scope Manager',v_grade,'active');
  INSERT INTO public.customers(display_name,currency)
    VALUES('Posting scope rollback customer','USD') RETURNING id INTO v_customer;
  INSERT INTO public.stock_items(oem_part_number,description,base_uom_id)
    VALUES('SCOPE-' || left(v_cashier::text,8),'Posting scope rollback item',v_uom)
    RETURNING id INTO v_item;

  IF has_schema_privilege('authenticated','private','USAGE') OR
     has_schema_privilege('anon','private','USAGE') THEN
    RAISE EXCEPTION 'smoke fail: private workflow schema exposed';
  END IF;
  FOREACH v_signature IN ARRAY ARRAY[
    'private.post_pos_return_case_core(uuid)',
    'private.take_pos_fulfillment_deposit_core(uuid,numeric,public.currency_code,public.payment_tender,uuid,text)',
    'private.refund_pos_fulfillment_deposit_core(uuid,uuid,text)',
    'private.finalize_pos_card_terminal_purchase_core(uuid)',
    'private.finalize_pos_card_terminal_purchase_full_legacy_core(uuid)',
    'private.finalize_pos_card_terminal_refund_core(uuid,text)',
    'private.finalize_delivery_card_terminal_payment_core(uuid)',
    'private.collect_delivery_cash_core(uuid,numeric,uuid,text)',
    'private._driver_cash_journal_core(public.driver_cash_handins,text,text,numeric,text)'
  ] LOOP
    IF has_function_privilege('anon',v_signature,'EXECUTE') OR
       has_function_privilege('authenticated',v_signature,'EXECUTE') OR
       has_function_privilege('service_role',v_signature,'EXECUTE') THEN
      RAISE EXCEPTION 'smoke fail: workflow core callable by client role: %',v_signature;
    END IF;
  END LOOP;

  PERFORM set_config('request.jwt.claim.sub',v_cashier::text,true);
  PERFORM set_config('request.jwt.claim.role','authenticated',true);
  PERFORM set_config('request.jwt.claims',json_build_object('sub',v_cashier,'role','authenticated')::text,true);
  IF public.is_pos_approver() OR public.has_staff_role(ARRAY['admin','finance']::public.staff_role[]) THEN
    RAISE EXCEPTION 'smoke fail: cashier fixture has elevated authority';
  END IF;
  PERFORM set_config('app.pos_posting',v_expected[1],true);
  PERFORM set_config('app.sales_checkout_accounting',v_expected[2],true);
  PERFORM set_config('app.payments_rpc',v_expected[3],true);
  PERFORM set_config('app.delivery_payment_rpc',v_expected[4],true);
  PERFORM set_config('request.jwt.claim.sub',v_outsider::text,true);
  PERFORM set_config('request.jwt.claims',json_build_object('sub',v_outsider,'role','authenticated')::text,true);
  SET LOCAL ROLE authenticated;
  FOR v_sql,v_error IN SELECT * FROM (VALUES
    ('SELECT public.post_pos_return_case(NULL::uuid)','POS manager approval required'),
    ('SELECT public.take_pos_fulfillment_deposit(NULL::uuid,1,''USD'',''cash'',NULL,NULL)','sales, warehouse, or admin role required'),
    ('SELECT public.refund_pos_fulfillment_deposit(NULL::uuid,NULL,NULL)','POS manager approval required'),
    ('SELECT public.finalize_pos_card_terminal_purchase(NULL::uuid)','admin, finance, or sales role required for payments'),
    ('SELECT public.finalize_pos_card_terminal_purchase_full_legacy(NULL::uuid)','admin, finance, or sales role required for payments'),
    ('SELECT public.finalize_pos_card_terminal_refund(NULL::uuid,NULL)','POS manager or finance approval required'),
    ('SELECT public.finalize_delivery_card_terminal_payment(NULL::uuid)','delivery card payment belongs to another driver'),
    ('SELECT public.collect_delivery_cash(NULL::uuid,1,NULL::uuid,NULL)','assigned dispatched driver job required')
  ) cases(call_sql,expected_error) LOOP
    BEGIN
      EXECUTE v_sql;
      RAISE EXCEPTION 'smoke fail: nonstaff caller entered financial workflow: %',v_sql;
    EXCEPTION WHEN OTHERS THEN
      IF SQLERRM <> v_error THEN RAISE; END IF;
    END;
    v_current := ARRAY[current_setting('app.pos_posting',true),current_setting('app.sales_checkout_accounting',true),
      current_setting('app.payments_rpc',true),current_setting('app.delivery_payment_rpc',true)];
    IF v_current IS DISTINCT FROM v_expected THEN
      RAISE EXCEPTION 'smoke fail: rejected nonstaff caller changed posting context';
    END IF;
  END LOOP;
  RESET ROLE;
  PERFORM set_config('request.jwt.claim.sub',v_cashier::text,true);
  PERFORM set_config('request.jwt.claims',json_build_object('sub',v_cashier,'role','authenticated')::text,true);
  SET LOCAL ROLE authenticated;
  BEGIN
    PERFORM public._sales_checkout_accounting_enter();
    RAISE EXCEPTION 'smoke fail: client enabled internal accounting context';
  EXCEPTION WHEN insufficient_privilege THEN NULL;
  END;
  v_request := public.create_pos_fulfillment_request('backorder',v_item,v_uom,1,NULL,v_main,v_customer);
  v_till := public.open_pos_till_session(v_main,'posting-scope-rollback',0,'USD');
  BEGIN
    PERFORM public.take_pos_fulfillment_deposit(v_request,10,'USD','cash',NULL,NULL);
    RAISE EXCEPTION 'smoke fail: missing till was accepted';
  EXCEPTION WHEN OTHERS THEN
    IF SQLERRM NOT LIKE 'open till required%' THEN RAISE; END IF;
  END;
  v_current := ARRAY[current_setting('app.pos_posting',true),current_setting('app.sales_checkout_accounting',true),
    current_setting('app.payments_rpc',true),current_setting('app.delivery_payment_rpc',true)];
  IF v_current IS DISTINCT FROM v_expected THEN
    RAISE EXCEPTION 'smoke fail: failed deposit changed caller contexts: %',v_current;
  END IF;
  v_deposit := public.take_pos_fulfillment_deposit(v_request,10,'USD','cash',v_till,NULL);
  v_current := ARRAY[current_setting('app.pos_posting',true),current_setting('app.sales_checkout_accounting',true),
    current_setting('app.payments_rpc',true),current_setting('app.delivery_payment_rpc',true)];
  IF v_current IS DISTINCT FROM v_expected THEN
    RAISE EXCEPTION 'smoke fail: successful deposit changed caller contexts: %',v_current;
  END IF;
  BEGIN
    PERFORM public.refund_pos_fulfillment_deposit((v_deposit->>'id')::uuid,v_till,'refund');
    RAISE EXCEPTION 'smoke fail: cashier approved own refund';
  EXCEPTION WHEN OTHERS THEN
    IF SQLERRM <> 'POS manager approval required' THEN RAISE; END IF;
  END;
  PERFORM public.cancel_pos_fulfillment_request(v_request,'scope regression');
  RESET ROLE;

  PERFORM set_config('request.jwt.claim.sub',v_manager::text,true);
  PERFORM set_config('request.jwt.claims',json_build_object('sub',v_manager,'role','authenticated')::text,true);
  IF NOT public.is_pos_approver() OR public.has_staff_role(ARRAY['admin','finance']::public.staff_role[]) THEN
    RAISE EXCEPTION 'smoke fail: manager fixture must approve POS without generic finance authority';
  END IF;
  SET LOCAL ROLE authenticated;
  v_refund := public.refund_pos_fulfillment_deposit((v_deposit->>'id')::uuid,v_till,'order cancelled');
  IF v_refund->>'status' <> 'refunded' THEN RAISE EXCEPTION 'smoke fail: refund not posted'; END IF;
  v_current := ARRAY[current_setting('app.pos_posting',true),current_setting('app.sales_checkout_accounting',true),
    current_setting('app.payments_rpc',true),current_setting('app.delivery_payment_rpc',true)];
  IF v_current IS DISTINCT FROM v_expected THEN
    RAISE EXCEPTION 'smoke fail: successful refund changed caller contexts: %',v_current;
  END IF;
  BEGIN
    PERFORM public.post_journal_entry(CURRENT_DATE,'direct manager ledger write','USD',1,
      '[{"account_code":"1120","debit":1},{"account_code":"2200","credit":1}]'::jsonb);
    RAISE EXCEPTION 'smoke fail: manager obtained generic ledger authority';
  EXCEPTION WHEN OTHERS THEN
    IF SQLERRM <> 'finance or admin role required' THEN RAISE; END IF;
  END;
  BEGIN
    PERFORM public.refund_pos_fulfillment_deposit((v_deposit->>'id')::uuid,v_till,'repeat');
    RAISE EXCEPTION 'smoke fail: deposit refunded twice';
  EXCEPTION WHEN OTHERS THEN
    IF SQLERRM <> 'deposit held for the customer required' THEN RAISE; END IF;
  END;
  RESET ROLE;
  SELECT journal_entry_id,refund_journal_entry_id INTO STRICT v_journal,v_refund_journal
    FROM public.pos_fulfillment_deposits WHERE id=(v_deposit->>'id')::uuid;
  SELECT count(*) INTO v_count FROM public.journal_entries
    WHERE id IN(v_journal,v_refund_journal) AND status='posted';
  IF v_count <> 2 OR EXISTS (
    SELECT 1 FROM public.journal_entry_lines WHERE journal_entry_id IN(v_journal,v_refund_journal)
    GROUP BY journal_entry_id HAVING sum(debit) <> sum(credit) OR sum(debit) <> 10
  ) THEN RAISE EXCEPTION 'smoke fail: real deposit/refund journals missing or unbalanced'; END IF;
  IF EXISTS (
    SELECT 1 FROM public.journal_entry_lines WHERE journal_entry_id IN(v_journal,v_refund_journal)
    GROUP BY account_code HAVING sum(debit-credit) <> 0
  ) OR (SELECT balance FROM public.store_credit_accounts WHERE customer_id=v_customer AND currency='USD') <> 0 THEN
    RAISE EXCEPTION 'smoke fail: refunded deposit left a cash/credit imbalance';
  END IF;

  -- An independent posted COD fixture exercises actual driver payment posting,
  -- its own retry, and peer/job request-ID isolation. No stock or sale is faked
  -- by the payment RPC: its job/invoice eligibility checks still run unchanged.
  PERFORM set_config('request.jwt.claim.role','service_role',true);
  INSERT INTO public.sales_invoices(warehouse_id,customer_id,status,subtotal,total,currency,
    fulfillment_mode,delivery_payment_method)
    VALUES(v_main,v_customer,'posted',12,12,'USD','dispatch','cash_on_delivery')
    RETURNING id INTO v_invoice;
  PERFORM public._logistics_begin_rpc(); -- migration-owner-only fixture setup
  INSERT INTO public.delivery_notes(sales_invoice_id,warehouse_id,status)
    VALUES(v_invoice,v_main,'submitted') RETURNING id INTO v_note;
  INSERT INTO public.delivery_jobs(delivery_note_id,assignee_user_id,status)
    VALUES(v_note,v_driver,'dispatched') RETURNING id INTO v_job;
  PERFORM set_config('app.logistics_rpc','',true);
  PERFORM set_config('request.jwt.claim.role','authenticated',true);
  PERFORM set_config('request.jwt.claim.sub',v_driver::text,true);
  PERFORM set_config('request.jwt.claims',json_build_object('sub',v_driver,'role','authenticated')::text,true);
  SET LOCAL ROLE authenticated;
  BEGIN
    PERFORM public.collect_delivery_cash(v_job,12,NULL,NULL);
    RAISE EXCEPTION 'smoke fail: missing cash request ID accepted';
  EXCEPTION WHEN OTHERS THEN
    IF SQLERRM <> 'cash collection request id required' THEN RAISE; END IF;
  END;
  v_collection := public.collect_delivery_cash(v_job,12,v_cash_request,'scope regression');
  IF public.collect_delivery_cash(v_job,12,v_cash_request,NULL)->>'payment_entry_id'
       IS DISTINCT FROM v_collection->>'payment_entry_id' THEN
    RAISE EXCEPTION 'smoke fail: own cash retry created another payment';
  END IF;
  BEGIN
    PERFORM public.collect_delivery_cash(gen_random_uuid(),12,v_cash_request,NULL);
    RAISE EXCEPTION 'smoke fail: cash request ID reused for another job';
  EXCEPTION WHEN OTHERS THEN
    IF SQLERRM <> 'delivery cash collection request belongs to another driver or job' THEN RAISE; END IF;
  END;
  RESET ROLE;
  PERFORM set_config('request.jwt.claim.sub',v_peer_driver::text,true);
  PERFORM set_config('request.jwt.claims',json_build_object('sub',v_peer_driver,'role','authenticated')::text,true);
  SET LOCAL ROLE authenticated;
  BEGIN
    PERFORM public.collect_delivery_cash(v_job,12,v_cash_request,NULL);
    RAISE EXCEPTION 'smoke fail: peer driver read cached cash collection';
  EXCEPTION WHEN OTHERS THEN
    IF SQLERRM <> 'delivery cash collection request belongs to another driver or job' THEN RAISE; END IF;
  END;
  RESET ROLE;
  IF (SELECT amount_paid FROM public.sales_invoices WHERE id=v_invoice) <> 12 OR
     (SELECT count(*) FROM public.delivery_cash_collections WHERE request_id=v_cash_request) <> 1 THEN
    RAISE EXCEPTION 'smoke fail: driver cash payment missing or duplicated';
  END IF;
  v_current := ARRAY[current_setting('app.pos_posting',true),current_setting('app.sales_checkout_accounting',true),
    current_setting('app.payments_rpc',true),current_setting('app.delivery_payment_rpc',true)];
  IF v_current IS DISTINCT FROM v_expected THEN
    RAISE EXCEPTION 'smoke fail: driver success/rejection changed caller contexts: %',v_current;
  END IF;

  -- Direct ledger calls stay denied for every operational actor after successful
  -- and rejected workflows. Legacy flags alone are deliberately insufficient.
  FOREACH v_actor IN ARRAY ARRAY[v_cashier,v_manager,v_driver] LOOP
    PERFORM set_config('request.jwt.claim.sub',v_actor::text,true);
    PERFORM set_config('request.jwt.claims',json_build_object('sub',v_actor,'role','authenticated')::text,true);
    SET LOCAL ROLE authenticated;
    BEGIN
      PERFORM private.post_pos_return_case_core(gen_random_uuid());
      RAISE EXCEPTION 'smoke fail: actor called private workflow core';
    EXCEPTION WHEN insufficient_privilege THEN NULL;
    END;
    FOREACH v_setting IN ARRAY ARRAY['app.sales_checkout_accounting','app.payments_rpc','app.delivery_payment_rpc'] LOOP
      PERFORM set_config(v_setting,'1',true);
      BEGIN
        PERFORM public.post_journal_entry(CURRENT_DATE,'direct operational ledger write','USD',1,
          '[{"account_code":"1120","debit":1},{"account_code":"2200","credit":1}]'::jsonb);
        RAISE EXCEPTION 'smoke fail: actor obtained generic ledger authority using %',v_setting;
      EXCEPTION WHEN OTHERS THEN
        IF SQLERRM <> 'finance or admin role required' THEN RAISE; END IF;
      END;
      BEGIN
        PERFORM public.post_journal(v_journal);
        RAISE EXCEPTION 'smoke fail: actor posted journal using %',v_setting;
      EXCEPTION WHEN OTHERS THEN
        IF SQLERRM <> 'finance or admin role required' THEN RAISE; END IF;
      END;
      PERFORM set_config('app.sales_checkout_accounting',v_expected[2],true);
      PERFORM set_config('app.payments_rpc',v_expected[3],true);
      PERFORM set_config('app.delivery_payment_rpc',v_expected[4],true);
    END LOOP;
    RESET ROLE;
  END LOOP;
  RAISE NOTICE 'retained_workflow_posting_smoke: PASS deposits/refunds/COD, own retry, peer/job denial, private cores and ledger/context isolation';
END;
$$;
ROLLBACK;
