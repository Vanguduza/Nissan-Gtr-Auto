import type { SupabaseClient } from "@gtr/supabase-client";

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
};

const MODES: SearchMode[] = ["part", "vin", "model", "pnc"];

export function isSearchMode(value: string): value is SearchMode {
  return (MODES as readonly string[]).includes(value);
}

export function normalizeSearchMode(value: string | undefined): SearchMode {
  return value && isSearchMode(value) ? value : "part";
}

function parseSearchCatalogResponse(raw: unknown): SearchCatalogResponse | null {
  if (!raw || typeof raw !== "object") return null;
  const obj = raw as Record<string, unknown>;
  const mode = typeof obj.mode === "string" ? obj.mode : "";
  const query = typeof obj.query === "string" ? obj.query : "";
  const results = Array.isArray(obj.results) ? (obj.results as SearchResult[]) : [];
  if (!isSearchMode(mode)) return null;
  return { mode, query, results };
}

export async function searchCatalog(
  client: SupabaseClient,
  mode: SearchMode,
  query: string,
): Promise<
  | { ok: true; data: SearchCatalogResponse }
  | { ok: false; error: string }
> {
  const { data, error } = await client.rpc("search_catalog", {
    p_mode: mode,
    p_query: query,
  });

  if (error) {
    return { ok: false, error: error.message };
  }

  const parsed = parseSearchCatalogResponse(data);
  if (!parsed) {
    return { ok: false, error: "Unexpected search response shape." };
  }

  return { ok: true, data: parsed };
}

export function partHref(oem: string | null | undefined): string | null {
  if (!oem?.trim()) return null;
  return `/parts/${encodeURIComponent(oem.trim())}`;
}
