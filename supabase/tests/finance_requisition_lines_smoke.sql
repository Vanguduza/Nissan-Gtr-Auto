-- Finance Phase D: multi-line requisition disburse smoke
-- Run as postgres after 20260725260000. Prefer seeded finance user.

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
  v_day DATE := DATE '2100-06-01' + ((random() * 900)::int);
  v_req UUID;
  v_je UUID;
  v_line_count INT;
  v_pe UUID;
BEGIN
  IF NOT EXISTS (SELECT 1 FROM public.profiles WHERE id = v_finance) THEN
    RAISE EXCEPTION 'smoke fail: seed finance user missing';
  END IF;

  PERFORM public._test_set_auth_uid(v_finance);

  v_req := public.create_finance_requisition(
    'petty_cash', 10, 'USD', 'Smoke', 'initial', '5300', '1110', 1
  );

  -- Replace with two expense lines (total 55)
  PERFORM public.set_finance_requisition_lines(
    v_req,
    '[
      {"expense_account_code":"5300","amount":30,"description":"Taxi"},
      {"expense_account_code":"5300","amount":25,"description":"Supplies"}
    ]'::jsonb
  );

  SELECT COUNT(*) INTO v_line_count
  FROM public.finance_requisition_lines
  WHERE requisition_id = v_req;

  IF v_line_count IS DISTINCT FROM 2 THEN
    RAISE EXCEPTION 'smoke fail: expected 2 lines got %', v_line_count;
  END IF;

  IF (
    SELECT amount FROM public.finance_requisitions WHERE id = v_req
  ) IS DISTINCT FROM 55 THEN
    RAISE EXCEPTION 'smoke fail: header amount not synced to 55';
  END IF;

  -- Direct line insert must fail (mutation guard)
  BEGIN
    INSERT INTO public.finance_requisition_lines (
      requisition_id, line_no, expense_account_code, amount
    ) VALUES (v_req, 99, '5300', 1);
    RAISE EXCEPTION 'smoke fail: direct line insert allowed';
  EXCEPTION
    WHEN OTHERS THEN
      IF SQLERRM LIKE 'smoke fail:%' THEN RAISE; END IF;
  END;

  BEGIN
    PERFORM public.disburse_finance_requisition(v_req, v_day);
    RAISE EXCEPTION 'smoke fail: disburse skipped approval';
  EXCEPTION
    WHEN OTHERS THEN
      IF SQLERRM LIKE 'smoke fail:%' THEN RAISE; END IF;
  END;

  PERFORM public.submit_finance_requisition(v_req);
  PERFORM public.approve_finance_requisition(v_req);
  v_je := public.disburse_finance_requisition(v_req, v_day);

  IF v_je IS NULL THEN
    RAISE EXCEPTION 'smoke fail: disburse returned null';
  END IF;

  IF NOT EXISTS (
    SELECT 1
    FROM public.journal_entry_lines
    WHERE journal_entry_id = v_je
      AND account_code = '5300'
      AND debit = 30
  ) OR NOT EXISTS (
    SELECT 1
    FROM public.journal_entry_lines
    WHERE journal_entry_id = v_je
      AND account_code = '5300'
      AND debit = 25
  ) OR NOT EXISTS (
    SELECT 1
    FROM public.journal_entry_lines
    WHERE journal_entry_id = v_je
      AND account_code = '1110'
      AND credit = 55
  ) THEN
    RAISE EXCEPTION 'smoke fail: multi-line JE shape wrong';
  END IF;

  SELECT payment_entry_id INTO v_pe
  FROM public.finance_requisitions
  WHERE id = v_req;

  IF v_pe IS NOT NULL THEN
    RAISE EXCEPTION 'smoke fail: payment_entry_id should stay null (reserved)';
  END IF;

  IF (
    SELECT journal_entry_id FROM public.finance_requisitions WHERE id = v_req
  ) IS DISTINCT FROM v_je THEN
    RAISE EXCEPTION 'smoke fail: journal_entry_id not linked';
  END IF;

  RAISE NOTICE 'finance_requisition_lines_smoke: PASS';
END;
$$;
