-- Phase 9: HR / attendance / gross-only payroll
-- HARD exclusions: NO PAYE, NSSA, POBS, APWCS, ZIMDEF, tax brackets, P4/P4A,
-- statutory remittance, ZIMRA. Net = gross − Σ manual deduction lines only.

CREATE TYPE public.employee_status AS ENUM ('active', 'inactive', 'terminated');
CREATE TYPE public.salary_pay_type AS ENUM ('hourly', 'salary');
CREATE TYPE public.attendance_event_type AS ENUM ('clock_in', 'clock_out');
CREATE TYPE public.payroll_run_status AS ENUM ('draft', 'submitted', 'cancelled');

-- ---------------------------------------------------------------------------
-- Employees
-- ---------------------------------------------------------------------------
CREATE TABLE public.employees (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID UNIQUE REFERENCES public.profiles (id) ON DELETE SET NULL,
  employee_code VARCHAR(32) NOT NULL UNIQUE,
  full_name TEXT NOT NULL,
  email TEXT,
  phone_e164 TEXT,
  hire_date DATE,
  status public.employee_status NOT NULL DEFAULT 'active',
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX employees_status_idx ON public.employees (status);
CREATE INDEX employees_user_idx ON public.employees (user_id) WHERE user_id IS NOT NULL;

-- ---------------------------------------------------------------------------
-- Salary structures / rates (explicit currency; no tax fields)
-- ---------------------------------------------------------------------------
CREATE TABLE public.salary_structures (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  employee_id UUID NOT NULL REFERENCES public.employees (id) ON DELETE CASCADE,
  pay_type public.salary_pay_type NOT NULL,
  rate NUMERIC(18, 4) NOT NULL CHECK (rate >= 0),
  currency public.currency_code NOT NULL,
  effective_from DATE NOT NULL,
  effective_to DATE,
  is_active BOOLEAN NOT NULL DEFAULT true,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT salary_structures_effective_range CHECK (
    effective_to IS NULL OR effective_to >= effective_from
  )
);

CREATE INDEX salary_structures_employee_idx ON public.salary_structures (employee_id);
CREATE INDEX salary_structures_active_idx
  ON public.salary_structures (employee_id, effective_from)
  WHERE is_active;

-- ---------------------------------------------------------------------------
-- Attendance (manual clock only — biometric deferred to Phase 12 bridges)
-- ---------------------------------------------------------------------------
CREATE TABLE public.attendance_events (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  employee_id UUID NOT NULL REFERENCES public.employees (id) ON DELETE CASCADE,
  event_type public.attendance_event_type NOT NULL,
  occurred_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  source TEXT NOT NULL DEFAULT 'manual' CHECK (source = 'manual'),
  notes TEXT,
  recorded_by UUID REFERENCES auth.users (id),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX attendance_events_employee_time_idx
  ON public.attendance_events (employee_id, occurred_at);

-- ---------------------------------------------------------------------------
-- Payroll runs / lines / manual deductions
-- ---------------------------------------------------------------------------
CREATE TABLE public.payroll_runs (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  document_number TEXT,
  status public.payroll_run_status NOT NULL DEFAULT 'draft',
  period_start DATE NOT NULL,
  period_end DATE NOT NULL,
  currency public.currency_code NOT NULL,
  exchange_rate NUMERIC(18, 8) NOT NULL DEFAULT 1 CHECK (exchange_rate > 0),
  total_gross NUMERIC(18, 4) NOT NULL DEFAULT 0 CHECK (total_gross >= 0),
  total_deductions NUMERIC(18, 4) NOT NULL DEFAULT 0 CHECK (total_deductions >= 0),
  total_net NUMERIC(18, 4) NOT NULL DEFAULT 0,
  notes TEXT,
  created_by UUID REFERENCES auth.users (id),
  submitted_at TIMESTAMPTZ,
  cancelled_at TIMESTAMPTZ,
  cancel_reason TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT payroll_runs_period CHECK (period_end >= period_start),
  CONSTRAINT payroll_runs_net_formula CHECK (total_net = total_gross - total_deductions)
);

CREATE TABLE public.payroll_lines (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  payroll_run_id UUID NOT NULL REFERENCES public.payroll_runs (id) ON DELETE CASCADE,
  employee_id UUID NOT NULL REFERENCES public.employees (id),
  line_no INT NOT NULL,
  hours_worked NUMERIC(18, 4) NOT NULL DEFAULT 0 CHECK (hours_worked >= 0),
  pay_type public.salary_pay_type NOT NULL,
  rate_applied NUMERIC(18, 4) NOT NULL CHECK (rate_applied >= 0),
  gross_amount NUMERIC(18, 4) NOT NULL CHECK (gross_amount >= 0),
  deductions_amount NUMERIC(18, 4) NOT NULL DEFAULT 0 CHECK (deductions_amount >= 0),
  net_amount NUMERIC(18, 4) NOT NULL,
  currency public.currency_code NOT NULL,
  exchange_rate NUMERIC(18, 8) NOT NULL DEFAULT 1 CHECK (exchange_rate > 0),
  salary_structure_id UUID REFERENCES public.salary_structures (id),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (payroll_run_id, line_no),
  UNIQUE (payroll_run_id, employee_id),
  CONSTRAINT payroll_lines_net_formula CHECK (net_amount = gross_amount - deductions_amount)
);

CREATE INDEX payroll_lines_run_idx ON public.payroll_lines (payroll_run_id);
CREATE INDEX payroll_lines_employee_idx ON public.payroll_lines (employee_id);

-- Manual/custom deduction lines ONLY — no tax_code / statutory_type columns ever.
CREATE TABLE public.payroll_deduction_lines (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  payroll_line_id UUID NOT NULL REFERENCES public.payroll_lines (id) ON DELETE CASCADE,
  label TEXT NOT NULL CHECK (length(trim(label)) > 0),
  amount NUMERIC(18, 4) NOT NULL CHECK (amount > 0),
  currency public.currency_code NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX payroll_deduction_lines_line_idx
  ON public.payroll_deduction_lines (payroll_line_id);

-- Payslip storage metadata (PDF bytes uploaded to private bucket by clients)
CREATE TABLE public.payslips (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  payroll_line_id UUID NOT NULL UNIQUE REFERENCES public.payroll_lines (id) ON DELETE CASCADE,
  payroll_run_id UUID NOT NULL REFERENCES public.payroll_runs (id) ON DELETE CASCADE,
  employee_id UUID NOT NULL REFERENCES public.employees (id),
  storage_bucket TEXT NOT NULL DEFAULT 'payslips',
  storage_path TEXT NOT NULL,
  mime_type TEXT NOT NULL DEFAULT 'application/pdf',
  generated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  generated_by UUID REFERENCES auth.users (id),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX payslips_employee_idx ON public.payslips (employee_id);
CREATE INDEX payslips_run_idx ON public.payslips (payroll_run_id);

INSERT INTO public.naming_series (prefix, description, pad_length) VALUES
  ('PR-', 'Payroll run', 5),
  ('EMP-', 'Employee code', 5)
ON CONFLICT (prefix) DO NOTHING;

-- ---------------------------------------------------------------------------
-- Auth helpers
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.current_employee_id()
RETURNS UUID
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
  SELECT e.id
  FROM public.employees e
  WHERE e.user_id = auth.uid()
  LIMIT 1;
$$;

CREATE OR REPLACE FUNCTION public._require_hr_staff()
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
  IF NOT (
    auth.role() = 'service_role'
    OR public.has_staff_role(ARRAY['admin', 'hr']::public.staff_role[])
  ) THEN
    RAISE EXCEPTION 'admin or hr role required';
  END IF;
END;
$$;

-- ---------------------------------------------------------------------------
-- Mutation guards (GUC app.payroll_rpc) — mirror Phase 4b/5b/8
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public._payroll_rpc_active()
RETURNS boolean
LANGUAGE sql
STABLE
AS $$
  SELECT COALESCE(current_setting('app.payroll_rpc', true), '') = '1';
$$;

CREATE OR REPLACE FUNCTION public._payroll_begin_rpc()
RETURNS void
LANGUAGE sql
AS $$
  SELECT set_config('app.payroll_rpc', '1', true);
$$;

CREATE OR REPLACE FUNCTION public.guard_payroll_run_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
  IF public._payroll_rpc_active() THEN
    IF TG_OP = 'DELETE' THEN
      RETURN OLD;
    END IF;
    RETURN NEW;
  END IF;

  IF TG_OP = 'INSERT' THEN
    IF NEW.status IS DISTINCT FROM 'draft' THEN
      RAISE EXCEPTION 'payroll_runs: new rows must be draft; use payroll RPCs';
    END IF;
    RAISE EXCEPTION 'payroll_runs: use payroll RPCs for insert';
  ELSIF TG_OP = 'DELETE' THEN
    RAISE EXCEPTION 'payroll_runs: direct delete not allowed';
  ELSIF TG_OP = 'UPDATE' THEN
    IF OLD.status IN ('submitted', 'cancelled') THEN
      RAISE EXCEPTION 'payroll_runs: submitted/cancelled runs are immutable; use cancel_payroll_run';
    END IF;
    RAISE EXCEPTION 'payroll_runs: use payroll RPCs for updates';
  END IF;

  RETURN NULL;
END;
$$;

CREATE OR REPLACE FUNCTION public.guard_payroll_line_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
  v_parent UUID;
  v_status public.payroll_run_status;
BEGIN
  IF public._payroll_rpc_active() THEN
    IF TG_OP = 'DELETE' THEN
      RETURN OLD;
    END IF;
    RETURN NEW;
  END IF;

  v_parent := COALESCE(NEW.payroll_run_id, OLD.payroll_run_id);
  SELECT status INTO v_status FROM public.payroll_runs WHERE id = v_parent;
  IF v_status IS NULL THEN
    RAISE EXCEPTION 'payroll_lines: parent not found';
  END IF;
  IF v_status <> 'draft' THEN
    RAISE EXCEPTION 'payroll_lines: parent must be draft (status=%)', v_status;
  END IF;
  RAISE EXCEPTION 'payroll_lines: use payroll RPCs';
END;
$$;

CREATE OR REPLACE FUNCTION public.guard_payroll_deduction_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
  v_line UUID;
  v_status public.payroll_run_status;
BEGIN
  IF public._payroll_rpc_active() THEN
    IF TG_OP = 'DELETE' THEN
      RETURN OLD;
    END IF;
    RETURN NEW;
  END IF;

  v_line := COALESCE(NEW.payroll_line_id, OLD.payroll_line_id);
  SELECT r.status INTO v_status
  FROM public.payroll_lines l
  JOIN public.payroll_runs r ON r.id = l.payroll_run_id
  WHERE l.id = v_line;
  IF v_status IS NULL THEN
    RAISE EXCEPTION 'payroll_deduction_lines: parent line not found';
  END IF;
  IF v_status <> 'draft' THEN
    RAISE EXCEPTION 'payroll_deduction_lines: parent must be draft (status=%)', v_status;
  END IF;
  RAISE EXCEPTION 'payroll_deduction_lines: use payroll RPCs';
END;
$$;

CREATE OR REPLACE FUNCTION public.guard_payslip_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
  IF public._payroll_rpc_active() THEN
    IF TG_OP = 'DELETE' THEN
      RETURN OLD;
    END IF;
    RETURN NEW;
  END IF;

  IF TG_OP = 'DELETE' THEN
    RAISE EXCEPTION 'payslips: direct delete not allowed';
  END IF;
  RAISE EXCEPTION 'payslips: use export_payslip RPC';
END;
$$;

CREATE TRIGGER payroll_runs_mutation_guard
  BEFORE INSERT OR UPDATE OR DELETE ON public.payroll_runs
  FOR EACH ROW
  EXECUTE PROCEDURE public.guard_payroll_run_mutation();

CREATE TRIGGER payroll_lines_mutation_guard
  BEFORE INSERT OR UPDATE OR DELETE ON public.payroll_lines
  FOR EACH ROW
  EXECUTE PROCEDURE public.guard_payroll_line_mutation();

CREATE TRIGGER payroll_deduction_lines_mutation_guard
  BEFORE INSERT OR UPDATE OR DELETE ON public.payroll_deduction_lines
  FOR EACH ROW
  EXECUTE PROCEDURE public.guard_payroll_deduction_mutation();

CREATE TRIGGER payslips_mutation_guard
  BEFORE INSERT OR UPDATE OR DELETE ON public.payslips
  FOR EACH ROW
  EXECUTE PROCEDURE public.guard_payslip_mutation();

REVOKE ALL ON FUNCTION public._payroll_rpc_active() FROM PUBLIC;
REVOKE ALL ON FUNCTION public._payroll_begin_rpc() FROM PUBLIC;

-- ---------------------------------------------------------------------------
-- Attendance hour rollup helper
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.attendance_hours_in_period(
  p_employee_id UUID,
  p_period_start TIMESTAMPTZ,
  p_period_end TIMESTAMPTZ
)
RETURNS NUMERIC
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_hours NUMERIC := 0;
  v_in TIMESTAMPTZ;
  r RECORD;
BEGIN
  IF p_period_end < p_period_start THEN
    RAISE EXCEPTION 'period_end must be >= period_start';
  END IF;

  FOR r IN
    SELECT event_type, occurred_at
    FROM public.attendance_events
    WHERE employee_id = p_employee_id
      AND occurred_at >= p_period_start
      AND occurred_at < p_period_end
    ORDER BY occurred_at ASC, created_at ASC
  LOOP
    IF r.event_type = 'clock_in' THEN
      v_in := r.occurred_at;
    ELSIF r.event_type = 'clock_out' AND v_in IS NOT NULL THEN
      v_hours := v_hours + EXTRACT(EPOCH FROM (r.occurred_at - v_in)) / 3600.0;
      v_in := NULL;
    END IF;
  END LOOP;

  RETURN ROUND(v_hours, 4);
END;
$$;

-- ---------------------------------------------------------------------------
-- Employee / salary RPCs
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.create_employee(
  p_employee_code TEXT DEFAULT NULL,
  p_full_name TEXT DEFAULT NULL,
  p_user_id UUID DEFAULT NULL,
  p_email TEXT DEFAULT NULL,
  p_phone_e164 TEXT DEFAULT NULL,
  p_hire_date DATE DEFAULT NULL
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
  PERFORM public._require_hr_staff();

  IF p_full_name IS NULL OR length(trim(p_full_name)) = 0 THEN
    RAISE EXCEPTION 'full_name required';
  END IF;

  v_code := COALESCE(NULLIF(trim(p_employee_code), ''), public.next_series_value('EMP-'));

  IF p_user_id IS NOT NULL AND NOT EXISTS (
    SELECT 1 FROM public.profiles WHERE id = p_user_id
  ) THEN
    RAISE EXCEPTION 'profile not found: %', p_user_id;
  END IF;

  INSERT INTO public.employees (
    employee_code, full_name, user_id, email, phone_e164, hire_date
  )
  VALUES (
    v_code, trim(p_full_name), p_user_id, p_email, p_phone_e164, p_hire_date
  )
  RETURNING id INTO v_id;

  RETURN v_id;
END;
$$;

CREATE OR REPLACE FUNCTION public.upsert_salary_structure(
  p_employee_id UUID,
  p_pay_type public.salary_pay_type,
  p_rate NUMERIC,
  p_currency public.currency_code,
  p_effective_from DATE,
  p_effective_to DATE DEFAULT NULL
)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_id UUID;
BEGIN
  PERFORM public._require_hr_staff();

  IF NOT EXISTS (SELECT 1 FROM public.employees WHERE id = p_employee_id) THEN
    RAISE EXCEPTION 'employee not found: %', p_employee_id;
  END IF;
  IF p_rate IS NULL OR p_rate < 0 THEN
    RAISE EXCEPTION 'rate must be >= 0';
  END IF;
  IF p_effective_from IS NULL THEN
    RAISE EXCEPTION 'effective_from required';
  END IF;

  -- Close overlapping active structures ending after new start
  UPDATE public.salary_structures
  SET is_active = false,
      effective_to = COALESCE(effective_to, p_effective_from - 1)
  WHERE employee_id = p_employee_id
    AND is_active
    AND effective_from <= COALESCE(p_effective_to, '9999-12-31'::date)
    AND COALESCE(effective_to, '9999-12-31'::date) >= p_effective_from;

  INSERT INTO public.salary_structures (
    employee_id, pay_type, rate, currency, effective_from, effective_to, is_active
  )
  VALUES (
    p_employee_id, p_pay_type, p_rate, p_currency, p_effective_from, p_effective_to, true
  )
  RETURNING id INTO v_id;

  RETURN v_id;
END;
$$;

CREATE OR REPLACE FUNCTION public.clock_attendance(
  p_employee_id UUID,
  p_event_type public.attendance_event_type,
  p_occurred_at TIMESTAMPTZ DEFAULT NULL,
  p_notes TEXT DEFAULT NULL
)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_id UUID;
  v_emp public.employees%ROWTYPE;
  v_when TIMESTAMPTZ := COALESCE(p_occurred_at, now());
  v_is_hr BOOLEAN;
  v_self UUID;
BEGIN
  v_is_hr := (
    auth.role() = 'service_role'
    OR public.has_staff_role(ARRAY['admin', 'hr']::public.staff_role[])
  );
  v_self := public.current_employee_id();

  IF NOT v_is_hr AND (v_self IS NULL OR v_self IS DISTINCT FROM p_employee_id) THEN
    RAISE EXCEPTION 'admin/hr role or own employee record required to clock attendance';
  END IF;

  SELECT * INTO v_emp FROM public.employees WHERE id = p_employee_id;
  IF NOT FOUND THEN
    RAISE EXCEPTION 'employee not found: %', p_employee_id;
  END IF;
  IF v_emp.status <> 'active' THEN
    RAISE EXCEPTION 'employee % is not active', p_employee_id;
  END IF;

  INSERT INTO public.attendance_events (
    employee_id, event_type, occurred_at, source, notes, recorded_by
  )
  VALUES (
    p_employee_id, p_event_type, v_when, 'manual', p_notes, auth.uid()
  )
  RETURNING id INTO v_id;

  RETURN v_id;
END;
$$;

CREATE OR REPLACE FUNCTION public.emit_staff_no_show(
  p_employee_id UUID,
  p_window_label TEXT DEFAULT NULL
)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_emp public.employees%ROWTYPE;
  v_event UUID;
  v_dedupe TEXT;
BEGIN
  PERFORM public._require_hr_staff();

  SELECT * INTO v_emp FROM public.employees WHERE id = p_employee_id;
  IF NOT FOUND THEN
    RAISE EXCEPTION 'employee not found: %', p_employee_id;
  END IF;

  v_dedupe := format(
    'staff_no_show:%s:%s',
    p_employee_id,
    COALESCE(p_window_label, to_char(CURRENT_DATE, 'YYYY-MM-DD'))
  );

  v_event := public.emit_domain_event(
    'staff_no_show',
    v_dedupe,
    jsonb_build_object(
      'employee_id', p_employee_id,
      'employee_code', v_emp.employee_code,
      'full_name', v_emp.full_name,
      'window', p_window_label
    ),
    auth.uid(),
    format('GTR Auto: staff no-show %s (%s)', v_emp.full_name, v_emp.employee_code)
  );

  RETURN v_event;
END;
$$;

-- ---------------------------------------------------------------------------
-- Payroll compute / submit / cancel
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public._refresh_payroll_run_totals(p_run_id UUID)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
  UPDATE public.payroll_runs r
  SET
    total_gross = COALESCE((
      SELECT SUM(l.gross_amount) FROM public.payroll_lines l WHERE l.payroll_run_id = p_run_id
    ), 0),
    total_deductions = COALESCE((
      SELECT SUM(l.deductions_amount) FROM public.payroll_lines l WHERE l.payroll_run_id = p_run_id
    ), 0),
    total_net = COALESCE((
      SELECT SUM(l.net_amount) FROM public.payroll_lines l WHERE l.payroll_run_id = p_run_id
    ), 0),
    updated_at = now()
  WHERE r.id = p_run_id;
END;
$$;

CREATE OR REPLACE FUNCTION public.create_payroll_run(
  p_period_start DATE,
  p_period_end DATE,
  p_currency public.currency_code,
  p_exchange_rate NUMERIC DEFAULT 1,
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
  PERFORM public._payroll_begin_rpc();
  PERFORM public._require_hr_staff();

  IF p_period_start IS NULL OR p_period_end IS NULL THEN
    RAISE EXCEPTION 'period_start and period_end required';
  END IF;
  IF p_period_end < p_period_start THEN
    RAISE EXCEPTION 'period_end must be >= period_start';
  END IF;
  IF p_exchange_rate IS NULL OR p_exchange_rate <= 0 THEN
    RAISE EXCEPTION 'exchange_rate must be > 0';
  END IF;

  INSERT INTO public.payroll_runs (
    document_number, period_start, period_end, currency, exchange_rate, notes, created_by
  )
  VALUES (
    public.next_series_value('PR-'),
    p_period_start,
    p_period_end,
    p_currency,
    p_exchange_rate,
    p_notes,
    auth.uid()
  )
  RETURNING id INTO v_id;

  RETURN v_id;
END;
$$;

CREATE OR REPLACE FUNCTION public.compute_payroll_run(
  p_payroll_run_id UUID,
  p_employee_ids UUID[] DEFAULT NULL
)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_run public.payroll_runs%ROWTYPE;
  v_emp RECORD;
  v_struct public.salary_structures%ROWTYPE;
  v_hours NUMERIC;
  v_gross NUMERIC;
  v_line_no INT := 0;
  v_start TIMESTAMPTZ;
  v_end TIMESTAMPTZ;
BEGIN
  PERFORM public._payroll_begin_rpc();
  PERFORM public._require_hr_staff();

  SELECT * INTO v_run FROM public.payroll_runs WHERE id = p_payroll_run_id FOR UPDATE;
  IF NOT FOUND THEN
    RAISE EXCEPTION 'payroll run not found: %', p_payroll_run_id;
  END IF;
  IF v_run.status <> 'draft' THEN
    RAISE EXCEPTION 'only draft payroll runs can be computed';
  END IF;

  -- Clear prior draft lines (cascade deductions)
  DELETE FROM public.payroll_lines WHERE payroll_run_id = p_payroll_run_id;

  v_start := v_run.period_start::timestamptz;
  v_end := (v_run.period_end + 1)::timestamptz;

  FOR v_emp IN
    SELECT e.*
    FROM public.employees e
    WHERE e.status = 'active'
      AND (p_employee_ids IS NULL OR e.id = ANY (p_employee_ids))
    ORDER BY e.employee_code
  LOOP
    SELECT * INTO v_struct
    FROM public.salary_structures s
    WHERE s.employee_id = v_emp.id
      AND s.is_active
      AND s.currency = v_run.currency
      AND s.effective_from <= v_run.period_end
      AND COALESCE(s.effective_to, '9999-12-31'::date) >= v_run.period_start
    ORDER BY s.effective_from DESC
    LIMIT 1;

    IF NOT FOUND THEN
      CONTINUE;
    END IF;

    v_hours := public.attendance_hours_in_period(v_emp.id, v_start, v_end);

    IF v_struct.pay_type = 'hourly' THEN
      v_gross := ROUND(v_hours * v_struct.rate, 4);
    ELSE
      -- Fixed salary for the period (hours informational only)
      v_gross := ROUND(v_struct.rate, 4);
    END IF;

    IF v_gross <= 0 AND v_hours <= 0 THEN
      CONTINUE;
    END IF;

    v_line_no := v_line_no + 1;
    INSERT INTO public.payroll_lines (
      payroll_run_id, employee_id, line_no, hours_worked, pay_type,
      rate_applied, gross_amount, deductions_amount, net_amount,
      currency, exchange_rate, salary_structure_id
    )
    VALUES (
      p_payroll_run_id, v_emp.id, v_line_no, v_hours, v_struct.pay_type,
      v_struct.rate, v_gross, 0, v_gross,
      v_run.currency, v_run.exchange_rate, v_struct.id
    );
  END LOOP;

  PERFORM public._refresh_payroll_run_totals(p_payroll_run_id);
  RETURN p_payroll_run_id;
END;
$$;

CREATE OR REPLACE FUNCTION public.add_payroll_deduction(
  p_payroll_line_id UUID,
  p_label TEXT,
  p_amount NUMERIC
)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_line public.payroll_lines%ROWTYPE;
  v_run public.payroll_runs%ROWTYPE;
  v_id UUID;
  v_ded NUMERIC;
BEGIN
  PERFORM public._payroll_begin_rpc();
  PERFORM public._require_hr_staff();

  IF p_label IS NULL OR length(trim(p_label)) = 0 THEN
    RAISE EXCEPTION 'deduction label required';
  END IF;
  -- Reject labels that imply statutory tax calc
  IF lower(p_label) ~ '(paye|nssa|pobs|apwcs|zimdef|statutory|tax.?bracket|p4a?)' THEN
    RAISE EXCEPTION 'statutory/tax deduction labels are not allowed; use manual labels only';
  END IF;
  IF p_amount IS NULL OR p_amount <= 0 THEN
    RAISE EXCEPTION 'deduction amount must be > 0';
  END IF;

  SELECT * INTO v_line FROM public.payroll_lines WHERE id = p_payroll_line_id FOR UPDATE;
  IF NOT FOUND THEN
    RAISE EXCEPTION 'payroll line not found: %', p_payroll_line_id;
  END IF;

  SELECT * INTO v_run FROM public.payroll_runs WHERE id = v_line.payroll_run_id FOR UPDATE;
  IF v_run.status <> 'draft' THEN
    RAISE EXCEPTION 'deductions only allowed on draft payroll runs';
  END IF;

  INSERT INTO public.payroll_deduction_lines (
    payroll_line_id, label, amount, currency
  )
  VALUES (
    p_payroll_line_id, trim(p_label), p_amount, v_line.currency
  )
  RETURNING id INTO v_id;

  SELECT COALESCE(SUM(amount), 0) INTO v_ded
  FROM public.payroll_deduction_lines
  WHERE payroll_line_id = p_payroll_line_id;

  IF v_ded > v_line.gross_amount THEN
    RAISE EXCEPTION 'deductions (%) exceed gross (%)', v_ded, v_line.gross_amount;
  END IF;

  UPDATE public.payroll_lines
  SET deductions_amount = v_ded,
      net_amount = gross_amount - v_ded
  WHERE id = p_payroll_line_id;

  PERFORM public._refresh_payroll_run_totals(v_line.payroll_run_id);
  RETURN v_id;
END;
$$;

CREATE OR REPLACE FUNCTION public.submit_payroll_run(p_payroll_run_id UUID)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_run public.payroll_runs%ROWTYPE;
  v_line_count INT;
BEGIN
  PERFORM public._payroll_begin_rpc();
  PERFORM public._require_hr_staff();

  SELECT * INTO v_run FROM public.payroll_runs WHERE id = p_payroll_run_id FOR UPDATE;
  IF NOT FOUND THEN
    RAISE EXCEPTION 'payroll run not found: %', p_payroll_run_id;
  END IF;
  IF v_run.status <> 'draft' THEN
    RAISE EXCEPTION 'only draft payroll runs can be submitted';
  END IF;

  SELECT COUNT(*) INTO v_line_count
  FROM public.payroll_lines WHERE payroll_run_id = p_payroll_run_id;
  IF v_line_count = 0 THEN
    RAISE EXCEPTION 'payroll run has no lines; compute first';
  END IF;

  -- Enforce tax-free formula at submit
  IF v_run.total_net IS DISTINCT FROM (v_run.total_gross - v_run.total_deductions) THEN
    RAISE EXCEPTION 'payroll net must equal gross − manual deductions';
  END IF;

  UPDATE public.payroll_runs
  SET status = 'submitted', submitted_at = now(), updated_at = now()
  WHERE id = p_payroll_run_id;

  PERFORM public.emit_domain_event(
    'payroll_run_ready',
    format('payroll_run_ready:%s', p_payroll_run_id),
    jsonb_build_object(
      'payroll_run_id', p_payroll_run_id,
      'document_number', v_run.document_number,
      'period_start', v_run.period_start,
      'period_end', v_run.period_end,
      'currency', v_run.currency,
      'exchange_rate', v_run.exchange_rate,
      'total_gross', v_run.total_gross,
      'total_deductions', v_run.total_deductions,
      'total_net', v_run.total_net
    ),
    auth.uid(),
    format(
      'GTR Auto: payroll %s ready (gross %s %s − manual %s = net %s)',
      v_run.document_number,
      v_run.total_gross,
      v_run.currency,
      v_run.total_deductions,
      v_run.total_net
    )
  );

  RETURN p_payroll_run_id;
END;
$$;

CREATE OR REPLACE FUNCTION public.cancel_payroll_run(
  p_payroll_run_id UUID,
  p_reason TEXT DEFAULT NULL
)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_run public.payroll_runs%ROWTYPE;
BEGIN
  PERFORM public._payroll_begin_rpc();
  PERFORM public._require_hr_staff();

  SELECT * INTO v_run FROM public.payroll_runs WHERE id = p_payroll_run_id FOR UPDATE;
  IF NOT FOUND THEN
    RAISE EXCEPTION 'payroll run not found: %', p_payroll_run_id;
  END IF;
  IF v_run.status = 'cancelled' THEN
    RAISE EXCEPTION 'payroll run already cancelled';
  END IF;

  -- Void/reverse pattern: flip status only; never edit submitted money lines
  UPDATE public.payroll_runs
  SET
    status = 'cancelled',
    cancelled_at = now(),
    cancel_reason = p_reason,
    updated_at = now()
  WHERE id = p_payroll_run_id;

  RETURN p_payroll_run_id;
END;
$$;

CREATE OR REPLACE FUNCTION public.export_payslip(p_payroll_line_id UUID)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_line public.payroll_lines%ROWTYPE;
  v_run public.payroll_runs%ROWTYPE;
  v_id UUID;
  v_path TEXT;
  v_is_hr BOOLEAN;
  v_self UUID;
BEGIN
  PERFORM public._payroll_begin_rpc();

  SELECT * INTO v_line FROM public.payroll_lines WHERE id = p_payroll_line_id;
  IF NOT FOUND THEN
    RAISE EXCEPTION 'payroll line not found: %', p_payroll_line_id;
  END IF;

  SELECT * INTO v_run FROM public.payroll_runs WHERE id = v_line.payroll_run_id;
  IF v_run.status NOT IN ('submitted', 'cancelled') THEN
    RAISE EXCEPTION 'payslip export requires submitted (or cancelled) payroll run';
  END IF;

  v_is_hr := (
    auth.role() = 'service_role'
    OR public.has_staff_role(ARRAY['admin', 'hr']::public.staff_role[])
  );
  v_self := public.current_employee_id();
  IF NOT v_is_hr AND (v_self IS NULL OR v_self IS DISTINCT FROM v_line.employee_id) THEN
    RAISE EXCEPTION 'admin/hr or own payslip only';
  END IF;

  SELECT id INTO v_id FROM public.payslips WHERE payroll_line_id = p_payroll_line_id;
  IF v_id IS NOT NULL THEN
    RETURN v_id;
  END IF;

  v_path := format('%s/%s.pdf', v_run.id, v_line.employee_id);

  INSERT INTO public.payslips (
    payroll_line_id, payroll_run_id, employee_id,
    storage_bucket, storage_path, mime_type, generated_by
  )
  VALUES (
    p_payroll_line_id, v_run.id, v_line.employee_id,
    'payslips', v_path, 'application/pdf', auth.uid()
  )
  RETURNING id INTO v_id;

  RETURN v_id;
END;
$$;

-- ---------------------------------------------------------------------------
-- Storage: private payslips bucket
-- ---------------------------------------------------------------------------
INSERT INTO storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
VALUES (
  'payslips',
  'payslips',
  false,
  5242880,
  ARRAY['application/pdf']
)
ON CONFLICT (id) DO NOTHING;

CREATE POLICY payslips_storage_select
  ON storage.objects FOR SELECT
  TO authenticated
  USING (
    bucket_id = 'payslips'
    AND (
      public.has_staff_role(ARRAY['admin', 'hr']::public.staff_role[])
      OR EXISTS (
        SELECT 1
        FROM public.payslips ps
        JOIN public.employees e ON e.id = ps.employee_id
        WHERE ps.storage_path = name
          AND e.user_id = auth.uid()
      )
    )
  );

CREATE POLICY payslips_storage_insert
  ON storage.objects FOR INSERT
  TO authenticated
  WITH CHECK (
    bucket_id = 'payslips'
    AND public.has_staff_role(ARRAY['admin', 'hr']::public.staff_role[])
  );

CREATE POLICY payslips_storage_update
  ON storage.objects FOR UPDATE
  TO authenticated
  USING (
    bucket_id = 'payslips'
    AND public.has_staff_role(ARRAY['admin', 'hr']::public.staff_role[])
  )
  WITH CHECK (
    bucket_id = 'payslips'
    AND public.has_staff_role(ARRAY['admin', 'hr']::public.staff_role[])
  );

CREATE POLICY payslips_storage_delete
  ON storage.objects FOR DELETE
  TO authenticated
  USING (
    bucket_id = 'payslips'
    AND public.has_staff_role(ARRAY['admin']::public.staff_role[])
  );

-- ---------------------------------------------------------------------------
-- RLS
-- ---------------------------------------------------------------------------
ALTER TABLE public.employees ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.salary_structures ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.attendance_events ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.payroll_runs ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.payroll_lines ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.payroll_deduction_lines ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.payslips ENABLE ROW LEVEL SECURITY;

CREATE POLICY employees_select_hr_or_self
  ON public.employees FOR SELECT TO authenticated
  USING (
    public.has_staff_role(ARRAY['admin', 'hr']::public.staff_role[])
    OR user_id = auth.uid()
  );

CREATE POLICY employees_insert_hr
  ON public.employees FOR INSERT TO authenticated
  WITH CHECK (public.has_staff_role(ARRAY['admin', 'hr']::public.staff_role[]));

CREATE POLICY employees_update_hr
  ON public.employees FOR UPDATE TO authenticated
  USING (public.has_staff_role(ARRAY['admin', 'hr']::public.staff_role[]))
  WITH CHECK (public.has_staff_role(ARRAY['admin', 'hr']::public.staff_role[]));

CREATE POLICY employees_delete_admin
  ON public.employees FOR DELETE TO authenticated
  USING (public.has_staff_role(ARRAY['admin']::public.staff_role[]));

CREATE POLICY salary_structures_select_hr_or_self
  ON public.salary_structures FOR SELECT TO authenticated
  USING (
    public.has_staff_role(ARRAY['admin', 'hr']::public.staff_role[])
    OR EXISTS (
      SELECT 1 FROM public.employees e
      WHERE e.id = employee_id AND e.user_id = auth.uid()
    )
  );

CREATE POLICY salary_structures_write_hr
  ON public.salary_structures FOR ALL TO authenticated
  USING (public.has_staff_role(ARRAY['admin', 'hr']::public.staff_role[]))
  WITH CHECK (public.has_staff_role(ARRAY['admin', 'hr']::public.staff_role[]));

CREATE POLICY attendance_events_select_hr_or_self
  ON public.attendance_events FOR SELECT TO authenticated
  USING (
    public.has_staff_role(ARRAY['admin', 'hr']::public.staff_role[])
    OR EXISTS (
      SELECT 1 FROM public.employees e
      WHERE e.id = employee_id AND e.user_id = auth.uid()
    )
  );

CREATE POLICY attendance_events_insert_hr_or_self
  ON public.attendance_events FOR INSERT TO authenticated
  WITH CHECK (
    public.has_staff_role(ARRAY['admin', 'hr']::public.staff_role[])
    OR EXISTS (
      SELECT 1 FROM public.employees e
      WHERE e.id = employee_id AND e.user_id = auth.uid()
    )
  );

CREATE POLICY attendance_events_update_hr
  ON public.attendance_events FOR UPDATE TO authenticated
  USING (public.has_staff_role(ARRAY['admin', 'hr']::public.staff_role[]))
  WITH CHECK (public.has_staff_role(ARRAY['admin', 'hr']::public.staff_role[]));

CREATE POLICY attendance_events_delete_hr
  ON public.attendance_events FOR DELETE TO authenticated
  USING (public.has_staff_role(ARRAY['admin', 'hr']::public.staff_role[]));

CREATE POLICY payroll_runs_select_hr
  ON public.payroll_runs FOR SELECT TO authenticated
  USING (
    public.has_staff_role(ARRAY['admin', 'hr']::public.staff_role[])
    OR EXISTS (
      SELECT 1
      FROM public.payroll_lines l
      JOIN public.employees e ON e.id = l.employee_id
      WHERE l.payroll_run_id = id AND e.user_id = auth.uid()
    )
  );

CREATE POLICY payroll_runs_write_hr
  ON public.payroll_runs FOR ALL TO authenticated
  USING (public.has_staff_role(ARRAY['admin', 'hr']::public.staff_role[]))
  WITH CHECK (public.has_staff_role(ARRAY['admin', 'hr']::public.staff_role[]));

CREATE POLICY payroll_lines_select_hr_or_self
  ON public.payroll_lines FOR SELECT TO authenticated
  USING (
    public.has_staff_role(ARRAY['admin', 'hr']::public.staff_role[])
    OR EXISTS (
      SELECT 1 FROM public.employees e
      WHERE e.id = employee_id AND e.user_id = auth.uid()
    )
  );

CREATE POLICY payroll_lines_write_hr
  ON public.payroll_lines FOR ALL TO authenticated
  USING (public.has_staff_role(ARRAY['admin', 'hr']::public.staff_role[]))
  WITH CHECK (public.has_staff_role(ARRAY['admin', 'hr']::public.staff_role[]));

CREATE POLICY payroll_deduction_lines_select_hr_or_self
  ON public.payroll_deduction_lines FOR SELECT TO authenticated
  USING (
    public.has_staff_role(ARRAY['admin', 'hr']::public.staff_role[])
    OR EXISTS (
      SELECT 1
      FROM public.payroll_lines l
      JOIN public.employees e ON e.id = l.employee_id
      WHERE l.id = payroll_line_id AND e.user_id = auth.uid()
    )
  );

CREATE POLICY payroll_deduction_lines_write_hr
  ON public.payroll_deduction_lines FOR ALL TO authenticated
  USING (public.has_staff_role(ARRAY['admin', 'hr']::public.staff_role[]))
  WITH CHECK (public.has_staff_role(ARRAY['admin', 'hr']::public.staff_role[]));

CREATE POLICY payslips_select_hr_or_self
  ON public.payslips FOR SELECT TO authenticated
  USING (
    public.has_staff_role(ARRAY['admin', 'hr']::public.staff_role[])
    OR EXISTS (
      SELECT 1 FROM public.employees e
      WHERE e.id = employee_id AND e.user_id = auth.uid()
    )
  );

CREATE POLICY payslips_write_hr
  ON public.payslips FOR ALL TO authenticated
  USING (public.has_staff_role(ARRAY['admin', 'hr']::public.staff_role[]))
  WITH CHECK (public.has_staff_role(ARRAY['admin', 'hr']::public.staff_role[]));

-- ---------------------------------------------------------------------------
-- Grants
-- ---------------------------------------------------------------------------
GRANT SELECT, INSERT, UPDATE, DELETE ON public.employees TO authenticated, service_role;
GRANT SELECT, INSERT, UPDATE, DELETE ON public.salary_structures TO authenticated, service_role;
GRANT SELECT, INSERT, UPDATE, DELETE ON public.attendance_events TO authenticated, service_role;
GRANT SELECT, INSERT, UPDATE, DELETE ON public.payroll_runs TO authenticated, service_role;
GRANT SELECT, INSERT, UPDATE, DELETE ON public.payroll_lines TO authenticated, service_role;
GRANT SELECT, INSERT, UPDATE, DELETE ON public.payroll_deduction_lines TO authenticated, service_role;
GRANT SELECT, INSERT, UPDATE, DELETE ON public.payslips TO authenticated, service_role;

REVOKE ALL ON FUNCTION public.current_employee_id() FROM PUBLIC;
REVOKE ALL ON FUNCTION public._require_hr_staff() FROM PUBLIC;
REVOKE ALL ON FUNCTION public.attendance_hours_in_period(UUID, TIMESTAMPTZ, TIMESTAMPTZ) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.create_employee(TEXT, TEXT, UUID, TEXT, TEXT, DATE) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.upsert_salary_structure(UUID, public.salary_pay_type, NUMERIC, public.currency_code, DATE, DATE) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.clock_attendance(UUID, public.attendance_event_type, TIMESTAMPTZ, TEXT) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.emit_staff_no_show(UUID, TEXT) FROM PUBLIC;
REVOKE ALL ON FUNCTION public._refresh_payroll_run_totals(UUID) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.create_payroll_run(DATE, DATE, public.currency_code, NUMERIC, TEXT) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.compute_payroll_run(UUID, UUID[]) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.add_payroll_deduction(UUID, TEXT, NUMERIC) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.submit_payroll_run(UUID) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.cancel_payroll_run(UUID, TEXT) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.export_payslip(UUID) FROM PUBLIC;

GRANT EXECUTE ON FUNCTION public.current_employee_id() TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public._require_hr_staff() TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.attendance_hours_in_period(UUID, TIMESTAMPTZ, TIMESTAMPTZ) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.create_employee(TEXT, TEXT, UUID, TEXT, TEXT, DATE) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.upsert_salary_structure(UUID, public.salary_pay_type, NUMERIC, public.currency_code, DATE, DATE) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.clock_attendance(UUID, public.attendance_event_type, TIMESTAMPTZ, TEXT) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.emit_staff_no_show(UUID, TEXT) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.create_payroll_run(DATE, DATE, public.currency_code, NUMERIC, TEXT) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.compute_payroll_run(UUID, UUID[]) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.add_payroll_deduction(UUID, TEXT, NUMERIC) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.submit_payroll_run(UUID) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.cancel_payroll_run(UUID, TEXT) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.export_payslip(UUID) TO authenticated, service_role;

-- @management_app_agent follow-on: HR screens (employees, clock, payroll draft/submit, payslip)
-- Smoke: supabase/tests/phase9_hr_payroll_smoke.sql
