-- Exported from the hosted project's supabase_migrations.schema_migrations (20260905122351 harden_staff_login_audit_and_employee_series).
-- Source of record for what production ran; see supabase/live-history/README.md.

-- P0 auth hardening: client-reported failed-login counters are attacker-controlled.
-- Only service_role may record arbitrary outcomes. Authenticated staff may record a
-- successful login only for their own active employee identifier.
CREATE OR REPLACE FUNCTION public.record_staff_login_attempt(
  p_identifier text,
  p_success boolean
)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = 'public'
AS $function$
DECLARE
  v_raw text;
  v_hash text;
  v_uid uuid := auth.uid();
  v_role text := auth.role();
  v_matches_self boolean := false;
BEGIN
  v_raw := lower(trim(COALESCE(p_identifier, '')));
  IF v_raw = '' THEN
    RETURN;
  END IF;

  IF v_role IS DISTINCT FROM 'service_role' THEN
    IF v_uid IS NULL THEN
      RAISE EXCEPTION 'authenticated session required for login audit';
    END IF;
    IF COALESCE(p_success, false) IS NOT TRUE THEN
      RAISE EXCEPTION 'failed login attempts must be recorded by the trusted auth service';
    END IF;

    SELECT EXISTS (
      SELECT 1
      FROM public.employees e
      LEFT JOIN auth.users u ON u.id = e.user_id
      WHERE e.user_id = v_uid
        AND e.status = 'active'
        AND (
          (
            position('@' IN v_raw) > 0
            AND (
              lower(COALESCE(e.email, '')) = v_raw
              OR lower(COALESCE(u.email, '')) = v_raw
            )
          )
          OR (
            v_raw ~ '^\+?[0-9]{7,15}$'
            AND regexp_replace(COALESCE(e.phone_e164, ''), '[^0-9+]', '', 'g')
                = regexp_replace(v_raw, '[^0-9+]', '', 'g')
          )
          OR (
            position('@' IN v_raw) = 0
            AND v_raw !~ '^\+?[0-9]{7,15}$'
            AND lower(COALESCE(e.employee_code, '')) = v_raw
          )
        )
    ) INTO v_matches_self;

    IF NOT v_matches_self THEN
      RAISE EXCEPTION 'login audit identifier does not belong to current staff user';
    END IF;
  END IF;

  v_hash := encode(extensions.digest(convert_to(v_raw, 'UTF8'), 'sha256'), 'hex');

  INSERT INTO public.staff_login_failures (identifier_hash, success)
  VALUES (v_hash, COALESCE(p_success, false));

  IF p_success THEN
    DELETE FROM public.staff_login_failures
    WHERE identifier_hash = v_hash
      AND success = false
      AND attempted_at > now() - interval '15 minutes';
  END IF;
END;
$function$;

REVOKE ALL ON FUNCTION public.record_staff_login_attempt(text, boolean) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.record_staff_login_attempt(text, boolean) TO authenticated, service_role;

COMMENT ON FUNCTION public.record_staff_login_attempt(text, boolean) IS
  'Trusted login-audit mutation. service_role may record outcomes; authenticated staff may record only their own successful login. Anonymous clients cannot mutate lockout state.';

-- Employee numbering is an HR-internal mutation. The only database caller is the
-- SECURITY DEFINER complete_hr_onboarding RPC; public callers must not burn series values.
REVOKE ALL ON FUNCTION public.next_employee_code_for_grade(text) FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.next_employee_code_for_grade(text) TO service_role;
