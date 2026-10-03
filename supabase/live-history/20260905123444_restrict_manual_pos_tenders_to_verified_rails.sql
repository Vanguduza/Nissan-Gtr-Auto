-- Exported from the hosted project's supabase_migrations.schema_migrations (20260905123444 restrict_manual_pos_tenders_to_verified_rails).
-- Source of record for what production ran; see supabase/live-history/README.md.

-- P0 payment-integrity hardening.
-- Manual POS tender settlement may only accept rails whose truth can be established
-- at the till. External PSP tenders must be finalized by verified provider callbacks.
CREATE OR REPLACE FUNCTION public.settle_invoice_tenders(p_invoice_id uuid, p_tenders jsonb)
RETURNS uuid
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO 'public'
AS $function$
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

  -- Validate the complete tender set before creating any payment entry.
  FOR v_elem IN SELECT * FROM jsonb_array_elements(p_tenders)
  LOOP
    IF v_elem->>'tender' IS NULL THEN
      RAISE EXCEPTION 'each tender line requires tender';
    END IF;
    v_tender := (v_elem->>'tender')::public.payment_tender;

    IF v_tender NOT IN ('cash'::public.payment_tender,
                        'bank'::public.payment_tender,
                        'store_credit'::public.payment_tender) THEN
      RAISE EXCEPTION 'external provider tender % requires verified provider settlement', v_tender;
    END IF;

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
$function$;

REVOKE ALL ON FUNCTION public.settle_invoice_tenders(uuid,jsonb) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.settle_invoice_tenders(uuid,jsonb) TO authenticated, service_role;

REVOKE ALL ON FUNCTION public.checkout_pos_cart_with_tenders(uuid,jsonb,text,text,text) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.checkout_pos_cart_with_tenders(uuid,jsonb,text,text,text) TO authenticated, service_role;
