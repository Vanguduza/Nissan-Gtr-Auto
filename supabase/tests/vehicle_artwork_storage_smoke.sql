-- vehicle-artwork Storage RLS smoke (public read; staff write).
-- Run after migrations, e.g.:
--   docker exec -i <supabase_db> psql -U postgres -d postgres -v ON_ERROR_STOP=1 \
--     < supabase/tests/vehicle_artwork_storage_smoke.sql

DO $$
DECLARE
  v_qual TEXT;
  v_check TEXT;
  v_roles TEXT[];
  v_customer UUID := 'c0000000-0000-4000-8000-0000000000a1';
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM storage.buckets
    WHERE id = 'vehicle-artwork'
      AND public IS TRUE
      AND file_size_limit = 5242880
      AND allowed_mime_types = ARRAY['image/webp']::text[]
  ) THEN
    RAISE EXCEPTION 'vehicle-artwork bucket missing, not public, or wrong limits';
  END IF;

  SELECT qual, roles INTO v_qual, v_roles
  FROM pg_policies
  WHERE schemaname = 'storage'
    AND tablename = 'objects'
    AND policyname = 'vehicle_artwork_public_read'
    AND cmd = 'SELECT';
  IF v_qual IS NULL OR v_qual NOT ILIKE '%vehicle-artwork%' THEN
    RAISE EXCEPTION 'missing or weak vehicle_artwork_public_read policy';
  END IF;

  SELECT with_check, roles INTO v_check, v_roles
  FROM pg_policies
  WHERE schemaname = 'storage'
    AND tablename = 'objects'
    AND policyname = 'vehicle_artwork_staff_insert'
    AND cmd = 'INSERT';
  IF v_check IS NULL
     OR v_check NOT ILIKE '%has_staff_role%'
     OR v_check NOT ILIKE '%vehicle-artwork%'
     OR v_check NOT ILIKE '%nissan_%'
     OR NOT ('authenticated' = ANY (v_roles)) THEN
    RAISE EXCEPTION 'missing or weak vehicle_artwork_staff_insert policy';
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM pg_policies
    WHERE schemaname = 'storage'
      AND tablename = 'objects'
      AND policyname = 'vehicle_artwork_staff_update'
      AND cmd = 'UPDATE'
      AND with_check ILIKE '%has_staff_role%'
  ) THEN
    RAISE EXCEPTION 'missing or weak vehicle_artwork_staff_update policy';
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM pg_policies
    WHERE schemaname = 'storage'
      AND tablename = 'objects'
      AND policyname = 'vehicle_artwork_staff_delete'
      AND cmd = 'DELETE'
      AND qual ILIKE '%has_staff_role%'
  ) THEN
    RAISE EXCEPTION 'missing or weak vehicle_artwork_staff_delete policy';
  END IF;

  -- Authenticated retail customer must not INSERT marketing artwork.
  PERFORM set_config('request.jwt.claim.sub', v_customer::text, true);
  PERFORM set_config(
    'request.jwt.claims',
    json_build_object('sub', v_customer::text, 'role', 'authenticated')::text,
    true
  );
  PERFORM set_config('request.jwt.claim.role', 'authenticated', true);
  SET LOCAL ROLE authenticated;
  BEGIN
    INSERT INTO storage.objects (bucket_id, name, owner, owner_id, metadata)
    VALUES (
      'vehicle-artwork',
      'nissan_navara_d23.webp',
      v_customer,
      v_customer::text,
      '{}'::jsonb
    );
    RAISE EXCEPTION 'smoke fail: customer insert should fail';
  EXCEPTION
    WHEN insufficient_privilege OR check_violation OR OTHERS THEN
      IF SQLERRM LIKE 'smoke fail:%' THEN RAISE; END IF;
  END;
  RESET ROLE;

  RAISE NOTICE 'vehicle-artwork storage smoke OK';
END;
$$;
