-- Batch 2: customer-receipts Storage bucket + claim/complete outbox RPCs
-- Real channel send stays in Edge; RPCs only lock rows and record outcomes.
-- Tax-agnostic; NO ZIMRA / FDMS / fiscal markers.

-- ---------------------------------------------------------------------------
-- Storage: private customer-receipts bucket
-- ---------------------------------------------------------------------------
INSERT INTO storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
VALUES (
  'customer-receipts',
  'customer-receipts',
  false,
  10485760,
  ARRAY['application/pdf']
)
ON CONFLICT (id) DO NOTHING;

DROP POLICY IF EXISTS customer_receipts_storage_select ON storage.objects;
CREATE POLICY customer_receipts_storage_select
  ON storage.objects FOR SELECT
  TO authenticated
  USING (
    bucket_id = 'customer-receipts'
    AND public.has_staff_role(ARRAY['admin', 'sales', 'finance']::public.staff_role[])
  );

DROP POLICY IF EXISTS customer_receipts_storage_insert ON storage.objects;
CREATE POLICY customer_receipts_storage_insert
  ON storage.objects FOR INSERT
  TO authenticated
  WITH CHECK (
    bucket_id = 'customer-receipts'
    AND public.has_staff_role(ARRAY['admin', 'sales', 'finance']::public.staff_role[])
  );

DROP POLICY IF EXISTS customer_receipts_storage_update ON storage.objects;
CREATE POLICY customer_receipts_storage_update
  ON storage.objects FOR UPDATE
  TO authenticated
  USING (
    bucket_id = 'customer-receipts'
    AND public.has_staff_role(ARRAY['admin', 'sales', 'finance']::public.staff_role[])
  )
  WITH CHECK (
    bucket_id = 'customer-receipts'
    AND public.has_staff_role(ARRAY['admin', 'sales', 'finance']::public.staff_role[])
  );

-- ---------------------------------------------------------------------------
-- Documents needing PDF (pending outbox without download_url)
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.list_receipt_documents_needing_pdf(
  p_limit INT DEFAULT 20
)
RETURNS TABLE (document_id UUID)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
  IF NOT (
    auth.role() = 'service_role'
    OR public.has_staff_role(ARRAY['admin', 'sales', 'finance']::public.staff_role[])
  ) THEN
    RAISE EXCEPTION 'staff or service role required';
  END IF;

  RETURN QUERY
  SELECT DISTINCT o.document_id
  FROM public.customer_receipt_outbox o
  WHERE o.status IN ('pending', 'rendering', 'failed')
    AND o.download_url IS NULL
    AND o.attempt_count < 8
  ORDER BY o.document_id
  LIMIT GREATEST(1, COALESCE(p_limit, 20));
END;
$$;

-- ---------------------------------------------------------------------------
-- Claim receipt outbox rows for Edge send (FOR UPDATE SKIP LOCKED)
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.claim_receipt_outbox_batch(
  p_limit INT DEFAULT 50
)
RETURNS SETOF public.customer_receipt_outbox
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
  IF NOT (
    auth.role() = 'service_role'
    OR public.has_staff_role(ARRAY['admin', 'sales', 'finance']::public.staff_role[])
  ) THEN
    RAISE EXCEPTION 'staff or service role required';
  END IF;

  RETURN QUERY
  WITH picked AS (
    SELECT o.id
    FROM public.customer_receipt_outbox o
    WHERE o.status IN ('pending', 'failed')
      AND o.download_url IS NOT NULL
      AND o.attempt_count < 8
    ORDER BY o.created_at
    LIMIT GREATEST(1, COALESCE(p_limit, 50))
    FOR UPDATE OF o SKIP LOCKED
  )
  UPDATE public.customer_receipt_outbox o
  SET
    status = 'sending',
    attempt_count = o.attempt_count + 1,
    last_error = NULL
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
  IF NOT (
    auth.role() = 'service_role'
    OR public.has_staff_role(ARRAY['admin', 'sales', 'finance']::public.staff_role[])
  ) THEN
    RAISE EXCEPTION 'staff or service role required';
  END IF;

  IF p_success THEN
    UPDATE public.customer_receipt_outbox
    SET
      status = 'sent',
      last_error = NULL,
      sent_at = now()
    WHERE id = p_id
      AND status IS DISTINCT FROM 'sent';
  ELSE
    UPDATE public.customer_receipt_outbox
    SET
      status = 'failed',
      last_error = left(COALESCE(p_error, 'send failed'), 2000)
    WHERE id = p_id
      AND status IS DISTINCT FROM 'sent';
  END IF;
END;
$$;

-- ---------------------------------------------------------------------------
-- Manager SMS claim / complete
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.claim_sms_outbox_batch(
  p_limit INT DEFAULT 50
)
RETURNS SETOF public.sms_outbox
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
  IF NOT (
    auth.role() = 'service_role'
    OR public.has_staff_role(ARRAY['admin']::public.staff_role[])
  ) THEN
    RAISE EXCEPTION 'admin or service role required to claim SMS outbox';
  END IF;

  RETURN QUERY
  WITH picked AS (
    SELECT o.id
    FROM public.sms_outbox o
    WHERE o.status IN ('pending', 'failed')
      AND o.attempt_count < 8
    ORDER BY o.created_at
    LIMIT GREATEST(1, COALESCE(p_limit, 50))
    FOR UPDATE OF o SKIP LOCKED
  )
  UPDATE public.sms_outbox o
  SET
    status = 'sending',
    attempt_count = o.attempt_count + 1,
    last_error = NULL
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
  IF NOT (
    auth.role() = 'service_role'
    OR public.has_staff_role(ARRAY['admin']::public.staff_role[])
  ) THEN
    RAISE EXCEPTION 'admin or service role required';
  END IF;

  IF p_success THEN
    UPDATE public.sms_outbox
    SET
      status = 'sent',
      last_error = NULL,
      sent_at = now(),
      provider_message_id = COALESCE(p_provider_message_id, provider_message_id)
    WHERE id = p_id
      AND status IS DISTINCT FROM 'sent';
  ELSE
    UPDATE public.sms_outbox
    SET
      status = 'failed',
      last_error = left(COALESCE(p_error, 'SMS send failed'), 2000)
    WHERE id = p_id
      AND status IS DISTINCT FROM 'sent';
  END IF;
END;
$$;

REVOKE ALL ON FUNCTION public.list_receipt_documents_needing_pdf(INT) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.claim_receipt_outbox_batch(INT) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.complete_receipt_outbox(UUID, BOOLEAN, TEXT) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.claim_sms_outbox_batch(INT) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.complete_sms_outbox(UUID, BOOLEAN, TEXT, TEXT) FROM PUBLIC;

GRANT EXECUTE ON FUNCTION public.list_receipt_documents_needing_pdf(INT)
  TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.claim_receipt_outbox_batch(INT)
  TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.complete_receipt_outbox(UUID, BOOLEAN, TEXT)
  TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.claim_sms_outbox_batch(INT)
  TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.complete_sms_outbox(UUID, BOOLEAN, TEXT, TEXT)
  TO authenticated, service_role;
