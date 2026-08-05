-- Multi-approver requisitions: threshold required_approvals + approval rows.
-- Prefer seeded finance user a000…0002.

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
  v_admin UUID := 'a0000000-0000-4000-8000-000000000001';
  v_req UUID;
  v_needed INT;
  v_count INT;
  v_status public.finance_requisition_status;
BEGIN
  IF NOT EXISTS (SELECT 1 FROM public.profiles WHERE id = v_finance) THEN
    RAISE EXCEPTION 'smoke fail: seed finance user missing';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM public.profiles WHERE id = v_admin) THEN
    RAISE EXCEPTION 'smoke fail: seed admin user missing';
  END IF;

  PERFORM public._test_set_auth_uid(v_finance);

  -- Amount 600 USD petty_cash → threshold requires 2 approvals
  v_req := public.create_finance_requisition(
    'petty_cash', 600, 'USD', 'Multi-approve smoke', 'dual', '5300', '1110', 1
  );
  PERFORM public.set_finance_requisition_lines(
    v_req,
    '[{"expense_account_code":"5300","amount":600,"description":"Dual"}]'::jsonb
  );
  PERFORM public.submit_finance_requisition(v_req);

  SELECT required_approvals, approval_count, status
  INTO v_needed, v_count, v_status
  FROM public.finance_requisitions WHERE id = v_req;

  IF v_needed IS DISTINCT FROM 2 THEN
    RAISE EXCEPTION 'smoke fail: expected required_approvals=2 got %', v_needed;
  END IF;
  IF v_status IS DISTINCT FROM 'submitted' THEN
    RAISE EXCEPTION 'smoke fail: expected submitted after submit got %', v_status;
  END IF;

  PERFORM public.approve_finance_requisition(v_req, 'first');

  SELECT approval_count, status INTO v_count, v_status
  FROM public.finance_requisitions WHERE id = v_req;

  IF v_count IS DISTINCT FROM 1 OR v_status IS DISTINCT FROM 'submitted' THEN
    RAISE EXCEPTION 'smoke fail: after 1/2 expected submitted count=1 got % %',
      v_count, v_status;
  END IF;

  -- Second distinct approver (admin)
  PERFORM public._test_set_auth_uid(v_admin);
  PERFORM public.approve_finance_requisition(v_req, 'second');

  SELECT approval_count, status INTO v_count, v_status
  FROM public.finance_requisitions WHERE id = v_req;

  IF v_count IS DISTINCT FROM 2 OR v_status IS DISTINCT FROM 'approved' THEN
    RAISE EXCEPTION 'smoke fail: after 2/2 expected approved count=2 got % %',
      v_count, v_status;
  END IF;

  IF (
    SELECT COUNT(*) FROM public.finance_requisition_approvals WHERE requisition_id = v_req
  ) IS DISTINCT FROM 2 THEN
    RAISE EXCEPTION 'smoke fail: expected 2 approval rows';
  END IF;

  RAISE NOTICE 'finance_multi_approver_smoke ok';
END;
$$;
