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
  ORDER BY e.entry_date DESC, e.posted_at DESC NULLS LAST, e.ctid DESC
  LIMIT 1;

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
        AND e.ctid > (
          SELECT j.ctid FROM public.journal_entries j WHERE j.id = v_last_id
        )
      )
    );

  RETURN COALESCE(v_amount, 0);
END;
$$;
