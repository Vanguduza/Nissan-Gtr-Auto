-- Phase 3 follow-up: gate remaining statement RPCs to finance/admin

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
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
  IF NOT (
    auth.role() = 'service_role'
    OR public.has_staff_role(ARRAY['admin', 'finance']::public.staff_role[])
  ) THEN
    RAISE EXCEPTION 'finance or admin role required';
  END IF;

  RETURN QUERY
  SELECT
    c.code,
    c.name,
    c.account_type,
    COALESCE(SUM(l.credit - l.debit), 0),
    COALESCE(SUM(
      public._line_usd_equiv(l.credit, e.currency, e.exchange_rate_applied)
      - public._line_usd_equiv(l.debit, e.currency, e.exchange_rate_applied)
    ), 0)
  FROM public.chart_of_accounts c
  JOIN public.journal_entry_lines l ON l.account_code = c.code
  JOIN public.journal_entries e ON e.id = l.journal_entry_id
  WHERE e.status = 'posted'
    AND e.entry_date BETWEEN p_from AND p_to
    AND c.account_type IN ('income', 'expense')
    AND (p_currency IS NULL OR e.currency = p_currency)
  GROUP BY c.code, c.name, c.account_type
  ORDER BY c.code;
END;
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
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
  IF NOT (
    auth.role() = 'service_role'
    OR public.has_staff_role(ARRAY['admin', 'finance']::public.staff_role[])
  ) THEN
    RAISE EXCEPTION 'finance or admin role required';
  END IF;

  RETURN QUERY
  SELECT
    c.code,
    c.name,
    c.account_type,
    CASE
      WHEN c.account_type IN ('asset', 'expense') THEN COALESCE(SUM(l.debit - l.credit), 0)
      ELSE COALESCE(SUM(l.credit - l.debit), 0)
    END,
    CASE
      WHEN c.account_type IN ('asset', 'expense') THEN COALESCE(SUM(
        public._line_usd_equiv(l.debit, e.currency, e.exchange_rate_applied)
        - public._line_usd_equiv(l.credit, e.currency, e.exchange_rate_applied)
      ), 0)
      ELSE COALESCE(SUM(
        public._line_usd_equiv(l.credit, e.currency, e.exchange_rate_applied)
        - public._line_usd_equiv(l.debit, e.currency, e.exchange_rate_applied)
      ), 0)
    END
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
END;
$$;

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
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
  IF NOT (
    auth.role() = 'service_role'
    OR public.has_staff_role(ARRAY['admin', 'finance']::public.staff_role[])
  ) THEN
    RAISE EXCEPTION 'finance or admin role required';
  END IF;

  RETURN QUERY
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
    SELECT COALESCE(SUM(r.amount_usd), 0) AS net_income_usd
    FROM public.report_profit_and_loss(p_from, p_to, p_currency) r
  )
  SELECT 'operating'::text, 'Net income (P&L proxy)'::text, (SELECT net_income_usd FROM pl)
  UNION ALL
  SELECT 'operating', 'Net change in cash (1100)', (SELECT net_cash_usd FROM cash_moves)
  UNION ALL
  SELECT 'investing', 'Not classified (Phase 3 heuristic)', 0::numeric
  UNION ALL
  SELECT 'financing', 'Not classified (Phase 3 heuristic)', 0::numeric;
END;
$$;
