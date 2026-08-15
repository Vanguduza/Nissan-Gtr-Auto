-- Allow sales/warehouse staff to open/close cash-sales till float (1120 only).
-- Petty cash 1110 remains finance/admin-only.
-- Pattern: SECURITY DEFINER + has_staff_role (same as existing period RPCs).

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
  v_code TEXT;
BEGIN
  v_code := btrim(COALESCE(p_account_code, ''));

  IF NOT (
    auth.role() = 'service_role'
    OR public.has_staff_role(ARRAY['admin', 'finance']::public.staff_role[])
    OR (
      v_code = '1120'
      AND public.has_staff_role(
        ARRAY['admin', 'finance', 'sales', 'warehouse']::public.staff_role[]
      )
    )
  ) THEN
    RAISE EXCEPTION 'finance or admin role required (sales/warehouse may open 1120 only)';
  END IF;

  IF v_code = '1110'
     AND NOT (
       auth.role() = 'service_role'
       OR public.has_staff_role(ARRAY['admin', 'finance']::public.staff_role[])
     )
  THEN
    RAISE EXCEPTION 'petty cash 1110 is finance-only';
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
  SELECT * INTO v_row
  FROM public.account_period_balances
  WHERE id = p_period_id
  FOR UPDATE;

  IF NOT FOUND THEN
    RAISE EXCEPTION 'account period not found: %', p_period_id;
  END IF;

  IF NOT (
    auth.role() = 'service_role'
    OR public.has_staff_role(ARRAY['admin', 'finance']::public.staff_role[])
    OR (
      v_row.account_code = '1120'
      AND public.has_staff_role(
        ARRAY['admin', 'finance', 'sales', 'warehouse']::public.staff_role[]
      )
    )
  ) THEN
    RAISE EXCEPTION 'finance or admin role required (sales/warehouse may close 1120 only)';
  END IF;

  IF v_row.account_code = '1110'
     AND NOT (
       auth.role() = 'service_role'
       OR public.has_staff_role(ARRAY['admin', 'finance']::public.staff_role[])
     )
  THEN
    RAISE EXCEPTION 'petty cash 1110 is finance-only';
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

COMMENT ON FUNCTION public.open_account_period(
  VARCHAR, public.currency_code, DATE, DATE, NUMERIC, TEXT
) IS
  'Open account period. Finance/admin any CoA; sales/warehouse may open 1120 cash-sales till only. 1110 petty finance-only.';

COMMENT ON FUNCTION public.close_account_period(UUID, NUMERIC, TEXT) IS
  'Close account period. Finance/admin any; sales/warehouse may close 1120 only. 1110 finance-only.';

-- Smoke (manual / CI snippet):
-- SELECT public.open_account_period('1120', 'USD', CURRENT_DATE, CURRENT_DATE, 100, 'pos smoke');
-- -- expect: UUID; sales role OK
-- SELECT public.open_account_period('1110', 'USD', CURRENT_DATE, CURRENT_DATE, 50, 'should fail for sales');
-- -- expect: exception for non-finance
