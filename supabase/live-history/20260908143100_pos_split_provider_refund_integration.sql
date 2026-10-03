-- Exported from the hosted project's supabase_migrations.schema_migrations (20260908143100 pos_split_provider_refund_integration).
-- Source of record for what production ran; see supabase/live-history/README.md.

-- Provider/card-terminal integration and refund lifecycle for staged POS split payments.

-- Wrap the existing full-order settlement primitive so provider webhooks can safely
-- capture partial split legs without finalizing the order prematurely.
ALTER FUNCTION private.settle_commerce_payment(UUID,TEXT,UUID,TEXT,NUMERIC,public.currency_code,NUMERIC,public.currency_code,NUMERIC,NUMERIC)
 RENAME TO settle_commerce_payment_full_legacy;

CREATE OR REPLACE FUNCTION private.capture_pos_split_external_leg(
 p_leg_id UUID,p_provider TEXT,p_provider_intent_id UUID,p_provider_ref TEXT,p_amount NUMERIC,p_currency public.currency_code,
 p_exchange_rate NUMERIC,p_settlement_currency public.currency_code DEFAULT NULL,p_settlement_amount NUMERIC DEFAULT NULL,
 p_settlement_exchange_rate NUMERIC DEFAULT NULL)
RETURNS UUID LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
DECLARE l public.pos_split_payment_legs%ROWTYPE; s public.pos_split_payment_sessions%ROWTYPE; o public.commerce_orders%ROWTYPE; v_pe UUID;
BEGIN
 SELECT * INTO l FROM public.pos_split_payment_legs WHERE id=p_leg_id FOR UPDATE;
 IF NOT FOUND THEN RAISE EXCEPTION 'split payment leg not found'; END IF;
 IF l.tender::text<>p_provider THEN RAISE EXCEPTION 'provider % does not match split leg tender %',p_provider,l.tender; END IF;
 IF l.status IN('captured','allocated','refund_review','refund_pending') AND l.payment_entry_id IS NOT NULL THEN RETURN l.payment_entry_id; END IF;
 IF l.status NOT IN('planned','pending','unknown') THEN RAISE EXCEPTION 'split leg cannot be captured in status %',l.status; END IF;
 SELECT * INTO s FROM public.pos_split_payment_sessions WHERE id=l.session_id FOR UPDATE;
 SELECT * INTO o FROM public.commerce_orders WHERE id=s.commerce_order_id FOR UPDATE;
 IF abs(round(p_amount,2)-l.requested_amount)>0.01 OR p_currency<>s.currency THEN RAISE EXCEPTION 'captured provider amount/currency does not match locked split leg'; END IF;
 v_pe:=public.create_payment_entry(o.customer_id,l.tender,l.requested_amount,s.currency,o.exchange_rate_applied,
  format('POS split %s %s',p_provider,s.id),p_settlement_currency,p_settlement_amount,p_settlement_exchange_rate);
 PERFORM private.post_pos_split_unapplied_payment_entry(v_pe,l.id);
 UPDATE public.pos_split_payment_legs SET status='captured',provider_intent_id=COALESCE(p_provider_intent_id,provider_intent_id),
  provider_ref=COALESCE(NULLIF(trim(COALESCE(p_provider_ref,'')),''),provider_ref),payment_entry_id=v_pe,locked_at=COALESCE(locked_at,now()),
  status_detail=NULL,updated_at=now() WHERE id=l.id;
 UPDATE public.commerce_orders SET active_payment_provider=NULL,active_payment_intent_id=NULL,payment_exception=NULL,updated_at=now()
  WHERE id=o.id AND (active_payment_intent_id IS NULL OR active_payment_intent_id=p_provider_intent_id);
 PERFORM private.refresh_pos_split_payment_session(s.id);
 IF (SELECT status FROM public.pos_split_payment_sessions WHERE id=s.id)='fully_committed' THEN PERFORM private.finalize_pos_split_payment_session(s.id); END IF;
 RETURN v_pe;
END $$;

REVOKE ALL ON FUNCTION private.capture_pos_split_external_leg(UUID,TEXT,UUID,TEXT,NUMERIC,public.currency_code,NUMERIC,public.currency_code,NUMERIC,NUMERIC)
 FROM PUBLIC,anon,authenticated;

CREATE OR REPLACE FUNCTION private.settle_commerce_payment(
 p_order_id UUID,p_provider TEXT,p_provider_intent_id UUID,p_provider_ref TEXT,p_amount NUMERIC,p_currency public.currency_code,
 p_exchange_rate NUMERIC,p_settlement_currency public.currency_code,p_settlement_amount NUMERIC,p_settlement_exchange_rate NUMERIC)
RETURNS UUID LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
DECLARE v_leg UUID;
BEGIN
 SELECT l.id INTO v_leg FROM public.pos_split_payment_legs l JOIN public.pos_split_payment_sessions s ON s.id=l.session_id
 WHERE s.commerce_order_id=p_order_id AND l.tender::text=p_provider AND l.provider_intent_id=p_provider_intent_id
 ORDER BY l.sequence_no LIMIT 1;
 IF v_leg IS NOT NULL THEN
  RETURN private.capture_pos_split_external_leg(v_leg,p_provider,p_provider_intent_id,p_provider_ref,p_amount,p_currency,p_exchange_rate,
    p_settlement_currency,p_settlement_amount,p_settlement_exchange_rate);
 END IF;
 RETURN private.settle_commerce_payment_full_legacy(p_order_id,p_provider,p_provider_intent_id,p_provider_ref,p_amount,p_currency,p_exchange_rate,
   p_settlement_currency,p_settlement_amount,p_settlement_exchange_rate);
END $$;

REVOKE ALL ON FUNCTION private.settle_commerce_payment(UUID,TEXT,UUID,TEXT,NUMERIC,public.currency_code,NUMERIC,public.currency_code,NUMERIC,NUMERIC)
 FROM PUBLIC,anon,authenticated;

CREATE OR REPLACE FUNCTION private.fail_pos_split_provider_leg(p_provider_intent_id UUID,p_provider TEXT,p_reason TEXT)
RETURNS void LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
DECLARE l public.pos_split_payment_legs%ROWTYPE; s public.pos_split_payment_sessions%ROWTYPE;
BEGIN
 SELECT * INTO l FROM public.pos_split_payment_legs WHERE provider_intent_id=p_provider_intent_id AND tender::text=p_provider FOR UPDATE;
 IF NOT FOUND OR l.status IN('captured','allocated','refund_review','refund_pending','refunded','cancelled') THEN RETURN; END IF;
 UPDATE public.pos_split_payment_legs SET status='failed',status_detail=left(COALESCE(p_reason,'provider payment failed'),500),failed_at=now(),updated_at=now() WHERE id=l.id;
 SELECT * INTO s FROM public.pos_split_payment_sessions WHERE id=l.session_id;
 UPDATE public.commerce_orders SET active_payment_provider=NULL,active_payment_intent_id=NULL,updated_at=now() WHERE id=s.commerce_order_id AND active_payment_intent_id=p_provider_intent_id;
 PERFORM private.refresh_pos_split_payment_session(l.session_id);
END $$;

REVOKE ALL ON FUNCTION private.fail_pos_split_provider_leg(UUID,TEXT,TEXT) FROM PUBLIC,anon,authenticated;

CREATE OR REPLACE FUNCTION private.sync_pos_split_provider_intent_status()
RETURNS trigger LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
DECLARE v_provider TEXT; v_status TEXT; l public.pos_split_payment_legs%ROWTYPE;
BEGIN
 v_provider:=CASE TG_TABLE_NAME WHEN 'ecocash_payment_intents' THEN 'ecocash' WHEN 'paynow_payment_intents' THEN 'paynow' WHEN 'contipay_payment_intents' THEN 'contipay' ELSE NULL END;
 IF v_provider IS NULL THEN RETURN NEW; END IF;
 SELECT * INTO l FROM public.pos_split_payment_legs WHERE provider_intent_id=NEW.id AND tender::text=v_provider FOR UPDATE;
 IF NOT FOUND THEN RETURN NEW; END IF;
 v_status:=NEW.status::text;
 IF v_status IN('failed','cancelled') THEN
  PERFORM private.fail_pos_split_provider_leg(NEW.id,v_provider,COALESCE(NEW.failure_reason,v_status));
 ELSIF v_status IN('pending','authorized') AND l.status='planned' THEN
  UPDATE public.pos_split_payment_legs SET status='pending',updated_at=now() WHERE id=l.id;
  PERFORM private.refresh_pos_split_payment_session(l.session_id);
 END IF;
 RETURN NEW;
END $$;

REVOKE ALL ON FUNCTION private.sync_pos_split_provider_intent_status() FROM PUBLIC,anon,authenticated;

DROP TRIGGER IF EXISTS pos_split_ecocash_status_sync ON public.ecocash_payment_intents;

CREATE TRIGGER pos_split_ecocash_status_sync AFTER UPDATE OF status ON public.ecocash_payment_intents FOR EACH ROW EXECUTE FUNCTION private.sync_pos_split_provider_intent_status();

DROP TRIGGER IF EXISTS pos_split_paynow_status_sync ON public.paynow_payment_intents;

CREATE TRIGGER pos_split_paynow_status_sync AFTER UPDATE OF status ON public.paynow_payment_intents FOR EACH ROW EXECUTE FUNCTION private.sync_pos_split_provider_intent_status();

DROP TRIGGER IF EXISTS pos_split_contipay_status_sync ON public.contipay_payment_intents;

CREATE TRIGGER pos_split_contipay_status_sync AFTER UPDATE OF status ON public.contipay_payment_intents FOR EACH ROW EXECUTE FUNCTION private.sync_pos_split_provider_intent_status();

CREATE OR REPLACE FUNCTION public.create_pos_split_ecocash_intent(p_leg_id UUID,p_payer_msisdn TEXT,p_external_ref TEXT,p_metadata JSONB DEFAULT '{}'::jsonb)
RETURNS UUID LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
DECLARE l public.pos_split_payment_legs%ROWTYPE; s public.pos_split_payment_sessions%ROWTYPE; o public.commerce_orders%ROWTYPE; v_msisdn TEXT; v_id UUID;
BEGIN
 PERFORM public._require_payments_staff(); SELECT * INTO l FROM public.pos_split_payment_legs WHERE id=p_leg_id FOR UPDATE;
 IF NOT FOUND OR l.tender<>'ecocash' OR l.status<>'planned' THEN RAISE EXCEPTION 'planned EcoCash split leg required'; END IF;
 SELECT * INTO s FROM public.pos_split_payment_sessions WHERE id=l.session_id FOR UPDATE; SELECT * INTO o FROM public.commerce_orders WHERE id=s.commerce_order_id FOR UPDATE;
 v_msisdn:=regexp_replace(trim(COALESCE(p_payer_msisdn,'')),'[^0-9]','','g'); IF v_msisdn~'^0[0-9]{9}$' THEN v_msisdn:='263'||substr(v_msisdn,2); END IF;
 IF v_msisdn!~'^263[0-9]{9}$' THEN RAISE EXCEPTION 'payer_msisdn must normalize to 263XXXXXXXXX'; END IF;
 INSERT INTO public.ecocash_payment_intents(external_ref,payer_msisdn,payer_mode,channel,customer_id,amount,currency,exchange_rate_applied,commerce_order_id,metadata,created_by)
 VALUES(trim(p_external_ref),v_msisdn,'pos_entered','pos',o.customer_id,l.requested_amount,s.currency,o.exchange_rate_applied,o.id,
  COALESCE(p_metadata,'{}'::jsonb)||jsonb_build_object('pos_commerce_order_id',o.id,'pos_split_leg_id',l.id),auth.uid()) RETURNING id INTO v_id;
 UPDATE public.pos_split_payment_legs SET status='pending',provider_intent_id=v_id,external_reference=trim(p_external_ref),updated_at=now() WHERE id=l.id;
 UPDATE public.commerce_orders SET state='payment_processing',active_payment_provider='ecocash',active_payment_intent_id=v_id,payment_exception=NULL,updated_at=now() WHERE id=o.id;
 PERFORM private.refresh_pos_split_payment_session(s.id); RETURN v_id;
END $$;

CREATE OR REPLACE FUNCTION public.create_pos_split_paynow_intent(p_leg_id UUID,p_method public.paynow_method,p_external_ref TEXT,p_metadata JSONB DEFAULT '{}'::jsonb)
RETURNS UUID LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
DECLARE l public.pos_split_payment_legs%ROWTYPE; s public.pos_split_payment_sessions%ROWTYPE; o public.commerce_orders%ROWTYPE; v_id UUID;
BEGIN
 PERFORM public._require_payments_staff(); SELECT * INTO l FROM public.pos_split_payment_legs WHERE id=p_leg_id FOR UPDATE;
 IF NOT FOUND OR l.tender<>'paynow' OR l.status<>'planned' THEN RAISE EXCEPTION 'planned Paynow split leg required'; END IF;
 SELECT * INTO s FROM public.pos_split_payment_sessions WHERE id=l.session_id FOR UPDATE; SELECT * INTO o FROM public.commerce_orders WHERE id=s.commerce_order_id FOR UPDATE;
 INSERT INTO public.paynow_payment_intents(external_ref,method,customer_id,amount,currency,exchange_rate_applied,commerce_order_id,metadata,created_by)
 VALUES(trim(p_external_ref),p_method,o.customer_id,l.requested_amount,s.currency,o.exchange_rate_applied,o.id,
  COALESCE(p_metadata,'{}'::jsonb)||jsonb_build_object('pos_commerce_order_id',o.id,'pos_split_leg_id',l.id),auth.uid()) RETURNING id INTO v_id;
 UPDATE public.pos_split_payment_legs SET status='pending',provider_intent_id=v_id,external_reference=trim(p_external_ref),updated_at=now() WHERE id=l.id;
 UPDATE public.commerce_orders SET state='payment_processing',active_payment_provider='paynow',active_payment_intent_id=v_id,payment_exception=NULL,updated_at=now() WHERE id=o.id;
 PERFORM private.refresh_pos_split_payment_session(s.id); RETURN v_id;
END $$;

CREATE OR REPLACE FUNCTION public.create_pos_split_contipay_intent(p_leg_id UUID,p_method public.contipay_method,p_external_ref TEXT,p_metadata JSONB DEFAULT '{}'::jsonb)
RETURNS UUID LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
DECLARE l public.pos_split_payment_legs%ROWTYPE; s public.pos_split_payment_sessions%ROWTYPE; o public.commerce_orders%ROWTYPE; v_id UUID;
BEGIN
 PERFORM public._require_payments_staff(); SELECT * INTO l FROM public.pos_split_payment_legs WHERE id=p_leg_id FOR UPDATE;
 IF NOT FOUND OR l.tender<>'contipay' OR l.status<>'planned' THEN RAISE EXCEPTION 'planned ContiPay split leg required'; END IF;
 SELECT * INTO s FROM public.pos_split_payment_sessions WHERE id=l.session_id FOR UPDATE; SELECT * INTO o FROM public.commerce_orders WHERE id=s.commerce_order_id FOR UPDATE;
 INSERT INTO public.contipay_payment_intents(external_ref,method,customer_id,amount,currency,exchange_rate_applied,commerce_order_id,metadata,created_by)
 VALUES(trim(p_external_ref),p_method,o.customer_id,l.requested_amount,s.currency,o.exchange_rate_applied,o.id,
  COALESCE(p_metadata,'{}'::jsonb)||jsonb_build_object('pos_commerce_order_id',o.id,'pos_split_leg_id',l.id),auth.uid()) RETURNING id INTO v_id;
 UPDATE public.pos_split_payment_legs SET status='pending',provider_intent_id=v_id,external_reference=trim(p_external_ref),updated_at=now() WHERE id=l.id;
 UPDATE public.commerce_orders SET state='payment_processing',active_payment_provider='contipay',active_payment_intent_id=v_id,payment_exception=NULL,updated_at=now() WHERE id=o.id;
 PERFORM private.refresh_pos_split_payment_session(s.id); RETURN v_id;
END $$;

REVOKE ALL ON FUNCTION public.create_pos_split_ecocash_intent(UUID,TEXT,TEXT,JSONB),
 public.create_pos_split_paynow_intent(UUID,public.paynow_method,TEXT,JSONB),public.create_pos_split_contipay_intent(UUID,public.contipay_method,TEXT,JSONB) FROM PUBLIC,anon;

GRANT EXECUTE ON FUNCTION public.create_pos_split_ecocash_intent(UUID,TEXT,TEXT,JSONB),
 public.create_pos_split_paynow_intent(UUID,public.paynow_method,TEXT,JSONB),public.create_pos_split_contipay_intent(UUID,public.contipay_method,TEXT,JSONB) TO authenticated,service_role;

-- Card terminal attempts can belong to a staged split leg or refund request.
ALTER TABLE public.pos_card_terminal_attempts ADD COLUMN IF NOT EXISTS split_leg_id UUID REFERENCES public.pos_split_payment_legs(id) ON DELETE RESTRICT;

ALTER TABLE public.pos_card_terminal_attempts ADD COLUMN IF NOT EXISTS split_refund_request_id UUID REFERENCES public.pos_split_refund_requests(id) ON DELETE RESTRICT;

CREATE INDEX IF NOT EXISTS pos_card_terminal_attempt_split_leg_idx ON public.pos_card_terminal_attempts(split_leg_id);

CREATE INDEX IF NOT EXISTS pos_card_terminal_attempt_split_refund_idx ON public.pos_card_terminal_attempts(split_refund_request_id);

ALTER TABLE public.pos_card_terminal_attempts DROP CONSTRAINT IF EXISTS pos_card_terminal_attempts_check;

ALTER TABLE public.pos_card_terminal_attempts ADD CONSTRAINT pos_card_terminal_attempts_check CHECK(
 (operation='purchase' AND commerce_order_id IS NOT NULL AND source_invoice_id IS NULL)
 OR (operation='refund' AND (source_invoice_id IS NOT NULL OR split_refund_request_id IS NOT NULL))
 OR (operation='reversal' AND parent_attempt_id IS NOT NULL));

CREATE OR REPLACE FUNCTION private.pos_card_terminal_attempt_payload(p_attempt_id UUID)
RETURNS JSONB LANGUAGE sql STABLE SECURITY DEFINER SET search_path='' AS $$
 SELECT jsonb_build_object(
  'attempt_id',a.id,'request_id',a.request_id,'operation',a.operation,'status',a.status,
  'terminal_id',a.terminal_id,'amount',a.amount,'currency',a.currency,'external_ref',a.external_ref,
  'terminal_transaction_id',a.terminal_transaction_id,'rrn',a.rrn,'authorization_code',a.authorization_code,
  'card_last4',a.card_last4,'card_scheme',a.card_scheme,'response_code',a.response_code,'response_message',a.response_message,
  'commerce_order_id',a.commerce_order_id,'source_invoice_id',a.source_invoice_id,'parent_attempt_id',a.parent_attempt_id,
  'split_leg_id',a.split_leg_id,'split_refund_request_id',a.split_refund_request_id,
  'payment_entry_id',a.payment_entry_id,'invoice_id',a.invoice_id,'finance_refund_id',a.finance_refund_id,'finalization_error',a.finalization_error,
  'terminal',jsonb_build_object('id',t.id,'code',t.code,'label',t.label,'acquirer_name',t.acquirer_name,
   'external_terminal_id',t.external_terminal_id,'adapter_key',t.adapter_key,'adapter_config',t.adapter_config)
 ) FROM public.pos_card_terminal_attempts a JOIN public.pos_card_terminals t ON t.id=a.terminal_id WHERE a.id=p_attempt_id;
$$;

REVOKE ALL ON FUNCTION private.pos_card_terminal_attempt_payload(UUID) FROM PUBLIC,anon,authenticated;

CREATE OR REPLACE FUNCTION public.begin_pos_split_card_terminal_leg(p_leg_id UUID,p_terminal_id UUID,p_request_id UUID)
RETURNS JSONB LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
DECLARE l public.pos_split_payment_legs%ROWTYPE; s public.pos_split_payment_sessions%ROWTYPE; o public.commerce_orders%ROWTYPE;
 c public.pos_carts%ROWTYPE; ts public.pos_till_sessions%ROWTYPE; t public.pos_card_terminals%ROWTYPE; a public.pos_card_terminal_attempts%ROWTYPE; v_ref TEXT;
BEGIN
 PERFORM public._require_payments_staff(); SELECT * INTO a FROM public.pos_card_terminal_attempts WHERE request_id=p_request_id;
 IF FOUND THEN RETURN private.pos_card_terminal_attempt_payload(a.id); END IF;
 SELECT * INTO l FROM public.pos_split_payment_legs WHERE id=p_leg_id FOR UPDATE;
 IF NOT FOUND OR l.tender<>'card_terminal' OR l.status<>'planned' THEN RAISE EXCEPTION 'planned card-terminal split leg required'; END IF;
 SELECT * INTO s FROM public.pos_split_payment_sessions WHERE id=l.session_id FOR UPDATE; SELECT * INTO o FROM public.commerce_orders WHERE id=s.commerce_order_id FOR UPDATE;
 SELECT * INTO c FROM public.pos_carts WHERE id=o.cart_id; SELECT * INTO ts FROM public.pos_till_sessions WHERE id=c.till_session_id;
 IF c.created_by<>auth.uid() OR ts.status<>'open' OR ts.operator_user_id<>auth.uid() THEN RAISE EXCEPTION 'active operator till required'; END IF;
 SELECT * INTO t FROM public.pos_card_terminals WHERE id=p_terminal_id;
 IF NOT FOUND OR NOT t.is_active OR (t.warehouse_id IS NOT NULL AND t.warehouse_id<>o.warehouse_id) OR (t.device_id IS NOT NULL AND t.device_id<>ts.device_id) THEN
  RAISE EXCEPTION 'active swipe machine assigned to this warehouse/device required'; END IF;
 v_ref:='GTR-CTS-'||replace(p_request_id::text,'-','');
 INSERT INTO public.pos_card_terminal_attempts(request_id,operation,terminal_id,commerce_order_id,split_leg_id,amount,currency,external_ref,created_by)
 VALUES(p_request_id,'purchase',p_terminal_id,o.id,l.id,l.requested_amount,s.currency,v_ref,auth.uid()) RETURNING * INTO a;
 UPDATE public.pos_split_payment_legs SET status='pending',card_terminal_attempt_id=a.id,external_reference=v_ref,updated_at=now() WHERE id=l.id;
 UPDATE public.commerce_orders SET state='payment_processing',active_payment_provider='card_terminal',active_payment_intent_id=a.id,payment_exception=NULL,updated_at=now() WHERE id=o.id;
 PERFORM private.refresh_pos_split_payment_session(s.id); RETURN private.pos_card_terminal_attempt_payload(a.id);
END $$;

REVOKE ALL ON FUNCTION public.begin_pos_split_card_terminal_leg(UUID,UUID,UUID) FROM PUBLIC,anon;

GRANT EXECUTE ON FUNCTION public.begin_pos_split_card_terminal_leg(UUID,UUID,UUID) TO authenticated,service_role;

ALTER FUNCTION public.finalize_pos_card_terminal_purchase(UUID) RENAME TO finalize_pos_card_terminal_purchase_full_legacy;

CREATE OR REPLACE FUNCTION public.finalize_pos_card_terminal_purchase(p_attempt_id UUID)
RETURNS JSONB LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
DECLARE a public.pos_card_terminal_attempts%ROWTYPE; v_pe UUID; v_inv UUID;
BEGIN
 SELECT * INTO a FROM public.pos_card_terminal_attempts WHERE id=p_attempt_id FOR UPDATE;
 IF NOT FOUND THEN RAISE EXCEPTION 'card terminal attempt not found'; END IF;
 IF a.split_leg_id IS NULL THEN RETURN public.finalize_pos_card_terminal_purchase_full_legacy(p_attempt_id); END IF;
 PERFORM public._require_payments_staff();
 IF a.created_by<>auth.uid() AND NOT public.is_pos_approver() AND NOT public.has_staff_role(ARRAY['finance']::public.staff_role[]) THEN RAISE EXCEPTION 'card terminal attempt access denied'; END IF;
 IF a.status='settled' THEN RETURN private.pos_card_terminal_attempt_payload(a.id); END IF;
 IF a.status<>'approved' THEN RAISE EXCEPTION 'terminal approval required before split capture'; END IF;
 v_pe:=private.capture_pos_split_external_leg(a.split_leg_id,'card_terminal',a.id,a.terminal_transaction_id,a.amount,a.currency,1,NULL,NULL,NULL);
 SELECT final_invoice_id INTO v_inv FROM public.pos_split_payment_sessions s JOIN public.pos_split_payment_legs l ON l.session_id=s.id WHERE l.id=a.split_leg_id;
 UPDATE public.pos_card_terminal_attempts SET status='settled',payment_entry_id=v_pe,invoice_id=v_inv,finalization_error=NULL,
  finalized_by=auth.uid(),finalized_at=now(),updated_at=now() WHERE id=a.id;
 RETURN private.pos_card_terminal_attempt_payload(a.id);
END $$;

REVOKE ALL ON FUNCTION public.finalize_pos_card_terminal_purchase(UUID) FROM PUBLIC,anon;

GRANT EXECUTE ON FUNCTION public.finalize_pos_card_terminal_purchase(UUID) TO authenticated,service_role;

CREATE OR REPLACE FUNCTION private.sync_pos_split_card_attempt_status()
RETURNS trigger LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
DECLARE l public.pos_split_payment_legs%ROWTYPE;
BEGIN
 IF NEW.split_leg_id IS NULL THEN RETURN NEW; END IF;
 SELECT * INTO l FROM public.pos_split_payment_legs WHERE id=NEW.split_leg_id FOR UPDATE; IF NOT FOUND THEN RETURN NEW; END IF;
 IF NEW.status='unknown' THEN UPDATE public.pos_split_payment_legs SET status='unknown',status_detail=COALESCE(NEW.response_message,'Swipe-machine outcome unknown'),updated_at=now() WHERE id=l.id AND status IN('pending','planned');
 ELSIF NEW.status IN('declined','cancelled','failed','reversed') THEN UPDATE public.pos_split_payment_legs SET status='failed',status_detail=COALESCE(NEW.response_message,NEW.status::text),failed_at=now(),updated_at=now() WHERE id=l.id AND status IN('pending','planned','unknown');
 END IF;
 PERFORM private.refresh_pos_split_payment_session(l.session_id); RETURN NEW;
END $$;

REVOKE ALL ON FUNCTION private.sync_pos_split_card_attempt_status() FROM PUBLIC,anon,authenticated;

DROP TRIGGER IF EXISTS pos_split_card_attempt_status_sync ON public.pos_card_terminal_attempts;

CREATE TRIGGER pos_split_card_attempt_status_sync AFTER UPDATE OF status ON public.pos_card_terminal_attempts FOR EACH ROW EXECUTE FUNCTION private.sync_pos_split_card_attempt_status();

-- Abort/cancel flow never assumes an external refund is immediate or fee-free.
CREATE OR REPLACE FUNCTION public.request_pos_split_cancellation(p_session_id UUID,p_reason TEXT,p_fee_policy TEXT DEFAULT 'manual_review')
RETURNS JSONB LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
DECLARE s public.pos_split_payment_sessions%ROWTYPE; l public.pos_split_payment_legs%ROWTYPE;
BEGIN
 PERFORM public._require_sales_staff(); IF p_fee_policy NOT IN('business_absorbs','customer_bears','manual_review') THEN RAISE EXCEPTION 'unsupported refund fee policy'; END IF;
 PERFORM private.refresh_pos_split_payment_session(p_session_id); SELECT * INTO s FROM public.pos_split_payment_sessions WHERE id=p_session_id FOR UPDATE;
 IF s.status='settled' THEN RAISE EXCEPTION 'settled sale must use the posted invoice return/refund workflow'; END IF;
 IF EXISTS(SELECT 1 FROM public.pos_split_payment_legs WHERE session_id=s.id AND status IN('pending','unknown')) THEN RAISE EXCEPTION 'reconcile every in-flight payment before cancelling the split sale'; END IF;
 UPDATE public.pos_split_payment_legs SET status='cancelled',updated_at=now() WHERE session_id=s.id AND status IN('planned','failed','held');
 FOR l IN SELECT * FROM public.pos_split_payment_legs WHERE session_id=s.id AND status='captured' FOR UPDATE LOOP
  INSERT INTO public.pos_split_refund_requests(session_id,leg_id,gross_amount,fee_policy,external_reference,notes,created_by)
  VALUES(s.id,l.id,l.requested_amount,p_fee_policy,'GTR-SPLIT-REFUND-'||l.id::text,p_reason,auth.uid()) ON CONFLICT(leg_id) DO NOTHING;
  UPDATE public.pos_split_payment_legs SET status='refund_review',status_detail='Refund requires explicit manager/provider confirmation',updated_at=now() WHERE id=l.id;
 END LOOP;
 PERFORM private.release_commerce_order(s.commerce_order_id,'cancelled',concat_ws(' · ','POS split cancelled; captured funds retained in refund liability',p_reason));
 IF EXISTS(SELECT 1 FROM public.pos_split_payment_legs WHERE session_id=s.id AND status='refund_review') THEN
  UPDATE public.pos_split_payment_sessions SET status='refund_review',cancelled_at=now(),updated_at=now() WHERE id=s.id;
 ELSE UPDATE public.pos_split_payment_sessions SET status='cancelled',cancelled_at=now(),updated_at=now() WHERE id=s.id; END IF;
 RETURN private.pos_split_payment_payload(s.id);
END $$;

CREATE OR REPLACE FUNCTION public.approve_pos_split_refund(
 p_refund_id UUID,p_fee_policy TEXT,p_estimated_provider_fee NUMERIC DEFAULT 0,p_estimated_transfer_fee NUMERIC DEFAULT 0,
 p_customer_fee NUMERIC DEFAULT 0,p_expected_days INTEGER DEFAULT 3,p_notes TEXT DEFAULT NULL)
RETURNS JSONB LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
DECLARE r public.pos_split_refund_requests%ROWTYPE; l public.pos_split_payment_legs%ROWTYPE;
BEGIN
 IF NOT (public.is_pos_approver() OR public.has_staff_role(ARRAY['finance']::public.staff_role[])) THEN RAISE EXCEPTION 'manager or finance approval required'; END IF;
 IF p_fee_policy NOT IN('business_absorbs','customer_bears','manual_review') THEN RAISE EXCEPTION 'unsupported refund fee policy'; END IF;
 SELECT * INTO r FROM public.pos_split_refund_requests WHERE id=p_refund_id FOR UPDATE; IF NOT FOUND OR r.status NOT IN('review','failed') THEN RAISE EXCEPTION 'review/failed refund request required'; END IF;
 SELECT * INTO l FROM public.pos_split_payment_legs WHERE id=r.leg_id FOR UPDATE;
 IF COALESCE(p_estimated_provider_fee,0)<0 OR COALESCE(p_estimated_transfer_fee,0)<0 OR COALESCE(p_customer_fee,0)<0 OR p_customer_fee>r.gross_amount THEN RAISE EXCEPTION 'invalid refund fee values'; END IF;
 IF p_customer_fee>0 AND p_fee_policy<>'customer_bears' THEN RAISE EXCEPTION 'customer fee deduction requires customer_bears policy'; END IF;
 UPDATE public.pos_split_refund_requests SET status='pending',fee_policy=p_fee_policy,estimated_provider_fee=round(COALESCE(p_estimated_provider_fee,0),2),
  estimated_transfer_fee=round(COALESCE(p_estimated_transfer_fee,0),2),approved_customer_fee=round(COALESCE(p_customer_fee,0),2),
  expected_settlement_at=now()+make_interval(days=>GREATEST(COALESCE(p_expected_days,3),0)),approved_by=auth.uid(),approved_at=now(),notes=concat_ws(E'\n',notes,p_notes),updated_at=now()
 WHERE id=r.id;
 UPDATE public.pos_split_payment_legs SET status='refund_pending',updated_at=now() WHERE id=l.id;
 UPDATE public.pos_split_payment_sessions SET status='refund_pending',updated_at=now() WHERE id=r.session_id;
 RETURN private.pos_split_payment_payload(r.session_id);
END $$;

CREATE OR REPLACE FUNCTION public.complete_pos_split_refund(
 p_refund_id UUID,p_provider_ref TEXT,p_actual_provider_fee NUMERIC DEFAULT 0,p_actual_transfer_fee NUMERIC DEFAULT 0,
 p_actual_customer_fee NUMERIC DEFAULT 0,p_notes TEXT DEFAULT NULL)
RETURNS JSONB LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
DECLARE r public.pos_split_refund_requests%ROWTYPE; l public.pos_split_payment_legs%ROWTYPE; pe public.payment_entries%ROWTYPE;
 s public.pos_split_payment_sessions%ROWTYPE; v_fee NUMERIC; v_customer_fee NUMERIC; v_net NUMERIC; v_acct TEXT; v_journal UUID; v_remaining INTEGER;
BEGIN
 IF NOT (auth.role()='service_role' OR public.is_pos_approver() OR public.has_staff_role(ARRAY['finance']::public.staff_role[])) THEN RAISE EXCEPTION 'manager, finance, or service role required'; END IF;
 SELECT * INTO r FROM public.pos_split_refund_requests WHERE id=p_refund_id FOR UPDATE; IF NOT FOUND THEN RAISE EXCEPTION 'split refund request not found'; END IF;
 IF r.status='settled' THEN RETURN private.pos_split_payment_payload(r.session_id); END IF;
 IF r.status NOT IN('pending','review','failed') THEN RAISE EXCEPTION 'refund cannot settle in status %',r.status; END IF;
 SELECT * INTO l FROM public.pos_split_payment_legs WHERE id=r.leg_id FOR UPDATE; SELECT * INTO pe FROM public.payment_entries WHERE id=l.payment_entry_id FOR UPDATE;
 SELECT * INTO s FROM public.pos_split_payment_sessions WHERE id=r.session_id FOR UPDATE;
 IF pe.status<>'posted' OR EXISTS(SELECT 1 FROM public.payment_allocations WHERE payment_entry_id=pe.id) THEN RAISE EXCEPTION 'unallocated posted split receipt required'; END IF;
 v_fee:=round(COALESCE(p_actual_provider_fee,0)+COALESCE(p_actual_transfer_fee,0),2); v_customer_fee:=round(COALESCE(p_actual_customer_fee,0),2);
 IF v_fee<0 OR v_customer_fee<0 OR v_customer_fee>r.gross_amount THEN RAISE EXCEPTION 'invalid actual refund fees'; END IF;
 IF v_customer_fee>0 AND r.fee_policy<>'customer_bears' THEN RAISE EXCEPTION 'customer fee deduction not approved by refund policy'; END IF;
 v_net:=round(r.gross_amount-v_customer_fee,2); v_acct:=public.gl_account_for_payment_tender(l.tender);
 PERFORM public._payments_rpc_enter();
 v_journal:=public.post_journal_entry(CURRENT_DATE,format('POS split refund %s',r.id),s.currency,1,
  jsonb_build_array(
   jsonb_build_object('account_code','2215','debit',r.gross_amount,'credit',0,'currency',s.currency),
   jsonb_build_object('account_code','6250','debit',v_fee,'credit',0,'currency',s.currency),
   jsonb_build_object('account_code',v_acct,'debit',0,'credit',v_net+v_fee,'currency',s.currency),
   jsonb_build_object('account_code','4250','debit',0,'credit',v_customer_fee,'currency',s.currency)
  ));
 UPDATE public.payment_entries SET status='cancelled',reversal_journal_entry_id=v_journal,cancelled_at=now(),updated_at=now() WHERE id=pe.id;
 UPDATE public.pos_split_refund_requests SET status='settled',actual_provider_fee=COALESCE(p_actual_provider_fee,0),actual_transfer_fee=COALESCE(p_actual_transfer_fee,0),
  actual_customer_fee=v_customer_fee,net_customer_refund=v_net,provider_ref=NULLIF(trim(COALESCE(p_provider_ref,'')),''),settled_by=auth.uid(),settled_at=now(),
  notes=concat_ws(E'\n',notes,p_notes),failure_reason=NULL,updated_at=now() WHERE id=r.id;
 UPDATE public.pos_split_payment_legs SET status='refunded',provider_ref=COALESCE(NULLIF(trim(COALESCE(p_provider_ref,'')),''),provider_ref),refunded_at=now(),updated_at=now() WHERE id=l.id;
 SELECT count(*) INTO v_remaining FROM public.pos_split_payment_legs WHERE session_id=s.id AND status IN('captured','refund_review','refund_pending','held','pending','unknown');
 IF v_remaining=0 THEN UPDATE public.pos_split_payment_sessions SET status='refunded',refunded_amount=total_amount,updated_at=now() WHERE id=s.id;
 ELSE PERFORM private.refresh_pos_split_payment_session(s.id); UPDATE public.pos_split_payment_sessions SET status='refund_pending',updated_at=now() WHERE id=s.id; END IF;
 PERFORM public.emit_domain_event('refund_issued','pos-split:refund:'||r.id::text,jsonb_build_object('split_refund_id',r.id,'leg_id',l.id,'gross_amount',r.gross_amount,
  'net_customer_refund',v_net,'provider_fee',COALESCE(p_actual_provider_fee,0),'transfer_fee',COALESCE(p_actual_transfer_fee,0),'customer_fee',v_customer_fee,'tender',l.tender,'provider_ref',p_provider_ref),
  auth.uid(),format('GTR Auto: split refund settled %s %s net',v_net,s.currency));
 RETURN private.pos_split_payment_payload(s.id);
END $$;

CREATE OR REPLACE FUNCTION public.fail_pos_split_refund(p_refund_id UUID,p_reason TEXT)
RETURNS JSONB LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
DECLARE r public.pos_split_refund_requests%ROWTYPE;
BEGIN
 IF NOT (public.is_pos_approver() OR public.has_staff_role(ARRAY['finance']::public.staff_role[])) THEN RAISE EXCEPTION 'manager or finance approval required'; END IF;
 SELECT * INTO r FROM public.pos_split_refund_requests WHERE id=p_refund_id FOR UPDATE; IF NOT FOUND OR r.status='settled' THEN RAISE EXCEPTION 'unsettled refund request required'; END IF;
 UPDATE public.pos_split_refund_requests SET status='failed',failure_reason=left(COALESCE(p_reason,'refund failed'),500),updated_at=now() WHERE id=r.id;
 UPDATE public.pos_split_payment_legs SET status='refund_review',status_detail=left(COALESCE(p_reason,'refund failed'),500),updated_at=now() WHERE id=r.leg_id;
 UPDATE public.pos_split_payment_sessions SET status='refund_review',updated_at=now() WHERE id=r.session_id;
 RETURN private.pos_split_payment_payload(r.session_id);
END $$;

REVOKE ALL ON FUNCTION public.request_pos_split_cancellation(UUID,TEXT,TEXT),
 public.approve_pos_split_refund(UUID,TEXT,NUMERIC,NUMERIC,NUMERIC,INTEGER,TEXT),
 public.complete_pos_split_refund(UUID,TEXT,NUMERIC,NUMERIC,NUMERIC,TEXT),public.fail_pos_split_refund(UUID,TEXT) FROM PUBLIC,anon;

GRANT EXECUTE ON FUNCTION public.request_pos_split_cancellation(UUID,TEXT,TEXT) TO authenticated,service_role;

GRANT EXECUTE ON FUNCTION public.approve_pos_split_refund(UUID,TEXT,NUMERIC,NUMERIC,NUMERIC,INTEGER,TEXT),
 public.complete_pos_split_refund(UUID,TEXT,NUMERIC,NUMERIC,NUMERIC,TEXT),public.fail_pos_split_refund(UUID,TEXT) TO authenticated,service_role;

-- Split card refunds are tied to the original terminal transaction and finish only after terminal approval.
CREATE OR REPLACE FUNCTION public.begin_pos_card_terminal_split_refund(p_refund_id UUID,p_terminal_id UUID,p_request_id UUID)
RETURNS JSONB LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
DECLARE r public.pos_split_refund_requests%ROWTYPE; l public.pos_split_payment_legs%ROWTYPE; p public.pos_card_terminal_attempts%ROWTYPE;
 a public.pos_card_terminal_attempts%ROWTYPE; t public.pos_card_terminals%ROWTYPE; s public.pos_split_payment_sessions%ROWTYPE; v_ref TEXT; v_amount NUMERIC;
BEGIN
 IF NOT (public.is_pos_approver() OR public.has_staff_role(ARRAY['finance']::public.staff_role[])) THEN RAISE EXCEPTION 'manager or finance approval required'; END IF;
 SELECT * INTO a FROM public.pos_card_terminal_attempts WHERE request_id=p_request_id; IF FOUND THEN RETURN private.pos_card_terminal_attempt_payload(a.id); END IF;
 SELECT * INTO r FROM public.pos_split_refund_requests WHERE id=p_refund_id FOR UPDATE; IF NOT FOUND OR r.status<>'pending' THEN RAISE EXCEPTION 'approved pending split refund required'; END IF;
 SELECT * INTO l FROM public.pos_split_payment_legs WHERE id=r.leg_id FOR UPDATE; IF l.tender<>'card_terminal' OR l.card_terminal_attempt_id IS NULL THEN RAISE EXCEPTION 'card-terminal split leg required'; END IF;
 SELECT * INTO p FROM public.pos_card_terminal_attempts WHERE id=l.card_terminal_attempt_id; IF NOT FOUND OR p.terminal_transaction_id IS NULL THEN RAISE EXCEPTION 'original terminal transaction evidence required'; END IF;
 SELECT * INTO t FROM public.pos_card_terminals WHERE id=p_terminal_id; SELECT * INTO s FROM public.pos_split_payment_sessions WHERE id=r.session_id;
 IF t.id IS NULL OR NOT t.is_active THEN RAISE EXCEPTION 'active card terminal required'; END IF;
 v_amount:=round(r.gross_amount-r.approved_customer_fee,2); IF v_amount<=0 THEN RAISE EXCEPTION 'net customer refund must be > 0'; END IF;
 v_ref:='GTR-CTSR-'||replace(p_request_id::text,'-','');
 INSERT INTO public.pos_card_terminal_attempts(request_id,operation,terminal_id,parent_attempt_id,split_leg_id,split_refund_request_id,amount,currency,external_ref,created_by)
 VALUES(p_request_id,'refund',p_terminal_id,p.id,l.id,r.id,v_amount,s.currency,v_ref,auth.uid()) RETURNING * INTO a;
 RETURN private.pos_card_terminal_attempt_payload(a.id);
END $$;

CREATE OR REPLACE FUNCTION public.finalize_pos_card_terminal_split_refund(p_attempt_id UUID,p_notes TEXT DEFAULT NULL)
RETURNS JSONB LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
DECLARE a public.pos_card_terminal_attempts%ROWTYPE; r public.pos_split_refund_requests%ROWTYPE; v_payload JSONB;
BEGIN
 IF NOT (public.is_pos_approver() OR public.has_staff_role(ARRAY['finance']::public.staff_role[])) THEN RAISE EXCEPTION 'manager or finance approval required'; END IF;
 SELECT * INTO a FROM public.pos_card_terminal_attempts WHERE id=p_attempt_id FOR UPDATE;
 IF NOT FOUND OR a.operation<>'refund' OR a.split_refund_request_id IS NULL OR a.status<>'approved' THEN RAISE EXCEPTION 'approved split card-terminal refund attempt required'; END IF;
 SELECT * INTO r FROM public.pos_split_refund_requests WHERE id=a.split_refund_request_id;
 v_payload:=public.complete_pos_split_refund(r.id,a.terminal_transaction_id,r.estimated_provider_fee,r.estimated_transfer_fee,r.approved_customer_fee,p_notes);
 UPDATE public.pos_card_terminal_attempts SET status='settled',finalized_by=auth.uid(),finalized_at=now(),updated_at=now() WHERE id=a.id;
 RETURN v_payload;
END $$;

REVOKE ALL ON FUNCTION public.begin_pos_card_terminal_split_refund(UUID,UUID,UUID),public.finalize_pos_card_terminal_split_refund(UUID,TEXT) FROM PUBLIC,anon;

GRANT EXECUTE ON FUNCTION public.begin_pos_card_terminal_split_refund(UUID,UUID,UUID),public.finalize_pos_card_terminal_split_refund(UUID,TEXT) TO authenticated,service_role;

-- Include unresolved staged split cash in blind till expected-cash without double-counting allocated invoice payments.
CREATE OR REPLACE FUNCTION public.pos_till_expected_cash(p_session_id UUID)
RETURNS NUMERIC LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path='' AS $$
DECLARE s public.pos_till_sessions%ROWTYPE; v_cash NUMERIC:=0; v_split_cash NUMERIC:=0; v_direct NUMERIC:=0; v_in NUMERIC:=0; v_out NUMERIC:=0;
BEGIN
 PERFORM public._require_sales_staff(); SELECT * INTO s FROM public.pos_till_sessions WHERE id=p_session_id;
 IF NOT FOUND THEN RAISE EXCEPTION 'till session not found'; END IF;
 IF s.operator_user_id<>auth.uid() AND NOT public.is_pos_approver() AND NOT public.has_staff_role(ARRAY['finance']::public.staff_role[]) THEN RAISE EXCEPTION 'till session access denied'; END IF;
 SELECT COALESCE(SUM(a.amount),0) INTO v_cash FROM public.payment_allocations a JOIN public.payment_entries p ON p.id=a.payment_entry_id JOIN public.sales_invoices i ON i.id=a.sales_invoice_id
 WHERE i.till_session_id=p_session_id AND p.status='posted' AND p.tender='cash';
 SELECT COALESCE(SUM(l.requested_amount),0) INTO v_split_cash
 FROM public.pos_split_payment_legs l JOIN public.pos_split_payment_sessions ps ON ps.id=l.session_id JOIN public.commerce_orders o ON o.id=ps.commerce_order_id
 JOIN public.pos_carts c ON c.id=o.cart_id WHERE c.till_session_id=p_session_id AND l.tender='cash' AND l.status IN('captured','refund_review','refund_pending');
 SELECT COALESCE(SUM(i.total),0) INTO v_direct FROM public.sales_invoices i WHERE i.till_session_id=p_session_id AND i.status='posted' AND i.doc_type='invoice' AND i.customer_id IS NULL
  AND NOT EXISTS(SELECT 1 FROM public.payment_allocations a WHERE a.sales_invoice_id=i.id);
 SELECT COALESCE(SUM(amount),0) INTO v_in FROM public.pos_till_cash_movements WHERE session_id=p_session_id AND kind='cash_in';
 SELECT COALESCE(SUM(amount),0) INTO v_out FROM public.pos_till_cash_movements WHERE session_id=p_session_id AND kind IN('cash_out','petty_cash','bank_drop','cash_refund');
 RETURN round(s.opening_float+v_cash+v_split_cash+v_direct+v_in-v_out,2);
END $$;

REVOKE ALL ON FUNCTION public.pos_till_expected_cash(UUID) FROM PUBLIC,anon;

GRANT EXECUTE ON FUNCTION public.pos_till_expected_cash(UUID) TO authenticated,service_role;

-- Direct checkout cancellation must never bypass staged captured/pending money.
ALTER FUNCTION public.cancel_pos_commerce_checkout(UUID,TEXT) RENAME TO cancel_pos_commerce_checkout_legacy;

CREATE OR REPLACE FUNCTION public.cancel_pos_commerce_checkout(p_order_id UUID,p_reason TEXT)
RETURNS UUID LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
DECLARE s public.pos_split_payment_sessions%ROWTYPE;
BEGIN
 SELECT * INTO s FROM public.pos_split_payment_sessions WHERE commerce_order_id=p_order_id FOR UPDATE;
 IF FOUND AND EXISTS(SELECT 1 FROM public.pos_split_payment_legs WHERE session_id=s.id AND status IN('held','pending','unknown','captured','refund_review','refund_pending')) THEN
  RAISE EXCEPTION 'split payment has locked/in-flight money; use split cancellation/refund recovery';
 END IF;
 RETURN public.cancel_pos_commerce_checkout_legacy(p_order_id,p_reason);
END $$;

REVOKE ALL ON FUNCTION public.cancel_pos_commerce_checkout(UUID,TEXT) FROM PUBLIC,anon;

GRANT EXECUTE ON FUNCTION public.cancel_pos_commerce_checkout(UUID,TEXT) TO authenticated,service_role;
