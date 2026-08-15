import type { SupabaseClient } from "@gtr/supabase-client";
import type { StorefrontResult } from "@/lib/customer-storefront";
import {
  downloadBrandedIdCardPdf,
  downloadBrandedPayslipPdf,
  exportPayslip,
  listPayrollDeductions,
  type DocumentCurrency,
} from "@/lib/staff-hr";

export type StaffMyProfile = {
  has_employee: boolean;
  user_id?: string;
  employee_id?: string;
  employee_code?: string;
  full_name?: string;
  email?: string | null;
  phone_e164?: string | null;
  address?: string | null;
  photo_storage_path?: string | null;
  status?: string;
  hire_date?: string | null;
  grade_id?: string | null;
  grade_code?: string | null;
  grade_title?: string | null;
  hr_role_id?: string | null;
  role_title?: string | null;
  module_access?: string[];
  staff_roles?: string[];
};

export type StaffPayslipHistoryRow = {
  payroll_line_id: string;
  payroll_run_id: string;
  payslip_id: string | null;
  period_start: string;
  period_end: string;
  document_number: string | null;
  run_status: string;
  currency: DocumentCurrency;
  gross_amount: number;
  deductions_amount: number;
  net_amount: number;
  funded: boolean;
  storage_bucket: string | null;
  storage_path: string | null;
  generated_at: string | null;
};

function asStringArray(raw: unknown): string[] {
  if (!Array.isArray(raw)) return [];
  return raw.filter((v): v is string => typeof v === "string");
}

function parseProfile(raw: unknown): StaffMyProfile {
  const row = (raw && typeof raw === "object" ? raw : {}) as Record<
    string,
    unknown
  >;
  return {
    has_employee: Boolean(row.has_employee),
    user_id: typeof row.user_id === "string" ? row.user_id : undefined,
    employee_id:
      typeof row.employee_id === "string" ? row.employee_id : undefined,
    employee_code:
      typeof row.employee_code === "string" ? row.employee_code : undefined,
    full_name: typeof row.full_name === "string" ? row.full_name : undefined,
    email: typeof row.email === "string" ? row.email : null,
    phone_e164: typeof row.phone_e164 === "string" ? row.phone_e164 : null,
    address: typeof row.address === "string" ? row.address : null,
    photo_storage_path:
      typeof row.photo_storage_path === "string"
        ? row.photo_storage_path
        : null,
    status: typeof row.status === "string" ? row.status : undefined,
    hire_date: typeof row.hire_date === "string" ? row.hire_date : null,
    grade_id: typeof row.grade_id === "string" ? row.grade_id : null,
    grade_code: typeof row.grade_code === "string" ? row.grade_code : null,
    grade_title: typeof row.grade_title === "string" ? row.grade_title : null,
    hr_role_id: typeof row.hr_role_id === "string" ? row.hr_role_id : null,
    role_title: typeof row.role_title === "string" ? row.role_title : null,
    module_access: asStringArray(row.module_access),
    staff_roles: asStringArray(row.staff_roles),
  };
}

function parseHistory(raw: unknown): StaffPayslipHistoryRow[] {
  if (!Array.isArray(raw)) return [];
  return raw.map((item) => {
    const row = (item && typeof item === "object" ? item : {}) as Record<
      string,
      unknown
    >;
    const currency =
      row.currency === "ZIG" || row.currency === "USD"
        ? row.currency
        : ("USD" as DocumentCurrency);
    return {
      payroll_line_id: String(row.payroll_line_id ?? ""),
      payroll_run_id: String(row.payroll_run_id ?? ""),
      payslip_id:
        typeof row.payslip_id === "string" ? row.payslip_id : null,
      period_start: String(row.period_start ?? ""),
      period_end: String(row.period_end ?? ""),
      document_number:
        typeof row.document_number === "string" ? row.document_number : null,
      run_status: String(row.run_status ?? ""),
      currency,
      gross_amount: Number(row.gross_amount ?? 0),
      deductions_amount: Number(row.deductions_amount ?? 0),
      net_amount: Number(row.net_amount ?? 0),
      funded: Boolean(row.funded),
      storage_bucket:
        typeof row.storage_bucket === "string" ? row.storage_bucket : null,
      storage_path:
        typeof row.storage_path === "string" ? row.storage_path : null,
      generated_at:
        typeof row.generated_at === "string" ? row.generated_at : null,
    };
  });
}

export async function getMyStaffProfile(
  client: SupabaseClient,
): Promise<StorefrontResult<StaffMyProfile>> {
  const { data, error } = await client.rpc("get_my_staff_profile");
  if (error) return { ok: false, error: error.message };
  return { ok: true, data: parseProfile(data) };
}

export async function updateMyStaffProfile(
  client: SupabaseClient,
  args: {
    phoneE164: string;
    address: string;
    email: string;
    /** When true, call GoTrue updateUser for email before RPC sync. */
    syncAuthEmail?: boolean;
  },
): Promise<StorefrontResult<StaffMyProfile>> {
  const email = args.email.trim();
  if (args.syncAuthEmail && email) {
    const { error: authErr } = await client.auth.updateUser({ email });
    if (authErr) return { ok: false, error: authErr.message };
  }
  const { data, error } = await client.rpc("update_my_staff_profile", {
    p_phone_e164: args.phoneE164.trim() || null,
    p_address: args.address.trim() || null,
    p_email: email || null,
  });
  if (error) return { ok: false, error: error.message };
  return { ok: true, data: parseProfile(data) };
}

export async function listMyPayslipHistory(
  client: SupabaseClient,
): Promise<StorefrontResult<StaffPayslipHistoryRow[]>> {
  const { data, error } = await client.rpc("list_my_payslip_history");
  if (error) return { ok: false, error: error.message };
  return { ok: true, data: parseHistory(data) };
}

/** Prefer Storage PDF when present; else render via payslip_render_payload + branded doc. */
export async function downloadMyPayslip(
  client: SupabaseClient,
  accessToken: string,
  row: StaffPayslipHistoryRow,
  profile: StaffMyProfile,
): Promise<StorefrontResult<true>> {
  if (row.storage_bucket && row.storage_path) {
    const { data, error } = await client.storage
      .from(row.storage_bucket)
      .download(row.storage_path);
    if (!error && data) {
      const url = URL.createObjectURL(data);
      const a = document.createElement("a");
      a.href = url;
      a.download = `payslip-${row.period_end || "staff"}.pdf`;
      a.click();
      URL.revokeObjectURL(url);
      return { ok: true, data: true };
    }
  }

  // Ensure payslips metadata row exists (idempotent for own/submitted lines).
  const exported = await exportPayslip(client, row.payroll_line_id);
  if (!exported.ok) {
    // Fall through to render payload even if metadata insert fails for cancelled.
  }

  const { data: payload, error } = await client.rpc("payslip_render_payload", {
    p_payroll_line_id: row.payroll_line_id,
  });
  if (error) return { ok: false, error: error.message };
  const body = (payload && typeof payload === "object" ? payload : {}) as Record<
    string,
    unknown
  >;
  const deductions = await listPayrollDeductions(client, row.payroll_line_id);
  const manual =
    deductions.ok
      ? deductions.data.map((d) => ({
          label: d.label,
          amount: Number(d.amount),
        }))
      : Array.isArray(body.manualDeductions)
        ? (body.manualDeductions as Array<{ label: string; amount: number }>)
        : [];

  return downloadBrandedPayslipPdf(accessToken, {
    storeName: String(body.storeName ?? "Nissan GTR Auto"),
    employeeName: String(body.employeeName ?? profile.full_name ?? ""),
    employeeCode: String(body.employeeCode ?? profile.employee_code ?? ""),
    periodStart: String(body.periodStart ?? row.period_start),
    periodEnd: String(body.periodEnd ?? row.period_end),
    currency:
      body.currency === "ZIG" || body.currency === "USD"
        ? body.currency
        : row.currency,
    grossPay: Number(body.grossPay ?? row.gross_amount),
    manualDeductions: manual,
    netPay: Number(body.netPay ?? row.net_amount),
  });
}

export async function downloadMyIdCard(
  accessToken: string,
  profile: StaffMyProfile,
): Promise<StorefrontResult<true>> {
  if (!profile.has_employee || !profile.employee_code) {
    return { ok: false, error: "No employee record linked to this account." };
  }
  const roleBits = [profile.role_title, profile.grade_code]
    .filter(Boolean)
    .join(" · ");
  return downloadBrandedIdCardPdf(accessToken, {
    storeName: "Nissan GTR Auto",
    fullName: profile.full_name ?? "Staff",
    roleTitle: roleBits || "Staff",
    staffRole: profile.staff_roles?.[0] ?? null,
    employeeCode: profile.employee_code,
    photoStoragePath: profile.photo_storage_path ?? null,
    verifyUrl: profile.employee_id
      ? `https://nissangtrauto.co.zw/staff/verify/${profile.employee_id}`
      : null,
  });
}
