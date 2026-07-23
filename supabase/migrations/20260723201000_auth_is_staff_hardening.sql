-- Phase 2 follow-up: close is_staff escalation (no client-settable GUC)
-- See security review: set_config('gtr.syncing_is_staff') was forgeable by authenticated.

CREATE OR REPLACE FUNCTION public.protect_profile_staff_flag()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
  -- Only non-client roles (e.g. postgres / migration / SECURITY DEFINER owner) may change is_staff.
  IF TG_OP = 'INSERT' THEN
    IF NEW.is_staff IS TRUE
       AND current_user IN ('authenticated', 'anon', 'authenticator') THEN
      RAISE EXCEPTION 'is_staff cannot be set on insert; use assign_staff_role';
    END IF;
    NEW.updated_at := now();
    RETURN NEW;
  END IF;

  IF NEW.is_staff IS DISTINCT FROM OLD.is_staff
     AND current_user IN ('authenticated', 'anon', 'authenticator') THEN
    RAISE EXCEPTION 'is_staff can only change via staff role assignment';
  END IF;

  NEW.updated_at := now();
  RETURN NEW;
END;
$$;

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
  -- Runs as function owner → protect_profile_staff_flag allows is_staff change.
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

-- Column privileges: clients cannot UPDATE is_staff even if they forge a WITH CHECK.
REVOKE UPDATE ON TABLE public.profiles FROM authenticated;
GRANT UPDATE (full_name, updated_at) ON TABLE public.profiles TO authenticated;

DROP POLICY IF EXISTS profiles_update_own ON public.profiles;
CREATE POLICY profiles_update_own
  ON public.profiles FOR UPDATE TO authenticated
  USING (id = auth.uid())
  WITH CHECK (
    id = auth.uid()
    AND is_staff = (SELECT p.is_staff FROM public.profiles p WHERE p.id = auth.uid())
  );

-- Fail-closed admin RPCs (service_role OR admin staff role only)
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
  IF NOT (
    auth.role() = 'service_role'
    OR public.has_staff_role(ARRAY['admin']::public.staff_role[])
  ) THEN
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
  IF NOT (
    auth.role() = 'service_role'
    OR public.has_staff_role(ARRAY['admin']::public.staff_role[])
  ) THEN
    RAISE EXCEPTION 'admin role required to revoke staff roles';
  END IF;

  DELETE FROM public.staff_roles
  WHERE user_id = p_user_id
    AND role = p_role;
END;
$$;
