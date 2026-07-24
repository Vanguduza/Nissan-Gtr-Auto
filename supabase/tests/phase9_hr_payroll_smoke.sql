-- Phase 9 HR / gross payroll smoke (postgres).
-- Asserts: tax-free schema, clock → compute → manual deductions → net,
-- submit immutability, cancel void, SMS emit. No PAYE/NSSA/statutory fields.

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
  v_wh UUID := 'a0000000-0000-4000-8000-000000000003';
  v_emp_user UUID := 'c0000000-0000-4000-8000-000000000020';
  v_emp UUID;
  v_struct UUID;
  v_run UUID;
  v_line UUID;
  v_ded UUID;
  v_payslip UUID;
  v_hours NUMERIC;
  v_gross NUMERIC;
  v_net NUMERIC;
  v_ded_sum NUMERIC;
  v_tax_cols INT;
  v_evt UUID;
  v_day DATE := DATE '2026-07-20';
BEGIN
  -- -----------------------------------------------------------------------
  -- Schema: deny statutory/tax column names on payroll tables
  -- -----------------------------------------------------------------------
  SELECT COUNT(*) INTO v_tax_cols
  FROM information_schema.columns
  WHERE table_schema = 'public'
    AND table_name IN (
      'employees', 'salary_structures', 'attendance_events',
      'payroll_runs', 'payroll_lines', 'payroll_deduction_lines', 'payslips'
    )
    AND column_name ~* '(paye|nssa|pobs|apwcs|zimdef|tax_code|tax_bracket|statutory|zimra|fiscal|p4a?)';

  IF v_tax_cols <> 0 THEN
    RAISE EXCEPTION 'smoke fail: forbidden tax/statutory columns present (%)', v_tax_cols;
  END IF;

  -- Seed employee auth user + profile
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
    'emp-p9-smoke@gtr.local',
    crypt('local-dev-emp', gen_salt('bf')),
    now(),
    '{"provider":"email","providers":["email"]}'::jsonb,
    '{"full_name":"Phase9 Emp"}'::jsonb,
    now(),
    now(),
    '', '', '', ''
  )
  ON CONFLICT (id) DO NOTHING;

  INSERT INTO public.profiles (id, full_name, is_staff)
  VALUES (v_emp_user, 'Phase9 Emp', false)
  ON CONFLICT (id) DO NOTHING;

  PERFORM public._test_set_auth_uid(v_admin);

  -- Ensure admin can act as HR (admin is enough via _require_hr_staff)
  SELECT id INTO v_emp FROM public.employees WHERE employee_code = 'P9-EMP-001';
  IF v_emp IS NULL THEN
    v_emp := public.create_employee(
      'P9-EMP-001',
      'Phase9 Hourly Worker',
      v_emp_user,
      'emp-p9-smoke@gtr.local',
      NULL,
      v_day - 30
    );
  ELSE
    UPDATE public.employees
    SET user_id = v_emp_user, full_name = 'Phase9 Hourly Worker', status = 'active', updated_at = now()
    WHERE id = v_emp;
  END IF;

  SELECT id INTO v_struct
  FROM public.salary_structures
  WHERE employee_id = v_emp
    AND is_active
    AND pay_type = 'hourly'
    AND rate = 10.0000
    AND currency = 'USD'
  ORDER BY effective_from DESC
  LIMIT 1;

  IF v_struct IS NULL THEN
    v_struct := public.upsert_salary_structure(
      v_emp,
      'hourly',
      10.0000,  -- USD/hour
      'USD',
      v_day - 30,
      NULL
    );
  END IF;

  IF v_struct IS NULL THEN
    RAISE EXCEPTION 'smoke fail: salary structure not created';
  END IF;

  -- Warehouse cannot create employees
  PERFORM public._test_set_auth_uid(v_wh);
  BEGIN
    PERFORM public.create_employee('P9-BAD', 'Nope');
    RAISE EXCEPTION 'smoke fail: warehouse create_employee should throw';
  EXCEPTION
    WHEN OTHERS THEN
      IF SQLERRM NOT LIKE '%admin or hr role required%' THEN
        RAISE;
      END IF;
  END;

  PERFORM public._test_set_auth_uid(v_admin);

  -- Idempotent: clear prior smoke attendance for the day
  DELETE FROM public.attendance_events
  WHERE employee_id = v_emp
    AND occurred_at >= v_day::timestamptz
    AND occurred_at < (v_day + 1)::timestamptz;

  -- Clock in/out: 8 hours on period day
  PERFORM public.clock_attendance(
    v_emp,
    'clock_in',
    (v_day + TIME '08:00')::timestamptz,
    'smoke in'
  );
  PERFORM public.clock_attendance(
    v_emp,
    'clock_out',
    (v_day + TIME '16:00')::timestamptz,
    'smoke out'
  );

  v_hours := public.attendance_hours_in_period(
    v_emp,
    v_day::timestamptz,
    (v_day + 1)::timestamptz
  );
  IF v_hours IS DISTINCT FROM 8 THEN
    RAISE EXCEPTION 'smoke fail: expected 8 hours, got %', v_hours;
  END IF;

  -- Non-HR non-self must not read another employee's hours
  PERFORM public._test_set_auth_uid(v_wh);
  BEGIN
    PERFORM public.attendance_hours_in_period(
      v_emp,
      v_day::timestamptz,
      (v_day + 1)::timestamptz
    );
    RAISE EXCEPTION 'smoke fail: warehouse attendance_hours_in_period for other emp should throw';
  EXCEPTION
    WHEN OTHERS THEN
      IF SQLERRM NOT LIKE '%admin/hr role or own employee record required%' THEN
        RAISE;
      END IF;
  END;

  -- Self may read own hours
  PERFORM public._test_set_auth_uid(v_emp_user);
  v_hours := public.attendance_hours_in_period(
    v_emp,
    v_day::timestamptz,
    (v_day + 1)::timestamptz
  );
  IF v_hours IS DISTINCT FROM 8 THEN
    RAISE EXCEPTION 'smoke fail: employee self hours expected 8, got %', v_hours;
  END IF;

  PERFORM public._test_set_auth_uid(v_admin);

  -- Payroll run for the day
  v_run := public.create_payroll_run(v_day, v_day, 'USD', 1, 'P9 smoke run');
  PERFORM public.compute_payroll_run(v_run, ARRAY[v_emp]);

  SELECT id, gross_amount, net_amount, hours_worked
  INTO v_line, v_gross, v_net, v_hours
  FROM public.payroll_lines
  WHERE payroll_run_id = v_run AND employee_id = v_emp;

  IF v_line IS NULL THEN
    RAISE EXCEPTION 'smoke fail: payroll line missing';
  END IF;
  IF v_hours IS DISTINCT FROM 8 OR v_gross IS DISTINCT FROM 80 THEN
    RAISE EXCEPTION 'smoke fail: expected 8h × 10 = 80 gross, got hours=% gross=%', v_hours, v_gross;
  END IF;
  IF v_net IS DISTINCT FROM 80 THEN
    RAISE EXCEPTION 'smoke fail: net before deductions should equal gross';
  END IF;

  -- Manual deduction only
  v_ded := public.add_payroll_deduction(v_line, 'Uniform advance', 15);

  SELECT deductions_amount, net_amount INTO v_ded_sum, v_net
  FROM public.payroll_lines WHERE id = v_line;

  IF v_ded_sum IS DISTINCT FROM 15 OR v_net IS DISTINCT FROM 65 THEN
    RAISE EXCEPTION 'smoke fail: net must be gross−manual (80−15=65), got ded=% net=%', v_ded_sum, v_net;
  END IF;

  -- Reject statutory-looking deduction labels
  BEGIN
    PERFORM public.add_payroll_deduction(v_line, 'PAYE July', 5);
    RAISE EXCEPTION 'smoke fail: PAYE label should be rejected';
  EXCEPTION
    WHEN OTHERS THEN
      IF SQLERRM NOT LIKE '%statutory/tax deduction labels%' THEN
        RAISE;
      END IF;
  END;

  BEGIN
    PERFORM public.add_payroll_deduction(v_line, 'NSSA contribution', 5);
    RAISE EXCEPTION 'smoke fail: NSSA label should be rejected';
  EXCEPTION
    WHEN OTHERS THEN
      IF SQLERRM NOT LIKE '%statutory/tax deduction labels%' THEN
        RAISE;
      END IF;
  END;

  BEGIN
    PERFORM public.add_payroll_deduction(v_line, 'tax withhold', 5);
    RAISE EXCEPTION 'smoke fail: bare tax label should be rejected';
  EXCEPTION
    WHEN OTHERS THEN
      IF SQLERRM NOT LIKE '%statutory/tax deduction labels%' THEN
        RAISE;
      END IF;
  END;

  -- Formula on run header
  IF NOT EXISTS (
    SELECT 1 FROM public.payroll_runs
    WHERE id = v_run
      AND total_gross = 80
      AND total_deductions = 15
      AND total_net = 65
      AND total_net = total_gross - total_deductions
  ) THEN
    RAISE EXCEPTION 'smoke fail: run totals formula mismatch';
  END IF;

  PERFORM public.submit_payroll_run(v_run);

  IF NOT EXISTS (
    SELECT 1 FROM public.payroll_runs WHERE id = v_run AND status = 'submitted'
  ) THEN
    RAISE EXCEPTION 'smoke fail: run not submitted';
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM public.domain_events
    WHERE event_code = 'payroll_run_ready'
      AND dedupe_key = format('payroll_run_ready:%s', v_run)
  ) THEN
    RAISE EXCEPTION 'smoke fail: payroll_run_ready domain event missing';
  END IF;

  -- Clear transaction-local RPC GUC so guards enforce (same as Phase 8 smokes)
  PERFORM set_config('app.payroll_rpc', '', true);

  -- Direct mutation of submitted run must fail
  BEGIN
    UPDATE public.payroll_runs SET notes = 'hack' WHERE id = v_run;
    RAISE EXCEPTION 'smoke fail: direct UPDATE of submitted run should throw';
  EXCEPTION
    WHEN OTHERS THEN
      IF SQLERRM NOT LIKE '%immutable%' AND SQLERRM NOT LIKE '%use payroll RPCs%' THEN
        RAISE;
      END IF;
  END;

  BEGIN
    UPDATE public.payroll_lines SET gross_amount = 999 WHERE id = v_line;
    RAISE EXCEPTION 'smoke fail: direct UPDATE of submitted line should throw';
  EXCEPTION
    WHEN OTHERS THEN
      IF SQLERRM NOT LIKE '%draft%' AND SQLERRM NOT LIKE '%use payroll RPCs%' THEN
        RAISE;
      END IF;
  END;

  BEGIN
    INSERT INTO public.payroll_deduction_lines (payroll_line_id, label, amount, currency)
    VALUES (v_line, 'sneaky', 1, 'USD');
    RAISE EXCEPTION 'smoke fail: direct INSERT deduction on submitted should throw';
  EXCEPTION
    WHEN OTHERS THEN
      IF SQLERRM NOT LIKE '%draft%' AND SQLERRM NOT LIKE '%use payroll RPCs%' THEN
        RAISE;
      END IF;
  END;

  -- Payslip metadata
  v_payslip := public.export_payslip(v_line);
  IF NOT EXISTS (
    SELECT 1 FROM public.payslips
    WHERE id = v_payslip
      AND payroll_line_id = v_line
      AND storage_bucket = 'payslips'
      AND storage_path = format('%s/%s.pdf', v_run, v_emp)
  ) THEN
    RAISE EXCEPTION 'smoke fail: payslip metadata missing';
  END IF;

  -- Cancel = void status flip (lines unchanged)
  PERFORM public.cancel_payroll_run(v_run, 'smoke void');
  IF NOT EXISTS (
    SELECT 1 FROM public.payroll_runs
    WHERE id = v_run AND status = 'cancelled' AND cancel_reason = 'smoke void'
  ) THEN
    RAISE EXCEPTION 'smoke fail: cancel did not void run';
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM public.payroll_lines
    WHERE id = v_line AND gross_amount = 80 AND net_amount = 65
  ) THEN
    RAISE EXCEPTION 'smoke fail: cancel must not edit posted money lines';
  END IF;

  -- Optional staff_no_show emit
  v_evt := public.emit_staff_no_show(v_emp, 'smoke-window');
  IF v_evt IS NULL OR NOT EXISTS (
    SELECT 1 FROM public.domain_events WHERE id = v_evt AND event_code = 'staff_no_show'
  ) THEN
    RAISE EXCEPTION 'smoke fail: staff_no_show event missing';
  END IF;

  -- Self-read: employee can see own line
  PERFORM public._test_set_auth_uid(v_emp_user);
  IF NOT EXISTS (
    SELECT 1 FROM public.payroll_lines WHERE id = v_line AND employee_id = v_emp
  ) THEN
    RAISE EXCEPTION 'smoke fail: employee cannot read own payroll line';
  END IF;

  RAISE NOTICE 'phase9_hr_payroll_smoke PASS';
END;
$$;
