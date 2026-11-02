-- HR onboarding auth: cross-isolate single-flight claim locks.
-- Edge hr-onboarding-create-auth claims before Admin createUser; releases after link.
-- Prevents duplicate Auth users under concurrent complete flows. No ZIMRA / payroll tax.

CREATE TABLE public.hr_auth_provision_locks (
  employee_id UUID PRIMARY KEY REFERENCES public.employees (id) ON DELETE CASCADE,
  claim_token TEXT NOT NULL,
  claimed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  expires_at TIMESTAMPTZ NOT NULL
);

COMMENT ON TABLE public.hr_auth_provision_locks IS
  'Short-lived single-flight locks for hr-onboarding-create-auth (service_role / DEFINER only).';

ALTER TABLE public.hr_auth_provision_locks ENABLE ROW LEVEL SECURITY;

-- No authenticated policies — staff never read locks; service_role + DEFINER RPCs only.
GRANT ALL ON TABLE public.hr_auth_provision_locks TO service_role;

CREATE OR REPLACE FUNCTION public.claim_hr_auth_provision(
  p_employee_id UUID,
  p_claim_token TEXT,
  p_ttl_seconds INT DEFAULT 120
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_emp public.employees%ROWTYPE;
  v_ttl INT := GREATEST(COALESCE(p_ttl_seconds, 120), 30);
  v_token TEXT := NULLIF(trim(COALESCE(p_claim_token, '')), '');
BEGIN
  IF auth.role() IS DISTINCT FROM 'service_role' THEN
    RAISE EXCEPTION 'service_role required';
  END IF;
  IF p_employee_id IS NULL OR v_token IS NULL THEN
    RAISE EXCEPTION 'employee_id and claim_token required';
  END IF;

  DELETE FROM public.hr_auth_provision_locks
  WHERE expires_at < now();

  SELECT * INTO v_emp
  FROM public.employees
  WHERE id = p_employee_id
  FOR UPDATE;
  IF NOT FOUND THEN
    RETURN jsonb_build_object('ok', false, 'reason', 'not_found');
  END IF;
  IF v_emp.status = 'terminated' THEN
    RETURN jsonb_build_object('ok', false, 'reason', 'terminated');
  END IF;
  IF v_emp.user_id IS NOT NULL THEN
    RETURN jsonb_build_object(
      'ok', false,
      'reason', 'already_linked',
      'user_id', v_emp.user_id
    );
  END IF;

  DELETE FROM public.hr_auth_provision_locks
  WHERE employee_id = p_employee_id
    AND expires_at < now();

  BEGIN
    INSERT INTO public.hr_auth_provision_locks (
      employee_id, claim_token, claimed_at, expires_at
    ) VALUES (
      p_employee_id, v_token, now(), now() + make_interval(secs => v_ttl)
    );
  EXCEPTION
    WHEN unique_violation THEN
      RETURN jsonb_build_object('ok', false, 'reason', 'in_flight');
  END;

  RETURN jsonb_build_object(
    'ok', true,
    'employee_id', v_emp.id,
    'email', v_emp.email,
    'phone_e164', v_emp.phone_e164,
    'full_name', v_emp.full_name,
    'employee_code', v_emp.employee_code
  );
END;
$$;

CREATE OR REPLACE FUNCTION public.release_hr_auth_provision(
  p_employee_id UUID,
  p_claim_token TEXT
)
RETURNS BOOLEAN
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_token TEXT := NULLIF(trim(COALESCE(p_claim_token, '')), '');
BEGIN
  IF auth.role() IS DISTINCT FROM 'service_role' THEN
    RAISE EXCEPTION 'service_role required';
  END IF;
  IF p_employee_id IS NULL OR v_token IS NULL THEN
    RETURN false;
  END IF;
  DELETE FROM public.hr_auth_provision_locks
  WHERE employee_id = p_employee_id
    AND claim_token = v_token;
  RETURN FOUND;
END;
$$;

REVOKE ALL ON FUNCTION public.claim_hr_auth_provision(UUID, TEXT, INT) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.release_hr_auth_provision(UUID, TEXT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.claim_hr_auth_provision(UUID, TEXT, INT) TO service_role;
GRANT EXECUTE ON FUNCTION public.release_hr_auth_provision(UUID, TEXT) TO service_role;
