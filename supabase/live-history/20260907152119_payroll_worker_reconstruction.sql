-- Exported from the hosted project's supabase_migrations.schema_migrations (20260907152119 payroll_worker_reconstruction).
-- Source of record for what production ran; see supabase/live-history/README.md.

-- Reconstruct deployed payroll worker support from repo-authoritative payroll/finance contracts.
-- Gross payroll only: no PAYE/NSSA/statutory remittance. Idempotent worker funding.

INSERT INTO public.chart_of_accounts (code, name, account_type, display_name)
VALUES ('2150', 'Payroll Deductions Clearing', 'liability', 'Payroll Deductions Clearing')
ON CONFLICT (code) DO UPDATE SET name = EXCLUDED.name, display_name = EXCLUDED.display_name, account_type = EXCLUDED.account_type, is_active = true;

CREATE TABLE IF NOT EXISTS public.payroll_run_funding (
  payroll_run_id UUID PRIMARY KEY REFERENCES public.payroll_runs(id) ON DELETE RESTRICT,
  journal_entry_id UUID NOT NULL UNIQUE REFERENCES public.journal_entries(id) ON DELETE RESTRICT,
  funded_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  funded_by UUID REFERENCES auth.users(id),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

ALTER TABLE public.payroll_run_funding ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS payroll_run_funding_select_staff ON public.payroll_run_funding;
CREATE POLICY payroll_run_funding_select_staff ON public.payroll_run_funding
FOR SELECT TO authenticated USING (
  public.has_staff_role(ARRAY['admin','finance','hr']::public.staff_role[])
);
REVOKE ALL ON TABLE public.payroll_run_funding FROM PUBLIC, anon, authenticated;
GRANT SELECT ON TABLE public.payroll_run_funding TO authenticated;
GRANT SELECT, INSERT ON TABLE public.payroll_run_funding TO service_role;

CREATE OR REPLACE FUNCTION public._next_payroll_schedule_at(
  p_frequency public.hr_pay_frequency,
  p_after TIMESTAMPTZ
)
RETURNS TIMESTAMPTZ
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_base TIMESTAMPTZ := COALESCE(p_after, now());
  v_day DATE := v_base::date;
  v_candidate TIMESTAMPTZ;
  v_dow INT;
BEGIN
  IF p_frequency = 'weekly' THEN
    v_dow := EXTRACT(ISODOW FROM v_day)::int;
    v_candidate := (v_day + ((8 - v_dow) % 7))::timestamptz + interval '6 hours';
    IF v_candidate <= v_base THEN v_candidate := v_candidate + interval '7 days'; END IF;
    RETURN v_candidate;
  ELSIF p_frequency = 'fortnightly' THEN
    v_candidate := date_trunc('month', v_base)::timestamptz + interval '6 hours';
    IF v_candidate > v_base THEN RETURN v_candidate; END IF;
    v_candidate := date_trunc('month', v_base)::timestamptz + interval '14 days 6 hours';
    IF v_candidate > v_base THEN RETURN v_candidate; END IF;
    RETURN (date_trunc('month', v_base) + interval '1 month')::timestamptz + interval '6 hours';
  ELSIF p_frequency = 'monthly' THEN
    v_candidate := date_trunc('month', v_base)::timestamptz + interval '24 days 6 hours';
    IF v_candidate <= v_base THEN v_candidate := (date_trunc('month', v_base) + interval '1 month 24 days 6 hours')::timestamptz; END IF;
    RETURN v_candidate;
  END IF;
  RAISE EXCEPTION 'unsupported pay frequency: %', p_frequency;
END;
$$;

UPDATE public.hr_payslip_schedules
SET next_run_at = public._next_payroll_schedule_at(pay_frequency, now())
WHERE is_active AND next_run_at IS NULL;

CREATE OR REPLACE FUNCTION public.fund_payroll_run(p_payroll_run_id UUID)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_run public.payroll_runs%ROWTYPE;
  v_existing public.payroll_run_funding%ROWTYPE;
  v_journal UUID;
  v_payslip_ids UUID[] := ARRAY[]::UUID[];
  v_ps UUID;
  v_line RECORD;
  v_lines JSONB;
BEGIN
  IF NOT (
    auth.role() = 'service_role'
    OR public.has_staff_role(ARRAY['admin','finance']::public.staff_role[])
  ) THEN RAISE EXCEPTION 'finance or admin role required'; END IF;

  SELECT * INTO v_run FROM public.payroll_runs WHERE id = p_payroll_run_id FOR UPDATE;
  IF NOT FOUND THEN RAISE EXCEPTION 'payroll run not found: %', p_payroll_run_id; END IF;
  IF v_run.status <> 'submitted' THEN RAISE EXCEPTION 'submitted payroll run required'; END IF;

  SELECT * INTO v_existing FROM public.payroll_run_funding WHERE payroll_run_id = p_payroll_run_id;
  IF FOUND THEN
    SELECT COALESCE(array_agg(id ORDER BY id), ARRAY[]::UUID[]) INTO v_payslip_ids
    FROM public.payslips WHERE payroll_run_id = p_payroll_run_id;
    RETURN jsonb_build_object(
      'payroll_run_id', p_payroll_run_id,
      'journal_entry_id', v_existing.journal_entry_id,
      'payslip_ids', to_jsonb(v_payslip_ids),
      'funded_count', cardinality(v_payslip_ids),
      'already_funded', true
    );
  END IF;

  IF v_run.total_gross <= 0 OR v_run.total_net < 0 THEN
    RAISE EXCEPTION 'invalid payroll totals for funding';
  END IF;

  v_lines := jsonb_build_array(
    jsonb_build_object('account_code','5200','debit',v_run.total_gross,'credit',0,'currency',v_run.currency),
    jsonb_build_object('account_code','1100','debit',0,'credit',v_run.total_net,'currency',v_run.currency)
  );
  IF v_run.total_deductions > 0 THEN
    v_lines := v_lines || jsonb_build_array(
      jsonb_build_object('account_code','2150','debit',0,'credit',v_run.total_deductions,'currency',v_run.currency)
    );
  END IF;

  v_journal := public.post_journal_entry(
    COALESCE(v_run.submitted_at::date, CURRENT_DATE),
    format('Payroll funded %s', COALESCE(v_run.document_number, v_run.id::text)),
    v_run.currency,
    v_run.exchange_rate,
    v_lines
  );

  PERFORM public._payroll_begin_rpc();
  FOR v_line IN SELECT id, employee_id FROM public.payroll_lines WHERE payroll_run_id = p_payroll_run_id ORDER BY line_no
  LOOP
    INSERT INTO public.payslips(payroll_line_id,payroll_run_id,employee_id,storage_bucket,storage_path,mime_type,generated_by)
    VALUES(v_line.id,p_payroll_run_id,v_line.employee_id,'payslips',format('%s/%s.pdf',p_payroll_run_id,v_line.employee_id),'application/pdf',auth.uid())
    ON CONFLICT (payroll_line_id) DO UPDATE SET storage_bucket=EXCLUDED.storage_bucket, storage_path=EXCLUDED.storage_path, mime_type=EXCLUDED.mime_type
    RETURNING id INTO v_ps;
    v_payslip_ids := array_append(v_payslip_ids, v_ps);
  END LOOP;

  INSERT INTO public.payroll_run_funding(payroll_run_id,journal_entry_id,funded_by)
  VALUES(p_payroll_run_id,v_journal,auth.uid());

  RETURN jsonb_build_object(
    'payroll_run_id', p_payroll_run_id,
    'journal_entry_id', v_journal,
    'payslip_ids', to_jsonb(v_payslip_ids),
    'funded_count', cardinality(v_payslip_ids),
    'already_funded', false
  );
END;
$$;

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
    IF v_sched.pay_frequency='weekly' THEN
      v_start := v_end - 6;
    ELSIF v_sched.pay_frequency='fortnightly' THEN
      -- Existing canonical cron is semi-monthly (1st/15th) despite the legacy enum name.
      -- Use complete non-overlapping windows: 15th..month-end, then 1st..14th.
      IF EXTRACT(DAY FROM v_due_at)::int = 15 THEN
        v_start := date_trunc('month', v_due_at)::date;
      ELSE
        v_start := (date_trunc('month', v_due_at) - interval '1 month' + interval '14 days')::date;
      END IF;
    ELSE
      v_start := (v_due_at::date - interval '1 month')::date;
    END IF;

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

REVOKE ALL ON FUNCTION public._next_payroll_schedule_at(public.hr_pay_frequency,TIMESTAMPTZ) FROM PUBLIC,anon,authenticated;
GRANT EXECUTE ON FUNCTION public._next_payroll_schedule_at(public.hr_pay_frequency,TIMESTAMPTZ) TO service_role;
REVOKE ALL ON FUNCTION public.fund_payroll_run(UUID) FROM PUBLIC,anon;
GRANT EXECUTE ON FUNCTION public.fund_payroll_run(UUID) TO authenticated,service_role;
REVOKE ALL ON FUNCTION public.payslip_render_payload(UUID) FROM PUBLIC,anon;
GRANT EXECUTE ON FUNCTION public.payslip_render_payload(UUID) TO authenticated,service_role;
REVOKE ALL ON FUNCTION public.run_due_payroll_schedules(TIMESTAMPTZ) FROM PUBLIC,anon,authenticated;
GRANT EXECUTE ON FUNCTION public.run_due_payroll_schedules(TIMESTAMPTZ) TO service_role;
