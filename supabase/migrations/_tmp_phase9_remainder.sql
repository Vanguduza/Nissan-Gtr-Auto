-- Remainder of 20260724070000 (partial apply recovery)
CREATE POLICY payroll_runs_select_hr
  ON public.payroll_runs FOR SELECT TO authenticated
  USING (
    public.has_staff_role(ARRAY['admin', 'hr']::public.staff_role[])
    OR EXISTS (
      SELECT 1
      FROM public.payroll_lines l
      JOIN public.employees e ON e.id = l.employee_id
      WHERE l.payroll_run_id = payroll_runs.id AND e.user_id = auth.uid()
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
