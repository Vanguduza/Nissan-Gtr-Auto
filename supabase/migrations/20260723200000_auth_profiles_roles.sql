-- Phase 2: auth → profiles, is_staff hardening, admin role RPCs
-- Exclusions: no ZIMRA / payroll-tax fields

-- ---------------------------------------------------------------------------
-- Signup → profiles
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.handle_new_user()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
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
  RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS on_auth_user_created ON auth.users;
CREATE TRIGGER on_auth_user_created
  AFTER INSERT ON auth.users
  FOR EACH ROW
  EXECUTE PROCEDURE public.handle_new_user();

-- ---------------------------------------------------------------------------
-- Block client escalation of is_staff; allow sync via GUC
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.protect_profile_staff_flag()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
  IF TG_OP = 'INSERT' THEN
    IF NEW.is_staff IS TRUE
       AND current_setting('gtr.syncing_is_staff', true) IS DISTINCT FROM 'on' THEN
      RAISE EXCEPTION 'is_staff cannot be set on insert; use assign_staff_role';
    END IF;
    NEW.updated_at := now();
    RETURN NEW;
  END IF;

  IF NEW.is_staff IS DISTINCT FROM OLD.is_staff
     AND current_setting('gtr.syncing_is_staff', true) IS DISTINCT FROM 'on' THEN
    RAISE EXCEPTION 'is_staff can only change via staff role assignment';
  END IF;

  NEW.updated_at := now();
  RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS profiles_protect_is_staff ON public.profiles;
CREATE TRIGGER profiles_protect_is_staff
  BEFORE INSERT OR UPDATE ON public.profiles
  FOR EACH ROW
  EXECUTE PROCEDURE public.protect_profile_staff_flag();

-- Keep profiles.is_staff in sync with staff_roles membership
CREATE OR REPLACE FUNCTION public.sync_profile_is_staff()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_user_id UUID;
BEGIN
  v_user_id := COALESCE(NEW.user_id, OLD.user_id);
  PERFORM set_config('gtr.syncing_is_staff', 'on', true);
  UPDATE public.profiles p
  SET
    is_staff = EXISTS (
      SELECT 1 FROM public.staff_roles sr WHERE sr.user_id = v_user_id
    ),
    updated_at = now()
  WHERE p.id = v_user_id;
  RETURN COALESCE(NEW, OLD);
END;
$$;

DROP TRIGGER IF EXISTS staff_roles_sync_is_staff ON public.staff_roles;
CREATE TRIGGER staff_roles_sync_is_staff
  AFTER INSERT OR DELETE ON public.staff_roles
  FOR EACH ROW
  EXECUTE PROCEDURE public.sync_profile_is_staff();

-- Tighten insert: clients may only create their own non-staff profile
DROP POLICY IF EXISTS profiles_insert_own ON public.profiles;
CREATE POLICY profiles_insert_own
  ON public.profiles FOR INSERT TO authenticated
  WITH CHECK (id = auth.uid() AND is_staff = false);

-- ---------------------------------------------------------------------------
-- Admin RPCs (preferred path; table RLS still allows admin direct writes)
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.assign_staff_role(
  p_user_id UUID,
  p_role public.staff_role
)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
  IF auth.role() = 'authenticated'
     AND NOT public.has_staff_role(ARRAY['admin']::public.staff_role[]) THEN
    RAISE EXCEPTION 'admin role required to assign staff roles';
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
BEGIN
  IF auth.role() = 'authenticated'
     AND NOT public.has_staff_role(ARRAY['admin']::public.staff_role[]) THEN
    RAISE EXCEPTION 'admin role required to revoke staff roles';
  END IF;

  DELETE FROM public.staff_roles
  WHERE user_id = p_user_id
    AND role = p_role;
END;
$$;

REVOKE ALL ON FUNCTION public.assign_staff_role(UUID, public.staff_role) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.revoke_staff_role(UUID, public.staff_role) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.assign_staff_role(UUID, public.staff_role)
  TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.revoke_staff_role(UUID, public.staff_role)
  TO authenticated, service_role;

-- Backfill: any existing auth users without profiles
INSERT INTO public.profiles (id, full_name, is_staff)
SELECT
  u.id,
  COALESCE(u.raw_user_meta_data ->> 'full_name', u.raw_user_meta_data ->> 'name'),
  false
FROM auth.users u
WHERE NOT EXISTS (SELECT 1 FROM public.profiles p WHERE p.id = u.id);
