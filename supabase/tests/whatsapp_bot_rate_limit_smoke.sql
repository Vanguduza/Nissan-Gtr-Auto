-- Smoke: WhatsApp bot rate-limit RPC (Batch 3).
-- Requires migration 20260724160000_whatsapp_bot_rate_limits applied.
-- Run as service_role / postgres (supabase db reset / psql with elevated role).
-- Does NOT call Meta or search_catalog.

DO $$
DECLARE
  v_r jsonb;
  v_i int;
BEGIN
  -- service_role gate: when run as postgres, auth.role() is typically null /
  -- not service_role depending on session. Prefer SET ROLE or call via Edge.
  -- This block assumes current_user can execute as SECURITY DEFINER owner
  -- AND we temporarily set request.jwt.claim.role for the check — skip if
  -- auth.role() is unavailable and use direct table simulation instead.

  -- Direct path: exercise table + function when role is service_role.
  PERFORM set_config('request.jwt.claim.role', 'service_role', true);
  PERFORM set_config('role', 'service_role', true);

  BEGIN
    -- Reset sender budget
    DELETE FROM public.whatsapp_bot_rate_limits WHERE wa_from = '263700000001';

    v_r := public.check_whatsapp_bot_rate_limit('263700000001', 3, 60);
    IF (v_r->>'allowed')::boolean IS NOT TRUE THEN
      RAISE EXCEPTION 'smoke fail: first request should be allowed';
    END IF;

    v_r := public.check_whatsapp_bot_rate_limit('263700000001', 3, 60);
    v_r := public.check_whatsapp_bot_rate_limit('263700000001', 3, 60);
    IF (v_r->>'allowed')::boolean IS NOT TRUE THEN
      RAISE EXCEPTION 'smoke fail: third request should still be allowed (max=3)';
    END IF;

    v_r := public.check_whatsapp_bot_rate_limit('263700000001', 3, 60);
    IF (v_r->>'allowed')::boolean IS DISTINCT FROM FALSE THEN
      RAISE EXCEPTION 'smoke fail: fourth request should be denied';
    END IF;
    IF COALESCE((v_r->>'retry_after_seconds')::int, 0) < 1 THEN
      RAISE EXCEPTION 'smoke fail: expected retry_after_seconds >= 1';
    END IF;

    RAISE NOTICE 'whatsapp_bot_rate_limit_smoke: OK (deny after max)';
  EXCEPTION
    WHEN OTHERS THEN
      IF SQLERRM LIKE 'service_role required%' THEN
        RAISE NOTICE
          'whatsapp_bot_rate_limit_smoke: SKIPPED (auth.role() != service_role in this session)';
      ELSE
        RAISE;
      END IF;
  END;

  -- Confirm anon must not have EXECUTE (catalog search stays authenticated+service_role)
  IF EXISTS (
    SELECT 1
    FROM information_schema.role_routine_grants
    WHERE routine_schema = 'public'
      AND routine_name = 'search_catalog'
      AND grantee = 'anon'
      AND privilege_type = 'EXECUTE'
  ) THEN
    RAISE EXCEPTION 'smoke fail: anon must not EXECUTE search_catalog';
  END IF;

  IF EXISTS (
    SELECT 1
    FROM information_schema.role_routine_grants
    WHERE routine_schema = 'public'
      AND routine_name = 'check_whatsapp_bot_rate_limit'
      AND grantee IN ('anon', 'authenticated')
      AND privilege_type = 'EXECUTE'
  ) THEN
    RAISE EXCEPTION 'smoke fail: anon/authenticated must not EXECUTE check_whatsapp_bot_rate_limit';
  END IF;

  RAISE NOTICE 'whatsapp_bot_rate_limit_smoke: grant checks OK';
END $$;
