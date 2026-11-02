-- Found by the end-to-end trading-day run on a local replica (supabase/sim/e2e_day.py).
--
-- 1. A cashier (sales role only) could not finish a counter sale through reserve-first checkout:
--    settle_pos_commerce_tenders -> private.finalize_commerce_order -> checkout_pos_cart posts the
--    sale journal, and create_journal_draft refused because the sales-checkout accounting context
--    was never entered (checkout_pos_cart_with_tenders and checkout_pos_cart_on_account enter it).
--    Only admin/finance users could complete a sale.
-- 2. A manager (POS approver without the finance role) could not post an approved return: the
--    credit note journal and issue_store_credit required finance/admin. post_pos_return_case now
--    enters the same sales accounting context after its manager check, and issue_store_credit
--    honours it.
-- 3. Every POS sale on account failed: checkout_pos_cart_on_account emits
--    'pos_credit_sale_authorized', which was never registered in sms_event_catalog.

CREATE OR REPLACE FUNCTION private.finalize_commerce_order(p_order_id uuid)
 RETURNS uuid
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
DECLARE v_prev_accounting text := COALESCE(current_setting('app.sales_checkout_accounting',true),''); v_order public.commerce_orders%ROWTYPE; v_inv UUID; v_inv_row public.sales_invoices%ROWTYPE; v_total NUMERIC; v_bad BOOLEAN;
BEGIN
 SELECT * INTO v_order FROM public.commerce_orders WHERE id=p_order_id FOR UPDATE;
 IF NOT FOUND THEN RAISE EXCEPTION 'commerce order not found'; END IF;
 IF v_order.sales_invoice_id IS NOT NULL THEN RETURN v_order.sales_invoice_id; END IF;
 IF v_order.state NOT IN ('awaiting_payment','payment_processing','payment_failed') THEN RAISE EXCEPTION 'commerce order cannot finalize in state %',v_order.state; END IF;
 IF v_order.reservation_expires_at IS NULL OR v_order.reservation_expires_at<=now() THEN RAISE EXCEPTION 'commerce reservation expired'; END IF;
 IF EXISTS(SELECT 1 FROM public.inventory_reservations r WHERE r.commerce_order_id=v_order.id AND r.state='active'
  AND (r.expires_at IS NULL OR r.expires_at<=now())) THEN RAISE EXCEPTION 'one or more inventory reservations expired'; END IF;
 SELECT COALESCE(SUM(line_total),0) INTO v_total FROM public.pos_cart_lines WHERE cart_id=v_order.cart_id;
 IF abs(v_total-v_order.total)>0.001 THEN RAISE EXCEPTION 'checkout snapshot total drift detected'; END IF;
 SELECT EXISTS(
  WITH required AS (
   SELECT stock_item_id,SUM(qty_base)::numeric qty FROM public.pos_cart_lines
   WHERE cart_id=v_order.cart_id AND COALESCE(issues_stock,true)=true AND is_core_charge=false GROUP BY stock_item_id
  )
  SELECT 1 FROM required q LEFT JOIN public.inventory_reservations r
   ON r.commerce_order_id=v_order.id AND r.stock_item_id=q.stock_item_id AND r.warehouse_id=v_order.warehouse_id
  WHERE r.id IS NULL OR abs(r.reserved_qty-q.qty)>0.0001 OR r.state<>'active'
 ) INTO v_bad;
 IF v_bad THEN RAISE EXCEPTION 'checkout reservation no longer matches cart snapshot'; END IF;
 PERFORM set_config('app.commerce_finalize','1',true);
 PERFORM set_config('app.commerce_fulfill_order_id',v_order.id::text,true);
 -- Posting the sale journal is part of checkout: allow it for the cashier, as the other checkout paths do.
 PERFORM public._sales_checkout_accounting_enter();
 BEGIN
  v_inv:=public.checkout_pos_cart(
   v_order.cart_id,
   NULLIF(v_order.checkout_snapshot->>'receipt_email',''),
   NULLIF(v_order.checkout_snapshot->>'receipt_whatsapp_e164',''),
   NULLIF(v_order.checkout_snapshot->>'receipt_phone_e164','')
  );
 EXCEPTION WHEN OTHERS THEN
  PERFORM set_config('app.commerce_finalize','',true);
  PERFORM set_config('app.commerce_fulfill_order_id','',true);
  PERFORM set_config('app.sales_checkout_accounting',v_prev_accounting,true);
  RAISE;
 END;
 PERFORM set_config('app.commerce_finalize','',true);
 PERFORM set_config('app.commerce_fulfill_order_id','',true);
 PERFORM set_config('app.sales_checkout_accounting',v_prev_accounting,true);
 SELECT * INTO v_inv_row FROM public.sales_invoices WHERE id=v_inv;
 IF NOT FOUND OR v_inv_row.status<>'posted' OR v_inv_row.doc_type<>'invoice' THEN
  RAISE EXCEPTION 'commerce finalization did not produce a posted invoice';
 END IF;
 IF v_order.fulfillment_mode='immediate' THEN
  UPDATE public.inventory_reservations SET consumed_qty=reserved_qty,state='consumed',consumed_at=now(),expires_at=NULL
  WHERE commerce_order_id=v_order.id AND state='active';
 ELSE
  UPDATE public.inventory_reservations SET state='allocated',expires_at=NULL,reservation_reason='paid_dispatch_allocation'
  WHERE commerce_order_id=v_order.id AND state='active';
 END IF;
 UPDATE public.commerce_orders SET sales_invoice_id=v_inv,finalized_at=now(),updated_at=now() WHERE id=v_order.id;
 PERFORM private.enqueue_commerce_event('commerce:'||v_order.id::text||':invoice:'||v_inv::text,'commerce.order_finalized',
  v_order.id,jsonb_build_object('sales_invoice_id',v_inv));
 RETURN v_inv;
END $function$;

INSERT INTO public.sms_event_catalog(code, description, category, priority, is_active)
VALUES ('pos_credit_sale_authorized', 'POS sale on account authorized against the customer credit limit', 'sales', 'normal', true)
ON CONFLICT (code) DO NOTHING;

CREATE OR REPLACE FUNCTION public.post_pos_return_case(p_return_case_id uuid)
 RETURNS uuid
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
DECLARE c public.pos_return_cases%ROWTYPE; i public.sales_invoices%ROWTYPE; v_lines JSONB; v_src_lines JSONB; v_cn UUID; v_amount NUMERIC:=0;
 v_adj UUID; v_sc UUID; v_repl UUID; v_claim UUID; v_first_item UUID; v_current NUMERIC; r RECORD; v_till UUID; v_paid_out NUMERIC;
 v_prev_accounting text := COALESCE(current_setting('app.sales_checkout_accounting',true),'');
BEGIN
 IF NOT public.is_pos_approver() THEN RAISE EXCEPTION 'POS manager approval required'; END IF;
 SELECT * INTO c FROM public.pos_return_cases WHERE id=p_return_case_id FOR UPDATE;
 IF NOT FOUND OR c.status<>'draft' THEN RAISE EXCEPTION 'draft return case required'; END IF;
 SELECT * INTO i FROM public.sales_invoices WHERE id=c.source_invoice_id AND status='posted' AND doc_type='invoice' FOR UPDATE;
 IF NOT FOUND THEN RAISE EXCEPTION 'posted source invoice required'; END IF;
 -- A manager approved this return: its credit note, payout journal and store credit are part of the
 -- sale's own accounting, so a manager without the finance role can post them.
 PERFORM public._sales_checkout_accounting_enter();
 FOR r IN SELECT rl.source_invoice_line_id,rl.qty,sl.qty sold_qty FROM public.pos_return_case_lines rl JOIN public.sales_invoice_lines sl ON sl.id=rl.source_invoice_line_id WHERE rl.return_case_id=p_return_case_id LOOP
  SELECT COALESCE(SUM(x.qty),0) INTO v_current FROM public.pos_return_case_lines x JOIN public.pos_return_cases xc ON xc.id=x.return_case_id
   WHERE x.source_invoice_line_id=r.source_invoice_line_id AND xc.status='posted';
  IF r.qty+v_current>r.sold_qty THEN RAISE EXCEPTION 'return quantity exceeds remaining sold quantity'; END IF;
 END LOOP;
 SELECT COALESCE(jsonb_agg(jsonb_build_object('stock_item_id',l.stock_item_id,'uom_id',l.uom_id,'qty',l.qty,'unit_price',l.unit_price,'currency',i.currency)),'[]'::jsonb),
        COALESCE(jsonb_agg(jsonb_build_object('source_invoice_line_id',l.source_invoice_line_id,'qty',l.qty)),'[]'::jsonb),
        COALESCE(SUM(round(l.qty*l.unit_price,2)),0),min(l.stock_item_id::text)::uuid
 INTO v_lines,v_src_lines,v_amount,v_first_item FROM public.pos_return_case_lines l WHERE l.return_case_id=p_return_case_id;
 IF jsonb_array_length(v_lines)=0 THEN RAISE EXCEPTION 'return case has no lines'; END IF;
 v_till:=COALESCE(c.till_session_id,i.till_session_id);

 IF c.resolution IN('credit_note','cash_refund','store_credit') THEN
  IF c.resolution IN('credit_note','store_credit') AND i.customer_id IS NULL THEN
   RAISE EXCEPTION '% requires named customer',replace(c.resolution::text,'_',' ');
  END IF;
  IF c.resolution='cash_refund' AND v_till IS NULL THEN RAISE EXCEPTION 'cash refund requires active till session'; END IF;
  -- The credit note is valued from the source lines (discounts included): that value is what goes back.
  v_cn:=private._post_pos_credit_note(c.source_invoice_id,v_src_lines,
    CASE WHEN c.resolution='cash_refund' AND i.customer_id IS NULL THEN '1100' ELSE '1200' END,
    format('Return %s credit note against %s',c.document_number,COALESCE(i.document_number,i.id::text)));
  SELECT total INTO v_amount FROM public.sales_invoices WHERE id=v_cn;
  IF c.resolution IN('cash_refund','store_credit') THEN
   -- Money only goes back for what was paid: an unpaid (account) sale is settled by the credit note.
   SELECT COALESCE(SUM(cn.total),0) INTO v_paid_out FROM public.pos_return_cases pc JOIN public.sales_invoices cn ON cn.id=pc.credit_note_id
    WHERE pc.source_invoice_id=i.id AND pc.status='posted' AND pc.resolution IN('cash_refund','store_credit');
   IF v_amount+v_paid_out>COALESCE(i.amount_paid,i.total)+0.005 THEN
    RAISE EXCEPTION 'refund exceeds amount paid on the sale; use credit note';
   END IF;
  END IF;
  IF c.resolution='cash_refund' THEN
   IF i.customer_id IS NOT NULL THEN
    v_adj:=public.post_journal_entry(CURRENT_DATE,format('Cash payout for return %s',c.document_number),i.currency,i.exchange_rate_applied,
      jsonb_build_array(jsonb_build_object('account_code','1200','debit',v_amount,'credit',0,'currency',i.currency),jsonb_build_object('account_code','1100','debit',0,'credit',v_amount,'currency',i.currency)));
    UPDATE public.customers SET open_balance=open_balance+v_amount,updated_at=now() WHERE id=i.customer_id;
   END IF;
   PERFORM public.record_pos_till_cash_movement(v_till,'cash_refund',v_amount,'customer_refund',c.notes,NULL);
  ELSIF c.resolution='store_credit' THEN
   v_sc:=public.issue_store_credit(i.customer_id,v_amount,i.currency,i.exchange_rate_applied,format('Return %s store credit',c.document_number),'1200');
   UPDATE public.customers SET open_balance=open_balance+v_amount,updated_at=now() WHERE id=i.customer_id;
  END IF;
 ELSIF c.resolution='replacement' THEN
  PERFORM private.receive_external_return_to_quarantine(v_lines,format('Return %s replacement received',c.document_number));
  v_repl:=private.issue_pos_replacement_stock(i.warehouse_id,c.replacement_lines,format('Return %s replacement issued',c.document_number));
 ELSIF c.resolution='warranty' THEN
  IF jsonb_array_length(v_lines)<>1 THEN RAISE EXCEPTION 'warranty submission supports one claimed item per case'; END IF;
  v_claim:=public.open_warranty_claim(NULL,c.source_invoice_id,NULL,concat_ws(' · ',c.document_number,c.notes));
  UPDATE public.warranty_claims SET stock_item_id=v_first_item WHERE id=v_claim;
 ELSE RAISE EXCEPTION 'unsupported return resolution'; END IF;

 UPDATE public.pos_return_cases SET status='posted',credit_note_id=v_cn,warranty_claim_id=v_claim,replacement_stock_entry_id=v_repl,
  adjustment_journal_entry_id=v_adj,store_credit_ledger_id=v_sc,approved_by=auth.uid(),approved_at=now(),posted_at=now(),updated_at=now()
 WHERE id=p_return_case_id;
 PERFORM public._log_pos_action('return_posted','pos_return_cases',p_return_case_id,NULL,
  jsonb_build_object('resolution',c.resolution,'amount',v_amount,'credit_note_id',v_cn,'warranty_claim_id',v_claim,'replacement_stock_entry_id',v_repl),c.notes);
 UPDATE public.pos_action_audit SET reason_code=c.reason_code,approved_by_user_id=auth.uid(),approval_policy_action='return_post'
 WHERE id=(SELECT id FROM public.pos_action_audit WHERE entity_type='pos_return_cases' AND entity_id=p_return_case_id ORDER BY created_at DESC LIMIT 1);
 PERFORM set_config('app.sales_checkout_accounting',v_prev_accounting,true);
 RETURN p_return_case_id;
END $function$;

CREATE OR REPLACE FUNCTION public.issue_store_credit(p_customer_id uuid, p_amount numeric, p_currency currency_code DEFAULT 'USD'::currency_code, p_exchange_rate numeric DEFAULT 1, p_reason text DEFAULT NULL::text, p_debit_account text DEFAULT '1100'::text)
 RETURNS uuid
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
DECLARE
  v_journal UUID;
  v_ledger UUID;
  v_debit TEXT;
BEGIN
  IF NOT (
    auth.role() = 'service_role'
    OR public.has_staff_role(ARRAY['admin','finance']::public.staff_role[])
    OR public._sales_checkout_accounting_active()
  ) THEN
    RAISE EXCEPTION 'finance or admin role required to issue store credit';
  END IF;
  PERFORM public._payments_rpc_enter();

  IF p_customer_id IS NULL THEN
    RAISE EXCEPTION 'customer_id required';
  END IF;
  IF p_amount IS NULL OR p_amount <= 0 THEN
    RAISE EXCEPTION 'issue amount must be > 0';
  END IF;
  IF p_currency='ZIG' AND (p_exchange_rate IS NULL OR p_exchange_rate<=0) THEN
    RAISE EXCEPTION 'exchange_rate_applied required for ZIG';
  END IF;

  v_debit := COALESCE(NULLIF(p_debit_account, ''), '1100');
  IF v_debit NOT IN ('1100', '1200') THEN
    RAISE EXCEPTION 'issue_store_credit debit must be 1100 or 1200';
  END IF;

  v_journal := public.post_journal_entry(
    CURRENT_DATE,
    COALESCE(p_reason, 'Store credit issued'),
    p_currency,
    CASE WHEN p_currency='USD' THEN COALESCE(p_exchange_rate,1) ELSE p_exchange_rate END,
    jsonb_build_array(
      jsonb_build_object('account_code',v_debit,'debit',p_amount,'credit',0,'currency',p_currency),
      jsonb_build_object('account_code','2200','debit',0,'credit',p_amount,'currency',p_currency)
    )
  );

  v_ledger := public._append_store_credit(
    p_customer_id,'issue',p_amount,p_currency,
    CASE WHEN p_currency='USD' THEN COALESCE(p_exchange_rate,1) ELSE p_exchange_rate END,
    NULL,v_journal,COALESCE(p_reason,'Store credit issued')
  );

  PERFORM public.emit_domain_event(
    'refund_issued',
    'sc:issue:' || v_ledger::text,
    jsonb_build_object('ledger_id',v_ledger,'customer_id',p_customer_id,'amount',p_amount,'currency',p_currency),
    auth.uid(),
    format('GTR Auto: store credit issued %s %s',p_amount,p_currency)
  );
  RETURN v_ledger;
END;
$function$;
