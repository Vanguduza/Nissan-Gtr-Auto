-- Phase 16 slice 4 — loyalty / points smoke (postgres).
-- Liability rule: earn credits 2210; redeem debits 2210 / credits 1200; expire debits 2210 / credits 5350.
-- Requires MAIN warehouse, EA uom, RETAIL price list, admin seed user.

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
  v_main UUID;
  v_uom UUID;
  v_list UUID;
  v_item UUID;
  v_cust UUID;
  v_cart UUID;
  v_inv UUID;
  v_earn UUID;
  v_redeem UUID;
  v_expire UUID;
  v_rev UUID;
  v_bal NUMERIC;
  v_paid NUMERIC;
  v_je UUID;
  v_codes TEXT;
  v_dr NUMERIC;
  v_cr NUMERIC;
  v_money NUMERIC;
BEGIN
  PERFORM public._test_set_auth_uid(v_admin);

  SELECT id INTO v_main FROM public.warehouses WHERE code = 'MAIN';
  SELECT id INTO v_uom FROM public.uoms WHERE code = 'EA';
  SELECT id INTO v_list FROM public.price_lists WHERE code = 'RETAIL';

  IF v_main IS NULL OR v_uom IS NULL OR v_list IS NULL THEN
    RAISE EXCEPTION 'smoke fail: seed MAIN/EA/RETAIL missing';
  END IF;

  -- CoA liability + expense present
  IF NOT EXISTS (
    SELECT 1 FROM public.chart_of_accounts WHERE code = '2210' AND account_type = 'liability'
  ) THEN
    RAISE EXCEPTION 'smoke fail: CoA 2210 Loyalty Points Liability missing';
  END IF;
  IF NOT EXISTS (
    SELECT 1 FROM public.chart_of_accounts WHERE code = '5350' AND account_type = 'expense'
  ) THEN
    RAISE EXCEPTION 'smoke fail: CoA 5350 Loyalty Program Expense missing';
  END IF;

  PERFORM public.set_loyalty_program_settings(1, 0.01, 'USD'::public.currency_code, true);

  INSERT INTO public.stock_items (oem_part_number, description, base_uom_id, reorder_point, reorder_qty)
  VALUES ('P16-LOY-001', 'Phase16 loyalty part', v_uom, 50, 10)
  ON CONFLICT (oem_part_number) DO UPDATE
  SET description = EXCLUDED.description, base_uom_id = COALESCE(public.stock_items.base_uom_id, EXCLUDED.base_uom_id)
  RETURNING id INTO v_item;
  IF v_item IS NULL THEN
    SELECT id INTO v_item FROM public.stock_items WHERE oem_part_number = 'P16-LOY-001';
  END IF;

  INSERT INTO public.price_list_items (price_list_id, stock_item_id, unit_price, core_charge)
  VALUES (v_list, v_item, 50, 0)
  ON CONFLICT (price_list_id, stock_item_id) DO UPDATE
  SET unit_price = 50, core_charge = 0;

  PERFORM public.post_stock_receipt(
    v_main,
    'P16 loyalty seed',
    jsonb_build_array(
      jsonb_build_object(
        'stock_item_id', v_item, 'uom_id', v_uom, 'qty', 40,
        'unit_cost', 12, 'currency', 'USD', 'valuation_method', 'FIFO'
      )
    )
  );

  INSERT INTO public.customers (display_name, currency)
  VALUES ('Phase16 Loyalty Customer', 'USD')
  RETURNING id INTO v_cust;

  v_cart := public.create_pos_cart(v_main, v_cust, 'USD'::public.currency_code, 'immediate'::public.fulfillment_mode);
  PERFORM public.add_cart_line(v_cart, v_item, v_uom, 2);
  v_inv := public.checkout_pos_cart(v_cart);
  -- Invoice total 100; leave open for redeem

  -- -----------------------------------------------------------------------
  -- 1) Earn 1000 pts → liability money_value 10.00 on 2210
  -- -----------------------------------------------------------------------
  v_earn := public.earn_loyalty_points(v_cust, 1000, 'USD', 1, 'P16 earn');

  SELECT points_balance INTO v_bal FROM public.loyalty_accounts WHERE customer_id = v_cust;
  IF v_bal <> 1000 THEN
    RAISE EXCEPTION 'smoke fail: earn balance expected 1000 got %', v_bal;
  END IF;

  SELECT journal_entry_id, money_value INTO v_je, v_money
  FROM public.loyalty_ledger WHERE id = v_earn;
  IF v_je IS NULL OR v_money <> 10 THEN
    RAISE EXCEPTION 'smoke fail: earn JE/money_value expected 10 got %', v_money;
  END IF;

  SELECT string_agg(account_code, ',' ORDER BY account_code),
         COALESCE(SUM(debit), 0), COALESCE(SUM(credit), 0)
  INTO v_codes, v_dr, v_cr
  FROM public.journal_entry_lines WHERE journal_entry_id = v_je;

  IF v_codes IS DISTINCT FROM '2210,5350' OR v_dr <> v_cr OR v_dr <> 10 THEN
    RAISE EXCEPTION 'smoke fail: earn JE expected 5350/2210 balanced 10 got codes=% dr=% cr=%',
      v_codes, v_dr, v_cr;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM public.journal_entry_lines
    WHERE journal_entry_id = v_je AND account_code = '2210' AND credit = 10
  ) THEN
    RAISE EXCEPTION 'smoke fail: earn must credit liability 2210';
  END IF;

  -- -----------------------------------------------------------------------
  -- 2) Redeem 400 pts ($4) against invoice AR
  -- -----------------------------------------------------------------------
  v_redeem := public.redeem_loyalty_points(v_cust, 400, 'USD', 1, v_inv, 'P16 redeem');

  SELECT points_balance INTO v_bal FROM public.loyalty_accounts WHERE customer_id = v_cust;
  IF v_bal <> 600 THEN
    RAISE EXCEPTION 'smoke fail: after redeem balance expected 600 got %', v_bal;
  END IF;

  SELECT amount_paid INTO v_paid FROM public.sales_invoices WHERE id = v_inv;
  IF v_paid <> 4 THEN
    RAISE EXCEPTION 'smoke fail: invoice amount_paid expected 4 got %', v_paid;
  END IF;

  SELECT journal_entry_id INTO v_je FROM public.loyalty_ledger WHERE id = v_redeem;
  SELECT string_agg(account_code, ',' ORDER BY account_code)
  INTO v_codes
  FROM public.journal_entry_lines WHERE journal_entry_id = v_je;

  IF v_codes IS DISTINCT FROM '1200,2210' THEN
    RAISE EXCEPTION 'smoke fail: redeem JE expected 1200,2210 got %', v_codes;
  END IF;
  IF NOT EXISTS (
    SELECT 1 FROM public.journal_entry_lines
    WHERE journal_entry_id = v_je AND account_code = '2210' AND debit = 4
  ) THEN
    RAISE EXCEPTION 'smoke fail: redeem must debit liability 2210';
  END IF;

  -- -----------------------------------------------------------------------
  -- 3) Over-redeem denied
  -- -----------------------------------------------------------------------
  BEGIN
    PERFORM public.redeem_loyalty_points(v_cust, 99999, 'USD', 1, v_inv, 'over');
    RAISE EXCEPTION 'smoke fail: over-redeem allowed';
  EXCEPTION
    WHEN OTHERS THEN
      IF SQLERRM LIKE 'smoke fail:%' THEN
        RAISE;
      END IF;
  END;

  -- -----------------------------------------------------------------------
  -- 4) Expire 100 pts → release 2210 / credit 5350
  -- -----------------------------------------------------------------------
  v_expire := public.expire_loyalty_points(v_cust, 100, 'USD', 1, 'P16 expire');
  SELECT points_balance INTO v_bal FROM public.loyalty_accounts WHERE customer_id = v_cust;
  IF v_bal <> 500 THEN
    RAISE EXCEPTION 'smoke fail: after expire balance expected 500 got %', v_bal;
  END IF;

  SELECT journal_entry_id INTO v_je FROM public.loyalty_ledger WHERE id = v_expire;
  IF NOT EXISTS (
    SELECT 1 FROM public.journal_entry_lines
    WHERE journal_entry_id = v_je AND account_code = '2210' AND debit = 1
  ) THEN
    RAISE EXCEPTION 'smoke fail: expire must debit 2210';
  END IF;

  -- -----------------------------------------------------------------------
  -- 5) Reverse redeem → restore points + AR
  -- -----------------------------------------------------------------------
  v_rev := public.reverse_loyalty_movement(v_redeem);
  SELECT points_balance INTO v_bal FROM public.loyalty_accounts WHERE customer_id = v_cust;
  IF v_bal <> 900 THEN
    RAISE EXCEPTION 'smoke fail: after reverse-redeem balance expected 900 got %', v_bal;
  END IF;

  SELECT amount_paid INTO v_paid FROM public.sales_invoices WHERE id = v_inv;
  IF v_paid <> 0 THEN
    RAISE EXCEPTION 'smoke fail: after reverse-redeem amount_paid expected 0 got %', v_paid;
  END IF;

  -- -----------------------------------------------------------------------
  -- 6) earn_loyalty_from_spend (50 spend × 1 pt = 50 pts)
  -- -----------------------------------------------------------------------
  PERFORM public.earn_loyalty_from_spend(v_cust, 50, 'USD', 1, 'P16 spend earn');
  SELECT points_balance INTO v_bal FROM public.loyalty_accounts WHERE customer_id = v_cust;
  IF v_bal <> 950 THEN
    RAISE EXCEPTION 'smoke fail: after spend-earn balance expected 950 got %', v_bal;
  END IF;

  -- -----------------------------------------------------------------------
  -- 7) Ledger append-only
  -- -----------------------------------------------------------------------
  BEGIN
    UPDATE public.loyalty_ledger SET reason = 'hack' WHERE id = v_earn;
    RAISE EXCEPTION 'smoke fail: loyalty_ledger UPDATE allowed';
  EXCEPTION
    WHEN OTHERS THEN
      IF SQLERRM LIKE 'smoke fail:%' THEN
        RAISE;
      END IF;
  END;

  -- Exclusion: no tax / ZIMRA strings in loyalty objects
  IF EXISTS (
    SELECT 1 FROM pg_proc p
    JOIN pg_namespace n ON n.oid = p.pronamespace
    WHERE n.nspname = 'public'
      AND p.proname ILIKE '%loyalty%'
      AND (
        pg_get_functiondef(p.oid) ILIKE '%ZIMRA%'
        OR pg_get_functiondef(p.oid) ILIKE '%FDMS%'
        OR pg_get_functiondef(p.oid) ILIKE '%PAYE%'
      )
  ) THEN
    RAISE EXCEPTION 'smoke fail: loyalty functions contain tax/ZIMRA strings';
  END IF;

  RAISE NOTICE 'phase16_loyalty_smoke PASS';
END;
$$;
