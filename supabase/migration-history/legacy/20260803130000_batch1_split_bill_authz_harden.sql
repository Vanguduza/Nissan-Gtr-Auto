-- Batch 1 harden: gate walk-in helper + authz before DEFINER cart mutate (RLS audit).

CREATE OR REPLACE FUNCTION public.ensure_pos_walkin_customer()
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_id UUID;
BEGIN
  -- Sales/payments/admin only — not open to storefront customers.
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
  -- Authz before any DEFINER DML (do not mutate carts for arbitrary authenticated callers).
  PERFORM public._require_payments_staff();
  PERFORM public._require_cart_mutate(p_cart_id);

  IF p_tenders IS NULL
     OR jsonb_typeof(p_tenders) <> 'array'
     OR jsonb_array_length(p_tenders) < 1 THEN
    RAISE EXCEPTION 'p_tenders required for split-bill checkout';
  END IF;

  -- Force AR path (debit 1200) so tender PEs can clear via per-method GLs.
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

-- Align tender map DML with finance write policy (PostgREST path).
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.payment_tender_gl_accounts TO authenticated;

-- Tighten SELECT to staff who handle payments (not every authenticated user).
DROP POLICY IF EXISTS payment_tender_gl_select ON public.payment_tender_gl_accounts;
CREATE POLICY payment_tender_gl_select ON public.payment_tender_gl_accounts
  FOR SELECT TO authenticated
  USING (
    EXISTS (
      SELECT 1 FROM public.staff_roles sr
      WHERE sr.user_id = auth.uid()
        AND sr.role IN ('admin', 'finance', 'sales')
    )
  );
