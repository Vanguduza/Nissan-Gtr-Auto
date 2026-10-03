-- Exported from the hosted project's supabase_migrations.schema_migrations (20260907122925 payroll_worker_reconstruction_runtime).
-- Source of record for what production ran; see supabase/live-history/README.md.

CREATE OR REPLACE FUNCTION public.payslip_render_payload(p_payroll_line_id UUID)
RETURNS JSONB
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_line public.payroll_lines%ROWTYPE;
  v_run public.payroll_runs%ROWTYPE;
  v_emp public.employees%ROWTYPE;
  v_ps public.payslips%ROWTYPE;
  v_deductions JSONB;
  v_self UUID;
BEGIN
  SELECT * INTO v_line FROM public.payroll_lines WHERE id=p_payroll_line_id;
  IF NOT FOUND THEN RAISE EXCEPTION 'payroll line not found: %',p_payroll_line_id; END IF;
  SELECT * INTO v_run FROM public.payroll_runs WHERE id=v_line.payroll_run_id;
  SELECT * INTO v_emp FROM public.employees WHERE id=v_line.employee_id;
  SELECT * INTO v_ps FROM public.payslips WHERE payroll_line_id=p_payroll_line_id;
  IF NOT FOUND THEN RAISE EXCEPTION 'payslip metadata not found for payroll line %',p_payroll_line_id; END IF;

  v_self := public.current_employee_id();
  IF NOT (
    auth.role()='service_role'
    OR public.has_staff_role(ARRAY['admin','hr']::public.staff_role[])
    OR v_self IS NOT DISTINCT FROM v_line.employee_id
  ) THEN RAISE EXCEPTION 'admin/hr or own payslip only'; END IF;

  SELECT COALESCE(jsonb_agg(jsonb_build_object('label',d.label,'amount',d.amount) ORDER BY d.created_at,d.id),'[]'::jsonb)
  INTO v_deductions FROM public.payroll_deduction_lines d WHERE d.payroll_line_id=p_payroll_line_id;

  RETURN jsonb_build_object(
    'storeName','Nissan GTR Auto',
    'employeeName',v_emp.full_name,
    'employeeCode',v_emp.employee_code,
    'periodStart',v_run.period_start,
    'periodEnd',v_run.period_end,
    'currency',v_run.currency,
    'grossPay',v_line.gross_amount,
    'manualDeductions',v_deductions,
    'netPay',v_line.net_amount,
    'storageBucket',v_ps.storage_bucket,
    'storagePath',v_ps.storage_path
  );
END;
$$;

CREATE OR REPLACE FUNCTION public.run_due_payroll_schedules(p_as_of TIMESTAMPTZ DEFAULT now())
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_sched public.hr_payslip_schedules%ROWTYPE;
  v_due_at TIMESTAMPTZ;
  v_end DATE;
  v_start DATE;
  v_run UUID;
  v_fund JSONB;
  v_results JSONB := '[]'::jsonb;
BEGIN
  IF NOT (auth.role()='service_role' OR public.has_staff_role(ARRAY['admin']::public.staff_role[])) THEN
    RAISE EXCEPTION 'service role or admin required';
  END IF;

  FOR v_sched IN
    SELECT * FROM public.hr_payslip_schedules
    WHERE is_active AND next_run_at IS NOT NULL AND next_run_at <= COALESCE(p_as_of,now())
    ORDER BY next_run_at, pay_frequency
    FOR UPDATE
  LOOP
    v_due_at := v_sched.next_run_at;
    v_end := v_due_at::date - 1;
    IF v_sched.pay_frequency='weekly' THEN v_start := v_end - 6;
    ELSIF v_sched.pay_frequency='fortnightly' THEN v_start := v_end - 13;
    ELSE v_start := (v_due_at::date - interval '1 month')::date; END IF;

    BEGIN
      v_run := public.run_scheduled_payroll_for_frequency(v_sched.pay_frequency,v_start,v_end,'USD',1);
      PERFORM public.submit_payroll_run(v_run);
      v_fund := public.fund_payroll_run(v_run);
      UPDATE public.hr_payslip_schedules
      SET last_run_at=v_due_at, next_run_at=public._next_payroll_schedule_at(pay_frequency,v_due_at + interval '1 second')
      WHERE id=v_sched.id;
      v_results := v_results || jsonb_build_array(jsonb_build_object('pay_frequency',v_sched.pay_frequency,'payroll_run_id',v_run,'ok',true,'fund',v_fund));
    EXCEPTION WHEN OTHERS THEN
      v_results := v_results || jsonb_build_array(jsonb_build_object('pay_frequency',v_sched.pay_frequency,'ok',false,'error',SQLERRM));
    END;
  END LOOP;

  RETURN jsonb_build_object('as_of',COALESCE(p_as_of,now()),'results',v_results);
END;
$$;

REVOKE ALL ON FUNCTION public.payslip_render_payload(UUID) FROM PUBLIC,anon;
GRANT EXECUTE ON FUNCTION public.payslip_render_payload(UUID) TO authenticated,service_role;
REVOKE ALL ON FUNCTION public.run_due_payroll_schedules(TIMESTAMPTZ) FROM PUBLIC,anon,authenticated;
GRANT EXECUTE ON FUNCTION public.run_due_payroll_schedules(TIMESTAMPTZ) TO service_role;
