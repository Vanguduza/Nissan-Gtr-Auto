-- Two tables had no row level security (found by supabase/tests/sim_role_rules_smoke.sql):
--   public.catalog_r2_release_inventory_work: catalog release scratch table written by the service role;
--     anon and authenticated could read, insert, update and delete it.
--   private.staff_badge_grants: read and written only by SECURITY DEFINER badge functions.
-- Neither needs client access, so RLS goes on with no policies (the service role and definer
-- functions bypass it) and the client grants are removed.
ALTER TABLE public.catalog_r2_release_inventory_work ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON public.catalog_r2_release_inventory_work FROM anon, authenticated;
ALTER TABLE private.staff_badge_grants ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON private.staff_badge_grants FROM anon, authenticated;
