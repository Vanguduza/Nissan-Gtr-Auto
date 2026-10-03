-- Exported from the hosted project's supabase_migrations.schema_migrations (20260905131924 replace_unsafe_return_refund_workflow).
-- Source of record for what production ran; see supabase/live-history/README.md.

CREATE OR REPLACE FUNCTION public.request_customer_return(p_invoice_id uuid,p_lines jsonb)
RETURNS uuid
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO ''
AS $$
DECLARE
  v_customer uuid:=public._current_customer_id();
  v_inv public.sales_invoices%ROWTYPE;
  v_req uuid;
  v_elem jsonb;
  v_src public.sales_invoice_lines%ROWTYPE;
  v_line_id uuid;
  v_item uuid;
  v_uom uuid;
  v_qty numeric;
  v_qty_base numeric;
  v_reserved numeric;
  v_match_count integer;
BEGIN
  IF v_customer IS NULL THEN RAISE EXCEPTION 'customer profile required'; END IF;
  SELECT * INTO v_inv FROM public.sales_invoices
  WHERE id=p_invoice_id AND customer_id=v_customer FOR UPDATE;
  IF NOT FOUND OR v_inv.doc_type<>'invoice' OR v_inv.status<>'posted' THEN
    RAISE EXCEPTION 'owned posted invoice required';
  END IF;
  IF p_lines IS NULL OR jsonb_typeof(p_lines)<>'array' OR jsonb_array_length(p_lines)=0 THEN
    RAISE EXCEPTION 'return lines required';
  END IF;

  INSERT INTO public.customer_return_requests(customer_id,source_invoice_id,requested_by)
  VALUES(v_customer,p_invoice_id,auth.uid()) RETURNING id INTO v_req;

  FOR v_elem IN SELECT * FROM jsonb_array_elements(p_lines)
  LOOP
    v_line_id:=NULLIF(v_elem->>'sales_invoice_line_id','')::uuid;
    v_qty:=NULLIF(v_elem->>'qty','')::numeric;
    IF v_qty IS NULL OR v_qty<=0 THEN RAISE EXCEPTION 'each return line requires qty > 0'; END IF;

    IF v_line_id IS NOT NULL THEN
      SELECT * INTO v_src FROM public.sales_invoice_lines
      WHERE id=v_line_id AND invoice_id=p_invoice_id;
    ELSE
      v_item:=NULLIF(v_elem->>'stock_item_id','')::uuid;
      v_uom:=NULLIF(v_elem->>'uom_id','')::uuid;
      IF v_item IS NULL OR v_uom IS NULL THEN
        RAISE EXCEPTION 'sales_invoice_line_id or stock_item_id/uom_id required';
      END IF;
      SELECT count(*) INTO v_match_count FROM public.sales_invoice_lines
      WHERE invoice_id=p_invoice_id AND stock_item_id=v_item AND uom_id=v_uom AND NOT is_core_charge;
      IF v_match_count<>1 THEN
        RAISE EXCEPTION 'return line is ambiguous; sales_invoice_line_id required';
      END IF;
      SELECT * INTO v_src FROM public.sales_invoice_lines
      WHERE invoice_id=p_invoice_id AND stock_item_id=v_item AND uom_id=v_uom AND NOT is_core_charge
      LIMIT 1;
    END IF;

    IF NOT FOUND OR v_src.is_core_charge THEN RAISE EXCEPTION 'returnable invoice line not found'; END IF;
    IF NOT COALESCE(v_src.issues_stock,true) THEN
      RAISE EXCEPTION 'non-stock/exploded-kit header returns require staff-assisted workflow';
    END IF;
    IF COALESCE(v_src.qty_fulfilled,0)<=0 THEN RAISE EXCEPTION 'unfulfilled items cannot be returned'; END IF;
    IF v_src.unit_cost_basis IS NULL THEN RAISE EXCEPTION 'invoice line cost basis missing; return requires finance review'; END IF;

    v_qty_base:=public.convert_to_base_uom(v_src.stock_item_id,v_src.uom_id,v_qty);
    SELECT COALESCE(sum(rl.qty_base),0) INTO v_reserved
    FROM public.customer_return_request_lines rl
    JOIN public.customer_return_requests rr ON rr.id=rl.return_request_id
    WHERE rl.source_invoice_line_id=v_src.id
      AND rr.status NOT IN ('rejected','cancelled');

    IF v_qty_base > GREATEST(v_src.qty_fulfilled-v_reserved,0)+0.0001 THEN
      RAISE EXCEPTION 'return quantity exceeds fulfilled unreturned quantity';
    END IF;

    INSERT INTO public.customer_return_request_lines(
      return_request_id,source_invoice_line_id,stock_item_id,uom_id,qty,qty_base,
      unit_price_snapshot,line_total_snapshot,unit_cost_basis,cost_total_basis
    ) VALUES(
      v_req,v_src.id,v_src.stock_item_id,v_src.uom_id,v_qty,v_qty_base,
      v_src.unit_price,round(v_src.unit_price*v_qty,2),v_src.unit_cost_basis,
      round(v_src.unit_cost_basis*v_qty_base,4)
    );
  END LOOP;

  PERFORM public.emit_domain_event(
    'return_requested','return:req:'||v_req::text,
    jsonb_build_object('return_request_id',v_req,'invoice_id',p_invoice_id),auth.uid(),NULL
  );
  RETURN v_req;
END;
$$;

CREATE OR REPLACE FUNCTION public.post_customer_return_credit_note(p_invoice_id uuid,p_lines jsonb)
RETURNS uuid
LANGUAGE sql
SECURITY DEFINER
SET search_path TO ''
AS $$ SELECT public.request_customer_return(p_invoice_id,p_lines); $$;

CREATE OR REPLACE FUNCTION public.review_customer_return(p_return_request_id uuid,p_approve boolean,p_notes text DEFAULT NULL)
RETURNS uuid
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO ''
AS $$
DECLARE v_req public.customer_return_requests%ROWTYPE;
BEGIN
  IF NOT (auth.role()='service_role' OR public.has_staff_role(ARRAY['admin','sales']::public.staff_role[])) THEN
    RAISE EXCEPTION 'sales or admin role required';
  END IF;
  SELECT * INTO v_req FROM public.customer_return_requests WHERE id=p_return_request_id FOR UPDATE;
  IF NOT FOUND THEN RAISE EXCEPTION 'return request not found'; END IF;
  IF v_req.status<>'requested' THEN RAISE EXCEPTION 'only requested returns can be reviewed'; END IF;

  UPDATE public.customer_return_requests
  SET status=CASE WHEN COALESCE(p_approve,false) THEN 'approved'::public.customer_return_status ELSE 'rejected'::public.customer_return_status END,
      review_notes=p_notes,reviewed_by=auth.uid(),reviewed_at=now(),updated_at=now()
  WHERE id=p_return_request_id;
  PERFORM public.emit_domain_event(
    CASE WHEN COALESCE(p_approve,false) THEN 'return_approved' ELSE 'return_rejected' END,
    'return:review:'||p_return_request_id::text,
    jsonb_build_object('return_request_id',p_return_request_id,'approved',COALESCE(p_approve,false)),auth.uid(),NULL
  );
  RETURN p_return_request_id;
END;
$$;

CREATE OR REPLACE FUNCTION public.receive_customer_return(p_return_request_id uuid)
RETURNS uuid
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO ''
AS $$
DECLARE
  v_req public.customer_return_requests%ROWTYPE;
  v_src public.sales_invoices%ROWTYPE;
  v_quar uuid;
  v_cn uuid;
  r record;
  v_source_line public.sales_invoice_lines%ROWTYPE;
  v_prior_returned numeric;
  v_total numeric:=0;
  v_cost_total numeric:=0;
  v_prior_ar_credit numeric:=0;
  v_invoice_open numeric:=0;
  v_ar_credit numeric:=0;
  v_refund_due numeric:=0;
  v_journal uuid;
  v_lines jsonb;
  v_refund uuid;
BEGIN
  IF NOT (auth.role()='service_role' OR public.has_staff_role(ARRAY['admin','warehouse']::public.staff_role[])) THEN
    RAISE EXCEPTION 'warehouse or admin role required to receive returns';
  END IF;
  SELECT * INTO v_req FROM public.customer_return_requests WHERE id=p_return_request_id FOR UPDATE;
  IF NOT FOUND THEN RAISE EXCEPTION 'return request not found'; END IF;
  IF v_req.status<>'approved' THEN RAISE EXCEPTION 'approved return request required'; END IF;
  IF v_req.credit_note_id IS NOT NULL THEN RETURN v_req.credit_note_id; END IF;

  SELECT * INTO v_src FROM public.sales_invoices WHERE id=v_req.source_invoice_id FOR UPDATE;
  IF NOT FOUND OR v_src.doc_type<>'invoice' OR v_src.status<>'posted' THEN RAISE EXCEPTION 'posted source invoice required'; END IF;
  SELECT id INTO v_quar FROM public.warehouses WHERE is_active AND is_quarantine ORDER BY code LIMIT 1;
  IF v_quar IS NULL THEN RAISE EXCEPTION 'active quarantine warehouse required'; END IF;

  INSERT INTO public.sales_invoices(
    doc_type,status,document_number,customer_id,warehouse_id,currency,exchange_rate_applied,
    return_against_id,customer_phone_e164,customer_email,customer_whatsapp_e164,posted_by,posted_at
  ) VALUES(
    'credit_note','draft',public.next_series_value('CN-'),v_src.customer_id,v_quar,v_src.currency,v_src.exchange_rate_applied,
    v_src.id,v_src.customer_phone_e164,v_src.customer_email,v_src.customer_whatsapp_e164,auth.uid(),now()
  ) RETURNING id INTO v_cn;

  FOR r IN
    SELECT * FROM public.customer_return_request_lines WHERE return_request_id=p_return_request_id ORDER BY created_at,id
  LOOP
    SELECT * INTO v_source_line FROM public.sales_invoice_lines WHERE id=r.source_invoice_line_id FOR UPDATE;
    IF NOT FOUND OR v_source_line.invoice_id<>v_src.id THEN RAISE EXCEPTION 'source invoice line changed'; END IF;
    SELECT COALESCE(sum(sil.qty_base),0) INTO v_prior_returned
    FROM public.sales_invoice_lines sil
    JOIN public.sales_invoices cn ON cn.id=sil.invoice_id
    WHERE sil.return_against_line_id=v_source_line.id AND cn.doc_type='credit_note' AND cn.status='posted';
    IF r.qty_base>GREATEST(v_source_line.qty_fulfilled-v_prior_returned,0)+0.0001 THEN
      RAISE EXCEPTION 'return quantity no longer available for source line %',v_source_line.id;
    END IF;

    INSERT INTO public.sales_invoice_lines(
      invoice_id,stock_item_id,uom_id,qty,qty_base,unit_price,line_total,qty_fulfilled,
      issues_stock,unit_cost_basis,cost_total_basis,return_against_line_id
    ) VALUES(
      v_cn,r.stock_item_id,r.uom_id,r.qty,r.qty_base,r.unit_price_snapshot,r.line_total_snapshot,r.qty_base,
      true,r.unit_cost_basis,r.cost_total_basis,r.source_invoice_line_id
    );

    PERFORM public._adjust_stock_level(r.stock_item_id,v_quar,r.qty_base,'FIFO',r.unit_cost_basis,v_src.currency);
    INSERT INTO public.stock_batches(batch_code,stock_item_id,warehouse_id,valuation_method,unit_cost,currency,qty_on_hand)
    VALUES(public.next_series_value('BATCH-'),r.stock_item_id,v_quar,'FIFO',r.unit_cost_basis,v_src.currency,r.qty_base);
    v_total:=v_total+r.line_total_snapshot;
    v_cost_total:=v_cost_total+r.cost_total_basis;
  END LOOP;

  IF v_total<=0 THEN RAISE EXCEPTION 'return has no credit value'; END IF;
  SELECT COALESCE(sum(rr.ar_credit_amount),0) INTO v_prior_ar_credit
  FROM public.customer_return_requests rr
  WHERE rr.source_invoice_id=v_src.id AND rr.id<>p_return_request_id
    AND rr.status IN ('received','refund_pending','completed');
  v_invoice_open:=GREATEST(v_src.total-COALESCE(v_src.amount_paid,0)-v_prior_ar_credit,0);
  v_ar_credit:=LEAST(v_total,v_invoice_open);
  v_refund_due:=GREATEST(v_total-v_ar_credit,0);

  v_lines:=jsonb_build_array(jsonb_build_object('account_code','4110','debit',round(v_total,2),'credit',0,'currency',v_src.currency));
  IF v_ar_credit>0 THEN
    v_lines:=v_lines||jsonb_build_array(jsonb_build_object('account_code','1200','debit',0,'credit',round(v_ar_credit,2),'currency',v_src.currency));
  END IF;
  IF v_refund_due>0 THEN
    v_lines:=v_lines||jsonb_build_array(jsonb_build_object('account_code','2220','debit',0,'credit',round(v_refund_due,2),'currency',v_src.currency));
  END IF;
  IF v_cost_total>0 THEN
    v_lines:=v_lines||jsonb_build_array(
      jsonb_build_object('account_code','1310','debit',round(v_cost_total,2),'credit',0,'currency',v_src.currency),
      jsonb_build_object('account_code','5100','debit',0,'credit',round(v_cost_total,2),'currency',v_src.currency)
    );
  END IF;

  v_journal:=public.post_journal_entry(CURRENT_DATE,format('Return CN against %s',COALESCE(v_src.document_number,v_src.id::text)),v_src.currency,v_src.exchange_rate_applied,v_lines);
  UPDATE public.sales_invoices SET status='posted',subtotal=round(v_total,2),total=round(v_total,2),journal_entry_id=v_journal,posted_at=now() WHERE id=v_cn;
  IF v_src.customer_id IS NOT NULL AND v_ar_credit>0 THEN
    UPDATE public.customers SET open_balance=GREATEST(0,open_balance-v_ar_credit),updated_at=now() WHERE id=v_src.customer_id;
  END IF;

  IF v_refund_due>0 THEN
    INSERT INTO public.return_refund_requests(return_request_id,credit_note_id,customer_id,source_invoice_id,amount,currency,method)
    VALUES(p_return_request_id,v_cn,v_req.customer_id,v_src.id,round(v_refund_due,2),v_src.currency,v_req.preferred_resolution)
    RETURNING id INTO v_refund;
  END IF;

  UPDATE public.customer_return_requests
  SET status=CASE WHEN v_refund_due>0 THEN 'refund_pending'::public.customer_return_status ELSE 'completed'::public.customer_return_status END,
      credit_note_id=v_cn,ar_credit_amount=round(v_ar_credit,2),refund_due_amount=round(v_refund_due,2),
      received_by=auth.uid(),received_at=now(),updated_at=now()
  WHERE id=p_return_request_id;

  PERFORM public.emit_domain_event('return_received','return:received:'||p_return_request_id::text,
    jsonb_build_object('return_request_id',p_return_request_id,'credit_note_id',v_cn,'ar_credit',v_ar_credit,'refund_due',v_refund_due),auth.uid(),NULL);
  IF v_refund_due>0 THEN
    PERFORM public.emit_domain_event('refund_pending','return:refund:'||p_return_request_id::text,
      jsonb_build_object('return_request_id',p_return_request_id,'refund_request_id',v_refund,'amount',v_refund_due,'currency',v_src.currency),auth.uid(),NULL);
  END IF;
  PERFORM public.enqueue_customer_receipts(v_cn);
  RETURN v_cn;
END;
$$;

CREATE OR REPLACE FUNCTION public.settle_return_refund_store_credit(p_return_request_id uuid,p_notes text DEFAULT NULL)
RETURNS uuid
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO ''
AS $$
DECLARE v_ref public.return_refund_requests%ROWTYPE; v_journal uuid; v_ledger uuid;
BEGIN
  IF NOT (auth.role()='service_role' OR public.has_staff_role(ARRAY['admin','finance']::public.staff_role[])) THEN
    RAISE EXCEPTION 'finance or admin role required';
  END IF;
  SELECT * INTO v_ref FROM public.return_refund_requests WHERE return_request_id=p_return_request_id FOR UPDATE;
  IF NOT FOUND THEN RAISE EXCEPTION 'pending refund not found'; END IF;
  IF v_ref.status='completed' THEN RETURN v_ref.id; END IF;
  IF v_ref.status NOT IN ('pending','failed') THEN RAISE EXCEPTION 'refund cannot settle in status %',v_ref.status; END IF;
  PERFORM public._payments_rpc_enter();
  v_journal:=public.post_journal_entry(CURRENT_DATE,COALESCE(p_notes,'Return refund converted to store credit'),v_ref.currency,1,
    jsonb_build_array(
      jsonb_build_object('account_code','2220','debit',v_ref.amount,'credit',0,'currency',v_ref.currency),
      jsonb_build_object('account_code','2200','debit',0,'credit',v_ref.amount,'currency',v_ref.currency)
    ));
  v_ledger:=public._append_store_credit(v_ref.customer_id,'issue',v_ref.amount,v_ref.currency,1,NULL,v_journal,COALESCE(p_notes,'Return refund converted to store credit'));
  UPDATE public.return_refund_requests SET method='store_credit',status='completed',processed_by=auth.uid(),processed_at=now(),failure_reason=NULL,updated_at=now() WHERE id=v_ref.id;
  UPDATE public.customer_return_requests SET status='completed',updated_at=now() WHERE id=p_return_request_id;
  PERFORM public.emit_domain_event('refund_completed','return:refund:store-credit:'||v_ref.id::text,
    jsonb_build_object('refund_request_id',v_ref.id,'return_request_id',p_return_request_id,'method','store_credit','ledger_id',v_ledger,'amount',v_ref.amount),auth.uid(),NULL);
  RETURN v_ref.id;
END;
$$;

CREATE OR REPLACE FUNCTION public.complete_return_refund_manual(p_return_request_id uuid,p_tender public.payment_tender,p_settlement_reference text)
RETURNS uuid
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO ''
AS $$
DECLARE v_ref public.return_refund_requests%ROWTYPE; v_gl text; v_journal uuid;
BEGIN
  IF NOT (auth.role()='service_role' OR public.has_staff_role(ARRAY['admin','finance']::public.staff_role[])) THEN
    RAISE EXCEPTION 'finance or admin role required';
  END IF;
  IF p_tender NOT IN ('cash'::public.payment_tender,'bank'::public.payment_tender) THEN
    RAISE EXCEPTION 'manual return refund supports cash or bank only; provider refunds require verified provider completion';
  END IF;
  IF NULLIF(trim(COALESCE(p_settlement_reference,'')),'') IS NULL THEN RAISE EXCEPTION 'settlement reference required'; END IF;
  SELECT * INTO v_ref FROM public.return_refund_requests WHERE return_request_id=p_return_request_id FOR UPDATE;
  IF NOT FOUND THEN RAISE EXCEPTION 'pending refund not found'; END IF;
  IF v_ref.status='completed' THEN RETURN v_ref.id; END IF;
  IF v_ref.status NOT IN ('pending','failed') THEN RAISE EXCEPTION 'refund cannot settle in status %',v_ref.status; END IF;
  v_gl:=public.gl_account_for_payment_tender(p_tender);
  v_journal:=public.post_journal_entry(CURRENT_DATE,format('Return refund %s %s',p_tender,p_settlement_reference),v_ref.currency,1,
    jsonb_build_array(
      jsonb_build_object('account_code','2220','debit',v_ref.amount,'credit',0,'currency',v_ref.currency),
      jsonb_build_object('account_code',v_gl,'debit',0,'credit',v_ref.amount,'currency',v_ref.currency)
    ));
  UPDATE public.return_refund_requests
  SET method=CASE WHEN p_tender='cash' THEN 'cash'::public.return_resolution_method ELSE 'bank'::public.return_resolution_method END,
      status='completed',settlement_reference=trim(p_settlement_reference),processed_by=auth.uid(),processed_at=now(),failure_reason=NULL,updated_at=now()
  WHERE id=v_ref.id;
  UPDATE public.customer_return_requests SET status='completed',updated_at=now() WHERE id=p_return_request_id;
  PERFORM public.emit_domain_event('refund_completed','return:refund:manual:'||v_ref.id::text,
    jsonb_build_object('refund_request_id',v_ref.id,'return_request_id',p_return_request_id,'method',p_tender,'reference',p_settlement_reference,'amount',v_ref.amount),auth.uid(),NULL);
  RETURN v_ref.id;
END;
$$;

CREATE OR REPLACE FUNCTION public.post_return_credit_note(p_invoice_id uuid,p_lines jsonb)
RETURNS uuid
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO ''
AS $$ BEGIN
  RAISE EXCEPTION 'legacy direct return posting disabled; use request/review/receive return workflow';
END; $$;

CREATE OR REPLACE FUNCTION public.post_finance_refund(p_invoice_id uuid,p_notes text DEFAULT NULL)
RETURNS uuid
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO ''
AS $$ BEGIN
  RAISE EXCEPTION 'legacy direct refund posting disabled; settle a pending return refund instead';
END; $$;

CREATE OR REPLACE FUNCTION public.post_pos_refund(p_invoice_id uuid,p_notes text DEFAULT NULL)
RETURNS uuid
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO ''
AS $$ BEGIN
  RAISE EXCEPTION 'legacy direct POS refund disabled; use canonical return/refund workflow';
END; $$;

REVOKE ALL ON FUNCTION public.request_customer_return(uuid,jsonb) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.post_customer_return_credit_note(uuid,jsonb) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.review_customer_return(uuid,boolean,text) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.receive_customer_return(uuid) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.settle_return_refund_store_credit(uuid,text) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.complete_return_refund_manual(uuid,public.payment_tender,text) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.post_return_credit_note(uuid,jsonb) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.post_finance_refund(uuid,text) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.post_pos_refund(uuid,text) FROM PUBLIC;

GRANT EXECUTE ON FUNCTION public.request_customer_return(uuid,jsonb) TO authenticated,service_role;
GRANT EXECUTE ON FUNCTION public.post_customer_return_credit_note(uuid,jsonb) TO authenticated,service_role;
GRANT EXECUTE ON FUNCTION public.review_customer_return(uuid,boolean,text) TO authenticated,service_role;
GRANT EXECUTE ON FUNCTION public.receive_customer_return(uuid) TO authenticated,service_role;
GRANT EXECUTE ON FUNCTION public.settle_return_refund_store_credit(uuid,text) TO authenticated,service_role;
GRANT EXECUTE ON FUNCTION public.complete_return_refund_manual(uuid,public.payment_tender,text) TO authenticated,service_role;
GRANT EXECUTE ON FUNCTION public.post_return_credit_note(uuid,jsonb) TO service_role;
GRANT EXECUTE ON FUNCTION public.post_finance_refund(uuid,text) TO service_role;
GRANT EXECUTE ON FUNCTION public.post_pos_refund(uuid,text) TO service_role;
