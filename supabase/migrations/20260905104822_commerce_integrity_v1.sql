-- Nissan GTR Auto Production Integrity Programme Rev 1
-- P0 commerce transaction boundary, reservation/idempotency and payment finalization.

CREATE SCHEMA IF NOT EXISTS private;
REVOKE ALL ON SCHEMA private FROM PUBLIC;
GRANT USAGE ON SCHEMA private TO service_role;

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'commerce_order_state') THEN
    CREATE TYPE public.commerce_order_state AS ENUM (
      'checkout_pending','awaiting_payment','payment_processing','paid','allocation_pending',
      'ready_for_pick','picking','packed','ready_for_collection','dispatch_ready','dispatched',
      'delivered','payment_failed','payment_expired','cancelled','partially_fulfilled','refunded','returned'
    );
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'inventory_reservation_state') THEN
    CREATE TYPE public.inventory_reservation_state AS ENUM ('active','allocated','consumed','released','expired');
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'commerce_outbox_state') THEN
    CREATE TYPE public.commerce_outbox_state AS ENUM ('pending','processing','delivered','failed');
  END IF;
END $$;

CREATE TABLE IF NOT EXISTS public.commerce_orders (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  customer_id UUID NOT NULL REFERENCES public.customers (id) ON DELETE RESTRICT,
  cart_id UUID NOT NULL REFERENCES public.pos_carts (id) ON DELETE RESTRICT,
  checkout_request_id UUID NOT NULL,
  warehouse_id UUID NOT NULL REFERENCES public.warehouses (id) ON DELETE RESTRICT,
  fulfillment_mode public.fulfillment_mode NOT NULL,
  currency public.currency_code NOT NULL,
  exchange_rate_applied NUMERIC(18, 8) NOT NULL DEFAULT 1 CHECK (exchange_rate_applied > 0),
  subtotal NUMERIC(18, 2) NOT NULL CHECK (subtotal >= 0),
  total NUMERIC(18, 2) NOT NULL CHECK (total > 0),
  state public.commerce_order_state NOT NULL DEFAULT 'checkout_pending',
  checkout_snapshot JSONB NOT NULL,
  reservation_expires_at TIMESTAMPTZ,
  sales_invoice_id UUID REFERENCES public.sales_invoices (id) ON DELETE RESTRICT,
  active_payment_provider TEXT CHECK (active_payment_provider IS NULL OR active_payment_provider IN ('contipay','paynow','ecocash')),
  active_payment_intent_id UUID,
  settled_payment_entry_id UUID REFERENCES public.payment_entries (id) ON DELETE RESTRICT,
  settled_provider TEXT CHECK (settled_provider IS NULL OR settled_provider IN ('contipay','paynow','ecocash')),
  settled_provider_ref TEXT,
  payment_exception TEXT,
  finalized_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (customer_id, checkout_request_id)
);

CREATE UNIQUE INDEX IF NOT EXISTS commerce_orders_one_live_cart_uidx ON public.commerce_orders (cart_id)
  WHERE state NOT IN ('payment_expired','cancelled','refunded','returned') AND finalized_at IS NULL;
CREATE INDEX IF NOT EXISTS commerce_orders_customer_created_idx ON public.commerce_orders (customer_id, created_at DESC);
CREATE INDEX IF NOT EXISTS commerce_orders_state_idx ON public.commerce_orders (state, updated_at);
CREATE INDEX IF NOT EXISTS commerce_orders_invoice_idx ON public.commerce_orders (sales_invoice_id) WHERE sales_invoice_id IS NOT NULL;

CREATE TABLE IF NOT EXISTS public.inventory_reservations (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  commerce_order_id UUID NOT NULL REFERENCES public.commerce_orders (id) ON DELETE CASCADE,
  stock_item_id UUID NOT NULL REFERENCES public.stock_items (id) ON DELETE RESTRICT,
  warehouse_id UUID NOT NULL REFERENCES public.warehouses (id) ON DELETE RESTRICT,
  required_qty NUMERIC(18, 3) NOT NULL CHECK (required_qty > 0),
  reserved_qty NUMERIC(18, 3) NOT NULL CHECK (reserved_qty > 0 AND reserved_qty <= required_qty),
  consumed_qty NUMERIC(18, 3) NOT NULL DEFAULT 0 CHECK (consumed_qty >= 0 AND consumed_qty <= reserved_qty),
  expires_at TIMESTAMPTZ,
  state public.inventory_reservation_state NOT NULL DEFAULT 'active',
  reservation_reason TEXT NOT NULL DEFAULT 'checkout_payment_hold',
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  consumed_at TIMESTAMPTZ,
  released_at TIMESTAMPTZ,
  UNIQUE (commerce_order_id, stock_item_id, warehouse_id)
);
CREATE INDEX IF NOT EXISTS inventory_reservations_availability_idx ON public.inventory_reservations (stock_item_id, warehouse_id, state, expires_at);
CREATE INDEX IF NOT EXISTS inventory_reservations_order_idx ON public.inventory_reservations (commerce_order_id);

CREATE TABLE IF NOT EXISTS public.commerce_outbox (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(), event_key TEXT NOT NULL UNIQUE, event_type TEXT NOT NULL,
  aggregate_type TEXT NOT NULL DEFAULT 'commerce_order', aggregate_id UUID NOT NULL,
  payload JSONB NOT NULL DEFAULT '{}'::jsonb, state public.commerce_outbox_state NOT NULL DEFAULT 'pending',
  attempts INTEGER NOT NULL DEFAULT 0 CHECK (attempts >= 0), last_error TEXT, next_attempt_at TIMESTAMPTZ,
  delivered_at TIMESTAMPTZ, created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS commerce_outbox_pending_idx ON public.commerce_outbox (state, next_attempt_at, created_at) WHERE state IN ('pending','failed');

CREATE TABLE IF NOT EXISTS public.commerce_payment_exceptions (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(), commerce_order_id UUID NOT NULL REFERENCES public.commerce_orders (id) ON DELETE RESTRICT,
  provider TEXT NOT NULL CHECK (provider IN ('contipay','paynow','ecocash')), provider_intent_id UUID, provider_ref TEXT,
  amount NUMERIC(18,2) NOT NULL CHECK (amount > 0), currency public.currency_code NOT NULL,
  exception_code TEXT NOT NULL, detail TEXT, payment_entry_id UUID REFERENCES public.payment_entries (id) ON DELETE RESTRICT,
  resolved_at TIMESTAMPTZ, resolution TEXT, created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS commerce_payment_exceptions_open_idx ON public.commerce_payment_exceptions (commerce_order_id, created_at DESC) WHERE resolved_at IS NULL;

ALTER TABLE public.pos_carts ADD COLUMN IF NOT EXISTS checkout_locked_at TIMESTAMPTZ,
  ADD COLUMN IF NOT EXISTS checkout_order_id UUID REFERENCES public.commerce_orders (id) ON DELETE SET NULL;
CREATE INDEX IF NOT EXISTS pos_carts_checkout_order_idx ON public.pos_carts (checkout_order_id) WHERE checkout_order_id IS NOT NULL;
ALTER TABLE public.contipay_payment_intents ADD COLUMN IF NOT EXISTS commerce_order_id UUID REFERENCES public.commerce_orders (id) ON DELETE RESTRICT;
ALTER TABLE public.paynow_payment_intents ADD COLUMN IF NOT EXISTS commerce_order_id UUID REFERENCES public.commerce_orders (id) ON DELETE RESTRICT;
ALTER TABLE public.ecocash_payment_intents ADD COLUMN IF NOT EXISTS commerce_order_id UUID REFERENCES public.commerce_orders (id) ON DELETE RESTRICT;
CREATE INDEX IF NOT EXISTS contipay_intents_commerce_order_idx ON public.contipay_payment_intents (commerce_order_id, created_at DESC) WHERE commerce_order_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS paynow_intents_commerce_order_idx ON public.paynow_payment_intents (commerce_order_id, created_at DESC) WHERE commerce_order_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS ecocash_intents_commerce_order_idx ON public.ecocash_payment_intents (commerce_order_id, created_at DESC) WHERE commerce_order_id IS NOT NULL;

ALTER TABLE public.commerce_orders ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.inventory_reservations ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.commerce_outbox ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.commerce_payment_exceptions ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS commerce_orders_customer_select ON public.commerce_orders;
CREATE POLICY commerce_orders_customer_select ON public.commerce_orders FOR SELECT TO authenticated USING (
  EXISTS (SELECT 1 FROM public.customers c WHERE c.id=commerce_orders.customer_id AND c.profile_id=(SELECT auth.uid())));
DROP POLICY IF EXISTS commerce_orders_staff_select ON public.commerce_orders;
CREATE POLICY commerce_orders_staff_select ON public.commerce_orders FOR SELECT TO authenticated USING (
  public.has_staff_role(ARRAY['admin','sales','warehouse','finance','dispatcher']::public.staff_role[]));
DROP POLICY IF EXISTS inventory_reservations_customer_select ON public.inventory_reservations;
CREATE POLICY inventory_reservations_customer_select ON public.inventory_reservations FOR SELECT TO authenticated USING (
  EXISTS (SELECT 1 FROM public.commerce_orders co JOIN public.customers c ON c.id=co.customer_id
    WHERE co.id=inventory_reservations.commerce_order_id AND c.profile_id=(SELECT auth.uid())));
DROP POLICY IF EXISTS inventory_reservations_staff_select ON public.inventory_reservations;
CREATE POLICY inventory_reservations_staff_select ON public.inventory_reservations FOR SELECT TO authenticated USING (
  public.has_staff_role(ARRAY['admin','sales','warehouse','finance','dispatcher']::public.staff_role[]));
DROP POLICY IF EXISTS commerce_outbox_staff_select ON public.commerce_outbox;
CREATE POLICY commerce_outbox_staff_select ON public.commerce_outbox FOR SELECT TO authenticated USING (public.has_staff_role(ARRAY['admin','finance']::public.staff_role[]));
DROP POLICY IF EXISTS commerce_payment_exceptions_staff_select ON public.commerce_payment_exceptions;
CREATE POLICY commerce_payment_exceptions_staff_select ON public.commerce_payment_exceptions FOR SELECT TO authenticated USING (public.has_staff_role(ARRAY['admin','finance','sales']::public.staff_role[]));
GRANT SELECT ON public.commerce_orders,public.inventory_reservations,public.commerce_outbox,public.commerce_payment_exceptions TO authenticated,service_role;
GRANT INSERT,UPDATE,DELETE ON public.commerce_orders,public.inventory_reservations,public.commerce_outbox,public.commerce_payment_exceptions TO service_role;

CREATE OR REPLACE FUNCTION private.enqueue_commerce_event(p_event_key TEXT,p_event_type TEXT,p_aggregate_id UUID,p_payload JSONB DEFAULT '{}'::jsonb)
RETURNS UUID LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
DECLARE v_id UUID; BEGIN
 INSERT INTO public.commerce_outbox(event_key,event_type,aggregate_id,payload) VALUES(p_event_key,p_event_type,p_aggregate_id,COALESCE(p_payload,'{}'::jsonb))
 ON CONFLICT(event_key) DO NOTHING RETURNING id INTO v_id;
 IF v_id IS NULL THEN SELECT id INTO v_id FROM public.commerce_outbox WHERE event_key=p_event_key; END IF; RETURN v_id; END; $$;
REVOKE ALL ON FUNCTION private.enqueue_commerce_event(TEXT,TEXT,UUID,JSONB) FROM PUBLIC,anon,authenticated;
GRANT EXECUTE ON FUNCTION private.enqueue_commerce_event(TEXT,TEXT,UUID,JSONB) TO service_role;

CREATE OR REPLACE FUNCTION public.get_inventory_available_quantity(p_stock_item_id UUID,p_warehouse_id UUID)
RETURNS NUMERIC LANGUAGE sql STABLE SECURITY INVOKER SET search_path='' AS $$
 SELECT GREATEST(COALESCE((SELECT sl.quantity FROM public.stock_levels sl WHERE sl.stock_item_id=p_stock_item_id AND sl.warehouse_id=p_warehouse_id),0)
 - COALESCE((SELECT SUM(GREATEST(r.reserved_qty-r.consumed_qty,0)) FROM public.inventory_reservations r
   WHERE r.stock_item_id=p_stock_item_id AND r.warehouse_id=p_warehouse_id AND r.state IN ('active','allocated')
   AND (r.state='allocated' OR r.expires_at>now())),0),0); $$;
REVOKE ALL ON FUNCTION public.get_inventory_available_quantity(UUID,UUID) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.get_inventory_available_quantity(UUID,UUID) TO authenticated,service_role;

CREATE OR REPLACE FUNCTION private.release_commerce_order(p_order_id UUID,p_terminal_state public.commerce_order_state,p_reason TEXT)
RETURNS void LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
DECLARE v_order public.commerce_orders%ROWTYPE; BEGIN
 SELECT * INTO v_order FROM public.commerce_orders WHERE id=p_order_id FOR UPDATE; IF NOT FOUND THEN RETURN; END IF;
 IF v_order.finalized_at IS NOT NULL OR v_order.settled_payment_entry_id IS NOT NULL THEN RETURN; END IF;
 UPDATE public.inventory_reservations SET state=CASE WHEN p_terminal_state='payment_expired' THEN 'expired'::public.inventory_reservation_state ELSE 'released'::public.inventory_reservation_state END,
   released_at=now() WHERE commerce_order_id=p_order_id AND state='active';
 UPDATE public.commerce_orders SET state=p_terminal_state,payment_exception=COALESCE(p_reason,payment_exception),updated_at=now() WHERE id=p_order_id;
 UPDATE public.pos_carts SET checkout_locked_at=NULL,checkout_order_id=NULL,updated_at=now() WHERE id=v_order.cart_id AND status='open' AND checkout_order_id=p_order_id;
 PERFORM private.enqueue_commerce_event('commerce:'||p_order_id::text||':'||p_terminal_state::text,'commerce.'||p_terminal_state::text,p_order_id,jsonb_build_object('reason',p_reason)); END; $$;
REVOKE ALL ON FUNCTION private.release_commerce_order(UUID,public.commerce_order_state,TEXT) FROM PUBLIC,anon,authenticated;

CREATE OR REPLACE FUNCTION public.expire_commerce_checkouts() RETURNS INTEGER LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
DECLARE r RECORD; v_count INTEGER:=0; BEGIN
 IF auth.role()<>'service_role' THEN RAISE EXCEPTION 'service role required'; END IF;
 FOR r IN SELECT id FROM public.commerce_orders WHERE state IN ('awaiting_payment','payment_processing','payment_failed')
   AND reservation_expires_at IS NOT NULL AND reservation_expires_at<=now() AND settled_payment_entry_id IS NULL FOR UPDATE SKIP LOCKED
 LOOP PERFORM private.release_commerce_order(r.id,'payment_expired','reservation TTL expired'); v_count:=v_count+1; END LOOP; RETURN v_count; END; $$;
REVOKE ALL ON FUNCTION public.expire_commerce_checkouts() FROM PUBLIC,anon,authenticated;
GRANT EXECUTE ON FUNCTION public.expire_commerce_checkouts() TO service_role;

CREATE OR REPLACE FUNCTION public._assert_customer_owns_open_cart(p_cart_id UUID) RETURNS void LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path='' AS $$
DECLARE v_cust UUID:=public._current_customer_id(); BEGIN
 IF v_cust IS NULL THEN RAISE EXCEPTION 'customer profile required'; END IF;
 IF NOT EXISTS(SELECT 1 FROM public.pos_carts c WHERE c.id=p_cart_id AND c.customer_id=v_cust AND c.channel='storefront' AND c.status='open' AND c.checkout_locked_at IS NULL)
 THEN RAISE EXCEPTION 'not authorized for this open storefront cart'; END IF; END; $$;
CREATE OR REPLACE FUNCTION public._require_cart_mutate(p_cart_id UUID) RETURNS void LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path='' AS $$
DECLARE v_locked BOOLEAN; BEGIN
 SELECT (checkout_locked_at IS NOT NULL) INTO v_locked FROM public.pos_carts WHERE id=p_cart_id;
 IF COALESCE(v_locked,false) AND COALESCE(current_setting('app.commerce_finalize',true),'')<>'1' THEN RAISE EXCEPTION 'cart is locked by checkout'; END IF;
 IF COALESCE(current_setting('app.commerce_finalize',true),'')='1' THEN
   IF auth.role()='service_role' OR public.has_staff_role(ARRAY['admin','finance']::public.staff_role[]) THEN RETURN; END IF;
   RAISE EXCEPTION 'commerce finalization role required';
 END IF;
 IF public._storefront_rpc_active() THEN PERFORM public._assert_customer_owns_open_cart(p_cart_id); RETURN; END IF;
 PERFORM public._require_sales_staff(); END; $$;
REVOKE ALL ON FUNCTION public._assert_customer_owns_open_cart(UUID) FROM PUBLIC,anon,authenticated;
REVOKE ALL ON FUNCTION public._require_cart_mutate(UUID) FROM PUBLIC,anon,authenticated;

CREATE OR REPLACE FUNCTION public.create_customer_cart(p_warehouse_id UUID,p_currency public.currency_code DEFAULT 'USD',p_fulfillment_mode public.fulfillment_mode DEFAULT 'dispatch',p_exchange_rate NUMERIC DEFAULT 1)
RETURNS UUID LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
DECLARE v_cust UUID:=public._current_customer_id(); v_id UUID; v_rate NUMERIC; BEGIN
 IF v_cust IS NULL THEN RAISE EXCEPTION 'customer profile required'; END IF; IF p_warehouse_id IS NULL THEN RAISE EXCEPTION 'warehouse_id required'; END IF;
 IF NOT EXISTS(SELECT 1 FROM public.warehouses w WHERE w.id=p_warehouse_id AND w.is_active=true AND COALESCE(w.is_quarantine,false)=false) THEN RAISE EXCEPTION 'saleable warehouse not found or inactive'; END IF;
 v_rate:=CASE WHEN p_currency='USD' THEN COALESCE(p_exchange_rate,1) ELSE p_exchange_rate END; IF v_rate IS NULL OR v_rate<=0 THEN RAISE EXCEPTION 'exchange_rate_applied must be > 0'; END IF;
 INSERT INTO public.pos_carts(document_number,customer_id,warehouse_id,currency,exchange_rate_applied,fulfillment_mode,channel,created_by)
 VALUES(public.next_series_value('CART-'),v_cust,p_warehouse_id,p_currency,v_rate,COALESCE(p_fulfillment_mode,'dispatch'),'storefront',auth.uid()) RETURNING id INTO v_id; RETURN v_id; END; $$;
REVOKE ALL ON FUNCTION public.create_customer_cart(UUID,public.currency_code,public.fulfillment_mode,NUMERIC) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.create_customer_cart(UUID,public.currency_code,public.fulfillment_mode,NUMERIC) TO authenticated,service_role;

CREATE OR REPLACE FUNCTION public.prepare_customer_checkout(p_cart_id UUID,p_checkout_request_id UUID,p_reservation_ttl INTERVAL DEFAULT interval '20 minutes')
RETURNS UUID LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
DECLARE v_cust UUID:=public._current_customer_id(); v_cart public.pos_carts%ROWTYPE; v_order public.commerce_orders%ROWTYPE; v_order_id UUID; v_existing UUID;
 v_exp TIMESTAMPTZ; v_subtotal NUMERIC; v_snapshot JSONB; v_line RECORD; v_physical NUMERIC; v_reserved NUMERIC; BEGIN
 IF v_cust IS NULL THEN RAISE EXCEPTION 'customer profile required'; END IF; IF p_checkout_request_id IS NULL THEN RAISE EXCEPTION 'checkout_request_id required'; END IF;
 IF p_reservation_ttl IS NULL OR p_reservation_ttl<interval '2 minutes' OR p_reservation_ttl>interval '60 minutes' THEN RAISE EXCEPTION 'reservation TTL must be between 2 and 60 minutes'; END IF;
 SELECT id INTO v_existing FROM public.commerce_orders WHERE customer_id=v_cust AND checkout_request_id=p_checkout_request_id; IF v_existing IS NOT NULL THEN RETURN v_existing; END IF;
 SELECT * INTO v_cart FROM public.pos_carts WHERE id=p_cart_id FOR UPDATE;
 IF NOT FOUND OR v_cart.customer_id IS DISTINCT FROM v_cust OR v_cart.channel<>'storefront' OR v_cart.status<>'open' THEN RAISE EXCEPTION 'owned open storefront cart required'; END IF;
 IF v_cart.checkout_order_id IS NOT NULL THEN
   SELECT * INTO v_order FROM public.commerce_orders WHERE id=v_cart.checkout_order_id FOR UPDATE;
   IF FOUND THEN
     IF v_order.finalized_at IS NULL AND v_order.state IN ('awaiting_payment','payment_processing','payment_failed') AND (v_order.reservation_expires_at IS NULL OR v_order.reservation_expires_at>now()) THEN RETURN v_order.id; END IF;
     IF v_order.finalized_at IS NULL AND v_order.settled_payment_entry_id IS NULL AND v_order.state IN ('awaiting_payment','payment_processing','payment_failed') THEN
       PERFORM private.release_commerce_order(v_order.id,'payment_expired','reservation TTL expired before retry'); SELECT * INTO v_cart FROM public.pos_carts WHERE id=p_cart_id FOR UPDATE; END IF;
   END IF;
 END IF;
 SELECT COALESCE(SUM(line_total),0) INTO v_subtotal FROM public.pos_cart_lines WHERE cart_id=p_cart_id; IF v_subtotal<=0 THEN RAISE EXCEPTION 'cart must contain a payable item'; END IF;
 SELECT jsonb_build_object('schema_version',1,'cart_id',v_cart.id,'customer_id',v_cart.customer_id,'warehouse_id',v_cart.warehouse_id,'currency',v_cart.currency,
   'exchange_rate_applied',v_cart.exchange_rate_applied,'fulfillment_mode',v_cart.fulfillment_mode,'subtotal',v_subtotal,'total',v_subtotal,'captured_at',now(),
   'lines',COALESCE(jsonb_agg(jsonb_build_object('line_id',l.id,'stock_item_id',l.stock_item_id,'uom_id',l.uom_id,'qty',l.qty,'qty_base',l.qty_base,
     'unit_price',l.unit_price,'line_total',l.line_total,'is_core_charge',l.is_core_charge,'issues_stock',l.issues_stock,'kit_id',l.kit_id,'kit_line_kind',l.kit_line_kind)
     ORDER BY l.created_at,l.id),'[]'::jsonb)) INTO v_snapshot FROM public.pos_cart_lines l WHERE l.cart_id=p_cart_id;
 v_exp:=now()+p_reservation_ttl;
 INSERT INTO public.commerce_orders(customer_id,cart_id,checkout_request_id,warehouse_id,fulfillment_mode,currency,exchange_rate_applied,subtotal,total,state,checkout_snapshot,reservation_expires_at)
 VALUES(v_cust,p_cart_id,p_checkout_request_id,v_cart.warehouse_id,v_cart.fulfillment_mode,v_cart.currency,v_cart.exchange_rate_applied,v_subtotal,v_subtotal,'checkout_pending',v_snapshot,v_exp) RETURNING id INTO v_order_id;
 FOR v_line IN SELECT stock_item_id,SUM(qty_base)::numeric required_qty FROM public.pos_cart_lines WHERE cart_id=p_cart_id AND COALESCE(issues_stock,true)=true AND is_core_charge=false GROUP BY stock_item_id ORDER BY stock_item_id LOOP
   SELECT quantity INTO v_physical FROM public.stock_levels WHERE stock_item_id=v_line.stock_item_id AND warehouse_id=v_cart.warehouse_id FOR UPDATE; v_physical:=COALESCE(v_physical,0);
   SELECT COALESCE(SUM(GREATEST(r.reserved_qty-r.consumed_qty,0)),0) INTO v_reserved FROM public.inventory_reservations r WHERE r.stock_item_id=v_line.stock_item_id AND r.warehouse_id=v_cart.warehouse_id AND r.state IN ('active','allocated') AND (r.state='allocated' OR r.expires_at>now());
   IF v_physical-v_reserved<v_line.required_qty THEN RAISE EXCEPTION 'insufficient available stock for item %: physical %, reserved %, required %',v_line.stock_item_id,v_physical,v_reserved,v_line.required_qty; END IF;
   INSERT INTO public.inventory_reservations(commerce_order_id,stock_item_id,warehouse_id,required_qty,reserved_qty,expires_at,state,reservation_reason)
   VALUES(v_order_id,v_line.stock_item_id,v_cart.warehouse_id,v_line.required_qty,v_line.required_qty,v_exp,'active','checkout_payment_hold');
 END LOOP;
 UPDATE public.commerce_orders SET state='awaiting_payment',updated_at=now() WHERE id=v_order_id;
 UPDATE public.pos_carts SET checkout_locked_at=now(),checkout_order_id=v_order_id,updated_at=now() WHERE id=p_cart_id;
 PERFORM private.enqueue_commerce_event('commerce:'||v_order_id::text||':prepared','commerce.checkout_prepared',v_order_id,jsonb_build_object('cart_id',p_cart_id,'expires_at',v_exp,'total',v_subtotal)); RETURN v_order_id;
EXCEPTION WHEN unique_violation THEN
 SELECT id INTO v_existing FROM public.commerce_orders WHERE customer_id=v_cust AND checkout_request_id=p_checkout_request_id; IF v_existing IS NOT NULL THEN RETURN v_existing; END IF; RAISE; END; $$;
REVOKE ALL ON FUNCTION public.prepare_customer_checkout(UUID,UUID,INTERVAL) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.prepare_customer_checkout(UUID,UUID,INTERVAL) TO authenticated,service_role;

CREATE OR REPLACE FUNCTION public.checkout_customer_cart(p_cart_id UUID) RETURNS UUID LANGUAGE sql SECURITY DEFINER SET search_path='' AS $$ SELECT public.prepare_customer_checkout(p_cart_id,p_cart_id,interval '20 minutes'); $$;
CREATE OR REPLACE FUNCTION public.checkout_customer_cart(p_cart_id UUID,p_checkout_request_id UUID) RETURNS UUID LANGUAGE sql SECURITY DEFINER SET search_path='' AS $$ SELECT public.prepare_customer_checkout(p_cart_id,p_checkout_request_id,interval '20 minutes'); $$;
REVOKE ALL ON FUNCTION public.checkout_customer_cart(UUID) FROM PUBLIC; REVOKE ALL ON FUNCTION public.checkout_customer_cart(UUID,UUID) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.checkout_customer_cart(UUID) TO authenticated,service_role; GRANT EXECUTE ON FUNCTION public.checkout_customer_cart(UUID,UUID) TO authenticated,service_role;

CREATE OR REPLACE FUNCTION public.get_customer_order(p_invoice_id UUID) RETURNS JSONB LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path='' AS $$
DECLARE v_cust UUID:=public._current_customer_id(); v_order public.commerce_orders%ROWTYPE; v_inv public.sales_invoices%ROWTYPE; v_dn_status public.delivery_note_status; v_pick_status public.pick_list_status;
 v_active_job_id UUID; v_amount_paid NUMERIC:=0; v_doc_number TEXT; BEGIN
 IF v_cust IS NULL THEN RAISE EXCEPTION 'customer profile required'; END IF;
 SELECT * INTO v_order FROM public.commerce_orders WHERE id=p_invoice_id AND customer_id=v_cust;
 IF FOUND THEN
  IF v_order.sales_invoice_id IS NOT NULL THEN
   SELECT * INTO v_inv FROM public.sales_invoices WHERE id=v_order.sales_invoice_id; v_amount_paid:=COALESCE(v_inv.amount_paid,0); v_doc_number:=v_inv.document_number;
   SELECT dn.status INTO v_dn_status FROM public.delivery_notes dn WHERE dn.sales_invoice_id=v_inv.id ORDER BY dn.created_at DESC LIMIT 1;
   SELECT pl.status INTO v_pick_status FROM public.pick_lists pl WHERE pl.sales_invoice_id=v_inv.id ORDER BY pl.created_at DESC LIMIT 1;
   SELECT dj.id INTO v_active_job_id FROM public.delivery_jobs dj JOIN public.delivery_notes dn ON dn.id=dj.delivery_note_id WHERE dn.sales_invoice_id=v_inv.id AND dj.status IN ('pending','dispatched') ORDER BY CASE dj.status WHEN 'dispatched' THEN 0 ELSE 1 END,dj.updated_at DESC,dj.created_at DESC LIMIT 1;
  ELSE SELECT c.document_number INTO v_doc_number FROM public.pos_carts c WHERE c.id=v_order.cart_id; IF v_order.settled_payment_entry_id IS NOT NULL THEN v_amount_paid:=v_order.total; END IF; END IF;
  RETURN jsonb_build_object('invoice_id',COALESCE(v_order.sales_invoice_id,v_order.id),'commerce_order_id',v_order.id,'document_number',v_doc_number,'doc_type','invoice','status',v_order.state,
   'fulfillment_mode',v_order.fulfillment_mode,'currency',v_order.currency,'exchange_rate_applied',v_order.exchange_rate_applied,'subtotal',v_order.subtotal,'total',v_order.total,'amount_paid',v_amount_paid,
   'amount_open',GREATEST(v_order.total-v_amount_paid,0),'cart_id',v_order.cart_id,'posted_at',CASE WHEN v_order.sales_invoice_id IS NULL THEN NULL ELSE v_inv.posted_at END,
   'pick_list_status',v_pick_status,'delivery_note_status',v_dn_status,'active_delivery_job_id',v_active_job_id,'payment_exception',v_order.payment_exception);
 END IF;
 v_inv:=public._assert_customer_owns_invoice(p_invoice_id);
 SELECT dn.status INTO v_dn_status FROM public.delivery_notes dn WHERE dn.sales_invoice_id=v_inv.id ORDER BY dn.created_at DESC LIMIT 1;
 SELECT pl.status INTO v_pick_status FROM public.pick_lists pl WHERE pl.sales_invoice_id=v_inv.id ORDER BY pl.created_at DESC LIMIT 1;
 SELECT dj.id INTO v_active_job_id FROM public.delivery_jobs dj JOIN public.delivery_notes dn ON dn.id=dj.delivery_note_id WHERE dn.sales_invoice_id=v_inv.id AND dj.status IN ('pending','dispatched') ORDER BY CASE dj.status WHEN 'dispatched' THEN 0 ELSE 1 END,dj.updated_at DESC,dj.created_at DESC LIMIT 1;
 RETURN jsonb_build_object('invoice_id',v_inv.id,'document_number',v_inv.document_number,'doc_type',v_inv.doc_type,'status',v_inv.status,'fulfillment_mode',v_inv.fulfillment_mode,'currency',v_inv.currency,
 'exchange_rate_applied',v_inv.exchange_rate_applied,'subtotal',v_inv.subtotal,'total',v_inv.total,'amount_paid',v_inv.amount_paid,'amount_open',v_inv.total-v_inv.amount_paid,'cart_id',v_inv.cart_id,'posted_at',v_inv.posted_at,
 'pick_list_status',v_pick_status,'delivery_note_status',v_dn_status,'active_delivery_job_id',v_active_job_id); END; $$;
REVOKE ALL ON FUNCTION public.get_customer_order(UUID) FROM PUBLIC; GRANT EXECUTE ON FUNCTION public.get_customer_order(UUID) TO authenticated,service_role;

CREATE OR REPLACE FUNCTION public.create_customer_contipay_intent(p_sales_invoice_id UUID,p_method public.contipay_method,p_external_ref TEXT DEFAULT NULL,p_amount NUMERIC DEFAULT NULL,p_settlement_currency public.currency_code DEFAULT NULL,p_settlement_amount NUMERIC DEFAULT NULL,p_settlement_exchange_rate NUMERIC DEFAULT NULL,p_metadata JSONB DEFAULT '{}'::jsonb)
RETURNS UUID LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
DECLARE v_cust UUID:=public._current_customer_id(); v_order public.commerce_orders%ROWTYPE; v_inv public.sales_invoices%ROWTYPE; v_open NUMERIC; v_amount NUMERIC; v_ref TEXT; v_id UUID; BEGIN
 IF v_cust IS NULL THEN RAISE EXCEPTION 'customer profile required'; END IF; SELECT * INTO v_order FROM public.commerce_orders WHERE id=p_sales_invoice_id AND customer_id=v_cust FOR UPDATE;
 IF FOUND THEN
  IF v_order.settled_payment_entry_id IS NOT NULL THEN RAISE EXCEPTION 'commerce order already paid'; END IF;
  IF v_order.reservation_expires_at IS NULL OR v_order.reservation_expires_at<=now() THEN PERFORM private.release_commerce_order(v_order.id,'payment_expired','payment attempted after reservation expiry'); RAISE EXCEPTION 'checkout reservation expired; rebuild checkout'; END IF;
  IF v_order.state NOT IN ('awaiting_payment','payment_processing','payment_failed') THEN RAISE EXCEPTION 'commerce order cannot accept payment in state %',v_order.state; END IF;
  v_amount:=COALESCE(p_amount,v_order.total); IF abs(v_amount-v_order.total)>0.001 THEN RAISE EXCEPTION 'commerce checkout requires full payment of %',v_order.total; END IF;
  SELECT id INTO v_id FROM public.contipay_payment_intents WHERE commerce_order_id=v_order.id AND status IN ('pending','authorized') ORDER BY created_at DESC LIMIT 1; IF v_id IS NOT NULL THEN RETURN v_id; END IF;
  v_ref:=COALESCE(NULLIF(trim(p_external_ref),''),'CP-ORD-'||v_order.id::text||'-'||gen_random_uuid()::text);
  INSERT INTO public.contipay_payment_intents(external_ref,method,customer_id,amount,currency,exchange_rate_applied,settlement_currency,settlement_amount,settlement_exchange_rate,commerce_order_id,metadata,created_by)
  VALUES(v_ref,p_method,v_order.customer_id,v_amount,v_order.currency,v_order.exchange_rate_applied,p_settlement_currency,p_settlement_amount,p_settlement_exchange_rate,v_order.id,COALESCE(p_metadata,'{}'::jsonb)||jsonb_build_object('commerce_order_id',v_order.id,'channel','storefront'),auth.uid()) RETURNING id INTO v_id;
  UPDATE public.commerce_orders SET state='payment_processing',active_payment_provider='contipay',active_payment_intent_id=v_id,updated_at=now() WHERE id=v_order.id;
  PERFORM private.enqueue_commerce_event('commerce:'||v_order.id::text||':contipay:'||v_id::text,'commerce.payment_intent_created',v_order.id,jsonb_build_object('provider','contipay','intent_id',v_id)); RETURN v_id;
 END IF;
 v_inv:=public._assert_customer_owns_invoice(p_sales_invoice_id); IF v_inv.doc_type<>'invoice' OR v_inv.status<>'posted' THEN RAISE EXCEPTION 'only posted invoices can receive payment intents'; END IF;
 v_open:=v_inv.total-v_inv.amount_paid; IF v_open<=0 THEN RAISE EXCEPTION 'invoice has no open balance'; END IF; v_amount:=COALESCE(p_amount,v_open); IF v_amount<=0 OR v_amount>v_open THEN RAISE EXCEPTION 'invalid payment amount'; END IF;
 v_ref:=COALESCE(NULLIF(trim(p_external_ref),''),'CP-CUST-'||v_inv.id::text||'-'||gen_random_uuid()::text);
 INSERT INTO public.contipay_payment_intents(external_ref,method,customer_id,amount,currency,exchange_rate_applied,settlement_currency,settlement_amount,settlement_exchange_rate,metadata,created_by)
 VALUES(v_ref,p_method,v_inv.customer_id,v_amount,v_inv.currency,v_inv.exchange_rate_applied,p_settlement_currency,p_settlement_amount,p_settlement_exchange_rate,COALESCE(p_metadata,'{}'::jsonb)||jsonb_build_object('sales_invoice_id',v_inv.id,'channel','storefront'),auth.uid()) RETURNING id INTO v_id; RETURN v_id; END; $$;

CREATE OR REPLACE FUNCTION public.create_customer_paynow_intent(p_sales_invoice_id UUID,p_method public.paynow_method,p_external_ref TEXT DEFAULT NULL,p_amount NUMERIC DEFAULT NULL,p_settlement_currency public.currency_code DEFAULT NULL,p_settlement_amount NUMERIC DEFAULT NULL,p_settlement_exchange_rate NUMERIC DEFAULT NULL,p_metadata JSONB DEFAULT '{}'::jsonb)
RETURNS UUID LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
DECLARE v_cust UUID:=public._current_customer_id(); v_order public.commerce_orders%ROWTYPE; v_inv public.sales_invoices%ROWTYPE; v_open NUMERIC; v_amount NUMERIC; v_ref TEXT; v_id UUID; BEGIN
 IF v_cust IS NULL THEN RAISE EXCEPTION 'customer profile required'; END IF; SELECT * INTO v_order FROM public.commerce_orders WHERE id=p_sales_invoice_id AND customer_id=v_cust FOR UPDATE;
 IF FOUND THEN
  IF v_order.settled_payment_entry_id IS NOT NULL THEN RAISE EXCEPTION 'commerce order already paid'; END IF;
  IF v_order.reservation_expires_at IS NULL OR v_order.reservation_expires_at<=now() THEN PERFORM private.release_commerce_order(v_order.id,'payment_expired','payment attempted after reservation expiry'); RAISE EXCEPTION 'checkout reservation expired; rebuild checkout'; END IF;
  IF v_order.state NOT IN ('awaiting_payment','payment_processing','payment_failed') THEN RAISE EXCEPTION 'commerce order cannot accept payment in state %',v_order.state; END IF;
  v_amount:=COALESCE(p_amount,v_order.total); IF abs(v_amount-v_order.total)>0.001 THEN RAISE EXCEPTION 'commerce checkout requires full payment of %',v_order.total; END IF;
  SELECT id INTO v_id FROM public.paynow_payment_intents WHERE commerce_order_id=v_order.id AND status IN ('pending','authorized') ORDER BY created_at DESC LIMIT 1; IF v_id IS NOT NULL THEN RETURN v_id; END IF;
  v_ref:=COALESCE(NULLIF(trim(p_external_ref),''),'PN-ORD-'||v_order.id::text||'-'||gen_random_uuid()::text);
  INSERT INTO public.paynow_payment_intents(external_ref,method,customer_id,amount,currency,exchange_rate_applied,settlement_currency,settlement_amount,settlement_exchange_rate,commerce_order_id,metadata,created_by)
  VALUES(v_ref,p_method,v_order.customer_id,v_amount,v_order.currency,v_order.exchange_rate_applied,p_settlement_currency,p_settlement_amount,p_settlement_exchange_rate,v_order.id,COALESCE(p_metadata,'{}'::jsonb)||jsonb_build_object('commerce_order_id',v_order.id,'channel','storefront'),auth.uid()) RETURNING id INTO v_id;
  UPDATE public.commerce_orders SET state='payment_processing',active_payment_provider='paynow',active_payment_intent_id=v_id,updated_at=now() WHERE id=v_order.id;
  PERFORM private.enqueue_commerce_event('commerce:'||v_order.id::text||':paynow:'||v_id::text,'commerce.payment_intent_created',v_order.id,jsonb_build_object('provider','paynow','intent_id',v_id)); RETURN v_id;
 END IF;
 v_inv:=public._assert_customer_owns_invoice(p_sales_invoice_id); IF v_inv.doc_type<>'invoice' OR v_inv.status<>'posted' THEN RAISE EXCEPTION 'only posted invoices can receive payment intents'; END IF;
 v_open:=v_inv.total-v_inv.amount_paid; IF v_open<=0 THEN RAISE EXCEPTION 'invoice has no open balance'; END IF; v_amount:=COALESCE(p_amount,v_open); IF v_amount<=0 OR v_amount>v_open THEN RAISE EXCEPTION 'invalid payment amount'; END IF;
 v_ref:=COALESCE(NULLIF(trim(p_external_ref),''),'PN-CUST-'||v_inv.id::text||'-'||gen_random_uuid()::text);
 INSERT INTO public.paynow_payment_intents(external_ref,method,customer_id,amount,currency,exchange_rate_applied,settlement_currency,settlement_amount,settlement_exchange_rate,metadata,created_by)
 VALUES(v_ref,p_method,v_inv.customer_id,v_amount,v_inv.currency,v_inv.exchange_rate_applied,p_settlement_currency,p_settlement_amount,p_settlement_exchange_rate,COALESCE(p_metadata,'{}'::jsonb)||jsonb_build_object('sales_invoice_id',v_inv.id,'channel','storefront'),auth.uid()) RETURNING id INTO v_id; RETURN v_id; END; $$;

CREATE OR REPLACE FUNCTION public.create_customer_ecocash_intent(p_sales_invoice_id UUID,p_payer_msisdn TEXT,p_payer_mode TEXT DEFAULT 'other',p_external_ref TEXT DEFAULT NULL,p_amount NUMERIC DEFAULT NULL,p_channel TEXT DEFAULT 'web',p_settlement_currency public.currency_code DEFAULT NULL,p_settlement_amount NUMERIC DEFAULT NULL,p_settlement_exchange_rate NUMERIC DEFAULT NULL,p_metadata JSONB DEFAULT '{}'::jsonb)
RETURNS UUID LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
DECLARE v_cust UUID:=public._current_customer_id(); v_order public.commerce_orders%ROWTYPE; v_inv public.sales_invoices%ROWTYPE; v_open NUMERIC; v_amount NUMERIC; v_ref TEXT; v_id UUID; v_msisdn TEXT; BEGIN
 IF v_cust IS NULL THEN RAISE EXCEPTION 'customer profile required'; END IF; IF p_payer_mode IS NULL OR p_payer_mode NOT IN ('whatsapp','saved','other','pos_entered','profile') THEN RAISE EXCEPTION 'invalid payer_mode'; END IF;
 IF p_channel IS NULL OR p_channel NOT IN ('whatsapp_flow','web','pos','ios','android_customer','api') THEN RAISE EXCEPTION 'invalid channel'; END IF;
 v_msisdn:=regexp_replace(trim(COALESCE(p_payer_msisdn,'')),'[^0-9]','','g'); IF v_msisdn~'^0[0-9]{9}$' THEN v_msisdn:='263'||substr(v_msisdn,2); END IF; IF v_msisdn!~'^263[0-9]{9}$' THEN RAISE EXCEPTION 'payer_msisdn must normalize to 263XXXXXXXXX'; END IF;
 SELECT * INTO v_order FROM public.commerce_orders WHERE id=p_sales_invoice_id AND customer_id=v_cust FOR UPDATE;
 IF FOUND THEN
  IF v_order.settled_payment_entry_id IS NOT NULL THEN RAISE EXCEPTION 'commerce order already paid'; END IF;
  IF v_order.reservation_expires_at IS NULL OR v_order.reservation_expires_at<=now() THEN PERFORM private.release_commerce_order(v_order.id,'payment_expired','payment attempted after reservation expiry'); RAISE EXCEPTION 'checkout reservation expired; rebuild checkout'; END IF;
  IF v_order.state NOT IN ('awaiting_payment','payment_processing','payment_failed') THEN RAISE EXCEPTION 'commerce order cannot accept payment in state %',v_order.state; END IF;
  v_amount:=COALESCE(p_amount,v_order.total); IF abs(v_amount-v_order.total)>0.001 THEN RAISE EXCEPTION 'commerce checkout requires full payment of %',v_order.total; END IF;
  SELECT id INTO v_id FROM public.ecocash_payment_intents WHERE commerce_order_id=v_order.id AND status IN ('pending','authorized') ORDER BY created_at DESC LIMIT 1; IF v_id IS NOT NULL THEN RETURN v_id; END IF;
  v_ref:=COALESCE(NULLIF(trim(p_external_ref),''),'EC-ORD-'||v_order.id::text||'-'||gen_random_uuid()::text);
  INSERT INTO public.ecocash_payment_intents(external_ref,payer_msisdn,payer_mode,channel,customer_id,sales_invoice_id,amount,currency,exchange_rate_applied,settlement_currency,settlement_amount,settlement_exchange_rate,commerce_order_id,metadata,created_by)
  VALUES(v_ref,v_msisdn,p_payer_mode,p_channel,v_order.customer_id,NULL,v_amount,v_order.currency,v_order.exchange_rate_applied,p_settlement_currency,p_settlement_amount,p_settlement_exchange_rate,v_order.id,COALESCE(p_metadata,'{}'::jsonb)||jsonb_build_object('commerce_order_id',v_order.id,'channel',p_channel),auth.uid()) RETURNING id INTO v_id;
  UPDATE public.commerce_orders SET state='payment_processing',active_payment_provider='ecocash',active_payment_intent_id=v_id,updated_at=now() WHERE id=v_order.id;
  PERFORM private.enqueue_commerce_event('commerce:'||v_order.id::text||':ecocash:'||v_id::text,'commerce.payment_intent_created',v_order.id,jsonb_build_object('provider','ecocash','intent_id',v_id)); RETURN v_id;
 END IF;
 v_inv:=public._assert_customer_owns_invoice(p_sales_invoice_id); IF v_inv.doc_type<>'invoice' OR v_inv.status<>'posted' THEN RAISE EXCEPTION 'only posted invoices can receive payment intents'; END IF;
 v_open:=v_inv.total-v_inv.amount_paid; IF v_open<=0 THEN RAISE EXCEPTION 'invoice has no open balance'; END IF; v_amount:=COALESCE(p_amount,v_open); IF v_amount<=0 OR v_amount>v_open THEN RAISE EXCEPTION 'invalid payment amount'; END IF;
 v_ref:=COALESCE(NULLIF(trim(p_external_ref),''),'EC-CUST-'||v_inv.id::text||'-'||gen_random_uuid()::text);
 INSERT INTO public.ecocash_payment_intents(external_ref,payer_msisdn,payer_mode,channel,customer_id,sales_invoice_id,amount,currency,exchange_rate_applied,settlement_currency,settlement_amount,settlement_exchange_rate,metadata,created_by)
 VALUES(v_ref,v_msisdn,p_payer_mode,p_channel,v_inv.customer_id,v_inv.id,v_amount,v_inv.currency,v_inv.exchange_rate_applied,p_settlement_currency,p_settlement_amount,p_settlement_exchange_rate,COALESCE(p_metadata,'{}'::jsonb)||jsonb_build_object('sales_invoice_id',v_inv.id,'channel',p_channel),auth.uid()) RETURNING id INTO v_id; RETURN v_id; END; $$;

REVOKE ALL ON FUNCTION public.create_customer_contipay_intent(UUID,public.contipay_method,TEXT,NUMERIC,public.currency_code,NUMERIC,NUMERIC,JSONB) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.create_customer_paynow_intent(UUID,public.paynow_method,TEXT,NUMERIC,public.currency_code,NUMERIC,NUMERIC,JSONB) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.create_customer_ecocash_intent(UUID,TEXT,TEXT,TEXT,NUMERIC,TEXT,public.currency_code,NUMERIC,NUMERIC,JSONB) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.create_customer_contipay_intent(UUID,public.contipay_method,TEXT,NUMERIC,public.currency_code,NUMERIC,NUMERIC,JSONB) TO authenticated,service_role;
GRANT EXECUTE ON FUNCTION public.create_customer_paynow_intent(UUID,public.paynow_method,TEXT,NUMERIC,public.currency_code,NUMERIC,NUMERIC,JSONB) TO authenticated,service_role;
GRANT EXECUTE ON FUNCTION public.create_customer_ecocash_intent(UUID,TEXT,TEXT,TEXT,NUMERIC,TEXT,public.currency_code,NUMERIC,NUMERIC,JSONB) TO authenticated,service_role;

CREATE OR REPLACE FUNCTION public._adjust_stock_level(p_item UUID,p_warehouse UUID,p_delta NUMERIC,p_valuation public.valuation_method,p_unit_cost NUMERIC,p_currency public.currency_code)
RETURNS void LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
DECLARE v_qty NUMERIC; v_before NUMERIC; v_after NUMERIC; v_reserved NUMERIC:=0; v_exempt_order UUID; BEGIN
 SELECT COALESCE(SUM(quantity),0) INTO v_before FROM public.stock_levels WHERE stock_item_id=p_item;
 BEGIN v_exempt_order:=NULLIF(current_setting('app.commerce_fulfill_order_id',true),'')::uuid; EXCEPTION WHEN invalid_text_representation THEN v_exempt_order:=NULL; END;
 INSERT INTO public.stock_levels(stock_item_id,warehouse_id,quantity,valuation_method,unit_cost,currency) VALUES(p_item,p_warehouse,GREATEST(p_delta,0),p_valuation,p_unit_cost,p_currency)
 ON CONFLICT(stock_item_id,warehouse_id) DO UPDATE SET quantity=public.stock_levels.quantity+p_delta,updated_at=now(),unit_cost=CASE
  WHEN p_delta>0 AND public.stock_levels.valuation_method='AVG' THEN CASE WHEN public.stock_levels.quantity+p_delta=0 THEN p_unit_cost ELSE round((COALESCE(public.stock_levels.unit_cost,0)*public.stock_levels.quantity+COALESCE(p_unit_cost,0)*p_delta)/(public.stock_levels.quantity+p_delta),4) END
  WHEN p_delta>0 THEN COALESCE(p_unit_cost,public.stock_levels.unit_cost) ELSE public.stock_levels.unit_cost END RETURNING quantity INTO v_qty;
 IF v_qty<0 THEN RAISE EXCEPTION 'insufficient stock for item % in warehouse %',p_item,p_warehouse; END IF;
 IF p_delta<0 THEN
  SELECT COALESCE(SUM(GREATEST(r.reserved_qty-r.consumed_qty,0)),0) INTO v_reserved FROM public.inventory_reservations r
   WHERE r.stock_item_id=p_item AND r.warehouse_id=p_warehouse AND r.state IN ('active','allocated') AND (r.state='allocated' OR r.expires_at>now()) AND (v_exempt_order IS NULL OR r.commerce_order_id<>v_exempt_order);
  IF v_qty+0.0001<v_reserved THEN RAISE EXCEPTION 'insufficient unreserved stock for item % in warehouse %: resulting %, protected %',p_item,p_warehouse,v_qty,v_reserved; END IF;
 END IF;
 IF p_delta>0 THEN SELECT COALESCE(SUM(quantity),0) INTO v_after FROM public.stock_levels WHERE stock_item_id=p_item; IF v_before<=0 AND v_after>0 THEN PERFORM public._notify_wishlist_back_in_stock(p_item); END IF; END IF; END; $$;
REVOKE ALL ON FUNCTION public._adjust_stock_level(UUID,UUID,NUMERIC,public.valuation_method,NUMERIC,public.currency_code) FROM PUBLIC,anon,authenticated;

DO $$ BEGIN IF to_regprocedure('public.submit_delivery_note_integrity_v1(uuid)') IS NULL THEN ALTER FUNCTION public.submit_delivery_note(UUID) RENAME TO submit_delivery_note_integrity_v1; END IF; END $$;
CREATE OR REPLACE FUNCTION public.submit_delivery_note(p_delivery_note_id UUID) RETURNS UUID LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
DECLARE v_order_id UUID; v_result UUID; BEGIN
 PERFORM public._logistics_begin_rpc(); PERFORM public._require_logistics_staff();
 SELECT co.id INTO v_order_id FROM public.delivery_notes dn JOIN public.commerce_orders co ON co.sales_invoice_id=dn.sales_invoice_id WHERE dn.id=p_delivery_note_id;
 IF v_order_id IS NOT NULL THEN PERFORM set_config('app.commerce_fulfill_order_id',v_order_id::text,true); END IF;
 BEGIN v_result:=public.submit_delivery_note_integrity_v1(p_delivery_note_id); EXCEPTION WHEN OTHERS THEN PERFORM set_config('app.commerce_fulfill_order_id','',true); RAISE; END;
 PERFORM set_config('app.commerce_fulfill_order_id','',true); RETURN v_result; END; $$;
REVOKE ALL ON FUNCTION public.submit_delivery_note(UUID) FROM PUBLIC; GRANT EXECUTE ON FUNCTION public.submit_delivery_note(UUID) TO authenticated,service_role;

CREATE OR REPLACE FUNCTION private.sync_delivery_reservations() RETURNS trigger LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
DECLARE v_order_id UUID; r RECORD; v_remaining NUMERIC; BEGIN
 SELECT co.id INTO v_order_id FROM public.commerce_orders co WHERE co.sales_invoice_id=NEW.sales_invoice_id; IF v_order_id IS NULL THEN RETURN NEW; END IF;
 IF OLD.status='draft' AND NEW.status='submitted' THEN
  FOR r IN SELECT stock_item_id,SUM(qty_base)::numeric qty FROM public.delivery_note_lines WHERE delivery_note_id=NEW.id GROUP BY stock_item_id LOOP
   UPDATE public.inventory_reservations ir SET consumed_qty=LEAST(ir.reserved_qty,ir.consumed_qty+r.qty),consumed_at=CASE WHEN ir.consumed_qty+r.qty>=ir.reserved_qty THEN now() ELSE ir.consumed_at END,
    state=CASE WHEN ir.consumed_qty+r.qty>=ir.reserved_qty THEN 'consumed'::public.inventory_reservation_state ELSE 'allocated'::public.inventory_reservation_state END
    WHERE ir.commerce_order_id=v_order_id AND ir.stock_item_id=r.stock_item_id AND ir.warehouse_id=NEW.warehouse_id;
  END LOOP;
  SELECT COALESCE(SUM(GREATEST(reserved_qty-consumed_qty,0)),0) INTO v_remaining FROM public.inventory_reservations WHERE commerce_order_id=v_order_id;
  UPDATE public.commerce_orders SET state=CASE WHEN v_remaining>0 THEN 'partially_fulfilled'::public.commerce_order_state ELSE 'dispatch_ready'::public.commerce_order_state END,updated_at=now() WHERE id=v_order_id;
 ELSIF OLD.status='submitted' AND NEW.status='cancelled' THEN
  FOR r IN SELECT stock_item_id,SUM(qty_base)::numeric qty FROM public.delivery_note_lines WHERE delivery_note_id=NEW.id GROUP BY stock_item_id LOOP
   UPDATE public.inventory_reservations ir SET consumed_qty=GREATEST(ir.consumed_qty-r.qty,0),consumed_at=NULL,state='allocated'
    WHERE ir.commerce_order_id=v_order_id AND ir.stock_item_id=r.stock_item_id AND ir.warehouse_id=NEW.warehouse_id;
  END LOOP;
  UPDATE public.commerce_orders SET state='allocation_pending',updated_at=now() WHERE id=v_order_id;
 END IF; RETURN NEW; END; $$;
REVOKE ALL ON FUNCTION private.sync_delivery_reservations() FROM PUBLIC,anon,authenticated;
DROP TRIGGER IF EXISTS commerce_delivery_reservation_sync ON public.delivery_notes;
CREATE TRIGGER commerce_delivery_reservation_sync AFTER UPDATE OF status ON public.delivery_notes FOR EACH ROW WHEN (OLD.status IS DISTINCT FROM NEW.status) EXECUTE FUNCTION private.sync_delivery_reservations();

CREATE OR REPLACE FUNCTION private.finalize_commerce_order(p_order_id UUID) RETURNS UUID LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
DECLARE v_order public.commerce_orders%ROWTYPE; v_inv UUID; v_inv_row public.sales_invoices%ROWTYPE; v_total NUMERIC; v_bad BOOLEAN; BEGIN
 SELECT * INTO v_order FROM public.commerce_orders WHERE id=p_order_id FOR UPDATE; IF NOT FOUND THEN RAISE EXCEPTION 'commerce order not found'; END IF;
 IF v_order.sales_invoice_id IS NOT NULL THEN RETURN v_order.sales_invoice_id; END IF; IF v_order.state NOT IN ('awaiting_payment','payment_processing','payment_failed') THEN RAISE EXCEPTION 'commerce order cannot finalize in state %',v_order.state; END IF;
 IF v_order.reservation_expires_at IS NULL OR v_order.reservation_expires_at<=now() THEN RAISE EXCEPTION 'commerce reservation expired'; END IF;
 IF EXISTS(SELECT 1 FROM public.inventory_reservations r WHERE r.commerce_order_id=v_order.id AND r.state='active' AND (r.expires_at IS NULL OR r.expires_at<=now())) THEN RAISE EXCEPTION 'one or more inventory reservations expired'; END IF;
 SELECT COALESCE(SUM(line_total),0) INTO v_total FROM public.pos_cart_lines WHERE cart_id=v_order.cart_id; IF abs(v_total-v_order.total)>0.001 THEN RAISE EXCEPTION 'checkout snapshot total drift detected'; END IF;
 SELECT EXISTS(WITH required AS (SELECT stock_item_id,SUM(qty_base)::numeric qty FROM public.pos_cart_lines WHERE cart_id=v_order.cart_id AND COALESCE(issues_stock,true)=true AND is_core_charge=false GROUP BY stock_item_id)
 SELECT 1 FROM required q LEFT JOIN public.inventory_reservations r ON r.commerce_order_id=v_order.id AND r.stock_item_id=q.stock_item_id AND r.warehouse_id=v_order.warehouse_id
 WHERE r.id IS NULL OR abs(r.reserved_qty-q.qty)>0.0001 OR r.state<>'active') INTO v_bad; IF v_bad THEN RAISE EXCEPTION 'checkout reservation no longer matches cart snapshot'; END IF;
 PERFORM set_config('app.commerce_finalize','1',true); PERFORM set_config('app.commerce_fulfill_order_id',v_order.id::text,true);
 BEGIN v_inv:=public.checkout_pos_cart(v_order.cart_id,NULL,NULL,NULL); EXCEPTION WHEN OTHERS THEN PERFORM set_config('app.commerce_finalize','',true); PERFORM set_config('app.commerce_fulfill_order_id','',true); RAISE; END;
 PERFORM set_config('app.commerce_finalize','',true); PERFORM set_config('app.commerce_fulfill_order_id','',true);
 SELECT * INTO v_inv_row FROM public.sales_invoices WHERE id=v_inv; IF NOT FOUND OR v_inv_row.status<>'posted' OR v_inv_row.doc_type<>'invoice' THEN RAISE EXCEPTION 'commerce finalization did not produce a posted invoice'; END IF;
 IF v_order.fulfillment_mode='immediate' THEN UPDATE public.inventory_reservations SET consumed_qty=reserved_qty,state='consumed',consumed_at=now(),expires_at=NULL WHERE commerce_order_id=v_order.id AND state='active';
 ELSE UPDATE public.inventory_reservations SET state='allocated',expires_at=NULL,reservation_reason='paid_dispatch_allocation' WHERE commerce_order_id=v_order.id AND state='active'; END IF;
 UPDATE public.commerce_orders SET sales_invoice_id=v_inv,finalized_at=now(),updated_at=now() WHERE id=v_order.id;
 PERFORM private.enqueue_commerce_event('commerce:'||v_order.id::text||':invoice:'||v_inv::text,'commerce.order_finalized',v_order.id,jsonb_build_object('sales_invoice_id',v_inv)); RETURN v_inv; END; $$;
REVOKE ALL ON FUNCTION private.finalize_commerce_order(UUID) FROM PUBLIC,anon,authenticated; GRANT EXECUTE ON FUNCTION private.finalize_commerce_order(UUID) TO service_role;

CREATE OR REPLACE FUNCTION private.settle_commerce_payment(p_order_id UUID,p_provider TEXT,p_provider_intent_id UUID,p_provider_ref TEXT,p_amount NUMERIC,p_currency public.currency_code,p_exchange_rate NUMERIC,p_settlement_currency public.currency_code,p_settlement_amount NUMERIC,p_settlement_exchange_rate NUMERIC)
RETURNS UUID LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
DECLARE v_order public.commerce_orders%ROWTYPE; v_inv UUID; v_pe UUID; v_error TEXT; BEGIN
 IF p_provider NOT IN ('contipay','paynow','ecocash') THEN RAISE EXCEPTION 'unsupported provider'; END IF; SELECT * INTO v_order FROM public.commerce_orders WHERE id=p_order_id FOR UPDATE; IF NOT FOUND THEN RAISE EXCEPTION 'commerce order not found'; END IF;
 IF abs(p_amount-v_order.total)>0.001 OR p_currency<>v_order.currency THEN RAISE EXCEPTION 'provider settlement does not match immutable checkout snapshot'; END IF;
 IF v_order.settled_payment_entry_id IS NOT NULL THEN
  v_pe:=public.create_payment_entry(v_order.customer_id,p_provider::public.payment_tender,p_amount,p_currency,p_exchange_rate,format('%s duplicate settlement for commerce order %s',p_provider,v_order.id),p_settlement_currency,p_settlement_amount,p_settlement_exchange_rate);
  PERFORM public.post_payment_entry(v_pe);
  INSERT INTO public.commerce_payment_exceptions(commerce_order_id,provider,provider_intent_id,provider_ref,amount,currency,exception_code,detail,payment_entry_id)
  VALUES(v_order.id,p_provider,p_provider_intent_id,p_provider_ref,p_amount,p_currency,'DUPLICATE_PROVIDER_SETTLEMENT','Order already has a settled payment; duplicate held as unapplied customer credit pending refund/review.',v_pe);
  PERFORM private.enqueue_commerce_event('commerce:'||v_order.id::text||':duplicate-settlement:'||p_provider||':'||p_provider_intent_id::text,'commerce.payment_exception',v_order.id,jsonb_build_object('code','DUPLICATE_PROVIDER_SETTLEMENT','provider',p_provider,'payment_entry_id',v_pe)); RETURN v_pe;
 END IF;
 BEGIN v_inv:=private.finalize_commerce_order(v_order.id); EXCEPTION WHEN OTHERS THEN v_error:=SQLERRM; END;
 IF v_inv IS NULL THEN
  v_pe:=public.create_payment_entry(v_order.customer_id,p_provider::public.payment_tender,p_amount,p_currency,p_exchange_rate,format('%s settlement pending order repair %s',p_provider,v_order.id),p_settlement_currency,p_settlement_amount,p_settlement_exchange_rate);
  PERFORM public.post_payment_entry(v_pe);
  UPDATE public.commerce_orders SET state='allocation_pending',settled_payment_entry_id=v_pe,settled_provider=p_provider,settled_provider_ref=p_provider_ref,payment_exception=v_error,updated_at=now() WHERE id=v_order.id;
  INSERT INTO public.commerce_payment_exceptions(commerce_order_id,provider,provider_intent_id,provider_ref,amount,currency,exception_code,detail,payment_entry_id)
  VALUES(v_order.id,p_provider,p_provider_intent_id,p_provider_ref,p_amount,p_currency,'PAID_ORDER_FINALIZATION_FAILED',v_error,v_pe);
  PERFORM private.enqueue_commerce_event('commerce:'||v_order.id::text||':finalization-failed:'||p_provider_intent_id::text,'commerce.payment_exception',v_order.id,jsonb_build_object('code','PAID_ORDER_FINALIZATION_FAILED','provider',p_provider,'payment_entry_id',v_pe,'detail',v_error)); RETURN v_pe;
 END IF;
 v_pe:=public.create_payment_entry(v_order.customer_id,p_provider::public.payment_tender,p_amount,p_currency,p_exchange_rate,format('%s commerce order %s',p_provider,v_order.id),p_settlement_currency,p_settlement_amount,p_settlement_exchange_rate);
 PERFORM public.allocate_payment(v_pe,jsonb_build_array(jsonb_build_object('sales_invoice_id',v_inv,'amount',p_amount))); PERFORM public.post_payment_entry(v_pe);
 UPDATE public.commerce_orders SET settled_payment_entry_id=v_pe,settled_provider=p_provider,settled_provider_ref=p_provider_ref,
 state=CASE WHEN fulfillment_mode='dispatch' THEN 'allocation_pending'::public.commerce_order_state ELSE 'paid'::public.commerce_order_state END,payment_exception=NULL,updated_at=now() WHERE id=v_order.id;
 PERFORM private.enqueue_commerce_event('commerce:'||v_order.id::text||':paid','commerce.payment_settled',v_order.id,jsonb_build_object('provider',p_provider,'provider_ref',p_provider_ref,'payment_entry_id',v_pe,'sales_invoice_id',v_inv)); RETURN v_pe; END; $$;
REVOKE ALL ON FUNCTION private.settle_commerce_payment(UUID,TEXT,UUID,TEXT,NUMERIC,public.currency_code,NUMERIC,public.currency_code,NUMERIC,NUMERIC) FROM PUBLIC,anon,authenticated;
GRANT EXECUTE ON FUNCTION private.settle_commerce_payment(UUID,TEXT,UUID,TEXT,NUMERIC,public.currency_code,NUMERIC,public.currency_code,NUMERIC,NUMERIC) TO service_role;

CREATE OR REPLACE FUNCTION public.mark_contipay_settled(p_external_ref TEXT,p_payload_hash TEXT,p_provider_ref TEXT DEFAULT NULL,p_allocations JSONB DEFAULT NULL,p_settlement_currency public.currency_code DEFAULT NULL,p_settlement_amount NUMERIC DEFAULT NULL,p_settlement_exchange_rate NUMERIC DEFAULT NULL,p_success BOOLEAN DEFAULT true,p_failure_reason TEXT DEFAULT NULL)
RETURNS UUID LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
DECLARE v_intent public.contipay_payment_intents%ROWTYPE; v_webhook UUID; v_pe UUID; v_hash TEXT; BEGIN
 PERFORM public._require_payments_staff(); v_hash:=NULLIF(trim(COALESCE(p_payload_hash,'')),''); IF v_hash IS NULL THEN RAISE EXCEPTION 'payload_hash required for webhook idempotency'; END IF;
 INSERT INTO public.contipay_webhook_events(external_ref,payload_hash,processed,result_note) VALUES(p_external_ref,v_hash,false,'received') ON CONFLICT(payload_hash) DO NOTHING RETURNING id INTO v_webhook;
 IF v_webhook IS NULL THEN SELECT COALESCE(payment_entry_id,id) INTO v_pe FROM public.contipay_payment_intents WHERE external_ref=p_external_ref; RETURN v_pe; END IF;
 SELECT * INTO v_intent FROM public.contipay_payment_intents WHERE external_ref=p_external_ref FOR UPDATE; IF NOT FOUND THEN UPDATE public.contipay_webhook_events SET processed=true,result_note='unknown external_ref' WHERE id=v_webhook; RAISE EXCEPTION 'contipay intent not found: %',p_external_ref; END IF;
 UPDATE public.contipay_webhook_events SET intent_id=v_intent.id WHERE id=v_webhook; IF v_intent.status='settled' THEN UPDATE public.contipay_webhook_events SET processed=true,result_note='already settled' WHERE id=v_webhook; RETURN COALESCE(v_intent.payment_entry_id,v_intent.id); END IF;
 IF NOT COALESCE(p_success,true) THEN
  UPDATE public.contipay_payment_intents SET status='failed',failure_reason=p_failure_reason,webhook_payload_hash=v_hash,last_webhook_at=now(),provider_ref=COALESCE(p_provider_ref,provider_ref),updated_at=now() WHERE id=v_intent.id;
  IF v_intent.commerce_order_id IS NOT NULL THEN UPDATE public.commerce_orders SET state='payment_failed',payment_exception=p_failure_reason,updated_at=now() WHERE id=v_intent.commerce_order_id AND settled_payment_entry_id IS NULL AND state IN ('awaiting_payment','payment_processing','payment_failed');
   PERFORM private.enqueue_commerce_event('commerce:'||v_intent.commerce_order_id::text||':contipay-failed:'||v_intent.id::text,'commerce.payment_failed',v_intent.commerce_order_id,jsonb_build_object('provider','contipay','reason',p_failure_reason)); END IF;
  PERFORM public.emit_domain_event('payment_failed','contipay:fail:'||v_intent.id::text||':'||v_hash,jsonb_build_object('intent_id',v_intent.id,'external_ref',p_external_ref,'reason',p_failure_reason),auth.uid(),format('GTR Auto: ContiPay failed %s',p_external_ref));
  UPDATE public.contipay_webhook_events SET processed=true,result_note='marked failed' WHERE id=v_webhook; RETURN v_intent.id; END IF;
 IF v_intent.commerce_order_id IS NOT NULL THEN
  v_pe:=private.settle_commerce_payment(v_intent.commerce_order_id,'contipay',v_intent.id,p_provider_ref,v_intent.amount,v_intent.currency,v_intent.exchange_rate_applied,COALESCE(p_settlement_currency,v_intent.settlement_currency),COALESCE(p_settlement_amount,v_intent.settlement_amount),COALESCE(p_settlement_exchange_rate,v_intent.settlement_exchange_rate));
 ELSE
  IF v_intent.status='cancelled' THEN RAISE EXCEPTION 'cannot settle cancelled ContiPay intent'; END IF; IF v_intent.customer_id IS NULL THEN RAISE EXCEPTION 'settled ContiPay intent requires customer_id'; END IF;
  v_pe:=public.create_payment_entry(v_intent.customer_id,'contipay',v_intent.amount,v_intent.currency,v_intent.exchange_rate_applied,format('ContiPay %s',v_intent.external_ref),COALESCE(p_settlement_currency,v_intent.settlement_currency),COALESCE(p_settlement_amount,v_intent.settlement_amount),COALESCE(p_settlement_exchange_rate,v_intent.settlement_exchange_rate));
  IF p_allocations IS NOT NULL AND jsonb_typeof(p_allocations)='array' AND jsonb_array_length(p_allocations)>0 THEN PERFORM public.allocate_payment(v_pe,p_allocations); END IF; PERFORM public.post_payment_entry(v_pe); END IF;
 UPDATE public.contipay_payment_intents SET status='settled',payment_entry_id=v_pe,provider_ref=COALESCE(p_provider_ref,provider_ref),webhook_payload_hash=v_hash,last_webhook_at=now(),settlement_currency=COALESCE(p_settlement_currency,settlement_currency),settlement_amount=COALESCE(p_settlement_amount,settlement_amount),settlement_exchange_rate=COALESCE(p_settlement_exchange_rate,settlement_exchange_rate),updated_at=now() WHERE id=v_intent.id;
 UPDATE public.contipay_webhook_events SET processed=true,result_note='settled' WHERE id=v_webhook; RETURN v_pe; END; $$;

CREATE OR REPLACE FUNCTION public.mark_paynow_settled(p_external_ref TEXT,p_payload_hash TEXT,p_provider_ref TEXT DEFAULT NULL,p_allocations JSONB DEFAULT NULL,p_settlement_currency public.currency_code DEFAULT NULL,p_settlement_amount NUMERIC DEFAULT NULL,p_settlement_exchange_rate NUMERIC DEFAULT NULL,p_success BOOLEAN DEFAULT true,p_failure_reason TEXT DEFAULT NULL)
RETURNS UUID LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
DECLARE v_intent public.paynow_payment_intents%ROWTYPE; v_webhook UUID; v_pe UUID; v_hash TEXT; BEGIN
 PERFORM public._require_payments_staff(); v_hash:=NULLIF(trim(COALESCE(p_payload_hash,'')),''); IF v_hash IS NULL THEN RAISE EXCEPTION 'payload_hash required for webhook idempotency'; END IF;
 INSERT INTO public.paynow_webhook_events(external_ref,payload_hash,processed,result_note) VALUES(p_external_ref,v_hash,false,'received') ON CONFLICT(payload_hash) DO NOTHING RETURNING id INTO v_webhook;
 IF v_webhook IS NULL THEN SELECT COALESCE(payment_entry_id,id) INTO v_pe FROM public.paynow_payment_intents WHERE external_ref=p_external_ref; RETURN v_pe; END IF;
 SELECT * INTO v_intent FROM public.paynow_payment_intents WHERE external_ref=p_external_ref FOR UPDATE; IF NOT FOUND THEN UPDATE public.paynow_webhook_events SET processed=true,result_note='unknown external_ref' WHERE id=v_webhook; RAISE EXCEPTION 'paynow intent not found: %',p_external_ref; END IF;
 UPDATE public.paynow_webhook_events SET intent_id=v_intent.id WHERE id=v_webhook; IF v_intent.status='settled' THEN UPDATE public.paynow_webhook_events SET processed=true,result_note='already settled' WHERE id=v_webhook; RETURN COALESCE(v_intent.payment_entry_id,v_intent.id); END IF;
 IF NOT COALESCE(p_success,true) THEN
  UPDATE public.paynow_payment_intents SET status='failed',failure_reason=p_failure_reason,webhook_payload_hash=v_hash,last_webhook_at=now(),provider_ref=COALESCE(p_provider_ref,provider_ref),updated_at=now() WHERE id=v_intent.id;
  IF v_intent.commerce_order_id IS NOT NULL THEN UPDATE public.commerce_orders SET state='payment_failed',payment_exception=p_failure_reason,updated_at=now() WHERE id=v_intent.commerce_order_id AND settled_payment_entry_id IS NULL AND state IN ('awaiting_payment','payment_processing','payment_failed');
   PERFORM private.enqueue_commerce_event('commerce:'||v_intent.commerce_order_id::text||':paynow-failed:'||v_intent.id::text,'commerce.payment_failed',v_intent.commerce_order_id,jsonb_build_object('provider','paynow','reason',p_failure_reason)); END IF;
  PERFORM public.emit_domain_event('payment_failed','paynow:fail:'||v_intent.id::text||':'||v_hash,jsonb_build_object('intent_id',v_intent.id,'external_ref',p_external_ref,'reason',p_failure_reason),auth.uid(),format('GTR Auto: Paynow failed %s',p_external_ref));
  UPDATE public.paynow_webhook_events SET processed=true,result_note='marked failed' WHERE id=v_webhook; RETURN v_intent.id; END IF;
 IF v_intent.commerce_order_id IS NOT NULL THEN
  v_pe:=private.settle_commerce_payment(v_intent.commerce_order_id,'paynow',v_intent.id,p_provider_ref,v_intent.amount,v_intent.currency,v_intent.exchange_rate_applied,COALESCE(p_settlement_currency,v_intent.settlement_currency),COALESCE(p_settlement_amount,v_intent.settlement_amount),COALESCE(p_settlement_exchange_rate,v_intent.settlement_exchange_rate));
 ELSE
  IF v_intent.status='cancelled' THEN RAISE EXCEPTION 'cannot settle cancelled Paynow intent'; END IF; IF v_intent.customer_id IS NULL THEN RAISE EXCEPTION 'settled Paynow intent requires customer_id'; END IF;
  v_pe:=public.create_payment_entry(v_intent.customer_id,'paynow',v_intent.amount,v_intent.currency,v_intent.exchange_rate_applied,format('Paynow %s',v_intent.external_ref),COALESCE(p_settlement_currency,v_intent.settlement_currency),COALESCE(p_settlement_amount,v_intent.settlement_amount),COALESCE(p_settlement_exchange_rate,v_intent.settlement_exchange_rate));
  IF p_allocations IS NOT NULL AND jsonb_typeof(p_allocations)='array' AND jsonb_array_length(p_allocations)>0 THEN PERFORM public.allocate_payment(v_pe,p_allocations); END IF; PERFORM public.post_payment_entry(v_pe); END IF;
 UPDATE public.paynow_payment_intents SET status='settled',payment_entry_id=v_pe,provider_ref=COALESCE(p_provider_ref,provider_ref),webhook_payload_hash=v_hash,last_webhook_at=now(),settlement_currency=COALESCE(p_settlement_currency,settlement_currency),settlement_amount=COALESCE(p_settlement_amount,settlement_amount),settlement_exchange_rate=COALESCE(p_settlement_exchange_rate,settlement_exchange_rate),updated_at=now() WHERE id=v_intent.id;
 UPDATE public.paynow_webhook_events SET processed=true,result_note='settled' WHERE id=v_webhook; RETURN v_pe; END; $$;

CREATE OR REPLACE FUNCTION public.mark_ecocash_settled(p_external_ref TEXT,p_payload_hash TEXT,p_provider_ref TEXT DEFAULT NULL,p_allocations JSONB DEFAULT NULL,p_settlement_currency public.currency_code DEFAULT NULL,p_settlement_amount NUMERIC DEFAULT NULL,p_settlement_exchange_rate NUMERIC DEFAULT NULL,p_success BOOLEAN DEFAULT true,p_failure_reason TEXT DEFAULT NULL)
RETURNS UUID LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
DECLARE v_intent public.ecocash_payment_intents%ROWTYPE; v_webhook UUID; v_pe UUID; v_hash TEXT; BEGIN
 PERFORM public._require_payments_staff(); v_hash:=NULLIF(trim(COALESCE(p_payload_hash,'')),''); IF v_hash IS NULL THEN RAISE EXCEPTION 'payload_hash required for webhook idempotency'; END IF;
 INSERT INTO public.ecocash_webhook_events(external_ref,payload_hash,processed,result_note) VALUES(p_external_ref,v_hash,false,'received') ON CONFLICT(payload_hash) DO NOTHING RETURNING id INTO v_webhook;
 IF v_webhook IS NULL THEN SELECT COALESCE(payment_entry_id,id) INTO v_pe FROM public.ecocash_payment_intents WHERE external_ref=p_external_ref; RETURN v_pe; END IF;
 SELECT * INTO v_intent FROM public.ecocash_payment_intents WHERE external_ref=trim(p_external_ref) FOR UPDATE; IF NOT FOUND THEN UPDATE public.ecocash_webhook_events SET processed=true,result_note='unknown external_ref' WHERE id=v_webhook; RAISE EXCEPTION 'ecocash intent not found: %',p_external_ref; END IF;
 UPDATE public.ecocash_webhook_events SET intent_id=v_intent.id WHERE id=v_webhook; IF v_intent.status='settled' THEN UPDATE public.ecocash_webhook_events SET processed=true,result_note='already settled' WHERE id=v_webhook; RETURN COALESCE(v_intent.payment_entry_id,v_intent.id); END IF;
 IF NOT COALESCE(p_success,true) THEN
  UPDATE public.ecocash_payment_intents SET status='failed',failure_reason=p_failure_reason,webhook_payload_hash=v_hash,last_webhook_at=now(),provider_ref=COALESCE(p_provider_ref,provider_ref),updated_at=now() WHERE id=v_intent.id;
  IF v_intent.commerce_order_id IS NOT NULL THEN UPDATE public.commerce_orders SET state='payment_failed',payment_exception=p_failure_reason,updated_at=now() WHERE id=v_intent.commerce_order_id AND settled_payment_entry_id IS NULL AND state IN ('awaiting_payment','payment_processing','payment_failed');
   PERFORM private.enqueue_commerce_event('commerce:'||v_intent.commerce_order_id::text||':ecocash-failed:'||v_intent.id::text,'commerce.payment_failed',v_intent.commerce_order_id,jsonb_build_object('provider','ecocash','reason',p_failure_reason)); END IF;
  IF v_intent.whatsapp_flow_order_id IS NOT NULL THEN UPDATE public.whatsapp_flow_orders SET status='FAILED',payment_reference=COALESCE(p_provider_ref,payment_reference),updated_at=now() WHERE id=v_intent.whatsapp_flow_order_id AND status='PENDING'; END IF;
  UPDATE public.ecocash_webhook_events SET processed=true,result_note='marked failed' WHERE id=v_webhook; RETURN v_intent.id; END IF;
 IF v_intent.commerce_order_id IS NOT NULL THEN
  v_pe:=private.settle_commerce_payment(v_intent.commerce_order_id,'ecocash',v_intent.id,p_provider_ref,v_intent.amount,v_intent.currency,v_intent.exchange_rate_applied,COALESCE(p_settlement_currency,v_intent.settlement_currency),COALESCE(p_settlement_amount,v_intent.settlement_amount),COALESCE(p_settlement_exchange_rate,v_intent.settlement_exchange_rate));
 ELSIF v_intent.customer_id IS NOT NULL AND (p_allocations IS NOT NULL OR v_intent.sales_invoice_id IS NOT NULL OR v_intent.channel IN ('web','pos','ios','android_customer')) THEN
  IF v_intent.status='cancelled' THEN RAISE EXCEPTION 'cannot settle cancelled EcoCash intent'; END IF;
  v_pe:=public.create_payment_entry(v_intent.customer_id,'ecocash',v_intent.amount,v_intent.currency,v_intent.exchange_rate_applied,format('EcoCash %s',v_intent.external_ref),COALESCE(p_settlement_currency,v_intent.settlement_currency),COALESCE(p_settlement_amount,v_intent.settlement_amount),COALESCE(p_settlement_exchange_rate,v_intent.settlement_exchange_rate));
  IF p_allocations IS NOT NULL AND jsonb_typeof(p_allocations)='array' AND jsonb_array_length(p_allocations)>0 THEN PERFORM public.allocate_payment(v_pe,p_allocations);
  ELSIF v_intent.sales_invoice_id IS NOT NULL THEN PERFORM public.allocate_payment(v_pe,jsonb_build_array(jsonb_build_object('sales_invoice_id',v_intent.sales_invoice_id,'amount',v_intent.amount))); END IF; PERFORM public.post_payment_entry(v_pe); END IF;
 UPDATE public.ecocash_payment_intents SET status='settled',payment_entry_id=COALESCE(v_pe,payment_entry_id),provider_ref=COALESCE(p_provider_ref,provider_ref),webhook_payload_hash=v_hash,last_webhook_at=now(),settlement_currency=COALESCE(p_settlement_currency,settlement_currency),settlement_amount=COALESCE(p_settlement_amount,settlement_amount),settlement_exchange_rate=COALESCE(p_settlement_exchange_rate,settlement_exchange_rate),updated_at=now() WHERE id=v_intent.id;
 IF v_intent.whatsapp_flow_order_id IS NOT NULL THEN UPDATE public.whatsapp_flow_orders SET status='PAID',payment_reference=COALESCE(p_provider_ref,payment_reference),updated_at=now() WHERE id=v_intent.whatsapp_flow_order_id AND status IS DISTINCT FROM 'PAID'; END IF;
 UPDATE public.ecocash_webhook_events SET processed=true,result_note='settled' WHERE id=v_webhook; RETURN COALESCE(v_pe,v_intent.id); END; $$;

REVOKE ALL ON FUNCTION public.mark_contipay_settled(TEXT,TEXT,TEXT,JSONB,public.currency_code,NUMERIC,NUMERIC,BOOLEAN,TEXT) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.mark_paynow_settled(TEXT,TEXT,TEXT,JSONB,public.currency_code,NUMERIC,NUMERIC,BOOLEAN,TEXT) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.mark_ecocash_settled(TEXT,TEXT,TEXT,JSONB,public.currency_code,NUMERIC,NUMERIC,BOOLEAN,TEXT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.mark_contipay_settled(TEXT,TEXT,TEXT,JSONB,public.currency_code,NUMERIC,NUMERIC,BOOLEAN,TEXT) TO service_role;
GRANT EXECUTE ON FUNCTION public.mark_paynow_settled(TEXT,TEXT,TEXT,JSONB,public.currency_code,NUMERIC,NUMERIC,BOOLEAN,TEXT) TO service_role;
GRANT EXECUTE ON FUNCTION public.mark_ecocash_settled(TEXT,TEXT,TEXT,JSONB,public.currency_code,NUMERIC,NUMERIC,BOOLEAN,TEXT) TO service_role;

COMMENT ON TABLE public.commerce_orders IS 'Canonical customer commerce order. Immutable checkout snapshot precedes provider payment; invoice/stock/accounting finalization follows verified settlement.';
COMMENT ON TABLE public.inventory_reservations IS 'Stock protection for pending and paid-dispatch commerce orders. Availability = physical - outstanding active/allocated reservation.';
COMMENT ON TABLE public.commerce_payment_exceptions IS 'Paid-but-not-finalized and duplicate-settlement recovery queue. Never silently discard a provider settlement.';
COMMENT ON FUNCTION public.checkout_customer_cart(UUID) IS 'Backward-compatible storefront checkout: stage immutable commerce order + reserve stock. Does not post invoice/journal or issue stock.';