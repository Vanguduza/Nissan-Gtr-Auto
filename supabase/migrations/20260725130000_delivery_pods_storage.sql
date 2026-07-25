-- Private Storage bucket for delivery POD photo + signature objects.
-- Plan: docs/plans/2026-07-25-dedicated-delivery-app.md (POD bridges)
-- Paths stored on delivery_jobs.pod_photo_path / pod_signature_path are object
-- keys inside this bucket (no bucket prefix).
-- Convention:
--   {delivery_job_id}/photo.jpg       (or .jpeg / .png / .webp)
--   {delivery_job_id}/signature.png   (or .jpg / .webp)
-- Exclusions: no ZIMRA; customers must not read POD media.

-- ---------------------------------------------------------------------------
-- Helpers (path → job; write/select gates)
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public._delivery_pod_job_id_from_path(p_name TEXT)
RETURNS UUID
LANGUAGE plpgsql
IMMUTABLE
AS $$
DECLARE
  v_seg TEXT;
  v_id UUID;
BEGIN
  IF p_name IS NULL OR length(trim(p_name)) = 0 THEN
    RETURN NULL;
  END IF;
  -- Strip accidental bucket prefix if a client passes "delivery-pods/..."
  v_seg := trim(both '/' FROM p_name);
  IF split_part(v_seg, '/', 1) = 'delivery-pods' THEN
    v_seg := substr(v_seg, length('delivery-pods/') + 1);
  END IF;
  v_seg := split_part(v_seg, '/', 1);
  BEGIN
    v_id := v_seg::uuid;
  EXCEPTION
    WHEN invalid_text_representation THEN
      RETURN NULL;
  END;
  RETURN v_id;
END;
$$;

CREATE OR REPLACE FUNCTION public._can_write_delivery_pod_object(p_name TEXT)
RETURNS BOOLEAN
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
  SELECT
    auth.role() = 'service_role'
    OR (
      public.has_staff_role(
        ARRAY['admin', 'warehouse', 'dispatcher']::public.staff_role[]
      )
      AND public._delivery_pod_job_id_from_path(p_name) IS NOT NULL
    )
    OR EXISTS (
      SELECT 1
      FROM public.delivery_jobs dj
      WHERE dj.id = public._delivery_pod_job_id_from_path(p_name)
        AND dj.assignee_user_id = auth.uid()
        AND public.has_staff_role(ARRAY['driver']::public.staff_role[])
        AND dj.status IN ('pending', 'dispatched')
    );
$$;

CREATE OR REPLACE FUNCTION public._can_select_delivery_pod_object(p_name TEXT)
RETURNS BOOLEAN
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
  SELECT
    auth.role() = 'service_role'
    OR (
      public.has_staff_role(
        ARRAY['admin', 'warehouse', 'dispatcher']::public.staff_role[]
      )
      AND public._delivery_pod_job_id_from_path(p_name) IS NOT NULL
    )
    -- Assigned driver may read own job objects (upload RETURNING / retries).
    OR EXISTS (
      SELECT 1
      FROM public.delivery_jobs dj
      WHERE dj.id = public._delivery_pod_job_id_from_path(p_name)
        AND dj.assignee_user_id = auth.uid()
        AND public.has_staff_role(ARRAY['driver']::public.staff_role[])
    );
$$;

COMMENT ON FUNCTION public._delivery_pod_job_id_from_path(TEXT) IS
  'Parses delivery-pods object key; first path segment must be delivery_job_id UUID.';
COMMENT ON FUNCTION public._can_write_delivery_pod_object(TEXT) IS
  'Assigned driver (non-terminal job) or dispatcher/admin/warehouse may INSERT/UPDATE.';
COMMENT ON FUNCTION public._can_select_delivery_pod_object(TEXT) IS
  'dispatcher/admin/warehouse or assigned driver may SELECT; customers denied.';

-- ---------------------------------------------------------------------------
-- Bucket
-- ---------------------------------------------------------------------------
INSERT INTO storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
VALUES (
  'delivery-pods',
  'delivery-pods',
  false,
  10485760, -- 10 MiB
  ARRAY[
    'image/jpeg',
    'image/jpg',
    'image/png',
    'image/webp'
  ]
)
ON CONFLICT (id) DO UPDATE
SET
  public = EXCLUDED.public,
  file_size_limit = EXCLUDED.file_size_limit,
  allowed_mime_types = EXCLUDED.allowed_mime_types;

-- ---------------------------------------------------------------------------
-- RLS policies on storage.objects
-- ---------------------------------------------------------------------------
DROP POLICY IF EXISTS delivery_pods_storage_select ON storage.objects;
CREATE POLICY delivery_pods_storage_select
  ON storage.objects FOR SELECT
  TO authenticated
  USING (
    bucket_id = 'delivery-pods'
    AND public._can_select_delivery_pod_object(name)
  );

DROP POLICY IF EXISTS delivery_pods_storage_insert ON storage.objects;
CREATE POLICY delivery_pods_storage_insert
  ON storage.objects FOR INSERT
  TO authenticated
  WITH CHECK (
    bucket_id = 'delivery-pods'
    AND public._can_write_delivery_pod_object(name)
  );

DROP POLICY IF EXISTS delivery_pods_storage_update ON storage.objects;
CREATE POLICY delivery_pods_storage_update
  ON storage.objects FOR UPDATE
  TO authenticated
  USING (
    bucket_id = 'delivery-pods'
    AND public._can_write_delivery_pod_object(name)
  )
  WITH CHECK (
    bucket_id = 'delivery-pods'
    AND public._can_write_delivery_pod_object(name)
  );

DROP POLICY IF EXISTS delivery_pods_storage_delete ON storage.objects;
CREATE POLICY delivery_pods_storage_delete
  ON storage.objects FOR DELETE
  TO authenticated
  USING (
    bucket_id = 'delivery-pods'
    AND public.has_staff_role(ARRAY['admin']::public.staff_role[])
  );

-- No anon policies → customers / share-token holders cannot read POD media via Storage.

REVOKE ALL ON FUNCTION public._delivery_pod_job_id_from_path(TEXT) FROM PUBLIC;
REVOKE ALL ON FUNCTION public._can_write_delivery_pod_object(TEXT) FROM PUBLIC;
REVOKE ALL ON FUNCTION public._can_select_delivery_pod_object(TEXT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public._delivery_pod_job_id_from_path(TEXT)
  TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public._can_write_delivery_pod_object(TEXT)
  TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public._can_select_delivery_pod_object(TEXT)
  TO authenticated, service_role;
