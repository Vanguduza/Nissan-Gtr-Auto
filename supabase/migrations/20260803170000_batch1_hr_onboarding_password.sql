-- Batch 1 §2.3 / §2.5 / §2.6 — onboarding stages, must_change_password, reset challenges.
-- No payroll tax. Camera capture remains Bridge-First (no HTML5).

ALTER TABLE public.profiles
  ADD COLUMN IF NOT EXISTS must_change_password BOOLEAN NOT NULL DEFAULT false;

COMMENT ON COLUMN public.profiles.must_change_password IS
  'Staff must change password before other staff surfaces (Batch 1 §2.5).';

CREATE TYPE public.hr_onboarding_stage AS ENUM (
  'personal',
  'role_contract',
  'banking_health',
  'documents',
  'credentials'
);

CREATE TABLE IF NOT EXISTS public.hr_onboarding_drafts (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  employee_id UUID REFERENCES public.employees (id) ON DELETE SET NULL,
  stage public.hr_onboarding_stage NOT NULL DEFAULT 'personal',
  payload JSONB NOT NULL DEFAULT '{}'::jsonb,
  -- Sensitive banking/health live only in payload keys under RLS (HR/admin write).
  banking_json JSONB,
  health_json JSONB,
  created_by UUID REFERENCES auth.users (id),
  updated_by UUID REFERENCES auth.users (id),
  completed_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS hr_onboarding_drafts_emp_idx
  ON public.hr_onboarding_drafts (employee_id);
CREATE INDEX IF NOT EXISTS hr_onboarding_drafts_stage_idx
  ON public.hr_onboarding_drafts (stage)
  WHERE completed_at IS NULL;

-- Password-reset OTP challenges (mirrors auth_otp_challenges; purpose=password_reset)
CREATE TABLE IF NOT EXISTS public.password_reset_challenges (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  channel TEXT NOT NULL CHECK (channel IN ('email', 'phone')),
  identifier TEXT NOT NULL,
  code_hash TEXT NOT NULL,
  expires_at TIMESTAMPTZ NOT NULL,
  attempt_count INT NOT NULL DEFAULT 0 CHECK (attempt_count >= 0),
  consumed_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT password_reset_challenges_identifier_nonempty CHECK (length(trim(identifier)) > 0),
  CONSTRAINT password_reset_challenges_hash_nonempty CHECK (length(trim(code_hash)) > 0)
);

CREATE INDEX IF NOT EXISTS password_reset_challenges_lookup_idx
  ON public.password_reset_challenges (channel, identifier, expires_at DESC);

CREATE OR REPLACE FUNCTION public.clear_must_change_password()
RETURNS VOID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
  IF auth.uid() IS NULL THEN
    RAISE EXCEPTION 'not authenticated';
  END IF;
  UPDATE public.profiles
  SET must_change_password = false, updated_at = now()
  WHERE id = auth.uid();
END;
$$;

CREATE OR REPLACE FUNCTION public.set_must_change_password(p_user_id UUID, p_required BOOLEAN DEFAULT true)
RETURNS VOID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
  IF NOT public.has_staff_role(ARRAY['admin', 'hr']::public.staff_role[]) THEN
    RAISE EXCEPTION 'hr or admin role required';
  END IF;
  UPDATE public.profiles
  SET must_change_password = COALESCE(p_required, true), updated_at = now()
  WHERE id = p_user_id;
END;
$$;

-- RLS
ALTER TABLE public.hr_onboarding_drafts ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.password_reset_challenges ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS hr_onboarding_select ON public.hr_onboarding_drafts;
CREATE POLICY hr_onboarding_select ON public.hr_onboarding_drafts
  FOR SELECT TO authenticated
  USING (public.has_staff_role(ARRAY['admin', 'hr']::public.staff_role[]));

DROP POLICY IF EXISTS hr_onboarding_write ON public.hr_onboarding_drafts;
CREATE POLICY hr_onboarding_write ON public.hr_onboarding_drafts
  FOR ALL TO authenticated
  USING (public.has_staff_role(ARRAY['admin', 'hr']::public.staff_role[]))
  WITH CHECK (public.has_staff_role(ARRAY['admin', 'hr']::public.staff_role[]));

-- Challenges: service_role only (Edge functions); no authenticated policies
GRANT ALL ON TABLE public.password_reset_challenges TO service_role;

REVOKE ALL ON FUNCTION public.clear_must_change_password() FROM PUBLIC;
REVOKE ALL ON FUNCTION public.set_must_change_password(UUID, BOOLEAN) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.clear_must_change_password() TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.set_must_change_password(UUID, BOOLEAN)
  TO authenticated, service_role;
