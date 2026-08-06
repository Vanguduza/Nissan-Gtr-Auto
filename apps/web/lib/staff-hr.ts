import type {
  Database,
  SupabaseClient,
} from "@gtr/supabase-client";
import {
  buildBusinessCardPayload,
  buildIdCardPayload,
  buildPayslipExportPayload,
  toRenderBrandedDocBody,
  type BrandedBusinessCardInput,
  type BrandedDocKind,
  type BrandedIdCardInput,
  type BrandedPayslipInput,
  type DocumentCurrency,
} from "@gtr/documents";
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
  gross_amount: number;
  deductions_amount: number;
  net_amount: number;
  currency: Database["public"]["Enums"]["currency_code"];
  payroll_run_id: string;
};

export type PayrollDeductionOption = {
  id: string;
  payroll_line_id: string;
  label: string;
  amount: number;
};

export async function listOpenPayrollLines(
  client: SupabaseClient,
): Promise<StorefrontResult<PayrollLineOption[]>> {
  const { data, error } = await client
    .from("payroll_lines")
    .select(
      "id, employee_id, gross_amount, deductions_amount, net_amount, currency, payroll_run_id",
    )
    .order("created_at", { ascending: false })
    .limit(40);
  if (error) return { ok: false, error: error.message };
  return { ok: true, data: (data as PayrollLineOption[]) ?? [] };
}

export async function listPayrollDeductions(
  client: SupabaseClient,
  payrollLineId: string,
): Promise<StorefrontResult<PayrollDeductionOption[]>> {
  const { data, error } = await client
    .from("payroll_deduction_lines")
    .select("id, payroll_line_id, label, amount")
    .eq("payroll_line_id", payrollLineId)
    .order("created_at", { ascending: true });
  if (error) return { ok: false, error: error.message };
  return { ok: true, data: (data as PayrollDeductionOption[]) ?? [] };
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

export type HrGradeOption = {
  id: string;
  code: string;
  title: string;
  sort_order: number;
};

export type HrRoleOption = {
  id: string;
  title: string;
  department: string | null;
  parent_role_id: string | null;
  grade_id: string;
  pay_frequency: string;
  module_access: unknown;
  is_active: boolean;
  hr_grades?: { code: string; title: string } | null;
};

export async function listHrGrades(
  client: SupabaseClient,
): Promise<StorefrontResult<HrGradeOption[]>> {
  const { data, error } = await client
    .from("hr_grades")
    .select("id, code, title, sort_order")
    .eq("is_active", true)
    .order("sort_order");
  if (error) return { ok: false, error: error.message };
  return { ok: true, data: (data as HrGradeOption[]) ?? [] };
}

export async function listHrRoles(
  client: SupabaseClient,
): Promise<StorefrontResult<HrRoleOption[]>> {
  const { data, error } = await client
    .from("hr_roles")
    .select(
      "id, title, department, parent_role_id, grade_id, pay_frequency, module_access, is_active, hr_grades ( code, title )",
    )
    .eq("is_active", true)
    .order("title");
  if (error) return { ok: false, error: error.message };
  const rows = (data ?? []).map((row) => {
    const grade = Array.isArray(row.hr_grades)
      ? (row.hr_grades[0] ?? null)
      : (row.hr_grades ?? null);
    return { ...row, hr_grades: grade } as HrRoleOption;
  });
  return { ok: true, data: rows };
}

export async function createHrGrade(
  client: SupabaseClient,
  args: { code: string; title: string; sortOrder?: number },
): Promise<StorefrontResult<string>> {
  const { data, error } = await client.rpc("create_hr_grade", {
    p_code: args.code,
    p_title: args.title,
    p_sort_order: args.sortOrder ?? 100,
  });
  if (error) return { ok: false, error: error.message };
  if (!data) return { ok: false, error: "create_hr_grade returned no id." };
  return { ok: true, data };
}

export async function createHrRole(
  client: SupabaseClient,
  args: {
    title: string;
    gradeId: string;
    parentRoleId?: string | null;
    department?: string | null;
    payFrequency?: string;
    moduleAccess?: string[];
  },
): Promise<StorefrontResult<string>> {
  const { data, error } = await client.rpc("create_hr_role", {
    p_title: args.title,
    p_grade_id: args.gradeId,
    p_parent_role_id: args.parentRoleId || undefined,
    p_department: args.department || undefined,
    p_pay_frequency: args.payFrequency || "monthly",
    p_module_access: args.moduleAccess ?? [],
  });
  if (error) return { ok: false, error: error.message };
  if (!data) return { ok: false, error: "create_hr_role returned no id." };
  return { ok: true, data };
}

export async function archiveHrRole(
  client: SupabaseClient,
  roleId: string,
): Promise<StorefrontResult<string>> {
  const { data, error } = await client.rpc("archive_hr_role", {
    p_role_id: roleId,
  });
  if (error) return { ok: false, error: error.message };
  if (!data) return { ok: false, error: "archive_hr_role returned no id." };
  return { ok: true, data };
}

export type HrOnboardingStage =
  Database["public"]["Enums"]["hr_onboarding_stage"];

export type HrOnboardingDraft = {
  id: string;
  employee_id: string | null;
  stage: HrOnboardingStage;
  payload: Record<string, unknown>;
  banking_json: Record<string, unknown> | null;
  health_json: Record<string, unknown> | null;
  completed_at: string | null;
  updated_at: string;
};

export async function listHrOnboardingDrafts(
  client: SupabaseClient,
): Promise<StorefrontResult<HrOnboardingDraft[]>> {
  const { data, error } = await client
    .from("hr_onboarding_drafts")
    .select(
      "id, employee_id, stage, payload, banking_json, health_json, completed_at, updated_at",
    )
    .is("completed_at", null)
    .order("updated_at", { ascending: false })
    .limit(40);
  if (error) return { ok: false, error: error.message };
  return {
    ok: true,
    data: ((data ?? []) as HrOnboardingDraft[]).map((d) => ({
      ...d,
      payload: (d.payload as Record<string, unknown>) ?? {},
      banking_json: (d.banking_json as Record<string, unknown>) ?? null,
      health_json: (d.health_json as Record<string, unknown>) ?? null,
    })),
  };
}

export async function saveHrOnboardingStage(
  client: SupabaseClient,
  args: {
    draftId?: string | null;
    stage: HrOnboardingStage;
    payload?: Record<string, unknown>;
    bankingJson?: Record<string, unknown> | null;
    healthJson?: Record<string, unknown> | null;
    employeeId?: string | null;
  },
): Promise<StorefrontResult<string>> {
  const { data, error } = await client.rpc("save_hr_onboarding_stage", {
    p_draft_id: args.draftId || undefined,
    p_stage: args.stage,
    p_payload: args.payload ?? {},
    p_banking_json: args.bankingJson ?? undefined,
    p_health_json: args.healthJson ?? undefined,
    p_employee_id: args.employeeId || undefined,
  });
  if (error) return { ok: false, error: error.message };
  if (!data) return { ok: false, error: "save_hr_onboarding_stage returned no id." };
  return { ok: true, data };
}

export async function completeHrOnboarding(
  client: SupabaseClient,
  draftId: string,
): Promise<StorefrontResult<Record<string, unknown>>> {
  const { data, error } = await client.rpc("complete_hr_onboarding", {
    p_draft_id: draftId,
  });
  if (error) return { ok: false, error: error.message };
  if (!data || typeof data !== "object") {
    return { ok: false, error: "complete_hr_onboarding returned no payload." };
  }
  return { ok: true, data: data as Record<string, unknown> };
}

export type HrOnboardingAuthChannel = {
  channel: "email" | "sms" | "whatsapp";
  status: "sent" | "stub" | "failed" | "skipped";
  outbox_id?: string;
  error?: string;
};

export type HrOnboardingCreateAuthResult = {
  employee_id: string;
  user_id: string;
  created: boolean;
  must_change_password: boolean;
  channels: HrOnboardingAuthChannel[];
};

/**
 * Edge Admin create/link when complete_hr_onboarding left user_id null.
 * Never returns the temp password — delivery is outbox/gateway only.
 */
export async function createHrOnboardingAuthUser(
  client: SupabaseClient,
  employeeId: string,
): Promise<StorefrontResult<HrOnboardingCreateAuthResult>> {
  const { data, error } = await client.functions.invoke(
    "hr-onboarding-create-auth",
    { body: { employee_id: employeeId } },
  );
  if (error) {
    return { ok: false, error: error.message };
  }
  const body = data as Record<string, unknown> | null;
  if (!body || body.ok !== true || typeof body.user_id !== "string") {
    const errMsg =
      typeof body?.error === "string"
        ? body.error
        : "hr-onboarding-create-auth failed.";
    return { ok: false, error: errMsg };
  }
  return {
    ok: true,
    data: {
      employee_id: String(body.employee_id ?? employeeId),
      user_id: body.user_id,
      created: Boolean(body.created),
      must_change_password: Boolean(body.must_change_password ?? true),
      channels: Array.isArray(body.channels)
        ? (body.channels as HrOnboardingAuthChannel[])
        : [],
    },
  };
}

/** Manual deduction payslip path — creates payslips row; PDF via render-branded-doc. */
export async function exportPayslip(
  client: SupabaseClient,
  payrollLineId: string,
): Promise<StorefrontResult<string>> {
  const { data, error } = await client.rpc("export_payslip", {
    p_payroll_line_id: payrollLineId,
  });
  if (error) return { ok: false, error: error.message };
  if (!data) return { ok: false, error: "export_payslip returned no id." };
  return { ok: true, data };
}

async function downloadBrandedDocPdf(
  accessToken: string,
  kind: BrandedDocKind,
  payload: Record<string, unknown>,
  filename: string,
): Promise<StorefrontResult<true>> {
  const base = process.env.NEXT_PUBLIC_SUPABASE_URL?.replace(/\/$/, "");
  if (!base) {
    return { ok: false, error: "NEXT_PUBLIC_SUPABASE_URL not configured." };
  }
  const res = await fetch(`${base}/functions/v1/render-branded-doc`, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${accessToken}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify(toRenderBrandedDocBody(kind, payload as never)),
  });
  if (!res.ok) {
    const errText = await res.text().catch(() => res.statusText);
    return { ok: false, error: `PDF render failed: ${errText}` };
  }
  const blob = await res.blob();
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = filename;
  a.click();
  URL.revokeObjectURL(url);
  return { ok: true, data: true };
}

/** Payslip PDF — gross − manual deductions only (no PAYE/NSSA). */
export async function downloadBrandedPayslipPdf(
  accessToken: string,
  input: BrandedPayslipInput,
): Promise<StorefrontResult<true>> {
  const payload = buildPayslipExportPayload(input);
  return downloadBrandedDocPdf(
    accessToken,
    "payslip",
    payload as unknown as Record<string, unknown>,
    `payslip-${payload.employeeCode || "staff"}.pdf`,
  );
}

/** CR80 ID card PDF (85.6×54 mm). No fiscal QR. */
export async function downloadBrandedIdCardPdf(
  accessToken: string,
  input: BrandedIdCardInput,
): Promise<StorefrontResult<true>> {
  const payload = buildIdCardPayload(input);
  return downloadBrandedDocPdf(
    accessToken,
    "id_card",
    payload as unknown as Record<string, unknown>,
    `id-card-${payload.employeeCode || "staff"}.pdf`,
  );
}

/** Business card PDF (90×50 mm). */
export async function downloadBrandedBusinessCardPdf(
  accessToken: string,
  input: BrandedBusinessCardInput,
): Promise<StorefrontResult<true>> {
  const payload = buildBusinessCardPayload(input);
  return downloadBrandedDocPdf(
    accessToken,
    "business_card",
    payload as unknown as Record<string, unknown>,
    `business-card-${payload.employeeCode || "staff"}.pdf`,
  );
}

export type { DocumentCurrency };

export async function setCustomerMarketingOptIn(
  client: SupabaseClient,
  args: { customerId: string; optIn: boolean },
): Promise<StorefrontResult<string>> {
  const { data, error } = await client.rpc("set_customer_marketing_opt_in", {
    p_customer_id: args.customerId,
    p_opt_in: args.optIn,
  });
  if (error) return { ok: false, error: error.message };
  if (!data) {
    return { ok: false, error: "set_customer_marketing_opt_in returned no id." };
  }
  return { ok: true, data };
}
