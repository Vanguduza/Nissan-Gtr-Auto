-- Smoke: WhatsApp bot rate-limit RPC (Batch 3).
-- Requires migration 20260724160000_whatsapp_bot_rate_limits applied.
-- Prefer: docker exec -i supabase_db_… psql -U postgres < this file
-- Does NOT call Meta or search_catalog.

CREATE OR REPLACE FUNCTION public._test_set_service_role(p_uid UUID DEFAULT NULL)
RETURNS void
LANGUAGE plpgsql
AS $$
BEGIN
  IF p_uid IS NOT NULL THEN
    PERFORM set_config('request.jwt.claim.sub', p_uid::text, true);
  END IF;
  PERFORM set_config(
    'request.jwt.claims',
    json_build_object(
      'sub', COALESCE(current_setting('request.jwt.claim.sub', true), ''),
      'role', 'service_role'
    )::text,
    true
  );
  PERFORM set_config('request.jwt.claim.role', 'service_role', true);
END;
$$;

DO $$
DECLARE
  v_r jsonb;
BEGIN
  PERFORM public._test_set_service_role();

  DELETE FROM public.whatsapp_bot_rate_limits WHERE wa_from = '263700000001';

  v_r := public.check_whatsapp_bot_rate_limit('263700000001', 3, 60);
  IF (v_r->>'allowed')::boolean IS NOT TRUE THEN
    RAISE EXCEPTION 'smoke fail: first request should be allowed';
  END IF;

  PERFORM public.check_whatsapp_bot_rate_limit('263700000001', 3, 60);
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

  -- anon must not EXECUTE search_catalog
  IF EXISTS (
    SELECT 1
    FROM information_schema.role_routine_grants
    WHERE specific_schema = 'public'
      AND routine_name = 'search_catalog'
      AND grantee = 'anon'
      AND privilege_type = 'EXECUTE'
  ) THEN
    RAISE EXCEPTION 'smoke fail: anon must not EXECUTE search_catalog';
  END IF;

  IF EXISTS (
    SELECT 1
    FROM information_schema.role_routine_grants
    WHERE specific_schema = 'public'
      AND routine_name = 'check_whatsapp_bot_rate_limit'
      AND grantee IN ('anon', 'authenticated')
      AND privilege_type = 'EXECUTE'
  ) THEN
    RAISE EXCEPTION 'smoke fail: anon/authenticated must not EXECUTE check_whatsapp_bot_rate_limit';
  END IF;

  RAISE NOTICE 'whatsapp_bot_rate_limit_smoke: OK';
END $$;
