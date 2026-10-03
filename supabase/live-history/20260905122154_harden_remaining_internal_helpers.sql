-- Exported from the hosted project's supabase_migrations.schema_migrations (20260905122154 harden_remaining_internal_helpers).
-- Source of record for what production ran; see supabase/live-history/README.md.

-- Close remaining directly callable internal control/mutation helpers.
-- Every legitimate caller is a SECURITY DEFINER domain RPC, so client EXECUTE is unnecessary.
DO $do$
DECLARE r record;
BEGIN
  FOR r IN
    SELECT p.oid::regprocedure AS signature
    FROM pg_proc p
    JOIN pg_namespace n ON n.oid = p.pronamespace
    WHERE n.nspname = 'public'
      AND p.proname = ANY (ARRAY[
        '_notify_sales_prep',
        '_require_return_post',
        '_procurement_end_rpc',
        '_storefront_rpc_exit'
      ])
  LOOP
    EXECUTE format('REVOKE ALL ON FUNCTION %s FROM PUBLIC, anon, authenticated', r.signature);
    EXECUTE format('GRANT EXECUTE ON FUNCTION %s TO service_role', r.signature);
  END LOOP;
END
$do$;

ALTER FUNCTION public._procurement_end_rpc() SET search_path = '';
ALTER FUNCTION public._storefront_rpc_exit() SET search_path = '';
