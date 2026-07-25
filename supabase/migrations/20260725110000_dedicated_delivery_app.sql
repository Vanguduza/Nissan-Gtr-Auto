-- Dedicated delivery app P0: driver role, presence, ETA columns, track tokens,
-- suggest/assign/POD/get_track RPCs; harden ingest for assigned driver JWT.
-- Plan: docs/plans/2026-07-25-dedicated-delivery-app.md
-- ADR: docs/decisions/2026-07-25-dedicated-delivery-app.md
-- Reuses Phase 10 delivery_jobs / delivery_locations / ingest_delivery_location.
-- Exclusions: no ZIMRA, no payroll tax, no browser GPS.

-- ---------------------------------------------------------------------------
-- Enums
-- ---------------------------------------------------------------------------
DO $$
BEGIN
  ALTER TYPE public.staff_role ADD VALUE 'driver';
EXCEPTION
  WHEN duplicate_object THEN NULL;
END;
$$;

DO $$
BEGIN
  CREATE TYPE public.driver_presence_status AS ENUM (
    'available', 'on_duty', 'break', 'offline'
  );
EXCEPTION
  WHEN duplicate_object THEN NULL;
END;
$$;

DO $$
BEGIN
  CREATE TYPE public.delivery_eta_source AS ENUM (
    'haversine', 'osrm', 'manual'
  );
EXCEPTION
  WHEN duplicate_object THEN NULL;
END;
$$;

DO $$
BEGIN
  CREATE TYPE public.delivery_completed_via AS ENUM (
    'pod', 'manual', 'admin'
  );
EXCEPTION
  WHEN duplicate_object THEN NULL;
END;
$$;

-- ---------------------------------------------------------------------------
-- driver_presence
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.driver_presence (
  user_id UUID PRIMARY KEY REFERENCES public.profiles (id) ON DELETE CASCADE,
  status public.driver_presence_status NOT NULL DEFAULT 'offline',
  last_lat DOUBLE PRECISION CHECK (last_lat IS NULL OR last_lat BETWEEN -90 AND 90),
  last_lng DOUBLE PRECISION CHECK (last_lng IS NULL OR last_lng BETWEEN -180 AND 180),
  last_seen_at TIMESTAMPTZ,
  capacity INTEGER NOT NULL DEFAULT 1 CHECK (capacity >= 0),
  shift_starts_at TIMESTAMPTZ,
  shift_ends_at TIMESTAMPTZ,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT driver_presence_shift_order CHECK (
    shift_starts_at IS NULL
    OR shift_ends_at IS NULL
    OR shift_ends_at > shift_starts_at
  )
);

CREATE INDEX IF NOT EXISTS driver_presence_status_idx
  ON public.driver_presence (status, last_seen_at DESC NULLS LAST);

COMMENT ON TABLE public.driver_presence IS
  'Driver availability for assignment. Self-write; dispatcher/admin/warehouse read.';

ALTER TABLE public.driver_presence ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS driver_presence_self_select ON public.driver_presence;
CREATE POLICY driver_presence_self_select ON public.driver_presence
  FOR SELECT TO authenticated
  USING (
    user_id = auth.uid()
    OR public.has_staff_role(
      ARRAY['admin', 'warehouse', 'dispatcher']::public.staff_role[]
    )
  );

DROP POLICY IF EXISTS driver_presence_self_insert ON public.driver_presence;
CREATE POLICY driver_presence_self_insert ON public.driver_presence
  FOR INSERT TO authenticated
  WITH CHECK (user_id = auth.uid());

DROP POLICY IF EXISTS driver_presence_self_update ON public.driver_presence;
CREATE POLICY driver_presence_self_update ON public.driver_presence
  FOR UPDATE TO authenticated
  USING (user_id = auth.uid())
  WITH CHECK (user_id = auth.uid());

-- No direct DELETE for clients
GRANT SELECT, INSERT, UPDATE ON TABLE public.driver_presence
  TO authenticated, service_role;

-- ---------------------------------------------------------------------------
-- delivery_jobs ETA / POD / geo extensions (keep eta_at)
-- ---------------------------------------------------------------------------
ALTER TABLE public.delivery_jobs
  ADD COLUMN IF NOT EXISTS eta_seconds INTEGER CHECK (eta_seconds IS NULL OR eta_seconds >= 0),
  ADD COLUMN IF NOT EXISTS eta_source public.delivery_eta_source,
  ADD COLUMN IF NOT EXISTS eta_updated_at TIMESTAMPTZ,
  ADD COLUMN IF NOT EXISTS pickup_lat DOUBLE PRECISION
    CHECK (pickup_lat IS NULL OR pickup_lat BETWEEN -90 AND 90),
  ADD COLUMN IF NOT EXISTS pickup_lng DOUBLE PRECISION
    CHECK (pickup_lng IS NULL OR pickup_lng BETWEEN -180 AND 180),
  ADD COLUMN IF NOT EXISTS dropoff_lat DOUBLE PRECISION
    CHECK (dropoff_lat IS NULL OR dropoff_lat BETWEEN -90 AND 90),
  ADD COLUMN IF NOT EXISTS dropoff_lng DOUBLE PRECISION
    CHECK (dropoff_lng IS NULL OR dropoff_lng BETWEEN -180 AND 180),
  ADD COLUMN IF NOT EXISTS failure_reason TEXT,
  ADD COLUMN IF NOT EXISTS pod_photo_path TEXT,
  ADD COLUMN IF NOT EXISTS pod_signature_path TEXT,
  ADD COLUMN IF NOT EXISTS completed_via public.delivery_completed_via;

COMMENT ON COLUMN public.delivery_jobs.eta_at IS
  'Absolute ETA timestamp; refreshed on ingest when coords available.';
COMMENT ON COLUMN public.delivery_jobs.eta_seconds IS
  'Seconds until ETA at last recompute (Haversine default).';
COMMENT ON COLUMN public.delivery_jobs.eta_source IS
  'haversine (default) | osrm (optional provider) | manual (dispatcher).';

-- ---------------------------------------------------------------------------
-- delivery_track_tokens — deny direct SELECT; RPC only
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.delivery_track_tokens (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  delivery_job_id UUID NOT NULL REFERENCES public.delivery_jobs (id) ON DELETE CASCADE,
  token_hash TEXT NOT NULL,
  expires_at TIMESTAMPTZ NOT NULL,
  revoked_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT delivery_track_tokens_hash_nonempty CHECK (length(trim(token_hash)) > 0)
);

CREATE UNIQUE INDEX IF NOT EXISTS delivery_track_tokens_hash_uidx
  ON public.delivery_track_tokens (token_hash);

CREATE INDEX IF NOT EXISTS delivery_track_tokens_job_idx
  ON public.delivery_track_tokens (delivery_job_id, created_at DESC);

COMMENT ON TABLE public.delivery_track_tokens IS
  'Share tokens for privacy-safe customer last-point track. Hash only; no direct SELECT.';

ALTER TABLE public.delivery_track_tokens ENABLE ROW LEVEL SECURITY;

-- Explicit deny: no policies for authenticated/anon. SECURITY DEFINER RPCs own access.
REVOKE ALL ON TABLE public.delivery_track_tokens FROM PUBLIC, anon, authenticated;
GRANT ALL ON TABLE public.delivery_track_tokens TO service_role;

-- ---------------------------------------------------------------------------
-- Geo / ETA helpers
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public._haversine_meters(
  p_lat1 DOUBLE PRECISION,
  p_lng1 DOUBLE PRECISION,
  p_lat2 DOUBLE PRECISION,
  p_lng2 DOUBLE PRECISION
)
RETURNS DOUBLE PRECISION
LANGUAGE sql
IMMUTABLE
AS $$
  SELECT CASE
    WHEN p_lat1 IS NULL OR p_lng1 IS NULL OR p_lat2 IS NULL OR p_lng2 IS NULL THEN NULL
    ELSE (
      2 * 6371000 * asin(
        sqrt(
          power(sin(radians(p_lat2 - p_lat1) / 2), 2)
          + cos(radians(p_lat1)) * cos(radians(p_lat2))
            * power(sin(radians(p_lng2 - p_lng1) / 2), 2)
        )
      )
    )
  END;
$$;

-- Assumed urban speed for Haversine ETA: 25 km/h
CREATE OR REPLACE FUNCTION public._haversine_eta_seconds(
  p_from_lat DOUBLE PRECISION,
  p_from_lng DOUBLE PRECISION,
  p_to_lat DOUBLE PRECISION,
  p_to_lng DOUBLE PRECISION
)
RETURNS INTEGER
LANGUAGE sql
IMMUTABLE
AS $$
  SELECT CASE
    WHEN public._haversine_meters(p_from_lat, p_from_lng, p_to_lat, p_to_lng) IS NULL
      THEN NULL
    ELSE greatest(
      0,
      ceil(
        public._haversine_meters(p_from_lat, p_from_lng, p_to_lat, p_to_lng)
        / (25.0 * 1000.0 / 3600.0)
      )::integer
    )
  END;
$$;

CREATE OR REPLACE FUNCTION public._recompute_delivery_eta_haversine(
  p_job_id UUID,
  p_lat DOUBLE PRECISION,
  p_lng DOUBLE PRECISION
)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_drop_lat DOUBLE PRECISION;
  v_drop_lng DOUBLE PRECISION;
  v_secs INTEGER;
  v_source public.delivery_eta_source;
BEGIN
  SELECT dropoff_lat, dropoff_lng, eta_source
  INTO v_drop_lat, v_drop_lng, v_source
  FROM public.delivery_jobs
  WHERE id = p_job_id;

  IF NOT FOUND THEN
    RETURN;
  END IF;

  -- Do not overwrite manual ETA
  IF v_source = 'manual' THEN
    RETURN;
  END IF;

  IF v_drop_lat IS NULL OR v_drop_lng IS NULL THEN
    RETURN;
  END IF;

  v_secs := public._haversine_eta_seconds(p_lat, p_lng, v_drop_lat, v_drop_lng);
  IF v_secs IS NULL THEN
    RETURN;
  END IF;

  UPDATE public.delivery_jobs
  SET
    eta_seconds = v_secs,
    eta_at = now() + make_interval(secs => v_secs),
    eta_source = 'haversine',
    eta_updated_at = now(),
    updated_at = now()
  WHERE id = p_job_id;
END;
$$;

CREATE OR REPLACE FUNCTION public._driver_open_job_count(p_user_id UUID)
RETURNS INTEGER
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
  SELECT count(*)::integer
  FROM public.delivery_jobs dj
  WHERE dj.assignee_user_id = p_user_id
    AND dj.status IN ('pending', 'dispatched');
$$;

CREATE OR REPLACE FUNCTION public._driver_shift_active(
  p_starts TIMESTAMPTZ,
  p_ends TIMESTAMPTZ,
  p_at TIMESTAMPTZ DEFAULT now()
)
RETURNS BOOLEAN
LANGUAGE sql
IMMUTABLE
AS $$
  SELECT
    (p_starts IS NULL AND p_ends IS NULL)
    OR (
      (p_starts IS NULL OR p_at >= p_starts)
      AND (p_ends IS NULL OR p_at <= p_ends)
    );
$$;

CREATE OR REPLACE FUNCTION public._driver_eligible_for_assign(
  p_user_id UUID,
  p_at TIMESTAMPTZ DEFAULT now()
)
RETURNS BOOLEAN
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_row public.driver_presence%ROWTYPE;
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM public.staff_roles
    WHERE user_id = p_user_id AND role = 'driver'
  ) THEN
    RETURN false;
  END IF;

  SELECT * INTO v_row FROM public.driver_presence WHERE user_id = p_user_id;
  IF NOT FOUND THEN
    RETURN false;
  END IF;

  IF v_row.status NOT IN ('available', 'on_duty') THEN
    RETURN false;
  END IF;

  IF NOT public._driver_shift_active(v_row.shift_starts_at, v_row.shift_ends_at, p_at) THEN
    RETURN false;
  END IF;

  IF public._driver_open_job_count(p_user_id) >= v_row.capacity THEN
    RETURN false;
  END IF;

  RETURN true;
END;
$$;

CREATE OR REPLACE FUNCTION public._hash_delivery_track_token(p_token TEXT)
RETURNS TEXT
LANGUAGE sql
IMMUTABLE
SET search_path = public, extensions
AS $$
  SELECT encode(extensions.digest(convert_to(p_token, 'UTF8'), 'sha256'), 'hex');
$$;

CREATE OR REPLACE FUNCTION public._customer_owns_delivery_job(p_job_id UUID)
RETURNS BOOLEAN
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
  SELECT EXISTS (
    SELECT 1
    FROM public.delivery_jobs dj
    JOIN public.delivery_notes dn ON dn.id = dj.delivery_note_id
    JOIN public.sales_invoices si ON si.id = dn.sales_invoice_id
    JOIN public.customers c ON c.id = si.customer_id
    WHERE dj.id = p_job_id
      AND c.profile_id = auth.uid()
  );
$$;

-- ---------------------------------------------------------------------------
-- Token mint (plaintext returned once; hash stored)
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.mint_delivery_track_token(
  p_delivery_job_id UUID,
  p_ttl INTERVAL DEFAULT interval '48 hours'
)
RETURNS TEXT
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, extensions
AS $$
DECLARE
  v_job public.delivery_jobs%ROWTYPE;
  v_raw TEXT;
  v_hash TEXT;
BEGIN
  PERFORM public._logistics_begin_rpc();

  IF NOT (
    auth.role() = 'service_role'
    OR public.has_staff_role(
      ARRAY['admin', 'warehouse', 'dispatcher']::public.staff_role[]
    )
  ) THEN
    RAISE EXCEPTION 'dispatcher, warehouse, or admin required to mint track token';
  END IF;

  SELECT * INTO v_job FROM public.delivery_jobs WHERE id = p_delivery_job_id FOR UPDATE;
  IF NOT FOUND THEN
    RAISE EXCEPTION 'delivery job not found';
  END IF;
  IF v_job.status IN ('completed', 'failed') THEN
    RAISE EXCEPTION 'cannot mint track token for terminal job';
  END IF;

  -- Revoke prior active tokens for this job
  UPDATE public.delivery_track_tokens
  SET revoked_at = now()
  WHERE delivery_job_id = p_delivery_job_id
    AND revoked_at IS NULL;

  v_raw := encode(extensions.gen_random_bytes(32), 'hex');
  v_hash := public._hash_delivery_track_token(v_raw);

  INSERT INTO public.delivery_track_tokens (
    delivery_job_id, token_hash, expires_at
  )
  VALUES (
    p_delivery_job_id,
    v_hash,
    now() + COALESCE(p_ttl, interval '48 hours')
  );

  RETURN v_raw;
END;
$$;

-- ---------------------------------------------------------------------------
-- Presence / suggest / assign
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.set_driver_presence(
  p_status public.driver_presence_status,
  p_capacity INTEGER DEFAULT NULL,
  p_shift_starts_at TIMESTAMPTZ DEFAULT NULL,
  p_shift_ends_at TIMESTAMPTZ DEFAULT NULL,
  p_last_lat DOUBLE PRECISION DEFAULT NULL,
  p_last_lng DOUBLE PRECISION DEFAULT NULL
)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_uid UUID := auth.uid();
BEGIN
  IF v_uid IS NULL THEN
    RAISE EXCEPTION 'authentication required';
  END IF;

  IF NOT (
    auth.role() = 'service_role'
    OR public.has_staff_role(ARRAY['driver']::public.staff_role[])
  ) THEN
    RAISE EXCEPTION 'driver role required to set presence';
  END IF;

  IF p_capacity IS NOT NULL AND p_capacity < 0 THEN
    RAISE EXCEPTION 'capacity must be >= 0';
  END IF;

  INSERT INTO public.driver_presence AS dp (
    user_id, status, capacity, shift_starts_at, shift_ends_at,
    last_lat, last_lng, last_seen_at, updated_at
  )
  VALUES (
    v_uid,
    p_status,
    COALESCE(p_capacity, 1),
    p_shift_starts_at,
    p_shift_ends_at,
    p_last_lat,
    p_last_lng,
    CASE WHEN p_last_lat IS NOT NULL THEN now() ELSE NULL END,
    now()
  )
  ON CONFLICT (user_id) DO UPDATE
  SET
    status = EXCLUDED.status,
    capacity = COALESCE(p_capacity, dp.capacity),
    shift_starts_at = COALESCE(p_shift_starts_at, dp.shift_starts_at),
    shift_ends_at = COALESCE(p_shift_ends_at, dp.shift_ends_at),
    last_lat = COALESCE(p_last_lat, dp.last_lat),
    last_lng = COALESCE(p_last_lng, dp.last_lng),
    last_seen_at = CASE
      WHEN p_last_lat IS NOT NULL THEN now()
      ELSE dp.last_seen_at
    END,
    updated_at = now();

  RETURN v_uid;
END;
$$;

CREATE OR REPLACE FUNCTION public.suggest_delivery_assignees(
  p_delivery_job_id UUID,
  p_limit INTEGER DEFAULT 5
)
RETURNS TABLE (
  user_id UUID,
  status public.driver_presence_status,
  distance_m DOUBLE PRECISION,
  capacity INTEGER,
  open_jobs INTEGER,
  last_lat DOUBLE PRECISION,
  last_lng DOUBLE PRECISION,
  last_seen_at TIMESTAMPTZ
)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_pickup_lat DOUBLE PRECISION;
  v_pickup_lng DOUBLE PRECISION;
  v_lim INTEGER := greatest(1, least(COALESCE(p_limit, 5), 50));
BEGIN
  PERFORM public._require_dispatcher_staff();

  SELECT dj.pickup_lat, dj.pickup_lng
  INTO v_pickup_lat, v_pickup_lng
  FROM public.delivery_jobs dj
  WHERE dj.id = p_delivery_job_id;

  IF NOT FOUND THEN
    RAISE EXCEPTION 'delivery job not found';
  END IF;

  RETURN QUERY
  SELECT
    dp.user_id,
    dp.status,
    public._haversine_meters(
      v_pickup_lat, v_pickup_lng, dp.last_lat, dp.last_lng
    ) AS distance_m,
    dp.capacity,
    public._driver_open_job_count(dp.user_id) AS open_jobs,
    dp.last_lat,
    dp.last_lng,
    dp.last_seen_at
  FROM public.driver_presence dp
  JOIN public.staff_roles sr
    ON sr.user_id = dp.user_id AND sr.role = 'driver'
  WHERE dp.status IN ('available', 'on_duty')
    AND public._driver_shift_active(dp.shift_starts_at, dp.shift_ends_at, now())
    AND public._driver_open_job_count(dp.user_id) < dp.capacity
  ORDER BY
    (public._haversine_meters(
      v_pickup_lat, v_pickup_lng, dp.last_lat, dp.last_lng
    ) IS NULL) ASC,
    public._haversine_meters(
      v_pickup_lat, v_pickup_lng, dp.last_lat, dp.last_lng
    ) ASC NULLS LAST,
    dp.last_seen_at DESC NULLS LAST
  LIMIT v_lim;
END;
$$;

CREATE OR REPLACE FUNCTION public.assign_delivery_job(
  p_delivery_job_id UUID,
  p_assignee_user_id UUID,
  p_override BOOLEAN DEFAULT false
)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_job public.delivery_jobs%ROWTYPE;
BEGIN
  PERFORM public._logistics_begin_rpc();
  PERFORM public._require_dispatcher_staff();

  IF p_assignee_user_id IS NULL THEN
    RAISE EXCEPTION 'assignee_user_id required';
  END IF;

  SELECT * INTO v_job FROM public.delivery_jobs WHERE id = p_delivery_job_id FOR UPDATE;
  IF NOT FOUND THEN
    RAISE EXCEPTION 'delivery job not found';
  END IF;
  IF v_job.status IN ('completed', 'failed') THEN
    RAISE EXCEPTION 'cannot assign terminal delivery job';
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM public.staff_roles
    WHERE user_id = p_assignee_user_id AND role = 'driver'
  ) THEN
    RAISE EXCEPTION 'assignee must have driver staff role';
  END IF;

  IF NOT COALESCE(p_override, false)
     AND NOT public._driver_eligible_for_assign(p_assignee_user_id) THEN
    RAISE EXCEPTION
      'assignee not eligible (need available/on_duty, capacity, shift); use override=true';
  END IF;

  UPDATE public.delivery_jobs
  SET
    assignee_user_id = p_assignee_user_id,
    updated_at = now()
  WHERE id = p_delivery_job_id;

  RETURN p_delivery_job_id;
END;
$$;

-- ---------------------------------------------------------------------------
-- Harden update_delivery_job_status: mint token on dispatch; POD gate on complete
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

-- Avoid nested mint auth edge-cases: mint after status flip without re-entering mint from here.
-- (mint_delivery_track_token is still the public remint API.)
  IF p_status = 'dispatched' THEN
    v_token := public.mint_delivery_track_token(p_delivery_job_id);
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
-- Harden ingest_delivery_location (same signature; extend authz)
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.ingest_delivery_location(
  p_delivery_job_id UUID,
  p_lat DOUBLE PRECISION,
  p_lng DOUBLE PRECISION,
  p_recorded_at TIMESTAMPTZ DEFAULT now(),
  p_accuracy_m NUMERIC DEFAULT NULL
)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_job public.delivery_jobs%ROWTYPE;
  v_id UUID;
  v_uid UUID := auth.uid();
  v_allowed BOOLEAN := false;
BEGIN
  PERFORM public._logistics_begin_rpc();

  IF auth.role() = 'service_role'
     OR public.has_staff_role(
       ARRAY['admin', 'warehouse', 'dispatcher']::public.staff_role[]
     ) THEN
    v_allowed := true;
  END IF;

  SELECT * INTO v_job FROM public.delivery_jobs WHERE id = p_delivery_job_id;
  IF NOT FOUND THEN
    RAISE EXCEPTION 'delivery job not found';
  END IF;
  IF v_job.status IN ('completed', 'failed') THEN
    RAISE EXCEPTION 'cannot ingest locations for terminal job';
  END IF;

  -- Assigned driver JWT (role driver + assignee match)
  IF NOT v_allowed
     AND v_uid IS NOT NULL
     AND v_job.assignee_user_id = v_uid
     AND public.has_staff_role(ARRAY['driver']::public.staff_role[]) THEN
    v_allowed := true;
  END IF;

  IF NOT v_allowed THEN
    RAISE EXCEPTION
      'bridge/service, dispatcher/warehouse/admin, or assigned driver required for GPS ingest';
  END IF;

  IF EXISTS (
    SELECT 1
    FROM public.delivery_locations
    WHERE delivery_job_id = p_delivery_job_id
      AND ingested_at > now() - interval '5 seconds'
  ) THEN
    RAISE EXCEPTION 'ingest rate limit: wait ~5s between points';
  END IF;

  INSERT INTO public.delivery_locations (
    delivery_job_id, lat, lng, accuracy_m, recorded_at, source
  )
  VALUES (
    p_delivery_job_id, p_lat, p_lng, p_accuracy_m,
    COALESCE(p_recorded_at, now()), 'bridge'
  )
  RETURNING id INTO v_id;

  -- Refresh driver_presence last_* for assignee when known
  IF v_job.assignee_user_id IS NOT NULL THEN
    INSERT INTO public.driver_presence AS dp (
      user_id, status, last_lat, last_lng, last_seen_at, updated_at
    )
    VALUES (
      v_job.assignee_user_id,
      'on_duty',
      p_lat,
      p_lng,
      now(),
      now()
    )
    ON CONFLICT (user_id) DO UPDATE
    SET
      last_lat = EXCLUDED.last_lat,
      last_lng = EXCLUDED.last_lng,
      last_seen_at = EXCLUDED.last_seen_at,
      updated_at = now(),
      status = CASE
        WHEN dp.status = 'offline' THEN 'on_duty'::public.driver_presence_status
        ELSE dp.status
      END;
  END IF;

  PERFORM public._recompute_delivery_eta_haversine(p_delivery_job_id, p_lat, p_lng);

  RETURN v_id;
END;
$$;

-- ---------------------------------------------------------------------------
-- Privacy-safe last point (never full trail)
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.get_delivery_track_point(
  p_delivery_job_id UUID DEFAULT NULL,
  p_token TEXT DEFAULT NULL
)
RETURNS TABLE (
  delivery_job_id UUID,
  lat DOUBLE PRECISION,
  lng DOUBLE PRECISION,
  recorded_at TIMESTAMPTZ,
  eta_at TIMESTAMPTZ,
  eta_seconds INTEGER,
  status public.delivery_job_status
)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_job_id UUID;
  v_hash TEXT;
  v_tok public.delivery_track_tokens%ROWTYPE;
  v_job public.delivery_jobs%ROWTYPE;
  v_ok BOOLEAN := false;
BEGIN
  IF p_delivery_job_id IS NULL AND (p_token IS NULL OR length(trim(p_token)) = 0) THEN
    RAISE EXCEPTION 'delivery_job_id or token required';
  END IF;

  IF p_token IS NOT NULL AND length(trim(p_token)) > 0 THEN
    v_hash := public._hash_delivery_track_token(trim(p_token));
    SELECT * INTO v_tok
    FROM public.delivery_track_tokens
    WHERE token_hash = v_hash
    ORDER BY created_at DESC
    LIMIT 1;

    IF NOT FOUND THEN
      RETURN;
    END IF;
    IF v_tok.revoked_at IS NOT NULL OR v_tok.expires_at < now() THEN
      RETURN;
    END IF;
    v_job_id := v_tok.delivery_job_id;
    v_ok := true;
  ELSE
    v_job_id := p_delivery_job_id;
  END IF;

  SELECT * INTO v_job FROM public.delivery_jobs WHERE id = v_job_id;
  IF NOT FOUND THEN
    RETURN;
  END IF;

  -- Active dispatched only — never terminal / pending
  IF v_job.status <> 'dispatched' THEN
    RETURN;
  END IF;

  IF NOT v_ok THEN
    IF auth.role() = 'service_role'
       OR public.has_staff_role(
         ARRAY['admin', 'warehouse', 'dispatcher']::public.staff_role[]
       )
       OR v_job.assignee_user_id = auth.uid()
       OR public._customer_owns_delivery_job(v_job_id) THEN
      v_ok := true;
    END IF;
  END IF;

  IF NOT v_ok THEN
    RETURN;
  END IF;

  RETURN QUERY
  SELECT
    v_job.id,
    dl.lat,
    dl.lng,
    dl.recorded_at,
    v_job.eta_at,
    v_job.eta_seconds,
    v_job.status
  FROM public.delivery_locations dl
  WHERE dl.delivery_job_id = v_job.id
  ORDER BY dl.recorded_at DESC, dl.ingested_at DESC
  LIMIT 1;
END;
$$;

-- ---------------------------------------------------------------------------
-- POD submit → complete
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.submit_delivery_pod(
  p_delivery_job_id UUID,
  p_pod_photo_path TEXT,
  p_pod_signature_path TEXT,
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
-- Grants (least privilege)
-- ---------------------------------------------------------------------------
REVOKE ALL ON FUNCTION public._haversine_meters(
  DOUBLE PRECISION, DOUBLE PRECISION, DOUBLE PRECISION, DOUBLE PRECISION
) FROM PUBLIC;
REVOKE ALL ON FUNCTION public._haversine_eta_seconds(
  DOUBLE PRECISION, DOUBLE PRECISION, DOUBLE PRECISION, DOUBLE PRECISION
) FROM PUBLIC;
REVOKE ALL ON FUNCTION public._recompute_delivery_eta_haversine(UUID, DOUBLE PRECISION, DOUBLE PRECISION)
  FROM PUBLIC;
REVOKE ALL ON FUNCTION public._driver_open_job_count(UUID) FROM PUBLIC;
REVOKE ALL ON FUNCTION public._driver_shift_active(TIMESTAMPTZ, TIMESTAMPTZ, TIMESTAMPTZ)
  FROM PUBLIC;
REVOKE ALL ON FUNCTION public._driver_eligible_for_assign(UUID, TIMESTAMPTZ) FROM PUBLIC;
REVOKE ALL ON FUNCTION public._hash_delivery_track_token(TEXT) FROM PUBLIC;
REVOKE ALL ON FUNCTION public._customer_owns_delivery_job(UUID) FROM PUBLIC;

REVOKE ALL ON FUNCTION public.mint_delivery_track_token(UUID, INTERVAL) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.set_driver_presence(
  public.driver_presence_status, INTEGER, TIMESTAMPTZ, TIMESTAMPTZ,
  DOUBLE PRECISION, DOUBLE PRECISION
) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.suggest_delivery_assignees(UUID, INTEGER) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.assign_delivery_job(UUID, UUID, BOOLEAN) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.get_delivery_track_point(UUID, TEXT) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.submit_delivery_pod(UUID, TEXT, TEXT, TEXT) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.ingest_delivery_location(
  UUID, DOUBLE PRECISION, DOUBLE PRECISION, TIMESTAMPTZ, NUMERIC
) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.update_delivery_job_status(
  UUID, public.delivery_job_status
) FROM PUBLIC;

GRANT EXECUTE ON FUNCTION public.mint_delivery_track_token(UUID, INTERVAL)
  TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.set_driver_presence(
  public.driver_presence_status, INTEGER, TIMESTAMPTZ, TIMESTAMPTZ,
  DOUBLE PRECISION, DOUBLE PRECISION
) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.suggest_delivery_assignees(UUID, INTEGER)
  TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.assign_delivery_job(UUID, UUID, BOOLEAN)
  TO authenticated, service_role;
-- Token holders may be anon (share link)
GRANT EXECUTE ON FUNCTION public.get_delivery_track_point(UUID, TEXT)
  TO anon, authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.submit_delivery_pod(UUID, TEXT, TEXT, TEXT)
  TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.ingest_delivery_location(
  UUID, DOUBLE PRECISION, DOUBLE PRECISION, TIMESTAMPTZ, NUMERIC
) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.update_delivery_job_status(
  UUID, public.delivery_job_status
) TO authenticated, service_role;

-- SELECT grant for new tables (RLS still applies; track tokens revoked above)
GRANT SELECT ON TABLE public.driver_presence TO authenticated, service_role;
