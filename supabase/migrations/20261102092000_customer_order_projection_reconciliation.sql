-- Reconcile reserve-first order projection with later COD/card delivery fields.
-- Retain ownership, active dispatched-job preference, and invoice-ID compatibility.
CREATE OR REPLACE FUNCTION public.get_customer_order(p_invoice_id UUID) RETURNS JSONB LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path='' AS $$
DECLARE v_cust UUID:=public._current_customer_id(); v_order public.commerce_orders%ROWTYPE; v_inv public.sales_invoices%ROWTYPE; v_dn_status public.delivery_note_status; v_pick_status public.pick_list_status;
 v_active_job_id UUID; v_amount_paid NUMERIC:=0; v_doc_number TEXT; BEGIN
 IF v_cust IS NULL THEN RAISE EXCEPTION 'customer profile required'; END IF;
 SELECT * INTO v_order FROM public.commerce_orders WHERE id=p_invoice_id AND customer_id=v_cust;
 IF FOUND THEN
  IF v_order.sales_invoice_id IS NOT NULL THEN
   v_inv:=public._assert_customer_owns_invoice(v_order.sales_invoice_id); v_amount_paid:=COALESCE(v_inv.amount_paid,0); v_doc_number:=v_inv.document_number;
   SELECT dn.status INTO v_dn_status FROM public.delivery_notes dn WHERE dn.sales_invoice_id=v_inv.id ORDER BY dn.created_at DESC LIMIT 1;
   SELECT pl.status INTO v_pick_status FROM public.pick_lists pl WHERE pl.sales_invoice_id=v_inv.id ORDER BY pl.created_at DESC LIMIT 1;
   SELECT dj.id INTO v_active_job_id FROM public.delivery_jobs dj JOIN public.delivery_notes dn ON dn.id=dj.delivery_note_id WHERE dn.sales_invoice_id=v_inv.id AND dj.status IN ('pending','dispatched') ORDER BY CASE dj.status WHEN 'dispatched' THEN 0 ELSE 1 END,dj.updated_at DESC,dj.created_at DESC LIMIT 1;
  ELSE SELECT c.document_number INTO v_doc_number FROM public.pos_carts c WHERE c.id=v_order.cart_id; IF v_order.settled_payment_entry_id IS NOT NULL THEN v_amount_paid:=v_order.total; END IF; END IF;
  RETURN jsonb_build_object('invoice_id',COALESCE(v_order.sales_invoice_id,v_order.id),'commerce_order_id',v_order.id,'document_number',v_doc_number,'doc_type','invoice','status',v_order.state,
   'fulfillment_mode',v_order.fulfillment_mode,'delivery_payment_method',v_inv.delivery_payment_method,'currency',v_order.currency,'exchange_rate_applied',v_order.exchange_rate_applied,'subtotal',v_order.subtotal,'total',v_order.total,'amount_paid',v_amount_paid,
   'amount_open',GREATEST(v_order.total-v_amount_paid,0),'cart_id',v_order.cart_id,'posted_at',CASE WHEN v_order.sales_invoice_id IS NULL THEN NULL ELSE v_inv.posted_at END,
   'pick_list_status',v_pick_status,'delivery_note_status',v_dn_status,'active_delivery_job_id',v_active_job_id,'payment_exception',v_order.payment_exception);
 END IF;
 v_inv:=public._assert_customer_owns_invoice(p_invoice_id);
 SELECT dn.status INTO v_dn_status FROM public.delivery_notes dn WHERE dn.sales_invoice_id=v_inv.id ORDER BY dn.created_at DESC LIMIT 1;
 SELECT pl.status INTO v_pick_status FROM public.pick_lists pl WHERE pl.sales_invoice_id=v_inv.id ORDER BY pl.created_at DESC LIMIT 1;
 SELECT dj.id INTO v_active_job_id FROM public.delivery_jobs dj JOIN public.delivery_notes dn ON dn.id=dj.delivery_note_id WHERE dn.sales_invoice_id=v_inv.id AND dj.status IN ('pending','dispatched') ORDER BY CASE dj.status WHEN 'dispatched' THEN 0 ELSE 1 END,dj.updated_at DESC,dj.created_at DESC LIMIT 1;
 RETURN jsonb_build_object('invoice_id',v_inv.id,'document_number',v_inv.document_number,'doc_type',v_inv.doc_type,'status',v_inv.status,'fulfillment_mode',v_inv.fulfillment_mode,'delivery_payment_method',v_inv.delivery_payment_method,'currency',v_inv.currency,
 'exchange_rate_applied',v_inv.exchange_rate_applied,'subtotal',v_inv.subtotal,'total',v_inv.total,'amount_paid',v_inv.amount_paid,'amount_open',v_inv.total-v_inv.amount_paid,'cart_id',v_inv.cart_id,'posted_at',v_inv.posted_at,
 'pick_list_status',v_pick_status,'delivery_note_status',v_dn_status,'active_delivery_job_id',v_active_job_id); END; $$;
REVOKE ALL ON FUNCTION public.get_customer_order(UUID) FROM PUBLIC, anon; GRANT EXECUTE ON FUNCTION public.get_customer_order(UUID) TO authenticated,service_role;

COMMENT ON FUNCTION public.get_customer_order(UUID) IS 'Owning customer projection of reserve-first commerce orders or legacy invoices. Includes COD/card method and active delivery job (dispatched preferred); exposes no GPS trail or tracking token.';
