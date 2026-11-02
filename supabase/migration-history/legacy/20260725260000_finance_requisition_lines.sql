-- Finance Phase D: requisition line items + multi-line disburse JE
-- Prior: 20260725250000_finance_period_balances_requisitions
-- payment_entry_id remains reserved (AR payment_entries unsuitable for vendor payouts)
-- Exclusions: no ZIMRA / fiscal; no payroll tax. Journals remain append-only.

-- ---------------------------------------------------------------------------
-- Lines table
-- ---------------------------------------------------------------------------
CREATE TABLE public.finance_requisition_lines (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  requisition_id UUID NOT NULL REFERENCES public.finance_requisitions (id) ON DELETE CASCADE,
  line_no INT NOT NULL CHECK (line_no > 0),
  description TEXT,
  expense_account_code VARCHAR(10) NOT NULL REFERENCES public.chart_of_accounts (code),
  amount NUMERIC(18, 2) NOT NULL CHECK (amount > 0),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (requisition_id, line_no)
);

CREATE INDEX finance_requisition_lines_req_idx
  ON public.finance_requisition_lines (requisition_id, line_no);

-- Backfill one line from existing header-only requisitions
INSERT INTO public.finance_requisition_lines (
  requisition_id, line_no, description, expense_account_code, amount
)
SELECT
  r.id,
  1,
  COALESCE(r.memo, r.payee, 'Requisition'),
  r.expense_account_code,
  r.amount
FROM public.finance_requisitions r
WHERE NOT EXISTS (
  SELECT 1 FROM public.finance_requisition_lines l WHERE l.requisition_id = r.id
);

CREATE OR REPLACE FUNCTION public.forbid_finance_requisition_line_direct_mutation()
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
  RAISE EXCEPTION 'finance_requisition_lines: use requisition RPCs';
END;
$$;

CREATE TRIGGER finance_requisition_lines_mutation_guard
  BEFORE INSERT OR UPDATE OR DELETE ON public.finance_requisition_lines
  FOR EACH ROW
  EXECUTE PROCEDURE public.forbid_finance_requisition_line_direct_mutation();

-- ---------------------------------------------------------------------------
-- Replace lines on draft (syncs header amount + primary expense code)
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.set_finance_requisition_lines(
  p_requisition_id UUID,
  p_lines JSONB
)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_row public.finance_requisitions%ROWTYPE;
  v_elem JSONB;
  v_line_no INT := 0;
  v_total NUMERIC(18, 2) := 0;
  v_first_expense VARCHAR(10);
  v_desc TEXT;
  v_expense VARCHAR(10);
  v_amount NUMERIC;
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
    RAISE EXCEPTION 'not allowed to edit this requisition';
  END IF;

  IF v_row.status <> 'draft' THEN
    RAISE EXCEPTION 'only draft requisitions can change lines (status=%)', v_row.status;
  END IF;

  IF p_lines IS NULL OR jsonb_typeof(p_lines) <> 'array' OR jsonb_array_length(p_lines) < 1 THEN
    RAISE EXCEPTION 'at least one requisition line is required';
  END IF;

  PERFORM public._finance_req_rpc_enter();

  DELETE FROM public.finance_requisition_lines WHERE requisition_id = p_requisition_id;

  FOR v_elem IN SELECT * FROM jsonb_array_elements(p_lines)
  LOOP
    v_line_no := v_line_no + 1;
    v_expense := nullif(trim(COALESCE(v_elem->>'expense_account_code', '')), '');
    v_amount := (v_elem->>'amount')::NUMERIC;
    v_desc := nullif(trim(COALESCE(v_elem->>'description', '')), '');

    IF v_expense IS NULL THEN
      RAISE EXCEPTION 'line %: expense_account_code required', v_line_no;
    END IF;
    IF v_amount IS NULL OR v_amount <= 0 THEN
      RAISE EXCEPTION 'line %: amount must be > 0', v_line_no;
    END IF;
    IF NOT EXISTS (
      SELECT 1 FROM public.chart_of_accounts c
      WHERE c.code = v_expense AND c.is_active
    ) THEN
      RAISE EXCEPTION 'line %: expense account not found: %', v_line_no, v_expense;
    END IF;

    IF v_first_expense IS NULL THEN
      v_first_expense := v_expense;
    END IF;
    v_total := v_total + v_amount;

    INSERT INTO public.finance_requisition_lines (
      requisition_id, line_no, description, expense_account_code, amount
    ) VALUES (
      p_requisition_id, v_line_no, v_desc, v_expense, v_amount
    );
  END LOOP;

  UPDATE public.finance_requisitions
  SET
    amount = v_total,
    expense_account_code = v_first_expense,
    updated_at = now()
  WHERE id = p_requisition_id;

  RETURN p_requisition_id;
END;
$$;

-- ---------------------------------------------------------------------------
-- create_finance_requisition — seed one line from header args
-- ---------------------------------------------------------------------------
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
  v_expense VARCHAR(10) := COALESCE(nullif(trim(p_expense_account_code), ''), '5300');
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

  IF p_currency = 'ZIG' AND (p_exchange_rate IS NULL OR p_exchange_rate <= 0) THEN
    RAISE EXCEPTION 'exchange_rate_applied required for ZIG requisitions';
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
    WHERE c.code = v_expense AND c.is_active
  ) THEN
    RAISE EXCEPTION 'expense account not found: %', v_expense;
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
    v_expense,
    v_cash,
    v_uid
  )
  RETURNING id INTO v_id;

  INSERT INTO public.finance_requisition_lines (
    requisition_id, line_no, description, expense_account_code, amount
  ) VALUES (
    v_id,
    1,
    COALESCE(nullif(trim(p_memo), ''), nullif(trim(p_payee), ''), 'Requisition'),
    v_expense,
    p_amount
  );

  RETURN v_id;
END;
$$;

-- ---------------------------------------------------------------------------
-- submit — require at least one line; header amount must match Σ lines
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.submit_finance_requisition(p_requisition_id UUID)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_row public.finance_requisitions%ROWTYPE;
  v_line_sum NUMERIC(18, 2);
  v_line_count INT;
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

  SELECT COUNT(*), COALESCE(SUM(amount), 0)
  INTO v_line_count, v_line_sum
  FROM public.finance_requisition_lines
  WHERE requisition_id = p_requisition_id;

  IF v_line_count < 1 THEN
    RAISE EXCEPTION 'cannot submit requisition without lines';
  END IF;
  IF v_line_sum IS DISTINCT FROM v_row.amount THEN
    RAISE EXCEPTION 'header amount % does not match line total %', v_row.amount, v_line_sum;
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

-- ---------------------------------------------------------------------------
-- disburse — multi-line Dr expenses / single Cr cash; journal_entry_id only
-- payment_entry_id intentionally left NULL (AR payment_entries need customer_id)
-- ---------------------------------------------------------------------------
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
  v_lines JSONB := '[]'::jsonb;
  v_line RECORD;
  v_line_sum NUMERIC(18, 2) := 0;
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
  IF v_row.currency = 'ZIG'
     AND (v_row.exchange_rate_applied IS NULL OR v_row.exchange_rate_applied <= 0) THEN
    RAISE EXCEPTION 'exchange_rate_applied required to disburse ZIG requisition';
  END IF;

  FOR v_line IN
    SELECT line_no, description, expense_account_code, amount
    FROM public.finance_requisition_lines
    WHERE requisition_id = p_requisition_id
    ORDER BY line_no
  LOOP
    v_line_sum := v_line_sum + v_line.amount;
    v_lines := v_lines || jsonb_build_array(
      jsonb_build_object(
        'account_code', v_line.expense_account_code,
        'debit', v_line.amount,
        'credit', 0,
        'currency', v_row.currency
      )
    );
  END LOOP;

  IF jsonb_array_length(v_lines) < 1 THEN
    RAISE EXCEPTION 'cannot disburse requisition without lines';
  END IF;
  IF v_line_sum IS DISTINCT FROM v_row.amount THEN
    RAISE EXCEPTION 'header amount % does not match line total %', v_row.amount, v_line_sum;
  END IF;

  v_lines := v_lines || jsonb_build_array(
    jsonb_build_object(
      'account_code', v_row.cash_account_code,
      'debit', 0,
      'credit', v_row.amount,
      'currency', v_row.currency
    )
  );

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

  -- Append-only JE: Dr expense line(s) / Cr cash (1110 petty or 1100 bank)
  v_je := public.post_journal_entry(
    COALESCE(p_entry_date, CURRENT_DATE),
    v_desc,
    v_row.currency,
    v_rate,
    v_lines
  );

  PERFORM public._finance_req_rpc_enter();

  UPDATE public.finance_requisitions
  SET
    status = 'disbursed',
    disbursed_by = auth.uid(),
    disbursed_at = now(),
    journal_entry_id = v_je,
    -- payment_entry_id reserved for future AP payment desk (not AR receipts)
    payment_entry_id = NULL,
    updated_at = now()
  WHERE id = p_requisition_id;

  RETURN v_je;
END;
$$;

-- ---------------------------------------------------------------------------
-- RLS + grants
-- ---------------------------------------------------------------------------
ALTER TABLE public.finance_requisition_lines ENABLE ROW LEVEL SECURITY;

CREATE POLICY finance_requisition_lines_select
  ON public.finance_requisition_lines FOR SELECT TO authenticated
  USING (
    EXISTS (
      SELECT 1
      FROM public.finance_requisitions r
      WHERE r.id = finance_requisition_lines.requisition_id
        AND (
          r.requested_by = auth.uid()
          OR public.has_staff_role(ARRAY['admin', 'finance']::public.staff_role[])
          OR (
            public.is_staff()
            AND r.status IN ('submitted', 'approved', 'rejected', 'disbursed', 'cancelled')
          )
        )
    )
  );

REVOKE ALL ON TABLE public.finance_requisition_lines FROM PUBLIC, anon;
GRANT SELECT ON TABLE public.finance_requisition_lines TO authenticated, service_role;
GRANT ALL ON TABLE public.finance_requisition_lines TO service_role;

REVOKE ALL ON FUNCTION public.forbid_finance_requisition_line_direct_mutation() FROM PUBLIC;
REVOKE ALL ON FUNCTION public.set_finance_requisition_lines(UUID, JSONB) FROM PUBLIC;

GRANT EXECUTE ON FUNCTION public.set_finance_requisition_lines(UUID, JSONB)
  TO authenticated, service_role;

-- Recreate grants for replaced functions (CREATE OR REPLACE keeps ownership; re-assert EXECUTE)
GRANT EXECUTE ON FUNCTION public.create_finance_requisition(
  public.finance_requisition_type, NUMERIC, public.currency_code,
  TEXT, TEXT, VARCHAR, VARCHAR, NUMERIC
) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.submit_finance_requisition(UUID)
  TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.disburse_finance_requisition(UUID, DATE)
  TO authenticated, service_role;
