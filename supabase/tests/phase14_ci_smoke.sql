-- Phase 14 CI gate: every public base table must have RLS enabled.
-- Run as postgres after migrations + seed, e.g.:
--   docker exec -i supabase_db_<project> psql -U postgres -d postgres -v ON_ERROR_STOP=1 < supabase/tests/phase14_ci_smoke.sql
-- Or via GitHub Actions job `db-smoke` in .github/workflows/ci.yml.

DO $$
DECLARE
  v_open TEXT[];
BEGIN
  SELECT coalesce(array_agg(c.relname ORDER BY c.relname), ARRAY[]::text[])
  INTO v_open
  FROM pg_class c
  JOIN pg_namespace n ON n.oid = c.relnamespace
  WHERE n.nspname = 'public'
    AND c.relkind = 'r'
    AND c.relrowsecurity IS NOT TRUE;

  IF array_length(v_open, 1) IS NOT NULL AND array_length(v_open, 1) > 0 THEN
    RAISE EXCEPTION 'phase14 CI smoke fail: public tables without RLS: %', array_to_string(v_open, ', ');
  END IF;

  RAISE NOTICE 'phase14 CI smoke OK: all public base tables have RLS enabled';
END;
$$;
