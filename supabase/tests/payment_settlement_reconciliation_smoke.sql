-- Local seeded database only. Test real legacy + reserve-first settlement and roll back fixtures.
BEGIN;
CREATE FUNCTION pg_temp.payment_actor(p_uid UUID,p_role TEXT DEFAULT 'authenticated') RETURNS VOID
LANGUAGE plpgsql AS $$ BEGIN
 PERFORM set_config('request.jwt.claim.sub',p_uid::TEXT,true);
 PERFORM set_config('request.jwt.claim.role',p_role,true);
 PERFORM set_config('request.jwt.claims',jsonb_build_object('sub',p_uid,'role',p_role)::TEXT,true);
END $$;
DO $$
DECLARE
 v_admin UUID := 'a0000000-0000-4000-8000-000000000001';
 v_user UUID := 'c0000000-0000-4000-8000-0000000000a1';
 v_customer UUID; v_main UUID; v_uom UUID; v_list UUID; v_item UUID;
 v_cart UUID; v_invoice UUID; v_order UUID; v_intent UUID; v_payment UUID; v_retry UUID;
 v_provider TEXT; v_external TEXT; v_denied BOOLEAN; v_allocations JSONB;
BEGIN
 PERFORM pg_temp.payment_actor(v_admin);
 SELECT id INTO v_main FROM public.warehouses WHERE code='MAIN';
 SELECT id INTO v_uom FROM public.uoms WHERE code='EA';
 SELECT id INTO v_list FROM public.price_lists WHERE code='RETAIL';
 SELECT id INTO v_customer FROM public.customers WHERE profile_id=v_user;
 IF v_main IS NULL OR v_uom IS NULL OR v_list IS NULL OR v_customer IS NULL THEN
  RAISE EXCEPTION 'payment smoke requires seeded MAIN/EA/RETAIL/storefront-a'; END IF;
 INSERT INTO public.stock_items(oem_part_number,description,base_uom_id,reorder_point,reorder_qty)
 VALUES('P-SETTLE-RECON-001','Settlement reconciliation fixture',v_uom,0,1) RETURNING id INTO v_item;
 INSERT INTO public.price_list_items(price_list_id,stock_item_id,unit_price,core_charge)
 VALUES(v_list,v_item,40,0);
 PERFORM public.post_stock_receipt(v_main,'settlement fixture',jsonb_build_array(jsonb_build_object(
  'stock_item_id',v_item,'uom_id',v_uom,'qty',20,'unit_cost',10,'currency','USD','valuation_method','FIFO')));

 FOREACH v_provider IN ARRAY ARRAY['contipay','paynow'] LOOP
  PERFORM pg_temp.payment_actor(v_admin);
  v_cart:=public.create_pos_cart(v_main,v_customer,'USD','immediate');
  PERFORM public.add_cart_line(v_cart,v_item,v_uom,1);
  v_invoice:=public.checkout_pos_cart(v_cart);
  PERFORM pg_temp.payment_actor(v_user);
  v_external:='SETTLE-RECON-'||v_provider||'-'||v_invoice::TEXT;
  IF v_provider='contipay' THEN
   v_intent:=public.create_customer_contipay_intent(v_invoice,'ecocash',v_external,40);
  ELSE
   v_intent:=public.create_customer_paynow_intent(v_invoice,'ecocash',v_external,40);
  END IF;
  PERFORM pg_temp.payment_actor(v_admin,'service_role');
  IF v_provider='contipay' THEN
   v_payment:=public.mark_contipay_settled(v_external,'hash-'||v_external,'provider-test',NULL);
   v_retry:=public.mark_contipay_settled(v_external,'hash-'||v_external,'provider-test',NULL);
  ELSE
   v_payment:=public.mark_paynow_settled(v_external,'hash-'||v_external,'provider-test',NULL);
   v_retry:=public.mark_paynow_settled(v_external,'hash-'||v_external,'provider-test',NULL);
  END IF;
  IF v_payment IS NULL OR v_retry IS DISTINCT FROM v_payment THEN
   RAISE EXCEPTION '% settlement did not return the same payment on duplicate webhook',v_provider; END IF;
  IF NOT EXISTS(SELECT 1 FROM public.payment_allocations WHERE payment_entry_id=v_payment
      AND sales_invoice_id=v_invoice AND amount=40) OR
     NOT EXISTS(SELECT 1 FROM public.sales_invoices WHERE id=v_invoice AND amount_paid=40) THEN
   RAISE EXCEPTION '% did not allocate/post the verified legacy invoice payment',v_provider; END IF;
 END LOOP;

 -- The fallback cannot redirect an intent's money to a peer invoice or another currency.
 v_denied:=false;
 BEGIN
  PERFORM private.resolve_payment_intent_allocations(NULL,jsonb_build_object('sales_invoice_id',v_invoice),40,gen_random_uuid(),'USD');
 EXCEPTION WHEN OTHERS THEN
  IF SQLERRM <> 'payment intent does not match an eligible customer invoice' THEN RAISE; END IF;
  v_denied:=true;
 END;
 IF NOT v_denied THEN RAISE EXCEPTION 'cross-customer metadata allocation accepted'; END IF;
 v_denied:=false;
 BEGIN
  PERFORM private.resolve_payment_intent_allocations(NULL,jsonb_build_object('sales_invoice_id',v_invoice),40,v_customer,'ZIG');
 EXCEPTION WHEN OTHERS THEN
  IF SQLERRM <> 'payment intent does not match an eligible customer invoice' THEN RAISE; END IF;
  v_denied:=true;
 END;
 IF NOT v_denied THEN RAISE EXCEPTION 'cross-currency metadata allocation accepted'; END IF;

 -- Preserve the newer reserve-first path: stock is reserved before any invoice is posted.
 PERFORM pg_temp.payment_actor(v_user);
 v_cart:=public.create_customer_cart(v_main,'USD','immediate',1);
 PERFORM public.add_customer_cart_line(v_cart,v_item,v_uom,1);
 v_order:=public.checkout_customer_cart(v_cart);
 IF NOT EXISTS(SELECT 1 FROM public.commerce_orders WHERE id=v_order AND sales_invoice_id IS NULL) THEN
  RAISE EXCEPTION 'reserve-first checkout posted an invoice before payment'; END IF;
 v_external:='SETTLE-RECON-COMMERCE-'||v_order::TEXT;
 v_intent:=public.create_customer_paynow_intent(v_order,'ecocash',v_external,40);
 PERFORM pg_temp.payment_actor(v_admin,'service_role');
 v_payment:=public.mark_paynow_settled(v_external,'hash-'||v_external,'provider-commerce',NULL);
 IF NOT EXISTS(SELECT 1 FROM public.commerce_orders co JOIN public.sales_invoices si ON si.id=co.sales_invoice_id
   WHERE co.id=v_order AND co.settled_payment_entry_id=v_payment AND si.amount_paid=40) THEN
  RAISE EXCEPTION 'reserve-first provider settlement lost finalization or allocation'; END IF;
 IF has_function_privilege('authenticated',
   'public.mark_paynow_settled(text,text,text,jsonb,public.currency_code,numeric,numeric,boolean,text)','EXECUTE') THEN
  RAISE EXCEPTION 'authenticated caller may directly settle provider payments'; END IF;
 RAISE NOTICE 'payment_settlement_reconciliation_smoke ok';
END $$;
ROLLBACK;
