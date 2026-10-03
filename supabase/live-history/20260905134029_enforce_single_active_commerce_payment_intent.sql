-- Exported from the hosted project's supabase_migrations.schema_migrations (20260905134029 enforce_single_active_commerce_payment_intent).
-- Source of record for what production ran; see supabase/live-history/README.md.

CREATE OR REPLACE FUNCTION private.active_commerce_payment_intent(
  p_order_id uuid,
  p_requested_provider text
)
RETURNS uuid
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path TO ''
AS $function$
DECLARE
  v_other_provider text;
  v_id uuid;
BEGIN
  IF p_requested_provider NOT IN ('paynow','ecocash','contipay') THEN
    RAISE EXCEPTION 'unsupported commerce payment provider: %', p_requested_provider;
  END IF;

  SELECT x.provider INTO v_other_provider
  FROM (
    SELECT 'paynow'::text AS provider, id, created_at
    FROM public.paynow_payment_intents
    WHERE commerce_order_id=p_order_id AND status IN ('pending','authorized')
    UNION ALL
    SELECT 'ecocash'::text, id, created_at
    FROM public.ecocash_payment_intents
    WHERE commerce_order_id=p_order_id AND status IN ('pending','authorized')
    UNION ALL
    SELECT 'contipay'::text, id, created_at
    FROM public.contipay_payment_intents
    WHERE commerce_order_id=p_order_id AND status IN ('pending','authorized')
  ) x
  WHERE x.provider <> p_requested_provider
  ORDER BY x.created_at DESC
  LIMIT 1;

  IF v_other_provider IS NOT NULL THEN
    RAISE EXCEPTION 'active % payment attempt exists for this order; wait for it to fail/cancel/expire before switching provider', v_other_provider;
  END IF;

  IF p_requested_provider='paynow' THEN
    SELECT id INTO v_id
    FROM public.paynow_payment_intents
    WHERE commerce_order_id=p_order_id AND status IN ('pending','authorized')
    ORDER BY created_at DESC LIMIT 1;
  ELSIF p_requested_provider='ecocash' THEN
    SELECT id INTO v_id
    FROM public.ecocash_payment_intents
    WHERE commerce_order_id=p_order_id AND status IN ('pending','authorized')
    ORDER BY created_at DESC LIMIT 1;
  ELSE
    SELECT id INTO v_id
    FROM public.contipay_payment_intents
    WHERE commerce_order_id=p_order_id AND status IN ('pending','authorized')
    ORDER BY created_at DESC LIMIT 1;
  END IF;

  RETURN v_id;
END;
$function$;

REVOKE ALL ON FUNCTION private.active_commerce_payment_intent(uuid,text) FROM PUBLIC, anon, authenticated;

CREATE OR REPLACE FUNCTION public.create_customer_paynow_intent(
  p_sales_invoice_id uuid,
  p_method public.paynow_method,
  p_external_ref text DEFAULT NULL::text,
  p_amount numeric DEFAULT NULL::numeric,
  p_settlement_currency public.currency_code DEFAULT NULL::public.currency_code,
  p_settlement_amount numeric DEFAULT NULL::numeric,
  p_settlement_exchange_rate numeric DEFAULT NULL::numeric,
  p_metadata jsonb DEFAULT '{}'::jsonb
)
RETURNS uuid
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO ''
AS $function$
DECLARE
  v_cust uuid := public._current_customer_id();
  v_order public.commerce_orders%ROWTYPE;
  v_inv public.sales_invoices%ROWTYPE;
  v_open numeric;
  v_amount numeric;
  v_ref text;
  v_id uuid;
BEGIN
  IF v_cust IS NULL THEN RAISE EXCEPTION 'customer profile required'; END IF;

  SELECT * INTO v_order
  FROM public.commerce_orders
  WHERE id=p_sales_invoice_id AND customer_id=v_cust
  FOR UPDATE;

  IF FOUND THEN
    IF v_order.settled_payment_entry_id IS NOT NULL THEN RAISE EXCEPTION 'commerce order already paid'; END IF;
    IF v_order.reservation_expires_at IS NULL OR v_order.reservation_expires_at<=now() THEN
      PERFORM private.release_commerce_order(v_order.id,'payment_expired','payment attempted after reservation expiry');
      RAISE EXCEPTION 'checkout reservation expired; rebuild checkout';
    END IF;
    IF v_order.state NOT IN ('awaiting_payment','payment_processing','payment_failed') THEN
      RAISE EXCEPTION 'commerce order cannot accept payment in state %',v_order.state;
    END IF;

    v_amount:=COALESCE(p_amount,v_order.total);
    IF abs(v_amount-v_order.total)>0.001 THEN
      RAISE EXCEPTION 'commerce checkout requires full payment of %',v_order.total;
    END IF;

    v_id := private.active_commerce_payment_intent(v_order.id,'paynow');
    IF v_id IS NOT NULL THEN RETURN v_id; END IF;

    v_ref:=COALESCE(NULLIF(trim(p_external_ref),''),'PN-ORD-'||v_order.id::text||'-'||gen_random_uuid()::text);
    INSERT INTO public.paynow_payment_intents(
      external_ref,method,customer_id,amount,currency,exchange_rate_applied,
      settlement_currency,settlement_amount,settlement_exchange_rate,
      commerce_order_id,metadata,created_by
    ) VALUES(
      v_ref,p_method,v_order.customer_id,v_amount,v_order.currency,v_order.exchange_rate_applied,
      p_settlement_currency,p_settlement_amount,p_settlement_exchange_rate,
      v_order.id,COALESCE(p_metadata,'{}'::jsonb)||jsonb_build_object('commerce_order_id',v_order.id,'channel','storefront'),auth.uid()
    ) RETURNING id INTO v_id;

    UPDATE public.commerce_orders
    SET state='payment_processing',active_payment_provider='paynow',active_payment_intent_id=v_id,updated_at=now()
    WHERE id=v_order.id;

    PERFORM private.enqueue_commerce_event(
      'commerce:'||v_order.id::text||':paynow:'||v_id::text,
      'commerce.payment_intent_created',v_order.id,
      jsonb_build_object('provider','paynow','intent_id',v_id)
    );
    RETURN v_id;
  END IF;

  v_inv:=public._assert_customer_owns_invoice(p_sales_invoice_id);
  IF v_inv.doc_type<>'invoice' OR v_inv.status<>'posted' THEN RAISE EXCEPTION 'only posted invoices can receive payment intents'; END IF;
  v_open:=v_inv.total-v_inv.amount_paid;
  IF v_open<=0 THEN RAISE EXCEPTION 'invoice has no open balance'; END IF;
  v_amount:=COALESCE(p_amount,v_open);
  IF v_amount<=0 OR v_amount>v_open THEN RAISE EXCEPTION 'invalid payment amount'; END IF;
  v_ref:=COALESCE(NULLIF(trim(p_external_ref),''),'PN-CUST-'||v_inv.id::text||'-'||gen_random_uuid()::text);

  INSERT INTO public.paynow_payment_intents(
    external_ref,method,customer_id,amount,currency,exchange_rate_applied,
    settlement_currency,settlement_amount,settlement_exchange_rate,metadata,created_by
  ) VALUES(
    v_ref,p_method,v_inv.customer_id,v_amount,v_inv.currency,v_inv.exchange_rate_applied,
    p_settlement_currency,p_settlement_amount,p_settlement_exchange_rate,
    COALESCE(p_metadata,'{}'::jsonb)||jsonb_build_object('sales_invoice_id',v_inv.id,'channel','storefront'),auth.uid()
  ) RETURNING id INTO v_id;
  RETURN v_id;
END;
$function$;

CREATE OR REPLACE FUNCTION public.create_customer_ecocash_intent(
  p_sales_invoice_id uuid,
  p_payer_msisdn text,
  p_payer_mode text DEFAULT 'other'::text,
  p_external_ref text DEFAULT NULL::text,
  p_amount numeric DEFAULT NULL::numeric,
  p_channel text DEFAULT 'web'::text,
  p_settlement_currency public.currency_code DEFAULT NULL::public.currency_code,
  p_settlement_amount numeric DEFAULT NULL::numeric,
  p_settlement_exchange_rate numeric DEFAULT NULL::numeric,
  p_metadata jsonb DEFAULT '{}'::jsonb
)
RETURNS uuid
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO ''
AS $function$
DECLARE
  v_cust uuid := public._current_customer_id();
  v_order public.commerce_orders%ROWTYPE;
  v_inv public.sales_invoices%ROWTYPE;
  v_open numeric;
  v_amount numeric;
  v_ref text;
  v_id uuid;
  v_msisdn text;
BEGIN
  IF v_cust IS NULL THEN RAISE EXCEPTION 'customer profile required'; END IF;
  IF p_payer_mode IS NULL OR p_payer_mode NOT IN ('whatsapp','saved','other','pos_entered','profile') THEN RAISE EXCEPTION 'invalid payer_mode'; END IF;
  IF p_channel IS NULL OR p_channel NOT IN ('whatsapp_flow','web','pos','ios','android_customer','api') THEN RAISE EXCEPTION 'invalid channel'; END IF;

  v_msisdn:=regexp_replace(trim(COALESCE(p_payer_msisdn,'')),'[^0-9]','','g');
  IF v_msisdn~'^0[0-9]{9}$' THEN v_msisdn:='263'||substr(v_msisdn,2); END IF;
  IF v_msisdn!~'^263[0-9]{9}$' THEN RAISE EXCEPTION 'payer_msisdn must normalize to 263XXXXXXXXX'; END IF;

  SELECT * INTO v_order
  FROM public.commerce_orders
  WHERE id=p_sales_invoice_id AND customer_id=v_cust
  FOR UPDATE;

  IF FOUND THEN
    IF v_order.settled_payment_entry_id IS NOT NULL THEN RAISE EXCEPTION 'commerce order already paid'; END IF;
    IF v_order.reservation_expires_at IS NULL OR v_order.reservation_expires_at<=now() THEN
      PERFORM private.release_commerce_order(v_order.id,'payment_expired','payment attempted after reservation expiry');
      RAISE EXCEPTION 'checkout reservation expired; rebuild checkout';
    END IF;
    IF v_order.state NOT IN ('awaiting_payment','payment_processing','payment_failed') THEN RAISE EXCEPTION 'commerce order cannot accept payment in state %',v_order.state; END IF;

    v_amount:=COALESCE(p_amount,v_order.total);
    IF abs(v_amount-v_order.total)>0.001 THEN RAISE EXCEPTION 'commerce checkout requires full payment of %',v_order.total; END IF;

    v_id := private.active_commerce_payment_intent(v_order.id,'ecocash');
    IF v_id IS NOT NULL THEN RETURN v_id; END IF;

    v_ref:=COALESCE(NULLIF(trim(p_external_ref),''),'EC-ORD-'||v_order.id::text||'-'||gen_random_uuid()::text);
    INSERT INTO public.ecocash_payment_intents(
      external_ref,payer_msisdn,payer_mode,channel,customer_id,sales_invoice_id,
      amount,currency,exchange_rate_applied,settlement_currency,settlement_amount,
      settlement_exchange_rate,commerce_order_id,metadata,created_by
    ) VALUES(
      v_ref,v_msisdn,p_payer_mode,p_channel,v_order.customer_id,NULL,
      v_amount,v_order.currency,v_order.exchange_rate_applied,p_settlement_currency,
      p_settlement_amount,p_settlement_exchange_rate,v_order.id,
      COALESCE(p_metadata,'{}'::jsonb)||jsonb_build_object('commerce_order_id',v_order.id,'channel',p_channel),auth.uid()
    ) RETURNING id INTO v_id;

    UPDATE public.commerce_orders
    SET state='payment_processing',active_payment_provider='ecocash',active_payment_intent_id=v_id,updated_at=now()
    WHERE id=v_order.id;

    PERFORM private.enqueue_commerce_event(
      'commerce:'||v_order.id::text||':ecocash:'||v_id::text,
      'commerce.payment_intent_created',v_order.id,
      jsonb_build_object('provider','ecocash','intent_id',v_id)
    );
    RETURN v_id;
  END IF;

  v_inv:=public._assert_customer_owns_invoice(p_sales_invoice_id);
  IF v_inv.doc_type<>'invoice' OR v_inv.status<>'posted' THEN RAISE EXCEPTION 'only posted invoices can receive payment intents'; END IF;
  v_open:=v_inv.total-v_inv.amount_paid;
  IF v_open<=0 THEN RAISE EXCEPTION 'invoice has no open balance'; END IF;
  v_amount:=COALESCE(p_amount,v_open);
  IF v_amount<=0 OR v_amount>v_open THEN RAISE EXCEPTION 'invalid payment amount'; END IF;
  v_ref:=COALESCE(NULLIF(trim(p_external_ref),''),'EC-CUST-'||v_inv.id::text||'-'||gen_random_uuid()::text);

  INSERT INTO public.ecocash_payment_intents(
    external_ref,payer_msisdn,payer_mode,channel,customer_id,sales_invoice_id,
    amount,currency,exchange_rate_applied,settlement_currency,settlement_amount,
    settlement_exchange_rate,metadata,created_by
  ) VALUES(
    v_ref,v_msisdn,p_payer_mode,p_channel,v_inv.customer_id,v_inv.id,
    v_amount,v_inv.currency,v_inv.exchange_rate_applied,p_settlement_currency,
    p_settlement_amount,p_settlement_exchange_rate,
    COALESCE(p_metadata,'{}'::jsonb)||jsonb_build_object('sales_invoice_id',v_inv.id,'channel',p_channel),auth.uid()
  ) RETURNING id INTO v_id;
  RETURN v_id;
END;
$function$;

CREATE OR REPLACE FUNCTION public.create_customer_contipay_intent(
  p_sales_invoice_id uuid,
  p_method public.contipay_method,
  p_external_ref text DEFAULT NULL::text,
  p_amount numeric DEFAULT NULL::numeric,
  p_settlement_currency public.currency_code DEFAULT NULL::public.currency_code,
  p_settlement_amount numeric DEFAULT NULL::numeric,
  p_settlement_exchange_rate numeric DEFAULT NULL::numeric,
  p_metadata jsonb DEFAULT '{}'::jsonb
)
RETURNS uuid
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO ''
AS $function$
DECLARE
  v_cust uuid := public._current_customer_id();
  v_order public.commerce_orders%ROWTYPE;
  v_inv public.sales_invoices%ROWTYPE;
  v_open numeric;
  v_amount numeric;
  v_ref text;
  v_id uuid;
BEGIN
  IF v_cust IS NULL THEN RAISE EXCEPTION 'customer profile required'; END IF;

  SELECT * INTO v_order
  FROM public.commerce_orders
  WHERE id=p_sales_invoice_id AND customer_id=v_cust
  FOR UPDATE;

  IF FOUND THEN
    IF v_order.settled_payment_entry_id IS NOT NULL THEN RAISE EXCEPTION 'commerce order already paid'; END IF;
    IF v_order.reservation_expires_at IS NULL OR v_order.reservation_expires_at<=now() THEN
      PERFORM private.release_commerce_order(v_order.id,'payment_expired','payment attempted after reservation expiry');
      RAISE EXCEPTION 'checkout reservation expired; rebuild checkout';
    END IF;
    IF v_order.state NOT IN ('awaiting_payment','payment_processing','payment_failed') THEN RAISE EXCEPTION 'commerce order cannot accept payment in state %',v_order.state; END IF;

    v_amount:=COALESCE(p_amount,v_order.total);
    IF abs(v_amount-v_order.total)>0.001 THEN RAISE EXCEPTION 'commerce checkout requires full payment of %',v_order.total; END IF;

    v_id := private.active_commerce_payment_intent(v_order.id,'contipay');
    IF v_id IS NOT NULL THEN RETURN v_id; END IF;

    v_ref:=COALESCE(NULLIF(trim(p_external_ref),''),'CP-ORD-'||v_order.id::text||'-'||gen_random_uuid()::text);
    INSERT INTO public.contipay_payment_intents(
      external_ref,method,customer_id,amount,currency,exchange_rate_applied,
      settlement_currency,settlement_amount,settlement_exchange_rate,
      commerce_order_id,metadata,created_by
    ) VALUES(
      v_ref,p_method,v_order.customer_id,v_amount,v_order.currency,v_order.exchange_rate_applied,
      p_settlement_currency,p_settlement_amount,p_settlement_exchange_rate,
      v_order.id,COALESCE(p_metadata,'{}'::jsonb)||jsonb_build_object('commerce_order_id',v_order.id,'channel','storefront'),auth.uid()
    ) RETURNING id INTO v_id;

    UPDATE public.commerce_orders
    SET state='payment_processing',active_payment_provider='contipay',active_payment_intent_id=v_id,updated_at=now()
    WHERE id=v_order.id;

    PERFORM private.enqueue_commerce_event(
      'commerce:'||v_order.id::text||':contipay:'||v_id::text,
      'commerce.payment_intent_created',v_order.id,
      jsonb_build_object('provider','contipay','intent_id',v_id)
    );
    RETURN v_id;
  END IF;

  v_inv:=public._assert_customer_owns_invoice(p_sales_invoice_id);
  IF v_inv.doc_type<>'invoice' OR v_inv.status<>'posted' THEN RAISE EXCEPTION 'only posted invoices can receive payment intents'; END IF;
  v_open:=v_inv.total-v_inv.amount_paid;
  IF v_open<=0 THEN RAISE EXCEPTION 'invoice has no open balance'; END IF;
  v_amount:=COALESCE(p_amount,v_open);
  IF v_amount<=0 OR v_amount>v_open THEN RAISE EXCEPTION 'invalid payment amount'; END IF;
  v_ref:=COALESCE(NULLIF(trim(p_external_ref),''),'CP-CUST-'||v_inv.id::text||'-'||gen_random_uuid()::text);

  INSERT INTO public.contipay_payment_intents(
    external_ref,method,customer_id,amount,currency,exchange_rate_applied,
    settlement_currency,settlement_amount,settlement_exchange_rate,metadata,created_by
  ) VALUES(
    v_ref,p_method,v_inv.customer_id,v_amount,v_inv.currency,v_inv.exchange_rate_applied,
    p_settlement_currency,p_settlement_amount,p_settlement_exchange_rate,
    COALESCE(p_metadata,'{}'::jsonb)||jsonb_build_object('sales_invoice_id',v_inv.id,'channel','storefront'),auth.uid()
  ) RETURNING id INTO v_id;
  RETURN v_id;
END;
$function$;
