-- Payroll funding + schedule runner: GL journals (5200/2150/1100) + payslip export hooks.
-- GROSS payroll only — no PAYE / NSSA / statutory. Net = gross − manual deductions.
-- Funding = append-only JE credit to cash/bank (no ContiPay disbursement).
-- Exclusions: no ZIMRA / fiscal QR / payroll tax columns.

-- ---------------------------------------------------------------------------
-- CoA: Salaries Payable
-- ---------------------------------------------------------------------------
INSERT INTO public.chart_of_accounts (code, name, account_type) VALUES
  ('2150', 'Salaries Payable', 'liability')
ON CONFLICT (code) DO UPDATE
SET
  name = EXCLUDED.name,
  account_type = EXCLUDED.account_type;

-- Display name if column exists (batch1 CoA)
DO $$
BEGIN
  IF EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = 'public'
      AND table_name = 'chart_of_accounts'
      AND column_name = 'display_name'
  ) THEN
    UPDATE public.chart_of_accounts
    SET display_name = COALESCE(display_name, 'Salaries payable (gross payroll)')
    WHERE code = '2150';
  END IF;
END;
$$;

-- ---------------------------------------------------------------------------
-- payroll_lines funding columns (immutable money; funding via RPC only)
-- ---------------------------------------------------------------------------
ALTER TABLE public.payroll_lines
  ADD COLUMN IF NOT EXISTS accrual_journal_id UUID REFERENCES public.journal_entries (id),
  ADD COLUMN IF NOT EXISTS payment_journal_id UUID REFERENCES public.journal_entries (id),
  ADD COLUMN IF NOT EXISTS funded_at TIMESTAMPTZ,
  ADD COLUMN IF NOT EXISTS funded_by UUID REFERENCES auth.users (id),
  ADD COLUMN IF NOT EXISTS cash_account_code VARCHAR(10) REFERENCES public.chart_of_accounts (code);

COMMENT ON COLUMN public.payroll_lines.accrual_journal_id IS
  'JE: Dr 5200 Payroll Expense / Cr 2150 Salaries Payable (net).';
COMMENT ON COLUMN public.payroll_lines.payment_journal_id IS
  'JE: Dr 2150 / Cr cash (1100 default). Booked funding — not bank API.';
COMMENT ON COLUMN public.payroll_lines.funded_at IS
  'When net was funded from cash GL; null until fund_payroll_* RPC.';

CREATE INDEX IF NOT EXISTS payroll_lines_funded_idx
  ON public.payroll_lines (funded_at)
  WHERE funded_at IS NOT NULL;

-- ---------------------------------------------------------------------------
-- Schedule: cash account, currency, next_run, auto_fund; HR may write
-- ---------------------------------------------------------------------------
ALTER TABLE public.hr_payslip_schedules
  ADD COLUMN IF NOT EXISTS cash_account_code VARCHAR(10)
    REFERENCES public.chart_of_accounts (code) DEFAULT '1100',
  ADD COLUMN IF NOT EXISTS currency public.currency_code NOT NULL DEFAULT 'USD',
  ADD COLUMN IF NOT EXISTS default_exchange_rate NUMERIC(18, 8) NOT NULL DEFAULT 1
    CHECK (default_exchange_rate > 0),
  ADD COLUMN IF NOT EXISTS auto_fund BOOLEAN NOT NULL DEFAULT true,
  ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  ADD COLUMN IF NOT EXISTS updated_by UUID REFERENCES auth.users (id);

UPDATE public.hr_payslip_schedules
SET cash_account_code = COALESCE(cash_account_code, '1100')
WHERE cash_account_code IS NULL;

-- Seed next_run_at when missing (monthly ~25th, weekly Mon, fortnightly 1/15)
UPDATE public.hr_payslip_schedules s
SET next_run_at = COALESCE(
  s.next_run_at,
  CASE s.pay_frequency
    WHEN 'weekly' THEN date_trunc('week', now()) + INTERVAL '7 days' + INTERVAL '6 hours'
    WHEN 'fortnightly' THEN
      CASE
        WHEN EXTRACT(DAY FROM CURRENT_DATE) < 15
          THEN (date_trunc('month', CURRENT_DATE) + INTERVAL '14 days')::timestamptz + INTERVAL '6 hours'
        ELSE (date_trunc('month', CURRENT_DATE) + INTERVAL '1 month')::timestamptz + INTERVAL '6 hours'
      END
    ELSE
      CASE
        WHEN EXTRACT(DAY FROM CURRENT_DATE) < 25
          THEN (date_trunc('month', CURRENT_DATE) + INTERVAL '24 days')::timestamptz + INTERVAL '6 hours'
        ELSE (date_trunc('month', CURRENT_DATE) + INTERVAL '1 month' + INTERVAL '24 days')::timestamptz
          + INTERVAL '6 hours'
      END
  END
)
WHERE s.next_run_at IS NULL;

-- Allow HR (not only admin) to configure schedules
DROP POLICY IF EXISTS hr_payslip_schedules_write ON public.hr_payslip_schedules;
CREATE POLICY hr_payslip_schedules_write ON public.hr_payslip_schedules
  FOR ALL TO authenticated
  USING (public.has_staff_role(ARRAY['admin', 'hr']::public.staff_role[]))
  WITH CHECK (public.has_staff_role(ARRAY['admin', 'hr']::public.staff_role[]));

-- Register worker cadence (ops cron mirrors edge URL)
INSERT INTO public.ai_worker_schedules (worker_key, cadence, cron_expr, edge_path, body_json, notes)
VALUES (
  'process_payroll_schedules',
  'daily',
  '0 6 * * *',
  '/functions/v1/process-payroll-schedules',
  '{}'::jsonb,
  'Due hr_payslip_schedules → create/compute/submit/fund gross payroll + PDF payslips. No tax / ContiPay.'
)
ON CONFLICT (worker_key) DO UPDATE
SET
  cron_expr = EXCLUDED.cron_expr,
  edge_path = EXCLUDED.edge_path,
  notes = EXCLUDED.notes,
  body_json = EXCLUDED.body_json;

-- ---------------------------------------------------------------------------
-- Internal JE poster (DEFINER; caller must already authorize HR/admin/service)
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public._post_journal_entry_payroll(
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
  v_doc TEXT;
  v_date DATE := COALESCE(p_entry_date, CURRENT_DATE);
BEGIN
  IF public.is_period_locked(v_date) THEN
    RAISE EXCEPTION 'accounting period is locked for date %', v_date;
  END IF;

  IF p_lines IS NULL OR jsonb_typeof(p_lines) <> 'array' OR jsonb_array_length(p_lines) = 0 THEN
    RAISE EXCEPTION 'journal lines required';
  END IF;

  INSERT INTO public.journal_entries (
    entry_date, description, currency, exchange_rate_applied, status, posted_by, posted_at
  )
  VALUES (
    v_date,
    p_description,
    p_currency,
    CASE WHEN p_currency = 'USD' THEN COALESCE(p_exchange_rate, 1) ELSE p_exchange_rate END,
    'draft',
    auth.uid(),
    now()
  )
  RETURNING id INTO v_id;

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
  v_doc := public.next_series_value('JV-');

  UPDATE public.journal_entries
  SET
    status = 'posted',
    posted_at = now(),
    posted_by = COALESCE(auth.uid(), posted_by),
    document_number = v_doc
  WHERE id = v_id;

  RETURN v_id;
END;
$$;

COMMENT ON FUNCTION public._post_journal_entry_payroll IS
  'Internal payroll JE poster (append-only). Call only from fund_payroll_* after HR authz.';

-- ---------------------------------------------------------------------------
-- Advance next_run_at helper
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public._hr_payslip_schedule_advance_next(
  p_frequency public.hr_pay_frequency,
  p_from TIMESTAMPTZ DEFAULT now()
)
RETURNS TIMESTAMPTZ
LANGUAGE plpgsql
IMMUTABLE
SET search_path = public
AS $$
BEGIN
  RETURN CASE p_frequency
    WHEN 'weekly' THEN p_from + INTERVAL '7 days'
    WHEN 'fortnightly' THEN p_from + INTERVAL '14 days'
    ELSE p_from + INTERVAL '1 month'
  END;
END;
$$;

-- ---------------------------------------------------------------------------
-- Fund selected payroll lines (accrue + pay + export_payslip metadata)
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.fund_payroll_lines(
  p_payroll_line_ids UUID[],
  p_cash_account_code VARCHAR DEFAULT '1100',
  p_entry_date DATE DEFAULT NULL
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_cash VARCHAR(10) := COALESCE(NULLIF(trim(p_cash_account_code), ''), '1100');
  v_date DATE := COALESCE(p_entry_date, CURRENT_DATE);
  v_currency public.currency_code;
  v_rate NUMERIC(18, 8);
  v_run_id UUID;
  v_run_doc TEXT;
  v_total NUMERIC(18, 4) := 0;
  v_accrual UUID;
  v_payment UUID;
  v_line RECORD;
  v_ids UUID[] := COALESCE(p_payroll_line_ids, ARRAY[]::UUID[]);
  v_funded INT := 0;
  v_skipped INT := 0;
  v_payslip_ids UUID[] := ARRAY[]::UUID[];
  v_ps UUID;
  v_cash_ok BOOLEAN;
  v_liability_ok BOOLEAN;
  v_expense_ok BOOLEAN;
BEGIN
  PERFORM public._payroll_begin_rpc();
  PERFORM public._require_hr_staff();

  IF cardinality(v_ids) IS NULL OR cardinality(v_ids) = 0 THEN
    RAISE EXCEPTION 'payroll line ids required';
  END IF;

  SELECT EXISTS (
    SELECT 1 FROM public.chart_of_accounts c
    WHERE c.code = v_cash AND c.account_type = 'asset'
  ) INTO v_cash_ok;
  IF NOT v_cash_ok THEN
    RAISE EXCEPTION 'cash account % must be an asset CoA code', v_cash;
  END IF;

  SELECT EXISTS (
    SELECT 1 FROM public.chart_of_accounts c WHERE c.code = '2150'
  ) INTO v_liability_ok;
  SELECT EXISTS (
    SELECT 1 FROM public.chart_of_accounts c WHERE c.code = '5200'
  ) INTO v_expense_ok;
  IF NOT v_liability_ok OR NOT v_expense_ok THEN
    RAISE EXCEPTION 'CoA 2150 Salaries Payable and 5200 Payroll Expense required';
  END IF;

  -- Lock candidate lines; must share one submitted run + currency
  SELECT l.currency, r.exchange_rate, r.id, r.document_number
  INTO v_currency, v_rate, v_run_id, v_run_doc
  FROM public.payroll_lines l
  JOIN public.payroll_runs r ON r.id = l.payroll_run_id
  WHERE l.id = ANY (v_ids)
  LIMIT 1;

  IF v_run_id IS NULL THEN
    RAISE EXCEPTION 'no matching payroll lines';
  END IF;

  IF EXISTS (
    SELECT 1
    FROM public.payroll_lines l
    JOIN public.payroll_runs r ON r.id = l.payroll_run_id
    WHERE l.id = ANY (v_ids)
      AND (
        r.status <> 'submitted'
        OR r.id IS DISTINCT FROM v_run_id
        OR l.currency IS DISTINCT FROM v_currency
      )
  ) THEN
    RAISE EXCEPTION 'all lines must be on one submitted run with the same currency';
  END IF;

  -- Sum net for unfunded lines only
  SELECT COALESCE(SUM(l.net_amount), 0)
  INTO v_total
  FROM public.payroll_lines l
  WHERE l.id = ANY (v_ids)
    AND l.payment_journal_id IS NULL
    AND l.net_amount > 0;

  SELECT COUNT(*) INTO v_skipped
  FROM public.payroll_lines l
  WHERE l.id = ANY (v_ids)
    AND l.payment_journal_id IS NOT NULL;

  IF v_total <= 0 THEN
    RETURN jsonb_build_object(
      'payroll_run_id', v_run_id,
      'funded_count', 0,
      'skipped_count', v_skipped,
      'accrual_journal_id', NULL,
      'payment_journal_id', NULL,
      'total_net', 0,
      'currency', v_currency,
      'cash_account_code', v_cash,
      'payslip_ids', '[]'::jsonb
    );
  END IF;

  -- 1) Accrue: Dr 5200 / Cr 2150
  v_accrual := public._post_journal_entry_payroll(
    v_date,
    format('Payroll accrue %s (net)', COALESCE(v_run_doc, v_run_id::text)),
    v_currency,
    v_rate,
    jsonb_build_array(
      jsonb_build_object(
        'account_code', '5200',
        'debit', v_total,
        'credit', 0,
        'currency', v_currency
      ),
      jsonb_build_object(
        'account_code', '2150',
        'debit', 0,
        'credit', v_total,
        'currency', v_currency
      )
    )
  );

  -- 2) Fund: Dr 2150 / Cr cash
  v_payment := public._post_journal_entry_payroll(
    v_date,
    format('Payroll fund %s from %s', COALESCE(v_run_doc, v_run_id::text), v_cash),
    v_currency,
    v_rate,
    jsonb_build_array(
      jsonb_build_object(
        'account_code', '2150',
        'debit', v_total,
        'credit', 0,
        'currency', v_currency
      ),
      jsonb_build_object(
        'account_code', v_cash,
        'debit', 0,
        'credit', v_total,
        'currency', v_currency
      )
    )
  );

  FOR v_line IN
    SELECT id FROM public.payroll_lines
    WHERE id = ANY (v_ids)
      AND payment_journal_id IS NULL
      AND net_amount > 0
    FOR UPDATE
  LOOP
    UPDATE public.payroll_lines
    SET
      accrual_journal_id = v_accrual,
      payment_journal_id = v_payment,
      funded_at = now(),
      funded_by = auth.uid(),
      cash_account_code = v_cash
    WHERE id = v_line.id;

    v_ps := public.export_payslip(v_line.id);
    v_payslip_ids := array_append(v_payslip_ids, v_ps);
    v_funded := v_funded + 1;
  END LOOP;

  PERFORM public.emit_domain_event(
    'payroll_run_ready',
    format('payroll_funded:%s:%s', v_run_id, v_payment),
    jsonb_build_object(
      'payroll_run_id', v_run_id,
      'accrual_journal_id', v_accrual,
      'payment_journal_id', v_payment,
      'total_net', v_total,
      'currency', v_currency,
      'cash_account_code', v_cash,
      'funded_count', v_funded
    ),
    auth.uid(),
    format(
      'GTR Auto: payroll %s funded %s %s from %s (gross − manual only)',
      COALESCE(v_run_doc, v_run_id::text),
      v_total,
      v_currency,
      v_cash
    )
  );

  RETURN jsonb_build_object(
    'payroll_run_id', v_run_id,
    'funded_count', v_funded,
    'skipped_count', v_skipped,
    'accrual_journal_id', v_accrual,
    'payment_journal_id', v_payment,
    'total_net', v_total,
    'currency', v_currency,
    'cash_account_code', v_cash,
    'payslip_ids', to_jsonb(v_payslip_ids)
  );
END;
$$;

CREATE OR REPLACE FUNCTION public.fund_payroll_run(
  p_payroll_run_id UUID,
  p_cash_account_code VARCHAR DEFAULT '1100',
  p_entry_date DATE DEFAULT NULL,
  p_employee_ids UUID[] DEFAULT NULL
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_ids UUID[];
BEGIN
  PERFORM public._require_hr_staff();

  SELECT COALESCE(array_agg(l.id), ARRAY[]::UUID[])
  INTO v_ids
  FROM public.payroll_lines l
  WHERE l.payroll_run_id = p_payroll_run_id
    AND (
      p_employee_ids IS NULL
      OR cardinality(p_employee_ids) = 0
      OR l.employee_id = ANY (p_employee_ids)
    );

  IF cardinality(v_ids) IS NULL OR cardinality(v_ids) = 0 THEN
    RAISE EXCEPTION 'no payroll lines for run %', p_payroll_run_id;
  END IF;

  RETURN public.fund_payroll_lines(v_ids, p_cash_account_code, p_entry_date);
END;
$$;

-- ---------------------------------------------------------------------------
-- Schedule upsert (admin|hr)
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.upsert_hr_payslip_schedule(
  p_pay_frequency public.hr_pay_frequency,
  p_cron_expr TEXT DEFAULT NULL,
  p_next_run_at TIMESTAMPTZ DEFAULT NULL,
  p_is_active BOOLEAN DEFAULT NULL,
  p_cash_account_code VARCHAR DEFAULT NULL,
  p_currency public.currency_code DEFAULT NULL,
  p_default_exchange_rate NUMERIC DEFAULT NULL,
  p_auto_fund BOOLEAN DEFAULT NULL
)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_id UUID;
  v_cash VARCHAR(10);
BEGIN
  IF NOT (
    auth.role() = 'service_role'
    OR public.has_staff_role(ARRAY['admin', 'hr']::public.staff_role[])
  ) THEN
    RAISE EXCEPTION 'hr or admin role required';
  END IF;

  v_cash := NULLIF(trim(COALESCE(p_cash_account_code, '')), '');
  IF v_cash IS NOT NULL THEN
    IF NOT EXISTS (
      SELECT 1 FROM public.chart_of_accounts c
      WHERE c.code = v_cash AND c.account_type = 'asset'
    ) THEN
      RAISE EXCEPTION 'cash account % must be an asset CoA code', v_cash;
    END IF;
  END IF;

  INSERT INTO public.hr_payslip_schedules (
    pay_frequency, cron_expr, next_run_at, is_active,
    cash_account_code, currency, default_exchange_rate, auto_fund,
    updated_at, updated_by
  )
  VALUES (
    p_pay_frequency,
    COALESCE(NULLIF(trim(p_cron_expr), ''), '0 6 25 * *'),
    p_next_run_at,
    COALESCE(p_is_active, true),
    COALESCE(v_cash, '1100'),
    COALESCE(p_currency, 'USD'),
    COALESCE(p_default_exchange_rate, 1),
    COALESCE(p_auto_fund, true),
    now(),
    auth.uid()
  )
  ON CONFLICT (pay_frequency) DO UPDATE
  SET
    cron_expr = COALESCE(NULLIF(trim(p_cron_expr), ''), public.hr_payslip_schedules.cron_expr),
    next_run_at = COALESCE(p_next_run_at, public.hr_payslip_schedules.next_run_at),
    is_active = COALESCE(p_is_active, public.hr_payslip_schedules.is_active),
    cash_account_code = COALESCE(v_cash, public.hr_payslip_schedules.cash_account_code),
    currency = COALESCE(p_currency, public.hr_payslip_schedules.currency),
    default_exchange_rate = COALESCE(
      p_default_exchange_rate,
      public.hr_payslip_schedules.default_exchange_rate
    ),
    auto_fund = COALESCE(p_auto_fund, public.hr_payslip_schedules.auto_fund),
    updated_at = now(),
    updated_by = auth.uid()
  RETURNING id INTO v_id;

  RETURN v_id;
END;
$$;

-- ---------------------------------------------------------------------------
-- Due schedules runner (service_role / HR)
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.run_due_payroll_schedules(
  p_as_of TIMESTAMPTZ DEFAULT now()
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_sched RECORD;
  v_period_start DATE;
  v_period_end DATE;
  v_run UUID;
  v_fund JSONB;
  v_results JSONB := '[]'::jsonb;
  v_as_of TIMESTAMPTZ := COALESCE(p_as_of, now());
BEGIN
  IF NOT (
    auth.role() = 'service_role'
    OR public.has_staff_role(ARRAY['admin', 'hr']::public.staff_role[])
  ) THEN
    RAISE EXCEPTION 'hr or admin role required';
  END IF;

  FOR v_sched IN
    SELECT *
    FROM public.hr_payslip_schedules
    WHERE is_active
      AND next_run_at IS NOT NULL
      AND next_run_at <= v_as_of
    ORDER BY next_run_at
    FOR UPDATE
  LOOP
    v_period_end := (v_as_of AT TIME ZONE 'UTC')::date;
    v_period_start := CASE v_sched.pay_frequency
      WHEN 'weekly' THEN v_period_end - 6
      WHEN 'fortnightly' THEN v_period_end - 13
      ELSE (date_trunc('month', v_period_end::timestamp)::date)
    END;

    BEGIN
      v_run := public.run_scheduled_payroll_for_frequency(
        v_sched.pay_frequency,
        v_period_start,
        v_period_end,
        v_sched.currency,
        v_sched.default_exchange_rate
      );
      PERFORM public.submit_payroll_run(v_run);

      IF v_sched.auto_fund THEN
        v_fund := public.fund_payroll_run(
          v_run,
          COALESCE(v_sched.cash_account_code, '1100'),
          v_period_end,
          NULL
        );
      ELSE
        v_fund := jsonb_build_object('funded_count', 0, 'auto_fund', false);
      END IF;

      UPDATE public.hr_payslip_schedules
      SET
        last_run_at = v_as_of,
        next_run_at = public._hr_payslip_schedule_advance_next(v_sched.pay_frequency, v_as_of),
        updated_at = now()
      WHERE id = v_sched.id;

      v_results := v_results || jsonb_build_array(
        jsonb_build_object(
          'pay_frequency', v_sched.pay_frequency,
          'payroll_run_id', v_run,
          'fund', v_fund,
          'ok', true
        )
      );
    EXCEPTION WHEN OTHERS THEN
      v_results := v_results || jsonb_build_array(
        jsonb_build_object(
          'pay_frequency', v_sched.pay_frequency,
          'ok', false,
          'error', SQLERRM
        )
      );
      -- Still advance to avoid tight failure loops
      UPDATE public.hr_payslip_schedules
      SET
        last_run_at = v_as_of,
        next_run_at = public._hr_payslip_schedule_advance_next(v_sched.pay_frequency, v_as_of),
        updated_at = now()
      WHERE id = v_sched.id;
    END;
  END LOOP;

  RETURN jsonb_build_object(
    'as_of', v_as_of,
    'results', v_results
  );
END;
$$;

-- Replace scheduled create/compute to also stamp next_run when called directly
CREATE OR REPLACE FUNCTION public.run_scheduled_payroll_for_frequency(
  p_frequency public.hr_pay_frequency,
  p_period_start DATE,
  p_period_end DATE,
  p_currency public.currency_code DEFAULT 'USD',
  p_exchange_rate NUMERIC DEFAULT 1
)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_run UUID;
  v_emps UUID[];
BEGIN
  IF NOT (
    auth.role() = 'service_role'
    OR public.has_staff_role(ARRAY['admin', 'hr']::public.staff_role[])
  ) THEN
    RAISE EXCEPTION 'hr or admin role required';
  END IF;

  SELECT COALESCE(array_agg(e.id), ARRAY[]::UUID[])
  INTO v_emps
  FROM public.employees e
  JOIN public.hr_roles r ON r.id = e.hr_role_id
  WHERE e.status = 'active'
    AND r.is_active
    AND r.pay_frequency = p_frequency;

  IF cardinality(v_emps) = 0 THEN
    RAISE EXCEPTION 'no active employees for pay_frequency %', p_frequency;
  END IF;

  v_run := public.create_payroll_run(
    p_period_start, p_period_end, p_currency, COALESCE(p_exchange_rate, 1),
    format('Scheduled %s payroll', p_frequency)
  );
  PERFORM public.compute_payroll_run(v_run, v_emps);

  UPDATE public.hr_payslip_schedules
  SET last_run_at = now(), updated_at = now()
  WHERE pay_frequency = p_frequency;

  RETURN v_run;
END;
$$;

-- Payslip payload helper for Edge PDF (gross − manual only)
CREATE OR REPLACE FUNCTION public.payslip_render_payload(p_payroll_line_id UUID)
RETURNS JSONB
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_line public.payroll_lines%ROWTYPE;
  v_run public.payroll_runs%ROWTYPE;
  v_emp public.employees%ROWTYPE;
  v_deds JSONB;
  v_is_hr BOOLEAN;
  v_self UUID;
BEGIN
  SELECT * INTO v_line FROM public.payroll_lines WHERE id = p_payroll_line_id;
  IF NOT FOUND THEN
    RAISE EXCEPTION 'payroll line not found';
  END IF;
  SELECT * INTO v_run FROM public.payroll_runs WHERE id = v_line.payroll_run_id;
  SELECT * INTO v_emp FROM public.employees WHERE id = v_line.employee_id;

  v_is_hr := (
    auth.role() = 'service_role'
    OR public.has_staff_role(ARRAY['admin', 'hr']::public.staff_role[])
  );
  v_self := public.current_employee_id();
  IF NOT v_is_hr AND (v_self IS NULL OR v_self IS DISTINCT FROM v_line.employee_id) THEN
    RAISE EXCEPTION 'admin/hr or own payslip only';
  END IF;

  SELECT COALESCE(
    jsonb_agg(
      jsonb_build_object('label', d.label, 'amount', d.amount)
      ORDER BY d.created_at
    ),
    '[]'::jsonb
  )
  INTO v_deds
  FROM public.payroll_deduction_lines d
  WHERE d.payroll_line_id = p_payroll_line_id;

  RETURN jsonb_build_object(
    'kind', 'payslip',
    'storeName', 'Nissan GTR Auto',
    'employeeName', v_emp.full_name,
    'employeeCode', v_emp.employee_code,
    'periodStart', v_run.period_start,
    'periodEnd', v_run.period_end,
    'currency', v_line.currency,
    'grossPay', v_line.gross_amount,
    'manualDeductions', v_deds,
    'netPay', v_line.net_amount,
    'storageBucket', 'payslips',
    'storagePath', format('%s/%s.pdf', v_run.id, v_line.employee_id),
    'payrollLineId', v_line.id,
    'payrollRunId', v_run.id,
    'funded', v_line.payment_journal_id IS NOT NULL
  );
END;
$$;

REVOKE ALL ON FUNCTION public._post_journal_entry_payroll(DATE, TEXT, public.currency_code, NUMERIC, JSONB) FROM PUBLIC;
REVOKE ALL ON FUNCTION public._hr_payslip_schedule_advance_next(public.hr_pay_frequency, TIMESTAMPTZ) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.fund_payroll_lines(UUID[], VARCHAR, DATE) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.fund_payroll_run(UUID, VARCHAR, DATE, UUID[]) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.upsert_hr_payslip_schedule(
  public.hr_pay_frequency, TEXT, TIMESTAMPTZ, BOOLEAN, VARCHAR, public.currency_code, NUMERIC, BOOLEAN
) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.run_due_payroll_schedules(TIMESTAMPTZ) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.payslip_render_payload(UUID) FROM PUBLIC;

GRANT EXECUTE ON FUNCTION public.fund_payroll_lines(UUID[], VARCHAR, DATE)
  TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.fund_payroll_run(UUID, VARCHAR, DATE, UUID[])
  TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.upsert_hr_payslip_schedule(
  public.hr_pay_frequency, TEXT, TIMESTAMPTZ, BOOLEAN, VARCHAR, public.currency_code, NUMERIC, BOOLEAN
) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.run_due_payroll_schedules(TIMESTAMPTZ)
  TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.payslip_render_payload(UUID)
  TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.run_scheduled_payroll_for_frequency(
  public.hr_pay_frequency, DATE, DATE, public.currency_code, NUMERIC
) TO authenticated, service_role;
