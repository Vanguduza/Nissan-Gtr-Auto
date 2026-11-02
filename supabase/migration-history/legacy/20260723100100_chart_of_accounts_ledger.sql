-- Chart of Accounts + append-only journal ledger

CREATE TABLE public.chart_of_accounts (
  code VARCHAR(10) PRIMARY KEY,
  name TEXT NOT NULL,
  account_type public.account_type NOT NULL,
  is_active BOOLEAN NOT NULL DEFAULT true,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE public.journal_entries (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  entry_date DATE NOT NULL DEFAULT CURRENT_DATE,
  description TEXT,
  currency public.currency_code NOT NULL,
  exchange_rate_applied NUMERIC(18, 8),
  posted_by UUID REFERENCES auth.users (id),
  posted_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  is_reversal BOOLEAN NOT NULL DEFAULT false,
  reverses_entry_id UUID REFERENCES public.journal_entries (id),
  CONSTRAINT journal_reversal_requires_target CHECK (
    (is_reversal = false AND reverses_entry_id IS NULL)
    OR (is_reversal = true AND reverses_entry_id IS NOT NULL)
  )
);

CREATE TABLE public.journal_entry_lines (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  journal_entry_id UUID NOT NULL REFERENCES public.journal_entries (id) ON DELETE RESTRICT,
  account_code VARCHAR(10) NOT NULL REFERENCES public.chart_of_accounts (code),
  debit NUMERIC(18, 2) NOT NULL DEFAULT 0 CHECK (debit >= 0),
  credit NUMERIC(18, 2) NOT NULL DEFAULT 0 CHECK (credit >= 0),
  currency public.currency_code NOT NULL,
  CONSTRAINT journal_line_one_side CHECK (
    (debit > 0 AND credit = 0) OR (credit > 0 AND debit = 0) OR (debit = 0 AND credit = 0)
  )
);

CREATE INDEX journal_entries_posted_at_idx ON public.journal_entries (posted_at DESC);
CREATE INDEX journal_entry_lines_entry_idx ON public.journal_entry_lines (journal_entry_id);
CREATE INDEX journal_entry_lines_account_idx ON public.journal_entry_lines (account_code);

-- Immutability: block UPDATE/DELETE on ledger tables
CREATE OR REPLACE FUNCTION public.forbid_ledger_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
  RAISE EXCEPTION 'Ledger tables are append-only. Use reversing entries instead of UPDATE/DELETE.';
END;
$$;

CREATE TRIGGER journal_entries_no_update
  BEFORE UPDATE ON public.journal_entries
  FOR EACH ROW EXECUTE PROCEDURE public.forbid_ledger_mutation();

CREATE TRIGGER journal_entries_no_delete
  BEFORE DELETE ON public.journal_entries
  FOR EACH ROW EXECUTE PROCEDURE public.forbid_ledger_mutation();

CREATE TRIGGER journal_entry_lines_no_update
  BEFORE UPDATE ON public.journal_entry_lines
  FOR EACH ROW EXECUTE PROCEDURE public.forbid_ledger_mutation();

CREATE TRIGGER journal_entry_lines_no_delete
  BEFORE DELETE ON public.journal_entry_lines
  FOR EACH ROW EXECUTE PROCEDURE public.forbid_ledger_mutation();

-- Balance check on insert of lines is application-level; optional DB constraint via deferred trigger later.

ALTER TABLE public.chart_of_accounts ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.journal_entries ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.journal_entry_lines ENABLE ROW LEVEL SECURITY;

CREATE POLICY coa_select_authenticated
  ON public.chart_of_accounts FOR SELECT TO authenticated
  USING (true);

CREATE POLICY coa_write_finance
  ON public.chart_of_accounts FOR ALL TO authenticated
  USING (public.has_staff_role(ARRAY['admin', 'finance']::public.staff_role[]))
  WITH CHECK (public.has_staff_role(ARRAY['admin', 'finance']::public.staff_role[]));

CREATE POLICY journal_select_finance
  ON public.journal_entries FOR SELECT TO authenticated
  USING (public.has_staff_role(ARRAY['admin', 'finance']::public.staff_role[]));

CREATE POLICY journal_insert_finance
  ON public.journal_entries FOR INSERT TO authenticated
  WITH CHECK (public.has_staff_role(ARRAY['admin', 'finance']::public.staff_role[]));

CREATE POLICY journal_lines_select_finance
  ON public.journal_entry_lines FOR SELECT TO authenticated
  USING (public.has_staff_role(ARRAY['admin', 'finance']::public.staff_role[]));

CREATE POLICY journal_lines_insert_finance
  ON public.journal_entry_lines FOR INSERT TO authenticated
  WITH CHECK (public.has_staff_role(ARRAY['admin', 'finance']::public.staff_role[]));

