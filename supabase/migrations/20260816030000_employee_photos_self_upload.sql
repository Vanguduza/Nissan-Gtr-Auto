-- Staff My Account: self ID-photo upload to Storage + profile path update.
-- Web file input only (no HTML5 QR/camera). Bridge remains SoR on Android.
-- No ZIMRA / payroll tax.

COMMENT ON COLUMN public.employees.photo_storage_path IS
  'Storage object key in employee-photos bucket; staff may set via update_my_staff_profile.';

INSERT INTO storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
VALUES (
  'employee-photos',
  'employee-photos',
  false,
  5242880,
  ARRAY['image/jpeg', 'image/png', 'image/webp']
)
ON CONFLICT (id) DO NOTHING;

-- Path convention: {employee_id}/{filename}
DROP POLICY IF EXISTS employee_photos_storage_select ON storage.objects;
CREATE POLICY employee_photos_storage_select
  ON storage.objects FOR SELECT
  TO authenticated
  USING (
    bucket_id = 'employee-photos'
    AND (
      public.has_staff_role(ARRAY['admin', 'hr']::public.staff_role[])
      OR EXISTS (
        SELECT 1
        FROM public.employees e
        WHERE e.user_id = auth.uid()
          AND name LIKE (e.id::text || '/%')
      )
    )
  );

DROP POLICY IF EXISTS employee_photos_storage_insert ON storage.objects;
CREATE POLICY employee_photos_storage_insert
  ON storage.objects FOR INSERT
  TO authenticated
  WITH CHECK (
    bucket_id = 'employee-photos'
    AND (
      public.has_staff_role(ARRAY['admin', 'hr']::public.staff_role[])
      OR EXISTS (
        SELECT 1
        FROM public.employees e
        WHERE e.user_id = auth.uid()
          AND name LIKE (e.id::text || '/%')
      )
    )
  );

DROP POLICY IF EXISTS employee_photos_storage_update ON storage.objects;
CREATE POLICY employee_photos_storage_update
  ON storage.objects FOR UPDATE
  TO authenticated
  USING (
    bucket_id = 'employee-photos'
    AND (
      public.has_staff_role(ARRAY['admin', 'hr']::public.staff_role[])
      OR EXISTS (
        SELECT 1
        FROM public.employees e
        WHERE e.user_id = auth.uid()
          AND name LIKE (e.id::text || '/%')
      )
    )
  )
  WITH CHECK (
    bucket_id = 'employee-photos'
    AND (
      public.has_staff_role(ARRAY['admin', 'hr']::public.staff_role[])
      OR EXISTS (
        SELECT 1
        FROM public.employees e
        WHERE e.user_id = auth.uid()
          AND name LIKE (e.id::text || '/%')
      )
    )
  );

DROP POLICY IF EXISTS employee_photos_storage_delete ON storage.objects;
CREATE POLICY employee_photos_storage_delete
  ON storage.objects FOR DELETE
  TO authenticated
  USING (
    bucket_id = 'employee-photos'
    AND (
      public.has_staff_role(ARRAY['admin', 'hr']::public.staff_role[])
      OR EXISTS (
        SELECT 1
        FROM public.employees e
        WHERE e.user_id = auth.uid()
          AND name LIKE (e.id::text || '/%')
      )
    )
  );

CREATE OR REPLACE FUNCTION public.update_my_staff_profile(
  p_phone_e164 TEXT DEFAULT NULL,
  p_address TEXT DEFAULT NULL,
  p_email TEXT DEFAULT NULL,
  p_photo_storage_path TEXT DEFAULT NULL
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_emp_id UUID;
  v_phone TEXT;
  v_address TEXT;
  v_email TEXT;
  v_photo TEXT;
BEGIN
  IF auth.uid() IS NULL THEN
    RAISE EXCEPTION 'not authenticated';
  END IF;

  SELECT id INTO v_emp_id
  FROM public.employees
  WHERE user_id = auth.uid()
  LIMIT 1;

  IF v_emp_id IS NULL THEN
    RAISE EXCEPTION 'no employee linked to this user';
  END IF;

  v_phone := nullif(trim(COALESCE(p_phone_e164, '')), '');
  v_address := nullif(trim(COALESCE(p_address, '')), '');
  v_email := nullif(lower(trim(COALESCE(p_email, ''))), '');
  v_photo := nullif(trim(COALESCE(p_photo_storage_path, '')), '');

  IF v_email IS NOT NULL AND v_email !~ '^[^@\s]+@[^@\s]+\.[^@\s]+$' THEN
    RAISE EXCEPTION 'invalid email';
  END IF;

  IF p_photo_storage_path IS NOT NULL AND v_photo IS NOT NULL THEN
    IF v_photo !~ ('^' || v_emp_id::text || '/') THEN
      RAISE EXCEPTION 'photo path must be under own employee id';
    END IF;
  END IF;

  UPDATE public.employees
  SET
    phone_e164 = CASE WHEN p_phone_e164 IS NULL THEN phone_e164 ELSE v_phone END,
    address = CASE WHEN p_address IS NULL THEN address ELSE v_address END,
    email = CASE WHEN p_email IS NULL THEN email ELSE v_email END,
    photo_storage_path = CASE
      WHEN p_photo_storage_path IS NULL THEN photo_storage_path
      ELSE v_photo
    END,
    updated_at = now()
  WHERE id = v_emp_id
    AND user_id = auth.uid();

  IF p_phone_e164 IS NOT NULL THEN
    UPDATE public.profiles
    SET phone_e164 = v_phone, updated_at = now()
    WHERE id = auth.uid();
  END IF;

  RETURN public.get_my_staff_profile();
END;
$$;

COMMENT ON FUNCTION public.update_my_staff_profile(TEXT, TEXT, TEXT, TEXT) IS
  'Staff may update own phone/address/email/photo path on employees (+ profiles.phone). '
  'Does not change grade, hr_role, wages, or staff_roles. Email auth change is client GoTrue.';

-- Replace 3-arg overload from 20260816020000 with 4-arg (photo path).
DROP FUNCTION IF EXISTS public.update_my_staff_profile(TEXT, TEXT, TEXT);

REVOKE ALL ON FUNCTION public.update_my_staff_profile(TEXT, TEXT, TEXT, TEXT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.update_my_staff_profile(TEXT, TEXT, TEXT, TEXT)
  TO authenticated, service_role;
