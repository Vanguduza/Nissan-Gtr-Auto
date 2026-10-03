-- Exported from the hosted project's supabase_migrations.schema_migrations (20260908122000 pos_reserve_first_receipts).
-- Source of record for what production ran; see supabase/live-history/README.md.

-- Preserve POS receipt contacts through reserve-first commerce finalization.
CREATE OR REPLACE FUNCTION public.prepare_pos_commerce_checkout_v2(
 p_cart_id UUID,p_checkout_request_id UUID,p_reservation_ttl INTERVAL DEFAULT interval '20 minutes',
 p_receipt_email TEXT DEFAULT NULL,p_receipt_whatsapp_e164 TEXT DEFAULT NULL,p_receipt_phone_e164 TEXT DEFAULT NULL)
RETURNS UUID LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
DECLARE v_order UUID;
BEGIN
 v_order:=public.prepare_pos_commerce_checkout(p_cart_id,p_checkout_request_id,p_reservation_ttl);
 UPDATE public.commerce_orders
 SET checkout_snapshot=checkout_snapshot||jsonb_strip_nulls(jsonb_build_object(
  'receipt_email',NULLIF(trim(COALESCE(p_receipt_email,'')),''),
  'receipt_whatsapp_e164',NULLIF(trim(COALESCE(p_receipt_whatsapp_e164,'')),''),
  'receipt_phone_e164',NULLIF(trim(COALESCE(p_receipt_phone_e164,'')),'')
 )),updated_at=now()
 WHERE id=v_order;
 RETURN v_order;
END $$;

REVOKE ALL ON FUNCTION public.prepare_pos_commerce_checkout_v2(UUID,UUID,INTERVAL,TEXT,TEXT,TEXT) FROM PUBLIC,anon;

GRANT EXECUTE ON FUNCTION public.prepare_pos_commerce_checkout_v2(UUID,UUID,INTERVAL,TEXT,TEXT,TEXT) TO authenticated,service_role;

CREATE OR REPLACE FUNCTION private.finalize_commerce_order(p_order_id UUID)
RETURNS UUID LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
DECLARE v_order public.commerce_orders%ROWTYPE; v_inv UUID; v_inv_row public.sales_invoices%ROWTYPE; v_total NUMERIC; v_bad BOOLEAN;
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
  RAISE;
 END;
 PERFORM set_config('app.commerce_finalize','',true);
 PERFORM set_config('app.commerce_fulfill_order_id','',true);
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
END $$;

REVOKE ALL ON FUNCTION private.finalize_commerce_order(UUID) FROM PUBLIC,anon,authenticated;

GRANT EXECUTE ON FUNCTION private.finalize_commerce_order(UUID) TO service_role;
