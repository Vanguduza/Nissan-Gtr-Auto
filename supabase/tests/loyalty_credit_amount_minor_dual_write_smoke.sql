-- B-MONEY-1: loyalty / store credit / credit-limit amount_minor dual-write smoke.
-- Prove columns + triggers + get_loyalty_balance minor return. Requires admin seed.

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
  v_cust UUID;
  v_limit_m BIGINT;
  v_open_m BIGINT;
  v_bal_m BIGINT;
  v_amt_m BIGINT;
  v_after_m BIGINT;
  v_ledger UUID;
  v_money_m BIGINT;
  v_est NUMERIC;
  v_est_m BIGINT;
  v_rpc_limit NUMERIC;
  v_rpc_limit_m BIGINT;
BEGIN
  PERFORM public._test_set_auth_uid(v_admin);

  INSERT INTO public.customers (display_name, currency, credit_limit, open_balance)
  VALUES ('B-MONEY-1 credit dual-write', 'USD', 100.00, 25.50)
  RETURNING id, credit_limit_minor, open_balance_minor
  INTO v_cust, v_limit_m, v_open_m;

  IF v_limit_m IS DISTINCT FROM 10000 OR v_open_m IS DISTINCT FROM 2550 THEN
    RAISE EXCEPTION
      'B-MONEY-1 smoke fail: customer minors want 10000/2550 got %/%',
      v_limit_m, v_open_m;
  END IF;

  v_ledger := public.issue_store_credit(v_cust, 15.00, 'USD', 1, 'B-MONEY-1 SC', '1100');

  SELECT a.balance_minor, l.amount_minor, l.balance_after_minor
  INTO v_bal_m, v_amt_m, v_after_m
  FROM public.store_credit_accounts a
  JOIN public.store_credit_ledger l ON l.account_id = a.id
  WHERE a.customer_id = v_cust AND l.id = v_ledger;

  IF v_bal_m IS DISTINCT FROM 1500 OR v_amt_m IS DISTINCT FROM 1500
     OR v_after_m IS DISTINCT FROM 1500 THEN
    RAISE EXCEPTION
      'B-MONEY-1 smoke fail: store credit minors want 1500 got bal=% amt=% after=%',
      v_bal_m, v_amt_m, v_after_m;
  END IF;

  v_ledger := public.earn_loyalty_points(v_cust, 1000, 'USD', 1, 'B-MONEY-1 earn');

  SELECT money_value_minor INTO v_money_m
  FROM public.loyalty_ledger WHERE id = v_ledger;

  IF v_money_m IS DISTINCT FROM 1000 THEN
    RAISE EXCEPTION
      'B-MONEY-1 smoke fail: loyalty money_value_minor want 1000 got %',
      v_money_m;
  END IF;

  SELECT estimated_liability, estimated_liability_minor
  INTO v_est, v_est_m
  FROM public.get_loyalty_balance(v_cust);

  IF v_est IS DISTINCT FROM 10.00 OR v_est_m IS DISTINCT FROM 1000 THEN
    RAISE EXCEPTION
      'B-MONEY-1 smoke fail: get_loyalty_balance want 10/1000 got %/%',
      v_est, v_est_m;
  END IF;

  SELECT credit_limit, credit_limit_minor
  INTO v_rpc_limit, v_rpc_limit_m
  FROM public.set_customer_credit(v_cust, 200.00, NULL);

  IF v_rpc_limit IS DISTINCT FROM 200.00 OR v_rpc_limit_m IS DISTINCT FROM 20000 THEN
    RAISE EXCEPTION
      'B-MONEY-1 smoke fail: set_customer_credit want 200/20000 got %/%',
      v_rpc_limit, v_rpc_limit_m;
  END IF;

  RAISE NOTICE 'B-MONEY-1 loyalty/credit amount_minor dual-write smoke OK cust=%', v_cust;
END;
$$;
