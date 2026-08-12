import type { SupabaseClient } from "@supabase/supabase-js";
import type { Database } from "./database.types";

/** Edge function path (relative to Supabase project URL). */
export const CATALOG_SEARCH_MEILI_FN = "catalog-search-meili" as const;

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

export type CatalogSearchArgs = {
  mode: SearchMode;
  query: string;
  limit?: number;
  facets?: string[];
};

/** Postgres FTS RPC — always available fallback. */
export const SEARCH_CATALOG_RPC = "search_catalog" as const;

export function searchCatalogRpcArgs(mode: SearchMode, query: string) {
  return { p_mode: mode, p_query: query.trim() } as const;
}

/**
 * Meili-backed search via Edge Function proxy (JWT forwarded; Meili key stays server-side).
 * Falls back to [searchCatalogFts] when the function is unreachable.
 */
export async function searchCatalogMeili(
  client: SupabaseClient<Database>,
  args: CatalogSearchArgs,
): Promise<
  | { ok: true; data: SearchCatalogResponse }
  | { ok: false; error: string }
> {
  const trimmed = args.query.trim();
  if (!trimmed) {
    return {
      ok: true,
      data: { mode: args.mode, query: "", results: [] },
    };
  }

  const { data, error } = await client.functions.invoke(CATALOG_SEARCH_MEILI_FN, {
    body: {
      mode: args.mode,
      query: trimmed,
      limit: args.limit,
      facets: args.facets,
    },
  });

  if (error) {
    return searchCatalogFts(client, args.mode, trimmed);
  }

  const parsed = parseSearchCatalogResponse(data);
  if (!parsed) {
    return searchCatalogFts(client, args.mode, trimmed);
  }
  return { ok: true, data: { ...parsed, backend: parsed.backend ?? "meili" } };
}

/** Direct Postgres FTS — interim / offline fallback. */
export async function searchCatalogFts(
  client: SupabaseClient<Database>,
  mode: SearchMode,
  query: string,
): Promise<
  | { ok: true; data: SearchCatalogResponse }
  | { ok: false; error: string }
> {
  const { data, error } = await client.rpc(SEARCH_CATALOG_RPC, searchCatalogRpcArgs(mode, query));
  if (error) {
    return { ok: false, error: error.message };
  }
  const parsed = parseSearchCatalogResponse(data);
  if (!parsed) {
    return { ok: false, error: "Unexpected search response shape." };
  }
  return { ok: true, data: { ...parsed, backend: "fts" } };
}

/**
 * Dual-read catalog search (E6): prefer Meili Edge, fall back to Postgres FTS.
 * Default for storefront/POS when `preferMeili` is true (production default).
 */
export async function searchCatalog(
  client: SupabaseClient<Database>,
  args: CatalogSearchArgs & { preferMeili?: boolean },
): Promise<
  | { ok: true; data: SearchCatalogResponse }
  | { ok: false; error: string }
> {
  const prefer = args.preferMeili !== false;
  if (prefer) {
    return searchCatalogMeili(client, args);
  }
  return searchCatalogFts(client, args.mode, args.query);
}

function parseSearchCatalogResponse(raw: unknown): SearchCatalogResponse | null {
  if (!raw || typeof raw !== "object") return null;
  const obj = raw as Record<string, unknown>;
  const mode = typeof obj.mode === "string" ? obj.mode : "";
  const query = typeof obj.query === "string" ? obj.query : "";
  const results = Array.isArray(obj.results) ? (obj.results as SearchResult[]) : [];
  if (!isSearchMode(mode)) return null;
  return {
    mode,
    query,
    results,
    backend: obj.backend === "meili" || obj.backend === "fts" ? obj.backend : undefined,
    facetDistribution:
      obj.facetDistribution && typeof obj.facetDistribution === "object"
        ? (obj.facetDistribution as Record<string, Record<string, number>>)
        : undefined,
  };
}

const MODES: SearchMode[] = ["part", "vin", "model", "pnc"];

export function isSearchMode(value: string): value is SearchMode {
  return (MODES as readonly string[]).includes(value);
}

export function normalizeSearchMode(value: string | undefined): SearchMode {
  return value && isSearchMode(value) ? value : "part";
}

/** Loyalty — mirrors web getLoyaltyBalance. */
export const GET_LOYALTY_BALANCE_RPC = "get_loyalty_balance" as const;

export function getLoyaltyBalanceArgs(customerId: string) {
  return { p_customer_id: customerId } as const;
}

export type LoyaltyBalance = {
  customer_id: string;
  points_balance: number;
  currency: Database["public"]["Enums"]["currency_code"];
  liability_per_point: number;
  estimated_liability: number;
};

/** Returns — mirrors web requestReturnCreditNote. */
export const POST_CUSTOMER_RETURN_CREDIT_NOTE_RPC =
  "post_customer_return_credit_note" as const;

export type ReturnCreditNoteLine = {
  stock_item_id: string;
  uom_id: string;
  qty: number;
};

export function postCustomerReturnCreditNoteArgs(
  invoiceId: string,
  lines: ReturnCreditNoteLine[],
) {
  return {
    p_invoice_id: invoiceId,
    p_lines: lines.map((l) => ({
      stock_item_id: l.stock_item_id,
      uom_id: l.uom_id,
      qty: l.qty,
    })),
  } as const;
}

/** Kits — PostgREST tables (no dedicated RPC). */
export const KITS_TABLE = "item_kits" as const;
export const KIT_COMPONENTS_TABLE = "item_kit_components" as const;

export type KitListItem = {
  kitId: string;
  stockItemId: string;
  oem: string;
  name: string;
  sellMode: Database["public"]["Enums"]["kit_sell_mode"];
  components: { oem: string; name: string; qty: number }[];
};

export function partHref(oem: string | null | undefined): string | null {
  if (!oem?.trim()) return null;
  return `/parts/${encodeURIComponent(oem.trim())}`;
}
