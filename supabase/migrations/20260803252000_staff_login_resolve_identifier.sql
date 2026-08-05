-- Staff login identifier resolve — emp# OR email OR phone → GoTrue email.
-- Callable by anon (pre-auth). Rate-limited; non-enumerating errors. Customers blocked.
-- Tablet / staff surfaces only — never customer storefront emp# signup.

CREATE TABLE IF NOT EXISTS public.staff_login_resolve_attempts (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  identifier_hash TEXT NOT NULL,
  attempted_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  success BOOLEAN NOT NULL DEFAULT false
);

CREATE INDEX IF NOT EXISTS staff_login_resolve_attempts_hash_idx
  ON public.staff_login_resolve_attempts (identifier_hash, attempted_at DESC);

ALTER TABLE public.staff_login_resolve_attempts ENABLE ROW LEVEL SECURITY;

-- No client SELECT/INSERT — SECURITY DEFINER only.
DROP POLICY IF EXISTS staff_login_resolve_deny ON public.staff_login_resolve_attempts;
CREATE POLICY staff_login_resolve_deny ON public.staff_login_resolve_attempts
  FOR ALL TO authenticated
  USING (false)
  WITH CHECK (false);

CREATE OR REPLACE FUNCTION public.resolve_staff_login_email(p_identifier TEXT)
RETURNS TEXT
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_raw TEXT;
  v_hash TEXT;
  v_recent INT;
  v_email TEXT;
  v_emp public.employees%ROWTYPE;
BEGIN
  v_raw := lower(trim(COALESCE(p_identifier, '')));
  IF v_raw = '' THEN
    RAISE EXCEPTION 'invalid credentials';
  END IF;

  v_hash := encode(extensions.digest(convert_to(v_raw, 'UTF8'), 'sha256'), 'hex');

  SELECT COUNT(*)::INT INTO v_recent
  FROM public.staff_login_resolve_attempts
  WHERE identifier_hash = v_hash
    AND attempted_at > now() - interval '15 minutes';

  IF v_recent >= 12 THEN
    RAISE EXCEPTION 'invalid credentials';
  END IF;

  -- Prefer employee_code (emp#) when no @ and not phone-like.
  IF position('@' IN v_raw) > 0 THEN
    SELECT e.* INTO v_emp
    FROM public.employees e
    WHERE e.status = 'active'
      AND (
        lower(COALESCE(e.email, '')) = v_raw
        OR EXISTS (
          SELECT 1 FROM auth.users u
          WHERE u.id = e.user_id AND lower(u.email) = v_raw
        )
      )
    LIMIT 1;
  ELSIF v_raw ~ '^\+?[0-9]{7,15}$' THEN
    SELECT e.* INTO v_emp
    FROM public.employees e
    WHERE e.status = 'active'
      AND regexp_replace(COALESCE(e.phone_e164, ''), '[^0-9+]', '', 'g')
          = regexp_replace(v_raw, '[^0-9+]', '', 'g')
    LIMIT 1;
  ELSE
    SELECT e.* INTO v_emp
    FROM public.employees e
    WHERE e.status = 'active'
      AND lower(e.employee_code) = v_raw
    LIMIT 1;
  END IF;

  IF NOT FOUND OR v_emp.user_id IS NULL THEN
    INSERT INTO public.staff_login_resolve_attempts (identifier_hash, success)
    VALUES (v_hash, false);
    RAISE EXCEPTION 'invalid credentials';
  END IF;

  SELECT lower(u.email) INTO v_email
  FROM auth.users u
  WHERE u.id = v_emp.user_id;

  IF v_email IS NULL OR v_email = '' THEN
    v_email := lower(NULLIF(trim(COALESCE(v_emp.email, '')), ''));
  END IF;

  IF v_email IS NULL OR v_email = '' THEN
    INSERT INTO public.staff_login_resolve_attempts (identifier_hash, success)
    VALUES (v_hash, false);
    RAISE EXCEPTION 'invalid credentials';
  END IF;

  INSERT INTO public.staff_login_resolve_attempts (identifier_hash, success)
  VALUES (v_hash, true);

  RETURN v_email;
END;
$$;

COMMENT ON FUNCTION public.resolve_staff_login_email(TEXT) IS
  'Pre-auth staff identifier → GoTrue email. emp#|email|phone. Active employees only. Non-enumerating.';

REVOKE ALL ON FUNCTION public.resolve_staff_login_email(TEXT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.resolve_staff_login_email(TEXT)
  TO anon, authenticated, service_role;
