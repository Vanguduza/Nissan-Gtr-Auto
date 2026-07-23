-- Phase 3 finance smoke (run as postgres after CoA seed).
-- Does not require auth users; uses SECURITY DEFINER paths as table owner where needed.

DO $$
DECLARE
  v_id UUID;
  v_rev UUID;
  v_period UUID;
  v_n1 TEXT;
  v_n2 TEXT;
  v_tb_debit NUMERIC;
BEGIN
  -- Naming concurrency uniqueness (sequential in one session still proves increment)
  v_n1 := public.next_series_value('JV-TEST-');
  v_n2 := public.next_series_value('JV-TEST-');
  IF v_n1 = v_n2 THEN
    RAISE EXCEPTION 'smoke fail: naming series not unique';
  END IF;

  -- Balanced post
  v_id := public.post_journal_entry(
    CURRENT_DATE,
    'Smoke sale',
    'USD',
    1,
    '[
      {"account_code":"1200","debit":100,"credit":0,"currency":"USD"},
      {"account_code":"4100","debit":0,"credit":100,"currency":"USD"}
    ]'::jsonb
  );

  -- Unbalanced rejected
  BEGIN
    PERFORM public.post_journal_entry(
      CURRENT_DATE,
      'Bad',
      'USD',
      1,
      '[{"account_code":"1200","debit":50,"credit":0,"currency":"USD"}]'::jsonb
    );
    RAISE EXCEPTION 'smoke fail: unbalanced post allowed';
  EXCEPTION
    WHEN OTHERS THEN
      IF SQLERRM LIKE 'smoke fail:%' THEN RAISE; END IF;
  END;

  -- Reverse
  v_rev := public.reverse_journal(v_id);
  IF v_rev IS NULL THEN
    RAISE EXCEPTION 'smoke fail: reverse returned null';
  END IF;

  -- Opening balances → TB
  PERFORM public.post_opening_balances(
    CURRENT_DATE,
    'USD',
    1,
    '[
      {"account_code":"1100","debit":1000,"credit":0,"currency":"USD"},
      {"account_code":"3100","debit":0,"credit":1000,"currency":"USD"}
    ]'::jsonb
  );

  SELECT debit INTO v_tb_debit
  FROM public.report_trial_balance(CURRENT_DATE, 'USD')
  WHERE account_code = '1100';

  IF v_tb_debit IS NULL OR v_tb_debit < 1000 THEN
    RAISE EXCEPTION 'smoke fail: opening cash not on TB (% )', v_tb_debit;
  END IF;

  -- Period lock
  INSERT INTO public.accounting_periods (period_start, period_end, label)
  VALUES (DATE '2000-01-01', DATE '2000-01-31', 'Smoke lock')
  RETURNING id INTO v_period;

  PERFORM public.lock_accounting_period(v_period);

  BEGIN
    PERFORM public.post_journal_entry(
      DATE '2000-01-15',
      'Should fail',
      'USD',
      1,
      '[
        {"account_code":"1100","debit":1,"credit":0,"currency":"USD"},
        {"account_code":"3100","debit":0,"credit":1,"currency":"USD"}
      ]'::jsonb
    );
    RAISE EXCEPTION 'smoke fail: post into locked period allowed';
  EXCEPTION
    WHEN OTHERS THEN
      IF SQLERRM LIKE 'smoke fail:%' THEN RAISE; END IF;
  END;

  RAISE NOTICE 'phase3_finance_smoke: PASS';
END;
$$;
