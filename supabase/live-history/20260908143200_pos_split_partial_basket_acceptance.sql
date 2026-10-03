-- Exported from the hosted project's supabase_migrations.schema_migrations (20260908143200 pos_split_partial_basket_acceptance).
-- Source of record for what production ran; see supabase/live-history/README.md.

-- Customer-confirmed reduced-basket resolution for staged POS split payments.
-- Captured surplus stays in split suspense until its refund actually settles.

ALTER TABLE public.pos_split_payment_legs
 ADD COLUMN IF NOT EXISTS applied_target_amount NUMERIC(18,2),
 ADD COLUMN IF NOT EXISTS refund_required_amount NUMERIC(18,2) NOT NULL DEFAULT 0;

ALTER TABLE public.pos_split_payment_legs DROP CONSTRAINT IF EXISTS pos_split_leg_applied_target_check;

ALTER TABLE public.pos_split_payment_legs ADD CONSTRAINT pos_split_leg_applied_target_check
 CHECK(applied_target_amount IS NULL OR (applied_target_amount>=0 AND applied_target_amount<=requested_amount));

ALTER TABLE public.pos_split_payment_legs DROP CONSTRAINT IF EXISTS pos_split_leg_refund_required_check;

ALTER TABLE public.pos_split_payment_legs ADD CONSTRAINT pos_split_leg_refund_required_check
 CHECK(refund_required_amount>=0 AND refund_required_amount<=requested_amount);

CREATE TABLE IF NOT EXISTS public.pos_split_acceptance_lines(
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
 session_id UUID NOT NULL REFERENCES public.pos_split_payment_sessions(id) ON DELETE RESTRICT,
 cart_line_id UUID NOT NULL REFERENCES public.pos_cart_lines(id) ON DELETE RESTRICT,
 original_qty NUMERIC(18,3) NOT NULL CHECK(original_qty>0),
 accepted_qty NUMERIC(18,3) NOT NULL CHECK(accepted_qty>0),
 accepted_line_total NUMERIC(18,2) NOT NULL CHECK(accepted_line_total>=0),
 auto_included_core BOOLEAN NOT NULL DEFAULT false,
 accepted_by UUID NOT NULL REFERENCES auth.users(id),
 accepted_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 UNIQUE(session_id,cart_line_id)
);

CREATE INDEX IF NOT EXISTS pos_split_acceptance_session_idx ON public.pos_split_acceptance_lines(session_id,accepted_at);

ALTER TABLE public.pos_split_acceptance_lines ENABLE ROW LEVEL SECURITY;

REVOKE ALL ON public.pos_split_acceptance_lines FROM PUBLIC,anon,authenticated;

GRANT ALL ON public.pos_split_acceptance_lines TO service_role;

ALTER TABLE public.pos_split_payment_sessions
 ADD COLUMN IF NOT EXISTS reduced_basket_accepted_at TIMESTAMPTZ,
 ADD COLUMN IF NOT EXISTS reduced_basket_accepted_by UUID REFERENCES auth.users(id),
 ADD COLUMN IF NOT EXISTS reduced_basket_customer_confirmed BOOLEAN NOT NULL DEFAULT false,
 ADD COLUMN IF NOT EXISTS reduced_basket_notes TEXT;

CREATE OR REPLACE FUNCTION private.pos_split_payment_payload(p_session_id UUID)
RETURNS JSONB LANGUAGE sql STABLE SECURITY DEFINER SET search_path='' AS $$
 SELECT jsonb_build_object(
  'session_id',s.id,'order_id',s.commerce_order_id,'status',s.status,'total',s.total_amount,'currency',s.currency,
  'captured_amount',s.captured_amount,'held_amount',s.held_amount,'pending_amount',s.pending_amount,'refunded_amount',s.refunded_amount,
  'locked_amount',round(s.captured_amount+s.held_amount,2),
  'balance_due',greatest(round(s.total_amount-s.captured_amount-s.held_amount,2),0),
  'available_to_allocate',greatest(round(s.total_amount-s.captured_amount-s.held_amount-s.pending_amount,2),0),
  'final_invoice_id',s.final_invoice_id,'finalization_error',s.finalization_error,
  'reduced_basket_accepted_at',s.reduced_basket_accepted_at,'reduced_basket_customer_confirmed',s.reduced_basket_customer_confirmed,
  'reduced_basket_notes',s.reduced_basket_notes,'created_at',s.created_at,'updated_at',s.updated_at,
  'legs',COALESCE((SELECT jsonb_agg(jsonb_build_object(
    'id',l.id,'request_id',l.request_id,'sequence_no',l.sequence_no,'tender',l.tender,'amount',l.requested_amount,'status',l.status,
    'applied_target_amount',l.applied_target_amount,'refund_required_amount',l.refund_required_amount,
    'external_reference',l.external_reference,'provider_intent_id',l.provider_intent_id,'provider_ref',l.provider_ref,
    'card_terminal_attempt_id',l.card_terminal_attempt_id,'payment_entry_id',l.payment_entry_id,'status_detail',l.status_detail,
    'created_at',l.created_at,'locked_at',l.locked_at,'failed_at',l.failed_at,'refunded_at',l.refunded_at
   ) ORDER BY l.sequence_no) FROM public.pos_split_payment_legs l WHERE l.session_id=s.id),'[]'::jsonb),
  'accepted_lines',COALESCE((SELECT jsonb_agg(jsonb_build_object(
    'cart_line_id',a.cart_line_id,'original_qty',a.original_qty,'accepted_qty',a.accepted_qty,
    'accepted_line_total',a.accepted_line_total,'auto_included_core',a.auto_included_core
   ) ORDER BY a.accepted_at,a.cart_line_id) FROM public.pos_split_acceptance_lines a WHERE a.session_id=s.id),'[]'::jsonb),
  'refunds',COALESCE((SELECT jsonb_agg(jsonb_build_object(
    'id',r.id,'leg_id',r.leg_id,'status',r.status,'gross_amount',r.gross_amount,'fee_policy',r.fee_policy,
    'estimated_provider_fee',r.estimated_provider_fee,'estimated_transfer_fee',r.estimated_transfer_fee,
    'approved_customer_fee',r.approved_customer_fee,'actual_provider_fee',r.actual_provider_fee,
    'actual_transfer_fee',r.actual_transfer_fee,'actual_customer_fee',r.actual_customer_fee,
    'net_customer_refund',r.net_customer_refund,'provider_ref',r.provider_ref,'expected_settlement_at',r.expected_settlement_at,
    'failure_reason',r.failure_reason,'notes',r.notes
   ) ORDER BY r.created_at) FROM public.pos_split_refund_requests r WHERE r.session_id=s.id),'[]'::jsonb)
 ) FROM public.pos_split_payment_sessions s WHERE s.id=p_session_id;
$$;

REVOKE ALL ON FUNCTION private.pos_split_payment_payload(UUID) FROM PUBLIC,anon,authenticated;

CREATE OR REPLACE FUNCTION private.allocate_pos_split_receipt_to_invoice(p_leg_id UUID,p_invoice_id UUID)
RETURNS UUID LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
DECLARE l public.pos_split_payment_legs%ROWTYPE; pe public.payment_entries%ROWTYPE; i public.sales_invoices%ROWTYPE;
 v_open NUMERIC; v_apply NUMERIC; v_journal UUID;
BEGIN
 PERFORM public._payments_rpc_enter();
 SELECT * INTO l FROM public.pos_split_payment_legs WHERE id=p_leg_id FOR UPDATE;
 IF NOT FOUND OR l.status NOT IN('captured','refund_review','refund_pending') OR l.payment_entry_id IS NULL THEN RAISE EXCEPTION 'captured split leg required'; END IF;
 SELECT * INTO pe FROM public.payment_entries WHERE id=l.payment_entry_id FOR UPDATE;
 SELECT * INTO i FROM public.sales_invoices WHERE id=p_invoice_id FOR UPDATE;
 IF pe.status<>'posted' OR i.status<>'posted' OR i.doc_type<>'invoice' OR pe.customer_id IS DISTINCT FROM i.customer_id OR pe.currency<>i.currency THEN
  RAISE EXCEPTION 'split receipt/invoice mismatch'; END IF;
 IF EXISTS(SELECT 1 FROM public.payment_allocations WHERE payment_entry_id=pe.id) THEN RETURN pe.id; END IF;
 v_apply:=round(COALESCE(l.applied_target_amount,l.requested_amount),2);
 IF v_apply<0 OR v_apply>pe.amount+0.01 THEN RAISE EXCEPTION 'invalid split applied target'; END IF;
 IF v_apply>0 THEN
  v_open:=round(i.total-i.amount_paid,2); IF v_apply>v_open+0.01 THEN RAISE EXCEPTION 'split receipt exceeds invoice open balance'; END IF;
  v_journal:=public.post_journal_entry(CURRENT_DATE,format('Apply POS split receipt %s to %s',pe.document_number,i.document_number),pe.currency,pe.exchange_rate_applied,
   jsonb_build_array(jsonb_build_object('account_code','2215','debit',v_apply,'credit',0,'currency',pe.currency),
                     jsonb_build_object('account_code','1200','debit',0,'credit',v_apply,'currency',pe.currency)));
  INSERT INTO public.payment_allocations(payment_entry_id,sales_invoice_id,amount,currency,exchange_rate_applied)
  VALUES(pe.id,i.id,v_apply,pe.currency,pe.exchange_rate_applied);
  UPDATE public.sales_invoices SET amount_paid=amount_paid+v_apply WHERE id=i.id;
  UPDATE public.customers SET open_balance=greatest(0,open_balance-v_apply),updated_at=now() WHERE id=pe.customer_id;
 END IF;
 UPDATE public.pos_split_payment_legs SET
  status=CASE WHEN refund_required_amount>0.009 THEN 'refund_review'::public.pos_split_payment_leg_status ELSE 'allocated'::public.pos_split_payment_leg_status END,
  allocation_journal_entry_id=v_journal,allocated_at=CASE WHEN v_apply>0 THEN now() ELSE allocated_at END,updated_at=now() WHERE id=l.id;
 RETURN pe.id;
END $$;

REVOKE ALL ON FUNCTION private.allocate_pos_split_receipt_to_invoice(UUID,UUID) FROM PUBLIC,anon,authenticated;

CREATE OR REPLACE FUNCTION private.finalize_pos_split_payment_session(p_session_id UUID)
RETURNS JSONB LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
DECLARE s public.pos_split_payment_sessions%ROWTYPE; o public.commerce_orders%ROWTYPE; l public.pos_split_payment_legs%ROWTYPE;
 v_inv UUID; v_pe UUID; v_first UUID; v_count INTEGER; v_error TEXT; v_apply NUMERIC; v_refunds INTEGER; v_status public.pos_split_payment_session_status;
BEGIN
 PERFORM private.refresh_pos_split_payment_session(p_session_id);
 SELECT * INTO s FROM public.pos_split_payment_sessions WHERE id=p_session_id FOR UPDATE;
 SELECT * INTO o FROM public.commerce_orders WHERE id=s.commerce_order_id FOR UPDATE;
 IF s.final_invoice_id IS NOT NULL AND s.status IN('settled','refund_review','refund_pending') THEN RETURN private.pos_split_payment_payload(s.id); END IF;
 IF s.pending_amount>0 OR s.captured_amount+s.held_amount+0.01<s.total_amount THEN RETURN private.pos_split_payment_payload(s.id); END IF;
 UPDATE public.pos_split_payment_sessions SET status='finalizing',finalization_error=NULL,updated_at=now() WHERE id=s.id;
 BEGIN
  v_inv:=private.finalize_commerce_order(o.id);
  FOR l IN SELECT * FROM public.pos_split_payment_legs WHERE session_id=s.id AND status IN('captured','held') ORDER BY sequence_no FOR UPDATE LOOP
   v_apply:=round(COALESCE(l.applied_target_amount,l.requested_amount),2);
   IF l.status='captured' THEN
    IF l.refund_required_amount>0.009 THEN
     INSERT INTO public.pos_split_refund_requests(session_id,leg_id,gross_amount,fee_policy,external_reference,notes,created_by)
     VALUES(s.id,l.id,l.refund_required_amount,'manual_review','GTR-SPLIT-SURPLUS-'||l.id::text,
       'Customer accepted a reduced basket; captured surplus requires explicit refund resolution.',auth.uid())
     ON CONFLICT(leg_id) DO UPDATE SET gross_amount=EXCLUDED.gross_amount,notes=EXCLUDED.notes,updated_at=now()
     WHERE public.pos_split_refund_requests.status IN('review','failed');
    END IF;
    v_pe:=private.allocate_pos_split_receipt_to_invoice(l.id,v_inv);
   ELSE
    IF v_apply<=0.009 THEN
     UPDATE public.pos_split_payment_legs SET status='cancelled',requested_amount=greatest(requested_amount,0.01),applied_target_amount=0,updated_at=now() WHERE id=l.id;
     CONTINUE;
    END IF;
    IF v_apply<l.requested_amount-0.009 THEN
     UPDATE public.pos_split_payment_legs SET metadata=metadata||jsonb_build_object('original_held_amount',requested_amount),requested_amount=v_apply,
      applied_target_amount=v_apply,updated_at=now() WHERE id=l.id;
    END IF;
    v_pe:=public.create_payment_entry(o.customer_id,'store_credit',v_apply,o.currency,o.exchange_rate_applied,format('POS split store credit %s',s.id));
    PERFORM public.allocate_payment(v_pe,jsonb_build_array(jsonb_build_object('sales_invoice_id',v_inv,'amount',v_apply)));
    PERFORM public.post_payment_entry(v_pe);
    UPDATE public.pos_split_payment_legs SET status='allocated',payment_entry_id=v_pe,allocated_at=now(),locked_at=COALESCE(locked_at,now()),updated_at=now() WHERE id=l.id;
   END IF;
   IF v_first IS NULL AND v_apply>0.009 THEN v_first:=v_pe; END IF;
  END LOOP;
 EXCEPTION WHEN OTHERS THEN
  v_error:=SQLERRM;
  UPDATE public.pos_split_payment_sessions SET status='finalization_failed',finalization_error=left(v_error,500),updated_at=now() WHERE id=s.id;
  UPDATE public.commerce_orders SET state='payment_processing',payment_exception='SPLIT_FINALIZATION_FAILED: '||left(v_error,400),updated_at=now() WHERE id=o.id;
  RETURN private.pos_split_payment_payload(s.id);
 END;
 IF NOT EXISTS(SELECT 1 FROM public.sales_invoices i WHERE i.id=v_inv AND i.amount_paid+0.01>=i.total) THEN
  RAISE EXCEPTION 'split finalization produced an invoice that is not fully paid';
 END IF;
 SELECT count(*) INTO v_count FROM public.pos_split_payment_legs WHERE session_id=s.id AND (status='allocated' OR payment_entry_id IS NOT NULL);
 UPDATE public.commerce_orders SET settled_payment_entry_id=v_first,settled_provider=CASE WHEN v_count=1 THEN
   (SELECT tender::text FROM public.pos_split_payment_legs WHERE session_id=s.id AND payment_entry_id IS NOT NULL ORDER BY sequence_no LIMIT 1)
  ELSE 'split_payment' END,
  settled_provider_ref='POS-SPLIT-'||s.id::text,payment_exception=NULL,active_payment_provider=NULL,active_payment_intent_id=NULL,
  state=CASE WHEN fulfillment_mode='dispatch' THEN 'allocation_pending'::public.commerce_order_state ELSE 'paid'::public.commerce_order_state END,
  updated_at=now() WHERE id=o.id;
 SELECT count(*) INTO v_refunds FROM public.pos_split_refund_requests WHERE session_id=s.id AND status<>'settled' AND status<>'cancelled';
 v_status:=CASE WHEN v_refunds>0 THEN 'refund_review'::public.pos_split_payment_session_status ELSE 'settled'::public.pos_split_payment_session_status END;
 UPDATE public.pos_split_payment_sessions SET status=v_status,final_invoice_id=v_inv,settled_at=now(),finalization_error=NULL,
  held_amount=0,pending_amount=0,updated_at=now() WHERE id=s.id;
 PERFORM private.enqueue_commerce_event('commerce:'||o.id::text||':split-settled:'||s.id::text,'commerce.payment_settled',o.id,
  jsonb_build_object('provider','split_payment','split_session_id',s.id,'sales_invoice_id',v_inv,'refunds_outstanding',v_refunds));
 RETURN private.pos_split_payment_payload(s.id);
END $$;

REVOKE ALL ON FUNCTION private.finalize_pos_split_payment_session(UUID) FROM PUBLIC,anon,authenticated;

CREATE OR REPLACE FUNCTION public.accept_pos_split_affordable_items(
 p_session_id UUID,p_items JSONB,p_customer_confirmed BOOLEAN,p_notes TEXT DEFAULT NULL)
RETURNS JSONB LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
DECLARE s public.pos_split_payment_sessions%ROWTYPE; o public.commerce_orders%ROWTYPE; c public.pos_carts%ROWTYPE; l public.pos_cart_lines%ROWTYPE;
 e JSONB; v_id UUID; v_qty NUMERIC; v_fraction NUMERIC; v_total NUMERIC:=0; v_locked NUMERIC; v_remaining NUMERIC; v_apply NUMERIC; v_refund NUMERIC;
 leg public.pos_split_payment_legs%ROWTYPE; r RECORD; v_available NUMERIC; v_lines JSONB;
BEGIN
 PERFORM public._require_payments_staff();
 IF NOT COALESCE(p_customer_confirmed,false) THEN RAISE EXCEPTION 'explicit customer confirmation is required before reducing a paid basket'; END IF;
 IF p_items IS NULL OR jsonb_typeof(p_items)<>'array' OR jsonb_array_length(p_items)=0 THEN RAISE EXCEPTION 'at least one accepted line is required'; END IF;
 PERFORM private.refresh_pos_split_payment_session(p_session_id);
 SELECT * INTO s FROM public.pos_split_payment_sessions WHERE id=p_session_id FOR UPDATE;
 IF NOT FOUND OR s.final_invoice_id IS NOT NULL OR s.status IN('settled','refund_pending','refunded','cancelled','finalizing') THEN RAISE EXCEPTION 'active unsettled split session required'; END IF;
 IF EXISTS(SELECT 1 FROM public.pos_split_payment_legs WHERE session_id=s.id AND status IN('pending','unknown')) THEN RAISE EXCEPTION 'reconcile the in-flight payment before changing the basket'; END IF;
 v_locked:=round(s.captured_amount+s.held_amount,2); IF v_locked<=0 THEN RAISE EXCEPTION 'no locked payment is available for reduced-basket settlement'; END IF;
 SELECT * INTO o FROM public.commerce_orders WHERE id=s.commerce_order_id FOR UPDATE;
 SELECT * INTO c FROM public.pos_carts WHERE id=o.cart_id FOR UPDATE;
 IF NOT FOUND OR c.created_by<>auth.uid() THEN RAISE EXCEPTION 'current operator cart required'; END IF;
 DELETE FROM public.pos_split_acceptance_lines WHERE session_id=s.id;
 FOR e IN SELECT * FROM jsonb_array_elements(p_items) LOOP
  v_id:=(e->>'cart_line_id')::uuid; v_qty:=round(COALESCE((e->>'qty')::numeric,0),3);
  SELECT * INTO l FROM public.pos_cart_lines WHERE id=v_id AND cart_id=c.id AND NOT is_core_charge FOR UPDATE;
  IF NOT FOUND OR v_qty<=0 OR v_qty>l.qty+0.0001 THEN RAISE EXCEPTION 'invalid accepted quantity for cart line %',v_id; END IF;
  IF EXISTS(SELECT 1 FROM public.pos_split_acceptance_lines WHERE session_id=s.id AND cart_line_id=l.id) THEN RAISE EXCEPTION 'duplicate accepted cart line %',l.id; END IF;
  v_fraction:=v_qty/l.qty;
  INSERT INTO public.pos_split_acceptance_lines(session_id,cart_line_id,original_qty,accepted_qty,accepted_line_total,auto_included_core,accepted_by)
  VALUES(s.id,l.id,l.qty,v_qty,round(l.line_total*v_fraction,2),false,auth.uid());
  v_total:=v_total+round(l.line_total*v_fraction,2);
  FOR r IN SELECT x.* FROM public.pos_cart_lines x WHERE x.parent_line_id=l.id AND x.is_core_charge ORDER BY x.id LOOP
   INSERT INTO public.pos_split_acceptance_lines(session_id,cart_line_id,original_qty,accepted_qty,accepted_line_total,auto_included_core,accepted_by)
   VALUES(s.id,r.id,r.qty,round(r.qty*v_fraction,3),round(r.line_total*v_fraction,2),true,auth.uid());
   v_total:=v_total+round(r.line_total*v_fraction,2);
  END LOOP;
 END LOOP;
 v_total:=round(v_total,2);
 IF v_total<=0 OR v_total>v_locked+0.01 THEN RAISE EXCEPTION 'accepted basket total % must be > 0 and <= locked payment %',v_total,v_locked; END IF;

 UPDATE public.pos_cart_lines x SET
  qty=a.accepted_qty,
  qty_base=round(x.qty_base*(a.accepted_qty/a.original_qty),3),
  line_total=a.accepted_line_total
 FROM public.pos_split_acceptance_lines a WHERE a.session_id=s.id AND a.cart_line_id=x.id;
 DELETE FROM public.pos_cart_lines x WHERE x.cart_id=c.id AND NOT EXISTS(
  SELECT 1 FROM public.pos_split_acceptance_lines a WHERE a.session_id=s.id AND a.cart_line_id=x.id);

 DELETE FROM public.inventory_reservations WHERE commerce_order_id=o.id AND state='active';
 FOR r IN SELECT stock_item_id,SUM(qty_base)::numeric required_qty FROM public.pos_cart_lines
  WHERE cart_id=c.id AND COALESCE(issues_stock,true)=true AND NOT is_core_charge GROUP BY stock_item_id ORDER BY stock_item_id LOOP
  v_available:=public.get_inventory_available_quantity(r.stock_item_id,c.warehouse_id);
  IF v_available<r.required_qty THEN RAISE EXCEPTION 'accepted basket stock became unavailable for item % (available %, required %)',r.stock_item_id,v_available,r.required_qty; END IF;
  INSERT INTO public.inventory_reservations(commerce_order_id,stock_item_id,warehouse_id,required_qty,reserved_qty,expires_at,state,reservation_reason)
  VALUES(o.id,r.stock_item_id,c.warehouse_id,r.required_qty,r.required_qty,greatest(COALESCE(o.reservation_expires_at,now()),now()+interval '60 minutes'),'active','pos_checkout_reduced_basket_hold');
 END LOOP;
 SELECT COALESCE(jsonb_agg(jsonb_build_object('line_id',x.id,'stock_item_id',x.stock_item_id,'uom_id',x.uom_id,'qty',x.qty,'qty_base',x.qty_base,
  'unit_price',x.unit_price,'line_total',x.line_total,'is_core_charge',x.is_core_charge) ORDER BY x.created_at),'[]'::jsonb)
 INTO v_lines FROM public.pos_cart_lines x WHERE x.cart_id=c.id;
 UPDATE public.commerce_orders SET subtotal=v_total,total=v_total,checkout_snapshot=jsonb_set(checkout_snapshot,'{lines}',v_lines,true),
  reservation_expires_at=greatest(COALESCE(reservation_expires_at,now()),now()+interval '60 minutes'),updated_at=now() WHERE id=o.id;
 UPDATE public.pos_split_payment_sessions SET total_amount=v_total,reduced_basket_accepted_at=now(),reduced_basket_accepted_by=auth.uid(),
  reduced_basket_customer_confirmed=true,reduced_basket_notes=p_notes,updated_at=now() WHERE id=s.id;

 v_remaining:=v_total;
 FOR leg IN SELECT * FROM public.pos_split_payment_legs WHERE session_id=s.id AND status='captured' ORDER BY sequence_no FOR UPDATE LOOP
  v_apply:=least(leg.requested_amount,greatest(v_remaining,0)); v_refund:=round(leg.requested_amount-v_apply,2); v_remaining:=round(v_remaining-v_apply,2);
  UPDATE public.pos_split_payment_legs SET applied_target_amount=v_apply,refund_required_amount=v_refund,updated_at=now() WHERE id=leg.id;
  IF v_refund>0.009 THEN
   INSERT INTO public.pos_split_refund_requests(session_id,leg_id,gross_amount,fee_policy,external_reference,notes,created_by)
   VALUES(s.id,leg.id,v_refund,'manual_review','GTR-SPLIT-SURPLUS-'||leg.id::text,
    concat_ws(' · ','Captured surplus after customer accepted reduced basket',p_notes),auth.uid()) ON CONFLICT(leg_id) DO UPDATE SET
    gross_amount=EXCLUDED.gross_amount,notes=EXCLUDED.notes,updated_at=now() WHERE public.pos_split_refund_requests.status IN('review','failed');
  END IF;
 END LOOP;
 FOR leg IN SELECT * FROM public.pos_split_payment_legs WHERE session_id=s.id AND status='held' ORDER BY sequence_no FOR UPDATE LOOP
  v_apply:=least(leg.requested_amount,greatest(v_remaining,0)); v_remaining:=round(v_remaining-v_apply,2);
  IF v_apply<=0.009 THEN UPDATE public.pos_split_payment_legs SET status='cancelled',applied_target_amount=0,updated_at=now() WHERE id=leg.id;
  ELSE UPDATE public.pos_split_payment_legs SET metadata=CASE WHEN v_apply<requested_amount-0.009 THEN metadata||jsonb_build_object('original_held_amount',requested_amount) ELSE metadata END,
    requested_amount=v_apply,applied_target_amount=v_apply,refund_required_amount=0,updated_at=now() WHERE id=leg.id; END IF;
 END LOOP;
 IF v_remaining>0.01 THEN RAISE EXCEPTION 'accepted basket requires % more than locked captured/held funds',v_remaining; END IF;
 UPDATE public.pos_split_payment_legs SET status='cancelled',updated_at=now() WHERE session_id=s.id AND status='planned';
 PERFORM private.refresh_pos_split_payment_session(s.id);
 RETURN private.finalize_pos_split_payment_session(s.id);
END $$;

REVOKE ALL ON FUNCTION public.accept_pos_split_affordable_items(UUID,JSONB,BOOLEAN,TEXT) FROM PUBLIC,anon;

GRANT EXECUTE ON FUNCTION public.accept_pos_split_affordable_items(UUID,JSONB,BOOLEAN,TEXT) TO authenticated,service_role;

CREATE OR REPLACE FUNCTION public.complete_pos_split_refund(
 p_refund_id UUID,p_provider_ref TEXT,p_actual_provider_fee NUMERIC DEFAULT 0,p_actual_transfer_fee NUMERIC DEFAULT 0,
 p_actual_customer_fee NUMERIC DEFAULT 0,p_notes TEXT DEFAULT NULL)
RETURNS JSONB LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
DECLARE r public.pos_split_refund_requests%ROWTYPE; l public.pos_split_payment_legs%ROWTYPE; pe public.payment_entries%ROWTYPE;
 s public.pos_split_payment_sessions%ROWTYPE; v_fee NUMERIC; v_customer_fee NUMERIC; v_net NUMERIC; v_acct TEXT; v_journal UUID; v_remaining INTEGER; v_allocated NUMERIC:=0;
BEGIN
 IF NOT (auth.role()='service_role' OR public.is_pos_approver() OR public.has_staff_role(ARRAY['finance']::public.staff_role[])) THEN RAISE EXCEPTION 'manager, finance, or service role required'; END IF;
 SELECT * INTO r FROM public.pos_split_refund_requests WHERE id=p_refund_id FOR UPDATE; IF NOT FOUND THEN RAISE EXCEPTION 'split refund request not found'; END IF;
 IF r.status='settled' THEN RETURN private.pos_split_payment_payload(r.session_id); END IF;
 IF r.status NOT IN('pending','review','failed') THEN RAISE EXCEPTION 'refund cannot settle in status %',r.status; END IF;
 SELECT * INTO l FROM public.pos_split_payment_legs WHERE id=r.leg_id FOR UPDATE; SELECT * INTO pe FROM public.payment_entries WHERE id=l.payment_entry_id FOR UPDATE;
 SELECT * INTO s FROM public.pos_split_payment_sessions WHERE id=r.session_id FOR UPDATE;
 IF pe.status<>'posted' THEN RAISE EXCEPTION 'posted split receipt required'; END IF;
 SELECT COALESCE(SUM(amount),0) INTO v_allocated FROM public.payment_allocations WHERE payment_entry_id=pe.id;
 IF v_allocated+r.gross_amount>pe.amount+0.01 THEN RAISE EXCEPTION 'refund plus applied amount exceeds captured receipt'; END IF;
 v_fee:=round(COALESCE(p_actual_provider_fee,0)+COALESCE(p_actual_transfer_fee,0),2); v_customer_fee:=round(COALESCE(p_actual_customer_fee,0),2);
 IF v_fee<0 OR v_customer_fee<0 OR v_customer_fee>r.gross_amount THEN RAISE EXCEPTION 'invalid actual refund fees'; END IF;
 IF v_customer_fee>0 AND r.fee_policy<>'customer_bears' THEN RAISE EXCEPTION 'customer fee deduction not approved by refund policy'; END IF;
 v_net:=round(r.gross_amount-v_customer_fee,2); v_acct:=public.gl_account_for_payment_tender(l.tender);
 PERFORM public._payments_rpc_enter();
 v_journal:=public.post_journal_entry(CURRENT_DATE,format('POS split refund %s',r.id),s.currency,1,
  jsonb_build_array(jsonb_build_object('account_code','2215','debit',r.gross_amount,'credit',0,'currency',s.currency),
   jsonb_build_object('account_code','6250','debit',v_fee,'credit',0,'currency',s.currency),
   jsonb_build_object('account_code',v_acct,'debit',0,'credit',v_net+v_fee,'currency',s.currency),
   jsonb_build_object('account_code','4250','debit',0,'credit',v_customer_fee,'currency',s.currency)));
 IF v_allocated<=0.001 AND abs(r.gross_amount-pe.amount)<=0.01 THEN
  UPDATE public.payment_entries SET status='cancelled',reversal_journal_entry_id=v_journal,cancelled_at=now(),updated_at=now() WHERE id=pe.id;
 END IF;
 UPDATE public.pos_split_refund_requests SET status='settled',actual_provider_fee=COALESCE(p_actual_provider_fee,0),actual_transfer_fee=COALESCE(p_actual_transfer_fee,0),
  actual_customer_fee=v_customer_fee,net_customer_refund=v_net,provider_ref=NULLIF(trim(COALESCE(p_provider_ref,'')),''),settled_by=auth.uid(),settled_at=now(),
  notes=concat_ws(E'\n',notes,p_notes),failure_reason=NULL,updated_at=now() WHERE id=r.id;
 UPDATE public.pos_split_payment_legs SET status=CASE WHEN v_allocated>0.001 THEN 'allocated'::public.pos_split_payment_leg_status ELSE 'refunded'::public.pos_split_payment_leg_status END,
  provider_ref=COALESCE(NULLIF(trim(COALESCE(p_provider_ref,'')),''),provider_ref),refunded_at=now(),updated_at=now() WHERE id=l.id;
 SELECT count(*) INTO v_remaining FROM public.pos_split_refund_requests WHERE session_id=s.id AND status NOT IN('settled','cancelled');
 IF s.final_invoice_id IS NOT NULL THEN
  UPDATE public.pos_split_payment_sessions SET status=CASE WHEN v_remaining=0 THEN 'settled'::public.pos_split_payment_session_status ELSE 'refund_pending'::public.pos_split_payment_session_status END,
   refunded_amount=(SELECT COALESCE(SUM(gross_amount) FILTER(WHERE status='settled'),0) FROM public.pos_split_refund_requests WHERE session_id=s.id),updated_at=now() WHERE id=s.id;
 ELSIF v_remaining=0 THEN
  UPDATE public.pos_split_payment_sessions SET status='refunded',refunded_amount=(SELECT COALESCE(SUM(gross_amount),0) FROM public.pos_split_refund_requests WHERE session_id=s.id),updated_at=now() WHERE id=s.id;
 ELSE UPDATE public.pos_split_payment_sessions SET status='refund_pending',updated_at=now() WHERE id=s.id; END IF;
 PERFORM public.emit_domain_event('refund_issued','pos-split:refund:'||r.id::text,jsonb_build_object('split_refund_id',r.id,'leg_id',l.id,'gross_amount',r.gross_amount,
  'net_customer_refund',v_net,'provider_fee',COALESCE(p_actual_provider_fee,0),'transfer_fee',COALESCE(p_actual_transfer_fee,0),'customer_fee',v_customer_fee,'tender',l.tender,'provider_ref',p_provider_ref),
  auth.uid(),format('GTR Auto: split refund settled %s %s net',v_net,s.currency));
 RETURN private.pos_split_payment_payload(s.id);
END $$;

REVOKE ALL ON FUNCTION public.complete_pos_split_refund(UUID,TEXT,NUMERIC,NUMERIC,NUMERIC,TEXT) FROM PUBLIC,anon;

GRANT EXECUTE ON FUNCTION public.complete_pos_split_refund(UUID,TEXT,NUMERIC,NUMERIC,NUMERIC,TEXT) TO authenticated,service_role;

CREATE OR REPLACE FUNCTION public.pos_till_expected_cash(p_session_id UUID)
RETURNS NUMERIC LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path='' AS $$
DECLARE s public.pos_till_sessions%ROWTYPE; v_cash NUMERIC:=0; v_split_cash NUMERIC:=0; v_direct NUMERIC:=0; v_in NUMERIC:=0; v_out NUMERIC:=0;
BEGIN
 PERFORM public._require_sales_staff(); SELECT * INTO s FROM public.pos_till_sessions WHERE id=p_session_id;
 IF NOT FOUND THEN RAISE EXCEPTION 'till session not found'; END IF;
 IF s.operator_user_id<>auth.uid() AND NOT public.is_pos_approver() AND NOT public.has_staff_role(ARRAY['finance']::public.staff_role[]) THEN RAISE EXCEPTION 'till session access denied'; END IF;
 SELECT COALESCE(SUM(a.amount),0) INTO v_cash FROM public.payment_allocations a JOIN public.payment_entries p ON p.id=a.payment_entry_id JOIN public.sales_invoices i ON i.id=a.sales_invoice_id
 WHERE i.till_session_id=p_session_id AND p.status='posted' AND p.tender='cash';
 SELECT COALESCE(SUM(greatest(l.requested_amount-COALESCE((SELECT SUM(a.amount) FROM public.payment_allocations a WHERE a.payment_entry_id=l.payment_entry_id),0),0)),0)
 INTO v_split_cash FROM public.pos_split_payment_legs l JOIN public.pos_split_payment_sessions ps ON ps.id=l.session_id JOIN public.commerce_orders o ON o.id=ps.commerce_order_id
 JOIN public.pos_carts c ON c.id=o.cart_id WHERE c.till_session_id=p_session_id AND l.tender='cash' AND l.status IN('captured','refund_review','refund_pending');
 SELECT COALESCE(SUM(i.total),0) INTO v_direct FROM public.sales_invoices i WHERE i.till_session_id=p_session_id AND i.status='posted' AND i.doc_type='invoice' AND i.customer_id IS NULL
  AND NOT EXISTS(SELECT 1 FROM public.payment_allocations a WHERE a.sales_invoice_id=i.id);
 SELECT COALESCE(SUM(amount),0) INTO v_in FROM public.pos_till_cash_movements WHERE session_id=p_session_id AND kind='cash_in';
 SELECT COALESCE(SUM(amount),0) INTO v_out FROM public.pos_till_cash_movements WHERE session_id=p_session_id AND kind IN('cash_out','petty_cash','bank_drop','cash_refund');
 RETURN round(s.opening_float+v_cash+v_split_cash+v_direct+v_in-v_out,2);
END $$;

REVOKE ALL ON FUNCTION public.pos_till_expected_cash(UUID) FROM PUBLIC,anon;

GRANT EXECUTE ON FUNCTION public.pos_till_expected_cash(UUID) TO authenticated,service_role;
