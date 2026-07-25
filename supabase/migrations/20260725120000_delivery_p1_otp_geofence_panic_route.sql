-- Dedicated delivery app P1: POD OTP, geofence suggestions, fail/reattempt,
-- panic_events, optimize_driver_stops, out-for-delivery customer SMS enqueue.
-- Plan: docs/plans/2026-07-25-dedicated-delivery-app.md
-- ADR: docs/decisions/2026-07-25-dedicated-delivery-app.md
-- Extends 20260725110000_dedicated_delivery_app.sql.
-- Exclusions: no ZIMRA, no payroll tax, no browser GPS, no auto status mutate.

-- ---------------------------------------------------------------------------
-- Enums / catalog
-- ---------------------------------------------------------------------------
DO $$
BEGIN
  CREATE TYPE public.delivery_failure_reason AS ENUM (
    'customer_absent',
    'refused',
    'wrong_address',
    'damaged',
    'other'
  );
EXCEPTION
  WHEN duplicate_object THEN NULL;
END;
$$;

INSERT INTO public.sms_event_catalog (code, description, category, priority) VALUES
  ('delivery_pod_otp', 'POD one-time code for customer delivery confirmation', 'logistics', 'high'),
  ('delivery_out_for_delivery', 'Customer notify: out for delivery + track link', 'logistics', 'normal')
ON CONFLICT (code) DO NOTHING;

-- ---------------------------------------------------------------------------
-- delivery_jobs: failure code, reattempt link, route sequence
-- ---------------------------------------------------------------------------
ALTER TABLE public.delivery_jobs
  ADD COLUMN IF NOT EXISTS failure_reason_code public.delivery_failure_reason,
  ADD COLUMN IF NOT EXISTS reattempt_of UUID REFERENCES public.delivery_jobs (id) ON DELETE SET NULL,
  ADD COLUMN IF NOT EXISTS route_sequence INTEGER CHECK (route_sequence IS NULL OR route_sequence >= 1);

CREATE INDEX IF NOT EXISTS delivery_jobs_reattempt_of_idx
  ON public.delivery_jobs (reattempt_of)
  WHERE reattempt_of IS NOT NULL;

CREATE INDEX IF NOT EXISTS delivery_jobs_assignee_route_idx
  ON public.delivery_jobs (assignee_user_id, route_sequence)
  WHERE status IN ('pending', 'dispatched');

COMMENT ON COLUMN public.delivery_jobs.failure_reason IS
  'Optional free-text failure detail; structured code lives in failure_reason_code.';
COMMENT ON COLUMN public.delivery_jobs.failure_reason_code IS
  'Stable failure reason enum for fail/reattempt workflows.';
COMMENT ON COLUMN public.delivery_jobs.reattempt_of IS
  'Parent failed job when this row is a reattempt child (pending until reassigned).';
COMMENT ON COLUMN public.delivery_jobs.route_sequence IS
  'Optional stop order from optimize_driver_stops (nearest-neighbor).';

-- ---------------------------------------------------------------------------
-- delivery_pod_otps — hash only; RPC access (no client SELECT)
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.delivery_pod_otps (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  delivery_job_id UUID NOT NULL REFERENCES public.delivery_jobs (id) ON DELETE CASCADE,
  code_hash TEXT NOT NULL,
  expires_at TIMESTAMPTZ NOT NULL,
  attempts INTEGER NOT NULL DEFAULT 0 CHECK (attempts >= 0),
  max_attempts INTEGER NOT NULL DEFAULT 5 CHECK (max_attempts >= 1),
  verified_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT delivery_pod_otps_hash_nonempty CHECK (length(trim(code_hash)) > 0)
);

CREATE INDEX IF NOT EXISTS delivery_pod_otps_job_idx
  ON public.delivery_pod_otps (delivery_job_id, created_at DESC);

COMMENT ON TABLE public.delivery_pod_otps IS
  'POD OTP hashes only. Plaintext returned once from generate_delivery_pod_otp; no direct SELECT.';

ALTER TABLE public.delivery_pod_otps ENABLE ROW LEVEL SECURITY;

REVOKE ALL ON TABLE public.delivery_pod_otps FROM PUBLIC, anon, authenticated;
GRANT ALL ON TABLE public.delivery_pod_otps TO service_role;

-- ---------------------------------------------------------------------------
-- panic_events
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.panic_events (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  driver_user_id UUID NOT NULL REFERENCES public.profiles (id) ON DELETE CASCADE,
  delivery_job_id UUID REFERENCES public.delivery_jobs (id) ON DELETE SET NULL,
  lat DOUBLE PRECISION CHECK (lat IS NULL OR lat BETWEEN -90 AND 90),
  lng DOUBLE PRECISION CHECK (lng IS NULL OR lng BETWEEN -180 AND 180),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  acknowledged_at TIMESTAMPTZ,
  acknowledged_by UUID REFERENCES public.profiles (id) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS panic_events_created_idx
  ON public.panic_events (created_at DESC);

CREATE INDEX IF NOT EXISTS panic_events_unacked_idx
  ON public.panic_events (created_at DESC)
  WHERE acknowledged_at IS NULL;

CREATE INDEX IF NOT EXISTS panic_events_driver_idx
  ON public.panic_events (driver_user_id, created_at DESC);

COMMENT ON TABLE public.panic_events IS
  'Driver panic / SOS. Driver insert own; dispatcher/admin/warehouse read + ack.';

ALTER TABLE public.panic_events ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS panic_events_driver_insert ON public.panic_events;
CREATE POLICY panic_events_driver_insert ON public.panic_events
  FOR INSERT TO authenticated
  WITH CHECK (
    driver_user_id = auth.uid()
    AND public.has_staff_role(ARRAY['driver']::public.staff_role[])
  );

DROP POLICY IF EXISTS panic_events_driver_select_own ON public.panic_events;
CREATE POLICY panic_events_driver_select_own ON public.panic_events
  FOR SELECT TO authenticated
  USING (
    driver_user_id = auth.uid()
    OR public.has_staff_role(
      ARRAY['admin', 'warehouse', 'dispatcher']::public.staff_role[]
    )
  );

DROP POLICY IF EXISTS panic_events_staff_ack ON public.panic_events;
CREATE POLICY panic_events_staff_ack ON public.panic_events
  FOR UPDATE TO authenticated
  USING (
    public.has_staff_role(
      ARRAY['admin', 'warehouse', 'dispatcher']::public.staff_role[]
    )
  )
  WITH CHECK (
    public.has_staff_role(
      ARRAY['admin', 'warehouse', 'dispatcher']::public.staff_role[]
    )
  );

GRANT SELECT, INSERT, UPDATE ON TABLE public.panic_events
  TO authenticated, service_role;

-- Realtime for dispatcher panic inbox (same pattern as delivery_locations)
DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM pg_publication WHERE pubname = 'supabase_realtime') THEN
    BEGIN
      ALTER PUBLICATION supabase_realtime ADD TABLE public.panic_events;
    EXCEPTION
      WHEN duplicate_object THEN NULL;
    END;
  END IF;
END;
$$;

-- ---------------------------------------------------------------------------
-- Helpers
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public._hash_delivery_pod_otp(p_code TEXT)
RETURNS TEXT
LANGUAGE sql
IMMUTABLE
AS $$
  SELECT encode(digest(convert_to(trim(p_code), 'UTF8'), 'sha256'), 'hex');
$$;

CREATE OR REPLACE FUNCTION public._delivery_job_customer_contact(p_delivery_job_id UUID)
RETURNS TABLE (
  profile_id UUID,
  phone_e164 TEXT,
  whatsapp_e164 TEXT,
  sales_invoice_id UUID
)
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
  SELECT
    c.profile_id,
    NULLIF(trim(COALESCE(si.customer_phone_e164, c.phone_e164)), ''),
    NULLIF(trim(COALESCE(si.customer_whatsapp_e164, c.whatsapp_e164, si.customer_phone_e164, c.phone_e164)), ''),
    si.id
  FROM public.delivery_jobs dj
  JOIN public.delivery_notes dn ON dn.id = dj.delivery_note_id
  JOIN public.sales_invoices si ON si.id = dn.sales_invoice_id
  LEFT JOIN public.customers c ON c.id = si.customer_id
  WHERE dj.id = p_delivery_job_id
  LIMIT 1;
$$;

-- Best-effort customer SMS via sms_outbox. Never raises to callers (fail closed).
-- Requires phone + profile_id (sms_outbox.recipient_user_id NOT NULL).
CREATE OR REPLACE FUNCTION public._enqueue_delivery_customer_sms(
  p_delivery_job_id UUID,
  p_event_code TEXT,
  p_dedupe_key TEXT,
  p_body TEXT
)
RETURNS BOOLEAN
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_contact RECORD;
  v_event_id UUID;
BEGIN
  IF p_event_code IS NULL OR length(trim(p_event_code)) = 0 THEN
    RETURN false;
  END IF;
  IF p_body IS NULL OR length(trim(p_body)) = 0 THEN
    RETURN false;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM public.sms_event_catalog c
    WHERE c.code = p_event_code AND c.is_active
  ) THEN
    RETURN false;
  END IF;

  SELECT * INTO v_contact
  FROM public._delivery_job_customer_contact(p_delivery_job_id);

  IF NOT FOUND
     OR v_contact.phone_e164 IS NULL
     OR v_contact.profile_id IS NULL THEN
    RETURN false;
  END IF;

  INSERT INTO public.domain_events (event_code, dedupe_key, payload, actor_user_id)
  VALUES (
    p_event_code,
    p_dedupe_key,
    jsonb_build_object(
      'delivery_job_id', p_delivery_job_id,
      'sales_invoice_id', v_contact.sales_invoice_id
    ),
    auth.uid()
  )
  ON CONFLICT (event_code, dedupe_key) DO NOTHING
  RETURNING id INTO v_event_id;

  IF v_event_id IS NULL THEN
    SELECT id INTO v_event_id
    FROM public.domain_events
    WHERE event_code = p_event_code AND dedupe_key = p_dedupe_key;
  END IF;

  IF v_event_id IS NULL THEN
    RETURN false;
  END IF;

  INSERT INTO public.sms_outbox (
    domain_event_id, event_code, recipient_user_id, phone_e164, body
  )
  VALUES (
    v_event_id,
    p_event_code,
    v_contact.profile_id,
    v_contact.phone_e164,
    trim(p_body)
  )
  ON CONFLICT (domain_event_id, recipient_user_id) DO NOTHING;

  RETURN true;
EXCEPTION
  WHEN OTHERS THEN
    RETURN false;
END;
$$;

CREATE OR REPLACE FUNCTION public._notify_out_for_delivery(
  p_delivery_job_id UUID,
  p_track_token TEXT
)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_body TEXT;
BEGIN
  IF p_track_token IS NULL OR length(trim(p_track_token)) = 0 THEN
    RETURN;
  END IF;

  -- Deep-link placeholder; web client binds /track/[token]. Worker sends when SMS keys present.
  v_body := format(
    'GTR Auto: Your order is out for delivery. Track: /track/%s',
    trim(p_track_token)
  );

  PERFORM public._enqueue_delivery_customer_sms(
    p_delivery_job_id,
    'delivery_out_for_delivery',
    'delivery_out_for_delivery:' || p_delivery_job_id::text,
    v_body
  );
END;
$$;

-- ---------------------------------------------------------------------------
-- POD OTP generate / verify
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.generate_delivery_pod_otp(
  p_delivery_job_id UUID,
  p_ttl INTERVAL DEFAULT interval '15 minutes'
)
RETURNS TEXT
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_job public.delivery_jobs%ROWTYPE;
  v_uid UUID := auth.uid();
  v_allowed BOOLEAN := false;
  v_code TEXT;
  v_hash TEXT;
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

  -- Expire prior unverified codes for this job
  UPDATE public.delivery_pod_otps
  SET expires_at = least(expires_at, now())
  WHERE delivery_job_id = p_delivery_job_id
    AND verified_at IS NULL
    AND expires_at > now();

  v_code := lpad((floor(random() * 1000000))::integer::text, 6, '0');
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

CREATE OR REPLACE FUNCTION public.verify_delivery_pod_otp(
  p_delivery_job_id UUID,
  p_code TEXT
)
RETURNS BOOLEAN
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_job public.delivery_jobs%ROWTYPE;
  v_uid UUID := auth.uid();
  v_allowed BOOLEAN := false;
  v_otp public.delivery_pod_otps%ROWTYPE;
  v_hash TEXT;
BEGIN
  PERFORM public._logistics_begin_rpc();

  IF p_code IS NULL OR length(trim(p_code)) = 0 THEN
    RAISE EXCEPTION 'OTP code required';
  END IF;

  SELECT * INTO v_job FROM public.delivery_jobs WHERE id = p_delivery_job_id FOR UPDATE;
  IF NOT FOUND THEN
    RAISE EXCEPTION 'delivery job not found';
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
    RAISE EXCEPTION 'assigned driver or dispatcher/warehouse/admin required to verify POD OTP';
  END IF;

  SELECT * INTO v_otp
  FROM public.delivery_pod_otps
  WHERE delivery_job_id = p_delivery_job_id
    AND verified_at IS NULL
    AND expires_at > now()
  ORDER BY created_at DESC
  LIMIT 1
  FOR UPDATE;

  IF NOT FOUND THEN
    RAISE EXCEPTION 'no active POD OTP for job';
  END IF;

  IF v_otp.attempts >= v_otp.max_attempts THEN
    RAISE EXCEPTION 'POD OTP max attempts exceeded';
  END IF;

  v_hash := public._hash_delivery_pod_otp(p_code);
  IF v_hash IS DISTINCT FROM v_otp.code_hash THEN
    UPDATE public.delivery_pod_otps
    SET attempts = attempts + 1
    WHERE id = v_otp.id;
    RAISE EXCEPTION 'invalid POD OTP';
  END IF;

  UPDATE public.delivery_pod_otps
  SET verified_at = now()
  WHERE id = v_otp.id;

  RETURN true;
END;
$$;

-- ---------------------------------------------------------------------------
-- Geofence suggestions (never auto-update status)
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.delivery_geofence_suggestion(
  p_delivery_job_id UUID,
  p_lat DOUBLE PRECISION,
  p_lng DOUBLE PRECISION,
  p_arrive_radius_m DOUBLE PRECISION DEFAULT 150,
  p_complete_radius_m DOUBLE PRECISION DEFAULT 50
)
RETURNS TABLE (
  suggest_arrive BOOLEAN,
  suggest_complete BOOLEAN,
  distance_m DOUBLE PRECISION
)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_job public.delivery_jobs%ROWTYPE;
  v_uid UUID := auth.uid();
  v_allowed BOOLEAN := false;
  v_dist DOUBLE PRECISION;
  v_arrive DOUBLE PRECISION := COALESCE(p_arrive_radius_m, 150);
  v_complete DOUBLE PRECISION := COALESCE(p_complete_radius_m, 50);
BEGIN
  IF p_lat IS NULL OR p_lng IS NULL THEN
    RAISE EXCEPTION 'lat and lng required';
  END IF;
  IF v_arrive < 0 OR v_complete < 0 THEN
    RAISE EXCEPTION 'geofence radii must be >= 0';
  END IF;
  -- Complete radius should not exceed arrive radius
  IF v_complete > v_arrive THEN
    v_complete := v_arrive;
  END IF;

  SELECT * INTO v_job FROM public.delivery_jobs WHERE id = p_delivery_job_id;
  IF NOT FOUND THEN
    RAISE EXCEPTION 'delivery job not found';
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
    RAISE EXCEPTION 'assigned driver or dispatcher/warehouse/admin required for geofence suggestion';
  END IF;

  IF v_job.dropoff_lat IS NULL OR v_job.dropoff_lng IS NULL THEN
    RETURN QUERY SELECT false, false, NULL::DOUBLE PRECISION;
    RETURN;
  END IF;

  v_dist := public._haversine_meters(
    p_lat, p_lng, v_job.dropoff_lat, v_job.dropoff_lng
  );

  RETURN QUERY SELECT
    (v_dist IS NOT NULL AND v_dist <= v_arrive),
    (v_dist IS NOT NULL AND v_dist <= v_complete),
    v_dist;
END;
$$;

-- ---------------------------------------------------------------------------
-- Fail + optional reattempt
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.fail_delivery_job(
  p_delivery_job_id UUID,
  p_reason public.delivery_failure_reason,
  p_notes TEXT DEFAULT NULL,
  p_create_reattempt BOOLEAN DEFAULT false
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
  v_child UUID;
  v_notes TEXT;
BEGIN
  PERFORM public._logistics_begin_rpc();

  IF p_reason IS NULL THEN
    RAISE EXCEPTION 'failure reason required';
  END IF;

  SELECT * INTO v_job FROM public.delivery_jobs WHERE id = p_delivery_job_id FOR UPDATE;
  IF NOT FOUND THEN
    RAISE EXCEPTION 'delivery job not found';
  END IF;
  IF v_job.status IN ('completed', 'failed') THEN
    RAISE EXCEPTION 'terminal delivery job cannot fail again (status=%)', v_job.status;
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
    RAISE EXCEPTION 'assigned driver or dispatcher/warehouse/admin required to fail job';
  END IF;

  v_notes := COALESCE(NULLIF(trim(p_notes), ''), v_job.notes);

  UPDATE public.delivery_jobs
  SET
    status = 'failed',
    failed_at = now(),
    failure_reason_code = p_reason,
    failure_reason = p_reason::text || CASE
      WHEN p_notes IS NOT NULL AND length(trim(p_notes)) > 0 THEN ': ' || trim(p_notes)
      ELSE ''
    END,
    notes = v_notes,
    updated_at = now()
  WHERE id = p_delivery_job_id;

  UPDATE public.delivery_track_tokens
  SET revoked_at = COALESCE(revoked_at, now())
  WHERE delivery_job_id = p_delivery_job_id
    AND revoked_at IS NULL;

  PERFORM public.emit_domain_event(
    'delivery_failed',
    'delivery_job:failed:' || p_delivery_job_id::text,
    jsonb_build_object(
      'delivery_job_id', p_delivery_job_id,
      'delivery_note_id', v_job.delivery_note_id,
      'status', 'failed',
      'failure_reason_code', p_reason::text
    )
  );

  IF COALESCE(p_create_reattempt, false) THEN
    INSERT INTO public.delivery_jobs (
      document_number,
      delivery_note_id,
      assignee_user_id,
      status,
      notes,
      created_by,
      pickup_lat,
      pickup_lng,
      dropoff_lat,
      dropoff_lng,
      reattempt_of
    )
    VALUES (
      public.next_series_value('DJ-'),
      v_job.delivery_note_id,
      NULL,
      'pending',
      'Reattempt of ' || COALESCE(v_job.document_number, p_delivery_job_id::text),
      auth.uid(),
      v_job.pickup_lat,
      v_job.pickup_lng,
      v_job.dropoff_lat,
      v_job.dropoff_lng,
      p_delivery_job_id
    )
    RETURNING id INTO v_child;

    RETURN v_child;
  END IF;

  RETURN p_delivery_job_id;
END;
$$;

-- ---------------------------------------------------------------------------
-- Panic
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.raise_delivery_panic(
  p_delivery_job_id UUID DEFAULT NULL,
  p_lat DOUBLE PRECISION DEFAULT NULL,
  p_lng DOUBLE PRECISION DEFAULT NULL
)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_uid UUID := auth.uid();
  v_id UUID;
  v_job public.delivery_jobs%ROWTYPE;
BEGIN
  IF v_uid IS NULL THEN
    RAISE EXCEPTION 'authentication required';
  END IF;

  IF NOT (
    auth.role() = 'service_role'
    OR public.has_staff_role(ARRAY['driver']::public.staff_role[])
  ) THEN
    RAISE EXCEPTION 'driver role required to raise panic';
  END IF;

  IF p_delivery_job_id IS NOT NULL THEN
    SELECT * INTO v_job FROM public.delivery_jobs WHERE id = p_delivery_job_id;
    IF NOT FOUND THEN
      RAISE EXCEPTION 'delivery job not found';
    END IF;
    IF auth.role() IS DISTINCT FROM 'service_role'
       AND v_job.assignee_user_id IS DISTINCT FROM v_uid THEN
      RAISE EXCEPTION 'panic job must be assigned to caller';
    END IF;
  END IF;

  INSERT INTO public.panic_events (
    driver_user_id, delivery_job_id, lat, lng
  )
  VALUES (
    v_uid, p_delivery_job_id, p_lat, p_lng
  )
  RETURNING id INTO v_id;

  RETURN v_id;
END;
$$;

-- ---------------------------------------------------------------------------
-- Multi-stop nearest-neighbor order
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.optimize_driver_stops(
  p_driver_user_id UUID
)
RETURNS TABLE (
  delivery_job_id UUID,
  route_sequence INTEGER,
  distance_m DOUBLE PRECISION
)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_lat DOUBLE PRECISION;
  v_lng DOUBLE PRECISION;
  v_cur_lat DOUBLE PRECISION;
  v_cur_lng DOUBLE PRECISION;
  v_seq INTEGER := 0;
  v_next UUID;
  v_next_dist DOUBLE PRECISION;
  v_next_lat DOUBLE PRECISION;
  v_next_lng DOUBLE PRECISION;
  v_remaining UUID[];
  v_id UUID;
  v_results UUID[] := ARRAY[]::UUID[];
  v_dists DOUBLE PRECISION[] := ARRAY[]::DOUBLE PRECISION[];
BEGIN
  PERFORM public._logistics_begin_rpc();

  IF p_driver_user_id IS NULL THEN
    RAISE EXCEPTION 'driver_user_id required';
  END IF;

  IF NOT (
    auth.role() = 'service_role'
    OR public.has_staff_role(
      ARRAY['admin', 'warehouse', 'dispatcher']::public.staff_role[]
    )
    OR (
      auth.uid() = p_driver_user_id
      AND public.has_staff_role(ARRAY['driver']::public.staff_role[])
    )
  ) THEN
    RAISE EXCEPTION 'dispatcher/warehouse/admin or self driver required for optimize_driver_stops';
  END IF;

  SELECT dp.last_lat, dp.last_lng
  INTO v_lat, v_lng
  FROM public.driver_presence dp
  WHERE dp.user_id = p_driver_user_id;

  SELECT array_agg(dj.id ORDER BY dj.created_at)
  INTO v_remaining
  FROM public.delivery_jobs dj
  WHERE dj.assignee_user_id = p_driver_user_id
    AND dj.status IN ('pending', 'dispatched');

  IF v_remaining IS NULL OR cardinality(v_remaining) = 0 THEN
    RETURN;
  END IF;

  -- Seed origin: presence, else first job pickup, else first dropoff
  IF v_lat IS NULL OR v_lng IS NULL THEN
    SELECT COALESCE(dj.pickup_lat, dj.dropoff_lat),
           COALESCE(dj.pickup_lng, dj.dropoff_lng)
    INTO v_lat, v_lng
    FROM public.delivery_jobs dj
    WHERE dj.id = v_remaining[1];
  END IF;

  v_cur_lat := v_lat;
  v_cur_lng := v_lng;

  WHILE cardinality(v_remaining) > 0 LOOP
    v_next := NULL;
    v_next_dist := NULL;
    v_next_lat := NULL;
    v_next_lng := NULL;

    FOREACH v_id IN ARRAY v_remaining LOOP
      DECLARE
        v_jlat DOUBLE PRECISION;
        v_jlng DOUBLE PRECISION;
        v_d DOUBLE PRECISION;
      BEGIN
        SELECT COALESCE(dj.dropoff_lat, dj.pickup_lat),
               COALESCE(dj.dropoff_lng, dj.pickup_lng)
        INTO v_jlat, v_jlng
        FROM public.delivery_jobs dj
        WHERE dj.id = v_id;

        v_d := public._haversine_meters(v_cur_lat, v_cur_lng, v_jlat, v_jlng);

        IF v_next IS NULL
           OR (v_d IS NOT NULL AND (v_next_dist IS NULL OR v_d < v_next_dist))
           OR (v_d IS NULL AND v_next_dist IS NULL AND v_next > v_id) THEN
          v_next := v_id;
          v_next_dist := v_d;
          v_next_lat := v_jlat;
          v_next_lng := v_jlng;
        END IF;
      END;
    END LOOP;

    v_seq := v_seq + 1;
    v_results := array_append(v_results, v_next);
    v_dists := array_append(v_dists, v_next_dist);

    UPDATE public.delivery_jobs
    SET route_sequence = v_seq, updated_at = now()
    WHERE id = v_next;

    v_remaining := array_remove(v_remaining, v_next);
    IF v_next_lat IS NOT NULL AND v_next_lng IS NOT NULL THEN
      v_cur_lat := v_next_lat;
      v_cur_lng := v_next_lng;
    END IF;
  END LOOP;

  FOR v_seq IN 1 .. cardinality(v_results) LOOP
    delivery_job_id := v_results[v_seq];
    route_sequence := v_seq;
    distance_m := v_dists[v_seq];
    RETURN NEXT;
  END LOOP;
END;
$$;

-- ---------------------------------------------------------------------------
-- Extend update_delivery_job_status: out-for-delivery notify (fail closed)
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.update_delivery_job_status(
  p_delivery_job_id UUID,
  p_status public.delivery_job_status
)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_job public.delivery_jobs%ROWTYPE;
  v_evt TEXT;
  v_token TEXT;
BEGIN
  PERFORM public._logistics_begin_rpc();
  PERFORM public._require_dispatcher_staff();

  SELECT * INTO v_job FROM public.delivery_jobs WHERE id = p_delivery_job_id FOR UPDATE;
  IF NOT FOUND THEN
    RAISE EXCEPTION 'delivery job not found';
  END IF;
  IF v_job.status IN ('completed', 'failed') THEN
    RAISE EXCEPTION 'terminal delivery job cannot change status (status=%)', v_job.status;
  END IF;
  IF p_status = 'pending' THEN
    RAISE EXCEPTION 'cannot revert job to pending';
  END IF;

  IF p_status = 'completed'
     AND (v_job.pod_photo_path IS NULL OR v_job.pod_signature_path IS NULL) THEN
    RAISE EXCEPTION 'POD photo and signature required; use submit_delivery_pod';
  END IF;

  IF p_status = 'failed' THEN
    RAISE EXCEPTION 'use fail_delivery_job for failed status (reason + optional reattempt)';
  END IF;

  UPDATE public.delivery_jobs
  SET
    status = p_status,
    dispatched_at = CASE
      WHEN p_status = 'dispatched' THEN COALESCE(dispatched_at, now())
      ELSE dispatched_at
    END,
    completed_at = CASE WHEN p_status = 'completed' THEN now() ELSE completed_at END,
    failed_at = CASE WHEN p_status = 'failed' THEN now() ELSE failed_at END,
    completed_via = CASE
      WHEN p_status = 'completed' THEN COALESCE(completed_via, 'manual'::public.delivery_completed_via)
      ELSE completed_via
    END,
    updated_at = now()
  WHERE id = p_delivery_job_id;

  IF p_status = 'dispatched' THEN
    v_token := public.mint_delivery_track_token(p_delivery_job_id);
    PERFORM public._notify_out_for_delivery(p_delivery_job_id, v_token);
  END IF;

  IF p_status IN ('completed', 'failed') THEN
    UPDATE public.delivery_track_tokens
    SET revoked_at = COALESCE(revoked_at, now())
    WHERE delivery_job_id = p_delivery_job_id
      AND revoked_at IS NULL;
  END IF;

  v_evt := CASE p_status
    WHEN 'dispatched' THEN 'delivery_dispatched'
    WHEN 'completed' THEN 'delivery_completed'
    WHEN 'failed' THEN 'delivery_failed'
    ELSE NULL
  END;

  IF v_evt IS NOT NULL THEN
    PERFORM public.emit_domain_event(
      v_evt,
      'delivery_job:' || p_status::text || ':' || p_delivery_job_id::text,
      jsonb_build_object(
        'delivery_job_id', p_delivery_job_id,
        'delivery_note_id', v_job.delivery_note_id,
        'status', p_status::text
      )
    );
  END IF;

  RETURN p_delivery_job_id;
END;
$$;

-- ---------------------------------------------------------------------------
-- submit_delivery_pod — require OTP (signature change: add p_otp_code)
-- ---------------------------------------------------------------------------
DROP FUNCTION IF EXISTS public.submit_delivery_pod(UUID, TEXT, TEXT, TEXT);

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

  -- Verify OTP (raises on failure); unlocks complete
  PERFORM public.verify_delivery_pod_otp(p_delivery_job_id, p_otp_code);

  UPDATE public.delivery_jobs
  SET
    pod_photo_path = trim(p_pod_photo_path),
    pod_signature_path = trim(p_pod_signature_path),
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
-- Grants
-- ---------------------------------------------------------------------------
REVOKE ALL ON FUNCTION public._hash_delivery_pod_otp(TEXT) FROM PUBLIC;
REVOKE ALL ON FUNCTION public._delivery_job_customer_contact(UUID) FROM PUBLIC;
REVOKE ALL ON FUNCTION public._enqueue_delivery_customer_sms(UUID, TEXT, TEXT, TEXT) FROM PUBLIC;
REVOKE ALL ON FUNCTION public._notify_out_for_delivery(UUID, TEXT) FROM PUBLIC;

REVOKE ALL ON FUNCTION public.generate_delivery_pod_otp(UUID, INTERVAL) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.verify_delivery_pod_otp(UUID, TEXT) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.delivery_geofence_suggestion(
  UUID, DOUBLE PRECISION, DOUBLE PRECISION, DOUBLE PRECISION, DOUBLE PRECISION
) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.fail_delivery_job(
  UUID, public.delivery_failure_reason, TEXT, BOOLEAN
) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.raise_delivery_panic(
  UUID, DOUBLE PRECISION, DOUBLE PRECISION
) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.optimize_driver_stops(UUID) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.submit_delivery_pod(UUID, TEXT, TEXT, TEXT, TEXT) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.update_delivery_job_status(
  UUID, public.delivery_job_status
) FROM PUBLIC;

GRANT EXECUTE ON FUNCTION public.generate_delivery_pod_otp(UUID, INTERVAL)
  TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.verify_delivery_pod_otp(UUID, TEXT)
  TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.delivery_geofence_suggestion(
  UUID, DOUBLE PRECISION, DOUBLE PRECISION, DOUBLE PRECISION, DOUBLE PRECISION
) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.fail_delivery_job(
  UUID, public.delivery_failure_reason, TEXT, BOOLEAN
) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.raise_delivery_panic(
  UUID, DOUBLE PRECISION, DOUBLE PRECISION
) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.optimize_driver_stops(UUID)
  TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.submit_delivery_pod(UUID, TEXT, TEXT, TEXT, TEXT)
  TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.update_delivery_job_status(
  UUID, public.delivery_job_status
) TO authenticated, service_role;
