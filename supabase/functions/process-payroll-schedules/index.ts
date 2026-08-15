/**
 * Cron / worker: due hr_payslip_schedules → create/compute/submit/fund
 * gross payroll (5200/2150/cash) + branded PDF payslips to Storage.
 *
 * AuthZ: x-worker-secret ↔ WORKER_SHARED_SECRET (see _shared/worker_auth.ts).
 * No PAYE/NSSA/statutory · no ZIMRA · no ContiPay bank API (GL cash only).
 */
import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient, type SupabaseClient } from "npm:@supabase/supabase-js@2";
import { assertWorkerSecret } from "../_shared/worker_auth.ts";
import { jsonErr, jsonOk } from "../_shared/channel_env.ts";
import { buildPayslipPdf } from "../_shared/branded_docs_pdf.ts";

function serviceClient(): SupabaseClient {
  return createClient(
    Deno.env.get("SUPABASE_URL")!,
    Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
  );
}

type FundResult = {
  payroll_run_id?: string;
  payslip_ids?: string[];
  funded_count?: number;
};

type ScheduleResult = {
  pay_frequency?: string;
  payroll_run_id?: string;
  ok?: boolean;
  error?: string;
  fund?: FundResult;
};

type DuePayload = {
  as_of?: string;
  results?: ScheduleResult[];
};

async function uploadPayslipPdf(
  supabase: SupabaseClient,
  payrollLineId: string,
): Promise<{ ok: boolean; path?: string; error?: string }> {
  const { data: payload, error } = await supabase.rpc("payslip_render_payload", {
    p_payroll_line_id: payrollLineId,
  });
  if (error || !payload || typeof payload !== "object") {
    return { ok: false, error: error?.message ?? "payslip_render_payload failed" };
  }
  const row = payload as Record<string, unknown>;
  const bucket = String(row.storageBucket ?? "payslips");
  const path = String(row.storagePath ?? "");
  if (!path) return { ok: false, error: "missing storagePath" };

  const deductions = Array.isArray(row.manualDeductions)
    ? row.manualDeductions.map((d) => {
      const x = d as Record<string, unknown>;
      return {
        label: String(x.label ?? "Deduction"),
        amount: Number(x.amount ?? 0),
      };
    })
    : [];

  const bytes = await buildPayslipPdf({
    storeName: String(row.storeName ?? "Nissan GTR Auto"),
    employeeName: String(row.employeeName ?? ""),
    employeeCode: String(row.employeeCode ?? ""),
    periodStart: String(row.periodStart ?? ""),
    periodEnd: String(row.periodEnd ?? ""),
    currency: String(row.currency ?? "USD"),
    grossPay: Number(row.grossPay ?? 0),
    manualDeductions: deductions,
    netPay: Number(row.netPay ?? 0),
  });

  const { error: upErr } = await supabase.storage
    .from(bucket)
    .upload(path, bytes, {
      contentType: "application/pdf",
      upsert: true,
    });
  if (upErr) return { ok: false, error: upErr.message };
  return { ok: true, path };
}

Deno.serve(async (req) => {
  try {
    const denied = assertWorkerSecret(req);
    if (denied) return denied;

    if (req.method !== "POST") {
      return jsonErr("POST required", 405);
    }

    const body = await req.json().catch(() => ({}));
    const asOf = typeof body?.as_of === "string" ? body.as_of : undefined;
    const skipPdf = Boolean(body?.skip_pdf);

    const supabase = serviceClient();
    const { data, error } = await supabase.rpc("run_due_payroll_schedules", {
      p_as_of: asOf ?? new Date().toISOString(),
    });
    if (error) {
      return jsonErr(error.message, 400);
    }

    const due = (data ?? {}) as DuePayload;
    const results = Array.isArray(due.results) ? due.results : [];
    const pdfUploads: Array<Record<string, unknown>> = [];

    if (!skipPdf) {
      for (const r of results) {
        if (!r?.ok || !r.fund?.payslip_ids?.length) continue;
        for (const psId of r.fund.payslip_ids) {
          // payslip_ids from fund RPC are payslip row ids — resolve line via table
          const { data: psRow } = await supabase
            .from("payslips")
            .select("payroll_line_id")
            .eq("id", psId)
            .maybeSingle();
          const lineId = psRow?.payroll_line_id as string | undefined;
          if (!lineId) {
            pdfUploads.push({ payslip_id: psId, ok: false, error: "line missing" });
            continue;
          }
          const uploaded = await uploadPayslipPdf(supabase, lineId);
          pdfUploads.push({
            payslip_id: psId,
            payroll_line_id: lineId,
            ...uploaded,
          });
        }
      }
    }

    await supabase.rpc("touch_ai_worker_schedule", {
      p_worker_key: "process_payroll_schedules",
    }).catch(() => null);

    return jsonOk({
      ok: true,
      due,
      pdf_uploads: pdfUploads,
      note: "Gross payroll only — no PAYE/NSSA; GL cash credit, not ContiPay",
    });
  } catch (e) {
    console.error("process-payroll-schedules:", e);
    return jsonErr(e instanceof Error ? e.message : "failed", 500);
  }
});
