-- Card refunds could never be finalised: finalize_pos_card_terminal_refund called post_finance_refund,
-- which is disabled ("settle a pending return refund instead"), so an approved card refund stayed in
-- recovery with the money gone from the card and nothing in the books. Found by supabase/sim/e2e_card.py.
-- A card refund now finalises as a credit note for the whole sale (parts back to quarantine, stock and
-- cost reversed) whose money side credits card terminal clearing (1170), the account the sale was paid into.
-- A sale already partly returned is refused at the start (its remaining amount would not match the card refund).

ALTER TABLE public.pos_card_terminal_attempts ADD COLUMN IF NOT EXISTS credit_note_id uuid REFERENCES public.sales_invoices(id);

CREATE OR REPLACE FUNCTION private._post_pos_credit_note(p_invoice_id uuid, p_lines jsonb, p_credit_account text, p_memo text)
 RETURNS uuid
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
DECLARE
  v_src public.sales_invoices%ROWTYPE;
  v_line public.sales_invoice_lines%ROWTYPE;
  v_quar uuid;
  v_cn uuid;
  v_el jsonb;
  v_qty numeric;
  v_qty_base numeric;
  v_prior numeric;
  v_value numeric;
  v_cost numeric;
  v_total numeric := 0;
  v_cost_total numeric := 0;
  v_journal uuid;
  v_jl jsonb;
BEGIN
  -- 1170: refunded to the customer's card through the card machine (finalize_pos_card_terminal_refund).
  IF p_credit_account NOT IN ('1200','1100','1170') THEN RAISE EXCEPTION 'invalid credit note account'; END IF;
  IF p_lines IS NULL OR jsonb_typeof(p_lines)<>'array' OR jsonb_array_length(p_lines)=0 THEN
    RAISE EXCEPTION 'credit note lines required';
  END IF;
  SELECT * INTO v_src FROM public.sales_invoices WHERE id=p_invoice_id FOR UPDATE;
  IF NOT FOUND OR v_src.doc_type<>'invoice' OR v_src.status<>'posted' THEN RAISE EXCEPTION 'posted source invoice required'; END IF;
  IF p_credit_account='1200' AND v_src.customer_id IS NULL THEN RAISE EXCEPTION 'credit note to account requires named customer'; END IF;
  SELECT id INTO v_quar FROM public.warehouses WHERE is_active AND is_quarantine ORDER BY code LIMIT 1;
  IF v_quar IS NULL THEN RAISE EXCEPTION 'active quarantine warehouse required'; END IF;

  INSERT INTO public.sales_invoices(
    doc_type,status,document_number,customer_id,warehouse_id,currency,exchange_rate_applied,
    return_against_id,customer_phone_e164,customer_email,customer_whatsapp_e164,posted_by,posted_at
  ) VALUES(
    'credit_note','draft',public.next_series_value('CN-'),v_src.customer_id,v_quar,v_src.currency,v_src.exchange_rate_applied,
    v_src.id,v_src.customer_phone_e164,v_src.customer_email,v_src.customer_whatsapp_e164,auth.uid(),now()
  ) RETURNING id INTO v_cn;

  FOR v_el IN SELECT * FROM jsonb_array_elements(p_lines) LOOP
    SELECT * INTO v_line FROM public.sales_invoice_lines
    WHERE id=(v_el->>'source_invoice_line_id')::uuid FOR UPDATE;
    IF NOT FOUND OR v_line.invoice_id<>v_src.id THEN RAISE EXCEPTION 'source invoice line not on this invoice'; END IF;
    IF COALESCE(v_line.is_core_charge,false) THEN RAISE EXCEPTION 'core charges are returned through the core return'; END IF;
    v_qty:=(v_el->>'qty')::numeric;
    IF v_qty IS NULL OR v_qty<=0 OR v_line.qty<=0 THEN RAISE EXCEPTION 'return quantity must be positive'; END IF;
    v_qty_base:=round(v_qty*v_line.qty_base/v_line.qty,6);
    SELECT COALESCE(sum(sil.qty_base),0) INTO v_prior
    FROM public.sales_invoice_lines sil JOIN public.sales_invoices cn ON cn.id=sil.invoice_id
    WHERE sil.return_against_line_id=v_line.id AND cn.doc_type='credit_note' AND cn.status='posted';
    IF v_qty_base>GREATEST(COALESCE(v_line.qty_fulfilled,v_line.qty_base)-v_prior,0)+0.0001 THEN
      RAISE EXCEPTION 'return quantity exceeds remaining sold quantity';
    END IF;
    v_value:=round(v_line.line_total*v_qty/v_line.qty,2);
    v_cost:=round(COALESCE(v_line.unit_cost_basis,0)*v_qty_base,2);

    INSERT INTO public.sales_invoice_lines(
      invoice_id,stock_item_id,uom_id,qty,qty_base,unit_price,line_total,qty_fulfilled,
      issues_stock,unit_cost_basis,cost_total_basis,return_against_line_id
    ) VALUES(
      v_cn,v_line.stock_item_id,v_line.uom_id,v_qty,v_qty_base,v_line.unit_price,v_value,v_qty_base,
      true,v_line.unit_cost_basis,v_cost,v_line.id
    );
    PERFORM public._adjust_stock_level(v_line.stock_item_id,v_quar,v_qty_base,'FIFO',COALESCE(v_line.unit_cost_basis,0),v_src.currency);
    INSERT INTO public.stock_batches(batch_code,stock_item_id,warehouse_id,valuation_method,unit_cost,currency,qty_on_hand)
    VALUES(public.next_series_value('BATCH-'),v_line.stock_item_id,v_quar,'FIFO',COALESCE(v_line.unit_cost_basis,0),v_src.currency,v_qty_base);
    v_total:=v_total+v_value;
    v_cost_total:=v_cost_total+v_cost;
  END LOOP;

  IF v_total<=0 THEN RAISE EXCEPTION 'return has no credit value'; END IF;
  v_jl:=jsonb_build_array(
    jsonb_build_object('account_code','4110','debit',v_total,'credit',0,'currency',v_src.currency),
    jsonb_build_object('account_code',p_credit_account,'debit',0,'credit',v_total,'currency',v_src.currency));
  IF v_cost_total>0 THEN
    v_jl:=v_jl||jsonb_build_array(
      jsonb_build_object('account_code','1310','debit',v_cost_total,'credit',0,'currency',v_src.currency),
      jsonb_build_object('account_code','5100','debit',0,'credit',v_cost_total,'currency',v_src.currency));
  END IF;
  v_journal:=public.post_journal_entry(CURRENT_DATE,COALESCE(p_memo,format('Return CN against %s',COALESCE(v_src.document_number,v_src.id::text))),
    v_src.currency,v_src.exchange_rate_applied,v_jl);
  UPDATE public.sales_invoices SET status='posted',subtotal=v_total,total=v_total,journal_entry_id=v_journal,posted_at=now() WHERE id=v_cn;
  IF p_credit_account='1200' THEN
    UPDATE public.customers SET open_balance=open_balance-v_total,updated_at=now() WHERE id=v_src.customer_id;
  END IF;

  PERFORM public.emit_domain_event('return_completed','return:done:'||v_cn::text,
    jsonb_build_object('credit_note_id',v_cn,'invoice_id',v_src.id,'amount',v_total),auth.uid(),NULL);
  PERFORM public.emit_domain_event('quarantine_received','return:quar:'||v_cn::text,
    jsonb_build_object('credit_note_id',v_cn),auth.uid(),NULL);
  PERFORM public.enqueue_customer_receipts(v_cn);
  RETURN v_cn;
END;
$function$;

CREATE OR REPLACE FUNCTION public.begin_pos_card_terminal_refund(p_invoice_id uuid, p_terminal_id uuid, p_request_id uuid)
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
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
 -- The refund returns the whole sale to the card; a sale already partly returned is refunded through returns.
 IF EXISTS(SELECT 1 FROM public.sales_invoices cn WHERE cn.return_against_id=i.id AND cn.doc_type='credit_note' AND cn.status='posted') THEN
  RAISE EXCEPTION 'part of this sale was already returned; refund the rest through a return';
 END IF;
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
END $function$;

CREATE OR REPLACE FUNCTION public.finalize_pos_card_terminal_refund(p_attempt_id uuid, p_notes text DEFAULT NULL::text)
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
DECLARE a public.pos_card_terminal_attempts%ROWTYPE; v_refund UUID; v_error TEXT; v_lines jsonb;
BEGIN
 IF NOT (public.is_pos_approver() OR public.has_staff_role(ARRAY['finance']::public.staff_role[])) THEN RAISE EXCEPTION 'POS manager or finance approval required'; END IF;
 SELECT * INTO a FROM public.pos_card_terminal_attempts WHERE id=p_attempt_id FOR UPDATE;
 IF NOT FOUND OR a.operation<>'refund' THEN RAISE EXCEPTION 'card terminal refund attempt required'; END IF;
 IF a.status='settled' THEN RETURN private.pos_card_terminal_attempt_payload(a.id); END IF;
 IF a.status<>'approved' THEN RAISE EXCEPTION 'terminal refund approval required before finance finalization'; END IF;
 BEGIN
  -- The machine paid the customer back: every line not yet returned comes back on a credit note
  -- (parts to quarantine), and the money side credits card clearing (Dr 4110 / Cr 1170).
  -- post_finance_refund (direct refund posting) is disabled, so finalising always failed before.
  SELECT jsonb_agg(jsonb_build_object('source_invoice_line_id',l.id,'qty',l.qty)) INTO v_lines
    FROM public.sales_invoice_lines l WHERE l.invoice_id=a.source_invoice_id AND NOT COALESCE(l.is_core_charge,false) AND l.qty>0;
  PERFORM public._sales_checkout_accounting_enter();
  v_refund:=private._post_pos_credit_note(a.source_invoice_id,v_lines,'1170',
    concat_ws(' · ','Card refund '||COALESCE(a.terminal_transaction_id,a.external_ref),p_notes));
  PERFORM public._sales_checkout_accounting_exit();
  IF abs((SELECT total FROM public.sales_invoices WHERE id=v_refund)-a.amount)>0.01 THEN
   RAISE EXCEPTION 'credit note % does not match the % refunded on the card',(SELECT total FROM public.sales_invoices WHERE id=v_refund),a.amount;
  END IF;
  UPDATE public.pos_card_terminal_attempts SET status='settled',credit_note_id=v_refund,finalization_error=NULL,
   finalized_by=auth.uid(),finalized_at=now(),updated_at=now() WHERE id=a.id;
 EXCEPTION WHEN OTHERS THEN
  v_error:=SQLERRM;
  PERFORM public._sales_checkout_accounting_exit();
  UPDATE public.pos_card_terminal_attempts SET finalization_error=left(v_error,500),updated_at=now() WHERE id=a.id;
  RETURN jsonb_build_object('attempt_id',a.id,'invoice_id',a.source_invoice_id,'status','recovery_required','error',left(v_error,500));
 END;
 RETURN private.pos_card_terminal_attempt_payload(a.id);
END $function$;

CREATE OR REPLACE FUNCTION private.pos_card_terminal_attempt_payload(p_attempt_id uuid)
 RETURNS jsonb
 LANGUAGE sql
 STABLE SECURITY DEFINER
 SET search_path TO ''
AS $function$
 SELECT jsonb_build_object(
  'attempt_id',a.id,'request_id',a.request_id,'operation',a.operation,'status',a.status,
  'terminal_id',a.terminal_id,'amount',a.amount,'currency',a.currency,'external_ref',a.external_ref,
  'terminal_transaction_id',a.terminal_transaction_id,'rrn',a.rrn,'authorization_code',a.authorization_code,
  'card_last4',a.card_last4,'card_scheme',a.card_scheme,'response_code',a.response_code,'response_message',a.response_message,
  'commerce_order_id',a.commerce_order_id,'delivery_job_id',a.delivery_job_id,'source_invoice_id',a.source_invoice_id,
  'parent_attempt_id',a.parent_attempt_id,'split_leg_id',a.split_leg_id,'split_refund_request_id',a.split_refund_request_id,
  'payment_entry_id',a.payment_entry_id,'invoice_id',a.invoice_id,'finance_refund_id',a.finance_refund_id,'credit_note_id',a.credit_note_id,'credit_note_number',(SELECT cn.document_number FROM public.sales_invoices cn WHERE cn.id=a.credit_note_id),'finalization_error',a.finalization_error,
  'terminal',jsonb_build_object('id',t.id,'code',t.code,'label',t.label,'acquirer_name',t.acquirer_name,
    'external_terminal_id',t.external_terminal_id,'adapter_key',t.adapter_key,'adapter_config',t.adapter_config,'allow_delivery',t.allow_delivery)
 ) FROM public.pos_card_terminal_attempts a JOIN public.pos_card_terminals t ON t.id=a.terminal_id WHERE a.id=p_attempt_id;
$function$;
