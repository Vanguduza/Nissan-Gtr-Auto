-- Single-role rules, checked as the simulated staff (supabase/sim/seed.py) who each hold only their
-- real roles. Earlier checks used one account with every role, which hid production bugs (a cashier
-- could not finish a sale, a driver could read the customer's delivery code, ...).
-- Runs after supabase/sim/ci/run.sh has seeded the stack; everything is rolled back.
\set ON_ERROR_STOP on
BEGIN;

CREATE FUNCTION pg_temp.uid(p_email text) RETURNS uuid LANGUAGE sql AS $$ SELECT id FROM auth.users WHERE email = p_email $$;

-- Act as a signed-in user for the rest of the transaction (until pg_temp.as_admin()).
CREATE FUNCTION pg_temp.as_user(p_email text) RETURNS void LANGUAGE plpgsql AS $$
BEGIN
  PERFORM set_config('request.jwt.claims', json_build_object('sub', pg_temp.uid(p_email), 'role', 'authenticated')::text, true);
  EXECUTE 'SET LOCAL ROLE authenticated';
END $$;
CREATE FUNCTION pg_temp.as_admin() RETURNS void LANGUAGE plpgsql AS $$
BEGIN
  EXECUTE 'RESET ROLE';
  PERFORM set_config('request.jwt.claims', '', true);
END $$;

-- Asserts [p_sql] fails (as the current user) with a message containing [p_expect].
CREATE FUNCTION pg_temp.refused(p_label text, p_sql text, p_expect text DEFAULT NULL) RETURNS void LANGUAGE plpgsql AS $$
BEGIN
  BEGIN
    EXECUTE p_sql;
  EXCEPTION WHEN OTHERS THEN
    IF p_expect IS NOT NULL AND position(lower(p_expect) IN lower(SQLERRM)) = 0 THEN
      RAISE EXCEPTION 'RULE % : refused for the wrong reason: %', p_label, SQLERRM;
    END IF;
    RAISE NOTICE 'ok  % (refused: %)', p_label, SQLERRM;
    RETURN;
  END;
  RAISE EXCEPTION 'RULE % : was allowed', p_label;
END $$;

GRANT EXECUTE ON ALL FUNCTIONS IN SCHEMA pg_temp TO authenticated;

DO $$ BEGIN
  IF pg_temp.uid('cashier@sim.gtr') IS NULL THEN RAISE EXCEPTION 'seed the simulation first (supabase/sim/seed.py)'; END IF;
END $$;

-- 1. A cashier (sales role only) can complete a cash sale at the counter.
DO $$
DECLARE v_wh uuid := (SELECT id FROM public.warehouses WHERE code = 'MAIN');
        v_item record; v_cart uuid; v_till uuid; v_order uuid; v_total numeric; v_res jsonb;
BEGIN
  SELECT si.id, si.base_uom_id INTO v_item FROM public.stock_levels sl JOIN public.stock_items si ON si.id = sl.stock_item_id
   WHERE sl.warehouse_id = v_wh AND NOT si.requires_serial
   ORDER BY public.get_inventory_available_quantity(si.id, v_wh) DESC LIMIT 1;
  PERFORM pg_temp.as_user('cashier@sim.gtr');
  SELECT (public.get_my_open_pos_till_session('sim-rules-tablet')->>'id')::uuid INTO v_till;
  IF v_till IS NULL THEN v_till := public.open_pos_till_session(v_wh, 'sim-rules-tablet', 20, 'USD'); END IF;
  v_cart := public.create_pos_cart(v_wh, NULL, 'USD', 'immediate');
  PERFORM public.add_cart_line(v_cart, v_item.id, v_item.base_uom_id, 1);
  PERFORM public.attach_pos_cart_till_session(v_cart, v_till);
  v_order := public.prepare_pos_commerce_checkout_v2(v_cart, gen_random_uuid(), '20 minutes', NULL, NULL, NULL);
  v_total := (public.get_pos_payment_status(v_order)->>'total')::numeric;
  v_res := public.settle_pos_commerce_tenders(v_order, gen_random_uuid(), jsonb_build_array(jsonb_build_object('tender', 'cash', 'amount', v_total)));
  IF v_res->>'invoice_id' IS NULL THEN RAISE EXCEPTION 'RULE cashier sells: no invoice'; END IF;
  RAISE NOTICE 'ok  cashier completes a cash sale (%)', v_res->>'invoice_id';
  PERFORM pg_temp.as_admin();
END $$;

-- 2. A cashier cannot do manager things.
DO $$
DECLARE v_terminal uuid := (SELECT id FROM public.pos_card_terminals LIMIT 1);
        v_cart uuid := (SELECT id FROM public.pos_carts WHERE status = 'open' LIMIT 1);
BEGIN
  PERFORM pg_temp.as_user('cashier@sim.gtr');
  PERFORM pg_temp.refused('cashier voids a sale', format('SELECT public.void_pos_cart_governed(%L, %L, NULL)', v_cart, 'operator_error'));
  PERFORM pg_temp.refused('cashier lifts a suspension', $q$ SELECT public.lift_customer_suspension(gen_random_uuid(), 'x') $q$, 'manager');
  PERFORM pg_temp.refused('cashier sees the exception report', $q$ SELECT public.get_exception_report(NULL, NULL, NULL) $q$);
  PERFORM pg_temp.refused('cashier sees the daily dashboard', $q$ SELECT public.get_daily_dashboard(NULL, NULL) $q$);
  PERFORM pg_temp.refused('cashier sees restock suggestions', $q$ SELECT public.get_restock_suggestions(NULL, 28, 14, 7, 28) $q$);
  PERFORM pg_temp.refused('cashier pairs a card machine', format('SELECT public.register_pos_card_terminal_device_key(%L, %L, %L, repeat(%L, 64))', v_terminal, 'x', 'eA==', '0'), 'admin');
  PERFORM pg_temp.as_admin();
END $$;

-- 3. A driver never sees the customer's delivery code, and cannot count or approve cash.
DO $$
DECLARE v_job uuid; v_code text;
BEGIN
  SELECT id INTO v_job FROM public.delivery_jobs WHERE status = 'dispatched' AND assignee_user_id = pg_temp.uid('driver1@sim.gtr') LIMIT 1;
  PERFORM pg_temp.as_user('driver1@sim.gtr');
  IF v_job IS NOT NULL THEN
    v_code := public.generate_delivery_pod_otp(v_job, NULL);
    IF v_code IS NOT NULL THEN RAISE EXCEPTION 'RULE driver must not receive the delivery code'; END IF;
    RAISE NOTICE 'ok  driver sends the code but does not receive it';
  ELSE
    RAISE NOTICE 'skip driver code check: no dispatched job for driver1';
  END IF;
  IF jsonb_array_length(public.get_my_delivery_codes()) <> 0 THEN RAISE EXCEPTION 'RULE driver sees customer delivery codes'; END IF;
  RAISE NOTICE 'ok  driver sees no customer delivery codes';
  PERFORM pg_temp.refused('driver reads stored codes', $q$ SELECT count(*) FROM private.delivery_pod_codes $q$);
  PERFORM pg_temp.refused('driver reads code hashes', $q$ SELECT count(*) FROM public.delivery_pod_otps $q$);
  PERFORM pg_temp.refused('driver lists all driver cash', $q$ SELECT public.list_driver_cash(NULL, 10) $q$);
  PERFORM pg_temp.refused('driver approves a cash difference', $q$ SELECT public.approve_driver_cash_variance(gen_random_uuid(), 'driver_short', NULL) $q$);
  PERFORM pg_temp.as_admin();
END $$;

-- 4. Nobody approves their own count: driver cash, stock transfers.
DO $$
DECLARE v_h uuid; v_e uuid;
BEGIN
  SELECT id INTO v_h FROM public.driver_cash_handins WHERE status = 'variance_pending' AND received_by = pg_temp.uid('manager@sim.gtr') LIMIT 1;
  IF v_h IS NOT NULL THEN
    PERFORM pg_temp.as_user('manager@sim.gtr');
    PERFORM pg_temp.refused('manager approves a difference they counted', format('SELECT public.approve_driver_cash_variance(%L, %L, NULL)', v_h, 'count_error'), 'different manager');
    PERFORM pg_temp.as_admin();
  END IF;
  SELECT id INTO v_e FROM public.stock_entries WHERE entry_type = 'transfer' AND status = 'pending_approval'
     AND COALESCE(first_approver_id, created_by) = pg_temp.uid('warehouse@sim.gtr') LIMIT 1;
  IF v_e IS NOT NULL THEN
    PERFORM pg_temp.as_user('warehouse@sim.gtr');
    PERFORM pg_temp.refused('warehouse receives a transfer they sent', format('SELECT public.approve_stock_transfer(%L)', v_e), 'different');
    PERFORM pg_temp.as_admin();
  END IF;
  RAISE NOTICE 'ok  self-approval checks done';
END $$;

-- 5. Signed-out visitors cannot call staff or money functions.
DO $$
DECLARE f text; bad text[] := '{}';
BEGIN
  FOREACH f IN ARRAY ARRAY[
    'public.settle_pos_commerce_tenders(uuid,uuid,jsonb)', 'public.checkout_pos_cart_on_account(uuid,text,text,text)',
    'public.submit_driver_cash_handin(public.currency_code,numeric,text)', 'public.receive_driver_cash_handin(uuid,numeric,text,text)',
    'public.list_my_approvals()', 'public.get_daily_dashboard(date,uuid)', 'public.get_exception_report(date,date,uuid)',
    'public.get_restock_suggestions(uuid,integer,integer,integer,integer)', 'public.get_my_delivery_codes()',
    'public.generate_delivery_pod_otp(uuid,interval)', 'public.lift_customer_suspension(uuid,text)'] LOOP
    IF has_function_privilege('anon', f, 'execute') THEN bad := bad || f; END IF;
  END LOOP;
  IF array_length(bad, 1) > 0 THEN RAISE EXCEPTION 'RULE anon can execute: %', bad; END IF;
  RAISE NOTICE 'ok  signed-out visitors cannot call staff or money functions';
END $$;

-- 7. Suspensions: only managers, finance and admin read the list; looking one up changes nothing.
DO $$
DECLARE v_before bigint := (SELECT count(*) FROM public.customer_suspensions); v_seen bigint; c uuid;
BEGIN
  PERFORM pg_temp.as_user('warehouse@sim.gtr');
  PERFORM pg_temp.refused('warehouse lists suspensions', $q$ SELECT public.list_customer_suspensions(NULL, 10) $q$, 'manager');
  SELECT count(*) INTO v_seen FROM public.customer_suspensions;
  IF v_seen <> 0 THEN RAISE EXCEPTION 'RULE warehouse reads % suspensions directly', v_seen; END IF;
  PERFORM pg_temp.as_admin();
  PERFORM pg_temp.as_user('cashier@sim.gtr');
  PERFORM pg_temp.refused('cashier lists suspensions', $q$ SELECT public.list_customer_suspensions(NULL, 10) $q$, 'manager');
  PERFORM pg_temp.as_admin();
  FOR c IN SELECT id FROM public.customers LOOP
    PERFORM pg_temp.as_user('cashier@sim.gtr');
    PERFORM public.get_customer_suspension(c);  -- the counter checks the customer in front of it
    PERFORM pg_temp.as_admin();
  END LOOP;
  IF (SELECT count(*) FROM public.customer_suspensions) <> v_before THEN RAISE EXCEPTION 'RULE looking up a suspension created one'; END IF;
  RAISE NOTICE 'ok  suspensions: list for managers only; lookups change nothing';
END $$;

-- 6. Every table has row level security.
DO $$
DECLARE v text[];
BEGIN
  SELECT array_agg(n.nspname || '.' || c.relname) INTO v FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
   WHERE c.relkind = 'r' AND n.nspname IN ('public', 'private') AND NOT c.relrowsecurity;
  IF v IS NOT NULL THEN RAISE EXCEPTION 'RULE tables without row level security: %', v; END IF;
  RAISE NOTICE 'ok  every public/private table has row level security';
END $$;

ROLLBACK;
