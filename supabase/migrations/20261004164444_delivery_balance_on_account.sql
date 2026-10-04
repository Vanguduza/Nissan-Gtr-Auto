-- Part settlement on cash/card on delivery: when the customer pays part and cannot pay the rest, the
-- driver asks to leave the balance on account. It is approved at once when the customer has a trade
-- account in good standing whose limit covers what they owe (this invoice included); otherwise it waits
-- for a dispatcher, finance, admin or POS approver to decide in the back office. submit_delivery_pod
-- then lets the driver complete with that (or a smaller) balance open on the invoice.

CREATE TABLE public.delivery_balance_approvals (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  delivery_job_id uuid NOT NULL REFERENCES public.delivery_jobs(id) ON DELETE CASCADE,
  sales_invoice_id uuid NOT NULL REFERENCES public.sales_invoices(id),
  customer_id uuid REFERENCES public.customers(id),
  amount numeric(14,2) NOT NULL CHECK (amount > 0),
  currency public.currency_code NOT NULL,
  reason text NOT NULL CHECK (length(trim(reason)) > 0),
  status text NOT NULL CHECK (status IN ('pending','auto_approved','approved','refused','cancelled')),
  basis text NOT NULL CHECK (basis IN ('credit_limit','back_office')),
  credit_limit numeric(14,2),
  exposure numeric(14,2),
  requested_by uuid NOT NULL DEFAULT auth.uid(),
  decided_by uuid,
  decided_at timestamptz,
  decision_note text,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX delivery_balance_approvals_job_idx ON public.delivery_balance_approvals(delivery_job_id, created_at DESC);
CREATE INDEX delivery_balance_approvals_pending_idx ON public.delivery_balance_approvals(status) WHERE status = 'pending';
CREATE UNIQUE INDEX delivery_balance_approvals_one_open ON public.delivery_balance_approvals(delivery_job_id) WHERE status IN ('pending','auto_approved','approved');

ALTER TABLE public.delivery_balance_approvals ENABLE ROW LEVEL SECURITY;
-- Reads: the requesting driver and the back office. Writes only through the functions below.
CREATE POLICY delivery_balance_approvals_read ON public.delivery_balance_approvals FOR SELECT TO authenticated
  USING (requested_by = auth.uid() OR public.has_staff_role(ARRAY['admin','dispatcher','finance']::public.staff_role[]) OR public.is_pos_approver());

CREATE OR REPLACE FUNCTION private._delivery_balance_approval_json(b public.delivery_balance_approvals)
 RETURNS jsonb LANGUAGE sql STABLE SECURITY DEFINER SET search_path TO '' AS $f$
 SELECT jsonb_build_object('id',b.id,'delivery_job_id',b.delivery_job_id,'sales_invoice_id',b.sales_invoice_id,'customer_id',b.customer_id,
  'customer_name',(SELECT c.display_name FROM public.customers c WHERE c.id=b.customer_id),
  'job_number',(SELECT j.document_number FROM public.delivery_jobs j WHERE j.id=b.delivery_job_id),
  'invoice_number',(SELECT si.document_number FROM public.sales_invoices si WHERE si.id=b.sales_invoice_id),
  'invoice_total',(SELECT si.total FROM public.sales_invoices si WHERE si.id=b.sales_invoice_id),
  'amount_paid',(SELECT si.amount_paid FROM public.sales_invoices si WHERE si.id=b.sales_invoice_id),
  'amount',b.amount,'currency',b.currency,'reason',b.reason,'status',b.status,'basis',b.basis,
  'credit_limit',b.credit_limit,'exposure',b.exposure,
  'requested_by_name',(SELECT p.full_name FROM public.profiles p WHERE p.id=b.requested_by),
  'decided_by_name',(SELECT p.full_name FROM public.profiles p WHERE p.id=b.decided_by),
  'decided_at',b.decided_at,'decision_note',b.decision_note,'created_at',b.created_at)
$f$;

-- Driver: leave the unpaid balance of this delivery on account.
CREATE OR REPLACE FUNCTION public.request_delivery_balance_on_account(p_delivery_job_id uuid, p_reason text)
 RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path TO '' AS $f$
DECLARE j public.delivery_jobs%ROWTYPE; i public.sales_invoices%ROWTYPE; c public.customers%ROWTYPE;
  v_due numeric; v_exposure numeric; b public.delivery_balance_approvals%ROWTYPE; v_auto boolean := false;
BEGIN
 SELECT * INTO j FROM public.delivery_jobs WHERE id=p_delivery_job_id FOR UPDATE;
 IF NOT FOUND OR j.status<>'dispatched' OR j.assignee_user_id IS DISTINCT FROM auth.uid() OR NOT public.has_staff_role(ARRAY['driver']::public.staff_role[]) THEN
  RAISE EXCEPTION 'assigned dispatched driver job required'; END IF;
 IF trim(COALESCE(p_reason,''))='' THEN RAISE EXCEPTION 'say why the customer cannot pay the rest'; END IF;
 SELECT si.* INTO i FROM public.delivery_notes dn JOIN public.sales_invoices si ON si.id=dn.sales_invoice_id WHERE dn.id=j.delivery_note_id;
 IF NOT FOUND OR i.delivery_payment_method NOT IN('cash_on_delivery','card_on_delivery','cash_or_card_on_delivery') THEN
  RAISE EXCEPTION 'this delivery is not cash or card on delivery'; END IF;
 IF EXISTS(SELECT 1 FROM public.pos_card_terminal_attempts a WHERE a.delivery_job_id=j.id AND a.status IN('initiated','approved','unknown')) THEN
  RAISE EXCEPTION 'finish the card payment on this delivery first'; END IF;
 v_due:=greatest(round(i.total-i.amount_paid,2),0);
 IF v_due<=0.004 THEN RAISE EXCEPTION 'nothing is owed on this delivery'; END IF;
 -- One open request per delivery: a repeat returns it (or replaces a pending one whose balance changed).
 SELECT * INTO b FROM public.delivery_balance_approvals WHERE delivery_job_id=j.id AND status IN('pending','auto_approved','approved') FOR UPDATE;
 IF FOUND THEN
  IF b.status<>'pending' AND v_due<=b.amount+0.01 THEN RETURN private._delivery_balance_approval_json(b); END IF;
  UPDATE public.delivery_balance_approvals SET status='cancelled',updated_at=now() WHERE id=b.id;
 END IF;
 IF i.customer_id IS NOT NULL THEN
  SELECT * INTO c FROM public.customers WHERE id=i.customer_id;
  SELECT COALESCE(sum(greatest(x.total-x.amount_paid,0)),0) INTO v_exposure FROM public.sales_invoices x
   WHERE x.customer_id=i.customer_id AND x.status='posted';
  v_auto := NOT COALESCE(c.credit_hold,false) AND COALESCE(c.credit_limit,0)>0 AND v_exposure<=c.credit_limit+0.01;
 END IF;
 INSERT INTO public.delivery_balance_approvals(delivery_job_id,sales_invoice_id,customer_id,amount,currency,reason,status,basis,credit_limit,exposure,decided_at)
 VALUES(j.id,i.id,i.customer_id,v_due,i.currency,trim(p_reason),CASE WHEN v_auto THEN 'auto_approved' ELSE 'pending' END,
  CASE WHEN v_auto THEN 'credit_limit' ELSE 'back_office' END,c.credit_limit,v_exposure,CASE WHEN v_auto THEN now() END)
 RETURNING * INTO b;
 RETURN private._delivery_balance_approval_json(b);
END $f$;

-- Dispatcher, finance, admin or POS approver: approve or refuse a pending request.
CREATE OR REPLACE FUNCTION public.decide_delivery_balance_on_account(p_approval_id uuid, p_approve boolean, p_note text DEFAULT NULL)
 RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path TO '' AS $f$
DECLARE b public.delivery_balance_approvals%ROWTYPE;
BEGIN
 IF NOT (public.has_staff_role(ARRAY['admin','dispatcher','finance']::public.staff_role[]) OR public.is_pos_approver()) THEN
  RAISE EXCEPTION 'dispatcher, finance, admin or approver required'; END IF;
 SELECT * INTO b FROM public.delivery_balance_approvals WHERE id=p_approval_id FOR UPDATE;
 IF NOT FOUND OR b.status<>'pending' THEN RAISE EXCEPTION 'pending request required'; END IF;
 IF b.requested_by = auth.uid() THEN RAISE EXCEPTION 'someone else must decide your own request'; END IF;
 IF NOT p_approve AND trim(COALESCE(p_note,''))='' THEN RAISE EXCEPTION 'say why it is refused'; END IF;
 UPDATE public.delivery_balance_approvals SET status=CASE WHEN p_approve THEN 'approved' ELSE 'refused' END,
  decided_by=auth.uid(),decided_at=now(),decision_note=NULLIF(trim(COALESCE(p_note,'')),''),updated_at=now()
 WHERE id=b.id RETURNING * INTO b;
 RETURN private._delivery_balance_approval_json(b);
END $f$;

-- Back office queue (pending first), or one status.
CREATE OR REPLACE FUNCTION public.list_delivery_balance_approvals(p_status text DEFAULT NULL, p_limit integer DEFAULT 100)
 RETURNS jsonb LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path TO '' AS $f$
BEGIN
 IF NOT (public.has_staff_role(ARRAY['admin','dispatcher','finance']::public.staff_role[]) OR public.is_pos_approver()) THEN
  RAISE EXCEPTION 'dispatcher, finance, admin or approver required'; END IF;
 RETURN COALESCE((SELECT jsonb_agg(private._delivery_balance_approval_json(b) ORDER BY (b.status='pending') DESC, b.created_at DESC)
  FROM (SELECT * FROM public.delivery_balance_approvals WHERE p_status IS NULL OR status=p_status ORDER BY created_at DESC LIMIT LEAST(GREATEST(COALESCE(p_limit,100),1),500)) b),'[]'::jsonb);
END $f$;

-- Driver: the latest request on this delivery, if any.
CREATE OR REPLACE FUNCTION public.get_delivery_balance_approval(p_delivery_job_id uuid)
 RETURNS jsonb LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path TO '' AS $f$
DECLARE b public.delivery_balance_approvals%ROWTYPE;
BEGIN
 SELECT * INTO b FROM public.delivery_balance_approvals WHERE delivery_job_id=p_delivery_job_id ORDER BY created_at DESC LIMIT 1;
 IF NOT FOUND THEN RETURN NULL; END IF;
 IF NOT (b.requested_by=auth.uid() OR public.has_staff_role(ARRAY['admin','dispatcher','finance']::public.staff_role[]) OR public.is_pos_approver()) THEN
  RAISE EXCEPTION 'not allowed'; END IF;
 RETURN private._delivery_balance_approval_json(b);
END $f$;

REVOKE ALL ON FUNCTION private._delivery_balance_approval_json(public.delivery_balance_approvals) FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.request_delivery_balance_on_account(uuid, text) FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.decide_delivery_balance_on_account(uuid, boolean, text) FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.list_delivery_balance_approvals(text, integer) FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.get_delivery_balance_approval(uuid) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.request_delivery_balance_on_account(uuid, text) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.decide_delivery_balance_on_account(uuid, boolean, text) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.list_delivery_balance_approvals(text, integer) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.get_delivery_balance_approval(uuid) TO authenticated, service_role;
REVOKE ALL ON TABLE public.delivery_balance_approvals FROM anon;
GRANT SELECT ON TABLE public.delivery_balance_approvals TO authenticated;

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
