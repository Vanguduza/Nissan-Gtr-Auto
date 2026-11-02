-- Batch 3: WhatsApp parts-finder bot per-sender rate limits.
-- service_role only via check_whatsapp_bot_rate_limit. No anon EXECUTE.
-- Tax-agnostic; NO ZIMRA / FDMS / fiscal markers.

CREATE TABLE public.whatsapp_bot_rate_limits (
  wa_from text PRIMARY KEY,
  window_started_at timestamptz NOT NULL DEFAULT now(),
  request_count integer NOT NULL DEFAULT 0
    CHECK (request_count >= 0),
  updated_at timestamptz NOT NULL DEFAULT now()
);

COMMENT ON TABLE public.whatsapp_bot_rate_limits IS
  'Fixed-window request budget per WhatsApp sender for parts-finder bot. service_role only.';

CREATE INDEX whatsapp_bot_rate_limits_updated_at_idx
  ON public.whatsapp_bot_rate_limits (updated_at);

ALTER TABLE public.whatsapp_bot_rate_limits ENABLE ROW LEVEL SECURITY;

-- Deny-by-default: no policies for anon/authenticated.
-- service_role bypasses RLS; prefer the RPC below for writes.

REVOKE ALL ON TABLE public.whatsapp_bot_rate_limits FROM PUBLIC;
REVOKE ALL ON TABLE public.whatsapp_bot_rate_limits FROM anon, authenticated;
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.whatsapp_bot_rate_limits TO service_role;

-- ---------------------------------------------------------------------------
-- check_whatsapp_bot_rate_limit — atomic fixed window; service_role only
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.check_whatsapp_bot_rate_limit(
  p_wa_from text,
  p_max_requests integer DEFAULT 20,
  p_window_seconds integer DEFAULT 60
)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_from text := regexp_replace(trim(coalesce(p_wa_from, '')), '\D', '', 'g');
  v_max int := LEAST(200, GREATEST(1, COALESCE(p_max_requests, 20)));
  v_window int := LEAST(3600, GREATEST(10, COALESCE(p_window_seconds, 60)));
  v_row public.whatsapp_bot_rate_limits%ROWTYPE;
  v_now timestamptz := clock_timestamp();
  v_allowed boolean;
  v_remaining int;
  v_retry int := 0;
BEGIN
  IF auth.role() IS DISTINCT FROM 'service_role' THEN
    RAISE EXCEPTION 'service_role required';
  END IF;

  IF v_from = '' THEN
    RAISE EXCEPTION 'wa_from required';
  END IF;

  INSERT INTO public.whatsapp_bot_rate_limits (
    wa_from, window_started_at, request_count, updated_at
  )
  VALUES (v_from, v_now, 0, v_now)
  ON CONFLICT (wa_from) DO NOTHING;

  SELECT * INTO STRICT v_row
  FROM public.whatsapp_bot_rate_limits
  WHERE wa_from = v_from
  FOR UPDATE;

  IF v_row.window_started_at + make_interval(secs => v_window) <= v_now THEN
    UPDATE public.whatsapp_bot_rate_limits
    SET
      window_started_at = v_now,
      request_count = 1,
      updated_at = v_now
    WHERE wa_from = v_from;
    v_allowed := true;
    v_remaining := v_max - 1;
  ELSIF v_row.request_count >= v_max THEN
    v_allowed := false;
    v_remaining := 0;
    v_retry := GREATEST(
      1,
      CEIL(
        EXTRACT(
          EPOCH FROM (
            v_row.window_started_at + make_interval(secs => v_window) - v_now
          )
        )
      )::int
    );
  ELSE
    UPDATE public.whatsapp_bot_rate_limits
    SET
      request_count = request_count + 1,
      updated_at = v_now
    WHERE wa_from = v_from;
    v_allowed := true;
    v_remaining := v_max - (v_row.request_count + 1);
  END IF;

  RETURN jsonb_build_object(
    'allowed', v_allowed,
    'remaining', GREATEST(0, v_remaining),
    'retry_after_seconds', v_retry,
    'max_requests', v_max,
    'window_seconds', v_window
  );
END;
$$;

COMMENT ON FUNCTION public.check_whatsapp_bot_rate_limit(text, integer, integer) IS
  'Consume one WhatsApp bot request for wa_from; returns allowed/remaining/retry_after. service_role only.';

REVOKE ALL ON FUNCTION public.check_whatsapp_bot_rate_limit(text, integer, integer) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.check_whatsapp_bot_rate_limit(text, integer, integer)
  FROM anon, authenticated;
GRANT EXECUTE ON FUNCTION public.check_whatsapp_bot_rate_limit(text, integer, integer)
  TO service_role;
