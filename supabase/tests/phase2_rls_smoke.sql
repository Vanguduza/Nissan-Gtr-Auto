-- Phase 2 RLS / helper smoke (staff vs customer).
-- Run as postgres after seed, e.g.:
--   psql "$DATABASE_URL" -f supabase/tests/phase2_rls_smoke.sql

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
  PERFORM set_config('role', 'authenticated', true);
END;
$$;

DO $$
DECLARE
  v_admin UUID := 'a0000000-0000-4000-8000-000000000001';
  v_finance UUID := 'a0000000-0000-4000-8000-000000000002';
  v_wh UUID := 'a0000000-0000-4000-8000-000000000003';
  v_customer UUID := 'b0000000-0000-4000-8000-000000000099';
BEGIN
  -- Seed staff rows
  IF NOT EXISTS (SELECT 1 FROM public.profiles WHERE id = v_admin AND is_staff) THEN
    RAISE EXCEPTION 'smoke fail: admin is_staff expected true';
  END IF;
  IF NOT EXISTS (
    SELECT 1 FROM public.staff_roles WHERE user_id = v_admin AND role = 'admin'
  ) THEN
    RAISE EXCEPTION 'smoke fail: admin role missing';
  END IF;
  IF NOT EXISTS (
    SELECT 1 FROM public.staff_roles WHERE user_id = v_finance AND role = 'finance'
  ) THEN
    RAISE EXCEPTION 'smoke fail: finance role missing';
  END IF;
  IF NOT EXISTS (
    SELECT 1 FROM public.staff_roles WHERE user_id = v_wh AND role = 'warehouse'
  ) THEN
    RAISE EXCEPTION 'smoke fail: warehouse role missing';
  END IF;

  -- Synthetic customer profile (no staff_roles)
  INSERT INTO auth.users (
    instance_id, id, aud, role, email, encrypted_password,
    email_confirmed_at, raw_app_meta_data, raw_user_meta_data,
    created_at, updated_at, confirmation_token, recovery_token,
    email_change_token_new, email_change
  )
  VALUES (
    '00000000-0000-0000-0000-000000000000',
    v_customer,
    'authenticated',
    'authenticated',
    'customer-smoke@gtr.local',
    crypt('local-dev-customer', gen_salt('bf')),
    now(),
    '{"provider":"email","providers":["email"]}'::jsonb,
    '{"full_name":"Smoke Customer"}'::jsonb,
    now(), now(), '', '', '', ''
  )
  ON CONFLICT (id) DO NOTHING;

  INSERT INTO public.profiles (id, full_name, is_staff)
  VALUES (v_customer, 'Smoke Customer', false)
  ON CONFLICT (id) DO NOTHING;

  -- As admin: helpers true
  PERFORM public._test_set_auth_uid(v_admin);
  IF NOT public.is_staff() THEN
    RAISE EXCEPTION 'smoke fail: is_staff() false for admin';
  END IF;
  IF NOT public.has_staff_role(ARRAY['admin']::public.staff_role[]) THEN
    RAISE EXCEPTION 'smoke fail: has_staff_role(admin) false for admin';
  END IF;

  -- As customer: helpers false
  PERFORM public._test_set_auth_uid(v_customer);
  IF public.is_staff() THEN
    RAISE EXCEPTION 'smoke fail: is_staff() true for customer';
  END IF;
  IF public.has_staff_role(ARRAY['admin']::public.staff_role[]) THEN
    RAISE EXCEPTION 'smoke fail: customer has admin role';
  END IF;

  -- Customer cannot escalate via GUC + UPDATE
  BEGIN
    PERFORM set_config('gtr.syncing_is_staff', 'on', true);
    UPDATE public.profiles SET is_staff = true WHERE id = v_customer;
    RAISE EXCEPTION 'smoke fail: customer UPDATED is_staff (should have been blocked)';
  EXCEPTION
    WHEN OTHERS THEN
      IF SQLERRM LIKE 'smoke fail:%' THEN
        RAISE;
      END IF;
      -- expected: privilege / trigger / policy error
      NULL;
  END;

  IF EXISTS (SELECT 1 FROM public.profiles WHERE id = v_customer AND is_staff) THEN
    RAISE EXCEPTION 'smoke fail: customer is_staff became true';
  END IF;

  -- Customer cannot assign_staff_role
  BEGIN
    PERFORM public.assign_staff_role(v_customer, 'sales');
    RAISE EXCEPTION 'smoke fail: customer assigned staff role';
  EXCEPTION
    WHEN OTHERS THEN
      IF SQLERRM LIKE 'smoke fail:%' THEN
        RAISE;
      END IF;
      NULL;
  END;

  RAISE NOTICE 'phase2_rls_smoke: PASS (staff vs customer helpers + anti-escalation)';
END;
$$;

DROP FUNCTION IF EXISTS public._test_set_auth_uid(UUID);
