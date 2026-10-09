-- Exported from the hosted project's supabase_migrations.schema_migrations (20260908121000 pos_reserve_first_manual_split).
-- Source of record for what production ran; see supabase/live-history/README.md.

-- Reserve-first manual split settlement for POS.
-- Digital providers settle only through verified provider intents/webhooks.
ALTER TABLE public.commerce_orders DROP CONSTRAINT IF EXISTS commerce_orders_settled_provider_check;

ALTER TABLE public.commerce_orders ADD CONSTRAINT commerce_orders_settled_provider_check
 CHECK (settled_provider IS NULL OR settled_provider IN ('contipay','paynow','ecocash','cash','bank','store_credit','manual_split'));

CREATE TABLE IF NOT EXISTS public.pos_commerce_tender_settlements (
  payment_request_id UUID PRIMARY KEY,
  commerce_order_id UUID NOT NULL REFERENCES public.commerce_orders(id) ON DELETE RESTRICT,
  invoice_id UUID REFERENCES public.sales_invoices(id) ON DELETE RESTRICT,
  tender_snapshot JSONB NOT NULL,
  payment_entry_ids JSONB NOT NULL DEFAULT '[]'::jsonb,
  created_by UUID NOT NULL REFERENCES auth.users(id),
  completed_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

ALTER TABLE public.pos_commerce_tender_settlements ENABLE ROW LEVEL SECURITY;

CREATE POLICY pos_commerce_tender_settlements_staff_select ON public.pos_commerce_tender_settlements
 FOR SELECT TO authenticated USING(public.has_staff_role(ARRAY['admin','sales','finance']::public.staff_role[]));

GRANT SELECT ON public.pos_commerce_tender_settlements TO authenticated,service_role;

GRANT ALL ON public.pos_commerce_tender_settlements TO service_role;

CREATE OR REPLACE FUNCTION public.settle_pos_commerce_tenders(
 p_order_id UUID,p_payment_request_id UUID,p_tenders JSONB)
RETURNS JSONB LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
DECLARE o public.commerce_orders%ROWTYPE; e JSONB; v_tender public.payment_tender; v_amt NUMERIC; v_sum NUMERIC:=0;
 v_inv UUID; v_pe UUID; v_first UUID; v_ids JSONB:='[]'::jsonb; v_distinct INTEGER; v_provider TEXT;
 existing public.pos_commerce_tender_settlements%ROWTYPE;
BEGIN
 PERFORM public._require_payments_staff();
 IF p_order_id IS NULL OR p_payment_request_id IS NULL THEN RAISE EXCEPTION 'order and payment request ids required'; END IF;
 IF p_tenders IS NULL OR jsonb_typeof(p_tenders)<>'array' OR jsonb_array_length(p_tenders)=0 THEN RAISE EXCEPTION 'non-empty tenders array required'; END IF;
 SELECT * INTO existing FROM public.pos_commerce_tender_settlements WHERE payment_request_id=p_payment_request_id;
 IF FOUND THEN
  IF existing.commerce_order_id<>p_order_id THEN RAISE EXCEPTION 'payment request id already belongs to another order'; END IF;
  IF existing.completed_at IS NOT NULL THEN
   RETURN jsonb_build_object('order_id',p_order_id,'invoice_id',existing.invoice_id,'payment_entry_ids',existing.payment_entry_ids,
    'state',(SELECT state FROM public.commerce_orders WHERE id=p_order_id));
  END IF;
 END IF;
 SELECT * INTO o FROM public.commerce_orders WHERE id=p_order_id FOR UPDATE;
 IF NOT FOUND THEN RAISE EXCEPTION 'commerce order not found'; END IF;
 IF o.settled_payment_entry_id IS NOT NULL THEN RAISE EXCEPTION 'commerce order already settled'; END IF;
 IF o.reservation_expires_at IS NULL OR o.reservation_expires_at<=now() THEN RAISE EXCEPTION 'commerce reservation expired'; END IF;
 IF o.state NOT IN('awaiting_payment','payment_failed') THEN RAISE EXCEPTION 'manual tenders cannot settle order in state %',o.state; END IF;
 FOR e IN SELECT * FROM jsonb_array_elements(p_tenders) LOOP
  v_tender:=(e->>'tender')::public.payment_tender;
  IF v_tender NOT IN('cash','bank','store_credit') THEN RAISE EXCEPTION 'digital tender % must use provider intent/webhook settlement',v_tender; END IF;
  v_amt:=round(COALESCE((e->>'amount')::numeric,0),2);
  IF v_amt<=0 THEN RAISE EXCEPTION 'tender amount must be > 0'; END IF;
  v_sum:=v_sum+v_amt;
 END LOOP;
 IF abs(v_sum-o.total)>0.01 THEN RAISE EXCEPTION 'manual tenders sum % must equal order total %',v_sum,o.total; END IF;
 INSERT INTO public.pos_commerce_tender_settlements(payment_request_id,commerce_order_id,tender_snapshot,created_by)
 VALUES(p_payment_request_id,p_order_id,p_tenders,auth.uid()) ON CONFLICT(payment_request_id) DO NOTHING;
 v_inv:=private.finalize_commerce_order(o.id);
 FOR e IN SELECT * FROM jsonb_array_elements(p_tenders) LOOP
  v_tender:=(e->>'tender')::public.payment_tender; v_amt:=round((e->>'amount')::numeric,2);
  v_pe:=public.create_payment_entry(o.customer_id,v_tender,v_amt,o.currency,o.exchange_rate_applied,
    format('POS reserve-first %s %s',v_tender,o.id));
  PERFORM public.allocate_payment(v_pe,jsonb_build_array(jsonb_build_object('sales_invoice_id',v_inv,'amount',v_amt)));
  PERFORM public.post_payment_entry(v_pe);
  IF v_first IS NULL THEN v_first:=v_pe; END IF;
  v_ids:=v_ids||jsonb_build_array(v_pe);
 END LOOP;
 SELECT count(DISTINCT x->>'tender') INTO v_distinct FROM jsonb_array_elements(p_tenders) x;
 v_provider:=CASE WHEN v_distinct=1 THEN (p_tenders->0->>'tender') ELSE 'manual_split' END;
 UPDATE public.commerce_orders SET settled_payment_entry_id=v_first,settled_provider=v_provider,
  settled_provider_ref='POS-MANUAL-'||p_payment_request_id::text,payment_exception=NULL,
  state=CASE WHEN fulfillment_mode='dispatch' THEN 'allocation_pending'::public.commerce_order_state ELSE 'paid'::public.commerce_order_state END,
  updated_at=now() WHERE id=o.id;
 UPDATE public.pos_commerce_tender_settlements SET invoice_id=v_inv,payment_entry_ids=v_ids,completed_at=now()
 WHERE payment_request_id=p_payment_request_id;
 PERFORM private.enqueue_commerce_event('commerce:'||o.id::text||':manual-settled:'||p_payment_request_id::text,
  'commerce.payment_settled',o.id,jsonb_build_object('provider',v_provider,'payment_entry_ids',v_ids,'sales_invoice_id',v_inv));
 RETURN jsonb_build_object('order_id',o.id,'invoice_id',v_inv,'payment_entry_ids',v_ids,
  'state',(SELECT state FROM public.commerce_orders WHERE id=o.id));
END $$;

REVOKE ALL ON FUNCTION public.settle_pos_commerce_tenders(UUID,UUID,JSONB) FROM PUBLIC,anon;

GRANT EXECUTE ON FUNCTION public.settle_pos_commerce_tenders(UUID,UUID,JSONB) TO authenticated,service_role;
