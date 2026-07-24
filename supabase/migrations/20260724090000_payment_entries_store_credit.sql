-- Phase 13: Payment Entry, allocations, store credit ledger
-- Exclusions: no ZIMRA/fiscal; no payroll tax. Ledger append-only via post/reverse JEs.

CREATE TYPE public.payment_tender AS ENUM ('cash', 'bank', 'contipay', 'store_credit');
CREATE TYPE public.payment_entry_status AS ENUM ('draft', 'posted', 'cancelled');
CREATE TYPE public.store_credit_movement AS ENUM ('issue', 'redeem', 'reverse');

INSERT INTO public.naming_series (prefix, description, pad_length) VALUES
  ('PE-', 'Payment entry', 5),
  ('SC-', 'Store credit movement', 5)
ON CONFLICT (prefix) DO NOTHING;

-- ---------------------------------------------------------------------------
-- Tables
-- ---------------------------------------------------------------------------
CREATE TABLE public.payment_entries (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  document_number TEXT,
  status public.payment_entry_status NOT NULL DEFAULT 'draft',
  customer_id UUID NOT NULL REFERENCES public.customers (id),
  tender public.payment_tender NOT NULL,
  currency public.currency_code NOT NULL DEFAULT 'USD',
  exchange_rate_applied NUMERIC(18, 8) NOT NULL DEFAULT 1,
  amount NUMERIC(18, 2) NOT NULL CHECK (amount > 0),
  -- Dual-currency settlement display (e.g. ContiPay charged ZIG, allocated USD)
  settlement_currency public.currency_code,
  settlement_amount NUMERIC(18, 2),
  settlement_exchange_rate NUMERIC(18, 8),
  notes TEXT,
  journal_entry_id UUID REFERENCES public.journal_entries (id),
  reversal_journal_entry_id UUID REFERENCES public.journal_entries (id),
  store_credit_issued NUMERIC(18, 2) NOT NULL DEFAULT 0 CHECK (store_credit_issued >= 0),
  posted_by UUID REFERENCES auth.users (id),
  posted_at TIMESTAMPTZ,
  cancelled_at TIMESTAMPTZ,
  created_by UUID REFERENCES auth.users (id),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT payment_settlement_pair CHECK (
    (settlement_currency IS NULL AND settlement_amount IS NULL)
    OR (settlement_currency IS NOT NULL AND settlement_amount IS NOT NULL AND settlement_amount > 0)
  )
);

CREATE TABLE public.payment_allocations (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  payment_entry_id UUID NOT NULL REFERENCES public.payment_entries (id) ON DELETE CASCADE,
  sales_invoice_id UUID NOT NULL REFERENCES public.sales_invoices (id) ON DELETE RESTRICT,
  amount NUMERIC(18, 2) NOT NULL CHECK (amount > 0),
  currency public.currency_code NOT NULL,
  exchange_rate_applied NUMERIC(18, 8) NOT NULL DEFAULT 1,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (payment_entry_id, sales_invoice_id)
);

CREATE INDEX payment_entries_customer_idx ON public.payment_entries (customer_id);
CREATE INDEX payment_entries_status_idx ON public.payment_entries (status);
CREATE INDEX payment_allocations_invoice_idx ON public.payment_allocations (sales_invoice_id);

CREATE TABLE public.store_credit_accounts (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  customer_id UUID NOT NULL UNIQUE REFERENCES public.customers (id) ON DELETE RESTRICT,
  currency public.currency_code NOT NULL DEFAULT 'USD',
  balance NUMERIC(18, 2) NOT NULL DEFAULT 0 CHECK (balance >= 0),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE public.store_credit_ledger (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  document_number TEXT,
  account_id UUID NOT NULL REFERENCES public.store_credit_accounts (id) ON DELETE RESTRICT,
  customer_id UUID NOT NULL REFERENCES public.customers (id) ON DELETE RESTRICT,
  movement public.store_credit_movement NOT NULL,
  amount NUMERIC(18, 2) NOT NULL CHECK (amount > 0),
  currency public.currency_code NOT NULL,
  exchange_rate_applied NUMERIC(18, 8) NOT NULL DEFAULT 1,
  balance_after NUMERIC(18, 2) NOT NULL CHECK (balance_after >= 0),
  payment_entry_id UUID REFERENCES public.payment_entries (id),
  journal_entry_id UUID REFERENCES public.journal_entries (id),
  reason TEXT,
  reverses_ledger_id UUID REFERENCES public.store_credit_ledger (id),
  created_by UUID REFERENCES auth.users (id),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX store_credit_ledger_account_idx
  ON public.store_credit_ledger (account_id, created_at DESC);

-- Append-only store credit ledger
CREATE OR REPLACE FUNCTION public.forbid_store_credit_ledger_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
  RAISE EXCEPTION 'store_credit_ledger is append-only';
END;
$$;

CREATE TRIGGER store_credit_ledger_no_update
  BEFORE UPDATE ON public.store_credit_ledger
  FOR EACH ROW EXECUTE PROCEDURE public.forbid_store_credit_ledger_mutation();

CREATE TRIGGER store_credit_ledger_no_delete
  BEFORE DELETE ON public.store_credit_ledger
  FOR EACH ROW EXECUTE PROCEDURE public.forbid_store_credit_ledger_mutation();

-- ---------------------------------------------------------------------------
-- AuthZ / RPC helpers
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public._payments_rpc_active()
RETURNS boolean
LANGUAGE sql
STABLE
AS $$
  SELECT COALESCE(current_setting('app.payments_rpc', true), '') = '1';
$$;

CREATE OR REPLACE FUNCTION public._payments_rpc_enter()
RETURNS void
LANGUAGE plpgsql
AS $$
BEGIN
  PERFORM set_config('app.payments_rpc', '1', true);
END;
$$;

CREATE OR REPLACE FUNCTION public._require_payments_staff()
RETURNS void
LANGUAGE plpgsql
STABLE
AS $$
BEGIN
  IF NOT (
    auth.role() = 'service_role'
    OR public.has_staff_role(ARRAY['admin', 'finance', 'sales']::public.staff_role[])
  ) THEN
    RAISE EXCEPTION 'admin, finance, or sales role required for payments';
  END IF;
END;
$$;

CREATE OR REPLACE FUNCTION public._ensure_store_credit_account(
  p_customer_id UUID,
  p_currency public.currency_code
)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_id UUID;
  v_cur public.currency_code;
BEGIN
  SELECT id, currency INTO v_id, v_cur
  FROM public.store_credit_accounts
  WHERE customer_id = p_customer_id
  FOR UPDATE;

  IF v_id IS NULL THEN
    INSERT INTO public.store_credit_accounts (customer_id, currency, balance)
    VALUES (p_customer_id, p_currency, 0)
    RETURNING id INTO v_id;
    RETURN v_id;
  END IF;

  IF v_cur <> p_currency THEN
    RAISE EXCEPTION 'store credit account currency is %, cannot use %', v_cur, p_currency;
  END IF;

  RETURN v_id;
END;
$$;

CREATE OR REPLACE FUNCTION public._append_store_credit(
  p_customer_id UUID,
  p_movement public.store_credit_movement,
  p_amount NUMERIC,
  p_currency public.currency_code,
  p_exchange_rate NUMERIC,
  p_payment_entry_id UUID,
  p_journal_entry_id UUID,
  p_reason TEXT,
  p_reverses_ledger_id UUID DEFAULT NULL
)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_acct UUID;
  v_bal NUMERIC;
  v_new NUMERIC;
  v_id UUID;
  v_prior public.store_credit_movement;
BEGIN
  IF p_amount IS NULL OR p_amount <= 0 THEN
    RAISE EXCEPTION 'store credit amount must be > 0';
  END IF;

  v_acct := public._ensure_store_credit_account(p_customer_id, p_currency);

  SELECT balance INTO v_bal
  FROM public.store_credit_accounts
  WHERE id = v_acct
  FOR UPDATE;

  IF p_movement = 'issue' THEN
    v_new := v_bal + p_amount;
  ELSIF p_movement = 'redeem' THEN
    IF v_bal < p_amount THEN
      RAISE EXCEPTION 'store credit overdraw: balance % amount %', v_bal, p_amount;
    END IF;
    v_new := v_bal - p_amount;
  ELSIF p_movement = 'reverse' THEN
    IF p_reverses_ledger_id IS NULL THEN
      RAISE EXCEPTION 'reverse requires reverses_ledger_id';
    END IF;
    SELECT movement INTO v_prior
    FROM public.store_credit_ledger WHERE id = p_reverses_ledger_id;
    IF v_prior = 'issue' THEN
      IF v_bal < p_amount THEN
        RAISE EXCEPTION 'cannot reverse store credit issue: insufficient balance';
      END IF;
      v_new := v_bal - p_amount;
    ELSIF v_prior = 'redeem' THEN
      v_new := v_bal + p_amount;
    ELSE
      RAISE EXCEPTION 'cannot reverse a reverse ledger row';
    END IF;
  ELSE
    RAISE EXCEPTION 'unknown store credit movement %', p_movement;
  END IF;

  UPDATE public.store_credit_accounts
  SET balance = v_new, updated_at = now()
  WHERE id = v_acct;

  INSERT INTO public.store_credit_ledger (
    document_number, account_id, customer_id, movement, amount, currency,
    exchange_rate_applied, balance_after, payment_entry_id, journal_entry_id,
    reason, reverses_ledger_id, created_by
  )
  VALUES (
    public.next_series_value('SC-'),
    v_acct, p_customer_id, p_movement, p_amount, p_currency,
    COALESCE(p_exchange_rate, 1), v_new, p_payment_entry_id, p_journal_entry_id,
    p_reason, p_reverses_ledger_id, auth.uid()
  )
  RETURNING id INTO v_id;

  RETURN v_id;
END;
$$;

-- ---------------------------------------------------------------------------
-- Payment RPCs
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.create_payment_entry(
  p_customer_id UUID,
  p_tender public.payment_tender,
  p_amount NUMERIC,
  p_currency public.currency_code DEFAULT 'USD',
  p_exchange_rate NUMERIC DEFAULT 1,
  p_notes TEXT DEFAULT NULL,
  p_settlement_currency public.currency_code DEFAULT NULL,
  p_settlement_amount NUMERIC DEFAULT NULL,
  p_settlement_exchange_rate NUMERIC DEFAULT NULL
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

  IF p_customer_id IS NULL THEN
    RAISE EXCEPTION 'customer_id required';
  END IF;
  IF p_amount IS NULL OR p_amount <= 0 THEN
    RAISE EXCEPTION 'payment amount must be > 0';
  END IF;
  IF p_currency = 'ZIG' AND (p_exchange_rate IS NULL OR p_exchange_rate <= 0) THEN
    RAISE EXCEPTION 'exchange_rate_applied required for ZIG';
  END IF;

  INSERT INTO public.payment_entries (
    document_number, customer_id, tender, currency, exchange_rate_applied, amount,
    notes, settlement_currency, settlement_amount, settlement_exchange_rate, created_by
  )
  VALUES (
    public.next_series_value('PE-'),
    p_customer_id,
    p_tender,
    p_currency,
    CASE WHEN p_currency = 'USD' THEN COALESCE(p_exchange_rate, 1) ELSE p_exchange_rate END,
    p_amount,
    p_notes,
    p_settlement_currency,
    p_settlement_amount,
    p_settlement_exchange_rate,
    auth.uid()
  )
  RETURNING id INTO v_id;

  RETURN v_id;
END;
$$;

CREATE OR REPLACE FUNCTION public.allocate_payment(
  p_payment_entry_id UUID,
  p_allocations JSONB
)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_pe public.payment_entries%ROWTYPE;
  v_elem JSONB;
  v_inv public.sales_invoices%ROWTYPE;
  v_amt NUMERIC;
  v_sum NUMERIC := 0;
  v_open NUMERIC;
  v_already NUMERIC;
BEGIN
  PERFORM public._require_payments_staff();
  PERFORM public._payments_rpc_enter();

  SELECT * INTO v_pe FROM public.payment_entries WHERE id = p_payment_entry_id FOR UPDATE;
  IF NOT FOUND THEN
    RAISE EXCEPTION 'payment entry not found: %', p_payment_entry_id;
  END IF;
  IF v_pe.status <> 'draft' THEN
    RAISE EXCEPTION 'only draft payments can be allocated';
  END IF;

  IF p_allocations IS NULL OR jsonb_typeof(p_allocations) <> 'array'
     OR jsonb_array_length(p_allocations) = 0 THEN
    RAISE EXCEPTION 'allocations array required';
  END IF;

  DELETE FROM public.payment_allocations WHERE payment_entry_id = p_payment_entry_id;

  FOR v_elem IN SELECT * FROM jsonb_array_elements(p_allocations)
  LOOP
    v_amt := (v_elem ->> 'amount')::numeric;
    IF v_amt IS NULL OR v_amt <= 0 THEN
      RAISE EXCEPTION 'allocation amount must be > 0';
    END IF;

    SELECT * INTO v_inv
    FROM public.sales_invoices
    WHERE id = (v_elem ->> 'sales_invoice_id')::uuid
    FOR UPDATE;

    IF NOT FOUND THEN
      RAISE EXCEPTION 'invoice not found';
    END IF;
    IF v_inv.status <> 'posted' OR v_inv.doc_type <> 'invoice' THEN
      RAISE EXCEPTION 'only posted sales invoices can be allocated';
    END IF;
    IF v_inv.customer_id IS DISTINCT FROM v_pe.customer_id THEN
      RAISE EXCEPTION 'invoice customer mismatch';
    END IF;
    IF v_inv.currency <> v_pe.currency THEN
      RAISE EXCEPTION 'allocation currency must match payment (invoice %, payment %)',
        v_inv.currency, v_pe.currency;
    END IF;

    SELECT COALESCE(SUM(pa.amount), 0) INTO v_already
    FROM public.payment_allocations pa
    JOIN public.payment_entries pe ON pe.id = pa.payment_entry_id
    WHERE pa.sales_invoice_id = v_inv.id
      AND pe.status = 'posted';

    v_open := v_inv.total - v_inv.amount_paid;
    -- amount_paid should equal posted allocations; prefer open from totals
    v_open := v_inv.total - GREATEST(v_inv.amount_paid, v_already);

    IF v_amt > v_open + 0.001 THEN
      RAISE EXCEPTION 'over-allocate denied: invoice % open % allocate %',
        v_inv.document_number, v_open, v_amt;
    END IF;

    INSERT INTO public.payment_allocations (
      payment_entry_id, sales_invoice_id, amount, currency, exchange_rate_applied
    )
    VALUES (
      p_payment_entry_id,
      v_inv.id,
      v_amt,
      v_pe.currency,
      COALESCE((v_elem ->> 'exchange_rate_applied')::numeric, v_pe.exchange_rate_applied)
    );

    v_sum := v_sum + v_amt;
  END LOOP;

  IF v_sum > v_pe.amount + 0.001 THEN
    RAISE EXCEPTION 'allocations % exceed payment amount %', v_sum, v_pe.amount;
  END IF;

  UPDATE public.payment_entries SET updated_at = now() WHERE id = p_payment_entry_id;
  RETURN p_payment_entry_id;
END;
$$;

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
  v_cash_acct TEXT := '1100';
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
    -- Allow pure store-credit issue via issue_store_credit; payment post needs allocations
    RAISE EXCEPTION 'payment requires at least one allocation';
  END IF;
  IF v_sum > v_pe.amount + 0.001 THEN
    RAISE EXCEPTION 'allocations exceed payment amount';
  END IF;

  -- Re-validate opens under lock
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
    v_cash_acct := '2200';
  END IF;

  -- JE: DR tender account, CR AR for allocations; CR 2200 for overpay
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

  -- Apply allocations to invoices + customer AR
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

  -- Link JE on store-credit redeem ledger if needed (already written)
  IF v_pe.tender = 'store_credit' THEN
    -- ledger row exists without JE; append-only so leave as-is (JE linked on header)
    NULL;
  END IF;

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
      'tender', v_pe.tender
    ),
    auth.uid(),
    format('GTR Auto: %s %s %s', v_event, v_pe.amount, v_pe.currency)
  );

  RETURN p_payment_entry_id;
END;
$$;

CREATE OR REPLACE FUNCTION public.cancel_payment_entry(p_payment_entry_id UUID)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_pe public.payment_entries%ROWTYPE;
  v_rev UUID;
  v_alloc RECORD;
  v_sc UUID;
BEGIN
  PERFORM public._require_payments_staff();
  PERFORM public._payments_rpc_enter();

  SELECT * INTO v_pe FROM public.payment_entries WHERE id = p_payment_entry_id FOR UPDATE;
  IF NOT FOUND THEN
    RAISE EXCEPTION 'payment entry not found: %', p_payment_entry_id;
  END IF;
  IF v_pe.status = 'cancelled' THEN
    RETURN p_payment_entry_id;
  END IF;
  IF v_pe.status = 'draft' THEN
    UPDATE public.payment_entries
    SET status = 'cancelled', cancelled_at = now(), updated_at = now()
    WHERE id = p_payment_entry_id;
    RETURN p_payment_entry_id;
  END IF;
  IF v_pe.status <> 'posted' THEN
    RAISE EXCEPTION 'cannot cancel payment in status %', v_pe.status;
  END IF;

  v_rev := public.reverse_journal(
    v_pe.journal_entry_id,
    format('Reversal of payment %s', COALESCE(v_pe.document_number, p_payment_entry_id::text))
  );

  FOR v_alloc IN
    SELECT * FROM public.payment_allocations WHERE payment_entry_id = p_payment_entry_id
  LOOP
    UPDATE public.sales_invoices
    SET amount_paid = GREATEST(0, amount_paid - v_alloc.amount)
    WHERE id = v_alloc.sales_invoice_id;

    UPDATE public.customers
    SET open_balance = open_balance + v_alloc.amount, updated_at = now()
    WHERE id = v_pe.customer_id;
  END LOOP;

  IF v_pe.store_credit_issued > 0 THEN
    SELECT id INTO v_sc
    FROM public.store_credit_ledger
    WHERE payment_entry_id = p_payment_entry_id AND movement = 'issue'
    ORDER BY created_at DESC
    LIMIT 1;

    IF v_sc IS NOT NULL THEN
      PERFORM public._append_store_credit(
        v_pe.customer_id, 'reverse', v_pe.store_credit_issued, v_pe.currency,
        v_pe.exchange_rate_applied, p_payment_entry_id, v_rev,
        'Cancel payment overpay credit', v_sc
      );
    END IF;
  END IF;

  IF v_pe.tender = 'store_credit' THEN
    SELECT id INTO v_sc
    FROM public.store_credit_ledger
    WHERE payment_entry_id = p_payment_entry_id AND movement = 'redeem'
    ORDER BY created_at DESC
    LIMIT 1;

    IF v_sc IS NOT NULL THEN
      PERFORM public._append_store_credit(
        v_pe.customer_id, 'reverse', v_pe.amount, v_pe.currency,
        v_pe.exchange_rate_applied, p_payment_entry_id, v_rev,
        'Cancel payment store credit redeem', v_sc
      );
    END IF;
  END IF;

  UPDATE public.payment_entries
  SET
    status = 'cancelled',
    reversal_journal_entry_id = v_rev,
    cancelled_at = now(),
    updated_at = now()
  WHERE id = p_payment_entry_id;

  RETURN p_payment_entry_id;
END;
$$;

CREATE OR REPLACE FUNCTION public.issue_store_credit(
  p_customer_id UUID,
  p_amount NUMERIC,
  p_currency public.currency_code DEFAULT 'USD',
  p_exchange_rate NUMERIC DEFAULT 1,
  p_reason TEXT DEFAULT NULL,
  p_debit_account TEXT DEFAULT '1100'
)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_journal UUID;
  v_ledger UUID;
  v_debit TEXT;
BEGIN
  PERFORM public._require_payments_staff();
  PERFORM public._payments_rpc_enter();

  IF p_amount IS NULL OR p_amount <= 0 THEN
    RAISE EXCEPTION 'issue amount must be > 0';
  END IF;

  v_debit := COALESCE(NULLIF(p_debit_account, ''), '1100');
  IF v_debit NOT IN ('1100', '1200') THEN
    RAISE EXCEPTION 'issue_store_credit debit must be 1100 or 1200';
  END IF;

  v_journal := public.post_journal_entry(
    CURRENT_DATE,
    COALESCE(p_reason, 'Store credit issued'),
    p_currency,
    CASE WHEN p_currency = 'USD' THEN COALESCE(p_exchange_rate, 1) ELSE p_exchange_rate END,
    jsonb_build_array(
      jsonb_build_object(
        'account_code', v_debit,
        'debit', p_amount, 'credit', 0, 'currency', p_currency
      ),
      jsonb_build_object(
        'account_code', '2200',
        'debit', 0, 'credit', p_amount, 'currency', p_currency
      )
    )
  );

  v_ledger := public._append_store_credit(
    p_customer_id, 'issue', p_amount, p_currency,
    CASE WHEN p_currency = 'USD' THEN COALESCE(p_exchange_rate, 1) ELSE p_exchange_rate END,
    NULL, v_journal, COALESCE(p_reason, 'Store credit issued')
  );

  PERFORM public.emit_domain_event(
    'refund_issued',
    'sc:issue:' || v_ledger::text,
    jsonb_build_object(
      'ledger_id', v_ledger,
      'customer_id', p_customer_id,
      'amount', p_amount,
      'currency', p_currency
    ),
    auth.uid(),
    format('GTR Auto: store credit issued %s %s', p_amount, p_currency)
  );

  RETURN v_ledger;
END;
$$;

CREATE OR REPLACE FUNCTION public.redeem_store_credit(
  p_customer_id UUID,
  p_amount NUMERIC,
  p_currency public.currency_code DEFAULT 'USD',
  p_exchange_rate NUMERIC DEFAULT 1,
  p_sales_invoice_id UUID DEFAULT NULL,
  p_notes TEXT DEFAULT NULL
)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_pe UUID;
  v_alloc JSONB;
BEGIN
  -- Convenience: create + allocate + post a store_credit payment entry
  v_pe := public.create_payment_entry(
    p_customer_id, 'store_credit', p_amount, p_currency, p_exchange_rate, p_notes
  );

  IF p_sales_invoice_id IS NOT NULL THEN
    v_alloc := jsonb_build_array(
      jsonb_build_object('sales_invoice_id', p_sales_invoice_id, 'amount', p_amount)
    );
    PERFORM public.allocate_payment(v_pe, v_alloc);
  ELSE
    RAISE EXCEPTION 'sales_invoice_id required to redeem store credit against AR';
  END IF;

  RETURN public.post_payment_entry(v_pe);
END;
$$;

-- ---------------------------------------------------------------------------
-- RLS
-- ---------------------------------------------------------------------------
ALTER TABLE public.payment_entries ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.payment_allocations ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.store_credit_accounts ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.store_credit_ledger ENABLE ROW LEVEL SECURITY;

CREATE POLICY payment_entries_staff_select ON public.payment_entries
  FOR SELECT TO authenticated
  USING (public.has_staff_role(ARRAY['admin', 'finance', 'sales']::public.staff_role[]));

CREATE POLICY payment_allocations_staff_select ON public.payment_allocations
  FOR SELECT TO authenticated
  USING (public.has_staff_role(ARRAY['admin', 'finance', 'sales']::public.staff_role[]));

CREATE POLICY store_credit_accounts_staff_select ON public.store_credit_accounts
  FOR SELECT TO authenticated
  USING (public.has_staff_role(ARRAY['admin', 'finance', 'sales']::public.staff_role[]));

CREATE POLICY store_credit_accounts_customer_select ON public.store_credit_accounts
  FOR SELECT TO authenticated
  USING (
    customer_id IN (
      SELECT c.id FROM public.customers c WHERE c.profile_id = auth.uid()
    )
  );

CREATE POLICY store_credit_ledger_staff_select ON public.store_credit_ledger
  FOR SELECT TO authenticated
  USING (public.has_staff_role(ARRAY['admin', 'finance', 'sales']::public.staff_role[]));

-- Writes only via SECURITY DEFINER RPCs (no direct INSERT policies for clients)

REVOKE ALL ON FUNCTION public._payments_rpc_active() FROM PUBLIC;
REVOKE ALL ON FUNCTION public._payments_rpc_enter() FROM PUBLIC;
REVOKE ALL ON FUNCTION public._require_payments_staff() FROM PUBLIC;
REVOKE ALL ON FUNCTION public._ensure_store_credit_account(UUID, public.currency_code) FROM PUBLIC;
REVOKE ALL ON FUNCTION public._append_store_credit(
  UUID, public.store_credit_movement, NUMERIC, public.currency_code, NUMERIC,
  UUID, UUID, TEXT, UUID
) FROM PUBLIC;

REVOKE ALL ON FUNCTION public.create_payment_entry(
  UUID, public.payment_tender, NUMERIC, public.currency_code, NUMERIC, TEXT,
  public.currency_code, NUMERIC, NUMERIC
) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.allocate_payment(UUID, JSONB) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.post_payment_entry(UUID) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.cancel_payment_entry(UUID) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.issue_store_credit(
  UUID, NUMERIC, public.currency_code, NUMERIC, TEXT, TEXT
) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.redeem_store_credit(
  UUID, NUMERIC, public.currency_code, NUMERIC, UUID, TEXT
) FROM PUBLIC;

GRANT EXECUTE ON FUNCTION public.create_payment_entry(
  UUID, public.payment_tender, NUMERIC, public.currency_code, NUMERIC, TEXT,
  public.currency_code, NUMERIC, NUMERIC
) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.allocate_payment(UUID, JSONB) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.post_payment_entry(UUID) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.cancel_payment_entry(UUID) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.issue_store_credit(
  UUID, NUMERIC, public.currency_code, NUMERIC, TEXT, TEXT
) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.redeem_store_credit(
  UUID, NUMERIC, public.currency_code, NUMERIC, UUID, TEXT
) TO authenticated, service_role;
