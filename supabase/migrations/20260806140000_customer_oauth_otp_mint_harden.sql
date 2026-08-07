-- Harden customer OAuth / OTP provisioning (security review follow-up).
-- 1) service_role-only ensure_customer_for_user — auth-otp mints customers after Admin
--    createUser (AFTER INSERT handle_new_user never sees gtr_provisioned_via in time).
-- 2) Broader staff deny on ensure_own_customer (employees / staff_roles / hr_onboarding).
-- 3) Partial UNIQUE on customers.profile_id + ON CONFLICT re-select.
-- No ZIMRA / payroll tax. Do not reopen public email signup.

-- ---------------------------------------------------------------------------
-- Deduplicate profile_id before unique index (keep earliest; unlink extras)
-- ---------------------------------------------------------------------------
WITH ranked AS (
  SELECT
    id,
    ROW_NUMBER() OVER (
      PARTITION BY profile_id
      ORDER BY created_at ASC, id ASC
    ) AS rn
  FROM public.customers
  WHERE profile_id IS NOT NULL
)
UPDATE public.customers c
SET profile_id = NULL,
    updated_at = now()
FROM ranked r
WHERE c.id = r.id
  AND r.rn > 1;

CREATE UNIQUE INDEX IF NOT EXISTS customers_profile_id_uidx
  ON public.customers (profile_id)
  WHERE profile_id IS NOT NULL;

COMMENT ON INDEX public.customers_profile_id_uidx IS
  'At most one storefront customers row per auth profile (NULL profile_id allowed for POS walk-ins).';

-- ---------------------------------------------------------------------------
-- Shared staff / HR deny helper
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public._storefront_customer_provision_denied(p_uid UUID)
RETURNS BOOLEAN
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
  SELECT p_uid IS NOT NULL AND (
    EXISTS (
      SELECT 1 FROM public.profiles pr
      WHERE pr.id = p_uid AND pr.is_staff IS TRUE
    )
    OR EXISTS (
      SELECT 1 FROM public.staff_roles sr WHERE sr.user_id = p_uid
    )
    OR EXISTS (
      SELECT 1 FROM public.employees e WHERE e.user_id = p_uid
    )
    OR EXISTS (
      SELECT 1
      FROM auth.users u
      WHERE u.id = p_uid
        AND coalesce(u.raw_app_meta_data ->> 'gtr_provisioned_via', '') =
          'hr_onboarding'
    )
  );
$$;

COMMENT ON FUNCTION public._storefront_customer_provision_denied(UUID) IS
  'True when uid must not receive a retail customers row (staff_roles, employees, '
  'profiles.is_staff, or HR onboarding Admin create).';

REVOKE ALL ON FUNCTION public._storefront_customer_provision_denied(UUID) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public._storefront_customer_provision_denied(UUID)
  TO authenticated, service_role;

-- ---------------------------------------------------------------------------
-- handle_new_user: OAuth (google/apple) only for customers — not OTP Admin creates
-- ---------------------------------------------------------------------------
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

  -- Google/Apple only. OTP Admin createUser applies custom app_metadata after
  -- AFTER INSERT, so auth_otp must mint via ensure_customer_for_user (Edge).
  IF v_provider IN ('google', 'apple') THEN
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
  'After auth.users insert: ensure profiles row; for Google/Apple OAuth also insert '
  'a retail customers row (idempotent). OTP signup mints customers via '
  'ensure_customer_for_user — do not rely on gtr_provisioned_via here for Admin creates.';

-- ---------------------------------------------------------------------------
-- ensure_customer_for_user: service_role mint (auth-otp complete_signup)
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.ensure_customer_for_user(p_uid UUID)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_cust UUID;
  v_name TEXT;
  v_email TEXT;
  v_retail UUID;
BEGIN
  IF auth.role() IS DISTINCT FROM 'service_role' THEN
    RAISE EXCEPTION 'service_role required';
  END IF;

  IF p_uid IS NULL THEN
    RAISE EXCEPTION 'p_uid required';
  END IF;

  IF public._storefront_customer_provision_denied(p_uid) THEN
    RAISE EXCEPTION 'staff accounts do not use storefront customer provisioning';
  END IF;

  SELECT c.id INTO v_cust
  FROM public.customers c
  WHERE c.profile_id = p_uid
  LIMIT 1;

  IF v_cust IS NOT NULL THEN
    RETURN v_cust;
  END IF;

  INSERT INTO public.profiles (id, full_name, is_staff)
  VALUES (p_uid, NULL, false)
  ON CONFLICT (id) DO NOTHING;

  SELECT
    nullif(
      btrim(
        COALESCE(
          p.full_name,
          split_part(COALESCE(u.email, ''), '@', 1)
        )
      ),
      ''
    ),
    nullif(u.email, '')
  INTO v_name, v_email
  FROM auth.users u
  LEFT JOIN public.profiles p ON p.id = u.id
  WHERE u.id = p_uid;

  IF NOT FOUND THEN
    RAISE EXCEPTION 'auth user not found: %', p_uid;
  END IF;

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
  VALUES (v_name, v_email, 'USD', p_uid, v_retail)
  ON CONFLICT (profile_id) WHERE (profile_id IS NOT NULL) DO NOTHING
  RETURNING id INTO v_cust;

  IF v_cust IS NULL THEN
    SELECT c.id INTO v_cust
    FROM public.customers c
    WHERE c.profile_id = p_uid
    LIMIT 1;
  END IF;

  RETURN v_cust;
END;
$$;

COMMENT ON FUNCTION public.ensure_customer_for_user(UUID) IS
  'service_role only: idempotent retail customers row for p_uid. Used by auth-otp '
  'complete_signup after Admin createUser (handle_new_user cannot see auth_otp meta).';

REVOKE ALL ON FUNCTION public.ensure_customer_for_user(UUID) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.ensure_customer_for_user(UUID) FROM anon, authenticated;
GRANT EXECUTE ON FUNCTION public.ensure_customer_for_user(UUID) TO service_role;

-- ---------------------------------------------------------------------------
-- ensure_own_customer: broader staff deny + ON CONFLICT
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

  IF public._storefront_customer_provision_denied(v_uid) THEN
    RAISE EXCEPTION 'staff accounts do not use storefront customer provisioning';
  END IF;

  SELECT c.id INTO v_cust
  FROM public.customers c
  WHERE c.profile_id = v_uid
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
  ON CONFLICT (profile_id) WHERE (profile_id IS NOT NULL) DO NOTHING
  RETURNING id INTO v_cust;

  IF v_cust IS NULL THEN
    SELECT c.id INTO v_cust
    FROM public.customers c
    WHERE c.profile_id = v_uid
    LIMIT 1;
  END IF;

  RETURN v_cust;
END;
$$;

COMMENT ON FUNCTION public.ensure_own_customer() IS
  'Idempotent: return customers.id for auth.uid(), inserting a retail row if missing. '
  'Staff / employees / HR-onboarded denied. Call after OAuth when _current_customer_id() '
  'is null. OTP signup prefers ensure_customer_for_user from Edge.';

REVOKE ALL ON FUNCTION public.ensure_own_customer() FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.ensure_own_customer() TO authenticated, service_role;
