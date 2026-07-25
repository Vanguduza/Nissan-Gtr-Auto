import type {
  AiDeliveryChannel,
  AiReportCadence,
  AiReportSubscriptionRow,
  SupabaseClient,
} from "@gtr/supabase-client";
import {
  requireSession,
  type StorefrontResult,
} from "@/lib/customer-storefront";

export { requireSession };
export type { AiDeliveryChannel, AiReportCadence, AiReportSubscriptionRow };

export const KPI_SET_OPS_SALES_V1 = "ops_sales_v1" as const;

export type CurrencyBucket = {
  currency: string;
  invoice_count?: number;
  revenue?: number;
  subtotal?: number;
  average_order_value?: number;
  credit_note_count?: number;
  returns_total?: number;
  customer_count?: number;
  open_balance?: number;
  invoice_count_hold?: number;
  total?: number;
  open_amount?: number;
  bucket?: string;
};

export type TopSkuRow = {
  oem: string | null;
  qty: number;
  currency: string;
  revenue: number;
};

export type OpsSalesKpis = {
  kpi_set?: string;
  period_from?: string;
  period_to?: string;
  sales?: {
    order_count?: number;
    by_currency?: CurrencyBucket[];
    average_order_value?: CurrencyBucket[];
  };
  returns?: {
    credit_note_count?: number;
    by_currency?: CurrencyBucket[];
  };
  top_skus?: {
    items?: TopSkuRow[];
    limit?: number;
  };
  inventory?: {
    on_hand_qty?: number;
    low_stock_count?: number;
    stockout_count?: number;
    quarantine_qty?: number;
  };
  ar_aging?: {
    customers_with_open_balance?: number;
    customer_open_balance_by_currency?: CurrencyBucket[];
    invoice_aging_buckets?: CurrencyBucket[];
  };
  credit_holds?: {
    customers_on_credit_hold?: number;
    invoices_on_hold?: number;
    on_hold_by_currency?: CurrencyBucket[];
  };
  open_deliveries?: {
    open_delivery_notes_draft?: number;
    open_delivery_jobs?: number;
    jobs_by_status?: { status: string; job_count: number }[];
  };
};

export type AnalyticsInsightsResult = {
  kpis: OpsSalesKpis;
  narrative: string | null;
  gemini_used: boolean;
  error: string | null;
  /** True when KPIs returned but narrative unavailable (e.g. 422 gemini_unavailable). */
  numericOnly: boolean;
};

export type SubscriptionInput = {
  cadence: AiReportCadence;
  channels: AiDeliveryChannel[];
  recipientEmails: string[];
  recipientWhatsappE164: string[];
  includeNarrative: boolean;
  timezone?: string;
  active?: boolean;
  kpiSet?: string;
};

function asRecord(v: unknown): Record<string, unknown> | null {
  return v && typeof v === "object" && !Array.isArray(v)
    ? (v as Record<string, unknown>)
    : null;
}

function parseInsightsBody(raw: unknown): AnalyticsInsightsResult | null {
  const body = asRecord(raw);
  if (!body) return null;
  const kpis = asRecord(body.kpis);
  if (!kpis) return null;
  const error =
    typeof body.error === "string"
      ? body.error
      : body.error == null
        ? null
        : String(body.error);
  const narrative =
    typeof body.narrative === "string"
      ? body.narrative
      : body.narrative == null
        ? null
        : String(body.narrative);
  const gemini_used = Boolean(body.gemini_used);
  const numericOnly =
    !narrative ||
    error === "gemini_unavailable" ||
    (!gemini_used && Boolean(error));
  return {
    kpis: kpis as OpsSalesKpis,
    narrative,
    gemini_used,
    error,
    numericOnly,
  };
}

async function readFunctionsErrorBody(
  error: { context?: unknown; message?: string } | null,
): Promise<unknown> {
  if (!error) return null;
  const ctx = error.context as
    | Response
    | { json?: () => Promise<unknown>; body?: unknown }
    | null
    | undefined;
  if (!ctx) return null;
  if (typeof Response !== "undefined" && ctx instanceof Response) {
    try {
      return await ctx.clone().json();
    } catch {
      return null;
    }
  }
  if (typeof ctx.json === "function") {
    try {
      return await ctx.json();
    } catch {
      return null;
    }
  }
  if ("body" in ctx && ctx.body != null) return ctx.body;
  return null;
}

/** Date input YYYY-MM-DD → inclusive local-day ISO bounds for analytics-insights. */
export function dateInputToPeriodBounds(
  fromDate: string,
  toDate: string,
): StorefrontResult<{ from: string; to: string }> {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(fromDate) || !/^\d{4}-\d{2}-\d{2}$/.test(toDate)) {
    return { ok: false, error: "Use YYYY-MM-DD dates." };
  }
  const from = new Date(`${fromDate}T00:00:00`);
  const toExclusive = new Date(`${toDate}T00:00:00`);
  toExclusive.setDate(toExclusive.getDate() + 1);
  if (Number.isNaN(from.getTime()) || Number.isNaN(toExclusive.getTime())) {
    return { ok: false, error: "Invalid date range." };
  }
  if (toExclusive <= from) {
    return { ok: false, error: "End date must be on or after start date." };
  }
  return {
    ok: true,
    data: { from: from.toISOString(), to: toExclusive.toISOString() },
  };
}

export function monthStartInput(d = new Date()): string {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-01`;
}

export function todayInput(d = new Date()): string {
  return d.toISOString().slice(0, 10);
}

/**
 * Staff JWT → POST analytics-insights.
 * 422 + gemini_unavailable still returns KPIs (numeric-only banner).
 */
export async function fetchAnalyticsInsights(
  client: SupabaseClient,
  args: {
    from: string;
    to: string;
    includeNarrative?: boolean;
    kpiSet?: string;
  },
): Promise<StorefrontResult<AnalyticsInsightsResult>> {
  const edge = await client.functions.invoke("analytics-insights", {
    body: {
      from: args.from,
      to: args.to,
      kpi_set: args.kpiSet ?? KPI_SET_OPS_SALES_V1,
      include_narrative: args.includeNarrative !== false,
    },
  });

  const fromData = parseInsightsBody(edge.data);
  if (fromData) {
    return { ok: true, data: fromData };
  }

  if (edge.error) {
    const errBody = await readFunctionsErrorBody(edge.error);
    const fromErr = parseInsightsBody(errBody);
    if (fromErr) {
      return { ok: true, data: fromErr };
    }
    const msg =
      asRecord(errBody)?.error != null
        ? String(asRecord(errBody)!.error)
        : edge.error.message || "analytics-insights failed";
    return { ok: false, error: msg };
  }

  return { ok: false, error: "analytics-insights returned no KPI payload." };
}

export async function listAiReportSubscriptions(
  client: SupabaseClient,
): Promise<StorefrontResult<AiReportSubscriptionRow[]>> {
  const { data, error } = await client
    .from("ai_report_subscriptions")
    .select(
      "id, created_by, cadence, channels, recipient_emails, recipient_whatsapp_e164, include_narrative, kpi_set, timezone, active, last_run_at, created_at, updated_at",
    )
    .order("created_at", { ascending: false })
    .limit(100);
  if (error) return { ok: false, error: error.message };
  return { ok: true, data: (data as AiReportSubscriptionRow[]) ?? [] };
}

function normalizeEmails(raw: string[]): string[] {
  return [
    ...new Set(
      raw
        .map((e) => e.trim().toLowerCase())
        .filter((e) => e.length > 0 && e.includes("@")),
    ),
  ];
}

function normalizeWhatsapp(raw: string[]): string[] {
  return [
    ...new Set(
      raw
        .map((e) => e.trim().replace(/\s+/g, ""))
        .filter((e) => /^\+?[1-9]\d{6,14}$/.test(e))
        .map((e) => (e.startsWith("+") ? e : `+${e}`)),
    ),
  ];
}

function validateSubscriptionInput(
  input: SubscriptionInput,
): StorefrontResult<true> {
  if (input.channels.length < 1) {
    return { ok: false, error: "Select at least one channel." };
  }
  const emails = normalizeEmails(input.recipientEmails);
  const wa = normalizeWhatsapp(input.recipientWhatsappE164);
  if (emails.length < 1 && wa.length < 1) {
    return {
      ok: false,
      error: "Provide at least one email or WhatsApp (E.164) recipient.",
    };
  }
  if (input.channels.includes("email") && emails.length < 1) {
    return { ok: false, error: "Email channel needs at least one recipient." };
  }
  if (input.channels.includes("whatsapp") && wa.length < 1) {
    return {
      ok: false,
      error: "WhatsApp channel needs at least one E.164 number.",
    };
  }
  return { ok: true, data: true };
}

export async function createAiReportSubscription(
  client: SupabaseClient,
  input: SubscriptionInput,
): Promise<StorefrontResult<string>> {
  const check = validateSubscriptionInput(input);
  if (!check.ok) return check;

  const session = await requireSession(client);
  if (!session.ok) return session;

  const emails = normalizeEmails(input.recipientEmails);
  const wa = normalizeWhatsapp(input.recipientWhatsappE164);

  const { data, error } = await client
    .from("ai_report_subscriptions")
    .insert({
      created_by: session.data.userId,
      cadence: input.cadence,
      channels: input.channels,
      recipient_emails: emails,
      recipient_whatsapp_e164: wa,
      include_narrative: input.includeNarrative,
      kpi_set: input.kpiSet ?? KPI_SET_OPS_SALES_V1,
      timezone: input.timezone?.trim() || "Africa/Harare",
      active: input.active !== false,
    })
    .select("id")
    .single();

  if (error) return { ok: false, error: error.message };
  if (!data?.id) return { ok: false, error: "Insert returned no id." };
  return { ok: true, data: data.id };
}

export async function updateAiReportSubscription(
  client: SupabaseClient,
  id: string,
  input: SubscriptionInput,
): Promise<StorefrontResult<true>> {
  const check = validateSubscriptionInput(input);
  if (!check.ok) return check;

  const emails = normalizeEmails(input.recipientEmails);
  const wa = normalizeWhatsapp(input.recipientWhatsappE164);

  const { error } = await client
    .from("ai_report_subscriptions")
    .update({
      cadence: input.cadence,
      channels: input.channels,
      recipient_emails: emails,
      recipient_whatsapp_e164: wa,
      include_narrative: input.includeNarrative,
      kpi_set: input.kpiSet ?? KPI_SET_OPS_SALES_V1,
      timezone: input.timezone?.trim() || "Africa/Harare",
      active: input.active !== false,
      updated_at: new Date().toISOString(),
    })
    .eq("id", id);

  if (error) return { ok: false, error: error.message };
  return { ok: true, data: true };
}

export async function deactivateAiReportSubscription(
  client: SupabaseClient,
  id: string,
): Promise<StorefrontResult<true>> {
  const { error } = await client
    .from("ai_report_subscriptions")
    .update({ active: false, updated_at: new Date().toISOString() })
    .eq("id", id);
  if (error) return { ok: false, error: error.message };
  return { ok: true, data: true };
}

export async function activateAiReportSubscription(
  client: SupabaseClient,
  id: string,
): Promise<StorefrontResult<true>> {
  const { error } = await client
    .from("ai_report_subscriptions")
    .update({ active: true, updated_at: new Date().toISOString() })
    .eq("id", id);
  if (error) return { ok: false, error: error.message };
  return { ok: true, data: true };
}

export function formatMoney(amount: number | undefined, currency: string): string {
  if (amount == null || !Number.isFinite(Number(amount))) return "—";
  return `${Number(amount).toLocaleString(undefined, {
    maximumFractionDigits: 2,
  })} ${currency}`;
}

export function splitRecipientField(raw: string): string[] {
  return raw
    .split(/[,;\n]+/)
    .map((s) => s.trim())
    .filter(Boolean);
}
