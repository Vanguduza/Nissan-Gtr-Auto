-- Exported from the hosted project's supabase_migrations.schema_migrations (20260905135531 deny_anonymous_mutating_security_definer_rpcs).
-- Source of record for what production ran; see supabase/live-history/README.md.

DO $do$
DECLARE
  r record;
  v_fn text;
BEGIN
  FOR r IN
    SELECT p.oid,p.proname,pg_get_function_identity_arguments(p.oid) AS args
    FROM pg_proc p
    JOIN pg_namespace n ON n.oid=p.pronamespace
    WHERE n.nspname='public'
      AND p.prosecdef
      AND p.provolatile='v'
      AND p.prorettype <> 'trigger'::regtype
      AND has_function_privilege('anon',p.oid,'EXECUTE')
      AND p.proname NOT IN (
        'resolve_staff_login_email',
        'staff_login_is_locked',
        'get_delivery_track_point'
      )
  LOOP
    v_fn := format('%I.%I(%s)','public',r.proname,r.args);
    EXECUTE 'REVOKE EXECUTE ON FUNCTION '||v_fn||' FROM PUBLIC, anon';
    EXECUTE 'GRANT EXECUTE ON FUNCTION '||v_fn||' TO authenticated, service_role';
  END LOOP;
END
$do$;
