-- Phase 16 slice 4: Loyalty / points (ledger-backed)
-- Liability rule: outstanding points post to CoA 2210 (Loyalty Points Liability).
-- Earn: Dr 5350 Loyalty Program Expense / Cr 2210.
-- Redeem vs AR: Dr 2210 / Cr 1200 (and apply to invoice amount_paid).
-- Corrections via reverse_loyalty_movement (reversing JE + ledger), never UPDATE/DELETE.
-- No ZIMRA / tax. Multi-currency: money_value carries currency + exchange_rate_applied.

-- ---------------------------------------------------------------------------
-- CoA
-- ---------------------------------------------------------------------------
INSERT INTO public.chart_of_accounts (code, name, account_type) VALUES
  ('2210', 'Loyalty Points Liability', 'liability'),
  ('5350', 'Loyalty Program Expense', 'expense')
ON CONFLICT (code) DO NOTHING;

-- ---------------------------------------------------------------------------
-- Enums / naming
-- ---------------------------------------------------------------------------
CREATE TYPE public.loyalty_movement AS ENUM ('earn', 'redeem', 'expire', 'reverse');

INSERT INTO public.naming_series (prefix, description, pad_length) VALUES
  ('LP-', 'Loyalty points movement', 5)
ON CONFLICT (prefix) DO NOTHING;

-- ---------------------------------------------------------------------------
-- Program settings (singleton row id=1)
-- ---------------------------------------------------------------------------
CREATE TABLE public.loyalty_program_settings (
  id INT PRIMARY KEY DEFAULT 1 CHECK (id = 1),
  is_active BOOLEAN NOT NULL DEFAULT true,
  -- Points awarded per 1 unit of spend in program currency
  points_per_currency_unit NUMERIC(18, 4) NOT NULL DEFAULT 1
    CHECK (points_per_currency_unit > 0),
  -- Balance-sheet liability per outstanding point (in program currency)
  liability_per_point NUMERIC(18, 6) NOT NULL DEFAULT 0.01
    CHECK (liability_per_point > 0),
  currency public.currency_code NOT NULL DEFAULT 'USD',
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_by UUID REFERENCES auth.users (id)
);

INSERT INTO public.loyalty_program_settings (id) VALUES (1)
ON CONFLICT (id) DO NOTHING;

-- ---------------------------------------------------------------------------
-- Per-customer points balance + append-only ledger
-- ---------------------------------------------------------------------------
CREATE TABLE public.loyalty_accounts (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  customer_id UUID NOT NULL UNIQUE REFERENCES public.customers (id) ON DELETE RESTRICT,
  -- Liability denomination for money_value on this account (locked at first earn)
  currency public.currency_code NOT NULL DEFAULT 'USD',
  points_balance NUMERIC(18, 2) NOT NULL DEFAULT 0 CHECK (points_balance >= 0),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE public.loyalty_ledger (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  document_number TEXT,
  account_id UUID NOT NULL REFERENCES public.loyalty_accounts (id) ON DELETE RESTRICT,
  customer_id UUID NOT NULL REFERENCES public.customers (id) ON DELETE RESTRICT,
  movement public.loyalty_movement NOT NULL,
  points NUMERIC(18, 2) NOT NULL CHECK (points > 0),
  money_value NUMERIC(18, 2) NOT NULL CHECK (money_value >= 0),
  currency public.currency_code NOT NULL,
  exchange_rate_applied NUMERIC(18, 8) NOT NULL DEFAULT 1,
  points_balance_after NUMERIC(18, 2) NOT NULL CHECK (points_balance_after >= 0),
  sales_invoice_id UUID REFERENCES public.sales_invoices (id) ON DELETE RESTRICT,
  journal_entry_id UUID REFERENCES public.journal_entries (id),
  reason TEXT,
  reverses_ledger_id UUID REFERENCES public.loyalty_ledger (id),
  created_by UUID REFERENCES auth.users (id),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX loyalty_ledger_account_idx
  ON public.loyalty_ledger (account_id, created_at DESC);
CREATE INDEX loyalty_ledger_customer_idx
  ON public.loyalty_ledger (customer_id, created_at DESC);

COMMENT ON TABLE public.loyalty_accounts IS
  'Per-customer loyalty points. Liability valued on CoA 2210 via money_value on ledger rows.';
COMMENT ON COLUMN public.loyalty_ledger.money_value IS
  'Liability amount posted to 2210 for this movement (points × liability_per_point at post time).';

-- Append-only ledger
CREATE OR REPLACE FUNCTION public.forbid_loyalty_ledger_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
  RAISE EXCEPTION 'loyalty_ledger is append-only';
END;
$$;

CREATE TRIGGER loyalty_ledger_no_update
  BEFORE UPDATE ON public.loyalty_ledger
  FOR EACH ROW EXECUTE PROCEDURE public.forbid_loyalty_ledger_mutation();

CREATE TRIGGER loyalty_ledger_no_delete
  BEFORE DELETE ON public.loyalty_ledger
  FOR EACH ROW EXECUTE PROCEDURE public.forbid_loyalty_ledger_mutation();

-- ---------------------------------------------------------------------------
-- RPC helpers / mutation gate
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public._loyalty_rpc_active()
RETURNS boolean
LANGUAGE sql
STABLE
AS $$
  SELECT COALESCE(current_setting('app.loyalty_rpc', true), '') = '1';
$$;

CREATE OR REPLACE FUNCTION public._loyalty_rpc_enter()
RETURNS void
LANGUAGE plpgsql
AS $$
BEGIN
  PERFORM set_config('app.loyalty_rpc', '1', true);
END;
$$;

CREATE OR REPLACE FUNCTION public._require_loyalty_staff()
RETURNS void
LANGUAGE plpgsql
STABLE
AS $$
BEGIN
  IF NOT (
    auth.role() = 'service_role'
    OR public.has_staff_role(ARRAY['admin', 'finance', 'sales']::public.staff_role[])
  ) THEN
    RAISE EXCEPTION 'admin, finance, or sales role required for loyalty';
  END IF;
END;
$$;

CREATE OR REPLACE FUNCTION public.guard_loyalty_account_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
  IF public._loyalty_rpc_active() THEN
    IF TG_OP = 'DELETE' THEN
      RETURN OLD;
    END IF;
    RETURN NEW;
  END IF;
  RAISE EXCEPTION 'loyalty_accounts: use loyalty RPCs only';
END;
$$;

CREATE TRIGGER loyalty_accounts_mutation_guard
  BEFORE INSERT OR UPDATE OR DELETE ON public.loyalty_accounts
  FOR EACH ROW EXECUTE PROCEDURE public.guard_loyalty_account_mutation();

CREATE OR REPLACE FUNCTION public.guard_loyalty_settings_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
  IF public._loyalty_rpc_active() THEN
    RETURN NEW;
  END IF;
  RAISE EXCEPTION 'loyalty_program_settings: use set_loyalty_program_settings RPC';
END;
$$;

CREATE TRIGGER loyalty_settings_mutation_guard
  BEFORE INSERT OR UPDATE OR DELETE ON public.loyalty_program_settings
  FOR EACH ROW EXECUTE PROCEDURE public.guard_loyalty_settings_mutation();

CREATE OR REPLACE FUNCTION public._ensure_loyalty_account(
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
  FROM public.loyalty_accounts
  WHERE customer_id = p_customer_id
  FOR UPDATE;

  IF v_id IS NULL THEN
    INSERT INTO public.loyalty_accounts (customer_id, currency, points_balance)
    VALUES (p_customer_id, p_currency, 0)
    RETURNING id INTO v_id;
    RETURN v_id;
  END IF;

  IF v_cur <> p_currency THEN
    RAISE EXCEPTION 'loyalty account currency is %, cannot use %', v_cur, p_currency;
  END IF;

  RETURN v_id;
END;
$$;

CREATE OR REPLACE FUNCTION public._loyalty_money_value(p_points NUMERIC)
RETURNS NUMERIC
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_rate NUMERIC;
BEGIN
  SELECT liability_per_point INTO v_rate
  FROM public.loyalty_program_settings
  WHERE id = 1;

  IF v_rate IS NULL OR v_rate <= 0 THEN
    RAISE EXCEPTION 'loyalty program settings missing or invalid';
  END IF;

  RETURN round(p_points * v_rate, 2);
END;
$$;

CREATE OR REPLACE FUNCTION public._append_loyalty(
  p_customer_id UUID,
  p_movement public.loyalty_movement,
  p_points NUMERIC,
  p_currency public.currency_code,
  p_exchange_rate NUMERIC,
  p_money_value NUMERIC,
  p_sales_invoice_id UUID,
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
  v_prior public.loyalty_movement;
BEGIN
  IF p_points IS NULL OR p_points <= 0 THEN
    RAISE EXCEPTION 'loyalty points must be > 0';
  END IF;
  IF p_money_value IS NULL OR p_money_value < 0 THEN
    RAISE EXCEPTION 'loyalty money_value must be >= 0';
  END IF;

  v_acct := public._ensure_loyalty_account(p_customer_id, p_currency);

  SELECT points_balance INTO v_bal
  FROM public.loyalty_accounts
  WHERE id = v_acct
  FOR UPDATE;

  IF p_movement = 'earn' THEN
    v_new := v_bal + p_points;
  ELSIF p_movement IN ('redeem', 'expire') THEN
    IF v_bal < p_points THEN
      RAISE EXCEPTION 'loyalty overdraw: balance % points %', v_bal, p_points;
    END IF;
    v_new := v_bal - p_points;
  ELSIF p_movement = 'reverse' THEN
    IF p_reverses_ledger_id IS NULL THEN
      RAISE EXCEPTION 'reverse requires reverses_ledger_id';
    END IF;
    SELECT movement INTO v_prior
    FROM public.loyalty_ledger WHERE id = p_reverses_ledger_id;
    IF v_prior = 'earn' THEN
      IF v_bal < p_points THEN
        RAISE EXCEPTION 'cannot reverse loyalty earn: insufficient balance';
      END IF;
      v_new := v_bal - p_points;
    ELSIF v_prior IN ('redeem', 'expire') THEN
      v_new := v_bal + p_points;
    ELSE
      RAISE EXCEPTION 'cannot reverse a reverse ledger row';
    END IF;
  ELSE
    RAISE EXCEPTION 'unknown loyalty movement %', p_movement;
  END IF;

  UPDATE public.loyalty_accounts
  SET points_balance = v_new, updated_at = now()
  WHERE id = v_acct;

  INSERT INTO public.loyalty_ledger (
    document_number, account_id, customer_id, movement, points, money_value,
    currency, exchange_rate_applied, points_balance_after, sales_invoice_id,
    journal_entry_id, reason, reverses_ledger_id, created_by
  )
  VALUES (
    public.next_series_value('LP-'),
    v_acct, p_customer_id, p_movement, p_points, p_money_value,
    p_currency, COALESCE(p_exchange_rate, 1), v_new, p_sales_invoice_id,
    p_journal_entry_id, p_reason, p_reverses_ledger_id, auth.uid()
  )
  RETURNING id INTO v_id;

  RETURN v_id;
END;
$$;

-- ---------------------------------------------------------------------------
-- Public RPCs
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.set_loyalty_program_settings(
  p_points_per_currency_unit NUMERIC DEFAULT NULL,
  p_liability_per_point NUMERIC DEFAULT NULL,
  p_currency public.currency_code DEFAULT NULL,
  p_is_active BOOLEAN DEFAULT NULL
)
RETURNS VOID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
  IF NOT (
    auth.role() = 'service_role'
    OR public.has_staff_role(ARRAY['admin', 'finance']::public.staff_role[])
  ) THEN
    RAISE EXCEPTION 'admin or finance role required to update loyalty settings';
  END IF;

  PERFORM public._loyalty_rpc_enter();

  UPDATE public.loyalty_program_settings
  SET
    points_per_currency_unit = COALESCE(p_points_per_currency_unit, points_per_currency_unit),
    liability_per_point = COALESCE(p_liability_per_point, liability_per_point),
    currency = COALESCE(p_currency, currency),
    is_active = COALESCE(p_is_active, is_active),
    updated_at = now(),
    updated_by = auth.uid()
  WHERE id = 1;
END;
$$;

CREATE OR REPLACE FUNCTION public.get_loyalty_balance(p_customer_id UUID)
RETURNS TABLE (
  customer_id UUID,
  points_balance NUMERIC,
  currency public.currency_code,
  liability_per_point NUMERIC,
  estimated_liability NUMERIC
)
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_ok BOOLEAN;
BEGIN
  v_ok := (
    auth.role() = 'service_role'
    OR public.has_staff_role(ARRAY['admin', 'finance', 'sales']::public.staff_role[])
    OR EXISTS (
      SELECT 1 FROM public.customers c
      WHERE c.id = p_customer_id AND c.profile_id = auth.uid()
    )
  );
  IF NOT v_ok THEN
    RAISE EXCEPTION 'not authorized to read loyalty balance';
  END IF;

  RETURN QUERY
  SELECT
    p_customer_id,
    COALESCE(a.points_balance, 0::numeric),
    COALESCE(a.currency, s.currency),
    s.liability_per_point,
    round(COALESCE(a.points_balance, 0) * s.liability_per_point, 2)
  FROM public.loyalty_program_settings s
  LEFT JOIN public.loyalty_accounts a ON a.customer_id = p_customer_id
  WHERE s.id = 1;
END;
$$;

CREATE OR REPLACE FUNCTION public.earn_loyalty_points(
  p_customer_id UUID,
  p_points NUMERIC,
  p_currency public.currency_code DEFAULT 'USD',
  p_exchange_rate NUMERIC DEFAULT 1,
  p_reason TEXT DEFAULT NULL,
  p_sales_invoice_id UUID DEFAULT NULL
)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_settings public.loyalty_program_settings%ROWTYPE;
  v_money NUMERIC;
  v_rate NUMERIC;
  v_journal UUID;
  v_ledger UUID;
  v_inv public.sales_invoices%ROWTYPE;
BEGIN
  PERFORM public._require_loyalty_staff();
  PERFORM public._loyalty_rpc_enter();

  SELECT * INTO v_settings FROM public.loyalty_program_settings WHERE id = 1;
  IF NOT FOUND OR NOT v_settings.is_active THEN
    RAISE EXCEPTION 'loyalty program inactive or missing';
  END IF;

  IF p_customer_id IS NULL THEN
    RAISE EXCEPTION 'customer_id required';
  END IF;
  IF p_points IS NULL OR p_points <= 0 THEN
    RAISE EXCEPTION 'earn points must be > 0';
  END IF;
  IF p_currency = 'ZIG' AND (p_exchange_rate IS NULL OR p_exchange_rate <= 0) THEN
    RAISE EXCEPTION 'exchange_rate_applied required for ZIG';
  END IF;

  IF p_sales_invoice_id IS NOT NULL THEN
    SELECT * INTO v_inv FROM public.sales_invoices WHERE id = p_sales_invoice_id;
    IF NOT FOUND THEN
      RAISE EXCEPTION 'sales invoice not found';
    END IF;
    IF v_inv.customer_id IS DISTINCT FROM p_customer_id THEN
      RAISE EXCEPTION 'invoice customer mismatch';
    END IF;
  END IF;

  v_rate := CASE
    WHEN p_currency = 'USD' THEN COALESCE(p_exchange_rate, 1)
    ELSE p_exchange_rate
  END;
  v_money := public._loyalty_money_value(p_points);

  IF v_money > 0 THEN
    v_journal := public.post_journal_entry(
      CURRENT_DATE,
      COALESCE(p_reason, format('Loyalty earn %s pts', p_points)),
      p_currency,
      v_rate,
      jsonb_build_array(
        jsonb_build_object(
          'account_code', '5350',
          'debit', v_money, 'credit', 0, 'currency', p_currency
        ),
        jsonb_build_object(
          'account_code', '2210',
          'debit', 0, 'credit', v_money, 'currency', p_currency
        )
      )
    );
  END IF;

  v_ledger := public._append_loyalty(
    p_customer_id, 'earn', p_points, p_currency, v_rate, v_money,
    p_sales_invoice_id, v_journal,
    COALESCE(p_reason, format('Loyalty earn %s pts', p_points))
  );

  RETURN v_ledger;
END;
$$;

CREATE OR REPLACE FUNCTION public.earn_loyalty_from_spend(
  p_customer_id UUID,
  p_spend_amount NUMERIC,
  p_currency public.currency_code DEFAULT 'USD',
  p_exchange_rate NUMERIC DEFAULT 1,
  p_reason TEXT DEFAULT NULL,
  p_sales_invoice_id UUID DEFAULT NULL
)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_settings public.loyalty_program_settings%ROWTYPE;
  v_points NUMERIC;
BEGIN
  PERFORM public._require_loyalty_staff();

  SELECT * INTO v_settings FROM public.loyalty_program_settings WHERE id = 1;
  IF NOT FOUND OR NOT v_settings.is_active THEN
    RAISE EXCEPTION 'loyalty program inactive or missing';
  END IF;
  IF p_spend_amount IS NULL OR p_spend_amount <= 0 THEN
    RAISE EXCEPTION 'spend amount must be > 0';
  END IF;

  v_points := round(p_spend_amount * v_settings.points_per_currency_unit, 2);
  IF v_points <= 0 THEN
    RAISE EXCEPTION 'computed points must be > 0';
  END IF;

  RETURN public.earn_loyalty_points(
    p_customer_id, v_points, p_currency, p_exchange_rate,
    COALESCE(p_reason, format('Loyalty earn from spend %s %s', p_spend_amount, p_currency)),
    p_sales_invoice_id
  );
END;
$$;

CREATE OR REPLACE FUNCTION public.redeem_loyalty_points(
  p_customer_id UUID,
  p_points NUMERIC,
  p_currency public.currency_code DEFAULT 'USD',
  p_exchange_rate NUMERIC DEFAULT 1,
  p_sales_invoice_id UUID DEFAULT NULL,
  p_reason TEXT DEFAULT NULL
)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_settings public.loyalty_program_settings%ROWTYPE;
  v_money NUMERIC;
  v_rate NUMERIC;
  v_journal UUID;
  v_ledger UUID;
  v_inv public.sales_invoices%ROWTYPE;
  v_open NUMERIC;
BEGIN
  PERFORM public._require_loyalty_staff();
  PERFORM public._loyalty_rpc_enter();

  SELECT * INTO v_settings FROM public.loyalty_program_settings WHERE id = 1;
  IF NOT FOUND OR NOT v_settings.is_active THEN
    RAISE EXCEPTION 'loyalty program inactive or missing';
  END IF;

  IF p_customer_id IS NULL THEN
    RAISE EXCEPTION 'customer_id required';
  END IF;
  IF p_points IS NULL OR p_points <= 0 THEN
    RAISE EXCEPTION 'redeem points must be > 0';
  END IF;
  IF p_sales_invoice_id IS NULL THEN
    RAISE EXCEPTION 'sales_invoice_id required to redeem loyalty against AR';
  END IF;
  IF p_currency = 'ZIG' AND (p_exchange_rate IS NULL OR p_exchange_rate <= 0) THEN
    RAISE EXCEPTION 'exchange_rate_applied required for ZIG';
  END IF;

  SELECT * INTO v_inv
  FROM public.sales_invoices
  WHERE id = p_sales_invoice_id
  FOR UPDATE;

  IF NOT FOUND THEN
    RAISE EXCEPTION 'sales invoice not found';
  END IF;
  IF v_inv.status <> 'posted' OR v_inv.doc_type <> 'invoice' THEN
    RAISE EXCEPTION 'only posted sales invoices can receive loyalty redemption';
  END IF;
  IF v_inv.customer_id IS DISTINCT FROM p_customer_id THEN
    RAISE EXCEPTION 'invoice customer mismatch';
  END IF;
  IF v_inv.currency <> p_currency THEN
    RAISE EXCEPTION 'loyalty redeem currency must match invoice (%)', v_inv.currency;
  END IF;

  v_rate := CASE
    WHEN p_currency = 'USD' THEN COALESCE(p_exchange_rate, 1)
    ELSE p_exchange_rate
  END;
  v_money := public._loyalty_money_value(p_points);

  v_open := v_inv.total - v_inv.amount_paid;
  IF v_money > v_open + 0.001 THEN
    RAISE EXCEPTION 'loyalty money_value % exceeds invoice open %', v_money, v_open;
  END IF;

  -- Liability draw-down against AR
  v_journal := public.post_journal_entry(
    CURRENT_DATE,
    COALESCE(p_reason, format('Loyalty redeem %s pts', p_points)),
    p_currency,
    v_rate,
    jsonb_build_array(
      jsonb_build_object(
        'account_code', '2210',
        'debit', v_money, 'credit', 0, 'currency', p_currency
      ),
      jsonb_build_object(
        'account_code', '1200',
        'debit', 0, 'credit', v_money, 'currency', p_currency
      )
    )
  );

  v_ledger := public._append_loyalty(
    p_customer_id, 'redeem', p_points, p_currency, v_rate, v_money,
    p_sales_invoice_id, v_journal,
    COALESCE(p_reason, format('Loyalty redeem %s pts', p_points))
  );

  UPDATE public.sales_invoices
  SET amount_paid = amount_paid + v_money
  WHERE id = v_inv.id;

  UPDATE public.customers
  SET
    open_balance = GREATEST(0, open_balance - v_money),
    updated_at = now()
  WHERE id = p_customer_id;

  RETURN v_ledger;
END;
$$;

CREATE OR REPLACE FUNCTION public.expire_loyalty_points(
  p_customer_id UUID,
  p_points NUMERIC,
  p_currency public.currency_code DEFAULT 'USD',
  p_exchange_rate NUMERIC DEFAULT 1,
  p_reason TEXT DEFAULT NULL
)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_money NUMERIC;
  v_rate NUMERIC;
  v_journal UUID;
  v_ledger UUID;
BEGIN
  PERFORM public._require_loyalty_staff();
  PERFORM public._loyalty_rpc_enter();

  IF p_points IS NULL OR p_points <= 0 THEN
    RAISE EXCEPTION 'expire points must be > 0';
  END IF;
  IF p_currency = 'ZIG' AND (p_exchange_rate IS NULL OR p_exchange_rate <= 0) THEN
    RAISE EXCEPTION 'exchange_rate_applied required for ZIG';
  END IF;

  v_rate := CASE
    WHEN p_currency = 'USD' THEN COALESCE(p_exchange_rate, 1)
    ELSE p_exchange_rate
  END;
  v_money := public._loyalty_money_value(p_points);

  -- Release liability back to income (contra expense / break-age)
  -- Use 5350 credit (reduce prior expense) when points expire unused.
  IF v_money > 0 THEN
    v_journal := public.post_journal_entry(
      CURRENT_DATE,
      COALESCE(p_reason, format('Loyalty expire %s pts', p_points)),
      p_currency,
      v_rate,
      jsonb_build_array(
        jsonb_build_object(
          'account_code', '2210',
          'debit', v_money, 'credit', 0, 'currency', p_currency
        ),
        jsonb_build_object(
          'account_code', '5350',
          'debit', 0, 'credit', v_money, 'currency', p_currency
        )
      )
    );
  END IF;

  v_ledger := public._append_loyalty(
    p_customer_id, 'expire', p_points, p_currency, v_rate, v_money,
    NULL, v_journal,
    COALESCE(p_reason, format('Loyalty expire %s pts', p_points))
  );

  RETURN v_ledger;
END;
$$;

CREATE OR REPLACE FUNCTION public.reverse_loyalty_movement(p_ledger_id UUID)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_row public.loyalty_ledger%ROWTYPE;
  v_rev_je UUID;
  v_ledger UUID;
  v_prior_rev UUID;
BEGIN
  PERFORM public._require_loyalty_staff();
  PERFORM public._loyalty_rpc_enter();

  SELECT * INTO v_row FROM public.loyalty_ledger WHERE id = p_ledger_id;
  IF NOT FOUND THEN
    RAISE EXCEPTION 'loyalty ledger not found: %', p_ledger_id;
  END IF;
  IF v_row.movement = 'reverse' THEN
    RAISE EXCEPTION 'cannot reverse a reverse row';
  END IF;

  SELECT id INTO v_prior_rev
  FROM public.loyalty_ledger
  WHERE reverses_ledger_id = p_ledger_id
  LIMIT 1;
  IF v_prior_rev IS NOT NULL THEN
    RAISE EXCEPTION 'loyalty movement already reversed';
  END IF;

  IF v_row.journal_entry_id IS NOT NULL THEN
    v_rev_je := public.reverse_journal(
      v_row.journal_entry_id,
      format('Reversal of loyalty %s', COALESCE(v_row.document_number, p_ledger_id::text))
    );
  END IF;

  -- Undo AR application on redeem
  IF v_row.movement = 'redeem' AND v_row.sales_invoice_id IS NOT NULL THEN
    UPDATE public.sales_invoices
    SET amount_paid = GREATEST(0, amount_paid - v_row.money_value)
    WHERE id = v_row.sales_invoice_id;

    UPDATE public.customers
    SET
      open_balance = open_balance + v_row.money_value,
      updated_at = now()
    WHERE id = v_row.customer_id;
  END IF;

  v_ledger := public._append_loyalty(
    v_row.customer_id, 'reverse', v_row.points, v_row.currency,
    v_row.exchange_rate_applied, v_row.money_value,
    v_row.sales_invoice_id, v_rev_je,
    format('Reverse %s', COALESCE(v_row.document_number, p_ledger_id::text)),
    p_ledger_id
  );

  RETURN v_ledger;
END;
$$;

-- ---------------------------------------------------------------------------
-- RLS
-- ---------------------------------------------------------------------------
ALTER TABLE public.loyalty_program_settings ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.loyalty_accounts ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.loyalty_ledger ENABLE ROW LEVEL SECURITY;

CREATE POLICY loyalty_settings_staff_select ON public.loyalty_program_settings
  FOR SELECT TO authenticated
  USING (true);

CREATE POLICY loyalty_accounts_staff_select ON public.loyalty_accounts
  FOR SELECT TO authenticated
  USING (public.has_staff_role(ARRAY['admin', 'finance', 'sales']::public.staff_role[]));

CREATE POLICY loyalty_accounts_customer_select ON public.loyalty_accounts
  FOR SELECT TO authenticated
  USING (
    customer_id IN (
      SELECT c.id FROM public.customers c WHERE c.profile_id = auth.uid()
    )
  );

CREATE POLICY loyalty_ledger_staff_select ON public.loyalty_ledger
  FOR SELECT TO authenticated
  USING (public.has_staff_role(ARRAY['admin', 'finance', 'sales']::public.staff_role[]));

CREATE POLICY loyalty_ledger_customer_select ON public.loyalty_ledger
  FOR SELECT TO authenticated
  USING (
    customer_id IN (
      SELECT c.id FROM public.customers c WHERE c.profile_id = auth.uid()
    )
  );

-- Writes only via SECURITY DEFINER RPCs
GRANT SELECT ON TABLE public.loyalty_program_settings TO authenticated;
GRANT SELECT ON TABLE public.loyalty_accounts TO authenticated;
GRANT SELECT ON TABLE public.loyalty_ledger TO authenticated;

REVOKE ALL ON FUNCTION public._loyalty_rpc_active() FROM PUBLIC;
REVOKE ALL ON FUNCTION public._loyalty_rpc_enter() FROM PUBLIC;
REVOKE ALL ON FUNCTION public._require_loyalty_staff() FROM PUBLIC;
REVOKE ALL ON FUNCTION public._ensure_loyalty_account(UUID, public.currency_code) FROM PUBLIC;
REVOKE ALL ON FUNCTION public._loyalty_money_value(NUMERIC) FROM PUBLIC;
REVOKE ALL ON FUNCTION public._append_loyalty(
  UUID, public.loyalty_movement, NUMERIC, public.currency_code, NUMERIC, NUMERIC,
  UUID, UUID, TEXT, UUID
) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.guard_loyalty_account_mutation() FROM PUBLIC;
REVOKE ALL ON FUNCTION public.guard_loyalty_settings_mutation() FROM PUBLIC;
REVOKE ALL ON FUNCTION public.forbid_loyalty_ledger_mutation() FROM PUBLIC;

REVOKE ALL ON FUNCTION public.set_loyalty_program_settings(
  NUMERIC, NUMERIC, public.currency_code, BOOLEAN
) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.get_loyalty_balance(UUID) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.earn_loyalty_points(
  UUID, NUMERIC, public.currency_code, NUMERIC, TEXT, UUID
) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.earn_loyalty_from_spend(
  UUID, NUMERIC, public.currency_code, NUMERIC, TEXT, UUID
) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.redeem_loyalty_points(
  UUID, NUMERIC, public.currency_code, NUMERIC, UUID, TEXT
) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.expire_loyalty_points(
  UUID, NUMERIC, public.currency_code, NUMERIC, TEXT
) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.reverse_loyalty_movement(UUID) FROM PUBLIC;

GRANT EXECUTE ON FUNCTION public.set_loyalty_program_settings(
  NUMERIC, NUMERIC, public.currency_code, BOOLEAN
) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.get_loyalty_balance(UUID) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.earn_loyalty_points(
  UUID, NUMERIC, public.currency_code, NUMERIC, TEXT, UUID
) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.earn_loyalty_from_spend(
  UUID, NUMERIC, public.currency_code, NUMERIC, TEXT, UUID
) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.redeem_loyalty_points(
  UUID, NUMERIC, public.currency_code, NUMERIC, UUID, TEXT
) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.expire_loyalty_points(
  UUID, NUMERIC, public.currency_code, NUMERIC, TEXT
) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.reverse_loyalty_movement(UUID) TO authenticated, service_role;
