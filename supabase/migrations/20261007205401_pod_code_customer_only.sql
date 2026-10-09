-- Proof-of-delivery code fixes.
--
-- 1. generate_delivery_pod_otp returned the customer's code to whoever asked, including the driver,
--    who could then complete a delivery without the customer. It now returns the code only to back
--    office (dispatcher / warehouse / admin), who may read it to a customer by phone; the driver gets
--    NULL. The SMS to the customer is unchanged.
-- 2. The customer can read their current code on their order page / in the app
--    (get_my_delivery_codes), for when the SMS does not arrive.
-- 3. The driver app verifies the code, then submits; submit_delivery_pod verified it again and
--    failed with "no active POD OTP for job" because the code was already used. A code already
--    verified for the job is now accepted.

CREATE TABLE IF NOT EXISTS private.delivery_pod_codes (
  delivery_job_id uuid PRIMARY KEY REFERENCES public.delivery_jobs(id) ON DELETE CASCADE,
  code text NOT NULL,
  expires_at timestamptz NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now()
);
ALTER TABLE private.delivery_pod_codes ENABLE ROW LEVEL SECURITY;
-- No policies: read only through get_my_delivery_codes (SECURITY DEFINER).
REVOKE ALL ON private.delivery_pod_codes FROM PUBLIC, anon, authenticated;

CREATE OR REPLACE FUNCTION public.generate_delivery_pod_otp(p_delivery_job_id uuid, p_ttl interval DEFAULT '00:15:00'::interval)
 RETURNS text
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public', 'extensions'
AS $function$
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

  -- Optional SMS to customer â€” never blocks OTP return to driver UI
  PERFORM public._enqueue_delivery_customer_sms(
    p_delivery_job_id,
    'delivery_pod_otp',
    'delivery_pod_otp:' || p_delivery_job_id::text || ':' || v_hash,
    format('GTR Auto: Your delivery confirmation code is %s. Do not share.', v_code)
  );

  -- Kept for the customer's own order page / app (only they can read it), replaced on resend.
  INSERT INTO private.delivery_pod_codes(delivery_job_id, code, expires_at)
  VALUES (p_delivery_job_id, v_code, now() + COALESCE(p_ttl, interval '15 minutes'))
  ON CONFLICT (delivery_job_id) DO UPDATE SET code = EXCLUDED.code, expires_at = EXCLUDED.expires_at, created_at = now();

  -- The code proves the customer received the parts, so the driver never sees it. Back office may
  -- read it out to a customer who has no account or did not get the SMS.
  IF auth.role() = 'service_role'
     OR public.has_staff_role(ARRAY['admin', 'warehouse', 'dispatcher']::public.staff_role[]) THEN
    RETURN v_code;
  END IF;
  RETURN NULL;
END;
$function$;

CREATE OR REPLACE FUNCTION public.submit_delivery_pod(p_delivery_job_id uuid, p_pod_photo_path text, p_pod_signature_path text, p_otp_code text, p_notes text DEFAULT NULL::text)
 RETURNS uuid
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
DECLARE
  v_job public.delivery_jobs%ROWTYPE;
  v_uid UUID := auth.uid();
  v_allowed BOOLEAN := false;
  v_back_office BOOLEAN := false;
  v_inv public.sales_invoices%ROWTYPE;
  v_due NUMERIC;
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
    v_back_office := true;
  ELSIF v_uid IS NOT NULL
        AND v_job.assignee_user_id = v_uid
        AND public.has_staff_role(ARRAY['driver']::public.staff_role[]) THEN
    v_allowed := true;
  END IF;

  IF NOT v_allowed THEN
    RAISE EXCEPTION 'assigned driver or dispatcher/warehouse/admin required for POD';
  END IF;

  -- Cash / card on delivery: the driver cannot complete while the invoice still has a balance the
  -- driver is meant to collect, or while a card charge on the machine is unresolved (it may have taken
  -- money). Dispatch, warehouse and admin may complete anyway (e.g. the balance goes on account).
  IF NOT v_back_office THEN
    SELECT si.* INTO v_inv
    FROM public.delivery_notes dn
    JOIN public.sales_invoices si ON si.id = dn.sales_invoice_id
    WHERE dn.id = v_job.delivery_note_id;
    IF FOUND AND v_inv.delivery_payment_method IN ('cash_on_delivery','card_on_delivery','cash_or_card_on_delivery') THEN
      v_due := greatest(round(v_inv.total - v_inv.amount_paid, 2), 0);
      -- A balance the customer could not pay may stay on account once approved (credit check or dispatch).
      IF v_due > 0.004 AND NOT EXISTS (
        SELECT 1 FROM public.delivery_balance_approvals b
        WHERE b.delivery_job_id = p_delivery_job_id AND b.status IN ('auto_approved','approved')
          AND v_due <= b.amount + 0.01
      ) THEN
        RAISE EXCEPTION 'collect % % before completing this delivery (cash or card on delivery)', v_inv.currency, v_due;
      END IF;
    END IF;
    IF EXISTS (
      SELECT 1 FROM public.pos_card_terminal_attempts a
      WHERE a.delivery_job_id = p_delivery_job_id AND a.status IN ('initiated','approved','unknown')
    ) THEN
      RAISE EXCEPTION 'finish the card payment on this delivery before completing it';
    END IF;
  END IF;

  -- High: bind paths to this job + require objects in delivery-pods
  v_photo := public._assert_delivery_pod_object_for_job(
    p_delivery_job_id, p_pod_photo_path, 'photo'
  );
  v_sig := public._assert_delivery_pod_object_for_job(
    p_delivery_job_id, p_pod_signature_path, 'signature'
  );

  -- Verify OTP (raises on failure); unlocks complete. The driver app checks the code with
  -- verify_delivery_pod_otp first (and may submit later from its offline queue): a code already
  -- verified for this job is accepted.
  IF NOT EXISTS (
    SELECT 1 FROM public.delivery_pod_otps o
    WHERE o.delivery_job_id = p_delivery_job_id
      AND o.verified_at IS NOT NULL
      AND o.code_hash = public._hash_delivery_pod_otp(p_otp_code)
  ) THEN
    PERFORM public.verify_delivery_pod_otp(p_delivery_job_id, p_otp_code);
  END IF;
  DELETE FROM private.delivery_pod_codes WHERE delivery_job_id = p_delivery_job_id;

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
$function$;

-- Customer: codes for my deliveries that are on the way.
CREATE OR REPLACE FUNCTION public.get_my_delivery_codes()
 RETURNS jsonb LANGUAGE sql STABLE SECURITY DEFINER SET search_path TO '' AS $f$
 SELECT COALESCE(jsonb_agg(jsonb_build_object(
   'delivery_job_id', dj.id, 'job_number', dj.document_number, 'sales_invoice_id', si.id,
   'invoice_number', si.document_number, 'code', pc.code, 'expires_at', pc.expires_at) ORDER BY pc.created_at DESC), '[]'::jsonb)
 FROM private.delivery_pod_codes pc
 JOIN public.delivery_jobs dj ON dj.id = pc.delivery_job_id
 JOIN public.delivery_notes dn ON dn.id = dj.delivery_note_id
 JOIN public.sales_invoices si ON si.id = dn.sales_invoice_id
 JOIN public.customers c ON c.id = si.customer_id
 WHERE c.profile_id = auth.uid() AND auth.uid() IS NOT NULL
   AND dj.status = 'dispatched' AND pc.expires_at > now();
$f$;
REVOKE ALL ON FUNCTION public.get_my_delivery_codes() FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.get_my_delivery_codes() TO authenticated;
