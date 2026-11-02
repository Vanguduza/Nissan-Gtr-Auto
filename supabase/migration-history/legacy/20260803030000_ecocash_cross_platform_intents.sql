-- Phase A: EcoCash payer MSISDN on WhatsApp Flow orders
-- Phase B scaffold: ecocash_payment_intents SoR (mirror Paynow) for web/POS/mobile
-- Secrets stay in Edge / FastAPI env only. No ZIMRA.

-- ── WhatsApp Flow payer fields (Phase A) ───────────────────────────────────
ALTER TABLE public.whatsapp_flow_orders
  ADD COLUMN IF NOT EXISTS ecocash_payer_msisdn TEXT;

ALTER TABLE public.whatsapp_flow_orders
  ADD COLUMN IF NOT EXISTS ecocash_payer_mode TEXT
    CHECK (
      ecocash_payer_mode IS NULL
      OR ecocash_payer_mode IN ('whatsapp', 'saved', 'other')
    );

COMMENT ON COLUMN public.whatsapp_flow_orders.ecocash_payer_msisdn IS
  'Normalized 263… EcoCash wallet charged (may differ from wa_id).';
COMMENT ON COLUMN public.whatsapp_flow_orders.ecocash_payer_mode IS
  'whatsapp = WA MSISDN; saved = profile/saved phone; other = entered MSISDN.';

-- ── Cross-platform tender + intents (Phase B SoR) ──────────────────────────
ALTER TYPE public.payment_tender ADD VALUE IF NOT EXISTS 'ecocash';

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'ecocash_intent_status') THEN
    CREATE TYPE public.ecocash_intent_status AS ENUM (
      'pending', 'authorized', 'settled', 'failed', 'cancelled'
    );
  END IF;
END $$;

CREATE TABLE IF NOT EXISTS public.ecocash_payment_intents (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  external_ref TEXT NOT NULL UNIQUE,
  status public.ecocash_intent_status NOT NULL DEFAULT 'pending',
  payer_msisdn TEXT NOT NULL,
  payer_mode TEXT NOT NULL DEFAULT 'other'
    CHECK (payer_mode IN ('whatsapp', 'saved', 'other', 'pos_entered', 'profile')),
  channel TEXT NOT NULL DEFAULT 'web'
    CHECK (channel IN (
      'whatsapp_flow', 'web', 'pos', 'ios', 'android_customer', 'api'
    )),
  customer_id UUID REFERENCES public.customers (id),
  sales_invoice_id UUID,
  whatsapp_flow_order_id UUID REFERENCES public.whatsapp_flow_orders (id),
  amount NUMERIC(18, 2) NOT NULL CHECK (amount > 0),
  currency public.currency_code NOT NULL DEFAULT 'USD',
  exchange_rate_applied NUMERIC(18, 8) NOT NULL DEFAULT 1,
  settlement_currency public.currency_code,
  settlement_amount NUMERIC(18, 2),
  settlement_exchange_rate NUMERIC(18, 8),
  payment_entry_id UUID REFERENCES public.payment_entries (id),
  provider_ref TEXT,
  webhook_payload_hash TEXT,
  last_webhook_at TIMESTAMPTZ,
  failure_reason TEXT,
  metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
  created_by UUID REFERENCES auth.users (id),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT ecocash_settlement_pair CHECK (
    (settlement_currency IS NULL AND settlement_amount IS NULL)
    OR (
      settlement_currency IS NOT NULL
      AND settlement_amount IS NOT NULL
      AND settlement_amount > 0
    )
  )
);

CREATE INDEX IF NOT EXISTS ecocash_intents_status_idx
  ON public.ecocash_payment_intents (status);
CREATE INDEX IF NOT EXISTS ecocash_intents_customer_idx
  ON public.ecocash_payment_intents (customer_id);
CREATE INDEX IF NOT EXISTS ecocash_intents_payer_idx
  ON public.ecocash_payment_intents (payer_msisdn);
CREATE INDEX IF NOT EXISTS ecocash_intents_invoice_idx
  ON public.ecocash_payment_intents (sales_invoice_id)
  WHERE sales_invoice_id IS NOT NULL;

CREATE TABLE IF NOT EXISTS public.ecocash_webhook_events (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  intent_id UUID REFERENCES public.ecocash_payment_intents (id) ON DELETE SET NULL,
  external_ref TEXT,
  payload_hash TEXT NOT NULL,
  processed BOOLEAN NOT NULL DEFAULT false,
  result_note TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (payload_hash)
);

CREATE INDEX IF NOT EXISTS ecocash_webhook_events_created_idx
  ON public.ecocash_webhook_events (created_at DESC);

COMMENT ON TABLE public.ecocash_payment_intents IS
  'EcoCash direct C2B intents (web/POS/mobile/WhatsApp). Secrets never stored here.';

ALTER TABLE public.ecocash_payment_intents ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.ecocash_webhook_events ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS ecocash_intents_staff_select ON public.ecocash_payment_intents;
CREATE POLICY ecocash_intents_staff_select ON public.ecocash_payment_intents
  FOR SELECT TO authenticated
  USING (
    EXISTS (
      SELECT 1 FROM public.staff_roles sr
      WHERE sr.user_id = auth.uid()
        AND sr.role IN ('admin', 'finance', 'sales')
    )
  );

DROP POLICY IF EXISTS ecocash_intents_customer_select ON public.ecocash_payment_intents;
CREATE POLICY ecocash_intents_customer_select ON public.ecocash_payment_intents
  FOR SELECT TO authenticated
  USING (
    customer_id IS NOT NULL
    AND customer_id = public._current_customer_id()
  );

-- Webhook events: no authenticated access (service_role only)
DROP POLICY IF EXISTS ecocash_webhook_events_deny_authenticated
  ON public.ecocash_webhook_events;
CREATE POLICY ecocash_webhook_events_deny_authenticated
  ON public.ecocash_webhook_events
  FOR ALL TO authenticated
  USING (false)
  WITH CHECK (false);

GRANT SELECT ON TABLE public.ecocash_payment_intents TO authenticated;
GRANT ALL ON TABLE public.ecocash_payment_intents TO service_role;
GRANT ALL ON TABLE public.ecocash_webhook_events TO service_role;

CREATE OR REPLACE FUNCTION public.create_ecocash_intent(
  p_external_ref TEXT,
  p_payer_msisdn TEXT,
  p_amount NUMERIC,
  p_currency public.currency_code DEFAULT 'USD',
  p_exchange_rate NUMERIC DEFAULT 1,
  p_payer_mode TEXT DEFAULT 'other',
  p_channel TEXT DEFAULT 'web',
  p_customer_id UUID DEFAULT NULL,
  p_sales_invoice_id UUID DEFAULT NULL,
  p_whatsapp_flow_order_id UUID DEFAULT NULL,
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
  v_msisdn TEXT;
BEGIN
  PERFORM public._require_payments_staff();
  PERFORM public._payments_rpc_enter();

  IF p_external_ref IS NULL OR length(trim(p_external_ref)) = 0 THEN
    RAISE EXCEPTION 'external_ref required';
  END IF;
  IF p_amount IS NULL OR p_amount <= 0 THEN
    RAISE EXCEPTION 'amount must be > 0';
  END IF;

  v_msisdn := regexp_replace(trim(COALESCE(p_payer_msisdn, '')), '[^0-9]', '', 'g');
  IF v_msisdn ~ '^0[0-9]{9}$' THEN
    v_msisdn := '263' || substr(v_msisdn, 2);
  END IF;
  IF v_msisdn !~ '^263[0-9]{9}$' THEN
    RAISE EXCEPTION 'payer_msisdn must normalize to 263XXXXXXXXX';
  END IF;

  IF p_payer_mode IS NULL OR p_payer_mode NOT IN (
    'whatsapp', 'saved', 'other', 'pos_entered', 'profile'
  ) THEN
    RAISE EXCEPTION 'invalid payer_mode';
  END IF;
  IF p_channel IS NULL OR p_channel NOT IN (
    'whatsapp_flow', 'web', 'pos', 'ios', 'android_customer', 'api'
  ) THEN
    RAISE EXCEPTION 'invalid channel';
  END IF;

  INSERT INTO public.ecocash_payment_intents (
    external_ref, payer_msisdn, payer_mode, channel, customer_id,
    sales_invoice_id, whatsapp_flow_order_id,
    amount, currency, exchange_rate_applied,
    settlement_currency, settlement_amount, settlement_exchange_rate,
    metadata, created_by
  )
  VALUES (
    trim(p_external_ref),
    v_msisdn,
    p_payer_mode,
    p_channel,
    p_customer_id,
    p_sales_invoice_id,
    p_whatsapp_flow_order_id,
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

-- Service-role / Edge settle: idempotent webhook + optional Payment Entry (invoice path).
CREATE OR REPLACE FUNCTION public.mark_ecocash_settled(
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
  v_intent public.ecocash_payment_intents%ROWTYPE;
  v_webhook UUID;
  v_pe UUID;
  v_hash TEXT;
BEGIN
  IF auth.role() = 'authenticated' AND NOT public.has_staff_role(
    ARRAY['admin', 'finance']::public.staff_role[]
  ) THEN
    RAISE EXCEPTION 'service role or finance/admin required for EcoCash settle';
  END IF;

  PERFORM public._payments_rpc_enter();

  v_hash := NULLIF(trim(COALESCE(p_payload_hash, '')), '');
  IF v_hash IS NULL THEN
    RAISE EXCEPTION 'payload_hash required for webhook idempotency';
  END IF;

  INSERT INTO public.ecocash_webhook_events (external_ref, payload_hash, processed, result_note)
  VALUES (p_external_ref, v_hash, false, 'received')
  ON CONFLICT (payload_hash) DO NOTHING
  RETURNING id INTO v_webhook;

  IF v_webhook IS NULL THEN
    SELECT i.id INTO v_pe
    FROM public.ecocash_payment_intents i
    WHERE i.external_ref = p_external_ref;
    RETURN COALESCE(v_pe, (
      SELECT intent_id FROM public.ecocash_webhook_events WHERE payload_hash = v_hash
    ));
  END IF;

  SELECT * INTO v_intent
  FROM public.ecocash_payment_intents
  WHERE external_ref = trim(p_external_ref)
  FOR UPDATE;

  IF NOT FOUND THEN
    UPDATE public.ecocash_webhook_events
    SET result_note = 'unknown external_ref', processed = true
    WHERE id = v_webhook;
    RAISE EXCEPTION 'ecocash intent not found: %', p_external_ref;
  END IF;

  UPDATE public.ecocash_webhook_events
  SET intent_id = v_intent.id
  WHERE id = v_webhook;

  IF v_intent.status = 'settled' THEN
    UPDATE public.ecocash_webhook_events
    SET processed = true, result_note = 'already settled'
    WHERE id = v_webhook;
    RETURN COALESCE(v_intent.payment_entry_id, v_intent.id);
  END IF;

  IF NOT COALESCE(p_success, true) THEN
    UPDATE public.ecocash_payment_intents
    SET
      status = 'failed',
      failure_reason = p_failure_reason,
      webhook_payload_hash = v_hash,
      last_webhook_at = now(),
      provider_ref = COALESCE(p_provider_ref, provider_ref),
      updated_at = now()
    WHERE id = v_intent.id;

    IF v_intent.whatsapp_flow_order_id IS NOT NULL THEN
      UPDATE public.whatsapp_flow_orders
      SET
        status = 'FAILED',
        payment_reference = COALESCE(p_provider_ref, payment_reference),
        updated_at = now()
      WHERE id = v_intent.whatsapp_flow_order_id AND status = 'PENDING';
    END IF;

    UPDATE public.ecocash_webhook_events
    SET processed = true, result_note = 'marked failed'
    WHERE id = v_webhook;

    RETURN v_intent.id;
  END IF;

  -- Invoice-backed channels: require customer + create/post Payment Entry
  IF v_intent.customer_id IS NOT NULL
     AND (
       p_allocations IS NOT NULL
       OR v_intent.sales_invoice_id IS NOT NULL
       OR v_intent.channel IN ('web', 'pos', 'ios', 'android_customer')
     ) THEN
    v_pe := public.create_payment_entry(
      v_intent.customer_id,
      'ecocash',
      v_intent.amount,
      v_intent.currency,
      v_intent.exchange_rate_applied,
      format('EcoCash %s', v_intent.external_ref),
      COALESCE(p_settlement_currency, v_intent.settlement_currency),
      COALESCE(p_settlement_amount, v_intent.settlement_amount),
      COALESCE(p_settlement_exchange_rate, v_intent.settlement_exchange_rate)
    );

    IF p_allocations IS NOT NULL AND jsonb_typeof(p_allocations) = 'array'
       AND jsonb_array_length(p_allocations) > 0 THEN
      PERFORM public.allocate_payment(v_pe, p_allocations);
    ELSIF v_intent.sales_invoice_id IS NOT NULL THEN
      PERFORM public.allocate_payment(
        v_pe,
        jsonb_build_array(
          jsonb_build_object(
            'sales_invoice_id', v_intent.sales_invoice_id,
            'amount', v_intent.amount
          )
        )
      );
    END IF;

    PERFORM public.post_payment_entry(v_pe);
  END IF;

  UPDATE public.ecocash_payment_intents
  SET
    status = 'settled',
    payment_entry_id = COALESCE(v_pe, payment_entry_id),
    provider_ref = COALESCE(p_provider_ref, provider_ref),
    webhook_payload_hash = v_hash,
    last_webhook_at = now(),
    settlement_currency = COALESCE(p_settlement_currency, settlement_currency),
    settlement_amount = COALESCE(p_settlement_amount, settlement_amount),
    settlement_exchange_rate = COALESCE(p_settlement_exchange_rate, settlement_exchange_rate),
    updated_at = now()
  WHERE id = v_intent.id;

  IF v_intent.whatsapp_flow_order_id IS NOT NULL THEN
    UPDATE public.whatsapp_flow_orders
    SET
      status = 'PAID',
      payment_reference = COALESCE(p_provider_ref, payment_reference),
      updated_at = now()
    WHERE id = v_intent.whatsapp_flow_order_id
      AND status IS DISTINCT FROM 'PAID';
  END IF;

  UPDATE public.ecocash_webhook_events
  SET processed = true, result_note = 'settled'
  WHERE id = v_webhook;

  RETURN COALESCE(v_pe, v_intent.id);
END;
$$;

REVOKE ALL ON FUNCTION public.create_ecocash_intent(
  TEXT, TEXT, NUMERIC, public.currency_code, NUMERIC, TEXT, TEXT,
  UUID, UUID, UUID, public.currency_code, NUMERIC, NUMERIC, JSONB
) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.create_ecocash_intent(
  TEXT, TEXT, NUMERIC, public.currency_code, NUMERIC, TEXT, TEXT,
  UUID, UUID, UUID, public.currency_code, NUMERIC, NUMERIC, JSONB
) TO authenticated, service_role;

REVOKE ALL ON FUNCTION public.mark_ecocash_settled(
  TEXT, TEXT, TEXT, JSONB, public.currency_code, NUMERIC, NUMERIC, BOOLEAN, TEXT
) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.mark_ecocash_settled(
  TEXT, TEXT, TEXT, JSONB, public.currency_code, NUMERIC, NUMERIC, BOOLEAN, TEXT
) TO service_role;
