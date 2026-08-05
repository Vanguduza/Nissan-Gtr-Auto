-- Shrink plaintext window for hr_credential_outbox bodies (temp passwords).
-- 1) Authenticated HR may SELECT metadata only — not body.
-- 2) Stale pending/sending bodies auto-redact after a short TTL (default 90s).
-- Complements complete_hr_credential_outbox redaction. No ZIMRA / payroll tax.

COMMENT ON COLUMN public.hr_credential_outbox.body IS
  'Ephemeral credential text (temp password). Readable by service_role only; redacted after send or scrub TTL.';

-- Column-level: staff see delivery status, never plaintext body.
REVOKE SELECT ON TABLE public.hr_credential_outbox FROM authenticated;
GRANT SELECT (
  id,
  employee_id,
  user_id,
  channel,
  recipient,
  status,
  attempt_count,
  last_error,
  provider_message_id,
  created_by,
  created_at,
  sent_at,
  claimed_at
) ON TABLE public.hr_credential_outbox TO authenticated;

-- Scrub plaintext left in pending/sending if Edge crashes mid-delivery.
CREATE OR REPLACE FUNCTION public.scrub_hr_credential_outbox_bodies(
  p_max_age_seconds INT DEFAULT 90
)
RETURNS INT
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_age INT := GREATEST(COALESCE(p_max_age_seconds, 90), 30);
  v_n INT := 0;
BEGIN
  IF auth.role() IS DISTINCT FROM 'service_role' THEN
    RAISE EXCEPTION 'service_role required';
  END IF;

  UPDATE public.hr_credential_outbox
  SET
    body = '[redacted — pending body window exceeded]',
    status = CASE
      WHEN status IN (
        'pending'::public.hr_credential_outbox_status,
        'sending'::public.hr_credential_outbox_status
      ) THEN 'failed'::public.hr_credential_outbox_status
      ELSE status
    END,
    last_error = CASE
      WHEN status IN (
        'pending'::public.hr_credential_outbox_status,
        'sending'::public.hr_credential_outbox_status
      ) THEN left(
        COALESCE(last_error, 'credential body scrubbed after max pending window'),
        500
      )
      ELSE last_error
    END,
    claimed_at = NULL
  WHERE body IS DISTINCT FROM '[redacted after delivery attempt]'
    AND body IS DISTINCT FROM '[redacted — pending body window exceeded]'
    AND body NOT LIKE '[redacted%'
    AND created_at < now() - make_interval(secs => v_age);

  GET DIAGNOSTICS v_n = ROW_COUNT;
  RETURN v_n;
END;
$$;

-- Mark sending + set claimed_at on enqueue so "pending" plaintext window is tiny
-- when Edge drains synchronously (complete_* still redacts immediately after send).
CREATE OR REPLACE FUNCTION public.enqueue_hr_credential_outbox(
  p_employee_id UUID,
  p_user_id UUID,
  p_channel public.hr_credential_channel,
  p_recipient TEXT,
  p_body TEXT
)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_id UUID;
  v_recipient TEXT := NULLIF(trim(COALESCE(p_recipient, '')), '');
  v_body TEXT := NULLIF(trim(COALESCE(p_body, '')), '');
BEGIN
  IF auth.role() IS DISTINCT FROM 'service_role' THEN
    RAISE EXCEPTION 'service_role required';
  END IF;

  IF p_employee_id IS NULL OR p_user_id IS NULL THEN
    RAISE EXCEPTION 'employee_id and user_id required';
  END IF;
  IF v_recipient IS NULL OR v_body IS NULL THEN
    RAISE EXCEPTION 'recipient and body required';
  END IF;

  -- Opportunistic scrub of stale plaintext before planting a new body.
  PERFORM public.scrub_hr_credential_outbox_bodies(90);

  INSERT INTO public.hr_credential_outbox (
    employee_id, user_id, channel, recipient, body, status, claimed_at, created_by
  ) VALUES (
    p_employee_id,
    p_user_id,
    p_channel,
    v_recipient,
    v_body,
    'sending'::public.hr_credential_outbox_status,
    now(),
    auth.uid()
  )
  ON CONFLICT (employee_id, channel, user_id) DO UPDATE
  SET
    recipient = EXCLUDED.recipient,
    body = EXCLUDED.body,
    status = 'sending'::public.hr_credential_outbox_status,
    attempt_count = 0,
    last_error = NULL,
    provider_message_id = NULL,
    sent_at = NULL,
    claimed_at = now(),
    created_by = COALESCE(EXCLUDED.created_by, public.hr_credential_outbox.created_by),
    created_at = now()
  RETURNING id INTO v_id;

  RETURN v_id;
END;
$$;

REVOKE ALL ON FUNCTION public.scrub_hr_credential_outbox_bodies(INT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.scrub_hr_credential_outbox_bodies(INT) TO service_role;

COMMENT ON FUNCTION public.scrub_hr_credential_outbox_bodies(INT) IS
  'Redacts plaintext hr_credential_outbox.body older than p_max_age_seconds; marks stuck pending/sending as failed.';
