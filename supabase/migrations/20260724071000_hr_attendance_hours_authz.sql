-- Phase 9 follow-up: AuthZ on attendance_hours_in_period + deduction label tighten.
-- HARD exclusions unchanged: NO PAYE/NSSA/statutory engines, NO ZIMRA.

-- ---------------------------------------------------------------------------
-- attendance_hours_in_period: HR/admin OR self only (clock/payslip pattern)
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.attendance_hours_in_period(
  p_employee_id UUID,
  p_period_start TIMESTAMPTZ,
  p_period_end TIMESTAMPTZ
)
RETURNS NUMERIC
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_hours NUMERIC := 0;
  v_in TIMESTAMPTZ;
  v_is_hr BOOLEAN;
  v_self UUID;
  r RECORD;
BEGIN
  v_is_hr := (
    auth.role() = 'service_role'
    OR public.has_staff_role(ARRAY['admin', 'hr']::public.staff_role[])
  );
  v_self := public.current_employee_id();

  IF NOT v_is_hr AND (v_self IS NULL OR v_self IS DISTINCT FROM p_employee_id) THEN
    RAISE EXCEPTION 'admin/hr role or own employee record required to read attendance hours';
  END IF;

  IF p_period_end < p_period_start THEN
    RAISE EXCEPTION 'period_end must be >= period_start';
  END IF;

  FOR r IN
    SELECT event_type, occurred_at
    FROM public.attendance_events
    WHERE employee_id = p_employee_id
      AND occurred_at >= p_period_start
      AND occurred_at < p_period_end
    ORDER BY occurred_at ASC, created_at ASC
  LOOP
    IF r.event_type = 'clock_in' THEN
      v_in := r.occurred_at;
    ELSIF r.event_type = 'clock_out' AND v_in IS NOT NULL THEN
      v_hours := v_hours + EXTRACT(EPOCH FROM (r.occurred_at - v_in)) / 3600.0;
      v_in := NULL;
    END IF;
  END LOOP;

  RETURN ROUND(v_hours, 4);
END;
$$;

REVOKE ALL ON FUNCTION public.attendance_hours_in_period(UUID, TIMESTAMPTZ, TIMESTAMPTZ) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.attendance_hours_in_period(UUID, TIMESTAMPTZ, TIMESTAMPTZ) FROM anon;
GRANT EXECUTE ON FUNCTION public.attendance_hours_in_period(UUID, TIMESTAMPTZ, TIMESTAMPTZ)
  TO authenticated, service_role;

-- ---------------------------------------------------------------------------
-- Prefer clock_attendance RPC: no direct INSERT for authenticated
-- (SECURITY DEFINER RPC still inserts as owner)
-- ---------------------------------------------------------------------------
REVOKE INSERT ON public.attendance_events FROM authenticated;
GRANT SELECT, UPDATE, DELETE ON public.attendance_events TO authenticated;
GRANT SELECT, INSERT, UPDATE, DELETE ON public.attendance_events TO service_role;

-- ---------------------------------------------------------------------------
-- Tighten add_payroll_deduction deny-regex: bare tax / zimra / fiscal
-- (body otherwise identical to 20260724070000)
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.add_payroll_deduction(
  p_payroll_line_id UUID,
  p_label TEXT,
  p_amount NUMERIC
)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_line public.payroll_lines%ROWTYPE;
  v_run public.payroll_runs%ROWTYPE;
  v_id UUID;
  v_ded NUMERIC;
BEGIN
  PERFORM public._payroll_begin_rpc();
  PERFORM public._require_hr_staff();

  IF p_label IS NULL OR length(trim(p_label)) = 0 THEN
    RAISE EXCEPTION 'deduction label required';
  END IF;
  -- Reject labels that imply statutory tax calc / authority remittance
  IF lower(p_label) ~ '(paye|nssa|pobs|apwcs|zimdef|statutory|tax.?bracket|p4a?|\ytax\y|zimra|fiscal)' THEN
    RAISE EXCEPTION 'statutory/tax deduction labels are not allowed; use manual labels only';
  END IF;
  IF p_amount IS NULL OR p_amount <= 0 THEN
    RAISE EXCEPTION 'deduction amount must be > 0';
  END IF;

  SELECT * INTO v_line FROM public.payroll_lines WHERE id = p_payroll_line_id FOR UPDATE;
  IF NOT FOUND THEN
    RAISE EXCEPTION 'payroll line not found: %', p_payroll_line_id;
  END IF;

  SELECT * INTO v_run FROM public.payroll_runs WHERE id = v_line.payroll_run_id FOR UPDATE;
  IF v_run.status <> 'draft' THEN
    RAISE EXCEPTION 'deductions only allowed on draft payroll runs';
  END IF;

  INSERT INTO public.payroll_deduction_lines (
    payroll_line_id, label, amount, currency
  )
  VALUES (
    p_payroll_line_id, trim(p_label), p_amount, v_line.currency
  )
  RETURNING id INTO v_id;

  SELECT COALESCE(SUM(amount), 0) INTO v_ded
  FROM public.payroll_deduction_lines
  WHERE payroll_line_id = p_payroll_line_id;

  IF v_ded > v_line.gross_amount THEN
    RAISE EXCEPTION 'deductions (%) exceed gross (%)', v_ded, v_line.gross_amount;
  END IF;

  UPDATE public.payroll_lines
  SET deductions_amount = v_ded,
      net_amount = gross_amount - v_ded
  WHERE id = p_payroll_line_id;

  PERFORM public._refresh_payroll_run_totals(v_line.payroll_run_id);
  RETURN v_id;
END;
$$;
