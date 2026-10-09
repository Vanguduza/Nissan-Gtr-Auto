-- Exported from the hosted project's supabase_migrations.schema_migrations (20260905132237 route_customer_return_request_to_internal_outbox).
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
      IF v_match_count<>1 THEN RAISE EXCEPTION 'return line is ambiguous; sales_invoice_line_id required'; END IF;
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
    WHERE rl.source_invoice_line_id=v_src.id AND rr.status NOT IN ('rejected','cancelled');

    IF v_qty_base>GREATEST(v_src.qty_fulfilled-v_reserved,0)+0.0001 THEN
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

  PERFORM private.enqueue_commerce_event(
    'return:req:'||v_req::text,'return.requested',v_req,
    jsonb_build_object('return_request_id',v_req,'invoice_id',p_invoice_id,'customer_id',v_customer)
  );
  RETURN v_req;
END;
$$;
