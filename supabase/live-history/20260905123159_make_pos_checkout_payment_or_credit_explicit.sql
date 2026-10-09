-- Exported from the hosted project's supabase_migrations.schema_migrations (20260905123159 make_pos_checkout_payment_or_credit_explicit).
-- Source of record for what production ran; see supabase/live-history/README.md.

-- P0 commercial authorization: checkout_pos_cart is an internal invoice-construction
-- primitive. Staff must choose either verified tenders or explicit on-account credit.
CREATE OR REPLACE FUNCTION public.checkout_pos_cart_on_account(
  p_cart_id uuid,
  p_receipt_email text DEFAULT NULL,
  p_receipt_whatsapp_e164 text DEFAULT NULL,
  p_receipt_phone_e164 text DEFAULT NULL
)
RETURNS uuid
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = 'public'
AS $function$
DECLARE
  v_cart public.pos_carts%ROWTYPE;
  v_cust public.customers%ROWTYPE;
  v_total numeric;
  v_invoice uuid;
BEGIN
  PERFORM public._require_payments_staff();
  PERFORM public._require_cart_mutate(p_cart_id);

  SELECT * INTO v_cart
  FROM public.pos_carts
  WHERE id = p_cart_id
  FOR UPDATE;

  IF NOT FOUND OR v_cart.status <> 'open' THEN
    RAISE EXCEPTION 'open cart required';
  END IF;
  IF v_cart.customer_id IS NULL THEN
    RAISE EXCEPTION 'registered customer required for on-account checkout';
  END IF;

  SELECT * INTO v_cust
  FROM public.customers
  WHERE id = v_cart.customer_id
  FOR UPDATE;

  IF NOT FOUND THEN
    RAISE EXCEPTION 'customer not found';
  END IF;
  IF v_cust.credit_hold THEN
    RAISE EXCEPTION 'customer is on credit hold';
  END IF;
  IF COALESCE(v_cust.credit_limit, 0) <= 0 THEN
    RAISE EXCEPTION 'customer has no approved credit limit';
  END IF;
  IF v_cart.currency IS DISTINCT FROM v_cust.currency THEN
    RAISE EXCEPTION 'on-account cart currency % must match customer account currency %', v_cart.currency, v_cust.currency;
  END IF;

  SELECT COALESCE(sum(line_total), 0)
  INTO v_total
  FROM public.pos_cart_lines
  WHERE cart_id = p_cart_id;

  IF v_total <= 0 THEN
    RAISE EXCEPTION 'cart total must be > 0';
  END IF;
  IF COALESCE(v_cust.open_balance, 0) + v_total > v_cust.credit_limit THEN
    RAISE EXCEPTION 'insufficient customer credit: open balance %, cart %, limit %',
      COALESCE(v_cust.open_balance, 0), v_total, v_cust.credit_limit;
  END IF;

  v_invoice := public.checkout_pos_cart(
    p_cart_id,
    p_receipt_email,
    p_receipt_whatsapp_e164,
    p_receipt_phone_e164
  );

  IF NOT EXISTS (
    SELECT 1 FROM public.sales_invoices si
    WHERE si.id = v_invoice AND si.status = 'posted' AND si.doc_type = 'invoice'
  ) THEN
    RAISE EXCEPTION 'on-account checkout did not produce a posted invoice';
  END IF;

  PERFORM public.emit_domain_event(
    'pos_credit_sale_authorized',
    'pos:credit:' || v_invoice::text,
    jsonb_build_object(
      'invoice_id', v_invoice,
      'cart_id', p_cart_id,
      'customer_id', v_cart.customer_id,
      'amount', v_total,
      'currency', v_cart.currency,
      'credit_limit', v_cust.credit_limit,
      'open_balance_before', COALESCE(v_cust.open_balance, 0)
    ),
    auth.uid(),
    format('POS on-account sale authorized: %s %s', v_total, v_cart.currency)
  );

  RETURN v_invoice;
END;
$function$;

REVOKE ALL ON FUNCTION public.checkout_pos_cart_on_account(uuid,text,text,text) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.checkout_pos_cart_on_account(uuid,text,text,text) TO authenticated, service_role;

-- Bare checkout may only be invoked by trusted service/internal SECURITY DEFINER callers.
REVOKE ALL ON FUNCTION public.checkout_pos_cart(uuid,text,text,text) FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.checkout_pos_cart(uuid,text,text,text) TO service_role;
