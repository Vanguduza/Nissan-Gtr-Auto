-- Google-only customer OAuth: drop Apple from Auth before-user-created + handle_new_user.
-- Apple Sign In removed from product surfaces; keep OTP + HR provisioning paths.
-- No ZIMRA / payroll tax.

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

  IF v_provider = 'google' THEN
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
      'Public email signup is disabled. Use OTP signup or Google sign-in.',
      'http_code',
      403
    )
  );
END;
$$;

COMMENT ON FUNCTION public.hook_before_user_created(jsonb) IS
  'Auth before-user-created hook: allow Google OAuth and Edge-provisioned '
  'email (gtr_provisioned_via=auth_otp|hr_onboarding); reject public GoTrue email signup.';

CREATE OR REPLACE FUNCTION public.handle_new_user()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_provider TEXT;
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

  -- Google only. OTP Admin createUser applies custom app_metadata after
  -- AFTER INSERT, so auth_otp must mint via ensure_customer_for_user (Edge).
  IF v_provider = 'google' THEN
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
      )
      ON CONFLICT (profile_id) WHERE (profile_id IS NOT NULL) DO NOTHING;
    END IF;
  END IF;

  RETURN NEW;
END;
$$;

COMMENT ON FUNCTION public.handle_new_user() IS
  'After auth.users insert: ensure profiles row; for Google OAuth also insert '
  'a retail customers row. OTP signup mints customers via ensure_customer_for_user.';
