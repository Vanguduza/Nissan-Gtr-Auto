-- Deny settle of cancelled ContiPay / Paynow intents (security follow-up).
-- Settle RPCs are service_role-only (edge webhooks); finance UI settles via edge.
-- Ledger amount still comes from DB intent rows; no ZIMRA.

CREATE OR REPLACE FUNCTION public.mark_contipay_settled(
  p_external_ref TEXT,
  p_payload_hash TEXT,
  p_provider_ref TEXT DEFAULT NULL,
  p_allocations JSONB DEFAULT NULL,
  p_settlement_currency public.currency_code DEFAULT NULL,
  p_settlement_amount NUMERIC DEFAULT NULL,
  p_settlement_exchange_rate NUMERIC DEFAULT NULL,
  p_success BOOLEAN DEFAULT true,
  p_failure_reason TEXT DEFAULT NULL
)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_intent public.contipay_payment_intents%ROWTYPE;
  v_webhook UUID;
  v_pe UUID;
  v_hash TEXT;
BEGIN
  IF auth.role() = 'authenticated' AND NOT public.has_staff_role(
    ARRAY['admin', 'finance']::public.staff_role[]
  ) THEN
    RAISE EXCEPTION 'service role or finance/admin required for ContiPay settle';
  END IF;

  PERFORM public._payments_rpc_enter();

  v_hash := NULLIF(trim(COALESCE(p_payload_hash, '')), '');
  IF v_hash IS NULL THEN
    RAISE EXCEPTION 'payload_hash required for webhook idempotency';
  END IF;

  INSERT INTO public.contipay_webhook_events (external_ref, payload_hash, processed, result_note)
  VALUES (p_external_ref, v_hash, false, 'received')
  ON CONFLICT (payload_hash) DO NOTHING
  RETURNING id INTO v_webhook;

  IF v_webhook IS NULL THEN
    SELECT i.id INTO v_pe
    FROM public.contipay_payment_intents i
    WHERE i.external_ref = p_external_ref;
    RETURN COALESCE(v_pe, (
      SELECT intent_id FROM public.contipay_webhook_events WHERE payload_hash = v_hash
    ));
  END IF;

  SELECT * INTO v_intent
  FROM public.contipay_payment_intents
  WHERE external_ref = p_external_ref
  FOR UPDATE;

  IF NOT FOUND THEN
    UPDATE public.contipay_webhook_events
    SET result_note = 'unknown external_ref', processed = true
    WHERE id = v_webhook;
    RAISE EXCEPTION 'contipay intent not found: %', p_external_ref;
  END IF;

  UPDATE public.contipay_webhook_events
  SET intent_id = v_intent.id
  WHERE id = v_webhook;

  IF v_intent.status = 'settled' THEN
    UPDATE public.contipay_webhook_events
    SET processed = true, result_note = 'already settled'
    WHERE id = v_webhook;
    RETURN v_intent.payment_entry_id;
  END IF;

  IF v_intent.status = 'cancelled' THEN
    UPDATE public.contipay_webhook_events
    SET processed = true, result_note = 'refused cancelled intent'
    WHERE id = v_webhook;
    RAISE EXCEPTION 'cannot settle cancelled ContiPay intent: %', p_external_ref;
  END IF;

  IF NOT COALESCE(p_success, true) THEN
    UPDATE public.contipay_payment_intents
    SET
      status = 'failed',
      failure_reason = p_failure_reason,
      webhook_payload_hash = v_hash,
      last_webhook_at = now(),
      provider_ref = COALESCE(p_provider_ref, provider_ref),
      updated_at = now()
    WHERE id = v_intent.id;

    PERFORM public.emit_domain_event(
      'payment_failed',
      'contipay:fail:' || v_intent.id::text || ':' || v_hash,
      jsonb_build_object(
        'intent_id', v_intent.id,
        'external_ref', p_external_ref,
        'reason', p_failure_reason
      ),
      auth.uid(),
      format('GTR Auto: ContiPay failed %s', p_external_ref)
    );

    UPDATE public.contipay_webhook_events
    SET processed = true, result_note = 'marked failed'
    WHERE id = v_webhook;

    RETURN v_intent.id;
  END IF;

  IF v_intent.customer_id IS NULL THEN
    RAISE EXCEPTION 'settled ContiPay intent requires customer_id';
  END IF;

  v_pe := public.create_payment_entry(
    v_intent.customer_id,
    'contipay',
    v_intent.amount,
    v_intent.currency,
    v_intent.exchange_rate_applied,
    format('ContiPay %s', v_intent.external_ref),
    COALESCE(p_settlement_currency, v_intent.settlement_currency),
    COALESCE(p_settlement_amount, v_intent.settlement_amount),
    COALESCE(p_settlement_exchange_rate, v_intent.settlement_exchange_rate)
  );

  IF p_allocations IS NOT NULL AND jsonb_typeof(p_allocations) = 'array'
     AND jsonb_array_length(p_allocations) > 0 THEN
    PERFORM public.allocate_payment(v_pe, p_allocations);
  END IF;

  PERFORM public.post_payment_entry(v_pe);

  UPDATE public.contipay_payment_intents
  SET
    status = 'settled',
    payment_entry_id = v_pe,
    provider_ref = COALESCE(p_provider_ref, provider_ref),
    webhook_payload_hash = v_hash,
    last_webhook_at = now(),
    settlement_currency = COALESCE(p_settlement_currency, settlement_currency),
    settlement_amount = COALESCE(p_settlement_amount, settlement_amount),
    settlement_exchange_rate = COALESCE(p_settlement_exchange_rate, settlement_exchange_rate),
    updated_at = now()
  WHERE id = v_intent.id;

  UPDATE public.contipay_webhook_events
  SET processed = true, result_note = 'settled'
  WHERE id = v_webhook;

  RETURN v_pe;
END;
$$;

CREATE OR REPLACE FUNCTION public.mark_paynow_settled(
  p_external_ref TEXT,
  p_payload_hash TEXT,
  p_provider_ref TEXT DEFAULT NULL,
  p_allocations JSONB DEFAULT NULL,
  p_settlement_currency public.currency_code DEFAULT NULL,
  p_settlement_amount NUMERIC DEFAULT NULL,
  p_settlement_exchange_rate NUMERIC DEFAULT NULL,
  p_success BOOLEAN DEFAULT true,
  p_failure_reason TEXT DEFAULT NULL
)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_intent public.paynow_payment_intents%ROWTYPE;
  v_webhook UUID;
  v_pe UUID;
  v_hash TEXT;
BEGIN
  IF auth.role() = 'authenticated' AND NOT public.has_staff_role(
    ARRAY['admin', 'finance']::public.staff_role[]
  ) THEN
    RAISE EXCEPTION 'service role or finance/admin required for Paynow settle';
  END IF;

  PERFORM public._payments_rpc_enter();

  v_hash := NULLIF(trim(COALESCE(p_payload_hash, '')), '');
  IF v_hash IS NULL THEN
    RAISE EXCEPTION 'payload_hash required for webhook idempotency';
  END IF;

  INSERT INTO public.paynow_webhook_events (external_ref, payload_hash, processed, result_note)
  VALUES (p_external_ref, v_hash, false, 'received')
  ON CONFLICT (payload_hash) DO NOTHING
  RETURNING id INTO v_webhook;

  IF v_webhook IS NULL THEN
    SELECT i.id INTO v_pe
    FROM public.paynow_payment_intents i
    WHERE i.external_ref = p_external_ref;
    RETURN COALESCE(v_pe, (
      SELECT intent_id FROM public.paynow_webhook_events WHERE payload_hash = v_hash
    ));
  END IF;

  SELECT * INTO v_intent
  FROM public.paynow_payment_intents
  WHERE external_ref = p_external_ref
  FOR UPDATE;

  IF NOT FOUND THEN
    UPDATE public.paynow_webhook_events
    SET result_note = 'unknown external_ref', processed = true
    WHERE id = v_webhook;
    RAISE EXCEPTION 'paynow intent not found: %', p_external_ref;
  END IF;

  UPDATE public.paynow_webhook_events
  SET intent_id = v_intent.id
  WHERE id = v_webhook;

  IF v_intent.status = 'settled' THEN
    UPDATE public.paynow_webhook_events
    SET processed = true, result_note = 'already settled'
    WHERE id = v_webhook;
    RETURN v_intent.payment_entry_id;
  END IF;

  IF v_intent.status = 'cancelled' THEN
    UPDATE public.paynow_webhook_events
    SET processed = true, result_note = 'refused cancelled intent'
    WHERE id = v_webhook;
    RAISE EXCEPTION 'cannot settle cancelled Paynow intent: %', p_external_ref;
  END IF;

  IF NOT COALESCE(p_success, true) THEN
    UPDATE public.paynow_payment_intents
    SET
      status = 'failed',
      failure_reason = p_failure_reason,
      webhook_payload_hash = v_hash,
      last_webhook_at = now(),
      provider_ref = COALESCE(p_provider_ref, provider_ref),
      updated_at = now()
    WHERE id = v_intent.id;

    PERFORM public.emit_domain_event(
      'payment_failed',
      'paynow:fail:' || v_intent.id::text || ':' || v_hash,
      jsonb_build_object(
        'intent_id', v_intent.id,
        'external_ref', p_external_ref,
        'reason', p_failure_reason
      ),
      auth.uid(),
      format('GTR Auto: Paynow failed %s', p_external_ref)
    );

    UPDATE public.paynow_webhook_events
    SET processed = true, result_note = 'marked failed'
    WHERE id = v_webhook;

    RETURN v_intent.id;
  END IF;

  IF v_intent.customer_id IS NULL THEN
    RAISE EXCEPTION 'settled Paynow intent requires customer_id';
  END IF;

  v_pe := public.create_payment_entry(
    v_intent.customer_id,
    'paynow',
    v_intent.amount,
    v_intent.currency,
    v_intent.exchange_rate_applied,
    format('Paynow %s', v_intent.external_ref),
    COALESCE(p_settlement_currency, v_intent.settlement_currency),
    COALESCE(p_settlement_amount, v_intent.settlement_amount),
    COALESCE(p_settlement_exchange_rate, v_intent.settlement_exchange_rate)
  );

  IF p_allocations IS NOT NULL AND jsonb_typeof(p_allocations) = 'array'
     AND jsonb_array_length(p_allocations) > 0 THEN
    PERFORM public.allocate_payment(v_pe, p_allocations);
  END IF;

  PERFORM public.post_payment_entry(v_pe);

  UPDATE public.paynow_payment_intents
  SET
    status = 'settled',
    payment_entry_id = v_pe,
    provider_ref = COALESCE(p_provider_ref, provider_ref),
    webhook_payload_hash = v_hash,
    last_webhook_at = now(),
    settlement_currency = COALESCE(p_settlement_currency, settlement_currency),
    settlement_amount = COALESCE(p_settlement_amount, settlement_amount),
    settlement_exchange_rate = COALESCE(p_settlement_exchange_rate, settlement_exchange_rate),
    updated_at = now()
  WHERE id = v_intent.id;

  UPDATE public.paynow_webhook_events
  SET processed = true, result_note = 'settled'
  WHERE id = v_webhook;

  RETURN v_pe;
END;
$$;
