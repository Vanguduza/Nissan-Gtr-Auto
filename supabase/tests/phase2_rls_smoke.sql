-- Phase 2 RLS / helper smoke checks (run after seed as postgres / service role).
-- Example: psql "$DATABASE_URL" -f supabase/tests/phase2_rls_smoke.sql

DO $$
DECLARE
  v_admin UUID := 'a0000000-0000-4000-8000-000000000001';
  v_finance UUID := 'a0000000-0000-4000-8000-000000000002';
  v_wh UUID := 'a0000000-0000-4000-8000-000000000003';
BEGIN
  IF NOT EXISTS (SELECT 1 FROM public.profiles WHERE id = v_admin AND is_staff) THEN
    RAISE EXCEPTION 'smoke fail: admin is_staff expected true';
  END IF;
  IF NOT EXISTS (
    SELECT 1 FROM public.staff_roles WHERE user_id = v_admin AND role = 'admin'
  ) THEN
    RAISE EXCEPTION 'smoke fail: admin role missing';
  END IF;
  IF NOT EXISTS (
    SELECT 1 FROM public.staff_roles WHERE user_id = v_finance AND role = 'finance'
  ) THEN
    RAISE EXCEPTION 'smoke fail: finance role missing';
  END IF;
  IF NOT EXISTS (
    SELECT 1 FROM public.staff_roles WHERE user_id = v_wh AND role = 'warehouse'
  ) THEN
    RAISE EXCEPTION 'smoke fail: warehouse role missing';
  END IF;

  -- Simulate customer: no staff_roles → is_staff false after sync
  -- (helpers themselves need auth.uid(); verified in app/integration later)

  RAISE NOTICE 'phase2_rls_smoke: seed staff rows OK';
END;
$$;
