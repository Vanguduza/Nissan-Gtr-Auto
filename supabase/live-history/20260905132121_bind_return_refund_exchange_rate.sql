-- Exported from the hosted project's supabase_migrations.schema_migrations (20260905132121 bind_return_refund_exchange_rate).
-- Source of record for what production ran; see supabase/live-history/README.md.

ALTER TABLE public.return_refund_requests
  ADD COLUMN IF NOT EXISTS exchange_rate_applied numeric NOT NULL DEFAULT 1;

CREATE OR REPLACE FUNCTION public.capture_return_refund_exchange_rate()
RETURNS trigger
LANGUAGE plpgsql
SET search_path TO ''
AS $$
DECLARE v_rate numeric; v_currency public.currency_code;
BEGIN
  SELECT si.exchange_rate_applied,si.currency INTO v_rate,v_currency
  FROM public.sales_invoices si WHERE si.id=NEW.source_invoice_id;
  IF NOT FOUND THEN RAISE EXCEPTION 'source invoice required for refund'; END IF;
  NEW.exchange_rate_applied:=CASE WHEN v_currency='USD' THEN COALESCE(v_rate,1) ELSE v_rate END;
  IF NEW.exchange_rate_applied IS NULL OR NEW.exchange_rate_applied<=0 THEN
    RAISE EXCEPTION 'valid source invoice exchange rate required for refund';
  END IF;
  RETURN NEW;
END;
$$;
DROP TRIGGER IF EXISTS return_refund_capture_exchange_rate ON public.return_refund_requests;
CREATE TRIGGER return_refund_capture_exchange_rate
BEFORE INSERT ON public.return_refund_requests
FOR EACH ROW EXECUTE FUNCTION public.capture_return_refund_exchange_rate();

CREATE OR REPLACE FUNCTION public.settle_return_refund_store_credit(p_return_request_id uuid,p_notes text DEFAULT NULL)
RETURNS uuid
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO ''
AS $$
DECLARE v_ref public.return_refund_requests%ROWTYPE; v_journal uuid; v_ledger uuid;
BEGIN
  IF NOT (auth.role()='service_role' OR public.has_staff_role(ARRAY['admin','finance']::public.staff_role[])) THEN
    RAISE EXCEPTION 'finance or admin role required';
  END IF;
  SELECT * INTO v_ref FROM public.return_refund_requests WHERE return_request_id=p_return_request_id FOR UPDATE;
  IF NOT FOUND THEN RAISE EXCEPTION 'pending refund not found'; END IF;
  IF v_ref.status='completed' THEN RETURN v_ref.id; END IF;
  IF v_ref.status NOT IN ('pending','failed') THEN RAISE EXCEPTION 'refund cannot settle in status %',v_ref.status; END IF;
  PERFORM public._payments_rpc_enter();
  v_journal:=public.post_journal_entry(CURRENT_DATE,COALESCE(p_notes,'Return refund converted to store credit'),v_ref.currency,v_ref.exchange_rate_applied,
    jsonb_build_array(
      jsonb_build_object('account_code','2220','debit',v_ref.amount,'credit',0,'currency',v_ref.currency),
      jsonb_build_object('account_code','2200','debit',0,'credit',v_ref.amount,'currency',v_ref.currency)
    ));
  v_ledger:=public._append_store_credit(v_ref.customer_id,'issue',v_ref.amount,v_ref.currency,v_ref.exchange_rate_applied,NULL,v_journal,COALESCE(p_notes,'Return refund converted to store credit'));
  UPDATE public.return_refund_requests SET method='store_credit',status='completed',processed_by=auth.uid(),processed_at=now(),failure_reason=NULL,updated_at=now() WHERE id=v_ref.id;
  UPDATE public.customer_return_requests SET status='completed',updated_at=now() WHERE id=p_return_request_id;
  PERFORM public.emit_domain_event('refund_completed','return:refund:store-credit:'||v_ref.id::text,
    jsonb_build_object('refund_request_id',v_ref.id,'return_request_id',p_return_request_id,'method','store_credit','ledger_id',v_ledger,'amount',v_ref.amount),auth.uid(),NULL);
  RETURN v_ref.id;
END;
$$;

CREATE OR REPLACE FUNCTION public.complete_return_refund_manual(p_return_request_id uuid,p_tender public.payment_tender,p_settlement_reference text)
RETURNS uuid
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO ''
AS $$
DECLARE v_ref public.return_refund_requests%ROWTYPE; v_gl text; v_journal uuid;
BEGIN
  IF NOT (auth.role()='service_role' OR public.has_staff_role(ARRAY['admin','finance']::public.staff_role[])) THEN
    RAISE EXCEPTION 'finance or admin role required';
  END IF;
  IF p_tender NOT IN ('cash'::public.payment_tender,'bank'::public.payment_tender) THEN
    RAISE EXCEPTION 'manual return refund supports cash or bank only; provider refunds require verified provider completion';
  END IF;
  IF NULLIF(trim(COALESCE(p_settlement_reference,'')),'') IS NULL THEN RAISE EXCEPTION 'settlement reference required'; END IF;
  SELECT * INTO v_ref FROM public.return_refund_requests WHERE return_request_id=p_return_request_id FOR UPDATE;
  IF NOT FOUND THEN RAISE EXCEPTION 'pending refund not found'; END IF;
  IF v_ref.status='completed' THEN RETURN v_ref.id; END IF;
  IF v_ref.status NOT IN ('pending','failed') THEN RAISE EXCEPTION 'refund cannot settle in status %',v_ref.status; END IF;
  v_gl:=public.gl_account_for_payment_tender(p_tender);
  v_journal:=public.post_journal_entry(CURRENT_DATE,format('Return refund %s %s',p_tender,p_settlement_reference),v_ref.currency,v_ref.exchange_rate_applied,
    jsonb_build_array(
      jsonb_build_object('account_code','2220','debit',v_ref.amount,'credit',0,'currency',v_ref.currency),
      jsonb_build_object('account_code',v_gl,'debit',0,'credit',v_ref.amount,'currency',v_ref.currency)
    ));
  UPDATE public.return_refund_requests
  SET method=CASE WHEN p_tender='cash' THEN 'cash'::public.return_resolution_method ELSE 'bank'::public.return_resolution_method END,
      status='completed',settlement_reference=trim(p_settlement_reference),processed_by=auth.uid(),processed_at=now(),failure_reason=NULL,updated_at=now()
  WHERE id=v_ref.id;
  UPDATE public.customer_return_requests SET status='completed',updated_at=now() WHERE id=p_return_request_id;
  PERFORM public.emit_domain_event('refund_completed','return:refund:manual:'||v_ref.id::text,
    jsonb_build_object('refund_request_id',v_ref.id,'return_request_id',p_return_request_id,'method',p_tender,'reference',p_settlement_reference,'amount',v_ref.amount),auth.uid(),NULL);
  RETURN v_ref.id;
END;
$$;
