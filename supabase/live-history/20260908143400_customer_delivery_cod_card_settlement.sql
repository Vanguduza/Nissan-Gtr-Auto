-- Exported from the hosted project's supabase_migrations.schema_migrations (20260908143400 customer_delivery_cod_card_settlement).
-- Source of record for what production ran; see supabase/live-history/README.md.

-- Explicit storefront/delivery settlement: prepay, COD, card-on-delivery, or cash/card flexible remainder.
-- Drivers may settle only their assigned dispatched job under a private payment scope.

DO $$ BEGIN
 IF NOT EXISTS(SELECT 1 FROM pg_type WHERE typname='delivery_payment_method') THEN
  CREATE TYPE public.delivery_payment_method AS ENUM('prepay','cash_on_delivery','card_on_delivery','cash_or_card_on_delivery');
 END IF;
END $$;

ALTER TABLE public.pos_carts ADD COLUMN IF NOT EXISTS delivery_payment_method public.delivery_payment_method;

ALTER TABLE public.sales_invoices ADD COLUMN IF NOT EXISTS delivery_payment_method public.delivery_payment_method;

CREATE TABLE IF NOT EXISTS public.delivery_cash_collections(
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(),request_id UUID NOT NULL UNIQUE,delivery_job_id UUID NOT NULL REFERENCES public.delivery_jobs(id) ON DELETE RESTRICT,
 sales_invoice_id UUID NOT NULL REFERENCES public.sales_invoices(id) ON DELETE RESTRICT,payment_entry_id UUID NOT NULL UNIQUE REFERENCES public.payment_entries(id) ON DELETE RESTRICT,
 amount NUMERIC(18,2) NOT NULL CHECK(amount>0),currency public.currency_code NOT NULL,collected_by UUID NOT NULL REFERENCES auth.users(id),
 collected_at TIMESTAMPTZ NOT NULL DEFAULT now(),notes TEXT
);

CREATE INDEX IF NOT EXISTS delivery_cash_collections_job_idx ON public.delivery_cash_collections(delivery_job_id,collected_at DESC);

ALTER TABLE public.delivery_cash_collections ENABLE ROW LEVEL SECURITY;

REVOKE ALL ON public.delivery_cash_collections FROM PUBLIC,anon,authenticated;

GRANT ALL ON public.delivery_cash_collections TO service_role;

CREATE OR REPLACE FUNCTION private.delivery_payment_rpc_active()
RETURNS BOOLEAN LANGUAGE sql STABLE SET search_path='' AS $$ SELECT COALESCE(current_setting('app.delivery_payment_rpc',true),'')='1' $$;

REVOKE ALL ON FUNCTION private.delivery_payment_rpc_active() FROM PUBLIC,anon,authenticated;

CREATE OR REPLACE FUNCTION public._require_payments_staff()
RETURNS void LANGUAGE plpgsql STABLE SET search_path=public AS $$
BEGIN
 IF NOT (auth.role()='service_role' OR public.has_staff_role(ARRAY['admin','finance','sales']::public.staff_role[])
   OR (private.delivery_payment_rpc_active() AND public.has_staff_role(ARRAY['driver']::public.staff_role[]))) THEN
  RAISE EXCEPTION 'admin, finance, or sales role required for payments';
 END IF;
END $$;

CREATE OR REPLACE FUNCTION public.set_customer_cart_delivery_payment_method(p_cart_id UUID,p_method public.delivery_payment_method)
RETURNS UUID LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
DECLARE c public.pos_carts%ROWTYPE;
BEGIN
 PERFORM public._storefront_rpc_enter();
 SELECT * INTO c FROM public.pos_carts WHERE id=p_cart_id FOR UPDATE;
 IF NOT FOUND OR c.status<>'open' THEN RAISE EXCEPTION 'open cart required'; END IF;
 PERFORM public._assert_customer_owns_open_cart(p_cart_id);
 IF c.fulfillment_mode<>'dispatch' AND p_method<>'prepay' THEN RAISE EXCEPTION 'pay-on-delivery is available only for dispatch orders'; END IF;
 UPDATE public.pos_carts SET delivery_payment_method=p_method,updated_at=now() WHERE id=c.id;
 RETURN c.id;
END $$;

CREATE OR REPLACE FUNCTION public.checkout_customer_cart_v2(p_cart_id UUID,p_delivery_payment_method public.delivery_payment_method DEFAULT 'prepay')
RETURNS UUID LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
DECLARE c public.pos_carts%ROWTYPE; v_id UUID;
BEGIN
 PERFORM public._storefront_rpc_enter();
 BEGIN
  PERFORM public._assert_customer_owns_open_cart(p_cart_id);
  SELECT * INTO c FROM public.pos_carts WHERE id=p_cart_id FOR UPDATE;
  IF c.fulfillment_mode<>'dispatch' AND p_delivery_payment_method<>'prepay' THEN RAISE EXCEPTION 'pay-on-delivery requires dispatch fulfillment'; END IF;
  UPDATE public.pos_carts SET delivery_payment_method=p_delivery_payment_method,updated_at=now() WHERE id=c.id;
  v_id:=public.checkout_pos_cart(p_cart_id);
  UPDATE public.sales_invoices SET delivery_payment_method=p_delivery_payment_method WHERE id=v_id;
 EXCEPTION WHEN OTHERS THEN PERFORM public._storefront_rpc_exit(); RAISE; END;
 PERFORM public._storefront_rpc_exit(); RETURN v_id;
END $$;

CREATE OR REPLACE FUNCTION public.get_customer_order(p_invoice_id UUID)
RETURNS JSONB LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path=public AS $$
DECLARE v_inv public.sales_invoices%ROWTYPE;v_dn_status public.delivery_note_status;v_pick_status public.pick_list_status;v_job UUID;
BEGIN
 v_inv:=public._assert_customer_owns_invoice(p_invoice_id);
 SELECT dn.status INTO v_dn_status FROM public.delivery_notes dn WHERE dn.sales_invoice_id=v_inv.id ORDER BY dn.created_at DESC LIMIT 1;
 SELECT pl.status INTO v_pick_status FROM public.pick_lists pl WHERE pl.sales_invoice_id=v_inv.id ORDER BY pl.created_at DESC LIMIT 1;
 SELECT dj.id INTO v_job FROM public.delivery_jobs dj JOIN public.delivery_notes dn ON dn.id=dj.delivery_note_id
  WHERE dn.sales_invoice_id=v_inv.id AND dj.status NOT IN('completed','failed') ORDER BY dj.created_at DESC LIMIT 1;
 RETURN jsonb_build_object('invoice_id',v_inv.id,'document_number',v_inv.document_number,'doc_type',v_inv.doc_type,'status',v_inv.status,
  'fulfillment_mode',v_inv.fulfillment_mode,'delivery_payment_method',v_inv.delivery_payment_method,'currency',v_inv.currency,
  'exchange_rate_applied',v_inv.exchange_rate_applied,'subtotal',v_inv.subtotal,'total',v_inv.total,'amount_paid',v_inv.amount_paid,
  'amount_open',greatest(v_inv.total-v_inv.amount_paid,0),'cart_id',v_inv.cart_id,'posted_at',v_inv.posted_at,
  'pick_list_status',v_pick_status,'delivery_note_status',v_dn_status,'active_delivery_job_id',v_job);
END $$;

CREATE OR REPLACE FUNCTION public.get_delivery_job_payment_context(p_delivery_job_id UUID)
RETURNS JSONB LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
DECLARE j public.delivery_jobs%ROWTYPE;i public.sales_invoices%ROWTYPE;v_due NUMERIC;
BEGIN
 SELECT * INTO j FROM public.delivery_jobs WHERE id=p_delivery_job_id; IF NOT FOUND THEN RAISE EXCEPTION 'delivery job not found'; END IF;
 IF auth.role()<>'service_role' AND NOT public.has_staff_role(ARRAY['admin']::public.staff_role[]) AND j.assignee_user_id IS DISTINCT FROM auth.uid() THEN
  RAISE EXCEPTION 'not authorized for delivery job settlement'; END IF;
 SELECT si.* INTO i FROM public.delivery_notes dn JOIN public.sales_invoices si ON si.id=dn.sales_invoice_id WHERE dn.id=j.delivery_note_id;
 IF NOT FOUND THEN RAISE EXCEPTION 'delivery invoice not found'; END IF;
 v_due:=greatest(round(i.total-i.amount_paid,2),0);
 RETURN jsonb_build_object('delivery_job_id',j.id,'sales_invoice_id',i.id,'document_number',i.document_number,'currency',i.currency,
  'invoice_total',i.total,'amount_paid',i.amount_paid,'amount_due',v_due,'invoice_total_minor',public._major_to_minor(i.total),
  'amount_paid_minor',public._major_to_minor(i.amount_paid),'amount_due_minor',public._major_to_minor(v_due),
  'delivery_payment_method',i.delivery_payment_method,'may_collect_cash',i.delivery_payment_method IN('cash_on_delivery','cash_or_card_on_delivery'),
  'may_collect_card',i.delivery_payment_method IN('card_on_delivery','cash_or_card_on_delivery'),'job_status',j.status);
END $$;

CREATE OR REPLACE FUNCTION public.collect_delivery_cash(p_delivery_job_id UUID,p_amount NUMERIC,p_request_id UUID,p_notes TEXT DEFAULT NULL)
RETURNS JSONB LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
DECLARE j public.delivery_jobs%ROWTYPE;i public.sales_invoices%ROWTYPE;v_due NUMERIC;v_pe UUID;v_existing public.delivery_cash_collections%ROWTYPE;
BEGIN
 SELECT * INTO v_existing FROM public.delivery_cash_collections WHERE request_id=p_request_id;
 IF FOUND THEN RETURN jsonb_build_object('collection_id',v_existing.id,'payment_entry_id',v_existing.payment_entry_id,'amount',v_existing.amount,'currency',v_existing.currency); END IF;
 SELECT * INTO j FROM public.delivery_jobs WHERE id=p_delivery_job_id FOR UPDATE;
 IF NOT FOUND OR j.status<>'dispatched' OR j.assignee_user_id IS DISTINCT FROM auth.uid() OR NOT public.has_staff_role(ARRAY['driver']::public.staff_role[]) THEN
  RAISE EXCEPTION 'assigned dispatched driver job required'; END IF;
 SELECT si.* INTO i FROM public.delivery_notes dn JOIN public.sales_invoices si ON si.id=dn.sales_invoice_id WHERE dn.id=j.delivery_note_id FOR UPDATE OF si;
 IF i.delivery_payment_method NOT IN('cash_on_delivery','cash_or_card_on_delivery') THEN RAISE EXCEPTION 'this delivery is not authorized for cash collection'; END IF;
 v_due:=greatest(round(i.total-i.amount_paid,2),0); IF v_due<=0 THEN RAISE EXCEPTION 'invoice has no balance due'; END IF;
 IF COALESCE(p_amount,0)<=0 OR p_amount>v_due+0.01 THEN RAISE EXCEPTION 'cash amount must be >0 and <= delivery balance %',v_due; END IF;
 PERFORM set_config('app.delivery_payment_rpc','1',true);
 v_pe:=public.create_payment_entry(i.customer_id,'cash',round(p_amount,2),i.currency,i.exchange_rate_applied,
  concat_ws(' · ','Cash on delivery '||COALESCE(i.document_number,i.id::text),p_notes));
 PERFORM public.allocate_payment(v_pe,jsonb_build_array(jsonb_build_object('sales_invoice_id',i.id,'amount',round(p_amount,2))));
 PERFORM public.post_payment_entry(v_pe);
 PERFORM set_config('app.delivery_payment_rpc','',true);
 INSERT INTO public.delivery_cash_collections(request_id,delivery_job_id,sales_invoice_id,payment_entry_id,amount,currency,collected_by,notes)
 VALUES(p_request_id,j.id,i.id,v_pe,round(p_amount,2),i.currency,auth.uid(),p_notes) RETURNING * INTO v_existing;
 RETURN jsonb_build_object('collection_id',v_existing.id,'payment_entry_id',v_pe,'amount',v_existing.amount,'currency',v_existing.currency,
  'balance_due',greatest(v_due-v_existing.amount,0));
EXCEPTION WHEN OTHERS THEN PERFORM set_config('app.delivery_payment_rpc','',true); RAISE;
END $$;

ALTER TABLE public.pos_card_terminals ADD COLUMN IF NOT EXISTS allow_delivery BOOLEAN NOT NULL DEFAULT false;

ALTER TABLE public.pos_card_terminal_attempts ADD COLUMN IF NOT EXISTS delivery_job_id UUID REFERENCES public.delivery_jobs(id) ON DELETE RESTRICT;

CREATE INDEX IF NOT EXISTS pos_card_terminal_attempt_delivery_job_idx ON public.pos_card_terminal_attempts(delivery_job_id,created_at DESC);

ALTER TABLE public.pos_card_terminal_attempts DROP CONSTRAINT IF EXISTS pos_card_terminal_attempts_check;

ALTER TABLE public.pos_card_terminal_attempts ADD CONSTRAINT pos_card_terminal_attempts_check CHECK(
 (operation='purchase' AND ((commerce_order_id IS NOT NULL AND delivery_job_id IS NULL AND source_invoice_id IS NULL)
   OR (delivery_job_id IS NOT NULL AND source_invoice_id IS NOT NULL)))
 OR (operation='refund' AND (source_invoice_id IS NOT NULL OR split_refund_request_id IS NOT NULL))
 OR (operation='reversal' AND parent_attempt_id IS NOT NULL));

CREATE OR REPLACE FUNCTION public.set_pos_card_terminal_delivery_enabled(p_terminal_id UUID,p_enabled BOOLEAN)
RETURNS UUID LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
BEGIN IF NOT public.has_staff_role(ARRAY['admin']::public.staff_role[]) THEN RAISE EXCEPTION 'admin role required'; END IF;
 UPDATE public.pos_card_terminals SET allow_delivery=p_enabled,updated_at=now() WHERE id=p_terminal_id; IF NOT FOUND THEN RAISE EXCEPTION 'card terminal not found'; END IF; RETURN p_terminal_id; END $$;

CREATE OR REPLACE FUNCTION private.pos_card_terminal_attempt_payload(p_attempt_id UUID)
RETURNS JSONB LANGUAGE sql STABLE SECURITY DEFINER SET search_path='' AS $$
 SELECT jsonb_build_object(
  'attempt_id',a.id,'request_id',a.request_id,'operation',a.operation,'status',a.status,
  'terminal_id',a.terminal_id,'amount',a.amount,'currency',a.currency,'external_ref',a.external_ref,
  'terminal_transaction_id',a.terminal_transaction_id,'rrn',a.rrn,'authorization_code',a.authorization_code,
  'card_last4',a.card_last4,'card_scheme',a.card_scheme,'response_code',a.response_code,'response_message',a.response_message,
  'commerce_order_id',a.commerce_order_id,'delivery_job_id',a.delivery_job_id,'source_invoice_id',a.source_invoice_id,
  'parent_attempt_id',a.parent_attempt_id,'split_leg_id',a.split_leg_id,'split_refund_request_id',a.split_refund_request_id,
  'payment_entry_id',a.payment_entry_id,'invoice_id',a.invoice_id,'finance_refund_id',a.finance_refund_id,'finalization_error',a.finalization_error,
  'terminal',jsonb_build_object('id',t.id,'code',t.code,'label',t.label,'acquirer_name',t.acquirer_name,
    'external_terminal_id',t.external_terminal_id,'adapter_key',t.adapter_key,'adapter_config',t.adapter_config,'allow_delivery',t.allow_delivery)
 ) FROM public.pos_card_terminal_attempts a JOIN public.pos_card_terminals t ON t.id=a.terminal_id WHERE a.id=p_attempt_id;
$$;

REVOKE ALL ON FUNCTION private.pos_card_terminal_attempt_payload(UUID) FROM PUBLIC,anon,authenticated;

CREATE OR REPLACE FUNCTION public.list_delivery_card_terminals(p_warehouse_id UUID,p_device_id TEXT)
RETURNS JSONB LANGUAGE sql STABLE SECURITY DEFINER SET search_path='' AS $$
 SELECT COALESCE(jsonb_agg(jsonb_build_object('id',t.id,'code',t.code,'label',t.label,'acquirer_name',t.acquirer_name,
  'external_terminal_id',t.external_terminal_id,'adapter_key',t.adapter_key,'adapter_config',t.adapter_config,
  'warehouse_id',t.warehouse_id,'device_id',t.device_id,'allow_delivery',t.allow_delivery) ORDER BY t.label,t.code),'[]'::jsonb)
 FROM public.pos_card_terminals t WHERE public.is_staff() AND t.is_active AND t.allow_delivery
  AND (t.warehouse_id IS NULL OR t.warehouse_id=p_warehouse_id) AND (t.device_id IS NULL OR t.device_id=p_device_id);
$$;

REVOKE ALL ON FUNCTION public.list_delivery_card_terminals(UUID,TEXT) FROM PUBLIC,anon;

GRANT EXECUTE ON FUNCTION public.list_delivery_card_terminals(UUID,TEXT) TO authenticated,service_role;

CREATE OR REPLACE FUNCTION public.begin_delivery_card_terminal_payment(
 p_delivery_job_id UUID,p_terminal_id UUID,p_device_id TEXT,p_amount NUMERIC,p_request_id UUID)
RETURNS JSONB LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
DECLARE j public.delivery_jobs%ROWTYPE;i public.sales_invoices%ROWTYPE;t public.pos_card_terminals%ROWTYPE;a public.pos_card_terminal_attempts%ROWTYPE;v_due NUMERIC;v_ref TEXT;
BEGIN
 SELECT * INTO a FROM public.pos_card_terminal_attempts WHERE request_id=p_request_id; IF FOUND THEN RETURN private.pos_card_terminal_attempt_payload(a.id); END IF;
 SELECT * INTO j FROM public.delivery_jobs WHERE id=p_delivery_job_id FOR UPDATE;
 IF NOT FOUND OR j.status<>'dispatched' OR j.assignee_user_id IS DISTINCT FROM auth.uid() OR NOT public.has_staff_role(ARRAY['driver']::public.staff_role[]) THEN RAISE EXCEPTION 'assigned dispatched driver job required'; END IF;
 SELECT si.* INTO i FROM public.delivery_notes dn JOIN public.sales_invoices si ON si.id=dn.sales_invoice_id WHERE dn.id=j.delivery_note_id FOR UPDATE OF si;
 IF i.delivery_payment_method NOT IN('card_on_delivery','cash_or_card_on_delivery') THEN RAISE EXCEPTION 'this delivery is not authorized for card collection'; END IF;
 v_due:=greatest(round(i.total-i.amount_paid,2),0); IF COALESCE(p_amount,0)<=0 OR p_amount>v_due+0.01 THEN RAISE EXCEPTION 'card amount must be >0 and <= delivery balance %',v_due; END IF;
 SELECT * INTO t FROM public.pos_card_terminals WHERE id=p_terminal_id;
 IF NOT FOUND OR NOT t.is_active OR NOT t.allow_delivery OR (t.device_id IS NOT NULL AND t.device_id<>p_device_id) OR (t.warehouse_id IS NOT NULL AND t.warehouse_id<>i.warehouse_id) THEN
  RAISE EXCEPTION 'delivery-enabled swipe machine assigned to this device/warehouse required'; END IF;
 IF EXISTS(SELECT 1 FROM public.pos_card_terminal_attempts x WHERE x.delivery_job_id=j.id AND x.status IN('initiated','approved','unknown')) THEN RAISE EXCEPTION 'reconcile the unresolved delivery card payment before charging again'; END IF;
 v_ref:='GTR-DCT-'||replace(p_request_id::text,'-','');
 INSERT INTO public.pos_card_terminal_attempts(request_id,operation,terminal_id,delivery_job_id,source_invoice_id,amount,currency,external_ref,created_by)
 VALUES(p_request_id,'purchase',t.id,j.id,i.id,round(p_amount,2),i.currency,v_ref,auth.uid()) RETURNING * INTO a;
 RETURN private.pos_card_terminal_attempt_payload(a.id);
END $$;

CREATE OR REPLACE FUNCTION public.finalize_delivery_card_terminal_payment(p_attempt_id UUID)
RETURNS JSONB LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
DECLARE a public.pos_card_terminal_attempts%ROWTYPE;j public.delivery_jobs%ROWTYPE;i public.sales_invoices%ROWTYPE;v_due NUMERIC;v_pe UUID;
BEGIN
 SELECT * INTO a FROM public.pos_card_terminal_attempts WHERE id=p_attempt_id FOR UPDATE;
 IF NOT FOUND OR a.operation<>'purchase' OR a.delivery_job_id IS NULL OR a.source_invoice_id IS NULL OR a.status<>'approved' THEN RAISE EXCEPTION 'approved delivery card purchase required'; END IF;
 IF a.created_by IS DISTINCT FROM auth.uid() OR NOT public.has_staff_role(ARRAY['driver']::public.staff_role[]) THEN RAISE EXCEPTION 'delivery card payment belongs to another driver'; END IF;
 SELECT * INTO j FROM public.delivery_jobs WHERE id=a.delivery_job_id FOR UPDATE; IF j.assignee_user_id IS DISTINCT FROM auth.uid() OR j.status<>'dispatched' THEN RAISE EXCEPTION 'assigned dispatched delivery required'; END IF;
 SELECT * INTO i FROM public.sales_invoices WHERE id=a.source_invoice_id FOR UPDATE; v_due:=greatest(round(i.total-i.amount_paid,2),0);
 IF a.amount>v_due+0.01 THEN RAISE EXCEPTION 'approved terminal amount exceeds current invoice balance'; END IF;
 PERFORM set_config('app.delivery_payment_rpc','1',true);
 v_pe:=public.create_payment_entry(i.customer_id,'card_terminal',a.amount,i.currency,i.exchange_rate_applied,
  format('Card on delivery %s · txn %s',COALESCE(i.document_number,i.id::text),a.terminal_transaction_id));
 PERFORM public.allocate_payment(v_pe,jsonb_build_array(jsonb_build_object('sales_invoice_id',i.id,'amount',a.amount)));
 PERFORM public.post_payment_entry(v_pe); PERFORM set_config('app.delivery_payment_rpc','',true);
 UPDATE public.pos_card_terminal_attempts SET status='settled',payment_entry_id=v_pe,invoice_id=i.id,finalization_error=NULL,finalized_by=auth.uid(),finalized_at=now(),updated_at=now() WHERE id=a.id;
 RETURN private.pos_card_terminal_attempt_payload(a.id);
EXCEPTION WHEN OTHERS THEN PERFORM set_config('app.delivery_payment_rpc','',true); RAISE;
END $$;

CREATE OR REPLACE FUNCTION public.record_pos_card_terminal_result(
 p_actor_user_id UUID,p_attempt_id UUID,p_outcome TEXT,p_terminal_transaction_id TEXT DEFAULT NULL,p_rrn TEXT DEFAULT NULL,
 p_authorization_code TEXT DEFAULT NULL,p_card_last4 TEXT DEFAULT NULL,p_card_scheme TEXT DEFAULT NULL,p_response_code TEXT DEFAULT NULL,p_response_message TEXT DEFAULT NULL)
RETURNS JSONB LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
DECLARE a public.pos_card_terminal_attempts%ROWTYPE;v_status public.pos_card_terminal_attempt_status;parent public.pos_card_terminal_attempts%ROWTYPE;v_actor_ok BOOLEAN:=false;
BEGIN
 IF auth.role()<>'service_role' THEN PERFORM public._require_payments_staff(); END IF;
 SELECT * INTO a FROM public.pos_card_terminal_attempts WHERE id=p_attempt_id FOR UPDATE; IF NOT FOUND THEN RAISE EXCEPTION 'card terminal attempt not found'; END IF;
 v_actor_ok:=EXISTS(SELECT 1 FROM public.staff_roles sr WHERE sr.user_id=p_actor_user_id AND sr.role IN('admin','finance','sales'));
 IF a.delivery_job_id IS NOT NULL THEN v_actor_ok:=v_actor_ok OR (a.created_by=p_actor_user_id AND EXISTS(SELECT 1 FROM public.staff_roles sr WHERE sr.user_id=p_actor_user_id AND sr.role='driver')); END IF;
 IF NOT v_actor_ok THEN RAISE EXCEPTION 'active authorized terminal actor required'; END IF;
 IF a.created_by<>p_actor_user_id AND NOT EXISTS(SELECT 1 FROM public.staff_roles sr WHERE sr.user_id=p_actor_user_id AND sr.role IN('admin','finance')) THEN RAISE EXCEPTION 'card terminal attempt access denied'; END IF;
 IF p_outcome NOT IN('approved','declined','cancelled','unknown','failed') THEN RAISE EXCEPTION 'unsupported terminal outcome'; END IF; v_status:=p_outcome::public.pos_card_terminal_attempt_status;
 IF a.status IN('settled','reversed') THEN RETURN private.pos_card_terminal_attempt_payload(a.id); END IF;
 IF a.status='approved' AND v_status<>'approved' THEN RAISE EXCEPTION 'approved terminal result cannot be downgraded; reverse or finalize it'; END IF;
 IF a.status NOT IN('initiated','unknown','approved') THEN IF a.status=v_status THEN RETURN private.pos_card_terminal_attempt_payload(a.id); END IF; RAISE EXCEPTION 'terminal result cannot transition from % to %',a.status,v_status; END IF;
 IF p_card_last4 IS NOT NULL AND p_card_last4!~'^[0-9]{4}$' THEN RAISE EXCEPTION 'card_last4 must contain exactly four digits'; END IF;
 IF length(COALESCE(p_terminal_transaction_id,''))>96 OR length(COALESCE(p_rrn,''))>64 OR length(COALESCE(p_authorization_code,''))>32 OR length(COALESCE(p_card_scheme,''))>32 OR length(COALESCE(p_response_code,''))>32 OR length(COALESCE(p_response_message,''))>240 THEN RAISE EXCEPTION 'terminal result field exceeds safe length'; END IF;
 IF v_status='approved' AND (trim(COALESCE(p_terminal_transaction_id,''))='' OR (trim(COALESCE(p_rrn,''))='' AND trim(COALESCE(p_authorization_code,''))='')) THEN RAISE EXCEPTION 'approved result requires terminal transaction id plus RRN or authorization code'; END IF;
 UPDATE public.pos_card_terminal_attempts SET status=v_status,terminal_transaction_id=COALESCE(NULLIF(trim(COALESCE(p_terminal_transaction_id,'')),''),terminal_transaction_id),
  rrn=COALESCE(NULLIF(trim(COALESCE(p_rrn,'')),''),rrn),authorization_code=COALESCE(NULLIF(trim(COALESCE(p_authorization_code,'')),''),authorization_code),
  card_last4=COALESCE(NULLIF(trim(COALESCE(p_card_last4,'')),''),card_last4),card_scheme=COALESCE(NULLIF(trim(COALESCE(p_card_scheme,'')),''),card_scheme),
  response_code=COALESCE(NULLIF(trim(COALESCE(p_response_code,'')),''),response_code),response_message=COALESCE(NULLIF(trim(COALESCE(p_response_message,'')),''),response_message),
  result_recorded_by=p_actor_user_id,result_recorded_at=now(),approved_at=CASE WHEN v_status='approved' THEN COALESCE(approved_at,now()) ELSE approved_at END,updated_at=now() WHERE id=a.id RETURNING * INTO a;
 IF a.delivery_job_id IS NULL THEN
  IF a.operation='purchase' THEN
   IF v_status='unknown' THEN UPDATE public.commerce_orders SET state='payment_processing',active_payment_provider='card_terminal',active_payment_intent_id=a.id,payment_exception='CARD_TERMINAL_OUTCOME_UNKNOWN',updated_at=now() WHERE id=a.commerce_order_id;
   ELSIF v_status='approved' THEN UPDATE public.commerce_orders SET state='payment_processing',active_payment_provider='card_terminal',active_payment_intent_id=a.id,payment_exception=NULL,updated_at=now() WHERE id=a.commerce_order_id;
   ELSE UPDATE public.commerce_orders SET state='payment_failed',active_payment_provider=NULL,active_payment_intent_id=NULL,payment_exception=NULL,updated_at=now() WHERE id=a.commerce_order_id AND settled_payment_entry_id IS NULL; END IF;
  ELSIF a.operation='reversal' THEN
   SELECT * INTO parent FROM public.pos_card_terminal_attempts WHERE id=a.parent_attempt_id FOR UPDATE;
   IF v_status='approved' THEN UPDATE public.pos_card_terminal_attempts SET status='reversed',finalized_by=p_actor_user_id,finalized_at=now(),updated_at=now() WHERE id=parent.id; UPDATE public.pos_card_terminal_attempts SET status='settled',finalized_by=p_actor_user_id,finalized_at=now(),updated_at=now() WHERE id=a.id;
   ELSIF v_status='unknown' THEN UPDATE public.commerce_orders SET state='payment_processing',active_payment_provider='card_terminal',active_payment_intent_id=a.id,payment_exception='CARD_TERMINAL_REVERSAL_UNKNOWN',updated_at=now() WHERE id=parent.commerce_order_id;
   END IF;
  END IF;
 END IF;
 RETURN private.pos_card_terminal_attempt_payload(a.id);
EXCEPTION WHEN unique_violation THEN RAISE EXCEPTION 'terminal transaction id was already recorded; reconcile existing attempt instead of charging again';
END $$;

CREATE OR REPLACE FUNCTION private.delivery_payment_completion_guard()
RETURNS trigger LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
DECLARE i public.sales_invoices%ROWTYPE;
BEGIN
 IF NEW.status='completed' AND OLD.status IS DISTINCT FROM 'completed' THEN
  SELECT si.* INTO i FROM public.delivery_notes dn JOIN public.sales_invoices si ON si.id=dn.sales_invoice_id WHERE dn.id=NEW.delivery_note_id;
  IF i.delivery_payment_method IS NOT NULL AND i.amount_paid+0.01<i.total THEN RAISE EXCEPTION 'delivery cannot complete until required payment clears; outstanding % %',i.currency,round(i.total-i.amount_paid,2); END IF;
 END IF; RETURN NEW;
END $$;

DROP TRIGGER IF EXISTS delivery_payment_completion_guard ON public.delivery_jobs;

CREATE TRIGGER delivery_payment_completion_guard BEFORE UPDATE OF status ON public.delivery_jobs FOR EACH ROW EXECUTE FUNCTION private.delivery_payment_completion_guard();

REVOKE ALL ON FUNCTION public.set_customer_cart_delivery_payment_method(UUID,public.delivery_payment_method),public.checkout_customer_cart_v2(UUID,public.delivery_payment_method),
 public.get_delivery_job_payment_context(UUID),public.collect_delivery_cash(UUID,NUMERIC,UUID,TEXT),public.set_pos_card_terminal_delivery_enabled(UUID,BOOLEAN),
 public.begin_delivery_card_terminal_payment(UUID,UUID,TEXT,NUMERIC,UUID),public.finalize_delivery_card_terminal_payment(UUID) FROM PUBLIC,anon;

GRANT EXECUTE ON FUNCTION public.set_customer_cart_delivery_payment_method(UUID,public.delivery_payment_method),public.checkout_customer_cart_v2(UUID,public.delivery_payment_method) TO authenticated,service_role;

GRANT EXECUTE ON FUNCTION public.get_delivery_job_payment_context(UUID),public.collect_delivery_cash(UUID,NUMERIC,UUID,TEXT),
 public.begin_delivery_card_terminal_payment(UUID,UUID,TEXT,NUMERIC,UUID),public.finalize_delivery_card_terminal_payment(UUID) TO authenticated,service_role;

GRANT EXECUTE ON FUNCTION public.set_pos_card_terminal_delivery_enabled(UUID,BOOLEAN) TO authenticated,service_role;

REVOKE ALL ON FUNCTION private.delivery_payment_rpc_active(),private.delivery_payment_completion_guard() FROM PUBLIC,anon,authenticated;

REVOKE ALL ON FUNCTION public.record_pos_card_terminal_result(UUID,UUID,TEXT,TEXT,TEXT,TEXT,TEXT,TEXT,TEXT,TEXT) FROM PUBLIC,anon,authenticated;

GRANT EXECUTE ON FUNCTION public.record_pos_card_terminal_result(UUID,UUID,TEXT,TEXT,TEXT,TEXT,TEXT,TEXT,TEXT,TEXT) TO service_role;
