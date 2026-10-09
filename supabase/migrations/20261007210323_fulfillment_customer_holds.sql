-- Back-orders and branch transfers for a customer; found by supabase/sim/e2e_backorder.py.
--
-- 1. A back-order could be marked ready before the part arrived, and "ready" held nothing: another
--    customer could buy the arrived part, after which the waiting customer's own sale failed.
--    Ready now requires the part in the branch and holds it for the customer for 14 days.
-- 2. A transfer made for a customer was held nowhere on arrival, could not be attached to the
--    customer's sale, and could be handed over without a paid sale. It is now held on arrival,
--    can be sold like a back-order, and is handed over only once paid.
-- 3. Checkout counts parts held for this sale's own back-order / transfer as available to it.
--    Posting the invoice links the request and releases the hold (the sale took the stock).
-- Cancelling a request already releases its holds; holds expire after 14 days.

-- Holds an arrived part for the customer it was ordered for (back-order or transfer).
CREATE OR REPLACE FUNCTION private.hold_pos_fulfillment_stock(p_request_id uuid)
 RETURNS void LANGUAGE plpgsql SECURITY DEFINER SET search_path TO '' AS $f$
DECLARE r public.pos_fulfillment_requests%ROWTYPE; v_qty numeric; v_wh uuid;
BEGIN
 SELECT * INTO r FROM public.pos_fulfillment_requests WHERE id=p_request_id;
 v_wh:=COALESCE(r.destination_warehouse_id,r.source_warehouse_id);
 v_qty:=public.convert_to_base_uom(r.stock_item_id,r.uom_id,r.qty);
 IF EXISTS(SELECT 1 FROM public.inventory_reservations WHERE pos_fulfillment_request_id=p_request_id AND state='active' AND warehouse_id=v_wh) THEN RETURN; END IF;
 IF public.get_inventory_available_quantity(r.stock_item_id,v_wh)<v_qty THEN
  RAISE EXCEPTION 'the part has not arrived yet (available %, needed %)',public.get_inventory_available_quantity(r.stock_item_id,v_wh),v_qty;
 END IF;
 INSERT INTO public.inventory_reservations(pos_fulfillment_request_id,stock_item_id,warehouse_id,required_qty,reserved_qty,expires_at,state,reservation_reason)
 VALUES(p_request_id,r.stock_item_id,v_wh,v_qty,v_qty,now()+interval '14 days','active','customer_order_hold');
END $f$;
REVOKE ALL ON FUNCTION private.hold_pos_fulfillment_stock(uuid) FROM PUBLIC, anon, authenticated;

CREATE OR REPLACE FUNCTION public.mark_pos_fulfillment_ready(p_request_id uuid, p_notes text DEFAULT NULL::text)
 RETURNS uuid
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
BEGIN
 PERFORM public._require_sales_staff();
 -- A back-order is ready only when the part is in the branch; it is then held for the customer.
 IF EXISTS(SELECT 1 FROM public.pos_fulfillment_requests WHERE id=p_request_id AND kind='backorder' AND status IN('requested','reserved')) THEN
  PERFORM private.hold_pos_fulfillment_stock(p_request_id);
 END IF;
 UPDATE public.pos_fulfillment_requests SET status='ready',ready_at=now(),notes=concat_ws(E'\n',notes,p_notes),updated_at=now()
 WHERE id=p_request_id AND status IN('requested','reserved') AND kind IN('customer_collection','alternate_pickup','backorder');
 IF NOT FOUND THEN RAISE EXCEPTION 'request cannot be marked ready'; END IF; RETURN p_request_id;
END $function$;

CREATE OR REPLACE FUNCTION private.sync_pos_fulfillment_transfer()
 RETURNS trigger
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
BEGIN
 IF NEW.entry_type='transfer' AND OLD.status IS DISTINCT FROM NEW.status THEN
  IF NEW.status='posted' THEN
   UPDATE public.inventory_reservations SET state='released',released_at=now()
    WHERE pos_fulfillment_request_id IN(SELECT id FROM public.pos_fulfillment_requests WHERE stock_entry_id=NEW.id) AND state IN('active','allocated');
   UPDATE public.pos_fulfillment_requests SET status='ready',ready_at=now(),expires_at=NULL,updated_at=now()
    WHERE stock_entry_id=NEW.id AND status='awaiting_transfer_approval';
   -- Moved for a customer: hold it at the receiving branch until they buy it.
   PERFORM private.hold_pos_fulfillment_stock(r.id)
    FROM public.pos_fulfillment_requests r WHERE r.stock_entry_id=NEW.id AND r.status='ready' AND r.customer_id IS NOT NULL;
  ELSIF NEW.status='rejected' THEN
   UPDATE public.inventory_reservations SET state='released',released_at=now()
    WHERE pos_fulfillment_request_id IN(SELECT id FROM public.pos_fulfillment_requests WHERE stock_entry_id=NEW.id) AND state IN('active','allocated');
   UPDATE public.pos_fulfillment_requests SET status='rejected',updated_at=now() WHERE stock_entry_id=NEW.id;
  END IF;
 END IF; RETURN NEW;
END $function$;

CREATE OR REPLACE FUNCTION public.prepare_pos_commerce_checkout(p_cart_id uuid, p_checkout_request_id uuid, p_reservation_ttl interval DEFAULT '00:20:00'::interval)
 RETURNS uuid
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
DECLARE c public.pos_carts%ROWTYPE; o public.commerce_orders%ROWTYPE; v_order UUID; v_customer UUID; r RECORD; v_available NUMERIC; v_existing UUID; s public.pos_till_sessions%ROWTYPE;
BEGIN
 PERFORM public._require_sales_staff();
 IF p_checkout_request_id IS NULL THEN RAISE EXCEPTION 'checkout_request_id required'; END IF;
 IF p_reservation_ttl IS NULL OR p_reservation_ttl<interval '2 minutes' OR p_reservation_ttl>interval '60 minutes' THEN RAISE EXCEPTION 'reservation TTL must be between 2 and 60 minutes'; END IF;
 SELECT * INTO c FROM public.pos_carts WHERE id=p_cart_id FOR UPDATE;
 IF NOT FOUND OR c.status<>'open' THEN RAISE EXCEPTION 'open POS cart required'; END IF;
 IF c.created_by IS DISTINCT FROM auth.uid() THEN RAISE EXCEPTION 'POS cart belongs to another operator'; END IF;
 IF c.till_session_id IS NULL THEN RAISE EXCEPTION 'open till session required before payment'; END IF;
 SELECT * INTO s FROM public.pos_till_sessions WHERE id=c.till_session_id FOR UPDATE;
 IF NOT FOUND OR s.status<>'open' OR s.operator_user_id<>auth.uid() OR s.warehouse_id<>c.warehouse_id THEN RAISE EXCEPTION 'active operator till session at cart warehouse required'; END IF;
 IF NOT EXISTS(SELECT 1 FROM public.pos_cart_lines WHERE cart_id=p_cart_id) THEN RAISE EXCEPTION 'cart has no lines'; END IF;
 IF c.checkout_order_id IS NOT NULL THEN
  SELECT * INTO o FROM public.commerce_orders WHERE id=c.checkout_order_id FOR UPDATE;
  IF FOUND AND o.finalized_at IS NULL AND o.state IN('awaiting_payment','payment_processing','payment_failed') AND o.reservation_expires_at>now() THEN RETURN o.id; END IF;
  IF FOUND AND o.finalized_at IS NULL AND o.state IN('awaiting_payment','payment_processing','payment_failed') THEN PERFORM private.release_commerce_order(o.id,'payment_expired','POS checkout reservation expired before retry'); END IF;
  SELECT * INTO c FROM public.pos_carts WHERE id=p_cart_id FOR UPDATE;
 END IF;
 SELECT id INTO v_existing FROM public.commerce_orders WHERE checkout_request_id=p_checkout_request_id AND cart_id=p_cart_id;
 IF v_existing IS NOT NULL THEN RETURN v_existing; END IF;
 v_customer:=c.customer_id;
 IF v_customer IS NULL THEN v_customer:=public.ensure_pos_walkin_customer(); UPDATE public.pos_carts SET customer_id=v_customer,updated_at=now() WHERE id=p_cart_id; END IF;
 INSERT INTO public.commerce_orders(customer_id,cart_id,checkout_request_id,warehouse_id,fulfillment_mode,currency,exchange_rate_applied,subtotal,total,state,checkout_snapshot,reservation_expires_at)
 SELECT v_customer,c.id,p_checkout_request_id,c.warehouse_id,c.fulfillment_mode,c.currency,c.exchange_rate_applied,
  COALESCE(SUM(l.line_total),0),COALESCE(SUM(l.line_total),0),'checkout_pending',
  jsonb_build_object('cart_id',c.id,'till_session_id',c.till_session_id,'customer_id',v_customer,'warehouse_id',c.warehouse_id,'currency',c.currency,'fulfillment_mode',c.fulfillment_mode,
   'lines',COALESCE(jsonb_agg(jsonb_build_object('line_id',l.id,'stock_item_id',l.stock_item_id,'uom_id',l.uom_id,'qty',l.qty,'qty_base',l.qty_base,'unit_price',l.unit_price,'line_total',l.line_total,'is_core_charge',l.is_core_charge) ORDER BY l.created_at),'[]'::jsonb)),
  now()+p_reservation_ttl
 FROM public.pos_cart_lines l WHERE l.cart_id=c.id GROUP BY c.id,c.warehouse_id,c.fulfillment_mode,c.currency,c.exchange_rate_applied,c.till_session_id
 RETURNING id INTO v_order;
 FOR r IN SELECT stock_item_id,SUM(qty_base)::numeric required_qty FROM public.pos_cart_lines WHERE cart_id=p_cart_id AND COALESCE(issues_stock,true)=true AND NOT is_core_charge GROUP BY stock_item_id ORDER BY stock_item_id LOOP
  v_available:=public.get_inventory_available_quantity(r.stock_item_id,c.warehouse_id)
   -- Parts held for this customer (back-order / transfer attached to this sale) are theirs to buy.
   + COALESCE((SELECT SUM(GREATEST(ir.reserved_qty-ir.consumed_qty,0)) FROM public.inventory_reservations ir
       JOIN public.pos_fulfillment_requests fr ON fr.id=ir.pos_fulfillment_request_id
      WHERE fr.cart_id=c.id AND ir.stock_item_id=r.stock_item_id AND ir.warehouse_id=c.warehouse_id
        AND ir.state='active' AND (ir.expires_at IS NULL OR ir.expires_at>now())),0);
  IF v_available<r.required_qty THEN RAISE EXCEPTION 'insufficient available stock for item % (available %, required %)',r.stock_item_id,v_available,r.required_qty; END IF;
  INSERT INTO public.inventory_reservations(commerce_order_id,stock_item_id,warehouse_id,required_qty,reserved_qty,expires_at,state,reservation_reason)
  VALUES(v_order,r.stock_item_id,c.warehouse_id,r.required_qty,r.required_qty,now()+p_reservation_ttl,'active','pos_checkout_payment_hold');
 END LOOP;
 UPDATE public.commerce_orders SET state='awaiting_payment',updated_at=now() WHERE id=v_order;
 UPDATE public.pos_carts SET checkout_locked_at=now(),checkout_order_id=v_order,updated_at=now() WHERE id=p_cart_id;
 PERFORM private.enqueue_commerce_event('commerce:'||v_order::text||':pos-prepared','commerce.checkout_prepared',v_order,jsonb_build_object('cart_id',p_cart_id,'till_session_id',c.till_session_id));
 RETURN v_order;
EXCEPTION WHEN unique_violation THEN
 SELECT id INTO v_existing FROM public.commerce_orders WHERE checkout_request_id=p_checkout_request_id AND cart_id=p_cart_id; IF v_existing IS NOT NULL THEN RETURN v_existing; END IF; RAISE;
END $function$;

CREATE OR REPLACE FUNCTION public.attach_pos_fulfillment_to_cart(p_request_id uuid, p_cart_id uuid)
 RETURNS uuid
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
DECLARE r public.pos_fulfillment_requests%ROWTYPE; c public.pos_carts%ROWTYPE;
BEGIN
 PERFORM public._require_sales_staff();
 SELECT * INTO r FROM public.pos_fulfillment_requests WHERE id=p_request_id FOR UPDATE;
 IF NOT FOUND OR r.invoice_id IS NOT NULL
    OR NOT ((r.kind='backorder' AND r.status IN('requested','ready')) OR (r.kind='branch_transfer' AND r.status='ready')) THEN
  RAISE EXCEPTION 'an open back-order or arrived transfer without a sale is required';
 END IF;
 SELECT * INTO c FROM public.pos_carts WHERE id=p_cart_id;
 IF NOT FOUND OR c.status<>'open' THEN RAISE EXCEPTION 'open sale required'; END IF;
 UPDATE public.pos_fulfillment_requests
 SET cart_id=p_cart_id, customer_id=COALESCE(customer_id,c.customer_id), updated_at=now()
 WHERE id=p_request_id;
 RETURN p_request_id;
END $function$;

CREATE OR REPLACE FUNCTION private.sync_pos_fulfillment_invoice()
 RETURNS trigger
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
BEGIN
 IF NEW.status='posted' AND NEW.cart_id IS NOT NULL THEN
  UPDATE public.inventory_reservations SET state='released',released_at=now()
   WHERE pos_fulfillment_request_id IN(SELECT id FROM public.pos_fulfillment_requests WHERE cart_id=NEW.cart_id AND status='reserved') AND state='active';
  UPDATE public.pos_fulfillment_requests SET invoice_id=NEW.id,status='ready',ready_at=now(),expires_at=NULL,updated_at=now()
   WHERE cart_id=NEW.cart_id AND status='reserved' AND kind IN('alternate_pickup','customer_collection');
  -- A back-order sold on this sale: link the invoice only when the back-ordered part is on it.
  UPDATE public.pos_fulfillment_requests r
   SET invoice_id=NEW.id, status='ready', ready_at=COALESCE(r.ready_at,now()), updated_at=now()
   WHERE r.cart_id=NEW.cart_id AND r.invoice_id IS NULL
     AND ((r.kind='backorder' AND r.status IN('requested','ready')) OR (r.kind='branch_transfer' AND r.status='ready'))
     AND EXISTS(SELECT 1 FROM public.sales_invoice_lines l WHERE l.invoice_id=NEW.id AND l.stock_item_id=r.stock_item_id);
  UPDATE public.inventory_reservations SET state='released',released_at=now()
   WHERE state='active' AND pos_fulfillment_request_id IN(
     SELECT id FROM public.pos_fulfillment_requests WHERE cart_id=NEW.cart_id AND invoice_id=NEW.id AND kind IN('backorder','branch_transfer'));
 END IF; RETURN NEW;
END $function$;

CREATE OR REPLACE FUNCTION public.collect_pos_fulfillment_request(p_request_id uuid, p_notes text DEFAULT NULL::text)
 RETURNS uuid
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
DECLARE r public.pos_fulfillment_requests%ROWTYPE; i public.sales_invoices%ROWTYPE;
BEGIN
 PERFORM public._require_sales_staff(); SELECT * INTO r FROM public.pos_fulfillment_requests WHERE id=p_request_id FOR UPDATE;
 IF NOT FOUND OR r.status<>'ready' THEN RAISE EXCEPTION 'ready fulfillment request required'; END IF;
 IF r.invoice_id IS NOT NULL THEN SELECT * INTO i FROM public.sales_invoices WHERE id=r.invoice_id;
  IF NOT FOUND OR i.status<>'posted' OR COALESCE(i.amount_paid,0)+0.01<i.total THEN RAISE EXCEPTION 'order must be fully paid before collection'; END IF;
 END IF;
 IF r.invoice_id IS NULL AND (r.kind<>'branch_transfer' OR r.customer_id IS NOT NULL) THEN RAISE EXCEPTION 'sale/invoice must be linked before customer collection'; END IF;
 UPDATE public.inventory_reservations SET state='released',released_at=now() WHERE pos_fulfillment_request_id=p_request_id AND state IN('active','allocated');
 UPDATE public.pos_fulfillment_requests SET status='collected',collected_at=now(),notes=concat_ws(E'\n',notes,p_notes),updated_at=now() WHERE id=p_request_id;
 RETURN p_request_id;
END $function$;
