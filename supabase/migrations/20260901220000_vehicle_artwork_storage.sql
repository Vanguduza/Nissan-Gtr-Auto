-- Public Storage bucket for customer-app model-family vehicle artwork.
-- Presentation only: the Android resolver maps SelectedFitmentVehicle → filename.
-- Do not add a fitment table for these assets.
-- Public URL:
--   /storage/v1/object/public/vehicle-artwork/<filename>.webp

INSERT INTO storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
VALUES (
  'vehicle-artwork',
  'vehicle-artwork',
  true,
  5242880,
  ARRAY['image/webp']
)
ON CONFLICT (id) DO UPDATE SET
  public = EXCLUDED.public,
  file_size_limit = EXCLUDED.file_size_limit,
  allowed_mime_types = EXCLUDED.allowed_mime_types;

DROP POLICY IF EXISTS vehicle_artwork_public_read ON storage.objects;
CREATE POLICY vehicle_artwork_public_read
  ON storage.objects FOR SELECT
  TO public
  USING (bucket_id = 'vehicle-artwork');

DROP POLICY IF EXISTS vehicle_artwork_staff_insert ON storage.objects;
CREATE POLICY vehicle_artwork_staff_insert
  ON storage.objects FOR INSERT
  TO authenticated
  WITH CHECK (
    bucket_id = 'vehicle-artwork'
    AND name ~ '^nissan_[a-z0-9_]+\.webp$'
    AND public.has_staff_role(ARRAY['admin', 'warehouse']::public.staff_role[])
  );

DROP POLICY IF EXISTS vehicle_artwork_staff_update ON storage.objects;
CREATE POLICY vehicle_artwork_staff_update
  ON storage.objects FOR UPDATE
  TO authenticated
  USING (
    bucket_id = 'vehicle-artwork'
    AND name ~ '^nissan_[a-z0-9_]+\.webp$'
    AND public.has_staff_role(ARRAY['admin', 'warehouse']::public.staff_role[])
  )
  WITH CHECK (
    bucket_id = 'vehicle-artwork'
    AND name ~ '^nissan_[a-z0-9_]+\.webp$'
    AND public.has_staff_role(ARRAY['admin', 'warehouse']::public.staff_role[])
  );

DROP POLICY IF EXISTS vehicle_artwork_staff_delete ON storage.objects;
CREATE POLICY vehicle_artwork_staff_delete
  ON storage.objects FOR DELETE
  TO authenticated
  USING (
    bucket_id = 'vehicle-artwork'
    AND name ~ '^nissan_[a-z0-9_]+\.webp$'
    AND public.has_staff_role(ARRAY['admin', 'warehouse']::public.staff_role[])
  );
