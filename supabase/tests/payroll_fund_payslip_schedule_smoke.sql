-- Payroll fund + schedule smoke (postgres).
-- Asserts: CoA 2150, two-step JE (5200/2150 then 2150/cash), export_payslip,
-- idempotent re-fund, schedule upsert, no statutory columns.
-- No PAYE/NSSA/ZIMRA. Run after 20260816010000_payroll_fund_payslip_schedule.sql.

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
  v_admin UUID := 'a0000000-0000-4000-8000-000000000001';
  v_emp_user UUID := 'c0000000-0000-4000-8000-000000000021';
  v_emp UUID;
  v_struct UUID;
  v_run UUID;
  v_line UUID;
  v_fund JSONB;
  v_fund2 JSONB;
  v_accrual UUID;
  v_payment UUID;
  v_debit NUMERIC;
  v_credit NUMERIC;
  v_day DATE := DATE '2026-08-16';
  v_sched UUID;
  v_tax_cols INT;
  v_payload JSONB;
BEGIN
  SELECT COUNT(*) INTO v_tax_cols
  FROM information_schema.columns
  WHERE table_schema = 'public'
    AND table_name IN (
      'payroll_runs', 'payroll_lines', 'payroll_deduction_lines', 'payslips'
    )
    AND column_name ~* '(paye|nssa|pobs|apwcs|zimdef|tax_code|tax_bracket|statutory|zimra|fiscal)';

  IF v_tax_cols <> 0 THEN
    RAISE EXCEPTION 'smoke fail: forbidden tax/statutory columns present (%)', v_tax_cols;
  END IF;

  IF NOT EXISTS (SELECT 1 FROM public.chart_of_accounts WHERE code = '2150') THEN
    RAISE EXCEPTION 'smoke fail: CoA 2150 Salaries Payable missing';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM public.chart_of_accounts WHERE code = '5200') THEN
    RAISE EXCEPTION 'smoke fail: CoA 5200 Payroll Expense missing';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM public.chart_of_accounts WHERE code = '1100') THEN
    RAISE EXCEPTION 'smoke fail: CoA 1100 cash/bank missing';
  END IF;

  INSERT INTO auth.users (
    instance_id, id, aud, role, email, encrypted_password, email_confirmed_at,
    raw_app_meta_data, raw_user_meta_data, created_at, updated_at,
    confirmation_token, recovery_token, email_change_token_new, email_change
  )
  VALUES (
    '00000000-0000-0000-0000-000000000000',
    v_emp_user,
    'authenticated',
    'authenticated',
    'emp-fund-smoke@gtr.local',
    crypt('local-dev-emp', gen_salt('bf')),
    now(),
    '{"provider":"email","providers":["email"]}'::jsonb,
    '{"full_name":"Fund Smoke Emp"}'::jsonb,
    now(),
    now(),
    '', '', '', ''
  )
  ON CONFLICT (id) DO NOTHING;

  INSERT INTO public.profiles (id, full_name, is_staff)
  VALUES (v_emp_user, 'Fund Smoke Emp', false)
  ON CONFLICT (id) DO NOTHING;

  PERFORM public._test_set_auth_uid(v_admin);

  SELECT id INTO v_emp FROM public.employees WHERE employee_code = 'PF-SMOKE-1';
  IF v_emp IS NULL THEN
    v_emp := public.create_employee(
      'PF-SMOKE-1',
      'Payroll Fund Smoke',
      v_emp_user,
      'emp-fund-smoke@gtr.local',
      NULL,
      v_day - 30
    );
  ELSE
    UPDATE public.employees
    SET user_id = v_emp_user, full_name = 'Payroll Fund Smoke', status = 'active', updated_at = now()
    WHERE id = v_emp;
  END IF;

  SELECT id INTO v_struct
  FROM public.salary_structures
  WHERE employee_id = v_emp AND is_active AND pay_type = 'salary' AND currency = 'USD'
  ORDER BY effective_from DESC
  LIMIT 1;

  IF v_struct IS NULL THEN
    v_struct := public.upsert_salary_structure(
      v_emp, 'salary', 1000.0000, 'USD', v_day - 30, NULL
    );
  END IF;

  v_run := public.create_payroll_run(v_day, v_day, 'USD', 1, 'Fund smoke run');
  PERFORM public.compute_payroll_run(v_run, ARRAY[v_emp]);

  SELECT id INTO v_line
  FROM public.payroll_lines
  WHERE payroll_run_id = v_run AND employee_id = v_emp;
  IF v_line IS NULL THEN
    RAISE EXCEPTION 'smoke fail: payroll line missing';
  END IF;

  PERFORM public.add_payroll_deduction(v_line, 'Uniform advance', 50);
  PERFORM public.submit_payroll_run(v_run);

  v_fund := public.fund_payroll_lines(ARRAY[v_line], '1100', v_day);
  IF (v_fund ->> 'funded_count')::int <> 1 THEN
    RAISE EXCEPTION 'smoke fail: expected funded_count=1 got %', v_fund;
  END IF;
  IF (v_fund ->> 'total_net')::numeric <> 950 THEN
    RAISE EXCEPTION 'smoke fail: expected total_net=950 got %', v_fund;
  END IF;

  v_accrual := (v_fund ->> 'accrual_journal_id')::uuid;
  v_payment := (v_fund ->> 'payment_journal_id')::uuid;

  SELECT COALESCE(SUM(debit), 0), COALESCE(SUM(credit), 0)
  INTO v_debit, v_credit
  FROM public.journal_entry_lines
  WHERE journal_entry_id = v_accrual;
  IF abs(v_debit - v_credit) > 0.009 OR v_debit <> 950 THEN
    RAISE EXCEPTION 'smoke fail: accrual JE unbalanced or wrong net %/%', v_debit, v_credit;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM public.journal_entry_lines
    WHERE journal_entry_id = v_accrual AND account_code = '5200' AND debit = 950
  ) THEN
    RAISE EXCEPTION 'smoke fail: accrual missing Dr 5200';
  END IF;
  IF NOT EXISTS (
    SELECT 1 FROM public.journal_entry_lines
    WHERE journal_entry_id = v_accrual AND account_code = '2150' AND credit = 950
  ) THEN
    RAISE EXCEPTION 'smoke fail: accrual missing Cr 2150';
  END IF;
  IF NOT EXISTS (
    SELECT 1 FROM public.journal_entry_lines
    WHERE journal_entry_id = v_payment AND account_code = '2150' AND debit = 950
  ) THEN
    RAISE EXCEPTION 'smoke fail: payment missing Dr 2150';
  END IF;
  IF NOT EXISTS (
    SELECT 1 FROM public.journal_entry_lines
    WHERE journal_entry_id = v_payment AND account_code = '1100' AND credit = 950
  ) THEN
    RAISE EXCEPTION 'smoke fail: payment missing Cr 1100';
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM public.payroll_lines
    WHERE id = v_line AND payment_journal_id = v_payment AND funded_at IS NOT NULL
  ) THEN
    RAISE EXCEPTION 'smoke fail: line not marked funded';
  END IF;

  IF NOT EXISTS (SELECT 1 FROM public.payslips WHERE payroll_line_id = v_line) THEN
    RAISE EXCEPTION 'smoke fail: payslip metadata missing after fund';
  END IF;

  v_fund2 := public.fund_payroll_lines(ARRAY[v_line], '1100', v_day);
  IF (v_fund2 ->> 'funded_count')::int <> 0 OR (v_fund2 ->> 'skipped_count')::int <> 1 THEN
    RAISE EXCEPTION 'smoke fail: re-fund not idempotent %', v_fund2;
  END IF;

  v_sched := public.upsert_hr_payslip_schedule(
    'monthly',
    '0 6 25 * *',
    now() + INTERVAL '30 days',
    true,
    '1100',
    'USD',
    1,
    true
  );
  IF v_sched IS NULL THEN
    RAISE EXCEPTION 'smoke fail: upsert schedule failed';
  END IF;

  v_payload := public.payslip_render_payload(v_line);
  IF (v_payload ->> 'netPay')::numeric <> 950
     OR (v_payload ->> 'grossPay')::numeric <> 1000 THEN
    RAISE EXCEPTION 'smoke fail: payslip payload %', v_payload;
  END IF;

  RAISE NOTICE 'payroll_fund_payslip_schedule_smoke PASS';
END;
$$;
