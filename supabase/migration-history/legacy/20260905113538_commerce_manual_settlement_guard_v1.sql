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

  INSERT INTO public.commerce_manual_payment_requests(id,commerce_order_id,tender,reference)
  VALUES(p_payment_request_id,p_order_id,p_tender,NULLIF(trim(COALESCE(p_reference,'')),''))
  ON CONFLICT(id) DO NOTHING;

  SELECT * INTO v_request
  FROM public.commerce_manual_payment_requests
  WHERE id=p_payment_request_id
  FOR UPDATE;

  IF v_request.commerce_order_id IS DISTINCT FROM p_order_id
     OR v_request.tender IS DISTINCT FROM p_tender THEN
    RAISE EXCEPTION 'payment request id was already used for a different order or tender';
  END IF;
  IF v_request.payment_entry_id IS NOT NULL THEN
    RETURN v_request.payment_entry_id;
  END IF;

  SELECT * INTO v_order
  FROM public.commerce_orders
  WHERE id=p_order_id
  FOR UPDATE;
  IF NOT FOUND THEN RAISE EXCEPTION 'commerce order not found'; END IF;

  IF v_order.settled_payment_entry_id IS NOT NULL THEN
    RAISE EXCEPTION 'commerce order is already settled; do not accept another manual payment';
  END IF;
  IF v_order.reservation_expires_at IS NULL OR v_order.reservation_expires_at <= now() THEN
    RAISE EXCEPTION 'commerce reservation expired; rebuild checkout before accepting payment';
  END IF;
  IF v_order.state NOT IN ('awaiting_payment','payment_failed') THEN
    RAISE EXCEPTION 'manual payment cannot be accepted while order is in state %', v_order.state;
  END IF;

  v_reference:=COALESCE(
    NULLIF(trim(COALESCE(p_reference,'')),''),
    'MANUAL-'||p_payment_request_id::text
  );
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
    SET payment_entry_id=v_pe,
        reference=v_reference,
        completed_at=now()
    WHERE id=p_payment_request_id;
  RETURN v_pe;
END;
$$;

REVOKE ALL ON FUNCTION public.settle_commerce_manual_payment(UUID,UUID,public.payment_tender,TEXT,public.currency_code,NUMERIC,NUMERIC) FROM PUBLIC,anon;
GRANT EXECUTE ON FUNCTION public.settle_commerce_manual_payment(UUID,UUID,public.payment_tender,TEXT,public.currency_code,NUMERIC,NUMERIC) TO authenticated,service_role;

COMMENT ON FUNCTION public.settle_commerce_manual_payment(UUID,UUID,public.payment_tender,TEXT,public.currency_code,NUMERIC,NUMERIC) IS
  'Idempotent staff cash/bank settlement for a live reserved commerce order. Rejects expired, already-settled, or provider-processing orders before cash/bank is accepted; successful settlement finalizes and posts atomically.';