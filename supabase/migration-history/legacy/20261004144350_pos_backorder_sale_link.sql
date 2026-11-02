-- Back-orders can be handed over: an arrived back-order is attached to the sale it is sold on, and
-- when that sale's invoice posts with the back-ordered part on it, the request gets the invoice linked
-- (collect_pos_fulfillment_request requires a paid, linked invoice). Before this, a back-order never
-- had an invoice and could only be released.

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
 IF NOT FOUND OR r.kind<>'backorder' OR r.status NOT IN('requested','ready') OR r.invoice_id IS NOT NULL THEN
  RAISE EXCEPTION 'an open back-order without a sale is required';
 END IF;
 SELECT * INTO c FROM public.pos_carts WHERE id=p_cart_id;
 IF NOT FOUND OR c.status<>'open' THEN RAISE EXCEPTION 'open sale required'; END IF;
 UPDATE public.pos_fulfillment_requests
 SET cart_id=p_cart_id, customer_id=COALESCE(customer_id,c.customer_id), updated_at=now()
 WHERE id=p_request_id;
 RETURN p_request_id;
END $function$;

REVOKE ALL ON FUNCTION public.attach_pos_fulfillment_to_cart(uuid, uuid) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.attach_pos_fulfillment_to_cart(uuid, uuid) TO authenticated, service_role;

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
   WHERE r.cart_id=NEW.cart_id AND r.kind='backorder' AND r.invoice_id IS NULL AND r.status IN('requested','ready')
     AND EXISTS(SELECT 1 FROM public.sales_invoice_lines l WHERE l.invoice_id=NEW.id AND l.stock_item_id=r.stock_item_id);
 END IF; RETURN NEW;
END $function$;
