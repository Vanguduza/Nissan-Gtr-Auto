-- Finance: per-account period open/close, GL register, petty funding, requisitions
-- Phases A+B+C (2026-07-25-finance-requisitions-period-balances)
-- Exclusions: no ZIMRA / fiscal; no payroll tax. Journals remain append-only.

-- ---------------------------------------------------------------------------
-- Phase A — account period balances + register
-- ---------------------------------------------------------------------------
CREATE TYPE public.account_period_status AS ENUM ('open', 'closed');

CREATE TABLE public.account_period_balances (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  account_code VARCHAR(10) NOT NULL REFERENCES public.chart_of_accounts (code),
  currency public.currency_code NOT NULL,
  period_start DATE NOT NULL,
  period_end DATE NOT NULL,
  opening_balance NUMERIC(18, 2) NOT NULL DEFAULT 0,
  closing_balance NUMERIC(18, 2),
  status public.account_period_status NOT NULL DEFAULT 'open',
  opened_by UUID REFERENCES auth.users (id),
  closed_by UUID REFERENCES auth.users (id),
  opened_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  closed_at TIMESTAMPTZ,
  notes TEXT,
  physical_count NUMERIC(18, 2),
  variance NUMERIC(18, 2),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT account_period_balances_range CHECK (period_end >= period_start),
  CONSTRAINT account_period_balances_closed_shape CHECK (
    (
      status = 'open'
      AND closing_balance IS NULL
      AND closed_at IS NULL
      AND closed_by IS NULL
    )
    OR (
      status = 'closed'
      AND closing_balance IS NOT NULL
      AND closed_at IS NOT NULL
    )
  )
);

CREATE UNIQUE INDEX account_period_balances_one_open_idx
  ON public.account_period_balances (account_code, currency)
  WHERE status = 'open';

CREATE INDEX account_period_balances_account_dates_idx
  ON public.account_period_balances (account_code, currency, period_start, period_end);

CREATE OR REPLACE FUNCTION public._finance_period_rpc_active()
RETURNS boolean
LANGUAGE sql
STABLE
AS $$
  SELECT COALESCE(current_setting('app.finance_period_rpc', true), '') = '1';
$$;

CREATE OR REPLACE FUNCTION public._finance_period_rpc_enter()
RETURNS void
LANGUAGE plpgsql
AS $$
BEGIN
  PERFORM set_config('app.finance_period_rpc', '1', true);
END;
$$;

CREATE OR REPLACE FUNCTION public.forbid_account_period_direct_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
  IF public._finance_period_rpc_active() THEN
    IF TG_OP = 'DELETE' THEN
      RETURN OLD;
    END IF;
    RETURN NEW;
  END IF;
  RAISE EXCEPTION 'account_period_balances: use open_account_period / close_account_period RPCs';
END;
$$;

CREATE TRIGGER account_period_balances_mutation_guard
  BEFORE INSERT OR UPDATE OR DELETE ON public.account_period_balances
  FOR EACH ROW
  EXECUTE PROCEDURE public.forbid_account_period_direct_mutation();

CREATE OR REPLACE FUNCTION public.open_account_period(
  p_account_code VARCHAR(10),
  p_currency public.currency_code,
  p_period_start DATE,
  p_period_end DATE,
  p_opening_balance NUMERIC DEFAULT 0,
  p_notes TEXT DEFAULT NULL
)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_id UUID;
BEGIN
  IF NOT (
    auth.role() = 'service_role'
    OR public.has_staff_role(ARRAY['admin', 'finance']::public.staff_role[])
  ) THEN
    RAISE EXCEPTION 'finance or admin role required';
  END IF;

  IF p_account_code IS NULL OR NOT EXISTS (
    SELECT 1 FROM public.chart_of_accounts c WHERE c.code = p_account_code AND c.is_active
  ) THEN
    RAISE EXCEPTION 'active account required: %', p_account_code;
  END IF;

  IF p_period_start IS NULL OR p_period_end IS NULL OR p_period_end < p_period_start THEN
    RAISE EXCEPTION 'invalid period range';
  END IF;

  IF EXISTS (
    SELECT 1
    FROM public.account_period_balances ap
    WHERE ap.account_code = p_account_code
      AND ap.currency = p_currency
      AND ap.status = 'open'
  ) THEN
    RAISE EXCEPTION 'open period already exists for % %', p_account_code, p_currency;
  END IF;

  PERFORM public._finance_period_rpc_enter();

  INSERT INTO public.account_period_balances (
    account_code, currency, period_start, period_end,
    opening_balance, status, opened_by, notes
  )
  VALUES (
    p_account_code,
    p_currency,
    p_period_start,
    p_period_end,
    COALESCE(p_opening_balance, 0),
    'open',
    auth.uid(),
    nullif(trim(p_notes), '')
  )
  RETURNING id INTO v_id;

  RETURN v_id;
END;
$$;

CREATE OR REPLACE FUNCTION public.close_account_period(
  p_period_id UUID,
  p_physical_count NUMERIC DEFAULT NULL,
  p_notes TEXT DEFAULT NULL
)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_row public.account_period_balances%ROWTYPE;
  v_activity NUMERIC(18, 2);
  v_closing NUMERIC(18, 2);
  v_variance NUMERIC(18, 2);
BEGIN
  IF NOT (
    auth.role() = 'service_role'
    OR public.has_staff_role(ARRAY['admin', 'finance']::public.staff_role[])
  ) THEN
    RAISE EXCEPTION 'finance or admin role required';
  END IF;

  SELECT * INTO v_row
  FROM public.account_period_balances
  WHERE id = p_period_id
  FOR UPDATE;

  IF NOT FOUND THEN
    RAISE EXCEPTION 'account period not found: %', p_period_id;
  END IF;
  IF v_row.status = 'closed' THEN
    RAISE EXCEPTION 'account period already closed: %', p_period_id;
  END IF;

  SELECT COALESCE(SUM(l.debit - l.credit), 0)
  INTO v_activity
  FROM public.journal_entry_lines l
  JOIN public.journal_entries e ON e.id = l.journal_entry_id
  WHERE e.status = 'posted'
    AND l.account_code = v_row.account_code
    AND e.currency = v_row.currency
    AND e.entry_date BETWEEN v_row.period_start AND v_row.period_end;

  v_closing := v_row.opening_balance + v_activity;

  IF p_physical_count IS NOT NULL THEN
    v_variance := p_physical_count - v_closing;
  ELSE
    v_variance := NULL;
  END IF;

  PERFORM public._finance_period_rpc_enter();

  UPDATE public.account_period_balances
  SET
    status = 'closed',
    closing_balance = v_closing,
    closed_at = now(),
    closed_by = auth.uid(),
    physical_count = p_physical_count,
    variance = v_variance,
    notes = CASE
      WHEN nullif(trim(p_notes), '') IS NULL THEN notes
      WHEN notes IS NULL OR notes = '' THEN nullif(trim(p_notes), '')
      ELSE notes || E'\n' || nullif(trim(p_notes), '')
    END
  WHERE id = p_period_id;

  RETURN p_period_id;
END;
$$;

CREATE OR REPLACE FUNCTION public.report_account_register(
  p_account_code VARCHAR(10),
  p_from DATE,
  p_to DATE,
  p_currency public.currency_code DEFAULT NULL
)
RETURNS TABLE (
  entry_date DATE,
  document_number TEXT,
  description TEXT,
  debit NUMERIC,
  credit NUMERIC,
  running_balance NUMERIC,
  currency public.currency_code,
  journal_entry_id UUID
)
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_opening NUMERIC(18, 2) := 0;
BEGIN
  IF NOT (
    auth.role() = 'service_role'
    OR public.has_staff_role(ARRAY['admin', 'finance']::public.staff_role[])
  ) THEN
    RAISE EXCEPTION 'finance or admin role required';
  END IF;

  IF p_account_code IS NULL OR p_from IS NULL OR p_to IS NULL OR p_to < p_from THEN
    RAISE EXCEPTION 'account, from, and to required (to >= from)';
  END IF;

  SELECT COALESCE(SUM(l.debit - l.credit), 0)
  INTO v_opening
  FROM public.journal_entry_lines l
  JOIN public.journal_entries e ON e.id = l.journal_entry_id
  WHERE e.status = 'posted'
    AND l.account_code = p_account_code
    AND e.entry_date < p_from
    AND (p_currency IS NULL OR e.currency = p_currency);

  RETURN QUERY
  WITH moves AS (
    SELECT
      e.entry_date AS entry_date,
      e.document_number AS document_number,
      e.description AS description,
      COALESCE(SUM(l.debit), 0) AS debit,
      COALESCE(SUM(l.credit), 0) AS credit,
      e.currency AS currency,
      e.id AS journal_entry_id
    FROM public.journal_entry_lines l
    JOIN public.journal_entries e ON e.id = l.journal_entry_id
    WHERE e.status = 'posted'
      AND l.account_code = p_account_code
      AND e.entry_date BETWEEN p_from AND p_to
      AND (p_currency IS NULL OR e.currency = p_currency)
    GROUP BY e.id, e.entry_date, e.document_number, e.description, e.currency
  )
  SELECT
    m.entry_date,
    m.document_number,
    m.description,
    m.debit,
    m.credit,
    v_opening + SUM(m.debit - m.credit) OVER (
      ORDER BY m.entry_date, m.document_number NULLS LAST, m.journal_entry_id
      ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW
    ) AS running_balance,
    m.currency,
    m.journal_entry_id
  FROM moves m
  ORDER BY m.entry_date, m.document_number NULLS LAST, m.journal_entry_id;
END;
$$;

ALTER TABLE public.account_period_balances ENABLE ROW LEVEL SECURITY;

CREATE POLICY account_period_balances_finance
  ON public.account_period_balances FOR ALL TO authenticated
  USING (public.has_staff_role(ARRAY['admin', 'finance']::public.staff_role[]))
  WITH CHECK (public.has_staff_role(ARRAY['admin', 'finance']::public.staff_role[]));

REVOKE ALL ON TABLE public.account_period_balances FROM PUBLIC, anon;
GRANT SELECT ON TABLE public.account_period_balances TO authenticated, service_role;
GRANT ALL ON TABLE public.account_period_balances TO service_role;

-- ---------------------------------------------------------------------------
-- Phase B — petty cash funding (1100 → 1110) + replenish amount helper
-- ---------------------------------------------------------------------------
INSERT INTO public.app_settings (key, value_json, description) VALUES
  (
    'finance.petty_cash_funding_account_code',
    '"1100"'::jsonb,
    'Canonical funding GL for petty cash imprest (1110 Petty Cash)'
  )
ON CONFLICT (key) DO NOTHING;

CREATE OR REPLACE FUNCTION public.petty_cash_funding_account_code()
RETURNS VARCHAR(10)
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
  SELECT COALESCE(
    (
      SELECT CASE
        WHEN jsonb_typeof(s.value_json) = 'string' THEN nullif(s.value_json #>> '{}', '')
        WHEN jsonb_typeof(s.value_json) = 'object' THEN nullif(s.value_json ->> 'code', '')
        ELSE NULL
      END
      FROM public.app_settings s
      WHERE s.key = 'finance.petty_cash_funding_account_code'
    ),
    '1100'
  )::varchar(10);
$$;

CREATE OR REPLACE FUNCTION public.compute_petty_cash_replenish_amount(
  p_currency public.currency_code DEFAULT 'USD',
  p_as_of DATE DEFAULT CURRENT_DATE
)
RETURNS NUMERIC
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_fund VARCHAR(10);
  v_last_id UUID;
  v_last_date DATE;
  v_last_posted TIMESTAMPTZ;
  v_amount NUMERIC(18, 2);
BEGIN
  IF NOT (
    auth.role() = 'service_role'
    OR public.has_staff_role(ARRAY['admin', 'finance']::public.staff_role[])
  ) THEN
    RAISE EXCEPTION 'finance or admin role required';
  END IF;

  v_fund := public.petty_cash_funding_account_code();

  -- Last float/replenish: Dr 1110 / Cr funding (1100)
  SELECT e.id, e.entry_date, e.posted_at
  INTO v_last_id, v_last_date, v_last_posted
  FROM public.journal_entries e
  WHERE e.status = 'posted'
    AND e.currency = p_currency
    AND e.entry_date <= COALESCE(p_as_of, CURRENT_DATE)
    AND EXISTS (
      SELECT 1 FROM public.journal_entry_lines d
      WHERE d.journal_entry_id = e.id
        AND d.account_code = '1110'
        AND d.debit > 0
    )
    AND EXISTS (
      SELECT 1 FROM public.journal_entry_lines c
      WHERE c.journal_entry_id = e.id
        AND c.account_code = v_fund
        AND c.credit > 0
    )
  ORDER BY e.entry_date DESC, e.posted_at DESC NULLS LAST, e.id DESC
  LIMIT 1;

  -- Sum spends (credits to 1110) after that float
  SELECT COALESCE(SUM(l.credit), 0)
  INTO v_amount
  FROM public.journal_entry_lines l
  JOIN public.journal_entries e ON e.id = l.journal_entry_id
  WHERE e.status = 'posted'
    AND e.currency = p_currency
    AND l.account_code = '1110'
    AND l.credit > 0
    AND e.entry_date <= COALESCE(p_as_of, CURRENT_DATE)
    AND (
      v_last_id IS NULL
      OR e.entry_date > v_last_date
      OR (
        e.entry_date = v_last_date
        AND e.posted_at > v_last_posted
      )
      OR (
        e.entry_date = v_last_date
        AND e.posted_at IS NOT DISTINCT FROM v_last_posted
        AND e.id > v_last_id
      )
    );

  RETURN COALESCE(v_amount, 0);
END;
$$;

-- ---------------------------------------------------------------------------
-- Phase C — finance requisitions (petty + payment)
-- ---------------------------------------------------------------------------
CREATE TYPE public.finance_requisition_type AS ENUM ('petty_cash', 'payment');
CREATE TYPE public.finance_requisition_status AS ENUM (
  'draft',
  'submitted',
  'approved',
  'rejected',
  'disbursed',
  'cancelled'
);

INSERT INTO public.naming_series (prefix, description, pad_length) VALUES
  ('FREQ-', 'Finance requisition', 5)
ON CONFLICT (prefix) DO NOTHING;

CREATE TABLE public.finance_requisitions (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  document_number TEXT,
  req_type public.finance_requisition_type NOT NULL,
  status public.finance_requisition_status NOT NULL DEFAULT 'draft',
  amount NUMERIC(18, 2) NOT NULL CHECK (amount > 0),
  currency public.currency_code NOT NULL,
  exchange_rate_applied NUMERIC(18, 8),
  payee TEXT,
  memo TEXT,
  expense_account_code VARCHAR(10) NOT NULL REFERENCES public.chart_of_accounts (code),
  cash_account_code VARCHAR(10) NOT NULL REFERENCES public.chart_of_accounts (code),
  requested_by UUID NOT NULL REFERENCES auth.users (id),
  submitted_at TIMESTAMPTZ,
  approved_by UUID REFERENCES auth.users (id),
  approved_at TIMESTAMPTZ,
  rejected_by UUID REFERENCES auth.users (id),
  rejected_at TIMESTAMPTZ,
  rejection_reason TEXT,
  disbursed_by UUID REFERENCES auth.users (id),
  disbursed_at TIMESTAMPTZ,
  journal_entry_id UUID REFERENCES public.journal_entries (id),
  payment_entry_id UUID REFERENCES public.payment_entries (id),
  cancelled_at TIMESTAMPTZ,
  cancelled_by UUID REFERENCES auth.users (id),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX finance_requisitions_status_idx
  ON public.finance_requisitions (status, created_at DESC);
CREATE INDEX finance_requisitions_requested_by_idx
  ON public.finance_requisitions (requested_by, created_at DESC);

CREATE OR REPLACE FUNCTION public._finance_req_rpc_active()
RETURNS boolean
LANGUAGE sql
STABLE
AS $$
  SELECT COALESCE(current_setting('app.finance_req_rpc', true), '') = '1';
$$;

CREATE OR REPLACE FUNCTION public._finance_req_rpc_enter()
RETURNS void
LANGUAGE plpgsql
AS $$
BEGIN
  PERFORM set_config('app.finance_req_rpc', '1', true);
END;
$$;

CREATE OR REPLACE FUNCTION public.forbid_finance_requisition_direct_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
  IF public._finance_req_rpc_active() THEN
    IF TG_OP = 'DELETE' THEN
      RETURN OLD;
    END IF;
    RETURN NEW;
  END IF;
  RAISE EXCEPTION 'finance_requisitions: use requisition RPCs';
END;
$$;

CREATE TRIGGER finance_requisitions_mutation_guard
  BEFORE INSERT OR UPDATE OR DELETE ON public.finance_requisitions
  FOR EACH ROW
  EXECUTE PROCEDURE public.forbid_finance_requisition_direct_mutation();

CREATE OR REPLACE FUNCTION public.create_finance_requisition(
  p_req_type public.finance_requisition_type,
  p_amount NUMERIC,
  p_currency public.currency_code,
  p_payee TEXT DEFAULT NULL,
  p_memo TEXT DEFAULT NULL,
  p_expense_account_code VARCHAR(10) DEFAULT '5300',
  p_cash_account_code VARCHAR(10) DEFAULT NULL,
  p_exchange_rate NUMERIC DEFAULT NULL
)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_id UUID;
  v_cash VARCHAR(10);
  v_uid UUID := auth.uid();
BEGIN
  IF v_uid IS NULL THEN
    RAISE EXCEPTION 'auth.uid() required to create requisition';
  END IF;

  IF NOT (
    auth.role() = 'service_role'
    OR public.is_staff()
    OR public.has_staff_role(
      ARRAY['admin', 'finance', 'sales', 'warehouse', 'dispatcher', 'hr']::public.staff_role[]
    )
  ) THEN
    RAISE EXCEPTION 'staff role required to create requisition';
  END IF;

  IF p_amount IS NULL OR p_amount <= 0 THEN
    RAISE EXCEPTION 'amount must be > 0';
  END IF;

  v_cash := COALESCE(
    nullif(trim(p_cash_account_code), ''),
    CASE
      WHEN p_req_type = 'petty_cash' THEN '1110'
      ELSE public.petty_cash_funding_account_code()
    END
  );

  IF p_req_type = 'payment' AND v_cash = '1110' THEN
    v_cash := public.petty_cash_funding_account_code();
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM public.chart_of_accounts c
    WHERE c.code = COALESCE(p_expense_account_code, '5300') AND c.is_active
  ) THEN
    RAISE EXCEPTION 'expense account not found: %', p_expense_account_code;
  END IF;
  IF NOT EXISTS (
    SELECT 1 FROM public.chart_of_accounts c
    WHERE c.code = v_cash AND c.is_active
  ) THEN
    RAISE EXCEPTION 'cash account not found: %', v_cash;
  END IF;

  PERFORM public._finance_req_rpc_enter();

  INSERT INTO public.finance_requisitions (
    req_type, status, amount, currency, exchange_rate_applied,
    payee, memo, expense_account_code, cash_account_code, requested_by
  )
  VALUES (
    p_req_type,
    'draft',
    p_amount,
    p_currency,
    CASE
      WHEN p_currency = 'USD' THEN COALESCE(p_exchange_rate, 1)
      ELSE p_exchange_rate
    END,
    nullif(trim(p_payee), ''),
    nullif(trim(p_memo), ''),
    COALESCE(p_expense_account_code, '5300'),
    v_cash,
    v_uid
  )
  RETURNING id INTO v_id;

  RETURN v_id;
END;
$$;

CREATE OR REPLACE FUNCTION public.submit_finance_requisition(p_requisition_id UUID)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_row public.finance_requisitions%ROWTYPE;
BEGIN
  SELECT * INTO v_row
  FROM public.finance_requisitions
  WHERE id = p_requisition_id
  FOR UPDATE;

  IF NOT FOUND THEN
    RAISE EXCEPTION 'requisition not found: %', p_requisition_id;
  END IF;

  IF NOT (
    auth.role() = 'service_role'
    OR public.has_staff_role(ARRAY['admin', 'finance']::public.staff_role[])
    OR v_row.requested_by = auth.uid()
  ) THEN
    RAISE EXCEPTION 'not allowed to submit this requisition';
  END IF;

  IF v_row.status <> 'draft' THEN
    RAISE EXCEPTION 'only draft requisitions can be submitted (status=%)', v_row.status;
  END IF;

  PERFORM public._finance_req_rpc_enter();

  UPDATE public.finance_requisitions
  SET
    status = 'submitted',
    submitted_at = now(),
    document_number = COALESCE(document_number, public.next_series_value('FREQ-')),
    updated_at = now()
  WHERE id = p_requisition_id;

  RETURN p_requisition_id;
END;
$$;

CREATE OR REPLACE FUNCTION public.approve_finance_requisition(p_requisition_id UUID)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_status public.finance_requisition_status;
BEGIN
  IF NOT (
    auth.role() = 'service_role'
    OR public.has_staff_role(ARRAY['admin', 'finance']::public.staff_role[])
  ) THEN
    RAISE EXCEPTION 'finance or admin role required to approve';
  END IF;

  SELECT status INTO v_status
  FROM public.finance_requisitions
  WHERE id = p_requisition_id
  FOR UPDATE;

  IF NOT FOUND THEN
    RAISE EXCEPTION 'requisition not found: %', p_requisition_id;
  END IF;
  IF v_status <> 'submitted' THEN
    RAISE EXCEPTION 'only submitted requisitions can be approved (status=%)', v_status;
  END IF;

  PERFORM public._finance_req_rpc_enter();

  UPDATE public.finance_requisitions
  SET
    status = 'approved',
    approved_by = auth.uid(),
    approved_at = now(),
    updated_at = now()
  WHERE id = p_requisition_id;

  RETURN p_requisition_id;
END;
$$;

CREATE OR REPLACE FUNCTION public.reject_finance_requisition(
  p_requisition_id UUID,
  p_reason TEXT DEFAULT NULL
)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_status public.finance_requisition_status;
BEGIN
  IF NOT (
    auth.role() = 'service_role'
    OR public.has_staff_role(ARRAY['admin', 'finance']::public.staff_role[])
  ) THEN
    RAISE EXCEPTION 'finance or admin role required to reject';
  END IF;

  SELECT status INTO v_status
  FROM public.finance_requisitions
  WHERE id = p_requisition_id
  FOR UPDATE;

  IF NOT FOUND THEN
    RAISE EXCEPTION 'requisition not found: %', p_requisition_id;
  END IF;
  IF v_status NOT IN ('submitted', 'approved') THEN
    RAISE EXCEPTION 'only submitted/approved requisitions can be rejected (status=%)', v_status;
  END IF;

  PERFORM public._finance_req_rpc_enter();

  UPDATE public.finance_requisitions
  SET
    status = 'rejected',
    rejected_by = auth.uid(),
    rejected_at = now(),
    rejection_reason = nullif(trim(p_reason), ''),
    updated_at = now()
  WHERE id = p_requisition_id;

  RETURN p_requisition_id;
END;
$$;

CREATE OR REPLACE FUNCTION public.cancel_finance_requisition(p_requisition_id UUID)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_row public.finance_requisitions%ROWTYPE;
BEGIN
  SELECT * INTO v_row
  FROM public.finance_requisitions
  WHERE id = p_requisition_id
  FOR UPDATE;

  IF NOT FOUND THEN
    RAISE EXCEPTION 'requisition not found: %', p_requisition_id;
  END IF;

  IF NOT (
    auth.role() = 'service_role'
    OR public.has_staff_role(ARRAY['admin', 'finance']::public.staff_role[])
    OR v_row.requested_by = auth.uid()
  ) THEN
    RAISE EXCEPTION 'not allowed to cancel this requisition';
  END IF;

  IF v_row.status NOT IN ('draft', 'submitted', 'rejected') THEN
    RAISE EXCEPTION 'cannot cancel requisition in status %', v_row.status;
  END IF;

  PERFORM public._finance_req_rpc_enter();

  UPDATE public.finance_requisitions
  SET
    status = 'cancelled',
    cancelled_at = now(),
    cancelled_by = auth.uid(),
    updated_at = now()
  WHERE id = p_requisition_id;

  RETURN p_requisition_id;
END;
$$;

CREATE OR REPLACE FUNCTION public.disburse_finance_requisition(
  p_requisition_id UUID,
  p_entry_date DATE DEFAULT CURRENT_DATE
)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_row public.finance_requisitions%ROWTYPE;
  v_je UUID;
  v_desc TEXT;
  v_rate NUMERIC;
BEGIN
  IF NOT (
    auth.role() = 'service_role'
    OR public.has_staff_role(ARRAY['admin', 'finance']::public.staff_role[])
  ) THEN
    RAISE EXCEPTION 'finance or admin role required to disburse';
  END IF;

  SELECT * INTO v_row
  FROM public.finance_requisitions
  WHERE id = p_requisition_id
  FOR UPDATE;

  IF NOT FOUND THEN
    RAISE EXCEPTION 'requisition not found: %', p_requisition_id;
  END IF;
  IF v_row.status <> 'approved' THEN
    RAISE EXCEPTION 'disburse requires approved status (got %); cannot skip approval', v_row.status;
  END IF;
  IF v_row.journal_entry_id IS NOT NULL THEN
    RAISE EXCEPTION 'requisition already disbursed';
  END IF;

  v_desc := format(
    'Disburse %s %s — %s',
    COALESCE(v_row.document_number, p_requisition_id::text),
    v_row.req_type::text,
    COALESCE(v_row.payee, v_row.memo, 'requisition')
  );

  v_rate := CASE
    WHEN v_row.currency = 'USD' THEN COALESCE(v_row.exchange_rate_applied, 1)
    ELSE v_row.exchange_rate_applied
  END;

  -- Append-only JE: Dr expense / Cr cash (1110 petty or 1100 bank)
  v_je := public.post_journal_entry(
    COALESCE(p_entry_date, CURRENT_DATE),
    v_desc,
    v_row.currency,
    v_rate,
    jsonb_build_array(
      jsonb_build_object(
        'account_code', v_row.expense_account_code,
        'debit', v_row.amount,
        'credit', 0,
        'currency', v_row.currency
      ),
      jsonb_build_object(
        'account_code', v_row.cash_account_code,
        'debit', 0,
        'credit', v_row.amount,
        'currency', v_row.currency
      )
    )
  );

  PERFORM public._finance_req_rpc_enter();

  UPDATE public.finance_requisitions
  SET
    status = 'disbursed',
    disbursed_by = auth.uid(),
    disbursed_at = now(),
    journal_entry_id = v_je,
    updated_at = now()
  WHERE id = p_requisition_id;

  RETURN v_je;
END;
$$;

ALTER TABLE public.finance_requisitions ENABLE ROW LEVEL SECURITY;

CREATE POLICY finance_requisitions_select
  ON public.finance_requisitions FOR SELECT TO authenticated
  USING (
    requested_by = auth.uid()
    OR public.has_staff_role(ARRAY['admin', 'finance']::public.staff_role[])
    OR (
      public.is_staff()
      AND status IN ('submitted', 'approved', 'rejected', 'disbursed', 'cancelled')
    )
  );

REVOKE ALL ON TABLE public.finance_requisitions FROM PUBLIC, anon;
GRANT SELECT ON TABLE public.finance_requisitions TO authenticated, service_role;
GRANT ALL ON TABLE public.finance_requisitions TO service_role;

-- ---------------------------------------------------------------------------
-- Grants
-- ---------------------------------------------------------------------------
REVOKE ALL ON FUNCTION public.open_account_period(
  VARCHAR, public.currency_code, DATE, DATE, NUMERIC, TEXT
) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.close_account_period(UUID, NUMERIC, TEXT) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.report_account_register(
  VARCHAR, DATE, DATE, public.currency_code
) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.petty_cash_funding_account_code() FROM PUBLIC;
REVOKE ALL ON FUNCTION public.compute_petty_cash_replenish_amount(
  public.currency_code, DATE
) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.create_finance_requisition(
  public.finance_requisition_type, NUMERIC, public.currency_code,
  TEXT, TEXT, VARCHAR, VARCHAR, NUMERIC
) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.submit_finance_requisition(UUID) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.approve_finance_requisition(UUID) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.reject_finance_requisition(UUID, TEXT) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.cancel_finance_requisition(UUID) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.disburse_finance_requisition(UUID, DATE) FROM PUBLIC;

GRANT EXECUTE ON FUNCTION public.open_account_period(
  VARCHAR, public.currency_code, DATE, DATE, NUMERIC, TEXT
) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.close_account_period(UUID, NUMERIC, TEXT)
  TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.report_account_register(
  VARCHAR, DATE, DATE, public.currency_code
) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.petty_cash_funding_account_code()
  TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.compute_petty_cash_replenish_amount(
  public.currency_code, DATE
) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.create_finance_requisition(
  public.finance_requisition_type, NUMERIC, public.currency_code,
  TEXT, TEXT, VARCHAR, VARCHAR, NUMERIC
) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.submit_finance_requisition(UUID)
  TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.approve_finance_requisition(UUID)
  TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.reject_finance_requisition(UUID, TEXT)
  TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.cancel_finance_requisition(UUID)
  TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.disburse_finance_requisition(UUID, DATE)
  TO authenticated, service_role;
