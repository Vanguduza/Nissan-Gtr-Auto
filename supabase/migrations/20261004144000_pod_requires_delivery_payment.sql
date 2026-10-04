-- Cash / card on delivery is enforced by the server, not only the driver app: submit_delivery_pod
-- refuses the driver while a cash/card-on-delivery invoice has a balance or a delivery card charge is
-- unresolved. Dispatch / warehouse / admin can still complete (balance stays open on the invoice).
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
      IF v_due > 0.004 THEN
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

  -- Verify OTP (raises on failure); unlocks complete
  PERFORM public.verify_delivery_pod_otp(p_delivery_job_id, p_otp_code);

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
