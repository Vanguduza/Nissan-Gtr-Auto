-- OAuth / storefront customer ensure smoke (postgres via docker exec).
-- Simulates Google first-login, Admin-shaped OTP create (meta after insert),
-- ensure_own_customer backfill, and staff deny. No ZIMRA.

CREATE OR REPLACE FUNCTION public._test_set_auth_uid(p_uid UUID)
RETURNS void
LANGUAGE plpgsql
AS $$
BEGIN
  PERFORM set_config('request.jwt.claim.sub', p_uid::text, true);
  PERFORM set_config(
    'request.jwt.claims',
    json_build_object(
      'sub', p_uid::text,
      'role', 'authenticated',
      'email', 'oauth-smoke@gtr.local'
    )::text,
    true
  );
  PERFORM set_config('request.jwt.claim.role', 'authenticated', true);
END;
$$;

CREATE OR REPLACE FUNCTION public._test_set_service_role()
RETURNS void
LANGUAGE plpgsql
AS $$
BEGIN
  PERFORM set_config('request.jwt.claim.role', 'service_role', true);
  PERFORM set_config(
    'request.jwt.claims',
    json_build_object('role', 'service_role')::text,
    true
  );
END;
$$;

DO $$
DECLARE
  v_oauth UUID := 'c0000000-0000-4000-8000-0000000000a3';
  v_backfill UUID := 'c0000000-0000-4000-8000-0000000000a4';
  v_admin_otp UUID := 'c0000000-0000-4000-8000-0000000000a5';
  v_staff UUID := 'c0000000-0000-4000-8000-0000000000a6';
  v_cust UUID;
  v_cust2 UUID;
  v_cust3 UUID;
  v_rpc UUID;
  v_denied BOOLEAN := false;
BEGIN
  -- Reset prior smoke fixtures (idempotent re-runs)
  DELETE FROM public.customers
  WHERE profile_id IN (v_oauth, v_backfill, v_admin_otp, v_staff);
  DELETE FROM public.employees WHERE id = 'e0000000-0000-4000-8000-0000000000a6';
  DELETE FROM public.staff_roles WHERE user_id = v_staff;
  DELETE FROM auth.users WHERE id IN (v_oauth, v_backfill, v_admin_otp, v_staff);
  DELETE FROM public.profiles WHERE id IN (v_oauth, v_backfill, v_admin_otp, v_staff);

  -- First-time Google-style Auth user (provider=google)
  INSERT INTO auth.users (
    instance_id, id, aud, role, email, encrypted_password, email_confirmed_at,
    raw_app_meta_data, raw_user_meta_data, created_at, updated_at,
    confirmation_token, recovery_token, email_change_token_new, email_change
  )
  VALUES (
    '00000000-0000-0000-0000-000000000000', v_oauth,
    'authenticated', 'authenticated', 'oauth-google@gtr.local',
    crypt('oauth-smoke-unused', gen_salt('bf')), now(),
    '{"provider":"google","providers":["google"]}'::jsonb,
    '{"full_name":"OAuth Google Smoke","name":"OAuth Google Smoke"}'::jsonb,
    now(), now(), '', '', '', ''
  )
  ON CONFLICT (id) DO NOTHING;

  SELECT c.id INTO v_cust
  FROM public.customers c
  WHERE c.profile_id = v_oauth
  LIMIT 1;

  IF v_cust IS NULL THEN
    RAISE EXCEPTION 'smoke fail: Google OAuth user missing customers row';
  END IF;

  PERFORM public._test_set_auth_uid(v_oauth);
  IF public._current_customer_id() IS DISTINCT FROM v_cust THEN
    RAISE EXCEPTION 'smoke fail: _current_customer_id after OAuth != customers.id';
  END IF;

  -- ensure_own_customer is idempotent
  v_rpc := public.ensure_own_customer();
  IF v_rpc IS DISTINCT FROM v_cust THEN
    RAISE EXCEPTION 'smoke fail: ensure_own_customer not idempotent';
  END IF;

  -- Backfill path: email user without customers (e.g. legacy) then ensure
  INSERT INTO auth.users (
    instance_id, id, aud, role, email, encrypted_password, email_confirmed_at,
    raw_app_meta_data, raw_user_meta_data, created_at, updated_at,
    confirmation_token, recovery_token, email_change_token_new, email_change
  )
  VALUES (
    '00000000-0000-0000-0000-000000000000', v_backfill,
    'authenticated', 'authenticated', 'oauth-backfill@gtr.local',
    crypt('oauth-smoke-unused', gen_salt('bf')), now(),
    '{"provider":"email","providers":["email"]}'::jsonb,
    '{"full_name":"Backfill Smoke"}'::jsonb,
    now(), now(), '', '', '', ''
  )
  ON CONFLICT (id) DO NOTHING;

  -- email without gtr_provisioned_via must NOT auto-create customers
  IF EXISTS (SELECT 1 FROM public.customers WHERE profile_id = v_backfill) THEN
    RAISE EXCEPTION 'smoke fail: plain email signup must not auto-create customers';
  END IF;

  PERFORM public._test_set_auth_uid(v_backfill);
  v_cust2 := public.ensure_own_customer();
  IF v_cust2 IS NULL THEN
    RAISE EXCEPTION 'smoke fail: ensure_own_customer backfill returned null';
  END IF;
  IF public._current_customer_id() IS DISTINCT FROM v_cust2 THEN
    RAISE EXCEPTION 'smoke fail: _current_customer_id after backfill mismatch';
  END IF;

  -- Admin-shaped OTP create: insert WITHOUT custom meta (trigger miss), then
  -- update meta + ensure_customer_for_user (mirrors real auth-otp path).
  INSERT INTO auth.users (
    instance_id, id, aud, role, email, encrypted_password, email_confirmed_at,
    raw_app_meta_data, raw_user_meta_data, created_at, updated_at,
    confirmation_token, recovery_token, email_change_token_new, email_change
  )
  VALUES (
    '00000000-0000-0000-0000-000000000000', v_admin_otp,
    'authenticated', 'authenticated', 'oauth-admin-otp@gtr.local',
    crypt('oauth-smoke-unused', gen_salt('bf')), now(),
    '{"provider":"email","providers":["email"]}'::jsonb,
    '{"full_name":"Admin OTP Smoke"}'::jsonb,
    now(), now(), '', '', '', ''
  )
  ON CONFLICT (id) DO NOTHING;

  IF EXISTS (SELECT 1 FROM public.customers WHERE profile_id = v_admin_otp) THEN
    RAISE EXCEPTION 'smoke fail: Admin-shaped insert must not auto-mint customers';
  END IF;

  UPDATE auth.users
  SET raw_app_meta_data =
    coalesce(raw_app_meta_data, '{}'::jsonb) ||
    '{"gtr_provisioned_via":"auth_otp"}'::jsonb
  WHERE id = v_admin_otp;

  PERFORM public._test_set_service_role();
  v_cust3 := public.ensure_customer_for_user(v_admin_otp);
  IF v_cust3 IS NULL THEN
    RAISE EXCEPTION 'smoke fail: ensure_customer_for_user returned null';
  END IF;
  IF NOT EXISTS (
    SELECT 1 FROM public.customers WHERE id = v_cust3 AND profile_id = v_admin_otp
  ) THEN
    RAISE EXCEPTION 'smoke fail: ensure_customer_for_user row missing';
  END IF;
  -- Idempotent
  IF public.ensure_customer_for_user(v_admin_otp) IS DISTINCT FROM v_cust3 THEN
    RAISE EXCEPTION 'smoke fail: ensure_customer_for_user not idempotent';
  END IF;

  -- Staff deny: employee-linked profile must not mint storefront customer
  INSERT INTO auth.users (
    instance_id, id, aud, role, email, encrypted_password, email_confirmed_at,
    raw_app_meta_data, raw_user_meta_data, created_at, updated_at,
    confirmation_token, recovery_token, email_change_token_new, email_change
  )
  VALUES (
    '00000000-0000-0000-0000-000000000000', v_staff,
    'authenticated', 'authenticated', 'oauth-staff-deny@gtr.local',
    crypt('oauth-smoke-unused', gen_salt('bf')), now(),
    '{"provider":"email","providers":["email"],"gtr_provisioned_via":"hr_onboarding"}'::jsonb,
    '{"full_name":"Staff Deny Smoke"}'::jsonb,
    now(), now(), '', '', '', ''
  )
  ON CONFLICT (id) DO NOTHING;

  INSERT INTO public.employees (
    id, user_id, employee_code, full_name, email, status
  )
  VALUES (
    'e0000000-0000-4000-8000-0000000000a6',
    v_staff,
    'SMOKE-STAFF-A6',
    'Staff Deny Smoke',
    'oauth-staff-deny@gtr.local',
    'active'
  )
  ON CONFLICT (id) DO NOTHING;

  PERFORM public._test_set_auth_uid(v_staff);
  BEGIN
    PERFORM public.ensure_own_customer();
  EXCEPTION
    WHEN OTHERS THEN
      IF SQLERRM LIKE '%staff accounts do not use storefront%' THEN
        v_denied := true;
      ELSE
        RAISE;
      END IF;
  END;
  IF NOT v_denied THEN
    RAISE EXCEPTION 'smoke fail: ensure_own_customer must deny employees / HR';
  END IF;

  RAISE NOTICE 'customer_oauth_ensure_smoke OK';
END;
$$;
