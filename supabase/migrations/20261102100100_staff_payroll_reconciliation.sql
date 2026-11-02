-- Add selected-line funding and schedule controls without duplicating hosted payroll payouts.
-- The UUID-only fund_payroll_run contract remains available to the canonical worker.
ALTER TABLE public.payroll_lines
  ADD COLUMN IF NOT EXISTS accrual_journal_id UUID REFERENCES public.journal_entries(id),
  ADD COLUMN IF NOT EXISTS payment_journal_id UUID REFERENCES public.journal_entries(id),
  ADD COLUMN IF NOT EXISTS funded_at TIMESTAMPTZ,
  ADD COLUMN IF NOT EXISTS funded_by UUID REFERENCES auth.users(id),
  ADD COLUMN IF NOT EXISTS cash_account_code VARCHAR(10) REFERENCES public.chart_of_accounts(code);

-- Hosted full-run funding predates per-line markers. Migrate those receipts before accepting selections.
DO $$
DECLARE v_previous TEXT := current_setting('app.payroll_rpc',true);
BEGIN
  PERFORM public._payroll_begin_rpc();
  UPDATE public.payroll_lines l SET accrual_journal_id=f.journal_entry_id,
    payment_journal_id=f.journal_entry_id,funded_at=f.funded_at,funded_by=f.funded_by,
    cash_account_code=COALESCE(l.cash_account_code,'1100')
  FROM public.payroll_run_funding f
  WHERE f.payroll_run_id=l.payroll_run_id AND l.payment_journal_id IS NULL;
  PERFORM set_config('app.payroll_rpc',COALESCE(v_previous,''),true);
END;
$$;

ALTER TABLE public.hr_payslip_schedules
  ADD COLUMN IF NOT EXISTS cash_account_code VARCHAR(10) REFERENCES public.chart_of_accounts(code) DEFAULT '1100',
  ADD COLUMN IF NOT EXISTS currency public.currency_code NOT NULL DEFAULT 'USD',
  ADD COLUMN IF NOT EXISTS default_exchange_rate NUMERIC(18,8) NOT NULL DEFAULT 1 CHECK(default_exchange_rate>0),
  ADD COLUMN IF NOT EXISTS auto_fund BOOLEAN NOT NULL DEFAULT true,
  ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  ADD COLUMN IF NOT EXISTS updated_by UUID REFERENCES auth.users(id);

CREATE OR REPLACE FUNCTION public.fund_payroll_lines(
  p_payroll_line_ids UUID[],p_cash_account_code VARCHAR DEFAULT '1100',p_entry_date DATE DEFAULT NULL
) RETURNS JSONB LANGUAGE plpgsql SECURITY DEFINER SET search_path=public AS $$
DECLARE
  v_ids UUID[]; v_run public.payroll_runs%ROWTYPE; v_journal UUID;
  v_cash VARCHAR(10):=COALESCE(NULLIF(trim(p_cash_account_code),''),'1100');
  v_date DATE:=COALESCE(p_entry_date,CURRENT_DATE);
  v_gross NUMERIC:=0; v_net NUMERIC:=0; v_deductions NUMERIC:=0;
  v_count INT:=0; v_skipped INT:=0; v_line RECORD; v_ps UUID;
  v_payslip_ids UUID[]:=ARRAY[]::UUID[]; v_lines JSONB;
  v_previous TEXT:=current_setting('app.payroll_rpc',true);
BEGIN
  IF auth.role()<>'service_role' AND NOT public.has_staff_role(ARRAY['admin','finance']::public.staff_role[]) THEN
    RAISE EXCEPTION 'finance or admin role required';
  END IF;
  SELECT array_agg(DISTINCT id) INTO v_ids FROM unnest(p_payroll_line_ids) id WHERE id IS NOT NULL;
  IF COALESCE(cardinality(v_ids),0)=0 THEN RAISE EXCEPTION 'payroll line ids required'; END IF;
  IF (SELECT count(*) FROM public.payroll_lines WHERE id=ANY(v_ids))<>cardinality(v_ids) THEN
    RAISE EXCEPTION 'unknown payroll line id';
  END IF;
  SELECT * INTO v_run FROM public.payroll_runs
  WHERE id=(SELECT payroll_run_id FROM public.payroll_lines WHERE id=v_ids[1]) FOR UPDATE;
  IF v_run.status<>'submitted' THEN RAISE EXCEPTION 'submitted payroll run required'; END IF;
  IF EXISTS(SELECT 1 FROM public.payroll_lines WHERE id=ANY(v_ids) AND
    (payroll_run_id<>v_run.id OR currency<>v_run.currency OR net_amount<0)) THEN
    RAISE EXCEPTION 'all selected lines must belong to one run, match its currency and have nonnegative net';
  END IF;
  IF NOT EXISTS(SELECT 1 FROM public.chart_of_accounts WHERE code=v_cash AND account_type='asset' AND is_active) THEN
    RAISE EXCEPTION 'active cash/bank asset account required';
  END IF;
  -- The parent run lock serializes full-run, partial and repeated requests before any journal is posted.
  SELECT COALESCE(sum(gross_amount),0),COALESCE(sum(net_amount),0),COALESCE(sum(deductions_amount),0),count(*)
  INTO v_gross,v_net,v_deductions,v_count FROM public.payroll_lines
  WHERE id=ANY(v_ids) AND payment_journal_id IS NULL;
  SELECT count(*) INTO v_skipped FROM public.payroll_lines WHERE id=ANY(v_ids) AND payment_journal_id IS NOT NULL;
  IF v_count=0 THEN
    SELECT array_agg(ps.id ORDER BY ps.id) INTO v_payslip_ids FROM public.payslips ps WHERE payroll_line_id=ANY(v_ids);
    SELECT payment_journal_id INTO v_journal FROM public.payroll_lines WHERE id=ANY(v_ids) ORDER BY funded_at DESC LIMIT 1;
    RETURN jsonb_build_object('payroll_run_id',v_run.id,'funded_count',0,'skipped_count',v_skipped,
      'already_funded',true,'journal_entry_id',v_journal,'accrual_journal_id',v_journal,'payment_journal_id',v_journal,
      'total_net',0,'currency',v_run.currency,'cash_account_code',v_cash,'payslip_ids',COALESCE(to_jsonb(v_payslip_ids),'[]'::jsonb));
  END IF;
  IF v_gross<=0 OR v_gross<>v_net+v_deductions THEN RAISE EXCEPTION 'invalid payroll totals'; END IF;
  -- Preserve native gross-pay accounting, including manual deductions; never expense only the net amount.
  v_lines:=jsonb_build_array(jsonb_build_object('account_code','5200','debit',v_gross,'credit',0,'currency',v_run.currency));
  IF v_net>0 THEN
    v_lines:=v_lines||jsonb_build_array(jsonb_build_object('account_code',v_cash,'debit',0,'credit',v_net,'currency',v_run.currency));
  END IF;
  IF v_deductions>0 THEN
    v_lines:=v_lines||jsonb_build_array(jsonb_build_object('account_code','2150','debit',0,'credit',v_deductions,'currency',v_run.currency));
  END IF;
  v_journal:=public.post_journal_entry(v_date,format('Payroll funded %s',COALESCE(v_run.document_number,v_run.id::text)),
    v_run.currency,v_run.exchange_rate,v_lines);
  PERFORM public._payroll_begin_rpc();
  FOR v_line IN SELECT id,employee_id FROM public.payroll_lines WHERE id=ANY(v_ids) AND payment_journal_id IS NULL ORDER BY id FOR UPDATE
  LOOP
    UPDATE public.payroll_lines SET accrual_journal_id=v_journal,payment_journal_id=v_journal,
      funded_at=now(),funded_by=auth.uid(),cash_account_code=v_cash WHERE id=v_line.id;
    INSERT INTO public.payslips(payroll_line_id,payroll_run_id,employee_id,storage_bucket,storage_path,mime_type,generated_by)
    VALUES(v_line.id,v_run.id,v_line.employee_id,'payslips',format('%s/%s.pdf',v_run.id,v_line.employee_id),'application/pdf',auth.uid())
    ON CONFLICT(payroll_line_id) DO UPDATE SET storage_bucket=EXCLUDED.storage_bucket,storage_path=EXCLUDED.storage_path,mime_type=EXCLUDED.mime_type
    RETURNING id INTO v_ps;
    v_payslip_ids:=array_append(v_payslip_ids,v_ps);
  END LOOP;
  IF NOT EXISTS(SELECT 1 FROM public.payroll_lines WHERE payroll_run_id=v_run.id AND payment_journal_id IS NULL) THEN
    INSERT INTO public.payroll_run_funding(payroll_run_id,journal_entry_id,funded_by)
    VALUES(v_run.id,v_journal,auth.uid()) ON CONFLICT(payroll_run_id) DO NOTHING;
  END IF;
  PERFORM set_config('app.payroll_rpc',COALESCE(v_previous,''),true);
  RETURN jsonb_build_object('payroll_run_id',v_run.id,'funded_count',v_count,'skipped_count',v_skipped,
    'already_funded',false,'journal_entry_id',v_journal,'accrual_journal_id',v_journal,'payment_journal_id',v_journal,
    'total_net',v_net,'currency',v_run.currency,'cash_account_code',v_cash,'payslip_ids',to_jsonb(v_payslip_ids));
END;
$$;
REVOKE ALL ON FUNCTION public.fund_payroll_lines(UUID[],VARCHAR,DATE) FROM PUBLIC,anon;
GRANT EXECUTE ON FUNCTION public.fund_payroll_lines(UUID[],VARCHAR,DATE) TO authenticated,service_role;

-- Distinct name avoids ambiguous PostgREST resolution against the native UUID-only RPC.
CREATE OR REPLACE FUNCTION public.fund_payroll_run_selected(
  p_payroll_run_id UUID,p_cash_account_code VARCHAR DEFAULT '1100',p_entry_date DATE DEFAULT NULL,p_employee_ids UUID[] DEFAULT NULL
) RETURNS JSONB LANGUAGE plpgsql SECURITY DEFINER SET search_path=public AS $$
DECLARE v_ids UUID[];
BEGIN
  SELECT array_agg(id ORDER BY id) INTO v_ids FROM public.payroll_lines
  WHERE payroll_run_id=p_payroll_run_id AND (p_employee_ids IS NULL OR cardinality(p_employee_ids)=0 OR employee_id=ANY(p_employee_ids));
  RETURN public.fund_payroll_lines(v_ids,p_cash_account_code,p_entry_date);
END;
$$;
REVOKE ALL ON FUNCTION public.fund_payroll_run_selected(UUID,VARCHAR,DATE,UUID[]) FROM PUBLIC,anon;
GRANT EXECUTE ON FUNCTION public.fund_payroll_run_selected(UUID,VARCHAR,DATE,UUID[]) TO authenticated,service_role;

CREATE OR REPLACE FUNCTION public.fund_payroll_run(p_payroll_run_id UUID)
RETURNS JSONB LANGUAGE plpgsql SECURITY DEFINER SET search_path=public AS $$
BEGIN
  RETURN public.fund_payroll_run_selected(p_payroll_run_id,'1100',NULL,NULL);
END;
$$;
REVOKE ALL ON FUNCTION public.fund_payroll_run(UUID) FROM PUBLIC,anon;
GRANT EXECUTE ON FUNCTION public.fund_payroll_run(UUID) TO authenticated,service_role;

-- Funding readers get payroll amounts plus a minimal employee label, never banking/health fields.
CREATE OR REPLACE FUNCTION public.list_payroll_funding_employees()
RETURNS TABLE(id UUID,employee_code VARCHAR,full_name TEXT,status TEXT)
LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path=public AS $$
BEGIN
  IF auth.role()<>'service_role' AND NOT public.has_staff_role(ARRAY['admin','finance','hr']::public.staff_role[]) THEN
    RAISE EXCEPTION 'payroll staff required';
  END IF;
  RETURN QUERY SELECT e.id,e.employee_code,e.full_name,e.status::text FROM public.employees e ORDER BY e.full_name,e.id;
END;
$$;
REVOKE ALL ON FUNCTION public.list_payroll_funding_employees() FROM PUBLIC,anon;
GRANT EXECUTE ON FUNCTION public.list_payroll_funding_employees() TO authenticated,service_role;
DROP POLICY IF EXISTS payroll_runs_select_finance ON public.payroll_runs;
CREATE POLICY payroll_runs_select_finance ON public.payroll_runs FOR SELECT TO authenticated
  USING(public.has_staff_role(ARRAY['admin','finance']::public.staff_role[]));
DROP POLICY IF EXISTS payroll_lines_select_finance ON public.payroll_lines;
CREATE POLICY payroll_lines_select_finance ON public.payroll_lines FOR SELECT TO authenticated
  USING(public.has_staff_role(ARRAY['admin','finance']::public.staff_role[]));
DROP POLICY IF EXISTS payroll_deductions_select_finance ON public.payroll_deduction_lines;
CREATE POLICY payroll_deductions_select_finance ON public.payroll_deduction_lines FOR SELECT TO authenticated
  USING(public.has_staff_role(ARRAY['admin','finance']::public.staff_role[]));
DROP POLICY IF EXISTS payslips_select_finance ON public.payslips;
CREATE POLICY payslips_select_finance ON public.payslips FOR SELECT TO authenticated
  USING(public.has_staff_role(ARRAY['admin','finance']::public.staff_role[]));
DROP POLICY IF EXISTS hr_payslip_schedules_select_finance ON public.hr_payslip_schedules;
CREATE POLICY hr_payslip_schedules_select_finance ON public.hr_payslip_schedules FOR SELECT TO authenticated
  USING(public.has_staff_role(ARRAY['admin','finance']::public.staff_role[]));

-- All authenticated schedule changes pass through the authority check below.
REVOKE INSERT,UPDATE,DELETE ON TABLE public.hr_payslip_schedules FROM authenticated;
GRANT SELECT ON TABLE public.hr_payslip_schedules TO authenticated;

CREATE OR REPLACE FUNCTION public.upsert_hr_payslip_schedule(
  p_pay_frequency public.hr_pay_frequency,p_cron_expr TEXT DEFAULT NULL,p_next_run_at TIMESTAMPTZ DEFAULT NULL,
  p_is_active BOOLEAN DEFAULT NULL,p_cash_account_code VARCHAR DEFAULT NULL,p_currency public.currency_code DEFAULT NULL,
  p_default_exchange_rate NUMERIC DEFAULT NULL,p_auto_fund BOOLEAN DEFAULT NULL
) RETURNS UUID LANGUAGE plpgsql SECURITY DEFINER SET search_path=public AS $$
DECLARE v_id UUID; v_finance BOOLEAN; v_existing public.hr_payslip_schedules%ROWTYPE;
BEGIN
  v_finance:=auth.role()='service_role' OR public.has_staff_role(ARRAY['admin','finance']::public.staff_role[]);
  IF NOT v_finance AND NOT public.has_staff_role(ARRAY['hr']::public.staff_role[]) THEN RAISE EXCEPTION 'payroll staff required'; END IF;
  PERFORM pg_advisory_xact_lock(hashtext('staff-payroll-schedule:'||p_pay_frequency::text)::bigint);
  SELECT * INTO v_existing FROM public.hr_payslip_schedules WHERE pay_frequency=p_pay_frequency FOR UPDATE;
  IF NOT v_finance THEN
    IF p_auto_fund IS NOT NULL OR p_cash_account_code IS NOT NULL OR p_currency IS NOT NULL OR p_default_exchange_rate IS NOT NULL THEN
      RAISE EXCEPTION 'finance or admin required to change funding terms';
    END IF;
    IF v_existing.id IS NOT NULL AND v_existing.auto_fund AND (
      (p_cron_expr IS NOT NULL AND p_cron_expr IS DISTINCT FROM v_existing.cron_expr) OR
      (p_next_run_at IS NOT NULL AND p_next_run_at IS DISTINCT FROM v_existing.next_run_at) OR
      (p_is_active IS NOT NULL AND p_is_active IS DISTINCT FROM v_existing.is_active)
    ) THEN RAISE EXCEPTION 'finance approval required to change an automatically funded schedule'; END IF;
  END IF;
  IF p_default_exchange_rate IS NOT NULL AND p_default_exchange_rate<=0 THEN RAISE EXCEPTION 'positive exchange rate required'; END IF;
  IF p_cash_account_code IS NOT NULL AND NOT EXISTS(SELECT 1 FROM public.chart_of_accounts WHERE code=trim(p_cash_account_code) AND account_type='asset' AND is_active) THEN
    RAISE EXCEPTION 'active cash/bank asset account required';
  END IF;
  IF p_cron_expr IS NOT NULL AND (length(p_cron_expr)>100 OR p_cron_expr!~ '^[-0-9*/,]+[ ]+[-0-9*/,]+[ ]+[-0-9*/,]+[ ]+[-0-9*/,]+[ ]+[-0-9*/,]+$') THEN
    RAISE EXCEPTION 'five-field cadence reference required';
  END IF;
  INSERT INTO public.hr_payslip_schedules(pay_frequency,cron_expr,next_run_at,is_active,cash_account_code,currency,default_exchange_rate,auto_fund,updated_at,updated_by)
  VALUES(p_pay_frequency,COALESCE(NULLIF(trim(p_cron_expr),''),'0 6 25 * *'),
    COALESCE(p_next_run_at,public._next_payroll_schedule_at(p_pay_frequency,now())),COALESCE(p_is_active,true),
    COALESCE(NULLIF(trim(p_cash_account_code),''),'1100'),COALESCE(p_currency,'USD'),COALESCE(p_default_exchange_rate,1),COALESCE(p_auto_fund,v_finance),now(),auth.uid())
  ON CONFLICT(pay_frequency) DO UPDATE SET cron_expr=COALESCE(p_cron_expr,public.hr_payslip_schedules.cron_expr),
    next_run_at=COALESCE(p_next_run_at,public.hr_payslip_schedules.next_run_at),
    is_active=COALESCE(p_is_active,public.hr_payslip_schedules.is_active),cash_account_code=COALESCE(p_cash_account_code,public.hr_payslip_schedules.cash_account_code),
    currency=COALESCE(p_currency,public.hr_payslip_schedules.currency),default_exchange_rate=COALESCE(p_default_exchange_rate,public.hr_payslip_schedules.default_exchange_rate),
    auto_fund=COALESCE(p_auto_fund,public.hr_payslip_schedules.auto_fund),updated_at=now(),updated_by=auth.uid()
  RETURNING id INTO v_id;
  RETURN v_id;
END;
$$;
REVOKE ALL ON FUNCTION public.upsert_hr_payslip_schedule(public.hr_pay_frequency,TEXT,TIMESTAMPTZ,BOOLEAN,VARCHAR,public.currency_code,NUMERIC,BOOLEAN) FROM PUBLIC,anon;
GRANT EXECUTE ON FUNCTION public.upsert_hr_payslip_schedule(public.hr_pay_frequency,TEXT,TIMESTAMPTZ,BOOLEAN,VARCHAR,public.currency_code,NUMERIC,BOOLEAN) TO authenticated,service_role;

CREATE OR REPLACE FUNCTION public.run_due_payroll_schedules(p_as_of TIMESTAMPTZ DEFAULT now())
RETURNS JSONB LANGUAGE plpgsql SECURITY DEFINER SET search_path=public AS $$
DECLARE v_sched public.hr_payslip_schedules%ROWTYPE;v_end DATE;v_start DATE;v_run UUID;v_fund JSONB;v_results JSONB:='[]'::jsonb;
BEGIN
  IF auth.role()<>'service_role' AND NOT public.has_staff_role(ARRAY['admin']::public.staff_role[]) THEN RAISE EXCEPTION 'service role or admin required'; END IF;
  FOR v_sched IN SELECT * FROM public.hr_payslip_schedules WHERE is_active AND next_run_at<=COALESCE(p_as_of,now()) ORDER BY next_run_at,id FOR UPDATE SKIP LOCKED
  LOOP
    BEGIN
      v_end:=v_sched.next_run_at::date-1;
      v_start:=CASE v_sched.pay_frequency WHEN 'weekly' THEN v_end-6 WHEN 'fortnightly' THEN v_end-13 ELSE (v_sched.next_run_at::date-interval '1 month')::date END;
      v_run:=public.run_scheduled_payroll_for_frequency(v_sched.pay_frequency,v_start,v_end,v_sched.currency,v_sched.default_exchange_rate);
      PERFORM public.submit_payroll_run(v_run);
      v_fund:=CASE WHEN v_sched.auto_fund THEN public.fund_payroll_run_selected(v_run,v_sched.cash_account_code,NULL,NULL)
        ELSE jsonb_build_object('payslip_ids','[]'::jsonb,'funded_count',0) END;
      UPDATE public.hr_payslip_schedules SET last_run_at=v_sched.next_run_at,
        next_run_at=public._next_payroll_schedule_at(pay_frequency,v_sched.next_run_at+interval '1 second'),updated_at=now() WHERE id=v_sched.id;
      v_results:=v_results||jsonb_build_array(jsonb_build_object('pay_frequency',v_sched.pay_frequency,'payroll_run_id',v_run,'ok',true,'fund',v_fund));
    EXCEPTION WHEN OTHERS THEN
      v_results:=v_results||jsonb_build_array(jsonb_build_object('pay_frequency',v_sched.pay_frequency,'ok',false,'error',SQLERRM));
    END;
  END LOOP;
  RETURN jsonb_build_object('as_of',COALESCE(p_as_of,now()),'results',v_results);
END;
$$;
REVOKE ALL ON FUNCTION public.run_due_payroll_schedules(TIMESTAMPTZ) FROM PUBLIC,anon,authenticated;
GRANT EXECUTE ON FUNCTION public.run_due_payroll_schedules(TIMESTAMPTZ) TO service_role;
