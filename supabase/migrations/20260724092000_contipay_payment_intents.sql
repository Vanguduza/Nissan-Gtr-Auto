-- Phase 13: ContiPay payment intents + webhook idempotency
-- Secrets (API keys / HMAC) live ONLY in Edge Function env — never in this schema.

CREATE TYPE public.contipay_method AS ENUM ('ecocash', 'visa', 'zimswitch');
CREATE TYPE public.contipay_intent_status AS ENUM (
  'pending', 'authorized', 'settled', 'failed', 'cancelled'
);

CREATE TABLE public.contipay_payment_intents (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  external_ref TEXT NOT NULL UNIQUE,
  method public.contipay_method NOT NULL,
  status public.contipay_intent_status NOT NULL DEFAULT 'pending',
  customer_id UUID REFERENCES public.customers (id),
  amount NUMERIC(18, 2) NOT NULL CHECK (amount > 0),
  currency public.currency_code NOT NULL DEFAULT 'USD',
  exchange_rate_applied NUMERIC(18, 8) NOT NULL DEFAULT 1,
  -- Dual-currency settlement display
  settlement_currency public.currency_code,
  settlement_amount NUMERIC(18, 2),
  settlement_exchange_rate NUMERIC(18, 8),
  payment_entry_id UUID REFERENCES public.payment_entries (id),
  provider_ref TEXT,
  -- Hash of normalized webhook body for idempotent settle (no secret material)
  webhook_payload_hash TEXT,
  last_webhook_at TIMESTAMPTZ,
  failure_reason TEXT,
  metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
  created_by UUID REFERENCES auth.users (id),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT contipay_settlement_pair CHECK (
    (settlement_currency IS NULL AND settlement_amount IS NULL)
    OR (settlement_currency IS NOT NULL AND settlement_amount IS NOT NULL AND settlement_amount > 0)
  )
);

CREATE INDEX contipay_intents_status_idx ON public.contipay_payment_intents (status);
CREATE INDEX contipay_intents_customer_idx ON public.contipay_payment_intents (customer_id);

CREATE TABLE public.contipay_webhook_events (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  intent_id UUID REFERENCES public.contipay_payment_intents (id) ON DELETE SET NULL,
  external_ref TEXT,
  payload_hash TEXT NOT NULL,
  processed BOOLEAN NOT NULL DEFAULT false,
  result_note TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (payload_hash)
);

CREATE INDEX contipay_webhook_events_created_idx
  ON public.contipay_webhook_events (created_at DESC);

CREATE OR REPLACE FUNCTION public.create_contipay_intent(
  p_external_ref TEXT,
  p_method public.contipay_method,
  p_amount NUMERIC,
  p_currency public.currency_code DEFAULT 'USD',
  p_exchange_rate NUMERIC DEFAULT 1,
  p_customer_id UUID DEFAULT NULL,
  p_settlement_currency public.currency_code DEFAULT NULL,
  p_settlement_amount NUMERIC DEFAULT NULL,
  p_settlement_exchange_rate NUMERIC DEFAULT NULL,
  p_metadata JSONB DEFAULT '{}'::jsonb
)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_id UUID;
BEGIN
  PERFORM public._require_payments_staff();
  PERFORM public._payments_rpc_enter();

  IF p_external_ref IS NULL OR length(trim(p_external_ref)) = 0 THEN
    RAISE EXCEPTION 'external_ref required';
  END IF;
  IF p_amount IS NULL OR p_amount <= 0 THEN
    RAISE EXCEPTION 'amount must be > 0';
  END IF;

  INSERT INTO public.contipay_payment_intents (
    external_ref, method, customer_id, amount, currency, exchange_rate_applied,
    settlement_currency, settlement_amount, settlement_exchange_rate,
    metadata, created_by
  )
  VALUES (
    trim(p_external_ref),
    p_method,
    p_customer_id,
    p_amount,
    p_currency,
    CASE WHEN p_currency = 'USD' THEN COALESCE(p_exchange_rate, 1) ELSE p_exchange_rate END,
    p_settlement_currency,
    p_settlement_amount,
    p_settlement_exchange_rate,
    COALESCE(p_metadata, '{}'::jsonb),
    auth.uid()
  )
  RETURNING id INTO v_id;

  RETURN v_id;
END;
$$;

-- Service/edge path: settle intent idempotently, create+post ContiPay payment entry.
-- p_allocations: [{sales_invoice_id, amount}] — required when settling against AR.
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
    -- Edge functions use service_role; staff may also settle in sandbox
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
    -- Duplicate webhook: return existing intent id if known
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

ALTER TABLE public.contipay_payment_intents ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.contipay_webhook_events ENABLE ROW LEVEL SECURITY;

CREATE POLICY contipay_intents_staff_select ON public.contipay_payment_intents
  FOR SELECT TO authenticated
  USING (public.has_staff_role(ARRAY['admin', 'finance', 'sales']::public.staff_role[]));

CREATE POLICY contipay_webhook_admin_select ON public.contipay_webhook_events
  FOR SELECT TO authenticated
  USING (public.has_staff_role(ARRAY['admin', 'finance']::public.staff_role[]));

REVOKE ALL ON FUNCTION public.create_contipay_intent(
  TEXT, public.contipay_method, NUMERIC, public.currency_code, NUMERIC, UUID,
  public.currency_code, NUMERIC, NUMERIC, JSONB
) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.mark_contipay_settled(
  TEXT, TEXT, TEXT, JSONB, public.currency_code, NUMERIC, NUMERIC, BOOLEAN, TEXT
) FROM PUBLIC;

GRANT EXECUTE ON FUNCTION public.create_contipay_intent(
  TEXT, public.contipay_method, NUMERIC, public.currency_code, NUMERIC, UUID,
  public.currency_code, NUMERIC, NUMERIC, JSONB
) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.mark_contipay_settled(
  TEXT, TEXT, TEXT, JSONB, public.currency_code, NUMERIC, NUMERIC, BOOLEAN, TEXT
) TO authenticated, service_role;
