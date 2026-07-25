/**
 * Gemini narrative for staff KPI aggregates (no PII).
 * Env: GEMINI_API_KEY — never log the key value.
 * Fail closed: callers treat missing key as narrative unavailable.
 */

const DEFAULT_MODEL = "gemini-2.0-flash";
const GEMINI_BASE =
  "https://generativelanguage.googleapis.com/v1beta/models";

export type GeminiNarrativeResult = {
  narrative: string | null;
  gemini_used: boolean;
  error: string | null;
};

export function getGeminiApiKey(): string | null {
  const key = Deno.env.get("GEMINI_API_KEY")?.trim() ?? "";
  return key || null;
}

export async function generateKpiNarrative(
  kpis: unknown,
  opts?: { model?: string },
): Promise<GeminiNarrativeResult> {
  const apiKey = getGeminiApiKey();
  if (!apiKey) {
    return {
      narrative: null,
      gemini_used: false,
      error: "gemini_unavailable",
    };
  }

  const model =
    opts?.model?.trim() ||
    Deno.env.get("GEMINI_MODEL")?.trim() ||
    DEFAULT_MODEL;

  const prompt =
    "You are an ops analyst for a Nissan spare-parts distributor (USD/ZIG). " +
    "Write a concise staff briefing (max 250 words) from the JSON KPI aggregates only. " +
    "Cover sales, returns/credit notes, top SKUs, inventory risk, AR aging, credit holds, and open deliveries. " +
    "Do not invent customer names, phone numbers, emails, invoice numbers, or journal details. " +
    "If a section is empty, say so briefly. Use plain text paragraphs, no markdown tables.\n\n" +
    `KPI JSON:\n${JSON.stringify(kpis)}`;

  try {
    const url =
      `${GEMINI_BASE}/${encodeURIComponent(model)}:generateContent` +
      `?key=${encodeURIComponent(apiKey)}`;
    const res = await fetch(url, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        contents: [{ role: "user", parts: [{ text: prompt }] }],
        generationConfig: {
          temperature: 0.3,
          maxOutputTokens: 1024,
        },
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
        narrative: null,
        gemini_used: false,
        error: `gemini_http_${res.status}`,
      };
    }
    const narrative = extractGeminiText(raw);
    if (!narrative) {
      return {
        narrative: null,
        gemini_used: false,
        error: "gemini_empty_response",
      };
    }
    return { narrative, gemini_used: true, error: null };
  } catch (e) {
    console.error("gemini: request error", String(e));
    return {
      narrative: null,
      gemini_used: false,
      error: "gemini_request_failed",
    };
  }
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
