-- Batch 1 §2.7 / §2.8 — leave types/balances/requests + payslip schedule by pay_frequency.
-- GROSS payroll only — no PAYE / NSSA / statutory remittance.

CREATE TYPE public.hr_leave_request_status AS ENUM (
  'draft',
  'submitted',
  'approved',
  'rejected',
  'cancelled'
);

CREATE TABLE IF NOT EXISTS public.hr_leave_types (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  code TEXT NOT NULL UNIQUE,
  title TEXT NOT NULL,
  is_paid BOOLEAN NOT NULL DEFAULT true,
  annual_allowance_days NUMERIC(6, 2) NOT NULL DEFAULT 0 CHECK (annual_allowance_days >= 0),
  is_active BOOLEAN NOT NULL DEFAULT true,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS public.hr_leave_balances (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  employee_id UUID NOT NULL REFERENCES public.employees (id) ON DELETE CASCADE,
  leave_type_id UUID NOT NULL REFERENCES public.hr_leave_types (id) ON DELETE RESTRICT,
  year INT NOT NULL CHECK (year >= 2000 AND year <= 2100),
  entitlement_days NUMERIC(6, 2) NOT NULL DEFAULT 0,
  used_days NUMERIC(6, 2) NOT NULL DEFAULT 0 CHECK (used_days >= 0),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (employee_id, leave_type_id, year)
);

CREATE TABLE IF NOT EXISTS public.hr_leave_requests (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  employee_id UUID NOT NULL REFERENCES public.employees (id) ON DELETE CASCADE,
  leave_type_id UUID NOT NULL REFERENCES public.hr_leave_types (id) ON DELETE RESTRICT,
  status public.hr_leave_request_status NOT NULL DEFAULT 'draft',
  start_date DATE NOT NULL,
  end_date DATE NOT NULL,
  days NUMERIC(6, 2) NOT NULL CHECK (days > 0),
  reason TEXT,
  submitted_at TIMESTAMPTZ,
  decided_by UUID REFERENCES auth.users (id),
  decided_at TIMESTAMPTZ,
  decision_note TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT hr_leave_requests_dates CHECK (end_date >= start_date)
);

CREATE INDEX IF NOT EXISTS hr_leave_requests_emp_idx ON public.hr_leave_requests (employee_id);
CREATE INDEX IF NOT EXISTS hr_leave_requests_status_idx ON public.hr_leave_requests (status);

INSERT INTO public.hr_leave_types (code, title, is_paid, annual_allowance_days) VALUES
  ('ANNUAL', 'Annual leave', true, 21),
  ('SICK', 'Sick leave', true, 10),
  ('UNPAID', 'Unpaid leave', false, 0)
ON CONFLICT (code) DO UPDATE
SET title = EXCLUDED.title, is_active = true, updated_at = now();

-- Payslip schedule driven by hr_roles.pay_frequency (gross only)
CREATE TABLE IF NOT EXISTS public.hr_payslip_schedules (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  pay_frequency public.hr_pay_frequency NOT NULL UNIQUE,
  cron_expr TEXT NOT NULL,
  last_run_at TIMESTAMPTZ,
  next_run_at TIMESTAMPTZ,
  is_active BOOLEAN NOT NULL DEFAULT true,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

INSERT INTO public.hr_payslip_schedules (pay_frequency, cron_expr) VALUES
  ('weekly', '0 6 * * 1'),
  ('fortnightly', '0 6 1,15 * *'),
  ('monthly', '0 6 25 * *')
ON CONFLICT (pay_frequency) DO NOTHING;

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
    p_period_start, p_period_end, p_currency, COALESCE(p_exchange_rate, 1)
  );
  PERFORM public.compute_payroll_run(v_run, v_emps);

  UPDATE public.hr_payslip_schedules
  SET last_run_at = now()
  WHERE pay_frequency = p_frequency;

  RETURN v_run;
END;
$$;

-- Leave RPCs (thin)
CREATE OR REPLACE FUNCTION public.submit_leave_request(p_request_id UUID)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_row public.hr_leave_requests%ROWTYPE;
BEGIN
  SELECT * INTO v_row FROM public.hr_leave_requests WHERE id = p_request_id FOR UPDATE;
  IF NOT FOUND THEN RAISE EXCEPTION 'leave request not found'; END IF;
  IF v_row.status <> 'draft' THEN RAISE EXCEPTION 'only draft requests can be submitted'; END IF;

  IF NOT (
    public.has_staff_role(ARRAY['admin', 'hr']::public.staff_role[])
    OR EXISTS (
      SELECT 1 FROM public.employees e
      WHERE e.id = v_row.employee_id AND e.user_id = auth.uid()
    )
  ) THEN
    RAISE EXCEPTION 'not allowed to submit this leave request';
  END IF;

  UPDATE public.hr_leave_requests
  SET status = 'submitted', submitted_at = now(), updated_at = now()
  WHERE id = p_request_id;
  RETURN p_request_id;
END;
$$;

CREATE OR REPLACE FUNCTION public.decide_leave_request(
  p_request_id UUID,
  p_approve BOOLEAN,
  p_note TEXT DEFAULT NULL
)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_row public.hr_leave_requests%ROWTYPE;
  v_year INT;
BEGIN
  IF NOT public.has_staff_role(ARRAY['admin', 'hr']::public.staff_role[]) THEN
    RAISE EXCEPTION 'hr or admin role required';
  END IF;

  SELECT * INTO v_row FROM public.hr_leave_requests WHERE id = p_request_id FOR UPDATE;
  IF NOT FOUND THEN RAISE EXCEPTION 'leave request not found'; END IF;
  IF v_row.status <> 'submitted' THEN
    RAISE EXCEPTION 'only submitted requests can be decided';
  END IF;

  IF p_approve THEN
    v_year := EXTRACT(YEAR FROM v_row.start_date)::INT;
    INSERT INTO public.hr_leave_balances (employee_id, leave_type_id, year, entitlement_days, used_days)
    VALUES (v_row.employee_id, v_row.leave_type_id, v_year, 0, v_row.days)
    ON CONFLICT (employee_id, leave_type_id, year) DO UPDATE
    SET used_days = public.hr_leave_balances.used_days + EXCLUDED.used_days,
        updated_at = now();

    UPDATE public.hr_leave_requests
    SET status = 'approved', decided_by = auth.uid(), decided_at = now(),
        decision_note = p_note, updated_at = now()
    WHERE id = p_request_id;
  ELSE
    UPDATE public.hr_leave_requests
    SET status = 'rejected', decided_by = auth.uid(), decided_at = now(),
        decision_note = p_note, updated_at = now()
    WHERE id = p_request_id;
  END IF;

  RETURN p_request_id;
END;
$$;

-- RLS
ALTER TABLE public.hr_leave_types ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.hr_leave_balances ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.hr_leave_requests ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.hr_payslip_schedules ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS hr_leave_types_select ON public.hr_leave_types;
CREATE POLICY hr_leave_types_select ON public.hr_leave_types
  FOR SELECT TO authenticated
  USING (public.is_staff());

DROP POLICY IF EXISTS hr_leave_types_write ON public.hr_leave_types;
CREATE POLICY hr_leave_types_write ON public.hr_leave_types
  FOR ALL TO authenticated
  USING (public.has_staff_role(ARRAY['admin', 'hr']::public.staff_role[]))
  WITH CHECK (public.has_staff_role(ARRAY['admin', 'hr']::public.staff_role[]));

DROP POLICY IF EXISTS hr_leave_balances_select ON public.hr_leave_balances;
CREATE POLICY hr_leave_balances_select ON public.hr_leave_balances
  FOR SELECT TO authenticated
  USING (
    public.has_staff_role(ARRAY['admin', 'hr']::public.staff_role[])
    OR EXISTS (
      SELECT 1 FROM public.employees e
      WHERE e.id = employee_id AND e.user_id = auth.uid()
    )
  );

DROP POLICY IF EXISTS hr_leave_balances_write ON public.hr_leave_balances;
CREATE POLICY hr_leave_balances_write ON public.hr_leave_balances
  FOR ALL TO authenticated
  USING (public.has_staff_role(ARRAY['admin', 'hr']::public.staff_role[]))
  WITH CHECK (public.has_staff_role(ARRAY['admin', 'hr']::public.staff_role[]));

DROP POLICY IF EXISTS hr_leave_requests_select ON public.hr_leave_requests;
CREATE POLICY hr_leave_requests_select ON public.hr_leave_requests
  FOR SELECT TO authenticated
  USING (
    public.has_staff_role(ARRAY['admin', 'hr']::public.staff_role[])
    OR EXISTS (
      SELECT 1 FROM public.employees e
      WHERE e.id = employee_id AND e.user_id = auth.uid()
    )
  );

DROP POLICY IF EXISTS hr_leave_requests_insert ON public.hr_leave_requests;
CREATE POLICY hr_leave_requests_insert ON public.hr_leave_requests
  FOR INSERT TO authenticated
  WITH CHECK (
    public.has_staff_role(ARRAY['admin', 'hr']::public.staff_role[])
    OR EXISTS (
      SELECT 1 FROM public.employees e
      WHERE e.id = employee_id AND e.user_id = auth.uid()
    )
  );

DROP POLICY IF EXISTS hr_leave_requests_update ON public.hr_leave_requests;
CREATE POLICY hr_leave_requests_update ON public.hr_leave_requests
  FOR UPDATE TO authenticated
  USING (
    public.has_staff_role(ARRAY['admin', 'hr']::public.staff_role[])
    OR (
      status = 'draft'
      AND EXISTS (
        SELECT 1 FROM public.employees e
        WHERE e.id = employee_id AND e.user_id = auth.uid()
      )
    )
  );

DROP POLICY IF EXISTS hr_payslip_schedules_select ON public.hr_payslip_schedules;
CREATE POLICY hr_payslip_schedules_select ON public.hr_payslip_schedules
  FOR SELECT TO authenticated
  USING (public.has_staff_role(ARRAY['admin', 'hr']::public.staff_role[]));

DROP POLICY IF EXISTS hr_payslip_schedules_write ON public.hr_payslip_schedules;
CREATE POLICY hr_payslip_schedules_write ON public.hr_payslip_schedules
  FOR ALL TO authenticated
  USING (public.has_staff_role(ARRAY['admin']::public.staff_role[]))
  WITH CHECK (public.has_staff_role(ARRAY['admin']::public.staff_role[]));

REVOKE ALL ON FUNCTION public.run_scheduled_payroll_for_frequency(
  public.hr_pay_frequency, DATE, DATE, public.currency_code, NUMERIC
) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.submit_leave_request(UUID) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.decide_leave_request(UUID, BOOLEAN, TEXT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.run_scheduled_payroll_for_frequency(
  public.hr_pay_frequency, DATE, DATE, public.currency_code, NUMERIC
) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.submit_leave_request(UUID) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.decide_leave_request(UUID, BOOLEAN, TEXT)
  TO authenticated, service_role;
