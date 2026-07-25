/**
 * Format catalog search hits for WhatsApp text + PDP deep-links.
 * PDP path matches apps/web: /parts/{oem}
 */

export type CatalogHit = Record<string, unknown>;

export type CatalogSearchPayload = {
  mode?: string;
  query?: string;
  results?: CatalogHit[];
};

const TOP_N = 5;

export function siteOrigin(siteUrlEnv: string | undefined | null): string {
  const raw =
    (siteUrlEnv?.trim() || "https://nissangtrauto.co.zw").replace(/\/$/, "");
  try {
    return new URL(raw).origin;
  } catch {
    return "https://nissangtrauto.co.zw";
  }
}

/** Absolute PDP URL: {origin}/parts/{encodeURIComponent(oem)} */
export function partDeepLink(origin: string, oem: string): string {
  const base = origin.replace(/\/$/, "");
  return `${base}/parts/${encodeURIComponent(oem.trim())}`;
}

function asString(v: unknown): string | null {
  if (typeof v === "string" && v.trim()) return v.trim();
  return null;
}

function collectPartLines(
  hits: CatalogHit[],
  origin: string,
  out: string[],
  seen: Set<string>,
): void {
  for (const hit of hits) {
    if (out.length >= TOP_N) return;
    const type = asString(hit.type) ?? "part";

    if (type === "part") {
      const oem = asString(hit.oem_part_number);
      if (!oem || seen.has(oem)) continue;
      seen.add(oem);
      const cat = asString(hit.category_name);
      const label = cat ? `${oem} (${cat})` : oem;
      out.push(`${out.length + 1}. ${label}\n${partDeepLink(origin, oem)}`);
      continue;
    }

    if (type === "vehicle") {
      const model = asString(hit.model_variant) ?? "Vehicle";
      const chassis = asString(hit.chassis_code);
      const year = hit.production_year != null ? String(hit.production_year) : null;
      const header = [model, chassis, year].filter(Boolean).join(" · ");
      const fitments = Array.isArray(hit.fitments)
        ? (hit.fitments as CatalogHit[])
        : [];
      if (fitments.length === 0) {
        if (out.length < TOP_N) {
          out.push(`${out.length + 1}. ${header}`);
        }
        continue;
      }
      for (const f of fitments) {
        if (out.length >= TOP_N) return;
        const oem = asString(f.oem_part_number);
        if (!oem || seen.has(oem)) continue;
        seen.add(oem);
        out.push(
          `${out.length + 1}. ${oem} — ${header}\n${partDeepLink(origin, oem)}`,
        );
      }
      continue;
    }

    if (type === "pnc") {
      const pnc = asString(hit.pnc_code) ?? "PNC";
      const cat = asString(hit.category_name);
      const header = cat ? `${pnc} · ${cat}` : pnc;
      const fitments = Array.isArray(hit.fitments)
        ? (hit.fitments as CatalogHit[])
        : [];
      if (fitments.length === 0) {
        if (out.length < TOP_N) {
          out.push(`${out.length + 1}. ${header}`);
        }
        continue;
      }
      for (const f of fitments) {
        if (out.length >= TOP_N) return;
        const oem = asString(f.oem_part_number);
        if (!oem || seen.has(oem)) continue;
        seen.add(oem);
        out.push(
          `${out.length + 1}. ${oem} — ${header}\n${partDeepLink(origin, oem)}`,
        );
      }
    }
  }
}

export function formatHandoffMessage(handoffNumber: string): string {
  const digits = handoffNumber.replace(/\D/g, "");
  const display = digits ? `+${digits}` : handoffNumber.trim();
  return (
    `Connecting you with our counter team.\n` +
    `Please call or WhatsApp ${display} — they can look up stock and quote.`
  );
}

export function formatRateLimitMessage(retryAfterSeconds: number): string {
  const secs = Math.max(1, retryAfterSeconds);
  return (
    `Too many searches — please wait about ${secs}s and try again.\n` +
    `Need a person now? Reply: agent`
  );
}

/**
 * Build outbound reply from search_catalog JSON.
 * Empty results → null (caller sends handoff).
 */
export function formatSearchReply(
  payload: CatalogSearchPayload,
  origin: string,
): string | null {
  const results = Array.isArray(payload.results) ? payload.results : [];
  const lines: string[] = [];
  const seen = new Set<string>();
  collectPartLines(results, origin, lines, seen);

  if (lines.length === 0) return null;

  const mode = asString(payload.mode) ?? "part";
  const query = asString(payload.query) ?? "";
  const header = query
    ? `Top hits (${mode}: ${query}):`
    : `Top hits (${mode}):`;

  return (
    `${header}\n\n${lines.join("\n\n")}\n\n` +
    `Open a link for details, or reply with another search.\n` +
    `Need a person? Reply: agent`
  );
}
