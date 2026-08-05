-- Batch 1 Phase 0: CoA display_name + per-payment-method GL accounts
-- EcoCash/Paynow/ContiPay/Cash tenders debit their own clearing accounts (not blanket 1100).
-- No ZIMRA. Append-only ledger unchanged — only which asset account is debited on PE post.

ALTER TABLE public.chart_of_accounts
  ADD COLUMN IF NOT EXISTS display_name TEXT;

UPDATE public.chart_of_accounts
SET display_name = name
WHERE display_name IS NULL;

ALTER TABLE public.chart_of_accounts
  ALTER COLUMN display_name SET NOT NULL;

COMMENT ON COLUMN public.chart_of_accounts.display_name IS
  'Plain-English primary UI label; code remains technical/secondary.';

-- Seed / clarify per-method clearing accounts (assets; sweep to 1100 later via imprest-style JE)
INSERT INTO public.chart_of_accounts (code, name, display_name, account_type) VALUES
  ('1100', 'Operating Bank', 'Operating bank account', 'asset'),
  ('1110', 'Petty Cash', 'Petty cash float', 'asset'),
  ('1120', 'Cash Sales Till', 'Cash till', 'asset'),
  ('1130', 'Online Payment Clearing', 'Online payments (legacy clearing)', 'asset'),
  ('1140', 'ContiPay Clearing', 'ContiPay', 'asset'),
  ('1150', 'Paynow Clearing', 'Paynow', 'asset'),
  ('1160', 'EcoCash Wallet', 'EcoCash (direct)', 'asset')
ON CONFLICT (code) DO UPDATE
SET
  display_name = EXCLUDED.display_name,
  name = COALESCE(public.chart_of_accounts.name, EXCLUDED.name),
  is_active = true;

-- Explicit mapping (editable later without redeploying PE post logic)
CREATE TABLE IF NOT EXISTS public.payment_tender_gl_accounts (
  tender public.payment_tender PRIMARY KEY,
  account_code VARCHAR(10) NOT NULL REFERENCES public.chart_of_accounts (code),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

ALTER TABLE public.payment_tender_gl_accounts ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS payment_tender_gl_select ON public.payment_tender_gl_accounts;
CREATE POLICY payment_tender_gl_select ON public.payment_tender_gl_accounts
  FOR SELECT TO authenticated
  USING (true);

DROP POLICY IF EXISTS payment_tender_gl_finance_write ON public.payment_tender_gl_accounts;
CREATE POLICY payment_tender_gl_finance_write ON public.payment_tender_gl_accounts
  FOR ALL TO authenticated
  USING (
    EXISTS (
      SELECT 1 FROM public.staff_roles sr
      WHERE sr.user_id = auth.uid()
        AND sr.role IN ('admin', 'finance')
    )
  )
  WITH CHECK (
    EXISTS (
      SELECT 1 FROM public.staff_roles sr
      WHERE sr.user_id = auth.uid()
        AND sr.role IN ('admin', 'finance')
    )
  );

GRANT SELECT ON TABLE public.payment_tender_gl_accounts TO authenticated;
GRANT ALL ON TABLE public.payment_tender_gl_accounts TO service_role;

INSERT INTO public.payment_tender_gl_accounts (tender, account_code) VALUES
  ('cash', '1120'),
  ('bank', '1100'),
  ('contipay', '1140'),
  ('paynow', '1150'),
  ('ecocash', '1160'),
  ('store_credit', '2200')
ON CONFLICT (tender) DO UPDATE
SET account_code = EXCLUDED.account_code, updated_at = now();

CREATE OR REPLACE FUNCTION public.gl_account_for_payment_tender(
  p_tender public.payment_tender
)
RETURNS TEXT
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_code TEXT;
BEGIN
  SELECT account_code INTO v_code
  FROM public.payment_tender_gl_accounts
  WHERE tender = p_tender;
  IF v_code IS NULL THEN
    RETURN CASE p_tender
      WHEN 'cash' THEN '1120'
      WHEN 'bank' THEN '1100'
      WHEN 'contipay' THEN '1140'
      WHEN 'paynow' THEN '1150'
      WHEN 'ecocash' THEN '1160'
      WHEN 'store_credit' THEN '2200'
      ELSE '1100'
    END;
  END IF;
  RETURN v_code;
END;
$$;

REVOKE ALL ON FUNCTION public.gl_account_for_payment_tender(public.payment_tender) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.gl_account_for_payment_tender(public.payment_tender)
  TO authenticated, service_role;

-- Patch PE post: debit per-tender GL (EcoCash settlements land on 1160, not ContiPay/Paynow)
CREATE OR REPLACE FUNCTION public.post_payment_entry(p_payment_entry_id UUID)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_pe public.payment_entries%ROWTYPE;
  v_alloc RECORD;
  v_sum NUMERIC := 0;
  v_overpay NUMERIC;
  v_cash_acct TEXT;
  v_lines JSONB := '[]'::jsonb;
  v_journal UUID;
  v_inv public.sales_invoices%ROWTYPE;
  v_open NUMERIC;
  v_event TEXT;
  v_all_cleared BOOLEAN := true;
BEGIN
  PERFORM public._require_payments_staff();
  PERFORM public._payments_rpc_enter();

  SELECT * INTO v_pe FROM public.payment_entries WHERE id = p_payment_entry_id FOR UPDATE;
  IF NOT FOUND THEN
    RAISE EXCEPTION 'payment entry not found: %', p_payment_entry_id;
  END IF;
  IF v_pe.status = 'posted' THEN
    RETURN p_payment_entry_id;
  END IF;
  IF v_pe.status <> 'draft' THEN
    RAISE EXCEPTION 'only draft payments can be posted';
  END IF;

  SELECT COALESCE(SUM(amount), 0) INTO v_sum
  FROM public.payment_allocations
  WHERE payment_entry_id = p_payment_entry_id;

  IF v_sum <= 0 AND v_pe.tender <> 'store_credit' THEN
    RAISE EXCEPTION 'payment requires at least one allocation';
  END IF;
  IF v_sum > v_pe.amount + 0.001 THEN
    RAISE EXCEPTION 'allocations exceed payment amount';
  END IF;

  FOR v_alloc IN
    SELECT * FROM public.payment_allocations WHERE payment_entry_id = p_payment_entry_id
  LOOP
    SELECT * INTO v_inv FROM public.sales_invoices WHERE id = v_alloc.sales_invoice_id FOR UPDATE;
    v_open := v_inv.total - v_inv.amount_paid;
    IF v_alloc.amount > v_open + 0.001 THEN
      RAISE EXCEPTION 'over-allocate denied at post: invoice %', v_inv.document_number;
    END IF;
  END LOOP;

  IF v_pe.tender = 'store_credit' THEN
    PERFORM public._append_store_credit(
      v_pe.customer_id, 'redeem', v_pe.amount, v_pe.currency,
      v_pe.exchange_rate_applied, p_payment_entry_id, NULL,
      format('Redeem on %s', COALESCE(v_pe.document_number, p_payment_entry_id::text))
    );
    v_cash_acct := public.gl_account_for_payment_tender('store_credit'::public.payment_tender);
  ELSE
    v_cash_acct := public.gl_account_for_payment_tender(v_pe.tender);
  END IF;

  v_overpay := round(v_pe.amount - v_sum, 2);
  v_lines := jsonb_build_array(
    jsonb_build_object(
      'account_code', v_cash_acct,
      'debit', v_pe.amount, 'credit', 0, 'currency', v_pe.currency
    )
  );
  IF v_sum > 0 THEN
    v_lines := v_lines || jsonb_build_array(
      jsonb_build_object(
        'account_code', '1200',
        'debit', 0, 'credit', v_sum, 'currency', v_pe.currency
      )
    );
  END IF;
  IF v_overpay > 0 THEN
    v_lines := v_lines || jsonb_build_array(
      jsonb_build_object(
        'account_code', '2200',
        'debit', 0, 'credit', v_overpay, 'currency', v_pe.currency
      )
    );
  END IF;

  v_journal := public.post_journal_entry(
    CURRENT_DATE,
    format('Payment %s', COALESCE(v_pe.document_number, p_payment_entry_id::text)),
    v_pe.currency,
    v_pe.exchange_rate_applied,
    v_lines
  );

  IF v_overpay > 0 THEN
    PERFORM public._append_store_credit(
      v_pe.customer_id, 'issue', v_overpay, v_pe.currency,
      v_pe.exchange_rate_applied, p_payment_entry_id, v_journal,
      'Overpay → store credit'
    );
    PERFORM public.emit_domain_event(
      'refund_issued',
      'sc:overpay:' || p_payment_entry_id::text,
      jsonb_build_object(
        'payment_entry_id', p_payment_entry_id,
        'amount', v_overpay,
        'currency', v_pe.currency
      ),
      auth.uid(),
      format('GTR Auto: store credit issued %s %s', v_overpay, v_pe.currency)
    );
  END IF;

  FOR v_alloc IN
    SELECT * FROM public.payment_allocations WHERE payment_entry_id = p_payment_entry_id
  LOOP
    UPDATE public.sales_invoices
    SET amount_paid = amount_paid + v_alloc.amount
    WHERE id = v_alloc.sales_invoice_id
    RETURNING * INTO v_inv;

    IF v_inv.amount_paid + 0.001 < v_inv.total THEN
      v_all_cleared := false;
    END IF;

    UPDATE public.customers
    SET open_balance = GREATEST(0, open_balance - v_alloc.amount),
        updated_at = now()
    WHERE id = v_pe.customer_id;
  END LOOP;

  UPDATE public.payment_entries
  SET
    status = 'posted',
    journal_entry_id = v_journal,
    store_credit_issued = COALESCE(v_overpay, 0),
    posted_by = auth.uid(),
    posted_at = now(),
    updated_at = now()
  WHERE id = p_payment_entry_id;

  IF v_all_cleared AND v_sum > 0 AND v_overpay = 0 THEN
    v_event := 'payment_received';
  ELSIF v_sum > 0 AND NOT v_all_cleared THEN
    v_event := 'payment_partial';
  ELSE
    v_event := 'payment_received';
  END IF;

  PERFORM public.emit_domain_event(
    v_event,
    'payment:' || v_event || ':' || p_payment_entry_id::text,
    jsonb_build_object(
      'payment_entry_id', p_payment_entry_id,
      'amount', v_pe.amount,
      'allocated', v_sum,
      'currency', v_pe.currency,
      'tender', v_pe.tender,
      'gl_account', v_cash_acct
    ),
    auth.uid(),
    format('GTR Auto: %s %s %s', v_event, v_pe.amount, v_pe.currency)
  );

  RETURN p_payment_entry_id;
END;
$$;
