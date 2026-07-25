import type {
  Database,
  SupabaseClient,
} from "@gtr/supabase-client";
import {
  requireSession,
  type StorefrontResult,
} from "@/lib/customer-storefront";

export { requireSession };

export type AttendanceEventType =
  Database["public"]["Enums"]["attendance_event_type"];

export type EmployeeOption = {
  id: string;
  employee_code: string;
  full_name: string;
  status: string;
};

export async function resolveSelfEmployeeId(
  client: SupabaseClient,
): Promise<StorefrontResult<string | null>> {
  const { data, error } = await client.rpc("current_employee_id");
  if (error) return { ok: false, error: error.message };
  return { ok: true, data: data ?? null };
}

export async function listEmployees(
  client: SupabaseClient,
): Promise<StorefrontResult<EmployeeOption[]>> {
  const { data, error } = await client
    .from("employees")
    .select("id, employee_code, full_name, status")
    .order("employee_code")
    .limit(200);
  if (error) return { ok: false, error: error.message };
  return { ok: true, data: (data as EmployeeOption[]) ?? [] };
}

export async function clockAttendance(
  client: SupabaseClient,
  args: {
    employeeId: string;
    eventType: AttendanceEventType;
    notes?: string;
  },
): Promise<StorefrontResult<string>> {
  const { data, error } = await client.rpc("clock_attendance", {
    p_employee_id: args.employeeId,
    p_event_type: args.eventType,
    p_notes: args.notes || undefined,
  });
  if (error) return { ok: false, error: error.message };
  if (!data) return { ok: false, error: "Clock event returned no id." };
  return { ok: true, data };
}

export async function attendanceHoursInPeriod(
  client: SupabaseClient,
  args: {
    employeeId: string;
    periodStart: string;
    periodEnd: string;
  },
): Promise<StorefrontResult<number>> {
  const { data, error } = await client.rpc("attendance_hours_in_period", {
    p_employee_id: args.employeeId,
    p_period_start: args.periodStart,
    p_period_end: args.periodEnd,
  });
  if (error) return { ok: false, error: error.message };
  const hours = typeof data === "number" ? data : Number(data);
  if (!Number.isFinite(hours)) {
    return { ok: false, error: "Hours lookup returned a non-numeric value." };
  }
  return { ok: true, data: hours };
}

export type PayrollLineOption = {
  id: string;
  employee_id: string;
  gross_pay: number;
  currency: Database["public"]["Enums"]["currency_code"];
  payroll_run_id: string;
};

export async function listOpenPayrollLines(
  client: SupabaseClient,
): Promise<StorefrontResult<PayrollLineOption[]>> {
  const { data, error } = await client
    .from("payroll_lines")
    .select("id, employee_id, gross_pay, currency, payroll_run_id")
    .order("created_at", { ascending: false })
    .limit(40);
  if (error) return { ok: false, error: error.message };
  return { ok: true, data: (data as PayrollLineOption[]) ?? [] };
}

/** Manual/custom deduction only — no PAYE/NSSA/statutory tax UI. */
export async function addPayrollDeduction(
  client: SupabaseClient,
  args: { payrollLineId: string; label: string; amount: number },
): Promise<StorefrontResult<string>> {
  const { data, error } = await client.rpc("add_payroll_deduction", {
    p_payroll_line_id: args.payrollLineId,
    p_label: args.label,
    p_amount: args.amount,
  });
  if (error) return { ok: false, error: error.message };
  if (!data) return { ok: false, error: "add_payroll_deduction returned no id." };
  return { ok: true, data };
}
