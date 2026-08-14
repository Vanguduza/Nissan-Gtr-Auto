-- B-MONEY-1 / H4 follow-on: loyalty + store credit + credit-limit *_minor dual-write.
-- Mirror cart/JE pattern (20260813300000 / 20260813400000). Nullable columns; no major drop.
-- Append-only ledgers: null→filled minors only (backfill exception). AI never invents money.

ALTER TABLE public.customers
  ADD COLUMN IF NOT EXISTS credit_limit_minor BIGINT,
  ADD COLUMN IF NOT EXISTS open_balance_minor BIGINT;

ALTER TABLE public.store_credit_accounts
  ADD COLUMN IF NOT EXISTS balance_minor BIGINT;

ALTER TABLE public.store_credit_ledger
  ADD COLUMN IF NOT EXISTS amount_minor BIGINT,
  ADD COLUMN IF NOT EXISTS balance_after_minor BIGINT;

ALTER TABLE public.loyalty_ledger
  ADD COLUMN IF NOT EXISTS money_value_minor BIGINT;

COMMENT ON COLUMN public.customers.credit_limit_minor IS
  'Dual-write minor units from credit_limit; prefer until cutover (B-MONEY-1).';
COMMENT ON COLUMN public.customers.open_balance_minor IS
  'Dual-write minor units from open_balance; prefer until cutover (B-MONEY-1).';
COMMENT ON COLUMN public.store_credit_accounts.balance_minor IS
  'Dual-write minor units from balance; prefer until cutover (B-MONEY-1).';
COMMENT ON COLUMN public.store_credit_ledger.amount_minor IS
  'Dual-write minor units from amount; prefer until cutover (B-MONEY-1).';
COMMENT ON COLUMN public.store_credit_ledger.balance_after_minor IS
  'Dual-write minor units from balance_after; prefer until cutover (B-MONEY-1).';
COMMENT ON COLUMN public.loyalty_ledger.money_value_minor IS
  'Dual-write minor units from money_value liability; prefer until cutover (B-MONEY-1).';

-- Ensure public._major_to_minor(NUMERIC) from 20260812030000.
CREATE OR REPLACE FUNCTION public._major_to_minor(p_amount NUMERIC)
RETURNS BIGINT
LANGUAGE sql
IMMUTABLE
AS $$
  SELECT CASE
    WHEN p_amount IS NULL THEN NULL
    ELSE ROUND(p_amount * 100)::BIGINT
  END;
$$;

CREATE OR REPLACE FUNCTION public._customers_credit_dual_write_minor()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
  IF NEW.credit_limit IS NOT NULL THEN
    NEW.credit_limit_minor := public._major_to_minor(NEW.credit_limit);
  END IF;
  IF NEW.open_balance IS NOT NULL THEN
    NEW.open_balance_minor := public._major_to_minor(NEW.open_balance);
  END IF;
  RETURN NEW;
END;
$$;

CREATE OR REPLACE FUNCTION public._store_credit_account_dual_write_minor()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
  IF NEW.balance IS NOT NULL THEN
    NEW.balance_minor := public._major_to_minor(NEW.balance);
  END IF;
  RETURN NEW;
END;
$$;

CREATE OR REPLACE FUNCTION public._store_credit_ledger_dual_write_minor()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
  IF NEW.amount IS NOT NULL THEN
    NEW.amount_minor := public._major_to_minor(NEW.amount);
  END IF;
  IF NEW.balance_after IS NOT NULL THEN
    NEW.balance_after_minor := public._major_to_minor(NEW.balance_after);
  END IF;
  RETURN NEW;
END;
$$;

CREATE OR REPLACE FUNCTION public._loyalty_ledger_dual_write_minor()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
  IF NEW.money_value IS NOT NULL THEN
    NEW.money_value_minor := public._major_to_minor(NEW.money_value);
  END IF;
  RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS customers_credit_dual_write_minor ON public.customers;
CREATE TRIGGER customers_credit_dual_write_minor
  BEFORE INSERT OR UPDATE OF credit_limit, open_balance ON public.customers
  FOR EACH ROW
  EXECUTE FUNCTION public._customers_credit_dual_write_minor();

DROP TRIGGER IF EXISTS store_credit_accounts_dual_write_minor ON public.store_credit_accounts;
CREATE TRIGGER store_credit_accounts_dual_write_minor
  BEFORE INSERT OR UPDATE OF balance ON public.store_credit_accounts
  FOR EACH ROW
  EXECUTE FUNCTION public._store_credit_account_dual_write_minor();

DROP TRIGGER IF EXISTS store_credit_ledger_dual_write_minor ON public.store_credit_ledger;
CREATE TRIGGER store_credit_ledger_dual_write_minor
  BEFORE INSERT OR UPDATE OF amount, balance_after ON public.store_credit_ledger
  FOR EACH ROW
  EXECUTE FUNCTION public._store_credit_ledger_dual_write_minor();

DROP TRIGGER IF EXISTS loyalty_ledger_dual_write_minor ON public.loyalty_ledger;
CREATE TRIGGER loyalty_ledger_dual_write_minor
  BEFORE INSERT OR UPDATE OF money_value ON public.loyalty_ledger
  FOR EACH ROW
  EXECUTE FUNCTION public._loyalty_ledger_dual_write_minor();

-- Append-only exception: null→filled minors only (never rewrite majors / set minors).
CREATE OR REPLACE FUNCTION public.forbid_store_credit_ledger_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
  IF TG_OP = 'DELETE' THEN
    RAISE EXCEPTION 'store_credit_ledger is append-only';
  END IF;

  IF TG_OP = 'UPDATE' THEN
    IF NEW.document_number IS NOT DISTINCT FROM OLD.document_number
      AND NEW.account_id IS NOT DISTINCT FROM OLD.account_id
      AND NEW.customer_id IS NOT DISTINCT FROM OLD.customer_id
      AND NEW.movement IS NOT DISTINCT FROM OLD.movement
      AND NEW.amount IS NOT DISTINCT FROM OLD.amount
      AND NEW.currency IS NOT DISTINCT FROM OLD.currency
      AND NEW.exchange_rate_applied IS NOT DISTINCT FROM OLD.exchange_rate_applied
      AND NEW.balance_after IS NOT DISTINCT FROM OLD.balance_after
      AND NEW.payment_entry_id IS NOT DISTINCT FROM OLD.payment_entry_id
      AND NEW.journal_entry_id IS NOT DISTINCT FROM OLD.journal_entry_id
      AND NEW.reverses_ledger_id IS NOT DISTINCT FROM OLD.reverses_ledger_id
      AND NEW.reason IS NOT DISTINCT FROM OLD.reason
      AND NEW.created_by IS NOT DISTINCT FROM OLD.created_by
      AND NEW.created_at IS NOT DISTINCT FROM OLD.created_at
      AND (OLD.amount_minor IS NULL OR NEW.amount_minor IS NOT DISTINCT FROM OLD.amount_minor)
      AND (
        OLD.balance_after_minor IS NULL
        OR NEW.balance_after_minor IS NOT DISTINCT FROM OLD.balance_after_minor
      )
    THEN
      RETURN NEW;
    END IF;
    RAISE EXCEPTION 'store_credit_ledger is append-only';
  END IF;

  RETURN NEW;
END;
$$;

CREATE OR REPLACE FUNCTION public.forbid_loyalty_ledger_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
  IF TG_OP = 'DELETE' THEN
    RAISE EXCEPTION 'loyalty_ledger is append-only';
  END IF;

  IF TG_OP = 'UPDATE' THEN
    IF NEW.document_number IS NOT DISTINCT FROM OLD.document_number
      AND NEW.account_id IS NOT DISTINCT FROM OLD.account_id
      AND NEW.customer_id IS NOT DISTINCT FROM OLD.customer_id
      AND NEW.movement IS NOT DISTINCT FROM OLD.movement
      AND NEW.points IS NOT DISTINCT FROM OLD.points
      AND NEW.money_value IS NOT DISTINCT FROM OLD.money_value
      AND NEW.currency IS NOT DISTINCT FROM OLD.currency
      AND NEW.exchange_rate_applied IS NOT DISTINCT FROM OLD.exchange_rate_applied
      AND NEW.points_balance_after IS NOT DISTINCT FROM OLD.points_balance_after
      AND NEW.sales_invoice_id IS NOT DISTINCT FROM OLD.sales_invoice_id
      AND NEW.journal_entry_id IS NOT DISTINCT FROM OLD.journal_entry_id
      AND NEW.reverses_ledger_id IS NOT DISTINCT FROM OLD.reverses_ledger_id
      AND NEW.reason IS NOT DISTINCT FROM OLD.reason
      AND NEW.created_by IS NOT DISTINCT FROM OLD.created_by
      AND NEW.created_at IS NOT DISTINCT FROM OLD.created_at
      AND (
        OLD.money_value_minor IS NULL
        OR NEW.money_value_minor IS NOT DISTINCT FROM OLD.money_value_minor
      )
    THEN
      RETURN NEW;
    END IF;
    RAISE EXCEPTION 'loyalty_ledger is append-only';
  END IF;

  RETURN NEW;
END;
$$;

-- Backfill null minors only.
UPDATE public.customers
SET
  credit_limit_minor = COALESCE(credit_limit_minor, public._major_to_minor(credit_limit)),
  open_balance_minor = COALESCE(open_balance_minor, public._major_to_minor(open_balance))
WHERE credit_limit_minor IS NULL OR open_balance_minor IS NULL;

UPDATE public.store_credit_accounts
SET balance_minor = COALESCE(balance_minor, public._major_to_minor(balance))
WHERE balance_minor IS NULL;

UPDATE public.store_credit_ledger
SET
  amount_minor = COALESCE(amount_minor, public._major_to_minor(amount)),
  balance_after_minor = COALESCE(balance_after_minor, public._major_to_minor(balance_after))
WHERE amount_minor IS NULL OR balance_after_minor IS NULL;

UPDATE public.loyalty_ledger
SET money_value_minor = COALESCE(money_value_minor, public._major_to_minor(money_value))
WHERE money_value_minor IS NULL;

-- RPC returns: prefer-ready *_minor alongside majors (no physical drop).
DROP FUNCTION IF EXISTS public.get_loyalty_balance(UUID);
CREATE FUNCTION public.get_loyalty_balance(p_customer_id UUID)
RETURNS TABLE (
  customer_id UUID,
  points_balance NUMERIC,
  currency public.currency_code,
  liability_per_point NUMERIC,
  estimated_liability NUMERIC,
  estimated_liability_minor BIGINT
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
    round(COALESCE(a.points_balance, 0) * s.liability_per_point, 2),
    public._major_to_minor(
      round(COALESCE(a.points_balance, 0) * s.liability_per_point, 2)
    )
  FROM public.loyalty_program_settings s
  LEFT JOIN public.loyalty_accounts a ON a.customer_id = p_customer_id
  WHERE s.id = 1;
END;
$$;

COMMENT ON FUNCTION public.get_loyalty_balance(UUID) IS
  'Loyalty points + estimated liability; dual-read estimated_liability_minor when present (B-MONEY-1).';

REVOKE ALL ON FUNCTION public.get_loyalty_balance(UUID) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.get_loyalty_balance(UUID) TO authenticated, service_role;

DROP FUNCTION IF EXISTS public.set_customer_credit(UUID, NUMERIC, BOOLEAN);
CREATE FUNCTION public.set_customer_credit(
  p_customer_id UUID,
  p_credit_limit NUMERIC DEFAULT NULL,
  p_credit_hold BOOLEAN DEFAULT NULL
)
RETURNS TABLE (
  customer_id UUID,
  credit_limit NUMERIC,
  credit_hold BOOLEAN,
  open_balance NUMERIC,
  currency public.currency_code,
  credit_limit_minor BIGINT,
  open_balance_minor BIGINT
)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_limit NUMERIC;
  v_hold BOOLEAN;
BEGIN
  IF NOT public.has_staff_role(ARRAY['admin', 'sales', 'finance']::public.staff_role[]) THEN
    RAISE EXCEPTION 'admin, sales, or finance role required to set customer credit';
  END IF;
  IF p_customer_id IS NULL THEN
    RAISE EXCEPTION 'customer_id required';
  END IF;
  IF p_credit_limit IS NULL AND p_credit_hold IS NULL THEN
    RAISE EXCEPTION 'provide p_credit_limit and/or p_credit_hold';
  END IF;
  IF p_credit_limit IS NOT NULL AND p_credit_limit < 0 THEN
    RAISE EXCEPTION 'credit_limit must be >= 0';
  END IF;

  SELECT c.credit_limit, c.credit_hold
  INTO v_limit, v_hold
  FROM public.customers c
  WHERE c.id = p_customer_id
  FOR UPDATE;

  IF NOT FOUND THEN
    RAISE EXCEPTION 'customer not found';
  END IF;

  v_limit := COALESCE(p_credit_limit, v_limit);
  v_hold := COALESCE(p_credit_hold, v_hold);

  UPDATE public.customers c
  SET
    credit_limit = v_limit,
    credit_hold = v_hold,
    updated_at = now()
  WHERE c.id = p_customer_id;

  RETURN QUERY
  SELECT
    c.id,
    c.credit_limit,
    c.credit_hold,
    c.open_balance,
    c.currency,
    c.credit_limit_minor,
    c.open_balance_minor
  FROM public.customers c
  WHERE c.id = p_customer_id;
END;
$$;

COMMENT ON FUNCTION public.set_customer_credit(UUID, NUMERIC, BOOLEAN) IS
  'Staff-only credit_limit / credit_hold mutator. Returns majors + *_minor for dual-read (B-MONEY-1).';

REVOKE ALL ON FUNCTION public.set_customer_credit(UUID, NUMERIC, BOOLEAN) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.set_customer_credit(UUID, NUMERIC, BOOLEAN)
  TO authenticated, service_role;
