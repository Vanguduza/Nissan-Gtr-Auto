-- Phase 13: customer receipt PDF artifacts + delivery hooks
-- Tax-agnostic receipts; NO ZIMRA / FDMS / fiscal QR.
-- Public download host: https://nissangtrauto.co.zw (see company-domain decision).
-- Storage bucket `customer-receipts` is private; signed URLs minted by edge/worker.

CREATE TABLE public.receipt_pdf_artifacts (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  document_type TEXT NOT NULL CHECK (document_type IN ('sales_invoice', 'credit_note')),
  document_id UUID NOT NULL REFERENCES public.sales_invoices (id) ON DELETE CASCADE,
  pdf_storage_path TEXT NOT NULL,
  download_token TEXT NOT NULL UNIQUE,
  download_url TEXT NOT NULL,
  signed_url_expires_at TIMESTAMPTZ,
  content_sha256 TEXT,
  byte_size INT,
  -- Explicit: no fiscal markers stored or required
  has_fiscal_payload BOOLEAN NOT NULL DEFAULT false CHECK (has_fiscal_payload = false),
  generated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  generated_by UUID REFERENCES auth.users (id),
  UNIQUE (document_id)
);

CREATE INDEX receipt_pdf_artifacts_token_idx ON public.receipt_pdf_artifacts (download_token);

ALTER TABLE public.customer_receipt_outbox
  ADD COLUMN IF NOT EXISTS receipt_pdf_artifact_id UUID
    REFERENCES public.receipt_pdf_artifacts (id) ON DELETE SET NULL;

COMMENT ON TABLE public.receipt_pdf_artifacts IS
  'Private Storage path + public company-domain download URL for tax-agnostic customer receipts. Bucket: customer-receipts (private).';

CREATE OR REPLACE FUNCTION public.mark_receipt_pdf_ready(
  p_document_id UUID,
  p_storage_path TEXT,
  p_download_token TEXT DEFAULT NULL,
  p_expires_at TIMESTAMPTZ DEFAULT (now() + interval '7 days'),
  p_byte_size INT DEFAULT NULL,
  p_content_sha256 TEXT DEFAULT NULL
)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_inv public.sales_invoices%ROWTYPE;
  v_token TEXT;
  v_url TEXT;
  v_art UUID;
  v_doc_type TEXT;
BEGIN
  IF NOT (
    auth.role() = 'service_role'
    OR public.has_staff_role(ARRAY['admin', 'sales', 'finance']::public.staff_role[])
  ) THEN
    RAISE EXCEPTION 'staff or service role required for receipt PDF';
  END IF;

  SELECT * INTO v_inv FROM public.sales_invoices WHERE id = p_document_id;
  IF NOT FOUND OR v_inv.status <> 'posted' THEN
    RAISE EXCEPTION 'posted invoice/credit note required';
  END IF;

  IF p_storage_path IS NULL OR length(trim(p_storage_path)) = 0 THEN
    RAISE EXCEPTION 'pdf storage path required';
  END IF;

  -- Reject accidental fiscal/ZIMRA path markers in storage key
  IF p_storage_path ~* '(zimra|fdms|fiscal)' THEN
    RAISE EXCEPTION 'fiscal storage paths are forbidden';
  END IF;

  v_token := COALESCE(NULLIF(trim(p_download_token), ''), replace(gen_random_uuid()::text, '-', ''));
  v_url := 'https://nissangtrauto.co.zw/receipts/' || v_token;
  v_doc_type := CASE WHEN v_inv.doc_type = 'credit_note' THEN 'credit_note' ELSE 'sales_invoice' END;

  INSERT INTO public.receipt_pdf_artifacts (
    document_type, document_id, pdf_storage_path, download_token, download_url,
    signed_url_expires_at, byte_size, content_sha256, generated_by
  )
  VALUES (
    v_doc_type, p_document_id, trim(p_storage_path), v_token, v_url,
    p_expires_at, p_byte_size, p_content_sha256, auth.uid()
  )
  ON CONFLICT (document_id) DO UPDATE
  SET
    pdf_storage_path = EXCLUDED.pdf_storage_path,
    download_token = EXCLUDED.download_token,
    download_url = EXCLUDED.download_url,
    signed_url_expires_at = EXCLUDED.signed_url_expires_at,
    byte_size = COALESCE(EXCLUDED.byte_size, public.receipt_pdf_artifacts.byte_size),
    content_sha256 = COALESCE(EXCLUDED.content_sha256, public.receipt_pdf_artifacts.content_sha256),
    generated_at = now(),
    generated_by = auth.uid()
  RETURNING id INTO v_art;

  UPDATE public.customer_receipt_outbox o
  SET
    pdf_storage_path = trim(p_storage_path),
    download_url = v_url,
    receipt_pdf_artifact_id = v_art,
    summary_body = CASE
      WHEN o.channel IN ('sms', 'whatsapp') THEN
        regexp_replace(o.summary_body, E'\\n\\[PDF link pending\\]$', '')
        || E'\n' || v_url
      ELSE o.summary_body
    END
  WHERE o.document_id = p_document_id
    AND o.status IN ('pending', 'rendering', 'failed');

  RETURN v_art;
END;
$$;

-- Stub-friendly channel drain: marks pending rows sent (or failed) after PDF ready.
-- Real SMS/email/WhatsApp adapters live in process-customer-receipts edge function.
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
BEGIN
  IF NOT (
    auth.role() = 'service_role'
    OR public.has_staff_role(ARRAY['admin', 'sales', 'finance']::public.staff_role[])
  ) THEN
    RAISE EXCEPTION 'staff or service role required';
  END IF;

  FOR v_row IN
    SELECT *
    FROM public.customer_receipt_outbox
    WHERE status IN ('pending', 'failed')
      AND download_url IS NOT NULL
      AND attempt_count < 8
    ORDER BY created_at
    LIMIT GREATEST(1, COALESCE(p_limit, 50))
    FOR UPDATE SKIP LOCKED
  LOOP
    -- Idempotent: never re-send successful channels
    IF v_row.status = 'sent' THEN
      CONTINUE;
    END IF;

    IF p_stub_success THEN
      UPDATE public.customer_receipt_outbox
      SET
        status = 'sent',
        attempt_count = attempt_count + 1,
        last_error = NULL,
        sent_at = now()
      WHERE id = v_row.id
        AND status IS DISTINCT FROM 'sent';
    ELSE
      UPDATE public.customer_receipt_outbox
      SET
        status = 'failed',
        attempt_count = attempt_count + 1,
        last_error = COALESCE(last_error, 'stub send failed')
      WHERE id = v_row.id
        AND status IS DISTINCT FROM 'sent';
    END IF;

    v_count := v_count + 1;
  END LOOP;

  RETURN v_count;
END;
$$;

-- Manager SMS outbox drain (gateway stub). Prefs already filtered at emit time.
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
BEGIN
  IF NOT (
    auth.role() = 'service_role'
    OR public.has_staff_role(ARRAY['admin']::public.staff_role[])
  ) THEN
    RAISE EXCEPTION 'admin or service role required to drain SMS outbox';
  END IF;

  FOR v_row IN
    SELECT *
    FROM public.sms_outbox
    WHERE status IN ('pending', 'failed')
      AND attempt_count < 8
    ORDER BY created_at
    LIMIT GREATEST(1, COALESCE(p_limit, 50))
    FOR UPDATE SKIP LOCKED
  LOOP
    IF p_stub_success THEN
      UPDATE public.sms_outbox
      SET
        status = 'sent',
        attempt_count = attempt_count + 1,
        last_error = NULL,
        sent_at = now(),
        provider_message_id = COALESCE(provider_message_id, 'stub-' || id::text)
      WHERE id = v_row.id
        AND status IS DISTINCT FROM 'sent';
    ELSE
      UPDATE public.sms_outbox
      SET
        status = 'failed',
        attempt_count = attempt_count + 1,
        last_error = COALESCE(last_error, 'stub SMS gateway failed')
      WHERE id = v_row.id
        AND status IS DISTINCT FROM 'sent';
    END IF;
    v_count := v_count + 1;
  END LOOP;

  RETURN v_count;
END;
$$;

ALTER TABLE public.receipt_pdf_artifacts ENABLE ROW LEVEL SECURITY;

CREATE POLICY receipt_pdf_artifacts_staff_select ON public.receipt_pdf_artifacts
  FOR SELECT TO authenticated
  USING (public.has_staff_role(ARRAY['admin', 'sales', 'finance']::public.staff_role[]));

-- Allow service role worker to update outbox status (via SECURITY DEFINER RPCs only)

REVOKE ALL ON FUNCTION public.mark_receipt_pdf_ready(
  UUID, TEXT, TEXT, TIMESTAMPTZ, INT, TEXT
) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.process_receipt_outbox_batch(INT, BOOLEAN) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.drain_sms_outbox_batch(INT, BOOLEAN) FROM PUBLIC;

GRANT EXECUTE ON FUNCTION public.mark_receipt_pdf_ready(
  UUID, TEXT, TEXT, TIMESTAMPTZ, INT, TEXT
) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.process_receipt_outbox_batch(INT, BOOLEAN)
  TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.drain_sms_outbox_batch(INT, BOOLEAN)
  TO authenticated, service_role;
