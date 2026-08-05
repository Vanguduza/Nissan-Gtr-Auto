-- Customer self-pay EcoCash direct C2B (parity with ContiPay/Paynow storefront RPCs).
-- Payer MSISDN is required — never assume WhatsApp/account phone equals EcoCash wallet.

CREATE OR REPLACE FUNCTION public.create_customer_ecocash_intent(
  p_sales_invoice_id UUID,
  p_payer_msisdn TEXT,
  p_payer_mode TEXT DEFAULT 'other',
  p_external_ref TEXT DEFAULT NULL,
  p_amount NUMERIC DEFAULT NULL,
  p_channel TEXT DEFAULT 'web',
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
  v_inv public.sales_invoices%ROWTYPE;
  v_open NUMERIC;
  v_amount NUMERIC;
  v_ref TEXT;
  v_id UUID;
  v_msisdn TEXT;
BEGIN
  v_inv := public._assert_customer_owns_invoice(p_sales_invoice_id);

  IF v_inv.doc_type <> 'invoice' OR v_inv.status <> 'posted' THEN
    RAISE EXCEPTION 'only posted invoices can receive payment intents';
  END IF;

  v_open := v_inv.total - v_inv.amount_paid;
  IF v_open <= 0 THEN
    RAISE EXCEPTION 'invoice has no open balance';
  END IF;

  v_amount := COALESCE(p_amount, v_open);
  IF v_amount IS NULL OR v_amount <= 0 THEN
    RAISE EXCEPTION 'amount must be > 0';
  END IF;
  IF v_amount > v_open THEN
    RAISE EXCEPTION 'amount % exceeds invoice open %', v_amount, v_open;
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

  v_msisdn := regexp_replace(trim(COALESCE(p_payer_msisdn, '')), '[^0-9]', '', 'g');
  IF v_msisdn ~ '^0[0-9]{9}$' THEN
    v_msisdn := '263' || substr(v_msisdn, 2);
  END IF;
  IF v_msisdn !~ '^263[0-9]{9}$' THEN
    RAISE EXCEPTION 'payer_msisdn must normalize to 263XXXXXXXXX';
  END IF;

  v_ref := COALESCE(
    nullif(trim(p_external_ref), ''),
    'EC-CUST-' || v_inv.id::text || '-' || gen_random_uuid()::text
  );

  PERFORM public._payments_rpc_enter();

  INSERT INTO public.ecocash_payment_intents (
    external_ref, payer_msisdn, payer_mode, channel, customer_id,
    sales_invoice_id, amount, currency, exchange_rate_applied,
    settlement_currency, settlement_amount, settlement_exchange_rate,
    metadata, created_by
  )
  VALUES (
    v_ref,
    v_msisdn,
    p_payer_mode,
    p_channel,
    v_inv.customer_id,
    v_inv.id,
    v_amount,
    v_inv.currency,
    v_inv.exchange_rate_applied,
    p_settlement_currency,
    p_settlement_amount,
    p_settlement_exchange_rate,
    COALESCE(p_metadata, '{}'::jsonb) || jsonb_build_object(
      'sales_invoice_id', v_inv.id,
      'channel', p_channel
    ),
    auth.uid()
  )
  RETURNING id INTO v_id;

  RETURN v_id;
END;
$$;

REVOKE ALL ON FUNCTION public.create_customer_ecocash_intent(
  UUID, TEXT, TEXT, TEXT, NUMERIC, TEXT,
  public.currency_code, NUMERIC, NUMERIC, JSONB
) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.create_customer_ecocash_intent(
  UUID, TEXT, TEXT, TEXT, NUMERIC, TEXT,
  public.currency_code, NUMERIC, NUMERIC, JSONB
) TO authenticated, service_role;

COMMENT ON FUNCTION public.create_customer_ecocash_intent IS
  'Customer self-pay EcoCash direct C2B intent; payer_msisdn required (saved vs other).';
