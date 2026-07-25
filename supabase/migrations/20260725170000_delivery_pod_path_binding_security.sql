-- Security harden: bind POD paths to job + delivery-pods objects;
-- driver_presence write requires driver role; POD OTP uses CSPRNG.
-- Exclusions: no ZIMRA / payroll tax.

-- ---------------------------------------------------------------------------
-- Path normalize + storage existence check
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public._normalize_delivery_pod_object_path(p_path TEXT)
RETURNS TEXT
LANGUAGE plpgsql
IMMUTABLE
AS $$
DECLARE
  v_path TEXT;
BEGIN
  IF p_path IS NULL OR length(trim(p_path)) = 0 THEN
    RETURN NULL;
  END IF;
  v_path := trim(both FROM p_path);
  v_path := trim(both '/' FROM v_path);
  -- Reject path traversal / absolute-looking segments
  IF v_path ~ '\.\.' OR v_path ~ '//' OR position('\' IN v_path) > 0 THEN
    RAISE EXCEPTION 'invalid POD storage path';
  END IF;
  IF split_part(v_path, '/', 1) = 'delivery-pods' THEN
    v_path := substr(v_path, length('delivery-pods/') + 1);
    v_path := trim(both '/' FROM v_path);
  END IF;
  IF v_path IS NULL OR length(v_path) = 0 OR position('/' IN v_path) = 0 THEN
    RAISE EXCEPTION 'POD path must be {job_id}/{filename} under delivery-pods';
  END IF;
  RETURN v_path;
END;
$$;

CREATE OR REPLACE FUNCTION public._assert_delivery_pod_object_for_job(
  p_delivery_job_id UUID,
  p_path TEXT,
  p_kind TEXT -- 'photo' | 'signature'
)
RETURNS TEXT
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_path TEXT;
  v_prefix TEXT;
  v_fname TEXT;
BEGIN
  v_path := public._normalize_delivery_pod_object_path(p_path);
  v_prefix := p_delivery_job_id::text || '/';

  IF v_path IS NULL OR left(v_path, length(v_prefix)) IS DISTINCT FROM v_prefix THEN
    RAISE EXCEPTION
      'POD % path must start with % (got %)',
      p_kind, v_prefix, COALESCE(p_path, '<null>');
  END IF;

  v_fname := substr(v_path, length(v_prefix) + 1);
  IF v_fname IS NULL OR length(v_fname) = 0 OR position('/' IN v_fname) > 0 THEN
    RAISE EXCEPTION 'POD % path must be a single file under job folder', p_kind;
  END IF;

  IF p_kind = 'photo' THEN
    IF v_fname !~* '^photo\.(jpe?g|png|webp)$' THEN
      RAISE EXCEPTION
        'POD photo filename must be photo.jpg|jpeg|png|webp (got %)', v_fname;
    END IF;
  ELSIF p_kind = 'signature' THEN
    IF v_fname !~* '^signature\.(jpe?g|png|webp)$' THEN
      RAISE EXCEPTION
        'POD signature filename must be signature.jpg|jpeg|png|webp (got %)',
        v_fname;
    END IF;
  ELSE
    RAISE EXCEPTION 'unknown POD kind %', p_kind;
  END IF;

  IF NOT EXISTS (
    SELECT 1
    FROM storage.objects o
    WHERE o.bucket_id = 'delivery-pods'
      AND o.name = v_path
  ) THEN
    RAISE EXCEPTION
      'POD % object missing in delivery-pods bucket at %', p_kind, v_path;
  END IF;

  RETURN v_path;
END;
$$;

COMMENT ON FUNCTION public._assert_delivery_pod_object_for_job(UUID, TEXT, TEXT) IS
  'Binds POD path to {job_id}/photo|signature.* and requires storage.objects row '
  'in private delivery-pods bucket.';

-- ---------------------------------------------------------------------------
-- submit_delivery_pod — path bind + object existence
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.submit_delivery_pod(
  p_delivery_job_id UUID,
  p_pod_photo_path TEXT,
  p_pod_signature_path TEXT,
  p_otp_code TEXT,
  p_notes TEXT DEFAULT NULL
)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_job public.delivery_jobs%ROWTYPE;
  v_uid UUID := auth.uid();
  v_allowed BOOLEAN := false;
  v_photo TEXT;
  v_sig TEXT;
BEGIN
  PERFORM public._logistics_begin_rpc();

  IF p_pod_photo_path IS NULL OR length(trim(p_pod_photo_path)) = 0
     OR p_pod_signature_path IS NULL OR length(trim(p_pod_signature_path)) = 0 THEN
    RAISE EXCEPTION 'pod_photo_path and pod_signature_path are required';
  END IF;

  IF p_otp_code IS NULL OR length(trim(p_otp_code)) = 0 THEN
    RAISE EXCEPTION 'POD OTP code required; use generate_delivery_pod_otp then verify';
  END IF;

  SELECT * INTO v_job FROM public.delivery_jobs WHERE id = p_delivery_job_id FOR UPDATE;
  IF NOT FOUND THEN
    RAISE EXCEPTION 'delivery job not found';
  END IF;
  IF v_job.status IN ('completed', 'failed') THEN
    RAISE EXCEPTION 'terminal delivery job cannot accept POD';
  END IF;
  IF v_job.status <> 'dispatched' THEN
    RAISE EXCEPTION 'POD requires dispatched job (status=%)', v_job.status;
  END IF;

  IF auth.role() = 'service_role'
     OR public.has_staff_role(
       ARRAY['admin', 'warehouse', 'dispatcher']::public.staff_role[]
     ) THEN
    v_allowed := true;
  ELSIF v_uid IS NOT NULL
        AND v_job.assignee_user_id = v_uid
        AND public.has_staff_role(ARRAY['driver']::public.staff_role[]) THEN
    v_allowed := true;
  END IF;

  IF NOT v_allowed THEN
    RAISE EXCEPTION 'assigned driver or dispatcher/warehouse/admin required for POD';
  END IF;

  -- High: bind paths to this job + require objects in delivery-pods
  v_photo := public._assert_delivery_pod_object_for_job(
    p_delivery_job_id, p_pod_photo_path, 'photo'
  );
  v_sig := public._assert_delivery_pod_object_for_job(
    p_delivery_job_id, p_pod_signature_path, 'signature'
  );

  -- Verify OTP (raises on failure); unlocks complete
  PERFORM public.verify_delivery_pod_otp(p_delivery_job_id, p_otp_code);

  UPDATE public.delivery_jobs
  SET
    pod_photo_path = v_photo,
    pod_signature_path = v_sig,
    notes = COALESCE(p_notes, notes),
    status = 'completed',
    completed_at = now(),
    completed_via = 'pod',
    updated_at = now()
  WHERE id = p_delivery_job_id;

  UPDATE public.delivery_track_tokens
  SET revoked_at = COALESCE(revoked_at, now())
  WHERE delivery_job_id = p_delivery_job_id
    AND revoked_at IS NULL;

  PERFORM public.emit_domain_event(
    'delivery_completed',
    'delivery_job:completed:' || p_delivery_job_id::text,
    jsonb_build_object(
      'delivery_job_id', p_delivery_job_id,
      'delivery_note_id', v_job.delivery_note_id,
      'status', 'completed',
      'completed_via', 'pod'
    )
  );

  RETURN p_delivery_job_id;
END;
$$;

-- ---------------------------------------------------------------------------
-- Medium: driver_presence INSERT/UPDATE require driver role
-- ---------------------------------------------------------------------------
DROP POLICY IF EXISTS driver_presence_self_insert ON public.driver_presence;
CREATE POLICY driver_presence_self_insert ON public.driver_presence
  FOR INSERT TO authenticated
  WITH CHECK (
    user_id = auth.uid()
    AND public.has_staff_role(ARRAY['driver']::public.staff_role[])
  );

DROP POLICY IF EXISTS driver_presence_self_update ON public.driver_presence;
CREATE POLICY driver_presence_self_update ON public.driver_presence
  FOR UPDATE TO authenticated
  USING (
    user_id = auth.uid()
    AND public.has_staff_role(ARRAY['driver']::public.staff_role[])
  )
  WITH CHECK (
    user_id = auth.uid()
    AND public.has_staff_role(ARRAY['driver']::public.staff_role[])
  );

-- ---------------------------------------------------------------------------
-- Medium: POD OTP via CSPRNG (extensions.gen_random_bytes)
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.generate_delivery_pod_otp(
  p_delivery_job_id UUID,
  p_ttl INTERVAL DEFAULT interval '15 minutes'
)
RETURNS TEXT
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, extensions
AS $$
DECLARE
  v_job public.delivery_jobs%ROWTYPE;
  v_uid UUID := auth.uid();
  v_allowed BOOLEAN := false;
  v_code TEXT;
  v_hash TEXT;
  v_bytes BYTEA;
  v_n BIGINT;
BEGIN
  PERFORM public._logistics_begin_rpc();

  SELECT * INTO v_job FROM public.delivery_jobs WHERE id = p_delivery_job_id FOR UPDATE;
  IF NOT FOUND THEN
    RAISE EXCEPTION 'delivery job not found';
  END IF;
  IF v_job.status <> 'dispatched' THEN
    RAISE EXCEPTION 'POD OTP requires dispatched job (status=%)', v_job.status;
  END IF;

  IF auth.role() = 'service_role'
     OR public.has_staff_role(
       ARRAY['admin', 'warehouse', 'dispatcher']::public.staff_role[]
     ) THEN
    v_allowed := true;
  ELSIF v_uid IS NOT NULL
        AND v_job.assignee_user_id = v_uid
        AND public.has_staff_role(ARRAY['driver']::public.staff_role[]) THEN
    v_allowed := true;
  END IF;

  IF NOT v_allowed THEN
    RAISE EXCEPTION 'assigned driver or dispatcher/warehouse/admin required for POD OTP';
  END IF;

  UPDATE public.delivery_pod_otps
  SET expires_at = least(expires_at, now())
  WHERE delivery_job_id = p_delivery_job_id
    AND verified_at IS NULL
    AND expires_at > now();

  -- Uniform 6-digit code from 4 CSPRNG bytes (not random())
  v_bytes := extensions.gen_random_bytes(4);
  v_n := (
    get_byte(v_bytes, 0)::bigint * 16777216
    + get_byte(v_bytes, 1)::bigint * 65536
    + get_byte(v_bytes, 2)::bigint * 256
    + get_byte(v_bytes, 3)::bigint
  ) % 1000000;
  v_code := lpad(v_n::text, 6, '0');
  v_hash := public._hash_delivery_pod_otp(v_code);

  INSERT INTO public.delivery_pod_otps (
    delivery_job_id, code_hash, expires_at
  )
  VALUES (
    p_delivery_job_id,
    v_hash,
    now() + COALESCE(p_ttl, interval '15 minutes')
  );

  -- Optional SMS to customer — never blocks OTP return to driver UI
  PERFORM public._enqueue_delivery_customer_sms(
    p_delivery_job_id,
    'delivery_pod_otp',
    'delivery_pod_otp:' || p_delivery_job_id::text || ':' || v_hash,
    format('GTR Auto: Your delivery confirmation code is %s. Do not share.', v_code)
  );

  RETURN v_code;
END;
$$;

REVOKE ALL ON FUNCTION public._normalize_delivery_pod_object_path(TEXT) FROM PUBLIC;
REVOKE ALL ON FUNCTION public._assert_delivery_pod_object_for_job(UUID, TEXT, TEXT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public._normalize_delivery_pod_object_path(TEXT)
  TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public._assert_delivery_pod_object_for_job(UUID, TEXT, TEXT)
  TO authenticated, service_role;

REVOKE ALL ON FUNCTION public.submit_delivery_pod(UUID, TEXT, TEXT, TEXT, TEXT) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.generate_delivery_pod_otp(UUID, INTERVAL) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.submit_delivery_pod(UUID, TEXT, TEXT, TEXT, TEXT)
  TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.generate_delivery_pod_otp(UUID, INTERVAL)
  TO authenticated, service_role;
