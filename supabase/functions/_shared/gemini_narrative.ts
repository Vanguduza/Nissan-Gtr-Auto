/**
 * Gemini helpers for staff KPI narratives, CRM promo copy, stores directives.
 * Env: GEMINI_API_KEY — never log the key value.
 * Fail closed: callers treat missing key as unavailable.
 * Staff finance/ops: aggregates only (no PII). CRM promo: opt-in name/vehicle/OEM only.
 */

const DEFAULT_MODEL = "gemini-2.0-flash";
const GEMINI_BASE =
  "https://generativelanguage.googleapis.com/v1beta/models";

export type GeminiNarrativeResult = {
  narrative: string | null;
  gemini_used: boolean;
  error: string | null;
};

export type GeminiJsonResult = {
  data: unknown | null;
  text: string | null;
  gemini_used: boolean;
  error: string | null;
};

export function getGeminiApiKey(): string | null {
  const key = Deno.env.get("GEMINI_API_KEY")?.trim() ?? "";
  return key || null;
}

async function callGemini(
  prompt: string,
  opts?: {
    model?: string;
    temperature?: number;
    maxOutputTokens?: number;
    json?: boolean;
  },
): Promise<GeminiJsonResult> {
  const apiKey = getGeminiApiKey();
  if (!apiKey) {
    return {
      data: null,
      text: null,
      gemini_used: false,
      error: "gemini_unavailable",
    };
  }

  const model =
    opts?.model?.trim() ||
    Deno.env.get("GEMINI_MODEL")?.trim() ||
    DEFAULT_MODEL;

  try {
    const url =
      `${GEMINI_BASE}/${encodeURIComponent(model)}:generateContent` +
      `?key=${encodeURIComponent(apiKey)}`;
    const generationConfig: Record<string, unknown> = {
      temperature: opts?.temperature ?? 0.3,
      maxOutputTokens: opts?.maxOutputTokens ?? 1024,
    };
    if (opts?.json) {
      generationConfig.responseMimeType = "application/json";
    }
    const res = await fetch(url, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        contents: [{ role: "user", parts: [{ text: prompt }] }],
        generationConfig,
      }),
    });
    const text = await res.text();
    let raw: unknown = text;
    try {
      raw = JSON.parse(text);
    } catch {
      /* keep text */
    }
    if (!res.ok) {
      console.error(
        "gemini: generateContent failed",
        res.status,
        typeof text === "string" ? text.slice(0, 200) : "",
      );
      return {
        data: null,
        text: null,
        gemini_used: false,
        error: `gemini_http_${res.status}`,
      };
    }
    const extracted = extractGeminiText(raw);
    if (!extracted) {
      return {
        data: null,
        text: null,
        gemini_used: false,
        error: "gemini_empty_response",
      };
    }
    if (opts?.json) {
      try {
        return {
          data: JSON.parse(extracted),
          text: extracted,
          gemini_used: true,
          error: null,
        };
      } catch {
        return {
          data: null,
          text: extracted,
          gemini_used: false,
          error: "gemini_invalid_json",
        };
      }
    }
    return {
      data: null,
      text: extracted,
      gemini_used: true,
      error: null,
    };
  } catch (e) {
    console.error("gemini: request error", String(e));
    return {
      data: null,
      text: null,
      gemini_used: false,
      error: "gemini_request_failed",
    };
  }
}

export async function generateKpiNarrative(
  kpis: unknown,
  opts?: { model?: string; mode?: "ops" | "finance" },
): Promise<GeminiNarrativeResult> {
  const mode = opts?.mode ?? "ops";
  const prompt = mode === "finance"
    ? "You are a finance analyst for a Nissan spare-parts distributor (USD/ZIG, tax-agnostic). " +
      "Write a concise staff briefing (max 250 words) from the JSON KPI aggregates only. " +
      "Highlight net profit/margin (USD equiv), top expense accounts, sales totals by currency, and AR aging. " +
      "Do not invent customer names, phones, emails, invoice numbers, or journal line details. " +
      "Plain text paragraphs only.\n\n" +
      `KPI JSON:\n${JSON.stringify(kpis)}`
    : "You are an ops analyst for a Nissan spare-parts distributor (USD/ZIG). " +
      "Write a concise staff briefing (max 250 words) from the JSON KPI aggregates only. " +
      "Cover sales, returns/credit notes, top SKUs, inventory risk, AR aging, credit holds, and open deliveries. " +
      "Do not invent customer names, phone numbers, emails, invoice numbers, or journal details. " +
      "If a section is empty, say so briefly. Use plain text paragraphs, no markdown tables.\n\n" +
      `KPI JSON:\n${JSON.stringify(kpis)}`;

  const result = await callGemini(prompt, {
    model: opts?.model,
    temperature: 0.3,
    maxOutputTokens: 1024,
  });
  return {
    narrative: result.text,
    gemini_used: result.gemini_used,
    error: result.error,
  };
}

export type PromoCopyInput = {
  display_name: string;
  vehicle_label?: string | null;
  oem_skus: string[];
};

export async function generatePromoCopy(
  input: PromoCopyInput,
  opts?: { model?: string },
): Promise<GeminiNarrativeResult> {
  const prompt =
    "You write short promotional SMS/email copy for Nissan GTR Auto (spare parts, Zimbabwe). " +
    "Output plain text only (max 320 chars for SMS-friendly). Mention the customer first name/display name, " +
    "their vehicle if provided, and list the OEM part numbers as suggestions — do not invent prices, " +
    "discounts, tax, or fiscal QR claims. No markdown.\n\n" +
    `Payload:\n${JSON.stringify(input)}`;

  const result = await callGemini(prompt, {
    model: opts?.model,
    temperature: 0.5,
    maxOutputTokens: 256,
  });
  return {
    narrative: result.text,
    gemini_used: result.gemini_used,
    error: result.error,
  };
}

export function templatePromoCopy(input: PromoCopyInput): string {
  const name = input.display_name?.trim() || "Customer";
  const vehicle = input.vehicle_label?.trim();
  const oems = (input.oem_skus ?? []).filter(Boolean).slice(0, 5);
  const oemPart = oems.length
    ? ` Suggested OEMs: ${oems.join(", ")}.`
    : "";
  const vehiclePart = vehicle ? ` for your ${vehicle}` : "";
  return (
    `Hi ${name}, Nissan GTR Auto here — it's been a while${vehiclePart}.` +
    `${oemPart} Reply or visit nissangtrauto.co.zw when you need parts.`
  );
}

export type StoresDirective = {
  action: "restock" | "clearance_bundle" | "watch" | "other";
  oem?: string;
  suggested_qty?: number | null;
  rationale: string;
};

export async function generateStoresDirectives(
  kpis: unknown,
  opts?: { model?: string },
): Promise<
  GeminiJsonResult & { directives: StoresDirective[]; narrative: string | null }
> {
  const prompt =
    "You are an inventory planner for a Nissan spare-parts distributor (USD/ZIG). " +
    "Given ABC tiers, on-hand, and open forecast suggestions, return JSON only with shape: " +
    '{"narrative":"string max 200 words","directives":[{"action":"restock|clearance_bundle|watch|other","oem":"OEM or null","suggested_qty":number|null,"rationale":"string"}]}. ' +
    "Max 12 directives. Prefer restock for Tier A low on-hand; clearance_bundle for Tier C high on-hand. " +
    "Do not invent OEMs not present in the JSON. No prices, tax, or fiscal content.\n\n" +
    `KPI JSON:\n${JSON.stringify(kpis)}`;

  const result = await callGemini(prompt, {
    model: opts?.model,
    temperature: 0.2,
    maxOutputTokens: 1536,
    json: true,
  });

  const directives = normalizeDirectives(result.data);
  const narrative =
    result.data && typeof result.data === "object" &&
      typeof (result.data as { narrative?: unknown }).narrative === "string"
      ? (result.data as { narrative: string }).narrative.trim()
      : result.text;

  return { ...result, directives, narrative };
}

function normalizeDirectives(data: unknown): StoresDirective[] {
  if (!data || typeof data !== "object") return [];
  const raw = (data as { directives?: unknown }).directives;
  if (!Array.isArray(raw)) return [];
  const out: StoresDirective[] = [];
  for (const item of raw.slice(0, 12)) {
    if (!item || typeof item !== "object") continue;
    const actionRaw = String((item as { action?: unknown }).action ?? "other");
    const action =
      actionRaw === "restock" || actionRaw === "clearance_bundle" ||
          actionRaw === "watch"
        ? actionRaw
        : "other";
    const oemVal = (item as { oem?: unknown }).oem;
    const oem = typeof oemVal === "string" && oemVal.trim()
      ? oemVal.trim()
      : undefined;
    const qtyRaw = (item as { suggested_qty?: unknown }).suggested_qty;
    let suggested_qty: number | null = null;
    if (typeof qtyRaw === "number" && Number.isFinite(qtyRaw) && qtyRaw > 0) {
      suggested_qty = qtyRaw;
    }
    const rationale = String(
      (item as { rationale?: unknown }).rationale ?? "",
    ).trim() || "n/a";
    out.push({ action, oem, suggested_qty, rationale });
  }
  return out;
}

function extractGeminiText(raw: unknown): string | null {
  if (!raw || typeof raw !== "object") return null;
  const candidates = (raw as { candidates?: unknown }).candidates;
  if (!Array.isArray(candidates) || !candidates.length) return null;
  const content = (candidates[0] as { content?: { parts?: unknown } })
    ?.content;
  const parts = content?.parts;
  if (!Array.isArray(parts)) return null;
  const texts = parts
    .map((p) =>
      p && typeof p === "object" && typeof (p as { text?: unknown }).text ===
          "string"
        ? (p as { text: string }).text.trim()
        : "",
    )
    .filter(Boolean);
  return texts.length ? texts.join("\n").trim() : null;
}
