-- Staff My Account smoke (postgres).
-- Asserts: address/photo columns, get/update_my_staff_profile self-only,
-- list_my_payslip_history includes unfunded submitted lines.
-- Run after the November staff reconciliation forward migrations.

BEGIN;
SELECT set_config('request.jwt.claim.role','service_role',true);
SELECT set_config('request.jwt.claims','{"role":"service_role"}',true);

CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE OR REPLACE FUNCTION public._test_set_auth_uid(p_uid UUID)
RETURNS VOID
LANGUAGE plpgsql
AS $$
BEGIN
  PERFORM set_config(
    'request.jwt.claim.sub',
    COALESCE(p_uid::text, ''),
    true
  );
  PERFORM set_config('request.jwt.claim.role', 'authenticated', true);
  PERFORM set_config('request.jwt.claims',json_build_object('sub',p_uid::text,'role','authenticated')::text,true);
END;
$$;

DO $$
DECLARE
  v_admin UUID := 'a0000000-0000-4000-8000-0000000000a1';
  v_emp_user UUID := 'c0000000-0000-4000-8000-0000000000a1';
  v_other UUID := 'c0000000-0000-4000-8000-0000000000a2';
  v_emp UUID;
  v_other_emp UUID;
  v_grade UUID;
  v_role UUID;
  v_struct UUID;
  v_run UUID;
  v_line UUID;
  v_prof JSONB;
  v_hist JSONB;
  v_addr_cols INT;
  v_denied BOOLEAN;
BEGIN
  SELECT COUNT(*) INTO v_addr_cols
  FROM information_schema.columns
  WHERE table_schema = 'public'
    AND table_name = 'employees'
    AND column_name IN ('address', 'photo_storage_path');
  IF v_addr_cols < 2 THEN
    RAISE EXCEPTION 'employees.address / photo_storage_path missing';
  END IF;

  INSERT INTO auth.users (
    instance_id, id, aud, role, email, encrypted_password,
    email_confirmed_at, raw_app_meta_data, raw_user_meta_data,
    created_at, updated_at
  )
  VALUES
    (
      '00000000-0000-0000-0000-000000000000', v_admin, 'authenticated', 'authenticated',
      'staff-acct-admin@gtr.local', extensions.crypt('local-dev', extensions.gen_salt('bf')),
      now(), '{"provider":"email","providers":["email"]}'::jsonb, '{}'::jsonb, now(), now()
    ),
    (
      '00000000-0000-0000-0000-000000000000', v_emp_user, 'authenticated', 'authenticated',
      'staff-acct-emp@gtr.local', extensions.crypt('local-dev', extensions.gen_salt('bf')),
      now(), '{"provider":"email","providers":["email"]}'::jsonb, '{}'::jsonb, now(), now()
    ),
    (
      '00000000-0000-0000-0000-000000000000', v_other, 'authenticated', 'authenticated',
      'staff-acct-other@gtr.local', extensions.crypt('local-dev', extensions.gen_salt('bf')),
      now(), '{"provider":"email","providers":["email"]}'::jsonb, '{}'::jsonb, now(), now()
    )
  ON CONFLICT (id) DO UPDATE
    SET email=EXCLUDED.email, email_confirmed_at=EXCLUDED.email_confirmed_at;

  INSERT INTO public.profiles (id, full_name, is_staff)
  VALUES
    (v_admin, 'Acct Admin', true),
    (v_emp_user, 'Acct Emp', true),
    (v_other, 'Acct Other', true)
  ON CONFLICT (id) DO UPDATE SET is_staff = true;

  INSERT INTO public.staff_roles (user_id, role)
  VALUES (v_admin, 'admin'), (v_emp_user, 'sales'), (v_other, 'warehouse')
  ON CONFLICT DO NOTHING;

  SELECT id INTO v_grade FROM public.hr_grades WHERE code = 'C1' LIMIT 1;
  IF v_grade IS NULL THEN
    INSERT INTO public.hr_grades (code, title, sort_order)
    VALUES ('C1', 'Shop attendant', 40)
    RETURNING id INTO v_grade;
  END IF;

  SELECT id INTO v_role FROM public.hr_roles WHERE title = 'Smoke sales acct' LIMIT 1;
  IF v_role IS NULL THEN
    INSERT INTO public.hr_roles (title, grade_id, module_access, is_active)
    VALUES ('Smoke sales acct', v_grade, '["crm","analytics"]'::jsonb, true)
    RETURNING id INTO v_role;
  END IF;

  SELECT id INTO v_emp FROM public.employees WHERE employee_code = 'GTRC1999';
  IF v_emp IS NULL THEN
    INSERT INTO public.employees (
      user_id, employee_code, full_name, email, phone_e164,
      address, grade_id, hr_role_id, status
    )
    VALUES (
      v_emp_user,
      'GTRC1999',
      'Acct Emp',
      'staff-acct-emp@gtr.local',
      '+263771000031',
      '1 Test Road',
      v_grade,
      v_role,
      'active'
    )
    RETURNING id INTO v_emp;
  ELSE
    UPDATE public.employees
    SET user_id = v_emp_user,
        address = '1 Test Road',
        grade_id = v_grade,
        hr_role_id = v_role,
        phone_e164 = '+263771000031',
        updated_at = now()
    WHERE id = v_emp;
  END IF;

  SELECT id INTO v_other_emp FROM public.employees WHERE employee_code = 'GTRC1998';
  IF v_other_emp IS NULL THEN
    INSERT INTO public.employees (
      user_id, employee_code, full_name, email, status
    )
    VALUES (v_other, 'GTRC1998', 'Acct Other', 'staff-acct-other@gtr.local', 'active')
    RETURNING id INTO v_other_emp;
  ELSE
    UPDATE public.employees
    SET user_id = v_other, updated_at = now()
    WHERE id = v_other_emp;
  END IF;

  PERFORM public._test_set_auth_uid(v_emp_user);
  v_prof := public.get_my_staff_profile();
  IF COALESCE((v_prof->>'has_employee')::boolean, false) IS NOT TRUE THEN
    RAISE EXCEPTION 'get_my_staff_profile missing employee';
  END IF;
  IF v_prof->>'employee_code' IS DISTINCT FROM 'GTRC1999' THEN
    RAISE EXCEPTION 'unexpected emp code: %', v_prof->>'employee_code';
  END IF;
  IF v_prof->>'address' IS DISTINCT FROM '1 Test Road' THEN
    RAISE EXCEPTION 'address not returned';
  END IF;

  v_prof := public.update_my_staff_profile(
    '+263771000099',
    '99 Updated Ave',
    'staff-acct-emp@gtr.local'
  );
  IF v_prof->>'phone_e164' IS DISTINCT FROM '+263771000099' THEN
    RAISE EXCEPTION 'phone not updated';
  END IF;
  IF v_prof->>'address' IS DISTINCT FROM '99 Updated Ave' THEN
    RAISE EXCEPTION 'address not updated';
  END IF;

  PERFORM public._test_set_auth_uid(v_other);
  v_prof := public.get_my_staff_profile();
  IF v_prof->>'employee_code' IS NOT DISTINCT FROM 'GTRC1999' THEN
    RAISE EXCEPTION 'other user saw wrong employee';
  END IF;

  PERFORM public._test_set_auth_uid(v_admin);
  PERFORM public._payroll_begin_rpc();

  INSERT INTO public.salary_structures (
    employee_id, pay_type, rate, currency, effective_from, is_active
  )
  VALUES (v_emp, 'salary', 1000, 'USD', DATE '2026-01-01', true)
  RETURNING id INTO v_struct;

  INSERT INTO public.payroll_runs (
    status, period_start, period_end, currency, exchange_rate,
    total_gross, total_deductions, total_net, created_by, submitted_at
  )
  VALUES (
    'submitted', DATE '2026-07-01', DATE '2026-07-31', 'USD', 1,
    1000, 0, 1000, v_admin, now()
  )
  RETURNING id INTO v_run;

  INSERT INTO public.payroll_lines (
    payroll_run_id, employee_id, line_no, hours_worked, pay_type,
    rate_applied, gross_amount, deductions_amount, net_amount,
    currency, exchange_rate, salary_structure_id
  )
  VALUES (
    v_run, v_emp, 1, 0, 'salary', 1000, 1000, 0, 1000, 'USD', 1, v_struct
  )
  RETURNING id INTO v_line;

  PERFORM public._test_set_auth_uid(v_emp_user);
  v_hist := public.list_my_payslip_history();
  IF jsonb_typeof(v_hist) IS DISTINCT FROM 'array' OR jsonb_array_length(v_hist) < 1 THEN
    RAISE EXCEPTION 'expected payslip history rows, got %', v_hist;
  END IF;
  IF NOT EXISTS (
    SELECT 1 FROM jsonb_array_elements(v_hist) x
    WHERE (x->>'payroll_line_id')::uuid = v_line
      AND COALESCE((x->>'funded')::boolean, true) = false
  ) THEN
    RAISE EXCEPTION 'unfunded submitted line missing from history';
  END IF;

  PERFORM public._test_set_auth_uid(v_other);
  v_hist := public.list_my_payslip_history();
  IF EXISTS (
    SELECT 1 FROM jsonb_array_elements(v_hist) x
    WHERE (x->>'payroll_line_id')::uuid = v_line
  ) THEN
    RAISE EXCEPTION 'other employee saw foreign payslip history';
  END IF;

  -- Upload must exist before a profile can reference its own protected photo.
  INSERT INTO storage.objects(bucket_id,name,metadata)
  VALUES('employee-photos',v_emp::text||'/portrait-smoke.jpg','{"mimetype":"image/jpeg","size":16}'::jsonb);
  PERFORM public._test_set_auth_uid(v_emp_user);
  v_prof:=public.update_my_staff_profile(NULL,NULL,NULL,v_emp::text||'/portrait-smoke.jpg');
  IF v_prof->>'photo_storage_path' IS DISTINCT FROM v_emp::text||'/portrait-smoke.jpg' THEN
    RAISE EXCEPTION 'own uploaded photo was not selected';
  END IF;
  v_denied:=false;
  BEGIN PERFORM public.update_my_staff_profile(NULL,NULL,NULL,v_other_emp::text||'/foreign.jpg');
  EXCEPTION WHEN OTHERS THEN v_denied:=true; END;
  IF NOT v_denied THEN RAISE EXCEPTION 'foreign photo path accepted'; END IF;
  v_denied:=false;
  BEGIN PERFORM public.update_my_staff_profile(NULL,NULL,'impersonation@gtr.local',NULL);
  EXCEPTION WHEN OTHERS THEN v_denied:=true; END;
  IF NOT v_denied THEN RAISE EXCEPTION 'unconfirmed authentication email accepted'; END IF;
  IF (SELECT email FROM public.employees WHERE id=v_emp) <> 'staff-acct-emp@gtr.local' THEN
    RAISE EXCEPTION 'rejected email changed employee identity';
  END IF;
  -- Simulate the native Auth confirmation, then synchronize only that confirmed email.
  UPDATE auth.users SET email='staff-acct-emp-confirmed@gtr.local',email_confirmed_at=now()
  WHERE id=v_emp_user;
  v_prof:=public.update_my_staff_profile(NULL,NULL,'staff-acct-emp-confirmed@gtr.local',NULL);
  IF v_prof->>'email' IS DISTINCT FROM 'staff-acct-emp-confirmed@gtr.local'
     OR (SELECT email FROM public.employees WHERE id=v_emp) IS DISTINCT FROM 'staff-acct-emp-confirmed@gtr.local' THEN
    RAISE EXCEPTION 'confirmed Auth email was not synchronized';
  END IF;
  v_prof:=public.staff_identity_card_payload('GTRC1999');
  IF v_prof->>'fullName'<>'Acct Emp' OR v_prof->>'employeeCode'<>'GTRC1999' THEN
    RAISE EXCEPTION 'own card payload is not canonical identity';
  END IF;
  v_denied:=false;
  BEGIN PERFORM public.staff_identity_card_payload('GTRC1998');
  EXCEPTION WHEN OTHERS THEN v_denied:=true; END;
  IF NOT v_denied THEN RAISE EXCEPTION 'non-HR staff can issue another employee card'; END IF;
  PERFORM public._test_set_auth_uid(NULL);
  v_denied:=false;
  BEGIN PERFORM public.verify_staff_identity(v_emp);
  EXCEPTION WHEN OTHERS THEN v_denied:=true; END;
  IF NOT v_denied THEN RAISE EXCEPTION 'unauthenticated staff identity verification accepted'; END IF;
  RAISE NOTICE 'staff self-service reconciliation smoke passed';
END;
$$;
ROLLBACK;
