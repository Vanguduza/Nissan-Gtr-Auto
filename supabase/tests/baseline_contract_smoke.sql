-- Read-only structure and capability assertions for the complete reconciled ERP.
BEGIN;
DO $$
DECLARE
  v_name text;
  v_sig text;
  v_count integer;
BEGIN
  SELECT count(*) INTO v_count FROM pg_tables WHERE schemaname = 'public';
  IF v_count < 210 THEN
    RAISE EXCEPTION 'baseline incomplete: expected at least 210 public tables, got %', v_count;
  END IF;
  IF EXISTS (SELECT 1 FROM pg_tables WHERE schemaname = 'public' AND NOT rowsecurity) THEN
    RAISE EXCEPTION 'baseline has public tables without RLS';
  END IF;
  FOREACH v_name IN ARRAY ARRAY[
    'commerce_orders','inventory_reservations','pos_fulfillment_requests',
    'pos_till_sessions','pos_commerce_tender_settlements','pos_split_payment_sessions',
    'pos_split_payment_legs','pos_card_terminals','pos_card_terminal_attempts',
    'payment_resolution_letters','delivery_cash_collections','payroll_run_funding',
    'catalog_r2_serving_objects','catalog_r2_vehicle_master'
  ] LOOP
    IF to_regclass('public.' || v_name) IS NULL THEN
      RAISE EXCEPTION 'baseline canonical hosted table missing: %', v_name;
    END IF;
  END LOOP;
  IF to_regclass('auth.identities') IS NULL OR to_regclass('storage.objects') IS NULL THEN
    RAISE EXCEPTION 'native Supabase Auth/Storage platform has not initialized';
  END IF;
  -- PostgreSQL need not short-circuit another bucket's policy predicate. Every
  -- helper referenced by an authenticated Storage policy must be executable by
  -- that role, even when testing an unrelated bucket.
  IF EXISTS (
    SELECT 1 FROM pg_policy po
    JOIN pg_depend d ON d.classid = 'pg_policy'::regclass AND d.objid = po.oid
    JOIN pg_proc p ON d.refclassid = 'pg_proc'::regclass AND d.refobjid = p.oid
    WHERE po.polrelid = 'storage.objects'::regclass
      AND (0::oid = ANY(po.polroles) OR
           'authenticated'::regrole::oid = ANY(po.polroles))
      AND NOT has_function_privilege('authenticated', p.oid, 'EXECUTE')
  ) THEN
    RAISE EXCEPTION 'authenticated Storage policy has an uncallable predicate';
  END IF;
  IF has_function_privilege('anon',
      'public._can_access_petty_cash_receipt_object(text)', 'EXECUTE') THEN
    RAISE EXCEPTION 'anonymous access to private receipt predicate';
  END IF;
  SELECT count(*) INTO v_count FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace
  WHERE n.nspname='public' AND p.proname='create_pos_cart';
  IF v_count <> 1 THEN
    RAISE EXCEPTION 'cart RPC must have one unambiguous defaulted constructor, got %', v_count;
  END IF;
  FOREACH v_sig IN ARRAY ARRAY[
    'public.mark_contipay_settled(text,text,text,jsonb,public.currency_code,numeric,numeric,boolean,text)',
    'public.mark_paynow_settled(text,text,text,jsonb,public.currency_code,numeric,numeric,boolean,text)',
    'public.mark_ecocash_settled(text,text,text,jsonb,public.currency_code,numeric,numeric,boolean,text)'
  ] LOOP
    IF has_function_privilege('anon', v_sig, 'EXECUTE') OR
       has_function_privilege('authenticated', v_sig, 'EXECUTE') OR
       NOT has_function_privilege('service_role', v_sig, 'EXECUTE') THEN
      RAISE EXCEPTION 'payment settlement must remain service-role-only: %', v_sig;
    END IF;
  END LOOP;
  FOREACH v_sig IN ARRAY ARRAY[
    'public.checkout_pos_cart(uuid,text,text,text)',
    'public.post_pos_refund(uuid,text)',
    'public.settle_invoice_tenders(uuid,jsonb)',
    'public.search_catalog(text,text)',
    'public.upsert_staff_product_page(uuid,numeric,text,numeric,text,boolean,boolean,boolean,smallint)'
  ] LOOP
    IF has_function_privilege('anon', v_sig, 'EXECUTE') THEN
      RAISE EXCEPTION 'anonymous staff mutation capability: %', v_sig;
    END IF;
  END LOOP;
  IF has_schema_privilege('anon','private','USAGE') OR
     has_schema_privilege('authenticated','private','USAGE') THEN
    RAISE EXCEPTION 'client role can enter private workflow schema';
  END IF;
  RAISE NOTICE 'baseline contract smoke OK: hosted capabilities, RLS and payment authority retained';
END;
$$;
ROLLBACK;
