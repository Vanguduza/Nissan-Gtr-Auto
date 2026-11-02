-- Real journals: partial funding -> full-run funding -> repeat must pay each line once.
BEGIN;
SELECT set_config('request.jwt.claim.role','service_role',true);
SELECT set_config('request.jwt.claims','{"role":"service_role"}',true);
DO $$
DECLARE
  v_fin UUID:='f1111111-1111-4111-8111-111111111111';
  v_hr UUID:='f2222222-2222-4222-8222-222222222222';
  v_e1 UUID;v_e2 UUID;v_run UUID;v_l1 UUID;v_l2 UUID;v_result JSONB;v_journal UUID;
  v_count INT;v_gross NUMERIC;v_cash NUMERIC;v_deductions NUMERIC;v_denied BOOLEAN;v_case INT;
BEGIN
  INSERT INTO auth.users(id,aud,role,email,raw_app_meta_data,raw_user_meta_data,created_at,updated_at)
  VALUES(v_fin,'authenticated','authenticated','reconcile-finance@gtr.local','{}','{}',now(),now()),
    (v_hr,'authenticated','authenticated','reconcile-hr@gtr.local','{}','{}',now(),now());
  INSERT INTO public.profiles(id,full_name,is_staff) VALUES(v_fin,'Reconcile Finance',true),(v_hr,'Reconcile HR',true)
    ON CONFLICT(id) DO UPDATE SET is_staff=true;
  INSERT INTO public.staff_roles(user_id,role) VALUES(v_fin,'finance'),(v_hr,'hr');
  INSERT INTO public.employees(employee_code,full_name,status) VALUES('REC-PAY-1','Pay One','active') RETURNING id INTO v_e1;
  INSERT INTO public.employees(employee_code,full_name,status) VALUES('REC-PAY-2','Pay Two','active') RETURNING id INTO v_e2;
  PERFORM public._payroll_begin_rpc();
  INSERT INTO public.payroll_runs(status,period_start,period_end,currency,exchange_rate,total_gross,total_deductions,total_net,submitted_at)
  VALUES('submitted',CURRENT_DATE-30,CURRENT_DATE,'USD',1,200,40,160,now()) RETURNING id INTO v_run;
  INSERT INTO public.payroll_lines(payroll_run_id,employee_id,line_no,pay_type,rate_applied,gross_amount,deductions_amount,net_amount,currency,exchange_rate)
  VALUES(v_run,v_e1,1,'salary',100,100,20,80,'USD',1) RETURNING id INTO v_l1;
  INSERT INTO public.payroll_lines(payroll_run_id,employee_id,line_no,pay_type,rate_applied,gross_amount,deductions_amount,net_amount,currency,exchange_rate)
  VALUES(v_run,v_e2,2,'salary',100,100,20,80,'USD',1) RETURNING id INTO v_l2;
  INSERT INTO public.payroll_deduction_lines(payroll_line_id,label,amount,currency) VALUES(v_l1,'Manual advance',20,'USD'),(v_l2,'Manual advance',20,'USD');
  PERFORM set_config('app.payroll_rpc','',true);
  PERFORM set_config('request.jwt.claim.sub',v_fin::text,true);
  PERFORM set_config('request.jwt.claim.role','authenticated',true);
  PERFORM set_config('request.jwt.claims',json_build_object('sub',v_fin::text,'role','authenticated')::text,true);
  v_result:=public.fund_payroll_lines(ARRAY[v_l1],'1100',CURRENT_DATE);
  IF (v_result->>'funded_count')::int<>1 OR (v_result->>'total_net')::numeric<>80 THEN RAISE EXCEPTION 'partial funding incorrect: %',v_result; END IF;
  v_result:=public.fund_payroll_run(v_run);
  IF (v_result->>'funded_count')::int<>1 OR (v_result->>'skipped_count')::int<>1 THEN RAISE EXCEPTION 'full-run wrapper repaid a selected line: %',v_result; END IF;
  SELECT count(*) INTO v_count FROM public.journal_entries;
  v_result:=public.fund_payroll_run(v_run);
  IF (v_result->>'funded_count')::int<>0 OR NOT (v_result->>'already_funded')::boolean THEN RAISE EXCEPTION 're-funding not idempotent'; END IF;
  IF (SELECT count(*) FROM public.journal_entries)<>v_count THEN RAISE EXCEPTION 'repeat created another journal'; END IF;
  SELECT COALESCE(sum(l.debit) FILTER(WHERE l.account_code='5200'),0),
    COALESCE(sum(l.credit) FILTER(WHERE l.account_code='1100'),0),
    COALESCE(sum(l.credit) FILTER(WHERE l.account_code='2150'),0)
  INTO v_gross,v_cash,v_deductions FROM public.journal_entry_lines l
  WHERE journal_entry_id IN(SELECT DISTINCT payment_journal_id FROM public.payroll_lines WHERE payroll_run_id=v_run);
  IF v_gross<>200 OR v_cash<>160 OR v_deductions<>40 THEN RAISE EXCEPTION 'gross/manual-deduction accounting wrong: %/%/%',v_gross,v_cash,v_deductions; END IF;
  IF (SELECT count(*) FROM public.payslips WHERE payroll_run_id=v_run)<>2 THEN RAISE EXCEPTION 'payslip metadata missing'; END IF;
  IF COALESCE(current_setting('app.payroll_rpc',true),'')<>'' THEN RAISE EXCEPTION 'payroll mutation capability leaked'; END IF;
  PERFORM public.upsert_hr_payslip_schedule('monthly','0 6 25 * *',now()+interval '30 days',false,'1100','USD',1,true);
  PERFORM set_config('request.jwt.claim.sub',v_hr::text,true);
  PERFORM set_config('request.jwt.claims',json_build_object('sub',v_hr::text,'role','authenticated')::text,true);
  FOR v_case IN 1..7 LOOP
    v_denied:=false;
    BEGIN
      CASE v_case
        WHEN 1 THEN PERFORM public.upsert_hr_payslip_schedule('monthly',p_cash_account_code=>'1100');
        WHEN 2 THEN PERFORM public.upsert_hr_payslip_schedule('monthly',p_currency=>'ZIG');
        WHEN 3 THEN PERFORM public.upsert_hr_payslip_schedule('monthly',p_default_exchange_rate=>1);
        WHEN 4 THEN PERFORM public.upsert_hr_payslip_schedule('monthly',p_auto_fund=>true);
        WHEN 5 THEN PERFORM public.upsert_hr_payslip_schedule('monthly',p_next_run_at=>now()-interval '1 day');
        WHEN 6 THEN PERFORM public.upsert_hr_payslip_schedule('monthly',p_is_active=>true);
        ELSE PERFORM public.upsert_hr_payslip_schedule('monthly',p_cron_expr=>'0 1 * * *');
      END CASE;
    EXCEPTION WHEN OTHERS THEN v_denied:=true; END;
    IF NOT v_denied THEN RAISE EXCEPTION 'HR schedule funding escalation accepted, case %',v_case; END IF;
  END LOOP;
  v_denied:=false;
  BEGIN PERFORM public.fund_payroll_lines(ARRAY[v_l1],'1100',CURRENT_DATE);
  EXCEPTION WHEN OTHERS THEN v_denied:=true; END;
  IF NOT v_denied THEN RAISE EXCEPTION 'HR role acquired direct funding authority'; END IF;
  IF has_table_privilege('authenticated','public.hr_payslip_schedules','UPDATE') THEN RAISE EXCEPTION 'direct schedule writes bypass RPC authority'; END IF;
  IF (SELECT count(*) FROM public.journal_entries)<>v_count THEN RAISE EXCEPTION 'rejected HR request changed journals'; END IF;
  RAISE NOTICE 'staff payroll reconciliation smoke passed';
END;
$$;
ROLLBACK;
