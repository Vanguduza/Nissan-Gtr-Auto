-- Finance period balances + register + requisitions smoke (Phases A+B+C).
-- Run as postgres after migrations (incl. 20260725250000). Prefer seeded finance user.

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
  v_finance UUID := 'a0000000-0000-4000-8000-000000000002';
  v_period UUID;
  v_je UUID;
  v_closing NUMERIC;
  v_opening NUMERIC := 500;
  v_reg_count INT;
  v_prev_bal NUMERIC;
  v_bal NUMERIC;
  v_prev_date DATE;
  v_date DATE;
  v_fund TEXT;
  v_replenish NUMERIC;
  v_req UUID;
  v_disburse_je UUID;
  v_req_status public.finance_requisition_status;
BEGIN
  IF NOT EXISTS (SELECT 1 FROM public.profiles WHERE id = v_finance) THEN
    RAISE EXCEPTION 'smoke fail: seed finance user missing';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM public.chart_of_accounts WHERE code = '1110') THEN
    RAISE EXCEPTION 'smoke fail: CoA 1110 Petty Cash required';
  END IF;

  PERFORM public._test_set_auth_uid(v_finance);

  -- Phase B: funding config
  v_fund := public.petty_cash_funding_account_code();
  IF v_fund IS DISTINCT FROM '1100' THEN
    RAISE EXCEPTION 'smoke fail: funding account expected 1100 got %', v_fund;
  END IF;

  -- Phase A: open period
  v_period := public.open_account_period(
    '1110', 'USD', CURRENT_DATE, CURRENT_DATE, v_opening, 'Smoke open'
  );

  -- Refuse second open
  BEGIN
    PERFORM public.open_account_period(
      '1110', 'USD', CURRENT_DATE, CURRENT_DATE, 0, 'dup'
    );
    RAISE EXCEPTION 'smoke fail: second open allowed';
  EXCEPTION
    WHEN OTHERS THEN
      IF SQLERRM LIKE 'smoke fail:%' THEN RAISE; END IF;
  END;

  -- Post activity: spend 75 from petty (Dr 5300 / Cr 1110)
  v_je := public.post_journal_entry(
    CURRENT_DATE,
    'Smoke petty spend',
    'USD',
    1,
    '[
      {"account_code":"5300","debit":75,"credit":0,"currency":"USD"},
      {"account_code":"1110","debit":0,"credit":75,"currency":"USD"}
    ]'::jsonb
  );

  -- Register: ordered, running balance = opening_activity_before + nets
  SELECT COUNT(*) INTO v_reg_count
  FROM public.report_account_register('1110', CURRENT_DATE, CURRENT_DATE, 'USD');

  IF v_reg_count < 1 THEN
    RAISE EXCEPTION 'smoke fail: register empty';
  END IF;

  v_prev_bal := NULL;
  v_prev_date := NULL;
  FOR v_date, v_bal IN
    SELECT r.entry_date, r.running_balance
    FROM public.report_account_register('1110', CURRENT_DATE, CURRENT_DATE, 'USD') r
    ORDER BY r.entry_date, r.document_number NULLS LAST, r.journal_entry_id
  LOOP
    IF v_prev_date IS NOT NULL AND v_date < v_prev_date THEN
      RAISE EXCEPTION 'smoke fail: register not date-ordered';
    END IF;
    v_prev_date := v_date;
    v_prev_bal := v_bal;
  END LOOP;

  -- Close math: opening 500 + (0 - 75) = 425
  PERFORM public.close_account_period(v_period, 420, 'physical short');

  SELECT closing_balance INTO v_closing
  FROM public.account_period_balances
  WHERE id = v_period;

  IF v_closing IS DISTINCT FROM 425 THEN
    RAISE EXCEPTION 'smoke fail: closing expected 425 got %', v_closing;
  END IF;

  IF (
    SELECT variance FROM public.account_period_balances WHERE id = v_period
  ) IS DISTINCT FROM -5 THEN
    RAISE EXCEPTION 'smoke fail: variance expected -5';
  END IF;

  -- Refuse re-close
  BEGIN
    PERFORM public.close_account_period(v_period);
    RAISE EXCEPTION 'smoke fail: re-close allowed';
  EXCEPTION
    WHEN OTHERS THEN
      IF SQLERRM LIKE 'smoke fail:%' THEN RAISE; END IF;
  END;

  -- Phase B: float then spend → replenish amount
  PERFORM public.post_journal_entry(
    CURRENT_DATE,
    'Smoke float 1110 from 1100',
    'USD',
    1,
    format(
      '[
        {"account_code":"1110","debit":200,"credit":0,"currency":"USD"},
        {"account_code":"%s","debit":0,"credit":200,"currency":"USD"}
      ]',
      v_fund
    )::jsonb
  );

  PERFORM public.post_journal_entry(
    CURRENT_DATE,
    'Smoke spend after float',
    'USD',
    1,
    '[
      {"account_code":"5300","debit":40,"credit":0,"currency":"USD"},
      {"account_code":"1110","debit":0,"credit":40,"currency":"USD"}
    ]'::jsonb
  );

  v_replenish := public.compute_petty_cash_replenish_amount('USD', CURRENT_DATE);
  IF v_replenish IS DISTINCT FROM 40 THEN
    RAISE EXCEPTION 'smoke fail: replenish expected 40 got %', v_replenish;
  END IF;

  -- Phase C: requisition flow; cannot skip approval
  v_req := public.create_finance_requisition(
    'petty_cash',
    25,
    'USD',
    'Courier',
    'Smoke taxi',
    '5300',
    '1110',
    1
  );

  BEGIN
    PERFORM public.disburse_finance_requisition(v_req);
    RAISE EXCEPTION 'smoke fail: disburse skipped approval';
  EXCEPTION
    WHEN OTHERS THEN
      IF SQLERRM LIKE 'smoke fail:%' THEN RAISE; END IF;
  END;

  PERFORM public.submit_finance_requisition(v_req);
  PERFORM public.approve_finance_requisition(v_req);
  v_disburse_je := public.disburse_finance_requisition(v_req, CURRENT_DATE);

  IF v_disburse_je IS NULL THEN
    RAISE EXCEPTION 'smoke fail: disburse returned null JE';
  END IF;

  SELECT status INTO v_req_status
  FROM public.finance_requisitions
  WHERE id = v_req;

  IF v_req_status IS DISTINCT FROM 'disbursed' THEN
    RAISE EXCEPTION 'smoke fail: expected disbursed got %', v_req_status;
  END IF;

  IF NOT EXISTS (
    SELECT 1
    FROM public.journal_entries e
    JOIN public.journal_entry_lines l ON l.journal_entry_id = e.id
    WHERE e.id = v_disburse_je
      AND e.status = 'posted'
      AND l.account_code = '1110'
      AND l.credit = 25
  ) THEN
    RAISE EXCEPTION 'smoke fail: disburse JE missing Cr 1110 25';
  END IF;

  -- Reject path leaves no JE
  v_req := public.create_finance_requisition(
    'payment', 10, 'USD', 'Vendor', 'Reject me', '5300', NULL, 1
  );
  PERFORM public.submit_finance_requisition(v_req);
  PERFORM public.reject_finance_requisition(v_req, 'nope');

  IF (
    SELECT journal_entry_id FROM public.finance_requisitions WHERE id = v_req
  ) IS NOT NULL THEN
    RAISE EXCEPTION 'smoke fail: reject created journal';
  END IF;

  RAISE NOTICE 'finance_period_balances_requisitions_smoke: PASS';
END;
$$;
