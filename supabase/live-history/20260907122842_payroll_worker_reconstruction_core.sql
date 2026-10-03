-- Exported from the hosted project's supabase_migrations.schema_migrations (20260907122842 payroll_worker_reconstruction_core).
-- Source of record for what production ran; see supabase/live-history/README.md.

INSERT INTO public.chart_of_accounts (code, name, account_type, display_name)
VALUES ('2150', 'Payroll Deductions Clearing', 'liability', 'Payroll Deductions Clearing')
ON CONFLICT (code) DO UPDATE SET name = EXCLUDED.name, display_name = EXCLUDED.display_name, account_type = EXCLUDED.account_type, is_active = true;

CREATE TABLE IF NOT EXISTS public.payroll_run_funding (
  payroll_run_id UUID PRIMARY KEY REFERENCES public.payroll_runs(id) ON DELETE RESTRICT,
  journal_entry_id UUID NOT NULL UNIQUE REFERENCES public.journal_entries(id) ON DELETE RESTRICT,
  funded_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  funded_by UUID REFERENCES auth.users(id),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

ALTER TABLE public.payroll_run_funding ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS payroll_run_funding_select_staff ON public.payroll_run_funding;
CREATE POLICY payroll_run_funding_select_staff ON public.payroll_run_funding
FOR SELECT TO authenticated USING (
  public.has_staff_role(ARRAY['admin','finance','hr']::public.staff_role[])
);
REVOKE ALL ON TABLE public.payroll_run_funding FROM PUBLIC, anon, authenticated;
GRANT SELECT ON TABLE public.payroll_run_funding TO authenticated;
GRANT SELECT, INSERT ON TABLE public.payroll_run_funding TO service_role;

CREATE OR REPLACE FUNCTION public._next_payroll_schedule_at(
  p_frequency public.hr_pay_frequency,
  p_after TIMESTAMPTZ
)
RETURNS TIMESTAMPTZ
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_base TIMESTAMPTZ := COALESCE(p_after, now());
  v_day DATE := v_base::date;
  v_candidate TIMESTAMPTZ;
  v_dow INT;
BEGIN
  IF p_frequency = 'weekly' THEN
    v_dow := EXTRACT(ISODOW FROM v_day)::int;
    v_candidate := (v_day + ((8 - v_dow) % 7))::timestamptz + interval '6 hours';
    IF v_candidate <= v_base THEN v_candidate := v_candidate + interval '7 days'; END IF;
    RETURN v_candidate;
  ELSIF p_frequency = 'fortnightly' THEN
    v_candidate := date_trunc('month', v_base)::timestamptz + interval '6 hours';
    IF v_candidate > v_base THEN RETURN v_candidate; END IF;
    v_candidate := date_trunc('month', v_base)::timestamptz + interval '14 days 6 hours';
    IF v_candidate > v_base THEN RETURN v_candidate; END IF;
    RETURN (date_trunc('month', v_base) + interval '1 month')::timestamptz + interval '6 hours';
  ELSIF p_frequency = 'monthly' THEN
    v_candidate := date_trunc('month', v_base)::timestamptz + interval '24 days 6 hours';
    IF v_candidate <= v_base THEN v_candidate := (date_trunc('month', v_base) + interval '1 month 24 days 6 hours')::timestamptz; END IF;
    RETURN v_candidate;
  END IF;
  RAISE EXCEPTION 'unsupported pay frequency: %', p_frequency;
END;
$$;

UPDATE public.hr_payslip_schedules
SET next_run_at = public._next_payroll_schedule_at(pay_frequency, now())
WHERE is_active AND next_run_at IS NULL;

REVOKE ALL ON FUNCTION public._next_payroll_schedule_at(public.hr_pay_frequency,TIMESTAMPTZ) FROM PUBLIC,anon,authenticated;
GRANT EXECUTE ON FUNCTION public._next_payroll_schedule_at(public.hr_pay_frequency,TIMESTAMPTZ) TO service_role;
