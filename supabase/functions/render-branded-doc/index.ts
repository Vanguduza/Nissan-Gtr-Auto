/**
 * Render branded PDFs (statement / payslip / ID / business card).
 * Staff JWT required. Tax-agnostic — no ZIMRA / fiscal QR.
 *
 * Page sizes (pdf-lib pt via mmToPt in branded_docs_pdf):
 * - statement / payslip: A4
 * - id_card: CR80 85.6×54 mm
 * - business_card: 90×50 mm
 *
 * POST { kind, ...payload } → application/pdf
 */
import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "npm:@supabase/supabase-js@2";
import { corsHeaders, jsonResponse } from "../_shared/payment_edge.ts";
import {
  buildBusinessCardPdf,
  buildIdCardPdf,
  buildPayslipPdf,
  buildStatementPdf,
} from "../_shared/branded_docs_pdf.ts";

type Kind = "statement" | "payslip" | "id_card" | "business_card";

Deno.serve(async (req) => {
  const cors = corsHeaders(req);
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: cors });
  }
  if (req.method !== "POST") {
    return jsonResponse({ error: "POST required" }, 405, cors);
  }

  const supabaseUrl = Deno.env.get("SUPABASE_URL")?.trim();
  const anonKey = Deno.env.get("SUPABASE_ANON_KEY")?.trim();
  if (!supabaseUrl || !anonKey) {
    return jsonResponse({ error: "misconfigured" }, 503, cors);
  }

  const auth = req.headers.get("Authorization") ?? "";
  if (!auth.toLowerCase().startsWith("bearer ")) {
    return jsonResponse({ error: "unauthorized" }, 401, cors);
  }

  const userClient = createClient(supabaseUrl, anonKey, {
    global: { headers: { Authorization: auth } },
    auth: { persistSession: false, autoRefreshToken: false },
  });

  const { data: userData, error: userErr } = await userClient.auth.getUser();
  if (userErr || !userData.user) {
    return jsonResponse({ error: "unauthorized" }, 401, cors);
  }

  const { data: isStaff, error: staffErr } = await userClient.rpc("is_staff");
  if (staffErr || !isStaff) {
    return jsonResponse({ error: "staff only" }, 403, cors);
  }

  let body: Record<string, unknown>;
  try {
    body = await req.json();
  } catch {
    return jsonResponse({ error: "invalid JSON" }, 400, cors);
  }

  const kind = String(body.kind ?? "") as Kind;
  let bytes: Uint8Array;

  try {
    if (kind === "statement") {
      bytes = await buildStatementPdf({
        storeName: String(body.storeName ?? "Nissan GTR Auto"),
        storeCode: (body.storeCode as string) ?? null,
        documentLabel: String(body.documentLabel ?? "Statement"),
        currency: String(body.currency ?? "USD"),
        exchangeRate: body.exchangeRate != null
          ? Number(body.exchangeRate)
          : null,
        asOf: (body.asOf as string) ?? null,
        partyName: (body.partyName as string) ?? null,
        openingBalance: body.openingBalance != null
          ? Number(body.openingBalance)
          : undefined,
        closingBalance: body.closingBalance != null
          ? Number(body.closingBalance)
          : undefined,
        lines: Array.isArray(body.lines)
          ? body.lines.map((l) => {
            const row = l as Record<string, unknown>;
            return {
              description: String(row.description ?? ""),
              qty: row.qty != null ? Number(row.qty) : undefined,
              unitPrice: row.unitPrice != null
                ? Number(row.unitPrice)
                : undefined,
              lineTotal: Number(row.lineTotal ?? 0),
            };
          })
          : [],
      });
    } else if (kind === "payslip") {
      bytes = await buildPayslipPdf({
        storeName: String(body.storeName ?? "Nissan GTR Auto"),
        employeeName: String(body.employeeName ?? ""),
        employeeCode: String(body.employeeCode ?? ""),
        periodStart: String(body.periodStart ?? ""),
        periodEnd: String(body.periodEnd ?? ""),
        currency: String(body.currency ?? "USD"),
        grossPay: Number(body.grossPay ?? 0),
        manualDeductions: Array.isArray(body.manualDeductions)
          ? body.manualDeductions.map((d) => {
            const row = d as Record<string, unknown>;
            return {
              label: String(row.label ?? "Deduction"),
              amount: Number(row.amount ?? 0),
            };
          })
          : [],
        netPay: Number(body.netPay ?? 0),
      });
    } else if (kind === "id_card") {
      bytes = await buildIdCardPdf({
        storeName: String(body.storeName ?? "Nissan GTR Auto"),
        fullName: String(body.fullName ?? ""),
        roleTitle: String(body.roleTitle ?? ""),
        staffRole: (body.staffRole as string) ?? null,
        employeeCode: String(body.employeeCode ?? ""),
        verifyUrl: (body.verifyUrl as string) ?? null,
      });
    } else if (kind === "business_card") {
      bytes = await buildBusinessCardPdf({
        storeName: String(body.storeName ?? "Nissan GTR Auto"),
        fullName: String(body.fullName ?? ""),
        roleTitle: String(body.roleTitle ?? ""),
        employeeCode: String(body.employeeCode ?? ""),
        phone: (body.phone as string) ?? null,
        email: (body.email as string) ?? null,
        domain: (body.domain as string) ?? "nissangtrauto.co.zw",
      });
    } else {
      return jsonResponse(
        { error: "kind must be statement|payslip|id_card|business_card" },
        400,
        cors,
      );
    }
  } catch (e) {
    console.error("render-branded-doc:", e);
    return jsonResponse({ error: "render failed" }, 500, cors);
  }

  return new Response(bytes, {
    status: 200,
    headers: {
      ...cors,
      "Content-Type": "application/pdf",
      "Content-Disposition": `attachment; filename="gtr-${kind}.pdf"`,
    },
  });
});
