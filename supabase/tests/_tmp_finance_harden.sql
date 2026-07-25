-- Local re-apply of security hardenings from 20260725250000 (already applied).

REVOKE ALL ON FUNCTION public._finance_period_rpc_active() FROM PUBLIC;
REVOKE ALL ON FUNCTION public._finance_period_rpc_enter() FROM PUBLIC;
REVOKE ALL ON FUNCTION public.forbid_account_period_direct_mutation() FROM PUBLIC;
REVOKE ALL ON FUNCTION public._finance_req_rpc_active() FROM PUBLIC;
REVOKE ALL ON FUNCTION public._finance_req_rpc_enter() FROM PUBLIC;
REVOKE ALL ON FUNCTION public.forbid_finance_requisition_direct_mutation() FROM PUBLIC;

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
  IF v_row.currency = 'ZIG'
     AND (v_row.exchange_rate_applied IS NULL OR v_row.exchange_rate_applied <= 0) THEN
    RAISE EXCEPTION 'exchange_rate_applied required to disburse ZIG requisition';
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
