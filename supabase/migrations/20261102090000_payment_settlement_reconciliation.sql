-- Forward reconciliation of the retained legacy invoice path (207f0a39).
-- Keep the reserve-first commerce settlement and idempotency contract intact.
CREATE OR REPLACE FUNCTION private.resolve_payment_intent_allocations(
  p_allocations JSONB,
  p_metadata JSONB,
  p_amount NUMERIC,
  p_customer_id UUID,
  p_currency public.currency_code
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY INVOKER
SET search_path = ''
AS $$
DECLARE
  v_invoice_id UUID;
  v_invoice public.sales_invoices%ROWTYPE;
  v_raw TEXT := NULLIF(btrim(COALESCE(p_metadata ->> 'sales_invoice_id', '')), '');
BEGIN
  IF p_allocations IS NOT NULL AND jsonb_typeof(p_allocations) = 'array'
     AND jsonb_array_length(p_allocations) > 0 THEN
    RETURN p_allocations;
  END IF;
  IF v_raw IS NULL THEN RETURN NULL; END IF;
  BEGIN
    v_invoice_id := v_raw::UUID;
  EXCEPTION WHEN invalid_text_representation THEN
    RAISE EXCEPTION 'payment intent invoice identifier is invalid';
  END;
  SELECT * INTO v_invoice FROM public.sales_invoices WHERE id = v_invoice_id FOR UPDATE;
  IF NOT FOUND OR v_invoice.customer_id IS DISTINCT FROM p_customer_id
     OR v_invoice.currency IS DISTINCT FROM p_currency
     OR v_invoice.doc_type <> 'invoice' OR v_invoice.status <> 'posted' THEN
    RAISE EXCEPTION 'payment intent does not match an eligible customer invoice';
  END IF;
  IF p_amount IS NULL OR p_amount <= 0 THEN
    RAISE EXCEPTION 'payment intent amount must be positive';
  END IF;
  RETURN jsonb_build_array(jsonb_build_object('sales_invoice_id', v_invoice.id, 'amount', p_amount));
END;
$$;
REVOKE ALL ON FUNCTION private.resolve_payment_intent_allocations(JSONB,JSONB,NUMERIC,UUID,public.currency_code)
  FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION private.resolve_payment_intent_allocations(JSONB,JSONB,NUMERIC,UUID,public.currency_code)
  TO service_role;

CREATE OR REPLACE FUNCTION public.mark_contipay_settled(p_external_ref TEXT,p_payload_hash TEXT,p_provider_ref TEXT DEFAULT NULL,p_allocations JSONB DEFAULT NULL,p_settlement_currency public.currency_code DEFAULT NULL,p_settlement_amount NUMERIC DEFAULT NULL,p_settlement_exchange_rate NUMERIC DEFAULT NULL,p_success BOOLEAN DEFAULT true,p_failure_reason TEXT DEFAULT NULL)
RETURNS UUID LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
DECLARE v_intent public.contipay_payment_intents%ROWTYPE; v_webhook UUID; v_pe UUID; v_hash TEXT; v_allocations JSONB; BEGIN
 PERFORM public._require_payments_staff(); v_hash:=NULLIF(trim(COALESCE(p_payload_hash,'')),''); IF v_hash IS NULL THEN RAISE EXCEPTION 'payload_hash required for webhook idempotency'; END IF;
 INSERT INTO public.contipay_webhook_events(external_ref,payload_hash,processed,result_note) VALUES(p_external_ref,v_hash,false,'received') ON CONFLICT(payload_hash) DO NOTHING RETURNING id INTO v_webhook;
 IF v_webhook IS NULL THEN SELECT COALESCE(payment_entry_id,id) INTO v_pe FROM public.contipay_payment_intents WHERE external_ref=p_external_ref; RETURN v_pe; END IF;
 SELECT * INTO v_intent FROM public.contipay_payment_intents WHERE external_ref=p_external_ref FOR UPDATE; IF NOT FOUND THEN UPDATE public.contipay_webhook_events SET processed=true,result_note='unknown external_ref' WHERE id=v_webhook; RAISE EXCEPTION 'contipay intent not found: %',p_external_ref; END IF;
 UPDATE public.contipay_webhook_events SET intent_id=v_intent.id WHERE id=v_webhook; IF v_intent.status='settled' THEN UPDATE public.contipay_webhook_events SET processed=true,result_note='already settled' WHERE id=v_webhook; RETURN COALESCE(v_intent.payment_entry_id,v_intent.id); END IF;
 IF NOT COALESCE(p_success,true) THEN
  UPDATE public.contipay_payment_intents SET status='failed',failure_reason=p_failure_reason,webhook_payload_hash=v_hash,last_webhook_at=now(),provider_ref=COALESCE(p_provider_ref,provider_ref),updated_at=now() WHERE id=v_intent.id;
  IF v_intent.commerce_order_id IS NOT NULL THEN UPDATE public.commerce_orders SET state='payment_failed',payment_exception=p_failure_reason,updated_at=now() WHERE id=v_intent.commerce_order_id AND settled_payment_entry_id IS NULL AND state IN ('awaiting_payment','payment_processing','payment_failed');
   PERFORM private.enqueue_commerce_event('commerce:'||v_intent.commerce_order_id::text||':contipay-failed:'||v_intent.id::text,'commerce.payment_failed',v_intent.commerce_order_id,jsonb_build_object('provider','contipay','reason',p_failure_reason)); END IF;
  PERFORM public.emit_domain_event('payment_failed','contipay:fail:'||v_intent.id::text||':'||v_hash,jsonb_build_object('intent_id',v_intent.id,'external_ref',p_external_ref,'reason',p_failure_reason),auth.uid(),format('GTR Auto: ContiPay failed %s',p_external_ref));
  UPDATE public.contipay_webhook_events SET processed=true,result_note='marked failed' WHERE id=v_webhook; RETURN v_intent.id; END IF;
 IF v_intent.commerce_order_id IS NOT NULL THEN
  v_pe:=private.settle_commerce_payment(v_intent.commerce_order_id,'contipay',v_intent.id,p_provider_ref,v_intent.amount,v_intent.currency,v_intent.exchange_rate_applied,COALESCE(p_settlement_currency,v_intent.settlement_currency),COALESCE(p_settlement_amount,v_intent.settlement_amount),COALESCE(p_settlement_exchange_rate,v_intent.settlement_exchange_rate));
 ELSE
  IF v_intent.status='cancelled' THEN RAISE EXCEPTION 'cannot settle cancelled ContiPay intent'; END IF; IF v_intent.customer_id IS NULL THEN RAISE EXCEPTION 'settled ContiPay intent requires customer_id'; END IF;
  v_pe:=public.create_payment_entry(v_intent.customer_id,'contipay',v_intent.amount,v_intent.currency,v_intent.exchange_rate_applied,format('ContiPay %s',v_intent.external_ref),COALESCE(p_settlement_currency,v_intent.settlement_currency),COALESCE(p_settlement_amount,v_intent.settlement_amount),COALESCE(p_settlement_exchange_rate,v_intent.settlement_exchange_rate));
  v_allocations:=private.resolve_payment_intent_allocations(p_allocations,v_intent.metadata,v_intent.amount,v_intent.customer_id,v_intent.currency);
  IF v_allocations IS NOT NULL THEN PERFORM public.allocate_payment(v_pe,v_allocations); END IF; PERFORM public.post_payment_entry(v_pe); END IF;
 UPDATE public.contipay_payment_intents SET status='settled',payment_entry_id=v_pe,provider_ref=COALESCE(p_provider_ref,provider_ref),webhook_payload_hash=v_hash,last_webhook_at=now(),settlement_currency=COALESCE(p_settlement_currency,settlement_currency),settlement_amount=COALESCE(p_settlement_amount,settlement_amount),settlement_exchange_rate=COALESCE(p_settlement_exchange_rate,settlement_exchange_rate),updated_at=now() WHERE id=v_intent.id;
 UPDATE public.contipay_webhook_events SET processed=true,result_note='settled' WHERE id=v_webhook; RETURN v_pe; END; $$;

CREATE OR REPLACE FUNCTION public.mark_paynow_settled(p_external_ref TEXT,p_payload_hash TEXT,p_provider_ref TEXT DEFAULT NULL,p_allocations JSONB DEFAULT NULL,p_settlement_currency public.currency_code DEFAULT NULL,p_settlement_amount NUMERIC DEFAULT NULL,p_settlement_exchange_rate NUMERIC DEFAULT NULL,p_success BOOLEAN DEFAULT true,p_failure_reason TEXT DEFAULT NULL)
RETURNS UUID LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
DECLARE v_intent public.paynow_payment_intents%ROWTYPE; v_webhook UUID; v_pe UUID; v_hash TEXT; v_allocations JSONB; BEGIN
 PERFORM public._require_payments_staff(); v_hash:=NULLIF(trim(COALESCE(p_payload_hash,'')),''); IF v_hash IS NULL THEN RAISE EXCEPTION 'payload_hash required for webhook idempotency'; END IF;
 INSERT INTO public.paynow_webhook_events(external_ref,payload_hash,processed,result_note) VALUES(p_external_ref,v_hash,false,'received') ON CONFLICT(payload_hash) DO NOTHING RETURNING id INTO v_webhook;
 IF v_webhook IS NULL THEN SELECT COALESCE(payment_entry_id,id) INTO v_pe FROM public.paynow_payment_intents WHERE external_ref=p_external_ref; RETURN v_pe; END IF;
 SELECT * INTO v_intent FROM public.paynow_payment_intents WHERE external_ref=p_external_ref FOR UPDATE; IF NOT FOUND THEN UPDATE public.paynow_webhook_events SET processed=true,result_note='unknown external_ref' WHERE id=v_webhook; RAISE EXCEPTION 'paynow intent not found: %',p_external_ref; END IF;
 UPDATE public.paynow_webhook_events SET intent_id=v_intent.id WHERE id=v_webhook; IF v_intent.status='settled' THEN UPDATE public.paynow_webhook_events SET processed=true,result_note='already settled' WHERE id=v_webhook; RETURN COALESCE(v_intent.payment_entry_id,v_intent.id); END IF;
 IF NOT COALESCE(p_success,true) THEN
  UPDATE public.paynow_payment_intents SET status='failed',failure_reason=p_failure_reason,webhook_payload_hash=v_hash,last_webhook_at=now(),provider_ref=COALESCE(p_provider_ref,provider_ref),updated_at=now() WHERE id=v_intent.id;
  IF v_intent.commerce_order_id IS NOT NULL THEN UPDATE public.commerce_orders SET state='payment_failed',payment_exception=p_failure_reason,updated_at=now() WHERE id=v_intent.commerce_order_id AND settled_payment_entry_id IS NULL AND state IN ('awaiting_payment','payment_processing','payment_failed');
   PERFORM private.enqueue_commerce_event('commerce:'||v_intent.commerce_order_id::text||':paynow-failed:'||v_intent.id::text,'commerce.payment_failed',v_intent.commerce_order_id,jsonb_build_object('provider','paynow','reason',p_failure_reason)); END IF;
  PERFORM public.emit_domain_event('payment_failed','paynow:fail:'||v_intent.id::text||':'||v_hash,jsonb_build_object('intent_id',v_intent.id,'external_ref',p_external_ref,'reason',p_failure_reason),auth.uid(),format('GTR Auto: Paynow failed %s',p_external_ref));
  UPDATE public.paynow_webhook_events SET processed=true,result_note='marked failed' WHERE id=v_webhook; RETURN v_intent.id; END IF;
 IF v_intent.commerce_order_id IS NOT NULL THEN
  v_pe:=private.settle_commerce_payment(v_intent.commerce_order_id,'paynow',v_intent.id,p_provider_ref,v_intent.amount,v_intent.currency,v_intent.exchange_rate_applied,COALESCE(p_settlement_currency,v_intent.settlement_currency),COALESCE(p_settlement_amount,v_intent.settlement_amount),COALESCE(p_settlement_exchange_rate,v_intent.settlement_exchange_rate));
 ELSE
  IF v_intent.status='cancelled' THEN RAISE EXCEPTION 'cannot settle cancelled Paynow intent'; END IF; IF v_intent.customer_id IS NULL THEN RAISE EXCEPTION 'settled Paynow intent requires customer_id'; END IF;
  v_pe:=public.create_payment_entry(v_intent.customer_id,'paynow',v_intent.amount,v_intent.currency,v_intent.exchange_rate_applied,format('Paynow %s',v_intent.external_ref),COALESCE(p_settlement_currency,v_intent.settlement_currency),COALESCE(p_settlement_amount,v_intent.settlement_amount),COALESCE(p_settlement_exchange_rate,v_intent.settlement_exchange_rate));
  v_allocations:=private.resolve_payment_intent_allocations(p_allocations,v_intent.metadata,v_intent.amount,v_intent.customer_id,v_intent.currency);
  IF v_allocations IS NOT NULL THEN PERFORM public.allocate_payment(v_pe,v_allocations); END IF; PERFORM public.post_payment_entry(v_pe); END IF;
 UPDATE public.paynow_payment_intents SET status='settled',payment_entry_id=v_pe,provider_ref=COALESCE(p_provider_ref,provider_ref),webhook_payload_hash=v_hash,last_webhook_at=now(),settlement_currency=COALESCE(p_settlement_currency,settlement_currency),settlement_amount=COALESCE(p_settlement_amount,settlement_amount),settlement_exchange_rate=COALESCE(p_settlement_exchange_rate,settlement_exchange_rate),updated_at=now() WHERE id=v_intent.id;
 UPDATE public.paynow_webhook_events SET processed=true,result_note='settled' WHERE id=v_webhook; RETURN v_pe; END; $$;

REVOKE ALL ON FUNCTION public.mark_contipay_settled(TEXT,TEXT,TEXT,JSONB,public.currency_code,NUMERIC,NUMERIC,BOOLEAN,TEXT) FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.mark_paynow_settled(TEXT,TEXT,TEXT,JSONB,public.currency_code,NUMERIC,NUMERIC,BOOLEAN,TEXT) FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.mark_contipay_settled(TEXT,TEXT,TEXT,JSONB,public.currency_code,NUMERIC,NUMERIC,BOOLEAN,TEXT) TO service_role;
GRANT EXECUTE ON FUNCTION public.mark_paynow_settled(TEXT,TEXT,TEXT,JSONB,public.currency_code,NUMERIC,NUMERIC,BOOLEAN,TEXT) TO service_role;
