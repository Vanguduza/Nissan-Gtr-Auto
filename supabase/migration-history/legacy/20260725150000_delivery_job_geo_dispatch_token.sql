-- Delivery job geo RPC + dispatch track-token return (no double-mint).
-- Plan: docs/plans/2026-07-25-dedicated-delivery-app.md
--
-- Mint behavior decision:
--   On transition to `dispatched`, update_delivery_job_status mints ONE track
--   token (feeds SMS via _notify_out_for_delivery) and RETURNS the plaintext
--   in jsonb.track_token. Clients must NOT call mint_delivery_track_token
--   immediately after dispatch — that would revoke the SMS/share token.
--   mint_delivery_track_token remains for intentional remint / rotate only.
-- Exclusions: no ZIMRA, no payroll tax.

-- ---------------------------------------------------------------------------
-- set_delivery_job_geo — pickup/dropoff for suggest + Haversine ETA
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.set_delivery_job_geo(
  p_delivery_job_id UUID,
  p_pickup_lat DOUBLE PRECISION DEFAULT NULL,
  p_pickup_lng DOUBLE PRECISION DEFAULT NULL,
  p_dropoff_lat DOUBLE PRECISION DEFAULT NULL,
  p_dropoff_lng DOUBLE PRECISION DEFAULT NULL
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

  IF p_delivery_job_id IS NULL THEN
    RAISE EXCEPTION 'delivery_job_id required';
  END IF;

  -- Pair integrity: lat/lng must both be null or both set
  IF (p_pickup_lat IS NULL) <> (p_pickup_lng IS NULL) THEN
    RAISE EXCEPTION 'pickup_lat and pickup_lng must both be set or both null';
  END IF;
  IF (p_dropoff_lat IS NULL) <> (p_dropoff_lng IS NULL) THEN
    RAISE EXCEPTION 'dropoff_lat and dropoff_lng must both be set or both null';
  END IF;

  IF p_pickup_lat IS NOT NULL AND (p_pickup_lat < -90 OR p_pickup_lat > 90) THEN
    RAISE EXCEPTION 'pickup_lat out of range';
  END IF;
  IF p_pickup_lng IS NOT NULL AND (p_pickup_lng < -180 OR p_pickup_lng > 180) THEN
    RAISE EXCEPTION 'pickup_lng out of range';
  END IF;
  IF p_dropoff_lat IS NOT NULL AND (p_dropoff_lat < -90 OR p_dropoff_lat > 90) THEN
    RAISE EXCEPTION 'dropoff_lat out of range';
  END IF;
  IF p_dropoff_lng IS NOT NULL AND (p_dropoff_lng < -180 OR p_dropoff_lng > 180) THEN
    RAISE EXCEPTION 'dropoff_lng out of range';
  END IF;

  SELECT * INTO v_job FROM public.delivery_jobs WHERE id = p_delivery_job_id FOR UPDATE;
  IF NOT FOUND THEN
    RAISE EXCEPTION 'delivery job not found';
  END IF;
  IF v_job.status IN ('completed', 'failed') THEN
    RAISE EXCEPTION 'cannot set geo on terminal delivery job (status=%)', v_job.status;
  END IF;

  UPDATE public.delivery_jobs
  SET
    pickup_lat = p_pickup_lat,
    pickup_lng = p_pickup_lng,
    dropoff_lat = p_dropoff_lat,
    dropoff_lng = p_dropoff_lng,
    updated_at = now()
  WHERE id = p_delivery_job_id;

  RETURN p_delivery_job_id;
END;
$$;

COMMENT ON FUNCTION public.set_delivery_job_geo(
  UUID, DOUBLE PRECISION, DOUBLE PRECISION, DOUBLE PRECISION, DOUBLE PRECISION
) IS
  'Dispatcher/admin/warehouse: set pickup/dropoff coords for suggest + ETA. '
  'Null pair clears that endpoint. Blocked on terminal jobs.';

-- ---------------------------------------------------------------------------
-- update_delivery_job_status — return jsonb with track_token on dispatch
-- ---------------------------------------------------------------------------
DROP FUNCTION IF EXISTS public.update_delivery_job_status(
  UUID, public.delivery_job_status
);

CREATE OR REPLACE FUNCTION public.update_delivery_job_status(
  p_delivery_job_id UUID,
  p_status public.delivery_job_status
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_job public.delivery_jobs%ROWTYPE;
  v_evt TEXT;
  v_token TEXT := NULL;
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

  -- Single mint on dispatch: plaintext returned once; SMS notify uses same token.
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

  RETURN jsonb_build_object(
    'delivery_job_id', p_delivery_job_id,
    'track_token', to_jsonb(v_token)
  );
END;
$$;

COMMENT ON FUNCTION public.update_delivery_job_status(
  UUID, public.delivery_job_status
) IS
  'Returns jsonb {delivery_job_id, track_token}. track_token is set only when '
  'transitioning to dispatched (one mint for SMS + share). Do not remint unless '
  'intentionally rotating; remint revokes the prior token.';

-- Soften mutation-guard message to name geo RPC
CREATE OR REPLACE FUNCTION public.guard_delivery_job_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
  IF public._logistics_rpc_active() THEN
    IF TG_OP = 'DELETE' THEN
      RETURN OLD;
    END IF;
    RETURN NEW;
  END IF;

  IF TG_OP = 'INSERT' THEN
    RAISE EXCEPTION 'delivery_jobs: use create_delivery_job RPC';
  ELSIF TG_OP = 'DELETE' THEN
    RAISE EXCEPTION 'delivery_jobs: direct delete not allowed';
  ELSIF TG_OP = 'UPDATE' THEN
    IF OLD.status IN ('completed', 'failed') THEN
      RAISE EXCEPTION 'delivery_jobs: terminal jobs are immutable';
    END IF;
    RAISE EXCEPTION
      'delivery_jobs: use update_delivery_job_status / set_delivery_job_geo / assign RPCs';
  END IF;

  RETURN NULL;
END;
$$;

COMMENT ON FUNCTION public.mint_delivery_track_token(UUID, INTERVAL) IS
  'Intentional remint/rotate only. Dispatch already mints via update_delivery_job_status '
  'and returns plaintext in jsonb.track_token — reminting revokes that token.';

-- ---------------------------------------------------------------------------
-- Grants
-- ---------------------------------------------------------------------------
REVOKE ALL ON FUNCTION public.set_delivery_job_geo(
  UUID, DOUBLE PRECISION, DOUBLE PRECISION, DOUBLE PRECISION, DOUBLE PRECISION
) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.update_delivery_job_status(
  UUID, public.delivery_job_status
) FROM PUBLIC;

GRANT EXECUTE ON FUNCTION public.set_delivery_job_geo(
  UUID, DOUBLE PRECISION, DOUBLE PRECISION, DOUBLE PRECISION, DOUBLE PRECISION
) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.update_delivery_job_status(
  UUID, public.delivery_job_status
) TO authenticated, service_role;
