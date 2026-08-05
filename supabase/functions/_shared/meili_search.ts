/**
 * Meilisearch catalog search helpers — server-side only (Edge Function).
 * Maps Meili hits → search_catalog-compatible JSON for storefront typeahead.
 */

export type SearchMode = "part" | "vin" | "model" | "pnc";

export type PartHit = {
  type: "part";
  oem_part_number: string;
  pnc_code?: string | null;
  chassis_code?: string | null;
  engine_code?: string | null;
  superseded_by?: string | null;
  diagram_path?: string | null;
  category_name?: string | null;
  subcategory_name?: string | null;
  matched_oe_number?: string | null;
  matched_brand?: string | null;
};

export type VehicleHit = {
  type: "vehicle";
  vin_prefix?: string | null;
  model_variant?: string | null;
  chassis_code?: string | null;
  engine_code?: string | null;
  production_year?: number | null;
  fitments?: PartHit[];
};

export type PncHit = {
  type: "pnc";
  pnc_code: string;
  category_name?: string | null;
  subcategory_name?: string | null;
  fitments?: PartHit[];
};

export type SearchResult = PartHit | VehicleHit | PncHit;

export type SearchCatalogResponse = {
  mode: SearchMode;
  query: string;
  results: SearchResult[];
  backend?: "meili" | "fts";
  facetDistribution?: Record<string, Record<string, number>>;
};

const MODES: SearchMode[] = ["part", "vin", "model", "pnc"];

export function isSearchMode(value: string): value is SearchMode {
  return (MODES as readonly string[]).includes(value);
}

export function meiliConfig(): { host: string; searchKey: string } | null {
  const host = Deno.env.get("MEILI_HOST")?.trim();
  const searchKey =
    Deno.env.get("MEILI_SEARCH_KEY")?.trim() ||
    Deno.env.get("MEILI_MASTER_KEY")?.trim();
  if (!host || !searchKey) return null;
  return { host, searchKey };
}

export function catalogSearchBackend(): "meili" | "fts" {
  const raw = (Deno.env.get("CATALOG_SEARCH_BACKEND") ?? "meili").toLowerCase();
  return raw === "fts" ? "fts" : "meili";
}

function docKindForMode(mode: SearchMode): string {
  if (mode === "part") return "part";
  if (mode === "pnc") return "pnc";
  return "vehicle";
}

function mapHit(mode: SearchMode, doc: Record<string, unknown>, query: string): SearchResult {
  if (mode === "part") {
    const oeNumbers = Array.isArray(doc.oe_numbers)
      ? (doc.oe_numbers as string[])
      : [];
    const qUpper = query.toUpperCase();
    const matchedOe = oeNumbers.find((oe) => oe.toUpperCase() === qUpper) ?? null;
    return {
      type: "part",
      oem_part_number: String(doc.oem_part_number ?? ""),
      pnc_code: (doc.pnc_code as string | null) ?? null,
      chassis_code: (doc.chassis_code as string | null) ?? null,
      engine_code: (doc.engine_code as string | null) ?? null,
      superseded_by: (doc.superseded_by as string | null) ?? null,
      diagram_path: (doc.diagram_path as string | null) ?? null,
      category_name: (doc.category_name as string | null) ?? null,
      subcategory_name: (doc.subcategory_name as string | null) ?? null,
      matched_oe_number: matchedOe,
      matched_brand: matchedOe ? "cross-ref" : null,
    };
  }
  if (mode === "pnc") {
    return {
      type: "pnc",
      pnc_code: String(doc.pnc_code ?? ""),
      category_name: (doc.category_name as string | null) ?? null,
      subcategory_name: (doc.subcategory_name as string | null) ?? null,
    };
  }
  return {
    type: "vehicle",
    vin_prefix: (doc.vin_prefix as string | null) ?? null,
    model_variant: (doc.model_variant as string | null) ?? null,
    chassis_code: (doc.chassis_code as string | null) ?? null,
    engine_code: (doc.engine_code as string | null) ?? null,
    production_year:
      typeof doc.production_year === "number" ? doc.production_year : null,
  };
}

export async function searchMeiliCatalog(
  mode: SearchMode,
  query: string,
  options: { limit?: number; facets?: string[] } = {},
): Promise<SearchCatalogResponse> {
  const cfg = meiliConfig();
  if (!cfg) {
    throw new Error("MEILI_HOST and MEILI_SEARCH_KEY (or MEILI_MASTER_KEY) required");
  }

  const trimmed = query.trim();
  const limit = Math.min(Math.max(options.limit ?? 20, 1), 50);
  const facetFields = allowlistedFacets(
    options.facets ?? defaultFacetsForMode(mode),
  );

  const body: Record<string, unknown> = {
    q: trimmed,
    limit,
    filter: `doc_kind = "${docKindForMode(mode)}"`,
  };
  if (facetFields.length) {
    body.facets = facetFields;
  }

  const resp = await fetch(
    `${cfg.host.replace(/\/$/, "")}/indexes/parts/search`,
    {
      method: "POST",
      headers: {
        Authorization: `Bearer ${cfg.searchKey}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify(body),
    },
  );

  if (!resp.ok) {
    const detail = await resp.text();
    throw new Error(`Meili search failed (${resp.status}): ${detail}`);
  }

  const payload = await resp.json();
  const hits = Array.isArray(payload.hits) ? payload.hits : [];
  const results = hits.map((doc: Record<string, unknown>) =>
    mapHit(mode, doc, trimmed)
  );

  return {
    mode,
    query: trimmed,
    results,
    backend: "meili",
    facetDistribution: payload.facetDistribution ?? undefined,
  };
}

const ALLOWED_FACETS = new Set([
  "category_name",
  "pnc_code",
  "chassis_code",
  "model_variant",
]);

function allowlistedFacets(fields: string[]): string[] {
  return fields.filter((f) => ALLOWED_FACETS.has(f));
}

function defaultFacetsForMode(mode: SearchMode): string[] {
  switch (mode) {
    case "part":
      return ["category_name", "pnc_code"];
    case "model":
      return ["model_variant", "chassis_code"];
    case "pnc":
      return ["category_name"];
    case "vin":
      return ["chassis_code"];
    default:
      return [];
  }
}

export async function searchCatalogFtsFallback(
  supabase: {
    rpc: (
      fn: string,
      args: Record<string, unknown>,
    ) => Promise<{ data: unknown; error: { message: string } | null }>;
  },
  mode: SearchMode,
  query: string,
): Promise<SearchCatalogResponse> {
  const { data, error } = await supabase.rpc("search_catalog", {
    p_mode: mode,
    p_query: query.trim(),
  });
  if (error) {
    throw new Error(error.message);
  }
  const raw = data as Record<string, unknown> | null;
  return {
    mode: (raw?.mode as SearchMode) ?? mode,
    query: String(raw?.query ?? query),
    results: Array.isArray(raw?.results) ? (raw!.results as SearchResult[]) : [],
    backend: "fts",
  };
}
