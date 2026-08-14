-- H4 / B-MONEY-1 slice 5: dual-write amount_minor on journal_entry_lines + payment_entries.
-- Mirror cart/invoice pattern (20260813300000). Nullable columns; no major drop; no RLS change.
-- Append-only: posted JE lines / posted payments stay immutable for money majors;
-- only null → filled minors from existing majors are allowed (backfill exception).

ALTER TABLE public.journal_entry_lines
  ADD COLUMN IF NOT EXISTS debit_minor BIGINT,
  ADD COLUMN IF NOT EXISTS credit_minor BIGINT;

ALTER TABLE public.payment_entries
  ADD COLUMN IF NOT EXISTS amount_minor BIGINT,
  ADD COLUMN IF NOT EXISTS settlement_amount_minor BIGINT;

COMMENT ON COLUMN public.journal_entry_lines.debit_minor IS
  'Dual-write minor units from debit; prefer with debit until cutover (H4).';
COMMENT ON COLUMN public.journal_entry_lines.credit_minor IS
  'Dual-write minor units from credit; prefer with credit until cutover (H4).';
COMMENT ON COLUMN public.payment_entries.amount_minor IS
  'Dual-write minor units from amount; prefer with amount until cutover (H4).';
COMMENT ON COLUMN public.payment_entries.settlement_amount_minor IS
  'Dual-write minor units from settlement_amount; prefer until cutover (H4).';

-- Reuse / ensure public._major_to_minor(NUMERIC) from 20260812030000.
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

CREATE OR REPLACE FUNCTION public._journal_line_dual_write_minor()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
  IF NEW.debit IS NOT NULL THEN
    NEW.debit_minor := public._major_to_minor(NEW.debit);
  END IF;
  IF NEW.credit IS NOT NULL THEN
    NEW.credit_minor := public._major_to_minor(NEW.credit);
  END IF;
  RETURN NEW;
END;
$$;

CREATE OR REPLACE FUNCTION public._payment_entry_dual_write_minor()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
  IF NEW.amount IS NOT NULL THEN
    NEW.amount_minor := public._major_to_minor(NEW.amount);
  END IF;
  IF NEW.settlement_amount IS NOT NULL THEN
    NEW.settlement_amount_minor := public._major_to_minor(NEW.settlement_amount);
  ELSIF TG_OP = 'UPDATE' AND NEW.settlement_amount IS NULL THEN
    NEW.settlement_amount_minor := NULL;
  END IF;
  RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS journal_entry_lines_dual_write_minor ON public.journal_entry_lines;
CREATE TRIGGER journal_entry_lines_dual_write_minor
  BEFORE INSERT OR UPDATE OF debit, credit ON public.journal_entry_lines
  FOR EACH ROW
  EXECUTE FUNCTION public._journal_line_dual_write_minor();

DROP TRIGGER IF EXISTS payment_entries_dual_write_minor ON public.payment_entries;
CREATE TRIGGER payment_entries_dual_write_minor
  BEFORE INSERT OR UPDATE OF amount, settlement_amount ON public.payment_entries
  FOR EACH ROW
  EXECUTE FUNCTION public._payment_entry_dual_write_minor();

-- Allow filling null minors on posted JE lines without touching majors (ledger append-only exception).
CREATE OR REPLACE FUNCTION public.forbid_posted_journal_line_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
  v_status public.journal_status;
  v_entry_id UUID;
BEGIN
  v_entry_id := COALESCE(NEW.journal_entry_id, OLD.journal_entry_id);
  SELECT status INTO v_status FROM public.journal_entries WHERE id = v_entry_id;

  IF TG_OP = 'DELETE' THEN
    IF v_status = 'posted' THEN
      RAISE EXCEPTION 'Posted journal lines are immutable. Use reverse_journal.';
    END IF;
    RETURN OLD;
  END IF;

  IF v_status = 'posted' THEN
    -- Null→filled minors only; never rewrite majors or already-set minors.
    IF NEW.journal_entry_id IS NOT DISTINCT FROM OLD.journal_entry_id
      AND NEW.account_code IS NOT DISTINCT FROM OLD.account_code
      AND NEW.debit IS NOT DISTINCT FROM OLD.debit
      AND NEW.credit IS NOT DISTINCT FROM OLD.credit
      AND NEW.currency IS NOT DISTINCT FROM OLD.currency
      AND (OLD.debit_minor IS NULL OR NEW.debit_minor IS NOT DISTINCT FROM OLD.debit_minor)
      AND (OLD.credit_minor IS NULL OR NEW.credit_minor IS NOT DISTINCT FROM OLD.credit_minor)
    THEN
      RETURN NEW;
    END IF;
    RAISE EXCEPTION 'Posted journal lines are immutable. Use reverse_journal.';
  END IF;

  RETURN NEW;
END;
$$;

-- Allow null→filled amount_minor / settlement_amount_minor when majors/status frozen.
CREATE OR REPLACE FUNCTION public.guard_payment_entry_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
  IF public._payments_rpc_active() THEN
    IF TG_OP = 'DELETE' THEN
      RETURN OLD;
    END IF;
    RETURN NEW;
  END IF;

  IF TG_OP = 'INSERT' THEN
    RAISE EXCEPTION 'payment_entries: use create_payment_entry RPC';
  ELSIF TG_OP = 'DELETE' THEN
    RAISE EXCEPTION 'payment_entries: direct delete not allowed';
  ELSIF TG_OP = 'UPDATE' THEN
    -- H4 backfill exception: fill null minors from majors only; never rewrite money majors.
    IF NEW.amount IS NOT DISTINCT FROM OLD.amount
      AND NEW.settlement_amount IS NOT DISTINCT FROM OLD.settlement_amount
      AND NEW.currency IS NOT DISTINCT FROM OLD.currency
      AND NEW.settlement_currency IS NOT DISTINCT FROM OLD.settlement_currency
      AND NEW.exchange_rate_applied IS NOT DISTINCT FROM OLD.exchange_rate_applied
      AND NEW.settlement_exchange_rate IS NOT DISTINCT FROM OLD.settlement_exchange_rate
      AND NEW.status IS NOT DISTINCT FROM OLD.status
      AND NEW.customer_id IS NOT DISTINCT FROM OLD.customer_id
      AND NEW.tender IS NOT DISTINCT FROM OLD.tender
      AND (OLD.amount_minor IS NULL OR NEW.amount_minor IS NOT DISTINCT FROM OLD.amount_minor)
      AND (
        OLD.settlement_amount_minor IS NULL
        OR NEW.settlement_amount_minor IS NOT DISTINCT FROM OLD.settlement_amount_minor
      )
    THEN
      RETURN NEW;
    END IF;

    IF OLD.status IN ('posted', 'cancelled') THEN
      RAISE EXCEPTION 'payment_entries: posted/cancelled payments are immutable (use cancel_payment_entry)';
    END IF;
    RAISE EXCEPTION 'payment_entries: use payment RPCs for updates';
  END IF;

  RETURN NULL;
END;
$$;

-- Backfill null minors only (does not rewrite existing minors or majors).
UPDATE public.journal_entry_lines
SET
  debit_minor = COALESCE(debit_minor, public._major_to_minor(debit)),
  credit_minor = COALESCE(credit_minor, public._major_to_minor(credit))
WHERE debit_minor IS NULL OR credit_minor IS NULL;

UPDATE public.payment_entries
SET
  amount_minor = COALESCE(amount_minor, public._major_to_minor(amount)),
  settlement_amount_minor = CASE
    WHEN settlement_amount IS NULL THEN settlement_amount_minor
    ELSE COALESCE(settlement_amount_minor, public._major_to_minor(settlement_amount))
  END
WHERE amount_minor IS NULL
   OR (settlement_amount IS NOT NULL AND settlement_amount_minor IS NULL);
