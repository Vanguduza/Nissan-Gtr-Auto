ALTER TABLE public.commerce_orders
  DROP CONSTRAINT IF EXISTS commerce_orders_settled_provider_check;
ALTER TABLE public.commerce_orders
  ADD CONSTRAINT commerce_orders_settled_provider_check
  CHECK (settled_provider IS NULL OR settled_provider IN ('contipay','paynow','ecocash','cash','bank'));

ALTER TABLE public.commerce_payment_exceptions
  DROP CONSTRAINT IF EXISTS commerce_payment_exceptions_provider_check;
ALTER TABLE public.commerce_payment_exceptions
  ADD CONSTRAINT commerce_payment_exceptions_provider_check
  CHECK (provider IN ('contipay','paynow','ecocash','cash','bank'));

CREATE TABLE IF NOT EXISTS public.commerce_manual_payment_requests (
  id UUID PRIMARY KEY,
  commerce_order_id UUID NOT NULL REFERENCES public.commerce_orders(id) ON DELETE RESTRICT,
  tender public.payment_tender NOT NULL CHECK (tender IN ('cash','bank')),
  reference TEXT,
  payment_entry_id UUID REFERENCES public.payment_entries(id) ON DELETE RESTRICT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  completed_at TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS commerce_manual_payment_requests_order_idx
  ON public.commerce_manual_payment_requests(commerce_order_id, created_at DESC);

ALTER TABLE public.commerce_manual_payment_requests ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS commerce_manual_payment_requests_staff_select ON public.commerce_manual_payment_requests;
CREATE POLICY commerce_manual_payment_requests_staff_select
  ON public.commerce_manual_payment_requests
  FOR SELECT TO authenticated
  USING (public.has_staff_role(ARRAY['admin','finance','sales']::public.staff_role[]));
GRANT SELECT ON public.commerce_manual_payment_requests TO authenticated, service_role;
GRANT INSERT,UPDATE,DELETE ON public.commerce_manual_payment_requests TO service_role;

CREATE OR REPLACE FUNCTION public._require_cart_mutate(p_cart_id UUID)
RETURNS void
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
  v_locked BOOLEAN;
BEGIN
  SELECT (checkout_locked_at IS NOT NULL)
    INTO v_locked
  FROM public.pos_carts
  WHERE id = p_cart_id;

  IF COALESCE(v_locked,false)
     AND COALESCE(current_setting('app.commerce_finalize',true),'') <> '1' THEN
    RAISE EXCEPTION 'cart is locked by checkout';
  END IF;

  IF COALESCE(current_setting('app.commerce_finalize',true),'') = '1' THEN
    IF COALESCE(auth.jwt()->>'role','') = 'service_role'
       OR public.has_staff_role(ARRAY['admin','finance','sales']::public.staff_role[]) THEN
      RETURN;
    END IF;
    RAISE EXCEPTION 'commerce finalization role required';
  END IF;

  IF public._storefront_rpc_active() THEN
    PERFORM public._assert_customer_owns_open_cart(p_cart_id);
    RETURN;
  END IF;
  PERFORM public._require_sales_staff();
END;
$$;
REVOKE ALL ON FUNCTION public._require_cart_mutate(UUID) FROM PUBLIC,anon,authenticated;

CREATE OR REPLACE FUNCTION private.settle_commerce_payment(
  p_order_id UUID,
  p_provider TEXT,
  p_provider_intent_id UUID,
  p_provider_ref TEXT,
  p_amount NUMERIC,
  p_currency public.currency_code,
  p_exchange_rate NUMERIC,
  p_settlement_currency public.currency_code,
  p_settlement_amount NUMERIC,
  p_settlement_exchange_rate NUMERIC
)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
  v_order public.commerce_orders%ROWTYPE;
  v_inv UUID;
  v_pe UUID;
  v_error TEXT;
BEGIN
  IF p_provider NOT IN ('contipay','paynow','ecocash','cash','bank') THEN
    RAISE EXCEPTION 'unsupported provider';
  END IF;

  SELECT * INTO v_order
  FROM public.commerce_orders
  WHERE id=p_order_id
  FOR UPDATE;
  IF NOT FOUND THEN RAISE EXCEPTION 'commerce order not found'; END IF;

  IF abs(p_amount-v_order.total)>0.001 OR p_currency<>v_order.currency THEN
    RAISE EXCEPTION 'payment does not match immutable checkout snapshot';
  END IF;

  IF v_order.settled_payment_entry_id IS NOT NULL THEN
    v_pe:=public.create_payment_entry(v_order.customer_id,p_provider::public.payment_tender,p_amount,p_currency,p_exchange_rate,format('%s duplicate settlement for commerce order %s',p_provider,v_order.id),p_settlement_currency,p_settlement_amount,p_settlement_exchange_rate);
    PERFORM public.post_payment_entry(v_pe);
    INSERT INTO public.commerce_payment_exceptions(commerce_order_id,provider,provider_intent_id,provider_ref,amount,currency,exception_code,detail,payment_entry_id)
    VALUES(v_order.id,p_provider,p_provider_intent_id,p_provider_ref,p_amount,p_currency,'DUPLICATE_PROVIDER_SETTLEMENT','Order already has a settled payment; duplicate held as unapplied customer credit pending refund/review.',v_pe);
    PERFORM private.enqueue_commerce_event('commerce:'||v_order.id::text||':duplicate-settlement:'||p_provider||':'||p_provider_intent_id::text,'commerce.payment_exception',v_order.id,jsonb_build_object('code','DUPLICATE_PROVIDER_SETTLEMENT','provider',p_provider,'payment_entry_id',v_pe));
    RETURN v_pe;
  END IF;

  BEGIN
    v_inv:=private.finalize_commerce_order(v_order.id);
  EXCEPTION WHEN OTHERS THEN
    v_error:=SQLERRM;
  END;

  IF v_inv IS NULL THEN
    v_pe:=public.create_payment_entry(v_order.customer_id,p_provider::public.payment_tender,p_amount,p_currency,p_exchange_rate,format('%s settlement pending order repair %s',p_provider,v_order.id),p_settlement_currency,p_settlement_amount,p_settlement_exchange_rate);
    PERFORM public.post_payment_entry(v_pe);
    UPDATE public.commerce_orders
      SET state='allocation_pending',settled_payment_entry_id=v_pe,settled_provider=p_provider,settled_provider_ref=p_provider_ref,payment_exception=v_error,updated_at=now()
      WHERE id=v_order.id;
    INSERT INTO public.commerce_payment_exceptions(commerce_order_id,provider,provider_intent_id,provider_ref,amount,currency,exception_code,detail,payment_entry_id)
    VALUES(v_order.id,p_provider,p_provider_intent_id,p_provider_ref,p_amount,p_currency,'PAID_ORDER_FINALIZATION_FAILED',v_error,v_pe);
    PERFORM private.enqueue_commerce_event('commerce:'||v_order.id::text||':finalization-failed:'||p_provider_intent_id::text,'commerce.payment_exception',v_order.id,jsonb_build_object('code','PAID_ORDER_FINALIZATION_FAILED','provider',p_provider,'payment_entry_id',v_pe,'detail',v_error));
    RETURN v_pe;
  END IF;

  v_pe:=public.create_payment_entry(v_order.customer_id,p_provider::public.payment_tender,p_amount,p_currency,p_exchange_rate,format('%s commerce order %s',p_provider,v_order.id),p_settlement_currency,p_settlement_amount,p_settlement_exchange_rate);
  PERFORM public.allocate_payment(v_pe,jsonb_build_array(jsonb_build_object('sales_invoice_id',v_inv,'amount',p_amount)));
  PERFORM public.post_payment_entry(v_pe);
  UPDATE public.commerce_orders
    SET settled_payment_entry_id=v_pe,settled_provider=p_provider,settled_provider_ref=p_provider_ref,
        state=CASE WHEN fulfillment_mode='dispatch' THEN 'allocation_pending'::public.commerce_order_state ELSE 'paid'::public.commerce_order_state END,
        payment_exception=NULL,updated_at=now()
    WHERE id=v_order.id;
  PERFORM private.enqueue_commerce_event('commerce:'||v_order.id::text||':paid','commerce.payment_settled',v_order.id,jsonb_build_object('provider',p_provider,'provider_ref',p_provider_ref,'payment_entry_id',v_pe,'sales_invoice_id',v_inv));
  RETURN v_pe;
END;
$$;
REVOKE ALL ON FUNCTION private.settle_commerce_payment(UUID,TEXT,UUID,TEXT,NUMERIC,public.currency_code,NUMERIC,public.currency_code,NUMERIC,NUMERIC) FROM PUBLIC,anon,authenticated;
GRANT EXECUTE ON FUNCTION private.settle_commerce_payment(UUID,TEXT,UUID,TEXT,NUMERIC,public.currency_code,NUMERIC,public.currency_code,NUMERIC,NUMERIC) TO service_role;

CREATE OR REPLACE FUNCTION public.settle_commerce_manual_payment(
  p_order_id UUID,
  p_payment_request_id UUID,
  p_tender public.payment_tender,
  p_reference TEXT DEFAULT NULL,
  p_settlement_currency public.currency_code DEFAULT NULL,
  p_settlement_amount NUMERIC DEFAULT NULL,
  p_settlement_exchange_rate NUMERIC DEFAULT NULL
)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
  v_order public.commerce_orders%ROWTYPE;
  v_request public.commerce_manual_payment_requests%ROWTYPE;
  v_pe UUID;
  v_reference TEXT;
BEGIN
  PERFORM public._require_payments_staff();
  IF p_order_id IS NULL OR p_payment_request_id IS NULL THEN
    RAISE EXCEPTION 'order_id and payment_request_id required';
  END IF;
  IF p_tender NOT IN ('cash','bank') THEN
    RAISE EXCEPTION 'manual commerce settlement supports cash or bank only';
  END IF;

  SELECT * INTO v_order
  FROM public.commerce_orders
  WHERE id=p_order_id
  FOR UPDATE;
  IF NOT FOUND THEN RAISE EXCEPTION 'commerce order not found'; END IF;

  INSERT INTO public.commerce_manual_payment_requests(id,commerce_order_id,tender,reference)
  VALUES(p_payment_request_id,p_order_id,p_tender,NULLIF(trim(COALESCE(p_reference,'')),''))
  ON CONFLICT(id) DO NOTHING;

  SELECT * INTO v_request
  FROM public.commerce_manual_payment_requests
  WHERE id=p_payment_request_id
  FOR UPDATE;

  IF v_request.commerce_order_id IS DISTINCT FROM p_order_id OR v_request.tender IS DISTINCT FROM p_tender THEN
    RAISE EXCEPTION 'payment request id was already used for a different order or tender';
  END IF;
  IF v_request.payment_entry_id IS NOT NULL THEN
    RETURN v_request.payment_entry_id;
  END IF;

  v_reference:=COALESCE(NULLIF(trim(COALESCE(p_reference,'')),''),'MANUAL-'||p_payment_request_id::text);
  v_pe:=private.settle_commerce_payment(
    v_order.id,
    p_tender::text,
    p_payment_request_id,
    v_reference,
    v_order.total,
    v_order.currency,
    v_order.exchange_rate_applied,
    p_settlement_currency,
    p_settlement_amount,
    p_settlement_exchange_rate
  );

  UPDATE public.commerce_manual_payment_requests
    SET payment_entry_id=v_pe,reference=v_reference,completed_at=now()
    WHERE id=p_payment_request_id;
  RETURN v_pe;
END;
$$;
REVOKE ALL ON FUNCTION public.settle_commerce_manual_payment(UUID,UUID,public.payment_tender,TEXT,public.currency_code,NUMERIC,NUMERIC) FROM PUBLIC,anon;
GRANT EXECUTE ON FUNCTION public.settle_commerce_manual_payment(UUID,UUID,public.payment_tender,TEXT,public.currency_code,NUMERIC,NUMERIC) TO authenticated,service_role;

COMMENT ON FUNCTION public.settle_commerce_manual_payment(UUID,UUID,public.payment_tender,TEXT,public.currency_code,NUMERIC,NUMERIC) IS
  'Idempotent staff cash/bank settlement for a reserved commerce order. Finalizes invoice then allocates/posts the verified manual payment in one database transaction; paid-finalization failures are retained for repair.';