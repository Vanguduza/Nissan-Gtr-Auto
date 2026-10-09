-- Exported from the hosted project's supabase_migrations.schema_migrations (20260905133838 bind_checkout_request_id_to_original_cart).
-- Source of record for what production ran; see supabase/live-history/README.md.

CREATE OR REPLACE FUNCTION public.prepare_customer_checkout(
  p_cart_id uuid,
  p_checkout_request_id uuid,
  p_reservation_ttl interval DEFAULT '00:20:00'::interval
)
RETURNS uuid
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO ''
AS $function$
DECLARE
  v_cust uuid := public._current_customer_id();
  v_cart public.pos_carts%ROWTYPE;
  v_order public.commerce_orders%ROWTYPE;
  v_order_id uuid;
  v_existing uuid;
  v_existing_cart uuid;
  v_exp timestamptz;
  v_subtotal numeric;
  v_snapshot jsonb;
  v_line record;
  v_physical numeric;
  v_reserved numeric;
BEGIN
  IF v_cust IS NULL THEN RAISE EXCEPTION 'customer profile required'; END IF;
  IF p_checkout_request_id IS NULL THEN RAISE EXCEPTION 'checkout_request_id required'; END IF;
  IF p_reservation_ttl IS NULL OR p_reservation_ttl < interval '2 minutes' OR p_reservation_ttl > interval '60 minutes' THEN
    RAISE EXCEPTION 'reservation TTL must be between 2 and 60 minutes';
  END IF;

  SELECT id, cart_id INTO v_existing, v_existing_cart
  FROM public.commerce_orders
  WHERE customer_id = v_cust AND checkout_request_id = p_checkout_request_id;

  IF v_existing IS NOT NULL THEN
    IF v_existing_cart IS DISTINCT FROM p_cart_id THEN
      RAISE EXCEPTION 'checkout_request_id already used for a different cart';
    END IF;
    RETURN v_existing;
  END IF;

  SELECT * INTO v_cart
  FROM public.pos_carts
  WHERE id = p_cart_id
  FOR UPDATE;

  IF NOT FOUND OR v_cart.customer_id IS DISTINCT FROM v_cust OR v_cart.channel <> 'storefront' OR v_cart.status <> 'open' THEN
    RAISE EXCEPTION 'owned open storefront cart required';
  END IF;

  IF v_cart.checkout_order_id IS NOT NULL THEN
    SELECT * INTO v_order
    FROM public.commerce_orders
    WHERE id = v_cart.checkout_order_id
    FOR UPDATE;

    IF FOUND THEN
      IF v_order.finalized_at IS NULL
         AND v_order.state IN ('awaiting_payment','payment_processing','payment_failed')
         AND (v_order.reservation_expires_at IS NULL OR v_order.reservation_expires_at > now()) THEN
        RETURN v_order.id;
      END IF;

      IF v_order.finalized_at IS NULL
         AND v_order.settled_payment_entry_id IS NULL
         AND v_order.state IN ('awaiting_payment','payment_processing','payment_failed') THEN
        PERFORM private.release_commerce_order(v_order.id,'payment_expired','reservation TTL expired before retry');
        SELECT * INTO v_cart FROM public.pos_carts WHERE id=p_cart_id FOR UPDATE;
      END IF;
    END IF;
  END IF;

  SELECT COALESCE(SUM(line_total),0) INTO v_subtotal
  FROM public.pos_cart_lines
  WHERE cart_id = p_cart_id;

  IF v_subtotal <= 0 THEN RAISE EXCEPTION 'cart must contain a payable item'; END IF;

  SELECT jsonb_build_object(
    'schema_version',1,
    'cart_id',v_cart.id,
    'customer_id',v_cart.customer_id,
    'warehouse_id',v_cart.warehouse_id,
    'currency',v_cart.currency,
    'exchange_rate_applied',v_cart.exchange_rate_applied,
    'fulfillment_mode',v_cart.fulfillment_mode,
    'subtotal',v_subtotal,
    'total',v_subtotal,
    'captured_at',now(),
    'lines',COALESCE(jsonb_agg(jsonb_build_object(
      'line_id',l.id,
      'stock_item_id',l.stock_item_id,
      'uom_id',l.uom_id,
      'qty',l.qty,
      'qty_base',l.qty_base,
      'unit_price',l.unit_price,
      'line_total',l.line_total,
      'is_core_charge',l.is_core_charge,
      'issues_stock',l.issues_stock,
      'kit_id',l.kit_id,
      'kit_line_kind',l.kit_line_kind
    ) ORDER BY l.created_at,l.id),'[]'::jsonb)
  ) INTO v_snapshot
  FROM public.pos_cart_lines l
  WHERE l.cart_id=p_cart_id;

  v_exp := now() + p_reservation_ttl;

  INSERT INTO public.commerce_orders(
    customer_id,cart_id,checkout_request_id,warehouse_id,fulfillment_mode,
    currency,exchange_rate_applied,subtotal,total,state,checkout_snapshot,reservation_expires_at
  ) VALUES(
    v_cust,p_cart_id,p_checkout_request_id,v_cart.warehouse_id,v_cart.fulfillment_mode,
    v_cart.currency,v_cart.exchange_rate_applied,v_subtotal,v_subtotal,'checkout_pending',v_snapshot,v_exp
  ) RETURNING id INTO v_order_id;

  FOR v_line IN
    SELECT stock_item_id,SUM(qty_base)::numeric required_qty
    FROM public.pos_cart_lines
    WHERE cart_id=p_cart_id AND COALESCE(issues_stock,true)=true AND is_core_charge=false
    GROUP BY stock_item_id
    ORDER BY stock_item_id
  LOOP
    SELECT quantity INTO v_physical
    FROM public.stock_levels
    WHERE stock_item_id=v_line.stock_item_id AND warehouse_id=v_cart.warehouse_id
    FOR UPDATE;
    v_physical := COALESCE(v_physical,0);

    SELECT COALESCE(SUM(GREATEST(r.reserved_qty-r.consumed_qty,0)),0)
    INTO v_reserved
    FROM public.inventory_reservations r
    WHERE r.stock_item_id=v_line.stock_item_id
      AND r.warehouse_id=v_cart.warehouse_id
      AND r.state IN ('active','allocated')
      AND (r.state='allocated' OR r.expires_at>now());

    IF v_physical-v_reserved < v_line.required_qty THEN
      RAISE EXCEPTION 'insufficient available stock for item %: physical %, reserved %, required %',
        v_line.stock_item_id,v_physical,v_reserved,v_line.required_qty;
    END IF;

    INSERT INTO public.inventory_reservations(
      commerce_order_id,stock_item_id,warehouse_id,required_qty,reserved_qty,
      expires_at,state,reservation_reason
    ) VALUES(
      v_order_id,v_line.stock_item_id,v_cart.warehouse_id,v_line.required_qty,v_line.required_qty,
      v_exp,'active','checkout_payment_hold'
    );
  END LOOP;

  UPDATE public.commerce_orders SET state='awaiting_payment',updated_at=now() WHERE id=v_order_id;
  UPDATE public.pos_carts SET checkout_locked_at=now(),checkout_order_id=v_order_id,updated_at=now() WHERE id=p_cart_id;

  PERFORM private.enqueue_commerce_event(
    'commerce:'||v_order_id::text||':prepared',
    'commerce.checkout_prepared',
    v_order_id,
    jsonb_build_object('cart_id',p_cart_id,'expires_at',v_exp,'total',v_subtotal)
  );

  RETURN v_order_id;
EXCEPTION WHEN unique_violation THEN
  SELECT id, cart_id INTO v_existing, v_existing_cart
  FROM public.commerce_orders
  WHERE customer_id=v_cust AND checkout_request_id=p_checkout_request_id;

  IF v_existing IS NOT NULL THEN
    IF v_existing_cart IS DISTINCT FROM p_cart_id THEN
      RAISE EXCEPTION 'checkout_request_id already used for a different cart';
    END IF;
    RETURN v_existing;
  END IF;
  RAISE;
END;
$function$;
