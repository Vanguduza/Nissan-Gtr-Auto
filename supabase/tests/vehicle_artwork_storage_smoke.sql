-- vehicle-artwork Storage RLS smoke (public read; staff write).
-- Run after migrations, e.g.:
--   docker exec -i <supabase_db> psql -U postgres -d postgres -v ON_ERROR_STOP=1 \
--     < supabase/tests/vehicle_artwork_storage_smoke.sql

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM storage.buckets WHERE id = 'vehicle-artwork' AND public IS TRUE
  ) THEN
    RAISE EXCEPTION 'vehicle-artwork bucket missing or not public';
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM pg_policies
    WHERE schemaname = 'storage'
      AND tablename = 'objects'
      AND policyname = 'vehicle_artwork_public_read'
  ) THEN
    RAISE EXCEPTION 'missing vehicle_artwork_public_read policy';
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM pg_policies
    WHERE schemaname = 'storage'
      AND tablename = 'objects'
      AND policyname = 'vehicle_artwork_staff_insert'
  ) THEN
    RAISE EXCEPTION 'missing vehicle_artwork_staff_insert policy';
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM pg_policies
    WHERE schemaname = 'storage'
      AND tablename = 'objects'
      AND policyname = 'vehicle_artwork_staff_update'
  ) THEN
    RAISE EXCEPTION 'missing vehicle_artwork_staff_update policy';
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM pg_policies
    WHERE schemaname = 'storage'
      AND tablename = 'objects'
      AND policyname = 'vehicle_artwork_staff_delete'
  ) THEN
    RAISE EXCEPTION 'missing vehicle_artwork_staff_delete policy';
  END IF;

  RAISE NOTICE 'vehicle-artwork storage smoke OK';
END;
$$;
