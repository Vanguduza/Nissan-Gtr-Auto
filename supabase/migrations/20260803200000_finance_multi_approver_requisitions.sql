-- Batch 1 follow-up: wire submit/approve to threshold required_approvals +
-- finance_requisition_approvals rows. Organogram reporting line may approve.
-- Append-only approvals; ledger unchanged. No ZIMRA.

-- ---------------------------------------------------------------------------
-- Approvals append-only
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.guard_finance_req_approvals_immutable()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
  RAISE EXCEPTION 'finance_requisition_approvals is append-only';
END;
$$;

DROP TRIGGER IF EXISTS finance_req_approvals_immutable ON public.finance_requisition_approvals;
CREATE TRIGGER finance_req_approvals_immutable
  BEFORE UPDATE OR DELETE ON public.finance_requisition_approvals
  FOR EACH ROW EXECUTE FUNCTION public.guard_finance_req_approvals_immutable();

-- ---------------------------------------------------------------------------
-- Organogram: user holds an ancestor role of the requester's hr_role
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public._finance_req_approver_on_reporting_line(
  p_requester_user_id UUID,
  p_approver_user_id UUID
)
RETURNS BOOLEAN
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
  WITH RECURSIVE requester_role AS (
    SELECT e.hr_role_id AS role_id
    FROM public.employees e
    WHERE e.user_id = p_requester_user_id
      AND e.hr_role_id IS NOT NULL
    LIMIT 1
  ),
  ancestors AS (
    SELECT r.parent_role_id AS role_id
    FROM public.hr_roles r
    JOIN requester_role rr ON rr.role_id = r.id
    WHERE r.parent_role_id IS NOT NULL
    UNION ALL
    SELECT r.parent_role_id
    FROM public.hr_roles r
    JOIN ancestors a ON a.role_id = r.id
    WHERE r.parent_role_id IS NOT NULL
  )
  SELECT EXISTS (
    SELECT 1
    FROM public.employees e
    WHERE e.user_id = p_approver_user_id
      AND e.hr_role_id IS NOT NULL
      AND e.hr_role_id IN (SELECT role_id FROM ancestors WHERE role_id IS NOT NULL)
  );
$$;

CREATE OR REPLACE FUNCTION public._finance_req_can_approve(
  p_requisition public.finance_requisitions
)
RETURNS BOOLEAN
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
  IF auth.role() = 'service_role' THEN
    RETURN true;
  END IF;
  IF auth.uid() IS NULL THEN
    RETURN false;
  END IF;
  IF public.has_staff_role(ARRAY['admin', 'finance']::public.staff_role[]) THEN
    RETURN true;
  END IF;
  -- Reporting-line managers: never self-approve
  IF p_requisition.requested_by = auth.uid() THEN
    RETURN false;
  END IF;
  RETURN public._finance_req_approver_on_reporting_line(
    p_requisition.requested_by,
    auth.uid()
  );
END;
$$;

-- ---------------------------------------------------------------------------
-- submit — set required_approvals from thresholds; reset approval_count
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.submit_finance_requisition(p_requisition_id UUID)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_row public.finance_requisitions%ROWTYPE;
  v_line_sum NUMERIC(18, 2);
  v_line_count INT;
  v_needed INT;
BEGIN
  SELECT * INTO v_row
  FROM public.finance_requisitions
  WHERE id = p_requisition_id
  FOR UPDATE;

  IF NOT FOUND THEN
    RAISE EXCEPTION 'requisition not found: %', p_requisition_id;
  END IF;

  IF NOT (
    auth.role() = 'service_role'
    OR public.has_staff_role(ARRAY['admin', 'finance']::public.staff_role[])
    OR v_row.requested_by = auth.uid()
  ) THEN
    RAISE EXCEPTION 'not allowed to submit this requisition';
  END IF;

  IF v_row.status <> 'draft' THEN
    RAISE EXCEPTION 'only draft requisitions can be submitted (status=%)', v_row.status;
  END IF;

  SELECT COUNT(*), COALESCE(SUM(amount), 0)
  INTO v_line_count, v_line_sum
  FROM public.finance_requisition_lines
  WHERE requisition_id = p_requisition_id;

  IF v_line_count < 1 THEN
    RAISE EXCEPTION 'cannot submit requisition without lines';
  END IF;
  IF v_line_sum IS DISTINCT FROM v_row.amount THEN
    RAISE EXCEPTION 'header amount % does not match line total %', v_row.amount, v_line_sum;
  END IF;

  v_needed := public._finance_req_required_approvals(
    v_row.req_type,
    v_row.currency,
    v_row.amount
  );

  PERFORM public._finance_req_rpc_enter();

  UPDATE public.finance_requisitions
  SET
    status = 'submitted',
    submitted_at = now(),
    document_number = COALESCE(document_number, public.next_series_value('FREQ-')),
    required_approvals = v_needed,
    approval_count = 0,
    approved_by = NULL,
    approved_at = NULL,
    updated_at = now()
  WHERE id = p_requisition_id;

  INSERT INTO public.finance_audit_log (
    actor_user_id, action, entity_type, entity_id, before_state, after_state
  ) VALUES (
    auth.uid(),
    'requisition_submitted',
    'finance_requisitions',
    p_requisition_id,
    jsonb_build_object('status', 'draft', 'amount', v_row.amount),
    jsonb_build_object(
      'status', 'submitted',
      'required_approvals', v_needed,
      'document_number', COALESCE(v_row.document_number, 'pending')
    )
  );

  RETURN p_requisition_id;
END;
$$;

-- ---------------------------------------------------------------------------
-- approve — append approval row; only flip to approved when count met
-- Drop 1-arg overload so DEFAULT covers approve_finance_requisition(uuid).
-- ---------------------------------------------------------------------------
DROP FUNCTION IF EXISTS public.approve_finance_requisition(UUID);

CREATE OR REPLACE FUNCTION public.approve_finance_requisition(
  p_requisition_id UUID,
  p_note TEXT DEFAULT NULL
)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_row public.finance_requisitions%ROWTYPE;
  v_uid UUID := auth.uid();
  v_new_count INT;
  v_final BOOLEAN := false;
BEGIN
  IF v_uid IS NULL AND auth.role() <> 'service_role' THEN
    RAISE EXCEPTION 'not authenticated';
  END IF;
  IF auth.role() = 'service_role' AND v_uid IS NULL THEN
    -- Smoke / worker path: use a stable sentinel only when JWT sub absent
    v_uid := 'a0000000-0000-4000-8000-000000000002'::uuid;
  END IF;

  SELECT * INTO v_row
  FROM public.finance_requisitions
  WHERE id = p_requisition_id
  FOR UPDATE;

  IF NOT FOUND THEN
    RAISE EXCEPTION 'requisition not found: %', p_requisition_id;
  END IF;
  IF v_row.status <> 'submitted' THEN
    RAISE EXCEPTION 'only submitted requisitions can be approved (status=%)', v_row.status;
  END IF;

  IF NOT public._finance_req_can_approve(v_row) THEN
    RAISE EXCEPTION
      'not allowed to approve (need finance/admin or organogram reporting line)';
  END IF;

  IF EXISTS (
    SELECT 1
    FROM public.finance_requisition_approvals
    WHERE requisition_id = p_requisition_id
      AND approver_user_id = v_uid
  ) THEN
    RAISE EXCEPTION 'already approved by this user';
  END IF;

  PERFORM public._finance_req_rpc_enter();

  INSERT INTO public.finance_requisition_approvals (
    requisition_id, approver_user_id, note
  ) VALUES (
    p_requisition_id, v_uid, nullif(trim(p_note), '')
  );

  v_new_count := COALESCE(v_row.approval_count, 0) + 1;
  v_final := v_new_count >= COALESCE(v_row.required_approvals, 1);

  UPDATE public.finance_requisitions
  SET
    approval_count = v_new_count,
    status = CASE WHEN v_final THEN 'approved'::public.finance_requisition_status ELSE status END,
    approved_by = CASE WHEN v_final THEN v_uid ELSE approved_by END,
    approved_at = CASE WHEN v_final THEN now() ELSE approved_at END,
    updated_at = now()
  WHERE id = p_requisition_id;

  INSERT INTO public.finance_audit_log (
    actor_user_id, action, entity_type, entity_id, before_state, after_state
  ) VALUES (
    v_uid,
    CASE WHEN v_final THEN 'requisition_approved' ELSE 'requisition_approval_partial' END,
    'finance_requisitions',
    p_requisition_id,
    jsonb_build_object(
      'status', v_row.status,
      'approval_count', v_row.approval_count,
      'required_approvals', v_row.required_approvals
    ),
    jsonb_build_object(
      'status', CASE WHEN v_final THEN 'approved' ELSE 'submitted' END,
      'approval_count', v_new_count,
      'required_approvals', v_row.required_approvals,
      'final', v_final
    )
  );

  RETURN p_requisition_id;
END;
$$;

REVOKE ALL ON FUNCTION public._finance_req_approver_on_reporting_line(UUID, UUID) FROM PUBLIC;
REVOKE ALL ON FUNCTION public._finance_req_can_approve(public.finance_requisitions) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.approve_finance_requisition(UUID, TEXT) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.submit_finance_requisition(UUID) FROM PUBLIC;

GRANT EXECUTE ON FUNCTION public.approve_finance_requisition(UUID, TEXT)
  TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.submit_finance_requisition(UUID)
  TO authenticated, service_role;
