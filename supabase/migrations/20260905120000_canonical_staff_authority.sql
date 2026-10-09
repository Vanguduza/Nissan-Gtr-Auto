-- Canonical hosted staff authority.
-- These three UUID/email/role mappings are production identity facts.
-- Passwords are deliberately excluded: credentials are secret operational inputs.

CREATE TABLE IF NOT EXISTS public.canonical_staff_accounts (
  user_id UUID PRIMARY KEY,
  email TEXT NOT NULL UNIQUE,
  full_name TEXT NOT NULL,
  role public.staff_role NOT NULL,
  is_active BOOLEAN NOT NULL DEFAULT true,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT canonical_staff_email_lowercase CHECK (email = lower(email))
);

COMMENT ON TABLE public.canonical_staff_accounts IS
  'Authoritative Nissan GTR Auto hosted staff UUID/email/role registry. Mutated only by migrations.';

ALTER TABLE public.canonical_staff_accounts ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON TABLE public.canonical_staff_accounts FROM PUBLIC, anon, authenticated, service_role;
GRANT SELECT ON TABLE public.canonical_staff_accounts TO service_role;

INSERT INTO public.canonical_staff_accounts (user_id, email, full_name, role, is_active)
VALUES
  ('a0000000-0000-4000-8000-000000000001', 'admin@gtr.local', 'Nissan GTR Auto Admin', 'admin', true),
  ('a0000000-0000-4000-8000-000000000002', 'finance@gtr.local', 'Nissan GTR Auto Finance', 'finance', true),
  ('a0000000-0000-4000-8000-000000000003', 'warehouse@gtr.local', 'Nissan GTR Auto Warehouse', 'warehouse', true)
ON CONFLICT (user_id) DO UPDATE
SET email = EXCLUDED.email,
    full_name = EXCLUDED.full_name,
    role = EXCLUDED.role,
    is_active = EXCLUDED.is_active,
    updated_at = now();

CREATE OR REPLACE FUNCTION public.protect_canonical_staff_role()
RETURNS trigger
LANGUAGE plpgsql
SET search_path = public
AS $$
DECLARE
  v_role public.staff_role;
BEGIN
  IF TG_OP = 'INSERT' THEN
    SELECT role INTO v_role FROM public.canonical_staff_accounts
    WHERE user_id = NEW.user_id AND is_active;
    IF FOUND AND NEW.role IS DISTINCT FROM v_role THEN
      RAISE EXCEPTION 'canonical staff % may only hold role %', NEW.user_id, v_role;
    END IF;
    RETURN NEW;
  END IF;

  IF TG_OP = 'DELETE' THEN
    SELECT role INTO v_role FROM public.canonical_staff_accounts
    WHERE user_id = OLD.user_id AND is_active;
    IF FOUND AND OLD.role = v_role
       AND current_user IN ('anon','authenticated','authenticator','service_role') THEN
      RAISE EXCEPTION 'cannot remove authoritative canonical staff role';
    END IF;
    RETURN OLD;
  END IF;
  -- staff_roles is not expected to update its PK, but fail closed if it does.
  SELECT role INTO v_role FROM public.canonical_staff_accounts
  WHERE user_id = OLD.user_id AND is_active;
  IF FOUND AND (NEW.user_id IS DISTINCT FROM OLD.user_id OR NEW.role IS DISTINCT FROM v_role) THEN
    RAISE EXCEPTION 'cannot mutate authoritative canonical staff role';
  END IF;
  SELECT role INTO v_role FROM public.canonical_staff_accounts
  WHERE user_id = NEW.user_id AND is_active;
  IF FOUND AND NEW.role IS DISTINCT FROM v_role THEN
    RAISE EXCEPTION 'canonical staff % may only hold role %', NEW.user_id, v_role;
  END IF;
  RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS staff_roles_protect_canonical ON public.staff_roles;
CREATE TRIGGER staff_roles_protect_canonical
  BEFORE INSERT OR UPDATE OR DELETE ON public.staff_roles
  FOR EACH ROW EXECUTE FUNCTION public.protect_canonical_staff_role();

CREATE OR REPLACE FUNCTION public.assign_staff_role(
  p_user_id UUID,
  p_role public.staff_role
)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_canonical_role public.staff_role;
BEGIN
  IF NOT (
    auth.role() = 'service_role'
    OR public.has_staff_role(ARRAY['admin']::public.staff_role[])
  ) THEN
    RAISE EXCEPTION 'admin role required to assign staff roles';
  END IF;

  SELECT role INTO v_canonical_role
  FROM public.canonical_staff_accounts
  WHERE user_id = p_user_id AND is_active;
  IF FOUND AND p_role IS DISTINCT FROM v_canonical_role THEN
    RAISE EXCEPTION 'canonical staff % may only hold role %', p_user_id, v_canonical_role;
  END IF;

  IF NOT EXISTS (SELECT 1 FROM public.profiles WHERE id = p_user_id) THEN
    RAISE EXCEPTION 'profile not found: %', p_user_id;
  END IF;

  INSERT INTO public.staff_roles (user_id, role)
  VALUES (p_user_id, p_role)
  ON CONFLICT (user_id, role) DO NOTHING;
END;
$$;

CREATE OR REPLACE FUNCTION public.revoke_staff_role(
  p_user_id UUID,
  p_role public.staff_role
)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_canonical_role public.staff_role;
BEGIN
  IF NOT (
    auth.role() = 'service_role'
    OR public.has_staff_role(ARRAY['admin']::public.staff_role[])
  ) THEN
    RAISE EXCEPTION 'admin role required to revoke staff roles';
  END IF;

  SELECT role INTO v_canonical_role
  FROM public.canonical_staff_accounts
  WHERE user_id = p_user_id AND is_active;
  IF FOUND AND p_role = v_canonical_role THEN
    RAISE EXCEPTION 'cannot revoke authoritative canonical staff role';
  END IF;

  DELETE FROM public.staff_roles
  WHERE user_id = p_user_id AND role = p_role;
END;
$$;

CREATE OR REPLACE FUNCTION public.canonical_staff_drift()
RETURNS TABLE (
  user_id UUID,
  email TEXT,
  role public.staff_role,
  auth_present BOOLEAN,
  email_matches BOOLEAN,
  email_confirmed BOOLEAN,
  provisioned_via_hr BOOLEAN,
  profile_present BOOLEAN,
  is_staff BOOLEAN,
  role_matches BOOLEAN,
  no_extra_roles BOOLEAN,
  in_sync BOOLEAN
)
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public, auth
AS $$
  SELECT
    c.user_id,
    c.email,
    c.role,
    u.id IS NOT NULL AS auth_present,
    COALESCE(lower(u.email) = c.email, false) AS email_matches,
    COALESCE(u.email_confirmed_at IS NOT NULL, false) AS email_confirmed,
    COALESCE(u.raw_app_meta_data ->> 'gtr_provisioned_via' = 'hr_onboarding', false) AS provisioned_via_hr,
    p.id IS NOT NULL AS profile_present,
    COALESCE(p.is_staff, false) AS is_staff,
    EXISTS (
      SELECT 1 FROM public.staff_roles sr
      WHERE sr.user_id = c.user_id AND sr.role = c.role
    ) AS role_matches,
    NOT EXISTS (
      SELECT 1 FROM public.staff_roles sr
      WHERE sr.user_id = c.user_id AND sr.role <> c.role
    ) AS no_extra_roles,
    (
      u.id IS NOT NULL
      AND lower(u.email) = c.email
      AND u.email_confirmed_at IS NOT NULL
      AND u.raw_app_meta_data ->> 'gtr_provisioned_via' = 'hr_onboarding'
      AND p.id IS NOT NULL
      AND p.is_staff IS TRUE
      AND EXISTS (
        SELECT 1 FROM public.staff_roles sr
        WHERE sr.user_id = c.user_id AND sr.role = c.role
      )
      AND NOT EXISTS (
        SELECT 1 FROM public.staff_roles sr
        WHERE sr.user_id = c.user_id AND sr.role <> c.role
      )
    ) AS in_sync
  FROM public.canonical_staff_accounts c
  LEFT JOIN auth.users u ON u.id = c.user_id
  LEFT JOIN public.profiles p ON p.id = c.user_id
  WHERE c.is_active
  ORDER BY c.email;
$$;

REVOKE ALL ON FUNCTION public.canonical_staff_drift() FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.canonical_staff_drift() TO service_role;
REVOKE ALL ON FUNCTION public.protect_canonical_staff_role() FROM PUBLIC, anon, authenticated;

COMMENT ON FUNCTION public.canonical_staff_drift() IS
  'Server-only authority gate: verifies canonical staff Auth/profile/role state without exposing secrets.';
