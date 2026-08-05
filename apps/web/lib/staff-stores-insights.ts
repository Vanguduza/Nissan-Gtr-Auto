import type { SupabaseClient } from "@gtr/supabase-client";
import type { StorefrontResult } from "@/lib/customer-storefront";

export type StoresDirective = {
  action: string;
  oem?: string;
  suggested_qty?: number | null;
  rationale: string;
};

export type StoresInsightsResult = {
  kpis: Record<string, unknown> | null;
  directives: StoresDirective[];
  narrative: string | null;
  gemini_used: boolean;
  error: string | null;
  directive_id: string | null;
  numericOnly: boolean;
};

function asRecord(v: unknown): Record<string, unknown> | null {
  return v && typeof v === "object" && !Array.isArray(v)
    ? (v as Record<string, unknown>)
    : null;
}

function parseStoresBody(raw: unknown): StoresInsightsResult | null {
  const rec = asRecord(raw);
  if (!rec || !("kpis" in rec)) return null;
  const directivesRaw = rec.directives;
  const directives: StoresDirective[] = Array.isArray(directivesRaw)
    ? directivesRaw
      .filter((d) => d && typeof d === "object")
      .map((d) => {
        const row = d as Record<string, unknown>;
        return {
          action: String(row.action ?? "other"),
          oem: typeof row.oem === "string" ? row.oem : undefined,
          suggested_qty:
            typeof row.suggested_qty === "number" ? row.suggested_qty : null,
          rationale: String(row.rationale ?? ""),
        };
      })
    : [];
  const error = rec.error != null ? String(rec.error) : null;
  const narrative =
    typeof rec.narrative === "string" ? rec.narrative : null;
  return {
    kpis: asRecord(rec.kpis),
    directives,
    narrative,
    gemini_used: Boolean(rec.gemini_used),
    error,
    directive_id:
      typeof rec.directive_id === "string" ? rec.directive_id : null,
    numericOnly: !narrative || error === "gemini_unavailable",
  };
}

export async function listActiveWarehouses(
  client: SupabaseClient,
): Promise<StorefrontResult<{ id: string; code: string; name: string }[]>> {
  const { data, error } = await client
    .from("warehouses")
    .select("id, code, name")
    .eq("is_active", true)
    .eq("is_quarantine", false)
    .order("code")
    .limit(50);
  if (error) return { ok: false, error: error.message };
  return {
    ok: true,
    data: (data as { id: string; code: string; name: string }[]) ?? [],
  };
}

export async function fetchStoresInsights(
  client: SupabaseClient,
  args: {
    warehouseId: string;
    includeDirectives?: boolean;
    runAbc?: boolean;
  },
): Promise<StorefrontResult<StoresInsightsResult>> {
  const edge = await client.functions.invoke("stores-insights", {
    body: {
      warehouse_id: args.warehouseId,
      include_directives: args.includeDirectives !== false,
      run_abc: args.runAbc !== false,
    },
  });

  const fromData = parseStoresBody(edge.data);
  if (fromData) return { ok: true, data: fromData };

  if (edge.error) {
    const msg = edge.error.message || "stores-insights failed";
    return { ok: false, error: msg };
  }
  return { ok: false, error: "stores-insights returned no KPI payload." };
}
