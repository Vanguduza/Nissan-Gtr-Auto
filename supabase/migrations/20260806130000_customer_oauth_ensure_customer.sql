-- Customer OAuth (Google / Apple): first Auth login must yield a storefront
-- customers row (AuthZ via _current_customer_id). Profiles stay on handle_new_user.
-- Signup gate: global enable_signup may be true so OAuth can mint users; public
-- email/password GoTrue signup is blocked by hook_before_user_created unless
-- Edge Admin createUser sets app_metadata.gtr_provisioned_via.
-- No ZIMRA / payroll tax. No staff OAuth requirement.

-- ---------------------------------------------------------------------------
-- before-user-created: allow Google/Apple + Edge-provisioned email only
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.hook_before_user_created(event jsonb)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_provider TEXT;
  v_via TEXT;
BEGIN
  v_provider := lower(coalesce(event->'user'->'app_metadata'->>'provider', ''));
  v_via := coalesce(event->'user'->'app_metadata'->>'gtr_provisioned_via', '');

  IF v_provider IN ('google', 'apple') THEN
    RETURN '{}'::jsonb;
  END IF;

  -- auth-otp complete_signup / hr-onboarding-create-auth set this via Admin API
  IF v_via IN ('auth_otp', 'hr_onboarding') THEN
    RETURN '{}'::jsonb;
  END IF;

  RETURN jsonb_build_object(
    'error',
    jsonb_build_object(
      'message',
      'Public email signup is disabled. Use OTP signup or Google/Apple sign-in.',
      'http_code',
      403
    )
  );
END;
$$;

COMMENT ON FUNCTION public.hook_before_user_created(jsonb) IS
  'Auth before-user-created hook: allow Google/Apple OAuth and Edge-provisioned '
  'email (gtr_provisioned_via=auth_otp|hr_onboarding); reject public GoTrue email signup.';

REVOKE ALL ON FUNCTION public.hook_before_user_created(jsonb) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.hook_before_user_created(jsonb) FROM anon, authenticated;
GRANT EXECUTE ON FUNCTION public.hook_before_user_created(jsonb) TO supabase_auth_admin;

-- ---------------------------------------------------------------------------
-- handle_new_user: profiles + storefront customers for OAuth / OTP signup
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.handle_new_user()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_provider TEXT;
  v_via TEXT;
  v_name TEXT;
  v_retail UUID;
BEGIN
  INSERT INTO public.profiles (id, full_name, is_staff)
  VALUES (
    NEW.id,
    COALESCE(
      NEW.raw_user_meta_data ->> 'full_name',
      NEW.raw_user_meta_data ->> 'name'
    ),
    false
  )
  ON CONFLICT (id) DO NOTHING;

  v_provider := lower(coalesce(NEW.raw_app_meta_data ->> 'provider', ''));
  v_via := coalesce(NEW.raw_app_meta_data ->> 'gtr_provisioned_via', '');

  -- Storefront accounts only (not HR staff Admin creates)
  IF v_provider IN ('google', 'apple') OR v_via = 'auth_otp' THEN
    IF NOT EXISTS (
      SELECT 1 FROM public.customers c WHERE c.profile_id = NEW.id
    ) THEN
      SELECT pl.id INTO v_retail
      FROM public.price_lists pl
      WHERE pl.code = 'RETAIL' AND pl.is_active
      LIMIT 1;

      v_name := nullif(
        btrim(
          COALESCE(
            NEW.raw_user_meta_data ->> 'full_name',
            NEW.raw_user_meta_data ->> 'name',
            split_part(COALESCE(NEW.email, ''), '@', 1)
          )
        ),
        ''
      );
      IF v_name IS NULL THEN
        v_name := 'Customer';
      END IF;

      INSERT INTO public.customers (
        display_name,
        email,
        currency,
        profile_id,
        price_list_id
      )
      VALUES (
        v_name,
        NULLIF(NEW.email, ''),
        'USD',
        NEW.id,
        v_retail
      );
    END IF;
  END IF;

  RETURN NEW;
END;
$$;

COMMENT ON FUNCTION public.handle_new_user() IS
  'After auth.users insert: ensure profiles row; for Google/Apple or OTP signup '
  'also insert a retail customers row linked by profile_id (idempotent).';

-- ---------------------------------------------------------------------------
-- ensure_own_customer: backfill / defense-in-depth after OAuth session
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.ensure_own_customer()
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_uid UUID := auth.uid();
  v_cust UUID;
  v_name TEXT;
  v_email TEXT;
  v_retail UUID;
BEGIN
  IF v_uid IS NULL THEN
    RAISE EXCEPTION 'not authenticated';
  END IF;

  -- Staff use staff_roles AuthZ; do not auto-mint storefront customers for them
  IF public.is_staff() THEN
    RAISE EXCEPTION 'staff accounts do not use storefront customer provisioning';
  END IF;

  SELECT c.id INTO v_cust
  FROM public.customers c
  WHERE c.profile_id = v_uid
  ORDER BY c.created_at ASC
  LIMIT 1;

  IF v_cust IS NOT NULL THEN
    RETURN v_cust;
  END IF;

  INSERT INTO public.profiles (id, full_name, is_staff)
  VALUES (v_uid, NULL, false)
  ON CONFLICT (id) DO NOTHING;

  SELECT
    nullif(
      btrim(
        COALESCE(
          p.full_name,
          split_part(COALESCE(auth.jwt() ->> 'email', ''), '@', 1)
        )
      ),
      ''
    ),
    nullif(auth.jwt() ->> 'email', '')
  INTO v_name, v_email
  FROM public.profiles p
  WHERE p.id = v_uid;

  IF v_name IS NULL THEN
    v_name := 'Customer';
  END IF;

  SELECT pl.id INTO v_retail
  FROM public.price_lists pl
  WHERE pl.code = 'RETAIL' AND pl.is_active
  LIMIT 1;

  INSERT INTO public.customers (
    display_name,
    email,
    currency,
    profile_id,
    price_list_id
  )
  VALUES (v_name, v_email, 'USD', v_uid, v_retail)
  RETURNING id INTO v_cust;

  RETURN v_cust;
END;
$$;

COMMENT ON FUNCTION public.ensure_own_customer() IS
  'Idempotent: return customers.id for auth.uid(), inserting a retail row if missing. '
  'Call after OAuth/session establish when _current_customer_id() is null. Staff denied.';

REVOKE ALL ON FUNCTION public.ensure_own_customer() FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.ensure_own_customer() TO authenticated, service_role;
