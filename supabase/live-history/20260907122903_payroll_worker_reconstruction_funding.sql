-- Exported from the hosted project's supabase_migrations.schema_migrations (20260907122903 payroll_worker_reconstruction_funding).
-- Source of record for what production ran; see supabase/live-history/README.md.

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

REVOKE ALL ON FUNCTION public.fund_payroll_run(UUID) FROM PUBLIC,anon;
GRANT EXECUTE ON FUNCTION public.fund_payroll_run(UUID) TO authenticated,service_role;
