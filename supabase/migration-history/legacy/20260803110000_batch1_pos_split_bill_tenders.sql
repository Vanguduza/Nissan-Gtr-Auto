-- Batch 1 §1.3 — split-bill / multi-tender settlement after POS checkout.
-- Posts one Payment Entry per tender → per-method GL via gl_account_for_payment_tender.
-- No ZIMRA. Append-only: each tender posts its own JE; corrections remain reverse-only.

-- ---------------------------------------------------------------------------
-- Walk-in customer for cash-and-carry when cart has no named customer
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.ensure_pos_walkin_customer()
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_id UUID;
BEGIN
  PERFORM public._require_payments_staff();

  SELECT id INTO v_id
  FROM public.customers
  WHERE display_name = 'POS Walk-in'
  ORDER BY created_at
  LIMIT 1;

  IF v_id IS NOT NULL THEN
    RETURN v_id;
  END IF;

  INSERT INTO public.customers (display_name, currency, credit_limit, credit_hold)
  VALUES ('POS Walk-in', 'USD', 0, false)
  RETURNING id INTO v_id;

  RETURN v_id;
END;
$$;

REVOKE ALL ON FUNCTION public.ensure_pos_walkin_customer() FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.ensure_pos_walkin_customer()
  TO authenticated, service_role;

-- ---------------------------------------------------------------------------
-- settle_invoice_tenders: array of { tender, amount, currency?, exchange_rate? }
-- Amounts must be in the invoice currency and sum to open balance (total − amount_paid).
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.settle_invoice_tenders(
  p_invoice_id UUID,
  p_tenders JSONB
)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_inv public.sales_invoices%ROWTYPE;
  v_elem JSONB;
  v_tender public.payment_tender;
  v_amt NUMERIC;
  v_cur public.currency_code;
  v_rate NUMERIC;
  v_sum NUMERIC := 0;
  v_open NUMERIC;
  v_pe UUID;
  v_customer UUID;
BEGIN
  PERFORM public._require_payments_staff();
  PERFORM public._payments_rpc_enter();

  IF p_tenders IS NULL OR jsonb_typeof(p_tenders) <> 'array' OR jsonb_array_length(p_tenders) < 1 THEN
    RAISE EXCEPTION 'p_tenders must be a non-empty JSON array';
  END IF;

  SELECT * INTO v_inv FROM public.sales_invoices WHERE id = p_invoice_id FOR UPDATE;
  IF NOT FOUND THEN
    RAISE EXCEPTION 'invoice not found: %', p_invoice_id;
  END IF;
  IF v_inv.status <> 'posted' OR v_inv.doc_type <> 'invoice' THEN
    RAISE EXCEPTION 'only posted sales invoices can be settled (got %)', v_inv.status;
  END IF;

  v_open := round(v_inv.total - COALESCE(v_inv.amount_paid, 0), 2);
  IF v_open <= 0 THEN
    RAISE EXCEPTION 'invoice has no open balance';
  END IF;

  v_customer := v_inv.customer_id;
  IF v_customer IS NULL THEN
    v_customer := public.ensure_pos_walkin_customer();
    UPDATE public.sales_invoices
    SET customer_id = v_customer
    WHERE id = p_invoice_id;
    v_inv.customer_id := v_customer;
  END IF;

  FOR v_elem IN SELECT * FROM jsonb_array_elements(p_tenders)
  LOOP
    IF v_elem->>'tender' IS NULL THEN
      RAISE EXCEPTION 'each tender line requires tender';
    END IF;
    v_tender := (v_elem->>'tender')::public.payment_tender;
    v_amt := round(COALESCE((v_elem->>'amount')::numeric, 0), 2);
    IF v_amt <= 0 THEN
      RAISE EXCEPTION 'tender amount must be > 0';
    END IF;
    v_cur := COALESCE(
      NULLIF(v_elem->>'currency', '')::public.currency_code,
      v_inv.currency
    );
    IF v_cur <> v_inv.currency THEN
      RAISE EXCEPTION 'mixed-currency tenders not supported in this release (got %, invoice %)',
        v_cur, v_inv.currency;
    END IF;
    v_rate := COALESCE(
      NULLIF(v_elem->>'exchange_rate', '')::numeric,
      v_inv.exchange_rate_applied,
      1
    );
    v_sum := v_sum + v_amt;
  END LOOP;

  IF abs(v_sum - v_open) > 0.01 THEN
    RAISE EXCEPTION 'tenders sum % must equal open balance %', v_sum, v_open;
  END IF;

  FOR v_elem IN SELECT * FROM jsonb_array_elements(p_tenders)
  LOOP
    v_tender := (v_elem->>'tender')::public.payment_tender;
    v_amt := round((v_elem->>'amount')::numeric, 2);
    v_cur := COALESCE(
      NULLIF(v_elem->>'currency', '')::public.currency_code,
      v_inv.currency
    );
    v_rate := COALESCE(
      NULLIF(v_elem->>'exchange_rate', '')::numeric,
      v_inv.exchange_rate_applied,
      1
    );

    v_pe := public.create_payment_entry(
      v_customer,
      v_tender,
      v_amt,
      v_cur,
      v_rate,
      format('POS split-bill %s', COALESCE(v_inv.document_number, p_invoice_id::text))
    );

    PERFORM public.allocate_payment(
      v_pe,
      jsonb_build_array(
        jsonb_build_object(
          'sales_invoice_id', p_invoice_id,
          'amount', v_amt,
          'currency', v_cur,
          'exchange_rate', v_rate
        )
      )
    );

    PERFORM public.post_payment_entry(v_pe);
  END LOOP;

  RETURN p_invoice_id;
END;
$$;

REVOKE ALL ON FUNCTION public.settle_invoice_tenders(UUID, JSONB) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.settle_invoice_tenders(UUID, JSONB)
  TO authenticated, service_role;

-- ---------------------------------------------------------------------------
-- checkout_pos_cart_with_tenders — checkout then settle (same receipt contact args)
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.checkout_pos_cart_with_tenders(
  p_cart_id UUID,
  p_tenders JSONB,
  p_receipt_email TEXT DEFAULT NULL,
  p_receipt_whatsapp_e164 TEXT DEFAULT NULL,
  p_receipt_phone_e164 TEXT DEFAULT NULL
)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_inv UUID;
  v_status TEXT;
BEGIN
  PERFORM public._require_payments_staff();
  PERFORM public._require_cart_mutate(p_cart_id);

  IF p_tenders IS NULL
     OR jsonb_typeof(p_tenders) <> 'array'
     OR jsonb_array_length(p_tenders) < 1 THEN
    RAISE EXCEPTION 'p_tenders required for split-bill checkout';
  END IF;

  -- Force AR path (debit 1200) so tender PEs can clear via per-method GLs.
  -- Walk-in cash checkout alone still debits 1100; split-bill must not double-cash.
  UPDATE public.pos_carts c
  SET
    customer_id = COALESCE(c.customer_id, public.ensure_pos_walkin_customer()),
    updated_at = now()
  WHERE c.id = p_cart_id
    AND c.customer_id IS NULL;

  v_inv := public.checkout_pos_cart(
    p_cart_id,
    p_receipt_email,
    p_receipt_whatsapp_e164,
    p_receipt_phone_e164
  );

  SELECT status::text INTO v_status FROM public.sales_invoices WHERE id = v_inv;
  IF v_status = 'on_hold' THEN
    RETURN v_inv;
  END IF;

  PERFORM public.settle_invoice_tenders(v_inv, p_tenders);
  RETURN v_inv;
END;
$$;

REVOKE ALL ON FUNCTION public.checkout_pos_cart_with_tenders(UUID, JSONB, TEXT, TEXT, TEXT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.checkout_pos_cart_with_tenders(UUID, JSONB, TEXT, TEXT, TEXT)
  TO authenticated, service_role;
