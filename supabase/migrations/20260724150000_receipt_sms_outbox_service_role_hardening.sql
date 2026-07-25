-- Batch 2 security hardening: outbox claim/complete/list + stub drains
-- executable by service_role only; private customer-receipts (staff SELECT, no write);
-- p_limit clamp; reclaim stuck sending rows.
-- Tax-agnostic; NO ZIMRA / FDMS / fiscal markers.

-- ---------------------------------------------------------------------------
-- claimed_at for stuck-sending reclaim (no updated_at on outbox tables)
-- ---------------------------------------------------------------------------
ALTER TABLE public.customer_receipt_outbox
  ADD COLUMN IF NOT EXISTS claimed_at TIMESTAMPTZ;

ALTER TABLE public.sms_outbox
  ADD COLUMN IF NOT EXISTS claimed_at TIMESTAMPTZ;

-- ---------------------------------------------------------------------------
-- Storage: drop authenticated write; keep private + staff SELECT
-- ---------------------------------------------------------------------------
DROP POLICY IF EXISTS customer_receipts_storage_insert ON storage.objects;
DROP POLICY IF EXISTS customer_receipts_storage_update ON storage.objects;

-- SELECT policy from 20260724140000 retained (staff admin/sales/finance).
-- Writes go through service_role Edge workers only (bypasses RLS).

-- ---------------------------------------------------------------------------
-- list_receipt_documents_needing_pdf — service_role + clamp
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.list_receipt_documents_needing_pdf(
  p_limit INT DEFAULT 20
)
RETURNS TABLE (document_id UUID)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_limit INT := LEAST(100, GREATEST(1, COALESCE(p_limit, 20)));
BEGIN
  IF auth.role() IS DISTINCT FROM 'service_role' THEN
    RAISE EXCEPTION 'service_role required';
  END IF;

  RETURN QUERY
  SELECT d.document_id
  FROM (
    SELECT DISTINCT o.document_id AS document_id
    FROM public.customer_receipt_outbox o
    WHERE o.status IN ('pending', 'rendering', 'failed')
      AND o.download_url IS NULL
      AND o.attempt_count < 8
  ) d
  ORDER BY d.document_id
  LIMIT v_limit;
END;
$$;

-- ---------------------------------------------------------------------------
-- claim_receipt_outbox_batch — service_role, clamp, reclaim stuck sending
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.claim_receipt_outbox_batch(
  p_limit INT DEFAULT 50
)
RETURNS SETOF public.customer_receipt_outbox
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_limit INT := LEAST(100, GREATEST(1, COALESCE(p_limit, 50)));
BEGIN
  IF auth.role() IS DISTINCT FROM 'service_role' THEN
    RAISE EXCEPTION 'service_role required';
  END IF;

  -- Reclaim workers that died mid-send (default 15 minutes).
  UPDATE public.customer_receipt_outbox
  SET
    status = 'failed',
    last_error = left(
      COALESCE(last_error || '; ', '') || 'reclaimed: stuck in sending',
      2000
    ),
    claimed_at = NULL
  WHERE status = 'sending'
    AND claimed_at IS NOT NULL
    AND claimed_at < now() - interval '15 minutes';

  RETURN QUERY
  WITH picked AS (
    SELECT o.id
    FROM public.customer_receipt_outbox o
    WHERE o.status IN ('pending', 'failed')
      AND o.download_url IS NOT NULL
      AND o.attempt_count < 8
    ORDER BY o.created_at
    LIMIT v_limit
    FOR UPDATE OF o SKIP LOCKED
  )
  UPDATE public.customer_receipt_outbox o
  SET
    status = 'sending',
    attempt_count = o.attempt_count + 1,
    last_error = NULL,
    claimed_at = now()
  FROM picked
  WHERE o.id = picked.id
  RETURNING o.*;
END;
$$;

CREATE OR REPLACE FUNCTION public.complete_receipt_outbox(
  p_id UUID,
  p_success BOOLEAN,
  p_error TEXT DEFAULT NULL
)
RETURNS VOID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
  IF auth.role() IS DISTINCT FROM 'service_role' THEN
    RAISE EXCEPTION 'service_role required';
  END IF;

  IF p_success THEN
    UPDATE public.customer_receipt_outbox
    SET
      status = 'sent',
      last_error = NULL,
      sent_at = now(),
      claimed_at = NULL
    WHERE id = p_id
      AND status IS DISTINCT FROM 'sent';
  ELSE
    UPDATE public.customer_receipt_outbox
    SET
      status = 'failed',
      last_error = left(COALESCE(p_error, 'send failed'), 2000),
      claimed_at = NULL
    WHERE id = p_id
      AND status IS DISTINCT FROM 'sent';
  END IF;
END;
$$;

-- ---------------------------------------------------------------------------
-- SMS claim / complete
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.claim_sms_outbox_batch(
  p_limit INT DEFAULT 50
)
RETURNS SETOF public.sms_outbox
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_limit INT := LEAST(100, GREATEST(1, COALESCE(p_limit, 50)));
BEGIN
  IF auth.role() IS DISTINCT FROM 'service_role' THEN
    RAISE EXCEPTION 'service_role required';
  END IF;

  UPDATE public.sms_outbox
  SET
    status = 'failed',
    last_error = left(
      COALESCE(last_error || '; ', '') || 'reclaimed: stuck in sending',
      2000
    ),
    claimed_at = NULL
  WHERE status = 'sending'
    AND claimed_at IS NOT NULL
    AND claimed_at < now() - interval '15 minutes';

  RETURN QUERY
  WITH picked AS (
    SELECT o.id
    FROM public.sms_outbox o
    WHERE o.status IN ('pending', 'failed')
      AND o.attempt_count < 8
    ORDER BY o.created_at
    LIMIT v_limit
    FOR UPDATE OF o SKIP LOCKED
  )
  UPDATE public.sms_outbox o
  SET
    status = 'sending',
    attempt_count = o.attempt_count + 1,
    last_error = NULL,
    claimed_at = now()
  FROM picked
  WHERE o.id = picked.id
  RETURNING o.*;
END;
$$;

CREATE OR REPLACE FUNCTION public.complete_sms_outbox(
  p_id UUID,
  p_success BOOLEAN,
  p_error TEXT DEFAULT NULL,
  p_provider_message_id TEXT DEFAULT NULL
)
RETURNS VOID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
  IF auth.role() IS DISTINCT FROM 'service_role' THEN
    RAISE EXCEPTION 'service_role required';
  END IF;

  IF p_success THEN
    UPDATE public.sms_outbox
    SET
      status = 'sent',
      last_error = NULL,
      sent_at = now(),
      provider_message_id = COALESCE(p_provider_message_id, provider_message_id),
      claimed_at = NULL
    WHERE id = p_id
      AND status IS DISTINCT FROM 'sent';
  ELSE
    UPDATE public.sms_outbox
    SET
      status = 'failed',
      last_error = left(COALESCE(p_error, 'SMS send failed'), 2000),
      claimed_at = NULL
    WHERE id = p_id
      AND status IS DISTINCT FROM 'sent';
  END IF;
END;
$$;

-- ---------------------------------------------------------------------------
-- Stub drains — service_role + clamp (local Edge stub path only)
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.process_receipt_outbox_batch(
  p_limit INT DEFAULT 50,
  p_stub_success BOOLEAN DEFAULT true
)
RETURNS INT
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_row public.customer_receipt_outbox%ROWTYPE;
  v_count INT := 0;
  v_limit INT := LEAST(100, GREATEST(1, COALESCE(p_limit, 50)));
BEGIN
  IF auth.role() IS DISTINCT FROM 'service_role' THEN
    RAISE EXCEPTION 'service_role required';
  END IF;

  FOR v_row IN
    SELECT *
    FROM public.customer_receipt_outbox
    WHERE status IN ('pending', 'failed')
      AND download_url IS NOT NULL
      AND attempt_count < 8
    ORDER BY created_at
    LIMIT v_limit
    FOR UPDATE SKIP LOCKED
  LOOP
    IF v_row.status = 'sent' THEN
      CONTINUE;
    END IF;

    IF p_stub_success THEN
      UPDATE public.customer_receipt_outbox
      SET
        status = 'sent',
        attempt_count = attempt_count + 1,
        last_error = NULL,
        sent_at = now(),
        claimed_at = NULL
      WHERE id = v_row.id
        AND status IS DISTINCT FROM 'sent';
    ELSE
      UPDATE public.customer_receipt_outbox
      SET
        status = 'failed',
        attempt_count = attempt_count + 1,
        last_error = COALESCE(last_error, 'stub send failed'),
        claimed_at = NULL
      WHERE id = v_row.id
        AND status IS DISTINCT FROM 'sent';
    END IF;

    v_count := v_count + 1;
  END LOOP;

  RETURN v_count;
END;
$$;

CREATE OR REPLACE FUNCTION public.drain_sms_outbox_batch(
  p_limit INT DEFAULT 50,
  p_stub_success BOOLEAN DEFAULT true
)
RETURNS INT
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_row public.sms_outbox%ROWTYPE;
  v_count INT := 0;
  v_limit INT := LEAST(100, GREATEST(1, COALESCE(p_limit, 50)));
BEGIN
  IF auth.role() IS DISTINCT FROM 'service_role' THEN
    RAISE EXCEPTION 'service_role required';
  END IF;

  FOR v_row IN
    SELECT *
    FROM public.sms_outbox
    WHERE status IN ('pending', 'failed')
      AND attempt_count < 8
    ORDER BY created_at
    LIMIT v_limit
    FOR UPDATE SKIP LOCKED
  LOOP
    IF p_stub_success THEN
      UPDATE public.sms_outbox
      SET
        status = 'sent',
        attempt_count = attempt_count + 1,
        last_error = NULL,
        sent_at = now(),
        provider_message_id = COALESCE(provider_message_id, 'stub-' || id::text),
        claimed_at = NULL
      WHERE id = v_row.id
        AND status IS DISTINCT FROM 'sent';
    ELSE
      UPDATE public.sms_outbox
      SET
        status = 'failed',
        attempt_count = attempt_count + 1,
        last_error = COALESCE(last_error, 'stub SMS gateway failed'),
        claimed_at = NULL
      WHERE id = v_row.id
        AND status IS DISTINCT FROM 'sent';
    END IF;
    v_count := v_count + 1;
  END LOOP;

  RETURN v_count;
END;
$$;

-- ---------------------------------------------------------------------------
-- EXECUTE: revoke clients; grant service_role only
-- ---------------------------------------------------------------------------
REVOKE ALL ON FUNCTION public.list_receipt_documents_needing_pdf(INT)
  FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.claim_receipt_outbox_batch(INT)
  FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.complete_receipt_outbox(UUID, BOOLEAN, TEXT)
  FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.claim_sms_outbox_batch(INT)
  FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.complete_sms_outbox(UUID, BOOLEAN, TEXT, TEXT)
  FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.process_receipt_outbox_batch(INT, BOOLEAN)
  FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.drain_sms_outbox_batch(INT, BOOLEAN)
  FROM PUBLIC, anon, authenticated;

GRANT EXECUTE ON FUNCTION public.list_receipt_documents_needing_pdf(INT)
  TO service_role;
GRANT EXECUTE ON FUNCTION public.claim_receipt_outbox_batch(INT)
  TO service_role;
GRANT EXECUTE ON FUNCTION public.complete_receipt_outbox(UUID, BOOLEAN, TEXT)
  TO service_role;
GRANT EXECUTE ON FUNCTION public.claim_sms_outbox_batch(INT)
  TO service_role;
GRANT EXECUTE ON FUNCTION public.complete_sms_outbox(UUID, BOOLEAN, TEXT, TEXT)
  TO service_role;
GRANT EXECUTE ON FUNCTION public.process_receipt_outbox_batch(INT, BOOLEAN)
  TO service_role;
GRANT EXECUTE ON FUNCTION public.drain_sms_outbox_batch(INT, BOOLEAN)
  TO service_role;
