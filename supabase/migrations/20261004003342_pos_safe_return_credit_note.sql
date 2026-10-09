-- POS returns and warranty credit notes (Blueprint §10, phase 6).
--
-- 20260905131924 replaced the unsafe public.post_return_credit_note (client-supplied prices, stock
-- valued at selling price, no remaining-quantity check) with a stub that always raises. The staff
-- POS paths post_pos_return_case (credit_note / cash_refund / store_credit) and
-- approve_pos_warranty_claim (credit_note) still called it, so every one of those resolutions failed.
--
-- This adds private._post_pos_credit_note: a credit note tied to source invoice lines. Price comes
-- from the source line (pro rata of its line_total, so line discounts carry over), stock goes to
-- quarantine at the line's cost basis, COGS is reversed at that basis, and the remaining returnable
-- quantity is checked against every posted credit note for the line (customer returns included).
-- The two POS functions call it instead of the disabled stub. The stub stays disabled.

CREATE OR REPLACE FUNCTION private._post_pos_credit_note(
  p_invoice_id uuid,
  p_lines jsonb,            -- [{source_invoice_line_id, qty}] qty in the source line's UoM
  p_credit_account text,    -- '1200' named customer (AR) | '1100' walk-in cash paid back from the till
  p_memo text
)
RETURNS uuid
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO ''
AS $$
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
  IF p_credit_account NOT IN ('1200','1100') THEN RAISE EXCEPTION 'invalid credit note account'; END IF;
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
$$;
REVOKE ALL ON FUNCTION private._post_pos_credit_note(uuid,jsonb,text,text) FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION private._post_pos_credit_note(uuid,jsonb,text,text) TO service_role;

CREATE OR REPLACE FUNCTION public.post_pos_return_case(p_return_case_id uuid)
 RETURNS uuid
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
DECLARE c public.pos_return_cases%ROWTYPE; i public.sales_invoices%ROWTYPE; v_lines JSONB; v_src_lines JSONB; v_cn UUID; v_amount NUMERIC:=0;
 v_adj UUID; v_sc UUID; v_repl UUID; v_claim UUID; v_first_item UUID; v_current NUMERIC; r RECORD; v_till UUID; v_paid_out NUMERIC;
BEGIN
 IF NOT public.is_pos_approver() THEN RAISE EXCEPTION 'POS manager approval required'; END IF;
 SELECT * INTO c FROM public.pos_return_cases WHERE id=p_return_case_id FOR UPDATE;
 IF NOT FOUND OR c.status<>'draft' THEN RAISE EXCEPTION 'draft return case required'; END IF;
 SELECT * INTO i FROM public.sales_invoices WHERE id=c.source_invoice_id AND status='posted' AND doc_type='invoice' FOR UPDATE;
 IF NOT FOUND THEN RAISE EXCEPTION 'posted source invoice required'; END IF;
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
 RETURN p_return_case_id;
END $function$;

CREATE OR REPLACE FUNCTION public.approve_pos_warranty_claim(p_claim_id uuid, p_resolution public.warranty_claim_resolution, p_lines jsonb DEFAULT NULL::jsonb, p_replacement_lines jsonb DEFAULT NULL::jsonb)
 RETURNS uuid
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
DECLARE w public.warranty_claims%ROWTYPE; i public.sales_invoices%ROWTYPE; v_lines JSONB; v_uom UUID; v_cn UUID; v_quar UUID; v_repl UUID;
 v_src_lines JSONB:='[]'::jsonb; v_el JSONB; v_line_id UUID;
BEGIN
 IF NOT public.is_pos_approver() THEN RAISE EXCEPTION 'POS manager approval required'; END IF;
 SELECT * INTO w FROM public.warranty_claims WHERE id=p_claim_id FOR UPDATE;
 IF NOT FOUND OR w.status<>'open' THEN RAISE EXCEPTION 'open warranty claim required'; END IF;
 IF p_resolution NOT IN('replacement','credit_note','return_only') THEN RAISE EXCEPTION 'invalid warranty approval resolution'; END IF;
 IF w.sales_invoice_id IS NOT NULL THEN SELECT * INTO i FROM public.sales_invoices WHERE id=w.sales_invoice_id; END IF;
 v_lines:=p_lines;
 IF v_lines IS NULL AND w.stock_item_id IS NOT NULL THEN
  SELECT id INTO v_uom FROM public.uoms WHERE code='EA' ORDER BY created_at LIMIT 1;
  IF v_uom IS NULL THEN RAISE EXCEPTION 'EA UOM required for default warranty return'; END IF;
  v_lines:=jsonb_build_array(jsonb_build_object('stock_item_id',w.stock_item_id,'uom_id',v_uom,'qty',1,'unit_price',0,'currency',COALESCE(i.currency,'USD')));
 END IF;
 IF p_resolution='credit_note' THEN
  IF w.sales_invoice_id IS NULL OR v_lines IS NULL OR jsonb_array_length(v_lines)=0 THEN RAISE EXCEPTION 'credit-note warranty requires source invoice and return lines'; END IF;
  IF i.customer_id IS NULL THEN RAISE EXCEPTION 'credit note requires named customer'; END IF;
  -- Each line is credited at the price it was sold for: by source line, or the invoice line for that item.
  FOR v_el IN SELECT * FROM jsonb_array_elements(v_lines) LOOP
   v_line_id:=NULLIF(v_el->>'source_invoice_line_id','')::uuid;
   IF v_line_id IS NULL THEN
    SELECT sl.id INTO v_line_id FROM public.sales_invoice_lines sl
    WHERE sl.invoice_id=w.sales_invoice_id AND sl.stock_item_id=(v_el->>'stock_item_id')::uuid AND NOT COALESCE(sl.is_core_charge,false)
    ORDER BY sl.created_at,sl.id LIMIT 1;
   END IF;
   IF v_line_id IS NULL THEN RAISE EXCEPTION 'warranty item not on the source invoice'; END IF;
   v_src_lines:=v_src_lines||jsonb_build_array(jsonb_build_object('source_invoice_line_id',v_line_id,'qty',COALESCE((v_el->>'qty')::numeric,1)));
  END LOOP;
  v_cn:=private._post_pos_credit_note(w.sales_invoice_id,v_src_lines,'1200',format('Warranty %s credit note',w.document_number));
 ELSE
  IF v_lines IS NULL OR jsonb_array_length(v_lines)=0 THEN RAISE EXCEPTION 'physical warranty return lines required'; END IF;
  v_quar:=private.receive_external_return_to_quarantine(v_lines,format('Warranty return %s',w.document_number));
 END IF;
 IF p_resolution='replacement' THEN
  IF p_replacement_lines IS NULL OR jsonb_typeof(p_replacement_lines)<>'array' OR jsonb_array_length(p_replacement_lines)=0 THEN RAISE EXCEPTION 'replacement lines required'; END IF;
  v_repl:=private.issue_pos_replacement_stock(COALESCE(i.warehouse_id,(SELECT id FROM public.warehouses WHERE code='MAIN' AND is_active LIMIT 1)),p_replacement_lines,format('Warranty replacement %s',w.document_number));
 END IF;
 IF w.stock_serial_id IS NOT NULL THEN
  UPDATE public.stock_serials SET status='quarantine',warehouse_id=(SELECT id FROM public.warehouses WHERE is_quarantine AND is_active ORDER BY code LIMIT 1) WHERE id=w.stock_serial_id;
 END IF;
 UPDATE public.warranty_claims SET status='approved',resolution=p_resolution,credit_note_id=v_cn,quarantine_stock_entry_id=v_quar,
  replacement_stock_entry_id=v_repl,decided_by=auth.uid(),decided_at=now(),updated_at=now() WHERE id=p_claim_id;
 PERFORM public.emit_domain_event('warranty_claim_approved','warranty:pos-approved:'||p_claim_id::text,
  jsonb_build_object('warranty_claim_id',p_claim_id,'resolution',p_resolution,'credit_note_id',v_cn,'quarantine_stock_entry_id',v_quar,'replacement_stock_entry_id',v_repl));
 PERFORM public._log_pos_action('warranty_approved','warranty_claims',p_claim_id,to_jsonb(w),(SELECT to_jsonb(x) FROM public.warranty_claims x WHERE x.id=p_claim_id),NULL);
 RETURN p_claim_id;
END $function$;
