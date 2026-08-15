-- Smoke: HR onboarding resolves staff_role and assigns on auth link.
-- Never grants admin via title heuristic. No ZIMRA / payroll-tax.

BEGIN;

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
END;
$$;

DO $$
DECLARE
  v_admin UUID := 'a0000000-0000-4000-8000-000000000001';
  v_hr UUID := 'a0000000-0000-4000-8000-0000000000h1';
  v_driver_user UUID := 'd0000000-0000-4000-8000-0000000000o1';
  v_grade UUID;
  v_hr_role UUID;
  v_draft UUID;
  v_emp UUID;
  v_role public.staff_role;
  v_complete JSONB;
BEGIN
  -- Ensure admin + HR callers exist
  INSERT INTO auth.users (
    instance_id, id, aud, role, email, encrypted_password,
    email_confirmed_at, raw_app_meta_data, raw_user_meta_data,
    created_at, updated_at
  ) VALUES
    (
      '00000000-0000-0000-0000-000000000000', v_admin, 'authenticated', 'authenticated',
      'admin-onboard@gtr.local', crypt('local-dev', gen_salt('bf')),
      now(), '{"provider":"email","providers":["email"]}'::jsonb, '{}'::jsonb, now(), now()
    ),
    (
      '00000000-0000-0000-0000-000000000000', v_hr, 'authenticated', 'authenticated',
      'hr-onboard@gtr.local', crypt('local-dev', gen_salt('bf')),
      now(), '{"provider":"email","providers":["email"]}'::jsonb, '{}'::jsonb, now(), now()
    ),
    (
      '00000000-0000-0000-0000-000000000000', v_driver_user, 'authenticated', 'authenticated',
      'driver-onboard@gtr.local', crypt('local-dev', gen_salt('bf')),
      now(), '{"provider":"email","providers":["email"]}'::jsonb, '{}'::jsonb, now(), now()
    )
  ON CONFLICT (id) DO NOTHING;

  INSERT INTO public.profiles (id, full_name, is_staff)
  VALUES
    (v_admin, 'Onboard Admin', true),
    (v_hr, 'Onboard HR', true),
    (v_driver_user, 'Pending Driver', false)
  ON CONFLICT (id) DO UPDATE SET full_name = EXCLUDED.full_name;

  INSERT INTO public.staff_roles (user_id, role) VALUES
    (v_admin, 'admin'),
    (v_hr, 'hr')
  ON CONFLICT DO NOTHING;

  PERFORM public._test_set_auth_uid(v_admin);

  v_grade := public.create_hr_grade('D1', 'Delivery grade', 50);
  v_hr_role := public.create_hr_role(
    'Delivery Driver',
    v_grade,
    NULL,
    'Fleet',
    NULL,
    NULL,
    'monthly',
    '["delivery"]'::jsonb,
    '{}'::jsonb,
    '{}',
    'driver'::public.staff_role
  );

  -- Explicit staff_role on draft wins
  PERFORM public._test_set_auth_uid(v_hr);
  v_draft := public.save_hr_onboarding_stage(
    NULL,
    'personal',
    jsonb_build_object(
      'full_name', 'Tariro Driver',
      'email', 'tariro.driver@gtr.local',
      'phone_e164', '+263771110001',
      'grade_id', v_grade,
      'hr_role_id', v_hr_role,
      'staff_role', 'driver'
    ),
    jsonb_build_object('bank_name', 'CBZ', 'account_number', '123'),
    jsonb_build_object('medical_aid', 'none'),
    NULL
  );

  v_complete := public.complete_hr_onboarding(v_draft);
  IF (v_complete->>'staff_role') IS DISTINCT FROM 'driver' THEN
    RAISE EXCEPTION 'smoke fail: expected staff_role driver, got %', v_complete->>'staff_role';
  END IF;

  v_emp := (v_complete->>'employee_id')::uuid;

  -- Simulate Edge link (service_role)
  PERFORM set_config('request.jwt.claim.role', 'service_role', true);
  PERFORM set_config('role', 'service_role', true);

  PERFORM public.link_employee_auth_user(
    v_emp,
    v_driver_user,
    '+263771110001',
    'Tariro Driver'
  );

  IF NOT EXISTS (
    SELECT 1 FROM public.staff_roles
    WHERE user_id = v_driver_user AND role = 'driver'
  ) THEN
    RAISE EXCEPTION 'smoke fail: driver staff_role not assigned on link';
  END IF;

  -- Heuristic: delivery title → driver without explicit staff_role
  v_role := public.resolve_hr_onboarding_staff_role(
    jsonb_build_object('hr_role_id', v_hr_role),
    v_hr_role
  );
  IF v_role IS DISTINCT FROM 'driver'::public.staff_role THEN
    RAISE EXCEPTION 'smoke fail: organogram default/heuristic expected driver, got %', v_role;
  END IF;

  -- Heuristic never invents admin
  v_role := public.resolve_hr_onboarding_staff_role(
    jsonb_build_object('role_title', 'Chief Admin Officer'),
    NULL
  );
  IF v_role = 'admin'::public.staff_role THEN
    RAISE EXCEPTION 'smoke fail: heuristic must not grant admin';
  END IF;

  RAISE NOTICE 'hr_onboarding_staff_role_smoke OK';
END;
$$;

ROLLBACK;
