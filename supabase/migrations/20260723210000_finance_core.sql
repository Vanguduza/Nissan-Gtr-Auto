-- Phase 3: journal draft/post/reverse, periods, naming series, statements, bank recon
-- Exclusions: no tax / ZIMRA / payroll-tax fields

CREATE TYPE public.journal_status AS ENUM ('draft', 'posted');
CREATE TYPE public.bank_recon_status AS ENUM ('open', 'matched', 'cleared');

-- ---------------------------------------------------------------------------
-- Journal: Draft → Submit
-- ---------------------------------------------------------------------------
ALTER TABLE public.journal_entries
  ADD COLUMN IF NOT EXISTS status public.journal_status NOT NULL DEFAULT 'posted',
  ADD COLUMN IF NOT EXISTS document_number TEXT;

CREATE INDEX IF NOT EXISTS journal_entries_status_idx
  ON public.journal_entries (status);

CREATE INDEX IF NOT EXISTS journal_entries_entry_date_idx
  ON public.journal_entries (entry_date);

-- Replace blanket forbid with posted-only immutability
DROP TRIGGER IF EXISTS journal_entries_no_update ON public.journal_entries;
DROP TRIGGER IF EXISTS journal_entry_lines_no_update ON public.journal_entry_lines;

CREATE OR REPLACE FUNCTION public.forbid_posted_journal_update()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
  IF OLD.status = 'posted' THEN
    RAISE EXCEPTION 'Posted journal entries are immutable. Use reverse_journal.';
  END IF;
  IF NEW.status = 'posted' AND OLD.status = 'draft' THEN
    -- Allow post_journal (SECURITY DEFINER) to flip status
    RETURN NEW;
  END IF;
  IF NEW.status IS DISTINCT FROM OLD.status AND NEW.status = 'draft' THEN
    RAISE EXCEPTION 'Cannot un-post a journal entry';
  END IF;
  RETURN NEW;
END;
$$;

CREATE TRIGGER journal_entries_protect_posted
  BEFORE UPDATE ON public.journal_entries
  FOR EACH ROW
  EXECUTE PROCEDURE public.forbid_posted_journal_update();

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
  IF v_status = 'posted' THEN
    RAISE EXCEPTION 'Posted journal lines are immutable. Use reverse_journal.';
  END IF;
  IF TG_OP = 'DELETE' THEN
    RETURN OLD;
  END IF;
  RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS journal_entry_lines_no_delete ON public.journal_entry_lines;
CREATE TRIGGER journal_entry_lines_protect_posted
  BEFORE UPDATE OR DELETE ON public.journal_entry_lines
  FOR EACH ROW
  EXECUTE PROCEDURE public.forbid_posted_journal_line_mutation();

-- Keep DELETE blocked on journal_entries headers always
CREATE OR REPLACE FUNCTION public.forbid_journal_entry_delete()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
  RAISE EXCEPTION 'Journal entries cannot be deleted. Use reverse_journal.';
END;
$$;

DROP TRIGGER IF EXISTS journal_entries_no_delete ON public.journal_entries;
CREATE TRIGGER journal_entries_no_delete
  BEFORE DELETE ON public.journal_entries
  FOR EACH ROW
  EXECUTE PROCEDURE public.forbid_journal_entry_delete();

-- ---------------------------------------------------------------------------
-- Accounting periods + lock
-- ---------------------------------------------------------------------------
CREATE TABLE public.accounting_periods (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  period_start DATE NOT NULL,
  period_end DATE NOT NULL,
  label TEXT NOT NULL,
  locked_at TIMESTAMPTZ,
  locked_by UUID REFERENCES auth.users (id),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT accounting_periods_range CHECK (period_end >= period_start),
  CONSTRAINT accounting_periods_unique_range UNIQUE (period_start, period_end)
);

CREATE OR REPLACE FUNCTION public.is_period_locked(p_date DATE)
RETURNS BOOLEAN
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
  SELECT EXISTS (
    SELECT 1
    FROM public.accounting_periods ap
    WHERE p_date BETWEEN ap.period_start AND ap.period_end
      AND ap.locked_at IS NOT NULL
  );
$$;

CREATE OR REPLACE FUNCTION public.lock_accounting_period(p_period_id UUID)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
  IF NOT (
    auth.role() = 'service_role'
    OR public.has_staff_role(ARRAY['admin', 'finance']::public.staff_role[])
  ) THEN
    RAISE EXCEPTION 'finance or admin role required to lock period';
  END IF;

  UPDATE public.accounting_periods
  SET locked_at = now(), locked_by = auth.uid()
  WHERE id = p_period_id
    AND locked_at IS NULL;

  IF NOT FOUND THEN
    RAISE EXCEPTION 'period not found or already locked: %', p_period_id;
  END IF;
END;
$$;

-- ---------------------------------------------------------------------------
-- Naming series
-- ---------------------------------------------------------------------------
CREATE TABLE public.naming_series (
  prefix TEXT PRIMARY KEY,
  current_value BIGINT NOT NULL DEFAULT 0,
  pad_length INT NOT NULL DEFAULT 5 CHECK (pad_length BETWEEN 1 AND 12),
  description TEXT,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE OR REPLACE FUNCTION public.next_series_value(p_prefix TEXT)
RETURNS TEXT
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_next BIGINT;
  v_pad INT;
BEGIN
  IF p_prefix IS NULL OR length(trim(p_prefix)) = 0 THEN
    RAISE EXCEPTION 'naming series prefix required';
  END IF;

  INSERT INTO public.naming_series (prefix, current_value)
  VALUES (p_prefix, 0)
  ON CONFLICT (prefix) DO NOTHING;

  SELECT current_value + 1, pad_length
  INTO v_next, v_pad
  FROM public.naming_series
  WHERE prefix = p_prefix
  FOR UPDATE;

  UPDATE public.naming_series
  SET current_value = v_next, updated_at = now()
  WHERE prefix = p_prefix;

  RETURN p_prefix || lpad(v_next::text, v_pad, '0');
END;
$$;

INSERT INTO public.naming_series (prefix, description, pad_length) VALUES
  ('JV-', 'Journal voucher', 5),
  ('OB-', 'Opening balance', 5),
  ('SINV-', 'Sales invoice', 5),
  ('PO-', 'Purchase order', 5),
  ('DN-', 'Delivery note', 5),
  ('PAY-', 'Payment entry', 5),
  ('BR-', 'Bank reconciliation', 5)
ON CONFLICT (prefix) DO NOTHING;

-- ---------------------------------------------------------------------------
-- Post / reverse journal RPCs
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public._assert_journal_balanced(p_entry_id UUID)
RETURNS void
LANGUAGE plpgsql
STABLE
SET search_path = public
AS $$
DECLARE
  v_debit NUMERIC(18, 2);
  v_credit NUMERIC(18, 2);
BEGIN
  SELECT COALESCE(SUM(debit), 0), COALESCE(SUM(credit), 0)
  INTO v_debit, v_credit
  FROM public.journal_entry_lines
  WHERE journal_entry_id = p_entry_id;

  IF abs(v_debit - v_credit) > 0.009 THEN
    RAISE EXCEPTION 'Unbalanced journal entry: debit=% credit=%', v_debit, v_credit;
  END IF;
  IF v_debit = 0 AND v_credit = 0 THEN
    RAISE EXCEPTION 'Journal entry has no lines';
  END IF;
END;
$$;

CREATE OR REPLACE FUNCTION public.create_journal_draft(
  p_entry_date DATE,
  p_description TEXT,
  p_currency public.currency_code,
  p_exchange_rate NUMERIC,
  p_lines JSONB
)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_id UUID;
  v_line JSONB;
BEGIN
  IF NOT (
    auth.role() = 'service_role'
    OR public.has_staff_role(ARRAY['admin', 'finance']::public.staff_role[])
  ) THEN
    RAISE EXCEPTION 'finance or admin role required';
  END IF;

  IF public.is_period_locked(p_entry_date) THEN
    RAISE EXCEPTION 'accounting period is locked for date %', p_entry_date;
  END IF;

  INSERT INTO public.journal_entries (
    entry_date, description, currency, exchange_rate_applied, status, posted_by, posted_at
  )
  VALUES (
    COALESCE(p_entry_date, CURRENT_DATE),
    p_description,
    p_currency,
    CASE WHEN p_currency = 'USD' THEN COALESCE(p_exchange_rate, 1) ELSE p_exchange_rate END,
    'draft',
    auth.uid(),
    now()
  )
  RETURNING id INTO v_id;

  IF p_lines IS NULL OR jsonb_typeof(p_lines) <> 'array' OR jsonb_array_length(p_lines) = 0 THEN
    RAISE EXCEPTION 'journal lines required';
  END IF;

  FOR v_line IN SELECT * FROM jsonb_array_elements(p_lines)
  LOOP
    INSERT INTO public.journal_entry_lines (
      journal_entry_id, account_code, debit, credit, currency
    )
    VALUES (
      v_id,
      v_line ->> 'account_code',
      COALESCE((v_line ->> 'debit')::numeric, 0),
      COALESCE((v_line ->> 'credit')::numeric, 0),
      COALESCE((v_line ->> 'currency')::public.currency_code, p_currency)
    );
  END LOOP;

  PERFORM public._assert_journal_balanced(v_id);
  RETURN v_id;
END;
$$;

CREATE OR REPLACE FUNCTION public.post_journal(p_entry_id UUID)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_date DATE;
  v_status public.journal_status;
  v_doc TEXT;
BEGIN
  IF NOT (
    auth.role() = 'service_role'
    OR public.has_staff_role(ARRAY['admin', 'finance']::public.staff_role[])
  ) THEN
    RAISE EXCEPTION 'finance or admin role required';
  END IF;

  SELECT entry_date, status INTO v_date, v_status
  FROM public.journal_entries
  WHERE id = p_entry_id
  FOR UPDATE;

  IF NOT FOUND THEN
    RAISE EXCEPTION 'journal entry not found: %', p_entry_id;
  END IF;
  IF v_status = 'posted' THEN
    RETURN p_entry_id;
  END IF;
  IF public.is_period_locked(v_date) THEN
    RAISE EXCEPTION 'accounting period is locked for date %', v_date;
  END IF;

  PERFORM public._assert_journal_balanced(p_entry_id);
  v_doc := public.next_series_value('JV-');

  UPDATE public.journal_entries
  SET
    status = 'posted',
    posted_at = now(),
    posted_by = COALESCE(auth.uid(), posted_by),
    document_number = COALESCE(document_number, v_doc)
  WHERE id = p_entry_id;

  RETURN p_entry_id;
END;
$$;

CREATE OR REPLACE FUNCTION public.post_journal_entry(
  p_entry_date DATE,
  p_description TEXT,
  p_currency public.currency_code,
  p_exchange_rate NUMERIC,
  p_lines JSONB
)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_id UUID;
BEGIN
  v_id := public.create_journal_draft(
    p_entry_date, p_description, p_currency, p_exchange_rate, p_lines
  );
  RETURN public.post_journal(v_id);
END;
$$;

CREATE OR REPLACE FUNCTION public.reverse_journal(
  p_entry_id UUID,
  p_description TEXT DEFAULT NULL
)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_src public.journal_entries%ROWTYPE;
  v_new_id UUID;
  v_line RECORD;
  v_desc TEXT;
BEGIN
  IF NOT (
    auth.role() = 'service_role'
    OR public.has_staff_role(ARRAY['admin', 'finance']::public.staff_role[])
  ) THEN
    RAISE EXCEPTION 'finance or admin role required';
  END IF;

  SELECT * INTO v_src FROM public.journal_entries WHERE id = p_entry_id;
  IF NOT FOUND THEN
    RAISE EXCEPTION 'journal entry not found: %', p_entry_id;
  END IF;
  IF v_src.status <> 'posted' THEN
    RAISE EXCEPTION 'only posted journals can be reversed';
  END IF;
  IF public.is_period_locked(CURRENT_DATE) THEN
    RAISE EXCEPTION 'accounting period is locked for today';
  END IF;

  v_desc := COALESCE(p_description, format('Reversal of %s', COALESCE(v_src.document_number, v_src.id::text)));

  INSERT INTO public.journal_entries (
    entry_date, description, currency, exchange_rate_applied,
    status, is_reversal, reverses_entry_id, posted_by, posted_at, document_number
  )
  VALUES (
    CURRENT_DATE,
    v_desc,
    v_src.currency,
    v_src.exchange_rate_applied,
    'draft',
    true,
    v_src.id,
    auth.uid(),
    now(),
    public.next_series_value('JV-')
  )
  RETURNING id INTO v_new_id;

  FOR v_line IN
    SELECT account_code, debit, credit, currency
    FROM public.journal_entry_lines
    WHERE journal_entry_id = p_entry_id
  LOOP
    INSERT INTO public.journal_entry_lines (
      journal_entry_id, account_code, debit, credit, currency
    )
    VALUES (
      v_new_id, v_line.account_code, v_line.credit, v_line.debit, v_line.currency
    );
  END LOOP;

  RETURN public.post_journal(v_new_id);
END;
$$;

-- Opening balances: balanced lines into BS accounts + equity plug if needed via caller lines
CREATE OR REPLACE FUNCTION public.post_opening_balances(
  p_as_of DATE,
  p_currency public.currency_code,
  p_exchange_rate NUMERIC,
  p_lines JSONB
)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
  RETURN public.post_journal_entry(
    p_as_of,
    format('Opening balances as of %s', p_as_of),
    p_currency,
    p_exchange_rate,
    p_lines
  );
END;
$$;

-- ---------------------------------------------------------------------------
-- Statement helpers (amounts in entry currency + usd_equiv)
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public._line_usd_equiv(
  p_amount NUMERIC,
  p_currency public.currency_code,
  p_rate NUMERIC
)
RETURNS NUMERIC
LANGUAGE sql
IMMUTABLE
AS $$
  SELECT CASE
    WHEN p_currency = 'USD' THEN p_amount
    WHEN p_rate IS NULL OR p_rate = 0 THEN NULL
    ELSE round(p_amount / p_rate, 2)
  END;
$$;

CREATE OR REPLACE FUNCTION public.report_trial_balance(
  p_as_of DATE DEFAULT CURRENT_DATE,
  p_currency public.currency_code DEFAULT NULL
)
RETURNS TABLE (
  account_code VARCHAR(10),
  account_name TEXT,
  account_type public.account_type,
  debit NUMERIC,
  credit NUMERIC,
  debit_usd NUMERIC,
  credit_usd NUMERIC
)
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
  SELECT
    c.code,
    c.name,
    c.account_type,
    COALESCE(SUM(l.debit), 0),
    COALESCE(SUM(l.credit), 0),
    COALESCE(SUM(public._line_usd_equiv(l.debit, e.currency, e.exchange_rate_applied)), 0),
    COALESCE(SUM(public._line_usd_equiv(l.credit, e.currency, e.exchange_rate_applied)), 0)
  FROM public.chart_of_accounts c
  LEFT JOIN public.journal_entry_lines l ON l.account_code = c.code
  LEFT JOIN public.journal_entries e
    ON e.id = l.journal_entry_id
   AND e.status = 'posted'
   AND e.entry_date <= p_as_of
   AND (p_currency IS NULL OR e.currency = p_currency)
  GROUP BY c.code, c.name, c.account_type
  ORDER BY c.code;
$$;

CREATE OR REPLACE FUNCTION public.report_profit_and_loss(
  p_from DATE,
  p_to DATE,
  p_currency public.currency_code DEFAULT NULL
)
RETURNS TABLE (
  account_code VARCHAR(10),
  account_name TEXT,
  account_type public.account_type,
  amount NUMERIC,
  amount_usd NUMERIC
)
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
  SELECT
    c.code,
    c.name,
    c.account_type,
    COALESCE(SUM(l.credit - l.debit), 0) AS amount,
    COALESCE(SUM(
      public._line_usd_equiv(l.credit, e.currency, e.exchange_rate_applied)
      - public._line_usd_equiv(l.debit, e.currency, e.exchange_rate_applied)
    ), 0) AS amount_usd
  FROM public.chart_of_accounts c
  JOIN public.journal_entry_lines l ON l.account_code = c.code
  JOIN public.journal_entries e ON e.id = l.journal_entry_id
  WHERE e.status = 'posted'
    AND e.entry_date BETWEEN p_from AND p_to
    AND c.account_type IN ('income', 'expense')
    AND (p_currency IS NULL OR e.currency = p_currency)
  GROUP BY c.code, c.name, c.account_type
  ORDER BY c.code;
$$;

CREATE OR REPLACE FUNCTION public.report_balance_sheet(
  p_as_of DATE DEFAULT CURRENT_DATE,
  p_currency public.currency_code DEFAULT NULL
)
RETURNS TABLE (
  account_code VARCHAR(10),
  account_name TEXT,
  account_type public.account_type,
  balance NUMERIC,
  balance_usd NUMERIC
)
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
  SELECT
    c.code,
    c.name,
    c.account_type,
    CASE
      WHEN c.account_type IN ('asset', 'expense') THEN COALESCE(SUM(l.debit - l.credit), 0)
      ELSE COALESCE(SUM(l.credit - l.debit), 0)
    END AS balance,
    CASE
      WHEN c.account_type IN ('asset', 'expense') THEN COALESCE(SUM(
        public._line_usd_equiv(l.debit, e.currency, e.exchange_rate_applied)
        - public._line_usd_equiv(l.credit, e.currency, e.exchange_rate_applied)
      ), 0)
      ELSE COALESCE(SUM(
        public._line_usd_equiv(l.credit, e.currency, e.exchange_rate_applied)
        - public._line_usd_equiv(l.debit, e.currency, e.exchange_rate_applied)
      ), 0)
    END AS balance_usd
  FROM public.chart_of_accounts c
  LEFT JOIN public.journal_entry_lines l ON l.account_code = c.code
  LEFT JOIN public.journal_entries e
    ON e.id = l.journal_entry_id
   AND e.status = 'posted'
   AND e.entry_date <= p_as_of
   AND (p_currency IS NULL OR e.currency = p_currency)
  WHERE c.account_type IN ('asset', 'liability', 'equity')
  GROUP BY c.code, c.name, c.account_type
  ORDER BY c.code;
$$;

-- Cash flow (heuristic): movements on cash account 1100 + P&L net as operating proxy
CREATE OR REPLACE FUNCTION public.report_cash_flow(
  p_from DATE,
  p_to DATE,
  p_currency public.currency_code DEFAULT NULL
)
RETURNS TABLE (
  section TEXT,
  label TEXT,
  amount_usd NUMERIC
)
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
  WITH cash_moves AS (
    SELECT
      COALESCE(SUM(
        public._line_usd_equiv(l.debit - l.credit, e.currency, e.exchange_rate_applied)
      ), 0) AS net_cash_usd
    FROM public.journal_entry_lines l
    JOIN public.journal_entries e ON e.id = l.journal_entry_id
    WHERE e.status = 'posted'
      AND e.entry_date BETWEEN p_from AND p_to
      AND l.account_code = '1100'
      AND (p_currency IS NULL OR e.currency = p_currency)
  ),
  pl AS (
    SELECT COALESCE(SUM(amount_usd), 0) AS net_income_usd
    FROM public.report_profit_and_loss(p_from, p_to, p_currency)
  )
  SELECT 'operating'::text, 'Net income (P&L proxy)'::text, (SELECT net_income_usd FROM pl)
  UNION ALL
  SELECT 'operating', 'Net change in cash (1100)', (SELECT net_cash_usd FROM cash_moves)
  UNION ALL
  SELECT 'investing', 'Not classified (Phase 3 heuristic)', 0
  UNION ALL
  SELECT 'financing', 'Not classified (Phase 3 heuristic)', 0;
$$;

-- ---------------------------------------------------------------------------
-- Bank reconciliation
-- ---------------------------------------------------------------------------
CREATE TABLE public.bank_statements (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  account_code VARCHAR(10) NOT NULL REFERENCES public.chart_of_accounts (code),
  currency public.currency_code NOT NULL,
  statement_date DATE NOT NULL,
  opening_balance NUMERIC(18, 2) NOT NULL DEFAULT 0,
  closing_balance NUMERIC(18, 2) NOT NULL DEFAULT 0,
  document_number TEXT,
  created_by UUID REFERENCES auth.users (id),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE public.bank_statement_lines (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  statement_id UUID NOT NULL REFERENCES public.bank_statements (id) ON DELETE CASCADE,
  line_date DATE NOT NULL,
  description TEXT,
  amount NUMERIC(18, 2) NOT NULL,
  status public.bank_recon_status NOT NULL DEFAULT 'open',
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE public.bank_recon_matches (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  statement_line_id UUID NOT NULL REFERENCES public.bank_statement_lines (id) ON DELETE CASCADE,
  journal_entry_line_id UUID NOT NULL REFERENCES public.journal_entry_lines (id) ON DELETE RESTRICT,
  matched_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  matched_by UUID REFERENCES auth.users (id),
  UNIQUE (statement_line_id, journal_entry_line_id)
);

CREATE OR REPLACE FUNCTION public.clear_bank_matches(p_match_ids UUID[])
RETURNS INT
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_count INT := 0;
  v_id UUID;
  v_line UUID;
BEGIN
  IF NOT (
    auth.role() = 'service_role'
    OR public.has_staff_role(ARRAY['admin', 'finance']::public.staff_role[])
  ) THEN
    RAISE EXCEPTION 'finance or admin role required';
  END IF;

  FOREACH v_id IN ARRAY p_match_ids
  LOOP
    SELECT statement_line_id INTO v_line
    FROM public.bank_recon_matches
    WHERE id = v_id;

    IF v_line IS NULL THEN
      CONTINUE;
    END IF;

    UPDATE public.bank_statement_lines
    SET status = 'cleared'
    WHERE id = v_line;

    v_count := v_count + 1;
  END LOOP;

  RETURN v_count;
END;
$$;

-- ---------------------------------------------------------------------------
-- RLS
-- ---------------------------------------------------------------------------
ALTER TABLE public.accounting_periods ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.naming_series ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.bank_statements ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.bank_statement_lines ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.bank_recon_matches ENABLE ROW LEVEL SECURITY;

CREATE POLICY periods_select_finance
  ON public.accounting_periods FOR SELECT TO authenticated
  USING (public.has_staff_role(ARRAY['admin', 'finance']::public.staff_role[]));

CREATE POLICY periods_write_finance
  ON public.accounting_periods FOR ALL TO authenticated
  USING (public.has_staff_role(ARRAY['admin', 'finance']::public.staff_role[]))
  WITH CHECK (public.has_staff_role(ARRAY['admin', 'finance']::public.staff_role[]));

CREATE POLICY naming_select_staff
  ON public.naming_series FOR SELECT TO authenticated
  USING (public.is_staff());

CREATE POLICY naming_admin_write
  ON public.naming_series FOR ALL TO authenticated
  USING (public.has_staff_role(ARRAY['admin']::public.staff_role[]))
  WITH CHECK (public.has_staff_role(ARRAY['admin']::public.staff_role[]));

CREATE POLICY bank_stmt_finance
  ON public.bank_statements FOR ALL TO authenticated
  USING (public.has_staff_role(ARRAY['admin', 'finance']::public.staff_role[]))
  WITH CHECK (public.has_staff_role(ARRAY['admin', 'finance']::public.staff_role[]));

CREATE POLICY bank_stmt_lines_finance
  ON public.bank_statement_lines FOR ALL TO authenticated
  USING (public.has_staff_role(ARRAY['admin', 'finance']::public.staff_role[]))
  WITH CHECK (public.has_staff_role(ARRAY['admin', 'finance']::public.staff_role[]));

CREATE POLICY bank_matches_finance
  ON public.bank_recon_matches FOR ALL TO authenticated
  USING (public.has_staff_role(ARRAY['admin', 'finance']::public.staff_role[]))
  WITH CHECK (public.has_staff_role(ARRAY['admin', 'finance']::public.staff_role[]));

-- Grants
REVOKE ALL ON FUNCTION public.next_series_value(TEXT) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.create_journal_draft(DATE, TEXT, public.currency_code, NUMERIC, JSONB) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.post_journal(UUID) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.post_journal_entry(DATE, TEXT, public.currency_code, NUMERIC, JSONB) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.reverse_journal(UUID, TEXT) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.post_opening_balances(DATE, public.currency_code, NUMERIC, JSONB) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.lock_accounting_period(UUID) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.clear_bank_matches(UUID[]) FROM PUBLIC;

GRANT EXECUTE ON FUNCTION public.next_series_value(TEXT) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.create_journal_draft(DATE, TEXT, public.currency_code, NUMERIC, JSONB) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.post_journal(UUID) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.post_journal_entry(DATE, TEXT, public.currency_code, NUMERIC, JSONB) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.reverse_journal(UUID, TEXT) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.post_opening_balances(DATE, public.currency_code, NUMERIC, JSONB) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.lock_accounting_period(UUID) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.clear_bank_matches(UUID[]) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.is_period_locked(DATE) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.report_trial_balance(DATE, public.currency_code) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.report_profit_and_loss(DATE, DATE, public.currency_code) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.report_balance_sheet(DATE, public.currency_code) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.report_cash_flow(DATE, DATE, public.currency_code) TO authenticated, service_role;
