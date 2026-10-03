-- Exported from the hosted project's supabase_migrations.schema_migrations (20260908143000 pos_staged_split_payments).
-- Source of record for what production ran; see supabase/live-history/README.md.

-- Staged split payments for reserve-first POS, including physical card terminals.
-- Captured legs post to POS Split Payment Suspense until the order is fully funded.
-- This prevents double-charge/retry hazards and prevents captured funds becoming spendable store credit.

INSERT INTO public.chart_of_accounts(code,name,display_name,account_type) VALUES
 ('2215','POS Split Payment Suspense','POS split payment suspense','liability'),
 ('6250','Payment and Refund Fees','Payment and refund fees','expense'),
 ('4250','Customer Payment Fee Recovery','Customer payment fee recovery','income')
ON CONFLICT(code) DO UPDATE SET name=EXCLUDED.name,display_name=EXCLUDED.display_name,is_active=true;

ALTER TABLE public.commerce_orders DROP CONSTRAINT IF EXISTS commerce_orders_settled_provider_check;

ALTER TABLE public.commerce_orders ADD CONSTRAINT commerce_orders_settled_provider_check
 CHECK (settled_provider IS NULL OR settled_provider IN
 ('contipay','paynow','ecocash','cash','bank','store_credit','manual_split','card_terminal','split_payment'));

DO $$ BEGIN
 IF NOT EXISTS(SELECT 1 FROM pg_type WHERE typname='pos_split_payment_session_status') THEN
  CREATE TYPE public.pos_split_payment_session_status AS ENUM(
   'open','partially_captured','leg_pending','fully_committed','finalizing','settled',
   'finalization_failed','refund_review','refund_pending','refunded','cancelled'
  );
 END IF;
 IF NOT EXISTS(SELECT 1 FROM pg_type WHERE typname='pos_split_payment_leg_status') THEN
  CREATE TYPE public.pos_split_payment_leg_status AS ENUM(
   'planned','held','pending','captured','failed','unknown','allocated',
   'refund_review','refund_pending','refunded','cancelled'
  );
 END IF;
 IF NOT EXISTS(SELECT 1 FROM pg_type WHERE typname='pos_split_refund_status') THEN
  CREATE TYPE public.pos_split_refund_status AS ENUM('review','pending','settled','failed','cancelled');
 END IF;
END $$;

CREATE TABLE IF NOT EXISTS public.pos_split_payment_sessions(
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
 commerce_order_id UUID NOT NULL UNIQUE REFERENCES public.commerce_orders(id) ON DELETE RESTRICT,
 status public.pos_split_payment_session_status NOT NULL DEFAULT 'open',
 total_amount NUMERIC(18,2) NOT NULL CHECK(total_amount>=0),
 currency public.currency_code NOT NULL,
 captured_amount NUMERIC(18,2) NOT NULL DEFAULT 0 CHECK(captured_amount>=0),
 held_amount NUMERIC(18,2) NOT NULL DEFAULT 0 CHECK(held_amount>=0),
 pending_amount NUMERIC(18,2) NOT NULL DEFAULT 0 CHECK(pending_amount>=0),
 refunded_amount NUMERIC(18,2) NOT NULL DEFAULT 0 CHECK(refunded_amount>=0),
 final_invoice_id UUID REFERENCES public.sales_invoices(id) ON DELETE RESTRICT,
 finalization_error TEXT,
 created_by UUID NOT NULL REFERENCES auth.users(id),
 updated_by UUID REFERENCES auth.users(id),
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 settled_at TIMESTAMPTZ,
 cancelled_at TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS public.pos_split_payment_legs(
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
 session_id UUID NOT NULL REFERENCES public.pos_split_payment_sessions(id) ON DELETE RESTRICT,
 request_id UUID NOT NULL UNIQUE,
 sequence_no INTEGER NOT NULL CHECK(sequence_no>0),
 tender public.payment_tender NOT NULL,
 requested_amount NUMERIC(18,2) NOT NULL CHECK(requested_amount>0),
 status public.pos_split_payment_leg_status NOT NULL DEFAULT 'planned',
 external_reference TEXT,
 provider_intent_id UUID,
 provider_ref TEXT,
 card_terminal_attempt_id UUID,
 payment_entry_id UUID REFERENCES public.payment_entries(id) ON DELETE RESTRICT,
 allocation_journal_entry_id UUID REFERENCES public.journal_entries(id) ON DELETE RESTRICT,
 status_detail TEXT,
 metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
 created_by UUID NOT NULL REFERENCES auth.users(id),
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 locked_at TIMESTAMPTZ,
 allocated_at TIMESTAMPTZ,
 failed_at TIMESTAMPTZ,
 refunded_at TIMESTAMPTZ,
 UNIQUE(session_id,sequence_no)
);

CREATE UNIQUE INDEX IF NOT EXISTS pos_split_payment_leg_provider_intent_uidx
 ON public.pos_split_payment_legs(tender,provider_intent_id) WHERE provider_intent_id IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS pos_split_payment_leg_terminal_attempt_uidx
 ON public.pos_split_payment_legs(card_terminal_attempt_id) WHERE card_terminal_attempt_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS pos_split_payment_legs_session_idx ON public.pos_split_payment_legs(session_id,sequence_no);

CREATE INDEX IF NOT EXISTS pos_split_payment_legs_payment_entry_idx ON public.pos_split_payment_legs(payment_entry_id);

CREATE TABLE IF NOT EXISTS public.pos_split_refund_requests(
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
 session_id UUID NOT NULL REFERENCES public.pos_split_payment_sessions(id) ON DELETE RESTRICT,
 leg_id UUID NOT NULL UNIQUE REFERENCES public.pos_split_payment_legs(id) ON DELETE RESTRICT,
 status public.pos_split_refund_status NOT NULL DEFAULT 'review',
 gross_amount NUMERIC(18,2) NOT NULL CHECK(gross_amount>0),
 fee_policy TEXT NOT NULL DEFAULT 'manual_review' CHECK(fee_policy IN('business_absorbs','customer_bears','manual_review')),
 estimated_provider_fee NUMERIC(18,2) NOT NULL DEFAULT 0 CHECK(estimated_provider_fee>=0),
 estimated_transfer_fee NUMERIC(18,2) NOT NULL DEFAULT 0 CHECK(estimated_transfer_fee>=0),
 approved_customer_fee NUMERIC(18,2) NOT NULL DEFAULT 0 CHECK(approved_customer_fee>=0),
 actual_provider_fee NUMERIC(18,2) NOT NULL DEFAULT 0 CHECK(actual_provider_fee>=0),
 actual_transfer_fee NUMERIC(18,2) NOT NULL DEFAULT 0 CHECK(actual_transfer_fee>=0),
 actual_customer_fee NUMERIC(18,2) NOT NULL DEFAULT 0 CHECK(actual_customer_fee>=0),
 net_customer_refund NUMERIC(18,2),
 external_reference TEXT,
 provider_ref TEXT,
 expected_settlement_at TIMESTAMPTZ,
 approved_by UUID REFERENCES auth.users(id),
 approved_at TIMESTAMPTZ,
 settled_by UUID REFERENCES auth.users(id),
 settled_at TIMESTAMPTZ,
 failure_reason TEXT,
 notes TEXT,
 created_by UUID NOT NULL REFERENCES auth.users(id),
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS pos_split_refunds_session_idx ON public.pos_split_refund_requests(session_id,status,created_at);

ALTER TABLE public.pos_split_payment_sessions ENABLE ROW LEVEL SECURITY;

ALTER TABLE public.pos_split_payment_legs ENABLE ROW LEVEL SECURITY;

ALTER TABLE public.pos_split_refund_requests ENABLE ROW LEVEL SECURITY;

REVOKE ALL ON public.pos_split_payment_sessions,public.pos_split_payment_legs,public.pos_split_refund_requests FROM PUBLIC,anon,authenticated;

GRANT ALL ON public.pos_split_payment_sessions,public.pos_split_payment_legs,public.pos_split_refund_requests TO service_role;

CREATE OR REPLACE FUNCTION private.pos_split_payment_payload(p_session_id UUID)
RETURNS JSONB LANGUAGE sql STABLE SECURITY DEFINER SET search_path='' AS $$
 SELECT jsonb_build_object(
  'session_id',s.id,'order_id',s.commerce_order_id,'status',s.status,'total',s.total_amount,'currency',s.currency,
  'captured_amount',s.captured_amount,'held_amount',s.held_amount,'pending_amount',s.pending_amount,'refunded_amount',s.refunded_amount,
  'locked_amount',round(s.captured_amount+s.held_amount,2),
  'balance_due',greatest(round(s.total_amount-s.captured_amount-s.held_amount,2),0),
  'available_to_allocate',greatest(round(s.total_amount-s.captured_amount-s.held_amount-s.pending_amount,2),0),
  'final_invoice_id',s.final_invoice_id,'finalization_error',s.finalization_error,
  'created_at',s.created_at,'updated_at',s.updated_at,
  'legs',COALESCE((SELECT jsonb_agg(jsonb_build_object(
    'id',l.id,'request_id',l.request_id,'sequence_no',l.sequence_no,'tender',l.tender,'amount',l.requested_amount,'status',l.status,
    'external_reference',l.external_reference,'provider_intent_id',l.provider_intent_id,'provider_ref',l.provider_ref,
    'card_terminal_attempt_id',l.card_terminal_attempt_id,'payment_entry_id',l.payment_entry_id,'status_detail',l.status_detail,
    'created_at',l.created_at,'locked_at',l.locked_at,'failed_at',l.failed_at,'refunded_at',l.refunded_at
   ) ORDER BY l.sequence_no) FROM public.pos_split_payment_legs l WHERE l.session_id=s.id),'[]'::jsonb),
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

CREATE OR REPLACE FUNCTION private.refresh_pos_split_payment_session(p_session_id UUID)
RETURNS JSONB LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
DECLARE s public.pos_split_payment_sessions%ROWTYPE; v_cap NUMERIC:=0; v_hold NUMERIC:=0; v_pending NUMERIC:=0; v_refunded NUMERIC:=0;
 v_status public.pos_split_payment_session_status; v_unknown BOOLEAN:=false; v_active UUID; v_provider TEXT;
BEGIN
 SELECT * INTO s FROM public.pos_split_payment_sessions WHERE id=p_session_id FOR UPDATE;
 IF NOT FOUND THEN RAISE EXCEPTION 'split payment session not found'; END IF;
 SELECT COALESCE(SUM(requested_amount) FILTER(WHERE status IN('captured','allocated','refund_review','refund_pending')),0),
        COALESCE(SUM(requested_amount) FILTER(WHERE status='held'),0),
        COALESCE(SUM(requested_amount) FILTER(WHERE status IN('pending','unknown')),0),
        COALESCE(SUM(requested_amount) FILTER(WHERE status='refunded'),0),
        COALESCE(bool_or(status='unknown'),false)
 INTO v_cap,v_hold,v_pending,v_refunded,v_unknown FROM public.pos_split_payment_legs WHERE session_id=s.id;
 IF s.status IN('settled','refunded','cancelled') THEN v_status:=s.status;
 ELSIF s.status IN('refund_review','refund_pending') THEN v_status:=s.status;
 ELSIF v_pending>0 THEN v_status:='leg_pending';
 ELSIF v_cap+v_hold+0.01>=s.total_amount THEN v_status:='fully_committed';
 ELSIF v_cap+v_hold>0 THEN v_status:='partially_captured'; ELSE v_status:='open'; END IF;
 UPDATE public.pos_split_payment_sessions SET status=v_status,captured_amount=round(v_cap,2),held_amount=round(v_hold,2),
  pending_amount=round(v_pending,2),refunded_amount=round(v_refunded,2),updated_at=now(),updated_by=auth.uid() WHERE id=s.id;
 SELECT l.provider_intent_id,l.tender::text INTO v_active,v_provider FROM public.pos_split_payment_legs l
  WHERE l.session_id=s.id AND l.status IN('pending','unknown') ORDER BY l.sequence_no LIMIT 1;
 IF v_status NOT IN('settled','refunded','cancelled','refund_review','refund_pending') THEN
  UPDATE public.commerce_orders SET
   state=CASE WHEN v_cap+v_hold+v_pending>0 THEN 'payment_processing'::public.commerce_order_state ELSE 'awaiting_payment'::public.commerce_order_state END,
   active_payment_provider=CASE WHEN v_active IS NOT NULL THEN v_provider ELSE NULL END,
   active_payment_intent_id=v_active,
   payment_exception=CASE
    WHEN v_unknown THEN 'SPLIT_PAYMENT_OUTCOME_UNKNOWN'
    WHEN v_pending=0 AND v_cap+v_hold>0 AND v_cap+v_hold+0.01<s.total_amount THEN 'SPLIT_BALANCE_DUE'
    ELSE NULL END,
   reservation_expires_at=CASE WHEN v_cap+v_hold+v_pending>0 THEN greatest(COALESCE(reservation_expires_at,now()),now()+interval '60 minutes') ELSE reservation_expires_at END,
   updated_at=now() WHERE id=s.commerce_order_id AND settled_payment_entry_id IS NULL;
  IF v_cap+v_hold+v_pending>0 THEN
   UPDATE public.inventory_reservations SET expires_at=greatest(COALESCE(expires_at,now()),now()+interval '60 minutes')
    WHERE commerce_order_id=s.commerce_order_id AND state='active';
  END IF;
 END IF;
 RETURN private.pos_split_payment_payload(s.id);
END $$;

REVOKE ALL ON FUNCTION private.refresh_pos_split_payment_session(UUID) FROM PUBLIC,anon,authenticated;

CREATE OR REPLACE FUNCTION private.post_pos_split_unapplied_payment_entry(p_payment_entry_id UUID,p_leg_id UUID)
RETURNS UUID LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
DECLARE pe public.payment_entries%ROWTYPE; l public.pos_split_payment_legs%ROWTYPE; v_alloc NUMERIC; v_acct TEXT; v_journal UUID;
BEGIN
 PERFORM public._payments_rpc_enter();
 SELECT * INTO l FROM public.pos_split_payment_legs WHERE id=p_leg_id FOR UPDATE;
 SELECT * INTO pe FROM public.payment_entries WHERE id=p_payment_entry_id FOR UPDATE;
 IF NOT FOUND THEN RAISE EXCEPTION 'payment entry not found'; END IF;
 IF pe.status='posted' THEN RETURN pe.id; END IF;
 IF pe.status<>'draft' OR pe.tender='store_credit' THEN RAISE EXCEPTION 'cash/provider draft payment entry required'; END IF;
 IF l.id IS NULL OR l.requested_amount<>pe.amount OR l.tender<>pe.tender THEN RAISE EXCEPTION 'split leg/payment mismatch'; END IF;
 SELECT COALESCE(SUM(amount),0) INTO v_alloc FROM public.payment_allocations WHERE payment_entry_id=pe.id;
 IF v_alloc>0.001 THEN RAISE EXCEPTION 'split suspense receipt cannot already be allocated'; END IF;
 v_acct:=public.gl_account_for_payment_tender(pe.tender);
 v_journal:=public.post_journal_entry(CURRENT_DATE,format('POS split receipt %s',COALESCE(pe.document_number,pe.id::text)),pe.currency,pe.exchange_rate_applied,
  jsonb_build_array(jsonb_build_object('account_code',v_acct,'debit',pe.amount,'credit',0,'currency',pe.currency),
                    jsonb_build_object('account_code','2215','debit',0,'credit',pe.amount,'currency',pe.currency)));
 UPDATE public.payment_entries SET status='posted',journal_entry_id=v_journal,store_credit_issued=0,posted_by=auth.uid(),posted_at=now(),updated_at=now() WHERE id=pe.id;
 PERFORM public.emit_domain_event('payment_partial','pos-split:capture:'||l.id::text,
  jsonb_build_object('split_leg_id',l.id,'payment_entry_id',pe.id,'amount',pe.amount,'currency',pe.currency,'tender',pe.tender,'suspense_account','2215'),
  auth.uid(),format('GTR Auto: split payment locked %s %s via %s',pe.amount,pe.currency,pe.tender));
 RETURN pe.id;
END $$;

REVOKE ALL ON FUNCTION private.post_pos_split_unapplied_payment_entry(UUID,UUID) FROM PUBLIC,anon,authenticated;

CREATE OR REPLACE FUNCTION private.allocate_pos_split_receipt_to_invoice(p_leg_id UUID,p_invoice_id UUID)
RETURNS UUID LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
DECLARE l public.pos_split_payment_legs%ROWTYPE; pe public.payment_entries%ROWTYPE; i public.sales_invoices%ROWTYPE; v_open NUMERIC; v_journal UUID;
BEGIN
 PERFORM public._payments_rpc_enter();
 SELECT * INTO l FROM public.pos_split_payment_legs WHERE id=p_leg_id FOR UPDATE;
 IF NOT FOUND OR l.status<>'captured' OR l.payment_entry_id IS NULL THEN RAISE EXCEPTION 'captured split leg required'; END IF;
 SELECT * INTO pe FROM public.payment_entries WHERE id=l.payment_entry_id FOR UPDATE;
 SELECT * INTO i FROM public.sales_invoices WHERE id=p_invoice_id FOR UPDATE;
 IF pe.status<>'posted' OR i.status<>'posted' OR i.doc_type<>'invoice' OR pe.customer_id IS DISTINCT FROM i.customer_id OR pe.currency<>i.currency THEN
  RAISE EXCEPTION 'split receipt/invoice mismatch'; END IF;
 IF EXISTS(SELECT 1 FROM public.payment_allocations WHERE payment_entry_id=pe.id) THEN RAISE EXCEPTION 'split receipt already allocated'; END IF;
 v_open:=round(i.total-i.amount_paid,2); IF pe.amount>v_open+0.01 THEN RAISE EXCEPTION 'split receipt exceeds invoice open balance'; END IF;
 v_journal:=public.post_journal_entry(CURRENT_DATE,format('Apply POS split receipt %s to %s',pe.document_number,i.document_number),pe.currency,pe.exchange_rate_applied,
  jsonb_build_array(jsonb_build_object('account_code','2215','debit',pe.amount,'credit',0,'currency',pe.currency),
                    jsonb_build_object('account_code','1200','debit',0,'credit',pe.amount,'currency',pe.currency)));
 INSERT INTO public.payment_allocations(payment_entry_id,sales_invoice_id,amount,currency,exchange_rate_applied)
 VALUES(pe.id,i.id,pe.amount,pe.currency,pe.exchange_rate_applied);
 UPDATE public.sales_invoices SET amount_paid=amount_paid+pe.amount WHERE id=i.id;
 UPDATE public.customers SET open_balance=greatest(0,open_balance-pe.amount),updated_at=now() WHERE id=pe.customer_id;
 UPDATE public.pos_split_payment_legs SET status='allocated',allocation_journal_entry_id=v_journal,allocated_at=now(),updated_at=now() WHERE id=l.id;
 RETURN pe.id;
END $$;

REVOKE ALL ON FUNCTION private.allocate_pos_split_receipt_to_invoice(UUID,UUID) FROM PUBLIC,anon,authenticated;

CREATE OR REPLACE FUNCTION private.enforce_pos_split_store_credit_holds()
RETURNS trigger LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
DECLARE v_held NUMERIC:=0;
BEGIN
 IF NEW.balance<OLD.balance THEN
  SELECT COALESCE(SUM(l.requested_amount),0) INTO v_held
  FROM public.pos_split_payment_legs l JOIN public.pos_split_payment_sessions s ON s.id=l.session_id
  WHERE s.status NOT IN('settled','refunded','cancelled') AND l.status='held'
    AND s.currency=NEW.currency AND EXISTS(SELECT 1 FROM public.commerce_orders o WHERE o.id=s.commerce_order_id AND o.customer_id=NEW.customer_id);
  IF NEW.balance+0.001<v_held THEN RAISE EXCEPTION 'store credit is held for an active POS split payment; available balance would be exceeded'; END IF;
 END IF;
 RETURN NEW;
END $$;

DROP TRIGGER IF EXISTS store_credit_split_hold_guard ON public.store_credit_accounts;

CREATE TRIGGER store_credit_split_hold_guard BEFORE UPDATE OF balance ON public.store_credit_accounts
 FOR EACH ROW EXECUTE FUNCTION private.enforce_pos_split_store_credit_holds();

REVOKE ALL ON FUNCTION private.enforce_pos_split_store_credit_holds() FROM PUBLIC,anon,authenticated;

CREATE OR REPLACE FUNCTION private.finalize_pos_split_payment_session(p_session_id UUID)
RETURNS JSONB LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
DECLARE s public.pos_split_payment_sessions%ROWTYPE; o public.commerce_orders%ROWTYPE; l public.pos_split_payment_legs%ROWTYPE;
 v_inv UUID; v_pe UUID; v_first UUID; v_sum NUMERIC; v_count INTEGER; v_error TEXT;
BEGIN
 PERFORM private.refresh_pos_split_payment_session(p_session_id);
 SELECT * INTO s FROM public.pos_split_payment_sessions WHERE id=p_session_id FOR UPDATE;
 SELECT * INTO o FROM public.commerce_orders WHERE id=s.commerce_order_id FOR UPDATE;
 IF s.status='settled' THEN RETURN private.pos_split_payment_payload(s.id); END IF;
 IF s.pending_amount>0 OR s.captured_amount+s.held_amount+0.01<s.total_amount THEN RETURN private.pos_split_payment_payload(s.id); END IF;
 UPDATE public.pos_split_payment_sessions SET status='finalizing',finalization_error=NULL,updated_at=now() WHERE id=s.id;
 BEGIN
  v_inv:=private.finalize_commerce_order(o.id);
  FOR l IN SELECT * FROM public.pos_split_payment_legs WHERE session_id=s.id AND status IN('captured','held') ORDER BY sequence_no FOR UPDATE LOOP
   IF l.status='captured' THEN
    v_pe:=private.allocate_pos_split_receipt_to_invoice(l.id,v_inv);
   ELSE
    UPDATE public.pos_split_payment_legs SET status='planned',updated_at=now() WHERE id=l.id;
    v_pe:=public.create_payment_entry(o.customer_id,'store_credit',l.requested_amount,o.currency,o.exchange_rate_applied,
      format('POS split store credit %s',s.id));
    PERFORM public.allocate_payment(v_pe,jsonb_build_array(jsonb_build_object('sales_invoice_id',v_inv,'amount',l.requested_amount)));
    PERFORM public.post_payment_entry(v_pe);
    UPDATE public.pos_split_payment_legs SET status='allocated',payment_entry_id=v_pe,allocated_at=now(),locked_at=COALESCE(locked_at,now()),updated_at=now() WHERE id=l.id;
   END IF;
   IF v_first IS NULL THEN v_first:=v_pe; END IF;
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
 SELECT count(*) INTO v_count FROM public.pos_split_payment_legs WHERE session_id=s.id AND status='allocated';
 UPDATE public.commerce_orders SET settled_payment_entry_id=v_first,settled_provider=CASE WHEN v_count=1 THEN
   (SELECT tender::text FROM public.pos_split_payment_legs WHERE session_id=s.id AND status='allocated' ORDER BY sequence_no LIMIT 1)
  ELSE 'split_payment' END,
  settled_provider_ref='POS-SPLIT-'||s.id::text,payment_exception=NULL,active_payment_provider=NULL,active_payment_intent_id=NULL,
  state=CASE WHEN fulfillment_mode='dispatch' THEN 'allocation_pending'::public.commerce_order_state ELSE 'paid'::public.commerce_order_state END,
  updated_at=now() WHERE id=o.id;
 UPDATE public.pos_split_payment_sessions SET status='settled',final_invoice_id=v_inv,settled_at=now(),finalization_error=NULL,
  captured_amount=s.total_amount,held_amount=0,pending_amount=0,updated_at=now() WHERE id=s.id;
 PERFORM private.enqueue_commerce_event('commerce:'||o.id::text||':split-settled:'||s.id::text,'commerce.payment_settled',o.id,
  jsonb_build_object('provider','split_payment','split_session_id',s.id,'sales_invoice_id',v_inv));
 RETURN private.pos_split_payment_payload(s.id);
END $$;

REVOKE ALL ON FUNCTION private.finalize_pos_split_payment_session(UUID) FROM PUBLIC,anon,authenticated;

CREATE OR REPLACE FUNCTION public.start_pos_split_payment(p_order_id UUID)
RETURNS JSONB LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
DECLARE o public.commerce_orders%ROWTYPE; c public.pos_carts%ROWTYPE; s public.pos_split_payment_sessions%ROWTYPE; t public.pos_till_sessions%ROWTYPE;
BEGIN
 PERFORM public._require_payments_staff();
 SELECT * INTO o FROM public.commerce_orders WHERE id=p_order_id FOR UPDATE;
 IF NOT FOUND OR o.sales_invoice_id IS NOT NULL OR o.settled_payment_entry_id IS NOT NULL OR o.state NOT IN('awaiting_payment','payment_processing','payment_failed') THEN
  RAISE EXCEPTION 'unsettled reserve-first commerce order required'; END IF;
 SELECT * INTO c FROM public.pos_carts WHERE id=o.cart_id;
 IF NOT FOUND OR c.created_by<>auth.uid() OR c.till_session_id IS NULL THEN RAISE EXCEPTION 'current operator cart and till required'; END IF;
 SELECT * INTO t FROM public.pos_till_sessions WHERE id=c.till_session_id;
 IF NOT FOUND OR t.status<>'open' OR t.operator_user_id<>auth.uid() THEN RAISE EXCEPTION 'active operator till required'; END IF;
 SELECT * INTO s FROM public.pos_split_payment_sessions WHERE commerce_order_id=o.id;
 IF NOT FOUND THEN
  INSERT INTO public.pos_split_payment_sessions(commerce_order_id,total_amount,currency,created_by,updated_by)
  VALUES(o.id,o.total,o.currency,auth.uid(),auth.uid()) RETURNING * INTO s;
 END IF;
 UPDATE public.commerce_orders SET state='payment_processing',reservation_expires_at=greatest(COALESCE(reservation_expires_at,now()),now()+interval '60 minutes'),updated_at=now() WHERE id=o.id;
 UPDATE public.inventory_reservations SET expires_at=greatest(COALESCE(expires_at,now()),now()+interval '60 minutes') WHERE commerce_order_id=o.id AND state='active';
 RETURN private.refresh_pos_split_payment_session(s.id);
END $$;

CREATE OR REPLACE FUNCTION public.get_pos_split_payment(p_session_id UUID)
RETURNS JSONB LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path='' AS $$
DECLARE s public.pos_split_payment_sessions%ROWTYPE; o public.commerce_orders%ROWTYPE;
BEGIN
 PERFORM public._require_payments_staff(); SELECT * INTO s FROM public.pos_split_payment_sessions WHERE id=p_session_id;
 IF NOT FOUND THEN RAISE EXCEPTION 'split payment session not found'; END IF;
 SELECT * INTO o FROM public.commerce_orders WHERE id=s.commerce_order_id;
 IF o.cart_id IS NOT NULL AND NOT EXISTS(SELECT 1 FROM public.pos_carts c WHERE c.id=o.cart_id AND (c.created_by=auth.uid() OR public.is_pos_approver() OR public.has_staff_role(ARRAY['finance']::public.staff_role[]))) THEN
  RAISE EXCEPTION 'split payment access denied'; END IF;
 RETURN private.pos_split_payment_payload(s.id);
END $$;

CREATE OR REPLACE FUNCTION public.find_pos_split_payment(p_order_id UUID)
RETURNS JSONB LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path='' AS $$
DECLARE v_id UUID;
BEGIN
 PERFORM public._require_payments_staff(); SELECT id INTO v_id FROM public.pos_split_payment_sessions WHERE commerce_order_id=p_order_id;
 IF v_id IS NULL THEN RETURN NULL; END IF; RETURN public.get_pos_split_payment(v_id);
END $$;

CREATE OR REPLACE FUNCTION public.add_pos_split_payment_leg(
 p_session_id UUID,p_tender public.payment_tender,p_amount NUMERIC,p_request_id UUID,p_external_reference TEXT DEFAULT NULL)
RETURNS JSONB LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
DECLARE s public.pos_split_payment_sessions%ROWTYPE; o public.commerce_orders%ROWTYPE; l public.pos_split_payment_legs%ROWTYPE;
 v_available NUMERIC; v_seq INTEGER; v_pe UUID; v_balance NUMERIC; v_held NUMERIC;
BEGIN
 PERFORM public._require_payments_staff();
 SELECT * INTO l FROM public.pos_split_payment_legs WHERE request_id=p_request_id;
 IF FOUND THEN RETURN jsonb_build_object('leg_id',l.id,'session',private.pos_split_payment_payload(l.session_id)); END IF;
 PERFORM private.refresh_pos_split_payment_session(p_session_id);
 SELECT * INTO s FROM public.pos_split_payment_sessions WHERE id=p_session_id FOR UPDATE;
 IF s.status IN('settled','refund_review','refund_pending','refunded','cancelled','finalizing') THEN RAISE EXCEPTION 'split session does not accept new payments in status %',s.status; END IF;
 IF EXISTS(SELECT 1 FROM public.pos_split_payment_legs WHERE session_id=s.id AND status IN('pending','unknown')) THEN
  RAISE EXCEPTION 'reconcile the in-flight payment before starting another tender'; END IF;
 v_available:=round(s.total_amount-s.captured_amount-s.held_amount-s.pending_amount,2);
 IF COALESCE(p_amount,0)<=0 OR p_amount>v_available+0.01 THEN RAISE EXCEPTION 'split leg amount must be > 0 and <= available balance %',v_available; END IF;
 SELECT * INTO o FROM public.commerce_orders WHERE id=s.commerce_order_id FOR UPDATE;
 v_seq:=COALESCE((SELECT max(sequence_no) FROM public.pos_split_payment_legs WHERE session_id=s.id),0)+1;
 IF p_tender='bank' AND trim(COALESCE(p_external_reference,''))='' THEN RAISE EXCEPTION 'bank split payment requires a transfer/reference number'; END IF;
 INSERT INTO public.pos_split_payment_legs(session_id,request_id,sequence_no,tender,requested_amount,status,external_reference,created_by)
 VALUES(s.id,p_request_id,v_seq,p_tender,round(p_amount,2),CASE WHEN p_tender='store_credit' THEN 'held'::public.pos_split_payment_leg_status
  WHEN p_tender IN('cash','bank') THEN 'planned'::public.pos_split_payment_leg_status ELSE 'planned'::public.pos_split_payment_leg_status END,
  NULLIF(trim(COALESCE(p_external_reference,'')),''),auth.uid()) RETURNING * INTO l;
 IF p_tender='store_credit' THEN
  SELECT COALESCE(a.balance,0) INTO v_balance FROM public.store_credit_accounts a WHERE a.customer_id=o.customer_id AND a.currency=o.currency FOR UPDATE;
  SELECT COALESCE(SUM(x.requested_amount),0) INTO v_held FROM public.pos_split_payment_legs x JOIN public.pos_split_payment_sessions xs ON xs.id=x.session_id
   WHERE x.status='held' AND x.id<>l.id AND xs.currency=o.currency AND EXISTS(SELECT 1 FROM public.commerce_orders xo WHERE xo.id=xs.commerce_order_id AND xo.customer_id=o.customer_id);
  IF v_balance-v_held+0.001<l.requested_amount THEN RAISE EXCEPTION 'insufficient available store credit after active POS holds'; END IF;
  UPDATE public.pos_split_payment_legs SET locked_at=now() WHERE id=l.id;
 ELSIF p_tender IN('cash','bank') THEN
  v_pe:=public.create_payment_entry(o.customer_id,p_tender,l.requested_amount,o.currency,o.exchange_rate_applied,
    concat_ws(' · ','POS split '||p_tender::text||' '||s.id::text,l.external_reference));
  PERFORM private.post_pos_split_unapplied_payment_entry(v_pe,l.id);
  UPDATE public.pos_split_payment_legs SET status='captured',payment_entry_id=v_pe,locked_at=now(),provider_ref=l.external_reference,updated_at=now() WHERE id=l.id;
 END IF;
 PERFORM private.refresh_pos_split_payment_session(s.id);
 IF (SELECT status FROM public.pos_split_payment_sessions WHERE id=s.id)='fully_committed' THEN PERFORM private.finalize_pos_split_payment_session(s.id); END IF;
 RETURN jsonb_build_object('leg_id',l.id,'session',private.pos_split_payment_payload(s.id));
END $$;

REVOKE ALL ON FUNCTION public.start_pos_split_payment(UUID),public.get_pos_split_payment(UUID),public.find_pos_split_payment(UUID),
 public.add_pos_split_payment_leg(UUID,public.payment_tender,NUMERIC,UUID,TEXT) FROM PUBLIC,anon;

GRANT EXECUTE ON FUNCTION public.start_pos_split_payment(UUID),public.get_pos_split_payment(UUID),public.find_pos_split_payment(UUID),
 public.add_pos_split_payment_leg(UUID,public.payment_tender,NUMERIC,UUID,TEXT) TO authenticated,service_role;
