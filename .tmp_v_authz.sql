-- Batch 2 verifier: claim RPC authz + fiscal artifact check (no full phase13)
CREATE OR REPLACE FUNCTION public._v_set_auth(p_uid UUID, p_role TEXT)
RETURNS void LANGUAGE plpgsql AS $$
BEGIN
  PERFORM set_config('request.jwt.claim.sub', p_uid::text, true);
  PERFORM set_config(
    'request.jwt.claims',
    json_build_object('sub', p_uid::text, 'role', p_role)::text,
    true
  );
  PERFORM set_config('request.jwt.claim.role', p_role, true);
END;
$$;

DO $$
DECLARE
  v_admin UUID := 'a0000000-0000-4000-8000-000000000001';
  v_ok BOOLEAN;
BEGIN
  -- authenticated must be denied by body check (even with EXECUTE if any)
  PERFORM public._v_set_auth(v_admin, 'authenticated');
  BEGIN
    PERFORM public.claim_receipt_outbox_batch(1);
    RAISE EXCEPTION 'FAIL: authenticated claimed receipt outbox';
  EXCEPTION WHEN OTHERS THEN
    IF SQLERRM LIKE 'FAIL:%' THEN RAISE; END IF;
    IF SQLERRM NOT ILIKE '%required%' AND SQLERRM NOT ILIKE '%permission denied%' THEN
      RAISE EXCEPTION 'FAIL unexpected receipt claim err: %', SQLERRM;
    END IF;
  END;

  BEGIN
    PERFORM public.claim_sms_outbox_batch(1);
    RAISE EXCEPTION 'FAIL: authenticated claimed sms outbox';
  EXCEPTION WHEN OTHERS THEN
    IF SQLERRM LIKE 'FAIL:%' THEN RAISE; END IF;
    IF SQLERRM NOT ILIKE '%required%' AND SQLERRM NOT ILIKE '%permission denied%' THEN
      RAISE EXCEPTION 'FAIL unexpected sms claim err: %', SQLERRM;
    END IF;
  END;

  BEGIN
    PERFORM public.list_receipt_documents_needing_pdf(1);
    RAISE EXCEPTION 'FAIL: authenticated listed needing pdf';
  EXCEPTION WHEN OTHERS THEN
    IF SQLERRM LIKE 'FAIL:%' THEN RAISE; END IF;
    IF SQLERRM NOT ILIKE '%required%' AND SQLERRM NOT ILIKE '%permission denied%' THEN
      RAISE EXCEPTION 'FAIL unexpected list pdf err: %', SQLERRM;
    END IF;
  END;

  BEGIN
    PERFORM public.process_receipt_outbox_batch(1, true);
    RAISE EXCEPTION 'FAIL: authenticated drained receipt stub';
  EXCEPTION WHEN OTHERS THEN
    IF SQLERRM LIKE 'FAIL:%' THEN RAISE; END IF;
    IF SQLERRM NOT ILIKE '%required%' AND SQLERRM NOT ILIKE '%permission denied%' THEN
      RAISE EXCEPTION 'FAIL unexpected receipt stub err: %', SQLERRM;
    END IF;
  END;

  BEGIN
    PERFORM public.drain_sms_outbox_batch(1, true);
    RAISE EXCEPTION 'FAIL: authenticated drained sms stub';
  EXCEPTION WHEN OTHERS THEN
    IF SQLERRM LIKE 'FAIL:%' THEN RAISE; END IF;
    IF SQLERRM NOT ILIKE '%required%' AND SQLERRM NOT ILIKE '%permission denied%' THEN
      RAISE EXCEPTION 'FAIL unexpected sms stub err: %', SQLERRM;
    END IF;
  END;

  -- worker role can claim (may return 0 rows)
  PERFORM public._v_set_auth(v_admin, 'service_role');
  PERFORM public.claim_receipt_outbox_batch(1);
  PERFORM public.claim_sms_outbox_batch(1);
  PERFORM public.list_receipt_documents_needing_pdf(1);

  IF EXISTS (SELECT 1 FROM public.receipt_pdf_artifacts WHERE has_fiscal_payload = true) THEN
    RAISE EXCEPTION 'FAIL: fiscal payload flag set';
  END IF;

  IF NOT EXISTS (SELECT 1 FROM storage.buckets WHERE id = 'customer-receipts' AND public = false) THEN
    RAISE EXCEPTION 'FAIL: customer-receipts bucket missing or public';
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema='public' AND table_name='customer_receipt_outbox' AND column_name='claimed_at'
  ) THEN
    RAISE EXCEPTION 'FAIL: claimed_at missing on receipt outbox';
  END IF;

  RAISE NOTICE 'BATCH2_AUTHZ_OK';
END;
$$;

DROP FUNCTION public._v_set_auth(UUID, TEXT);
