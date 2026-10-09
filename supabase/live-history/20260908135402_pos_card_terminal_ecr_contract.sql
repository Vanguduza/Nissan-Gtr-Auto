-- Exported from the hosted project's supabase_migrations.schema_migrations (20260908135402 pos_card_terminal_ecr_contract).
-- Source of record for what production ran; see supabase/live-history/README.md.

-- Physical card-terminal / swipe-machine integration for reserve-first POS.
-- Stores terminal references only. PAN, PIN, CVV, track data and EMV payloads never enter GTR.

INSERT INTO public.chart_of_accounts(code,name,display_name,account_type)
VALUES ('1170','Card Terminal Clearing','Card terminal clearing','asset')
ON CONFLICT(code) DO UPDATE SET
  name=EXCLUDED.name, display_name=EXCLUDED.display_name, is_active=true;

INSERT INTO public.payment_tender_gl_accounts(tender,account_code)
VALUES ('card_terminal','1170')
ON CONFLICT(tender) DO UPDATE SET account_code=EXCLUDED.account_code,updated_at=now();

ALTER TABLE public.commerce_orders DROP CONSTRAINT IF EXISTS commerce_orders_settled_provider_check;
ALTER TABLE public.commerce_orders ADD CONSTRAINT commerce_orders_settled_provider_check
 CHECK (settled_provider IS NULL OR settled_provider IN
 ('contipay','paynow','ecocash','cash','bank','store_credit','manual_split','card_terminal'));

DO $$ BEGIN
 IF NOT EXISTS(SELECT 1 FROM pg_type WHERE typname='pos_card_terminal_operation') THEN
  CREATE TYPE public.pos_card_terminal_operation AS ENUM('purchase','refund','reversal');
 END IF;
 IF NOT EXISTS(SELECT 1 FROM pg_type WHERE typname='pos_card_terminal_attempt_status') THEN
  CREATE TYPE public.pos_card_terminal_attempt_status AS ENUM(
   'initiated','approved','declined','cancelled','unknown','failed','settled','reversed'
  );
 END IF;
END $$;

CREATE TABLE IF NOT EXISTS public.pos_card_terminals(
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
 code TEXT NOT NULL UNIQUE,
 label TEXT NOT NULL,
 acquirer_name TEXT,
 external_terminal_id TEXT,
 adapter_key TEXT NOT NULL DEFAULT 'android_intent_v1'
  CHECK(adapter_key IN('android_intent_v1')),
 adapter_config JSONB NOT NULL DEFAULT '{}'::jsonb,
 warehouse_id UUID REFERENCES public.warehouses(id),
 device_id TEXT,
 is_active BOOLEAN NOT NULL DEFAULT true,
 created_by UUID REFERENCES auth.users(id),
 updated_by UUID REFERENCES auth.users(id),
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS public.pos_card_terminal_attempts(
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
 request_id UUID NOT NULL UNIQUE,
 operation public.pos_card_terminal_operation NOT NULL,
 status public.pos_card_terminal_attempt_status NOT NULL DEFAULT 'initiated',
 terminal_id UUID NOT NULL REFERENCES public.pos_card_terminals(id),
 commerce_order_id UUID REFERENCES public.commerce_orders(id),
 source_invoice_id UUID REFERENCES public.sales_invoices(id),
 parent_attempt_id UUID REFERENCES public.pos_card_terminal_attempts(id),
 amount NUMERIC(18,2) NOT NULL CHECK(amount>0),
 currency public.currency_code NOT NULL,
 external_ref TEXT NOT NULL UNIQUE,
 terminal_transaction_id TEXT,
 rrn TEXT,
 authorization_code TEXT,
 card_last4 VARCHAR(4),
 card_scheme TEXT,
 response_code TEXT,
 response_message TEXT,
 payment_entry_id UUID REFERENCES public.payment_entries(id),
 invoice_id UUID REFERENCES public.sales_invoices(id),
 finance_refund_id UUID REFERENCES public.finance_refunds(id),
 finalization_error TEXT,
 created_by UUID NOT NULL REFERENCES auth.users(id),
 result_recorded_by UUID REFERENCES auth.users(id),
 finalized_by UUID REFERENCES auth.users(id),
 result_recorded_at TIMESTAMPTZ,
 approved_at TIMESTAMPTZ,
 finalized_at TIMESTAMPTZ,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 CHECK(card_last4 IS NULL OR card_last4 ~ '^[0-9]{4}$'),
 CHECK((operation='purchase' AND commerce_order_id IS NOT NULL AND source_invoice_id IS NULL)
    OR (operation='refund' AND source_invoice_id IS NOT NULL)
    OR (operation='reversal' AND parent_attempt_id IS NOT NULL))
);
CREATE UNIQUE INDEX IF NOT EXISTS pos_card_terminal_txn_uidx
 ON public.pos_card_terminal_attempts(terminal_id,operation,terminal_transaction_id)
 WHERE terminal_transaction_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS pos_card_terminal_attempt_order_idx
 ON public.pos_card_terminal_attempts(commerce_order_id,created_at DESC);
CREATE INDEX IF NOT EXISTS pos_card_terminal_attempt_invoice_idx
 ON public.pos_card_terminal_attempts(source_invoice_id,created_at DESC);
CREATE INDEX IF NOT EXISTS pos_card_terminal_attempt_recovery_idx
 ON public.pos_card_terminal_attempts(status,updated_at DESC);

ALTER TABLE public.pos_card_terminals ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.pos_card_terminal_attempts ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON public.pos_card_terminals,public.pos_card_terminal_attempts FROM PUBLIC,anon,authenticated;
GRANT ALL ON public.pos_card_terminals,public.pos_card_terminal_attempts TO service_role;

CREATE OR REPLACE FUNCTION public.upsert_pos_card_terminal(
 p_id UUID,p_code TEXT,p_label TEXT,p_acquirer_name TEXT,p_external_terminal_id TEXT,
 p_adapter_key TEXT,p_adapter_config JSONB,p_warehouse_id UUID,p_device_id TEXT,p_is_active BOOLEAN)
RETURNS UUID LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
DECLARE v_id UUID; v_bad TEXT; v_allowed TEXT[]:=ARRAY[
 'package_name','purchase_action','refund_action','status_action','reversal_action',
 'amount_minor_key','currency_key','reference_key','operation_key','original_transaction_id_key',
 'result_status_key','result_transaction_id_key','result_rrn_key','result_auth_code_key',
 'result_last4_key','result_scheme_key','result_response_code_key','result_response_message_key'
];
BEGIN
 IF NOT public.has_staff_role(ARRAY['admin']::public.staff_role[]) THEN RAISE EXCEPTION 'admin role required'; END IF;
 IF trim(COALESCE(p_code,''))='' OR trim(COALESCE(p_label,''))='' THEN RAISE EXCEPTION 'terminal code and label required'; END IF;
 IF COALESCE(p_adapter_key,'')<>'android_intent_v1' THEN RAISE EXCEPTION 'unsupported terminal adapter'; END IF;
 SELECT k INTO v_bad FROM jsonb_object_keys(COALESCE(p_adapter_config,'{}'::jsonb)) AS x(k)
 WHERE NOT(k=ANY(v_allowed)) LIMIT 1;
 IF v_bad IS NOT NULL THEN RAISE EXCEPTION 'unsupported or sensitive terminal config key: %',v_bad; END IF;
 IF trim(COALESCE(p_adapter_config->>'package_name',''))='' OR trim(COALESCE(p_adapter_config->>'purchase_action',''))='' THEN
  RAISE EXCEPTION 'android intent terminal requires package_name and purchase_action';
 END IF;
 IF p_warehouse_id IS NOT NULL AND NOT EXISTS(SELECT 1 FROM public.warehouses WHERE id=p_warehouse_id AND is_active) THEN RAISE EXCEPTION 'active warehouse required'; END IF;
 INSERT INTO public.pos_card_terminals(id,code,label,acquirer_name,external_terminal_id,adapter_key,adapter_config,warehouse_id,device_id,is_active,created_by,updated_by)
 VALUES(COALESCE(p_id,gen_random_uuid()),trim(p_code),trim(p_label),NULLIF(trim(COALESCE(p_acquirer_name,'')),''),NULLIF(trim(COALESCE(p_external_terminal_id,'')),''),p_adapter_key,COALESCE(p_adapter_config,'{}'::jsonb),p_warehouse_id,NULLIF(trim(COALESCE(p_device_id,'')),''),COALESCE(p_is_active,true),auth.uid(),auth.uid())
 ON CONFLICT(code) DO UPDATE SET label=EXCLUDED.label,acquirer_name=EXCLUDED.acquirer_name,external_terminal_id=EXCLUDED.external_terminal_id,
  adapter_key=EXCLUDED.adapter_key,adapter_config=EXCLUDED.adapter_config,warehouse_id=EXCLUDED.warehouse_id,device_id=EXCLUDED.device_id,
  is_active=EXCLUDED.is_active,updated_by=auth.uid(),updated_at=now()
 RETURNING id INTO v_id;
 RETURN v_id;
END $$;

CREATE OR REPLACE FUNCTION public.list_pos_card_terminals(p_warehouse_id UUID DEFAULT NULL,p_device_id TEXT DEFAULT NULL)
RETURNS TABLE(id UUID,code TEXT,label TEXT,acquirer_name TEXT,external_terminal_id TEXT,adapter_key TEXT,adapter_config JSONB,warehouse_id UUID,device_id TEXT,is_active BOOLEAN)
LANGUAGE sql STABLE SECURITY DEFINER SET search_path='' AS $$
 SELECT t.id,t.code,t.label,t.acquirer_name,t.external_terminal_id,t.adapter_key,t.adapter_config,t.warehouse_id,t.device_id,t.is_active
 FROM public.pos_card_terminals t
 WHERE public.is_staff() AND t.is_active
  AND (p_warehouse_id IS NULL OR t.warehouse_id IS NULL OR t.warehouse_id=p_warehouse_id)
  AND (p_device_id IS NULL OR t.device_id IS NULL OR t.device_id=p_device_id)
 ORDER BY t.label,t.code;
$$;

CREATE OR REPLACE FUNCTION private.pos_card_terminal_attempt_payload(p_attempt_id UUID)
RETURNS JSONB LANGUAGE sql STABLE SECURITY DEFINER SET search_path='' AS $$
 SELECT jsonb_build_object(
  'attempt_id',a.id,'request_id',a.request_id,'operation',a.operation,'status',a.status,
  'terminal_id',a.terminal_id,'amount',a.amount,'currency',a.currency,'external_ref',a.external_ref,
  'terminal_transaction_id',a.terminal_transaction_id,'rrn',a.rrn,'authorization_code',a.authorization_code,
  'card_last4',a.card_last4,'card_scheme',a.card_scheme,'response_code',a.response_code,'response_message',a.response_message,
  'commerce_order_id',a.commerce_order_id,'source_invoice_id',a.source_invoice_id,'parent_attempt_id',a.parent_attempt_id,
  'payment_entry_id',a.payment_entry_id,'invoice_id',a.invoice_id,'finance_refund_id',a.finance_refund_id,
  'finalization_error',a.finalization_error,
  'terminal',jsonb_build_object('id',t.id,'code',t.code,'label',t.label,'acquirer_name',t.acquirer_name,
   'external_terminal_id',t.external_terminal_id,'adapter_key',t.adapter_key,'adapter_config',t.adapter_config)
 )
 FROM public.pos_card_terminal_attempts a JOIN public.pos_card_terminals t ON t.id=a.terminal_id
 WHERE a.id=p_attempt_id;
$$;
REVOKE ALL ON FUNCTION private.pos_card_terminal_attempt_payload(UUID) FROM PUBLIC,anon,authenticated;

CREATE OR REPLACE FUNCTION public.begin_pos_card_terminal_purchase(p_order_id UUID,p_terminal_id UUID,p_request_id UUID)
RETURNS JSONB LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
DECLARE o public.commerce_orders%ROWTYPE; c public.pos_carts%ROWTYPE; s public.pos_till_sessions%ROWTYPE;
 t public.pos_card_terminals%ROWTYPE; a public.pos_card_terminal_attempts%ROWTYPE; v_ref TEXT;
BEGIN
 PERFORM public._require_payments_staff();
 IF p_order_id IS NULL OR p_terminal_id IS NULL OR p_request_id IS NULL THEN RAISE EXCEPTION 'order, terminal and request ids required'; END IF;
 SELECT * INTO a FROM public.pos_card_terminal_attempts WHERE request_id=p_request_id;
 IF FOUND THEN
  IF a.operation<>'purchase' OR a.commerce_order_id<>p_order_id OR a.terminal_id<>p_terminal_id THEN RAISE EXCEPTION 'terminal request id belongs to another operation'; END IF;
  RETURN private.pos_card_terminal_attempt_payload(a.id);
 END IF;
 SELECT * INTO o FROM public.commerce_orders WHERE id=p_order_id FOR UPDATE;
 IF NOT FOUND OR o.sales_invoice_id IS NOT NULL OR o.settled_payment_entry_id IS NOT NULL THEN RAISE EXCEPTION 'unsettled commerce order required'; END IF;
 IF o.reservation_expires_at IS NULL OR o.reservation_expires_at<=now() THEN RAISE EXCEPTION 'commerce reservation expired'; END IF;
 IF o.state NOT IN('awaiting_payment','payment_failed','payment_processing') THEN RAISE EXCEPTION 'card terminal cannot start in order state %',o.state; END IF;
 SELECT * INTO c FROM public.pos_carts WHERE id=o.cart_id;
 IF NOT FOUND OR c.created_by<>auth.uid() OR c.till_session_id IS NULL THEN RAISE EXCEPTION 'current operator cart and till required'; END IF;
 SELECT * INTO s FROM public.pos_till_sessions WHERE id=c.till_session_id;
 IF NOT FOUND OR s.status<>'open' OR s.operator_user_id<>auth.uid() OR s.warehouse_id<>o.warehouse_id THEN RAISE EXCEPTION 'active operator till required'; END IF;
 SELECT * INTO t FROM public.pos_card_terminals WHERE id=p_terminal_id;
 IF NOT FOUND OR NOT t.is_active THEN RAISE EXCEPTION 'active card terminal required'; END IF;
 IF t.warehouse_id IS NOT NULL AND t.warehouse_id<>o.warehouse_id THEN RAISE EXCEPTION 'card terminal is assigned to another warehouse'; END IF;
 IF t.device_id IS NOT NULL AND t.device_id<>s.device_id THEN RAISE EXCEPTION 'card terminal is assigned to another till device'; END IF;
 IF o.state='payment_processing' THEN
  IF o.active_payment_provider IS DISTINCT FROM 'card_terminal' THEN RAISE EXCEPTION 'another payment provider is already processing'; END IF;
  SELECT * INTO a FROM public.pos_card_terminal_attempts WHERE id=o.active_payment_intent_id;
  IF FOUND AND a.status IN('initiated','approved','unknown') THEN
   RAISE EXCEPTION 'existing card terminal attempt % is unresolved; reconcile it before charging again',a.id;
  END IF;
 END IF;
 v_ref:='GTR-CT-'||replace(p_request_id::text,'-','');
 INSERT INTO public.pos_card_terminal_attempts(request_id,operation,terminal_id,commerce_order_id,amount,currency,external_ref,created_by)
 VALUES(p_request_id,'purchase',p_terminal_id,p_order_id,o.total,o.currency,v_ref,auth.uid()) RETURNING * INTO a;
 UPDATE public.commerce_orders SET state='payment_processing',active_payment_provider='card_terminal',active_payment_intent_id=a.id,
  payment_exception=NULL,updated_at=now() WHERE id=o.id;
 PERFORM private.enqueue_commerce_event('commerce:'||o.id::text||':card-terminal:'||a.id::text,
  'commerce.card_terminal_started',o.id,jsonb_build_object('attempt_id',a.id,'terminal_id',p_terminal_id,'external_ref',v_ref));
 RETURN private.pos_card_terminal_attempt_payload(a.id);
END $$;

CREATE OR REPLACE FUNCTION public.get_pos_card_terminal_attempt(p_attempt_id UUID)
RETURNS JSONB LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path='' AS $$
DECLARE a public.pos_card_terminal_attempts%ROWTYPE;
BEGIN
 PERFORM public._require_payments_staff(); SELECT * INTO a FROM public.pos_card_terminal_attempts WHERE id=p_attempt_id;
 IF NOT FOUND THEN RAISE EXCEPTION 'card terminal attempt not found'; END IF;
 IF a.created_by<>auth.uid() AND NOT public.is_pos_approver() AND NOT public.has_staff_role(ARRAY['finance']::public.staff_role[]) THEN RAISE EXCEPTION 'card terminal attempt access denied'; END IF;
 RETURN private.pos_card_terminal_attempt_payload(a.id);
END $$;

CREATE OR REPLACE FUNCTION public.record_pos_card_terminal_result(
 p_actor_user_id UUID,p_attempt_id UUID,p_outcome TEXT,p_terminal_transaction_id TEXT DEFAULT NULL,p_rrn TEXT DEFAULT NULL,
 p_authorization_code TEXT DEFAULT NULL,p_card_last4 TEXT DEFAULT NULL,p_card_scheme TEXT DEFAULT NULL,
 p_response_code TEXT DEFAULT NULL,p_response_message TEXT DEFAULT NULL)
RETURNS JSONB LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
DECLARE a public.pos_card_terminal_attempts%ROWTYPE; v_status public.pos_card_terminal_attempt_status; parent public.pos_card_terminal_attempts%ROWTYPE;
BEGIN
 PERFORM public._require_payments_staff();
 IF p_actor_user_id IS NULL OR NOT EXISTS(SELECT 1 FROM public.staff_roles sr WHERE sr.user_id=p_actor_user_id AND sr.role IN('admin','finance','sales')) THEN RAISE EXCEPTION 'active payments staff actor required'; END IF;
 SELECT * INTO a FROM public.pos_card_terminal_attempts WHERE id=p_attempt_id FOR UPDATE;
 IF NOT FOUND THEN RAISE EXCEPTION 'card terminal attempt not found'; END IF;
 IF a.created_by<>p_actor_user_id AND NOT EXISTS(SELECT 1 FROM public.staff_roles sr WHERE sr.user_id=p_actor_user_id AND sr.role IN('admin','finance')) THEN RAISE EXCEPTION 'card terminal attempt access denied'; END IF;
 IF p_outcome NOT IN('approved','declined','cancelled','unknown','failed') THEN RAISE EXCEPTION 'unsupported terminal outcome'; END IF;
 v_status:=p_outcome::public.pos_card_terminal_attempt_status;
 IF a.status IN('settled','reversed') THEN RETURN private.pos_card_terminal_attempt_payload(a.id); END IF;
 IF a.status='approved' AND v_status<>'approved' THEN RAISE EXCEPTION 'approved terminal result cannot be downgraded; reverse or finalize it'; END IF;
 IF a.status NOT IN('initiated','unknown','approved') THEN
  IF a.status=v_status THEN RETURN private.pos_card_terminal_attempt_payload(a.id); END IF;
  RAISE EXCEPTION 'terminal result cannot transition from % to %',a.status,v_status;
 END IF;
 IF p_card_last4 IS NOT NULL AND p_card_last4!~'^[0-9]{4}$' THEN RAISE EXCEPTION 'card_last4 must contain exactly four digits'; END IF;
 IF length(COALESCE(p_terminal_transaction_id,''))>96 OR length(COALESCE(p_rrn,''))>64 OR length(COALESCE(p_authorization_code,''))>32 OR
    length(COALESCE(p_card_scheme,''))>32 OR length(COALESCE(p_response_code,''))>32 OR length(COALESCE(p_response_message,''))>240 THEN RAISE EXCEPTION 'terminal result field exceeds safe length'; END IF;
 IF v_status='approved' AND (trim(COALESCE(p_terminal_transaction_id,''))='' OR (trim(COALESCE(p_rrn,''))='' AND trim(COALESCE(p_authorization_code,''))='')) THEN
  RAISE EXCEPTION 'approved result requires terminal transaction id plus RRN or authorization code';
 END IF;
 UPDATE public.pos_card_terminal_attempts SET status=v_status,
  terminal_transaction_id=COALESCE(NULLIF(trim(COALESCE(p_terminal_transaction_id,'')),''),terminal_transaction_id),
  rrn=COALESCE(NULLIF(trim(COALESCE(p_rrn,'')),''),rrn),authorization_code=COALESCE(NULLIF(trim(COALESCE(p_authorization_code,'')),''),authorization_code),
  card_last4=COALESCE(NULLIF(trim(COALESCE(p_card_last4,'')),''),card_last4),card_scheme=COALESCE(NULLIF(trim(COALESCE(p_card_scheme,'')),''),card_scheme),
  response_code=COALESCE(NULLIF(trim(COALESCE(p_response_code,'')),''),response_code),response_message=COALESCE(NULLIF(trim(COALESCE(p_response_message,'')),''),response_message),
  result_recorded_by=p_actor_user_id,result_recorded_at=now(),approved_at=CASE WHEN v_status='approved' THEN COALESCE(approved_at,now()) ELSE approved_at END,
  updated_at=now() WHERE id=a.id RETURNING * INTO a;
 IF a.operation='purchase' THEN
  IF v_status='unknown' THEN
   UPDATE public.commerce_orders SET state='payment_processing',active_payment_provider='card_terminal',active_payment_intent_id=a.id,
    payment_exception='CARD_TERMINAL_OUTCOME_UNKNOWN',updated_at=now() WHERE id=a.commerce_order_id;
  ELSIF v_status='approved' THEN
   UPDATE public.commerce_orders SET state='payment_processing',active_payment_provider='card_terminal',active_payment_intent_id=a.id,
    payment_exception=NULL,updated_at=now() WHERE id=a.commerce_order_id;
  ELSE
   UPDATE public.commerce_orders SET state='payment_failed',active_payment_provider=NULL,active_payment_intent_id=NULL,
    payment_exception=NULL,updated_at=now() WHERE id=a.commerce_order_id AND settled_payment_entry_id IS NULL;
  END IF;
 ELSIF a.operation='reversal' THEN
  SELECT * INTO parent FROM public.pos_card_terminal_attempts WHERE id=a.parent_attempt_id FOR UPDATE;
  IF v_status='approved' THEN
   UPDATE public.pos_card_terminal_attempts SET status='reversed',finalized_by=p_actor_user_id,finalized_at=now(),updated_at=now() WHERE id=parent.id;
   UPDATE public.pos_card_terminal_attempts SET status='settled',finalized_by=p_actor_user_id,finalized_at=now(),updated_at=now() WHERE id=a.id;
   UPDATE public.commerce_orders SET state='payment_failed',active_payment_provider=NULL,active_payment_intent_id=NULL,payment_exception=NULL,updated_at=now()
    WHERE id=parent.commerce_order_id AND settled_payment_entry_id IS NULL;
  ELSIF v_status='unknown' THEN
   UPDATE public.commerce_orders SET state='payment_processing',active_payment_provider='card_terminal',active_payment_intent_id=a.id,
    payment_exception='CARD_TERMINAL_REVERSAL_UNKNOWN',updated_at=now() WHERE id=parent.commerce_order_id;
  ELSE
   UPDATE public.commerce_orders SET state='payment_processing',active_payment_provider='card_terminal',active_payment_intent_id=parent.id,
    payment_exception='CARD_TERMINAL_APPROVED_NOT_FINALIZED',updated_at=now() WHERE id=parent.commerce_order_id;
  END IF;
 END IF;
 RETURN private.pos_card_terminal_attempt_payload(a.id);
EXCEPTION WHEN unique_violation THEN
 RAISE EXCEPTION 'terminal transaction id was already recorded; reconcile the existing attempt instead of charging again';
END $$;

CREATE OR REPLACE FUNCTION public.finalize_pos_card_terminal_purchase(p_attempt_id UUID)
RETURNS JSONB LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
DECLARE a public.pos_card_terminal_attempts%ROWTYPE; o public.commerce_orders%ROWTYPE; v_inv UUID; v_pe UUID; v_error TEXT;
BEGIN
 PERFORM public._require_payments_staff(); SELECT * INTO a FROM public.pos_card_terminal_attempts WHERE id=p_attempt_id FOR UPDATE;
 IF NOT FOUND OR a.operation<>'purchase' THEN RAISE EXCEPTION 'purchase card terminal attempt required'; END IF;
 IF a.created_by<>auth.uid() AND NOT public.is_pos_approver() AND NOT public.has_staff_role(ARRAY['finance']::public.staff_role[]) THEN RAISE EXCEPTION 'card terminal attempt access denied'; END IF;
 IF a.status='settled' THEN RETURN private.pos_card_terminal_attempt_payload(a.id); END IF;
 IF a.status<>'approved' THEN RAISE EXCEPTION 'terminal approval required before finalization'; END IF;
 SELECT * INTO o FROM public.commerce_orders WHERE id=a.commerce_order_id FOR UPDATE;
 IF NOT FOUND OR o.settled_payment_entry_id IS NOT NULL OR o.sales_invoice_id IS NOT NULL THEN RAISE EXCEPTION 'order is already finalized by another settlement'; END IF;
 IF abs(o.total-a.amount)>0.01 OR o.currency<>a.currency THEN RAISE EXCEPTION 'terminal approval does not match reserved order amount/currency'; END IF;
 BEGIN
  v_inv:=private.finalize_commerce_order(o.id);
  v_pe:=public.create_payment_entry(o.customer_id,'card_terminal',a.amount,a.currency,o.exchange_rate_applied,
   format('Card terminal %s · txn %s',a.external_ref,a.terminal_transaction_id));
  PERFORM public.allocate_payment(v_pe,jsonb_build_array(jsonb_build_object('sales_invoice_id',v_inv,'amount',a.amount)));
  PERFORM public.post_payment_entry(v_pe);
  UPDATE public.pos_card_terminal_attempts SET status='settled',payment_entry_id=v_pe,invoice_id=v_inv,finalization_error=NULL,
   finalized_by=auth.uid(),finalized_at=now(),updated_at=now() WHERE id=a.id;
  UPDATE public.commerce_orders SET settled_payment_entry_id=v_pe,settled_provider='card_terminal',settled_provider_ref=a.terminal_transaction_id,
   active_payment_provider=NULL,active_payment_intent_id=NULL,payment_exception=NULL,
   state=CASE WHEN fulfillment_mode='dispatch' THEN 'allocation_pending'::public.commerce_order_state ELSE 'paid'::public.commerce_order_state END,
   updated_at=now() WHERE id=o.id;
  PERFORM private.enqueue_commerce_event('commerce:'||o.id::text||':card-terminal-settled:'||a.id::text,
   'commerce.payment_settled',o.id,jsonb_build_object('provider','card_terminal','attempt_id',a.id,'payment_entry_id',v_pe,'sales_invoice_id',v_inv,'terminal_transaction_id',a.terminal_transaction_id));
 EXCEPTION WHEN OTHERS THEN
  v_error:=SQLERRM;
  UPDATE public.pos_card_terminal_attempts SET finalization_error=left(v_error,500),updated_at=now() WHERE id=a.id;
  UPDATE public.commerce_orders SET state='payment_processing',active_payment_provider='card_terminal',active_payment_intent_id=a.id,
   payment_exception='CARD_TERMINAL_APPROVED_NOT_FINALIZED',updated_at=now() WHERE id=o.id;
  RETURN jsonb_build_object('attempt_id',a.id,'order_id',o.id,'status','recovery_required','error',left(v_error,500));
 END;
 RETURN private.pos_card_terminal_attempt_payload(a.id);
END $$;

CREATE OR REPLACE FUNCTION public.begin_pos_card_terminal_reversal(p_purchase_attempt_id UUID,p_request_id UUID)
RETURNS JSONB LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
DECLARE p public.pos_card_terminal_attempts%ROWTYPE; a public.pos_card_terminal_attempts%ROWTYPE; o public.commerce_orders%ROWTYPE; v_ref TEXT;
BEGIN
 PERFORM public._require_payments_staff();
 SELECT * INTO a FROM public.pos_card_terminal_attempts WHERE request_id=p_request_id;
 IF FOUND THEN
  IF a.operation<>'reversal' OR a.parent_attempt_id<>p_purchase_attempt_id THEN RAISE EXCEPTION 'terminal request id belongs to another operation'; END IF;
  RETURN private.pos_card_terminal_attempt_payload(a.id);
 END IF;
 SELECT * INTO p FROM public.pos_card_terminal_attempts WHERE id=p_purchase_attempt_id FOR UPDATE;
 IF NOT FOUND OR p.operation<>'purchase' OR p.status<>'approved' OR p.payment_entry_id IS NOT NULL OR p.invoice_id IS NOT NULL THEN
  RAISE EXCEPTION 'approved, unfinalized purchase attempt required for reversal';
 END IF;
 IF p.created_by<>auth.uid() AND NOT public.is_pos_approver() THEN RAISE EXCEPTION 'purchase operator or POS manager required'; END IF;
 SELECT * INTO o FROM public.commerce_orders WHERE id=p.commerce_order_id FOR UPDATE;
 IF NOT FOUND OR o.settled_payment_entry_id IS NOT NULL THEN RAISE EXCEPTION 'settled order cannot use terminal reversal; use refund flow'; END IF;
 IF EXISTS(SELECT 1 FROM public.pos_card_terminal_attempts x WHERE x.parent_attempt_id=p.id AND x.operation='reversal' AND x.status IN('initiated','approved','unknown')) THEN
  RAISE EXCEPTION 'an unresolved reversal already exists for this purchase';
 END IF;
 v_ref:='GTR-CTRV-'||replace(p_request_id::text,'-','');
 INSERT INTO public.pos_card_terminal_attempts(request_id,operation,terminal_id,commerce_order_id,parent_attempt_id,amount,currency,external_ref,created_by)
 VALUES(p_request_id,'reversal',p.terminal_id,p.commerce_order_id,p.id,p.amount,p.currency,v_ref,auth.uid()) RETURNING * INTO a;
 UPDATE public.commerce_orders SET state='payment_processing',active_payment_provider='card_terminal',active_payment_intent_id=a.id,
  payment_exception='CARD_TERMINAL_REVERSAL_PENDING',updated_at=now() WHERE id=o.id;
 RETURN private.pos_card_terminal_attempt_payload(a.id);
END $$;

CREATE OR REPLACE FUNCTION public.begin_pos_card_terminal_refund(p_invoice_id UUID,p_terminal_id UUID,p_request_id UUID)
RETURNS JSONB LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
DECLARE i public.sales_invoices%ROWTYPE; t public.pos_card_terminals%ROWTYPE; p public.pos_card_terminal_attempts%ROWTYPE;
 a public.pos_card_terminal_attempts%ROWTYPE; v_pe UUID; v_ref TEXT;
BEGIN
 IF NOT (public.is_pos_approver() OR public.has_staff_role(ARRAY['finance']::public.staff_role[])) THEN RAISE EXCEPTION 'POS manager or finance approval required'; END IF;
 SELECT * INTO a FROM public.pos_card_terminal_attempts WHERE request_id=p_request_id;
 IF FOUND THEN
  IF a.operation<>'refund' OR a.source_invoice_id<>p_invoice_id OR a.terminal_id<>p_terminal_id THEN RAISE EXCEPTION 'terminal request id belongs to another operation'; END IF;
  RETURN private.pos_card_terminal_attempt_payload(a.id);
 END IF;
 SELECT * INTO i FROM public.sales_invoices WHERE id=p_invoice_id FOR UPDATE;
 IF NOT FOUND OR i.status<>'posted' OR i.doc_type<>'invoice' THEN RAISE EXCEPTION 'posted sales invoice required'; END IF;
 IF EXISTS(SELECT 1 FROM public.finance_refunds f WHERE f.original_invoice_id=i.id) THEN RAISE EXCEPTION 'finance refund already posted for invoice'; END IF;
 SELECT pe.id INTO v_pe FROM public.payment_entries pe JOIN public.payment_allocations pa ON pa.payment_entry_id=pe.id
 WHERE pa.sales_invoice_id=i.id AND pe.status='posted' AND pe.tender='card_terminal' AND pa.amount+0.01>=i.total ORDER BY pe.posted_at DESC LIMIT 1;
 IF v_pe IS NULL THEN RAISE EXCEPTION 'invoice was not fully settled through a card terminal'; END IF;
 SELECT * INTO p FROM public.pos_card_terminal_attempts WHERE payment_entry_id=v_pe AND invoice_id=i.id AND operation='purchase' AND status='settled' ORDER BY finalized_at DESC LIMIT 1;
 IF NOT FOUND THEN RAISE EXCEPTION 'source card terminal purchase evidence not found'; END IF;
 IF EXISTS(SELECT 1 FROM public.pos_card_terminal_attempts x WHERE x.source_invoice_id=i.id AND x.operation='refund' AND x.status IN('initiated','approved','unknown','settled')) THEN
  RAISE EXCEPTION 'an existing card terminal refund must be reconciled before another refund';
 END IF;
 SELECT * INTO t FROM public.pos_card_terminals WHERE id=p_terminal_id;
 IF NOT FOUND OR NOT t.is_active THEN RAISE EXCEPTION 'active card terminal required'; END IF;
 IF t.warehouse_id IS NOT NULL AND t.warehouse_id<>i.warehouse_id THEN RAISE EXCEPTION 'refund terminal is assigned to another warehouse'; END IF;
 v_ref:='GTR-CTRF-'||replace(p_request_id::text,'-','');
 INSERT INTO public.pos_card_terminal_attempts(request_id,operation,terminal_id,source_invoice_id,parent_attempt_id,amount,currency,external_ref,created_by)
 VALUES(p_request_id,'refund',p_terminal_id,i.id,p.id,i.total,i.currency,v_ref,auth.uid()) RETURNING * INTO a;
 RETURN private.pos_card_terminal_attempt_payload(a.id);
END $$;

CREATE OR REPLACE FUNCTION public.finalize_pos_card_terminal_refund(p_attempt_id UUID,p_notes TEXT DEFAULT NULL)
RETURNS JSONB LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
DECLARE a public.pos_card_terminal_attempts%ROWTYPE; v_refund UUID; v_error TEXT;
BEGIN
 IF NOT (public.is_pos_approver() OR public.has_staff_role(ARRAY['finance']::public.staff_role[])) THEN RAISE EXCEPTION 'POS manager or finance approval required'; END IF;
 SELECT * INTO a FROM public.pos_card_terminal_attempts WHERE id=p_attempt_id FOR UPDATE;
 IF NOT FOUND OR a.operation<>'refund' THEN RAISE EXCEPTION 'card terminal refund attempt required'; END IF;
 IF a.status='settled' THEN RETURN private.pos_card_terminal_attempt_payload(a.id); END IF;
 IF a.status<>'approved' THEN RAISE EXCEPTION 'terminal refund approval required before finance finalization'; END IF;
 BEGIN
  v_refund:=public.post_finance_refund(a.source_invoice_id,concat_ws(' · ','Card terminal refund '||COALESCE(a.terminal_transaction_id,a.external_ref),p_notes));
  UPDATE public.pos_card_terminal_attempts SET status='settled',finance_refund_id=v_refund,finalization_error=NULL,
   finalized_by=auth.uid(),finalized_at=now(),updated_at=now() WHERE id=a.id;
 EXCEPTION WHEN OTHERS THEN
  v_error:=SQLERRM;
  UPDATE public.pos_card_terminal_attempts SET finalization_error=left(v_error,500),updated_at=now() WHERE id=a.id;
  RETURN jsonb_build_object('attempt_id',a.id,'invoice_id',a.source_invoice_id,'status','recovery_required','error',left(v_error,500));
 END;
 RETURN private.pos_card_terminal_attempt_payload(a.id);
END $$;

CREATE OR REPLACE FUNCTION public.list_pos_card_terminal_recovery(p_limit INTEGER DEFAULT 100)
RETURNS TABLE(id UUID,operation TEXT,status TEXT,terminal_id UUID,terminal_label TEXT,commerce_order_id UUID,source_invoice_id UUID,
 amount NUMERIC,currency TEXT,external_ref TEXT,terminal_transaction_id TEXT,rrn TEXT,authorization_code TEXT,card_last4 TEXT,card_scheme TEXT,
 response_code TEXT,response_message TEXT,finalization_error TEXT,created_at TIMESTAMPTZ,updated_at TIMESTAMPTZ)
LANGUAGE sql STABLE SECURITY DEFINER SET search_path='' AS $$
 SELECT a.id,a.operation::text,a.status::text,a.terminal_id,t.label,a.commerce_order_id,a.source_invoice_id,a.amount,a.currency::text,a.external_ref,
  a.terminal_transaction_id,a.rrn,a.authorization_code,a.card_last4,a.card_scheme,a.response_code,a.response_message,a.finalization_error,a.created_at,a.updated_at
 FROM public.pos_card_terminal_attempts a JOIN public.pos_card_terminals t ON t.id=a.terminal_id
 WHERE public.has_staff_role(ARRAY['admin','finance','sales']::public.staff_role[])
  AND (a.status IN('initiated','approved','unknown') OR a.finalization_error IS NOT NULL)
 ORDER BY a.updated_at DESC LIMIT LEAST(GREATEST(COALESCE(p_limit,100),1),250);
$$;

CREATE TABLE IF NOT EXISTS public.pos_card_terminal_device_keys(
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
 terminal_id UUID NOT NULL REFERENCES public.pos_card_terminals(id) ON DELETE CASCADE,
 device_id TEXT NOT NULL,
 public_key_spki_base64 TEXT NOT NULL,
 key_sha256 TEXT NOT NULL CHECK(key_sha256 ~ '^[0-9a-f]{64}$'),
 is_active BOOLEAN NOT NULL DEFAULT true,
 created_by UUID REFERENCES auth.users(id),
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 revoked_at TIMESTAMPTZ,
 UNIQUE(terminal_id,device_id,key_sha256)
);
CREATE UNIQUE INDEX IF NOT EXISTS pos_card_terminal_one_active_device_key_uidx
 ON public.pos_card_terminal_device_keys(terminal_id,device_id) WHERE is_active AND revoked_at IS NULL;
ALTER TABLE public.pos_card_terminal_device_keys ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON public.pos_card_terminal_device_keys FROM PUBLIC,anon,authenticated;
GRANT ALL ON public.pos_card_terminal_device_keys TO service_role;

CREATE OR REPLACE FUNCTION public.register_pos_card_terminal_device_key(
 p_terminal_id UUID,p_device_id TEXT,p_public_key_spki_base64 TEXT,p_key_sha256 TEXT)
RETURNS UUID LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
DECLARE v_id UUID; v_bytes BYTEA;
BEGIN
 IF NOT public.has_staff_role(ARRAY['admin']::public.staff_role[]) THEN RAISE EXCEPTION 'admin role required'; END IF;
 IF trim(COALESCE(p_device_id,''))='' OR trim(COALESCE(p_public_key_spki_base64,''))='' OR COALESCE(p_key_sha256,'')!~'^[0-9a-f]{64}$' THEN
  RAISE EXCEPTION 'device id, public key and sha256 required';
 END IF;
 IF NOT EXISTS(SELECT 1 FROM public.pos_card_terminals WHERE id=p_terminal_id AND is_active) THEN RAISE EXCEPTION 'active terminal required'; END IF;
 BEGIN v_bytes:=decode(p_public_key_spki_base64,'base64'); EXCEPTION WHEN OTHERS THEN RAISE EXCEPTION 'public key must be base64 SPKI'; END;
 IF octet_length(v_bytes)<200 OR octet_length(v_bytes)>1024 THEN RAISE EXCEPTION 'unexpected public key size'; END IF;
 IF encode(digest(v_bytes,'sha256'),'hex')<>lower(p_key_sha256) THEN RAISE EXCEPTION 'public key sha256 mismatch'; END IF;
 UPDATE public.pos_card_terminal_device_keys SET is_active=false,revoked_at=now()
 WHERE terminal_id=p_terminal_id AND device_id=trim(p_device_id) AND is_active AND revoked_at IS NULL;
 INSERT INTO public.pos_card_terminal_device_keys(terminal_id,device_id,public_key_spki_base64,key_sha256,is_active,created_by,revoked_at)
 VALUES(p_terminal_id,trim(p_device_id),trim(p_public_key_spki_base64),lower(p_key_sha256),true,auth.uid(),NULL)
 ON CONFLICT(terminal_id,device_id,key_sha256) DO UPDATE SET
  public_key_spki_base64=EXCLUDED.public_key_spki_base64,is_active=true,revoked_at=NULL,created_by=auth.uid()
 RETURNING id INTO v_id;
 RETURN v_id;
END $$;

CREATE OR REPLACE FUNCTION public.get_pos_payment_status(p_order_id UUID)
RETURNS JSONB LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path='' AS $$
DECLARE o public.commerce_orders%ROWTYPE; v_status TEXT; v_failure TEXT; v_ex JSONB;
BEGIN
 PERFORM public._require_sales_staff(); SELECT * INTO o FROM public.commerce_orders WHERE id=p_order_id;
 IF NOT FOUND THEN RAISE EXCEPTION 'commerce order not found'; END IF;
 IF o.active_payment_provider='ecocash' THEN SELECT status::text,failure_reason INTO v_status,v_failure FROM public.ecocash_payment_intents WHERE id=o.active_payment_intent_id;
 ELSIF o.active_payment_provider='paynow' THEN SELECT status::text,failure_reason INTO v_status,v_failure FROM public.paynow_payment_intents WHERE id=o.active_payment_intent_id;
 ELSIF o.active_payment_provider='contipay' THEN SELECT status::text,failure_reason INTO v_status,v_failure FROM public.contipay_payment_intents WHERE id=o.active_payment_intent_id;
 ELSIF o.active_payment_provider='card_terminal' THEN SELECT status::text,COALESCE(finalization_error,response_message) INTO v_status,v_failure FROM public.pos_card_terminal_attempts WHERE id=o.active_payment_intent_id;
 END IF;
 SELECT COALESCE(jsonb_agg(jsonb_build_object('id',e.id,'provider',e.provider,'code',e.exception_code,'detail',e.detail,'resolved_at',e.resolved_at,'resolution',e.resolution,'created_at',e.created_at) ORDER BY e.created_at DESC),'[]'::jsonb)
 INTO v_ex FROM public.commerce_payment_exceptions e WHERE e.commerce_order_id=p_order_id;
 RETURN jsonb_build_object('order_id',o.id,'cart_id',o.cart_id,'state',o.state,'total',o.total,'currency',o.currency,'reservation_expires_at',o.reservation_expires_at,
  'active_provider',o.active_payment_provider,'active_intent_id',o.active_payment_intent_id,'provider_status',v_status,'provider_failure',v_failure,
  'settled_payment_entry_id',o.settled_payment_entry_id,'settled_provider',o.settled_provider,'settled_provider_ref',o.settled_provider_ref,
  'sales_invoice_id',o.sales_invoice_id,'payment_exception',o.payment_exception,'exceptions',v_ex,'finalized_at',o.finalized_at);
END $$;

CREATE OR REPLACE FUNCTION public.cancel_pos_commerce_checkout(p_order_id UUID,p_reason TEXT)
RETURNS UUID LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
DECLARE o public.commerce_orders%ROWTYPE; v_status TEXT;
BEGIN
 PERFORM public._require_sales_staff(); SELECT * INTO o FROM public.commerce_orders WHERE id=p_order_id FOR UPDATE;
 IF NOT FOUND THEN RAISE EXCEPTION 'commerce order not found'; END IF;
 IF o.settled_payment_entry_id IS NOT NULL THEN RAISE EXCEPTION 'settled order cannot be cancelled'; END IF;
 IF o.active_payment_provider='ecocash' THEN SELECT status::text INTO v_status FROM public.ecocash_payment_intents WHERE id=o.active_payment_intent_id;
 ELSIF o.active_payment_provider='paynow' THEN SELECT status::text INTO v_status FROM public.paynow_payment_intents WHERE id=o.active_payment_intent_id;
 ELSIF o.active_payment_provider='contipay' THEN SELECT status::text INTO v_status FROM public.contipay_payment_intents WHERE id=o.active_payment_intent_id;
 ELSIF o.active_payment_provider='card_terminal' THEN SELECT status::text INTO v_status FROM public.pos_card_terminal_attempts WHERE id=o.active_payment_intent_id;
 END IF;
 IF o.active_payment_provider='card_terminal' AND v_status IN('initiated','approved','unknown') THEN
  RAISE EXCEPTION 'card terminal outcome is unresolved; reconcile or reverse it before releasing stock';
 END IF;
 IF o.active_payment_provider<>'card_terminal' AND v_status IN('pending','authorized') THEN RAISE EXCEPTION 'provider payment still pending; confirm failure/cancellation before releasing stock'; END IF;
 PERFORM private.release_commerce_order(o.id,'cancelled',COALESCE(NULLIF(trim(p_reason),''),'POS checkout cancelled'));
 RETURN o.id;
END $$;

REVOKE ALL ON FUNCTION public.upsert_pos_card_terminal(UUID,TEXT,TEXT,TEXT,TEXT,TEXT,JSONB,UUID,TEXT,BOOLEAN) FROM PUBLIC,anon;
REVOKE ALL ON FUNCTION public.list_pos_card_terminals(UUID,TEXT) FROM PUBLIC,anon;
REVOKE ALL ON FUNCTION public.begin_pos_card_terminal_purchase(UUID,UUID,UUID) FROM PUBLIC,anon;
REVOKE ALL ON FUNCTION public.get_pos_card_terminal_attempt(UUID) FROM PUBLIC,anon;
REVOKE ALL ON FUNCTION public.record_pos_card_terminal_result(UUID,UUID,TEXT,TEXT,TEXT,TEXT,TEXT,TEXT,TEXT,TEXT) FROM PUBLIC,anon,authenticated;
REVOKE ALL ON FUNCTION public.finalize_pos_card_terminal_purchase(UUID) FROM PUBLIC,anon;
REVOKE ALL ON FUNCTION public.begin_pos_card_terminal_reversal(UUID,UUID) FROM PUBLIC,anon;
REVOKE ALL ON FUNCTION public.begin_pos_card_terminal_refund(UUID,UUID,UUID) FROM PUBLIC,anon;
REVOKE ALL ON FUNCTION public.finalize_pos_card_terminal_refund(UUID,TEXT) FROM PUBLIC,anon;
REVOKE ALL ON FUNCTION public.list_pos_card_terminal_recovery(INTEGER) FROM PUBLIC,anon;
REVOKE ALL ON FUNCTION public.register_pos_card_terminal_device_key(UUID,TEXT,TEXT,TEXT) FROM PUBLIC,anon;
REVOKE ALL ON FUNCTION public.get_pos_payment_status(UUID),public.cancel_pos_commerce_checkout(UUID,TEXT) FROM PUBLIC,anon;

GRANT EXECUTE ON FUNCTION public.upsert_pos_card_terminal(UUID,TEXT,TEXT,TEXT,TEXT,TEXT,JSONB,UUID,TEXT,BOOLEAN),
 public.list_pos_card_terminals(UUID,TEXT),public.begin_pos_card_terminal_purchase(UUID,UUID,UUID),public.get_pos_card_terminal_attempt(UUID),
 public.finalize_pos_card_terminal_purchase(UUID),public.begin_pos_card_terminal_reversal(UUID,UUID),public.begin_pos_card_terminal_refund(UUID,UUID,UUID),
 public.finalize_pos_card_terminal_refund(UUID,TEXT),public.list_pos_card_terminal_recovery(INTEGER),public.register_pos_card_terminal_device_key(UUID,TEXT,TEXT,TEXT),
 public.get_pos_payment_status(UUID),public.cancel_pos_commerce_checkout(UUID,TEXT) TO authenticated,service_role;
GRANT EXECUTE ON FUNCTION public.record_pos_card_terminal_result(UUID,UUID,TEXT,TEXT,TEXT,TEXT,TEXT,TEXT,TEXT,TEXT) TO service_role;
