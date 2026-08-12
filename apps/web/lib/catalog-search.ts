import type { SupabaseClient } from "@gtr/supabase-client";
import {
  searchCatalog as searchCatalogDual,
  type PartHit,
  type PncHit,
  type SearchCatalogResponse,
  type SearchMode,
  type SearchResult,
  type VehicleHit,
  isSearchMode,
  normalizeSearchMode,
  partHref,
} from "@gtr/supabase-client";

export {
  type SearchMode,
  type SearchCatalogResponse,
  type SearchResult,
  type PartHit,
  type VehicleHit,
  type PncHit,
  isSearchMode,
  normalizeSearchMode,
  partHref,
};

export { epcHref, catalogPath } from "@gtr/shared";

/** Facets requested from Meili Edge proxy — mirrors mobile InlineSearch. */
export const MEILI_FACETS = [
  "category_name",
  "pnc_code",
  "chassis_code",
  "model_variant",
] as const;

export type SearchSuggestionKind = "category" | "model" | "part";

export type SearchSuggestion = {
  kind: SearchSuggestionKind;
  title: string;
  subtitle?: string;
  /** When set, navigate to PDP. */
  oem?: string;
  /** When set, apply as catalog/search filter query. */
  filterQuery?: string;
};

export type FacetChip = {
  facet: string;
  value: string;
  count: number;
};

export type SearchFetchResult = {
  suggestions: SearchSuggestion[];
  facetChips: FacetChip[];
  backend?: "meili" | "fts";
};

/**
 * Dual-read catalog search (E6): Meili Edge first, Postgres FTS fallback.
 * Never calls Meili directly from the browser. Set preferMeili=false for FTS-only.
 */
export async function searchCatalog(
  client: SupabaseClient,
  mode: SearchMode,
  query: string,
  opts?: { limit?: number; facets?: string[]; preferMeili?: boolean },
): Promise<
  | { ok: true; data: SearchCatalogResponse }
  | { ok: false; error: string }
> {
  return searchCatalogDual(client, {
    mode,
    query,
    limit: opts?.limit,
    facets: opts?.facets ?? [...MEILI_FACETS],
    preferMeili: opts?.preferMeili,
  });
}

function absorbPartSuggestions(
  out: Map<string, SearchSuggestion>,
  parts: PartHit[],
  asParts: boolean,
) {
  for (const hit of parts) {
    const oem = hit.oem_part_number?.trim();
    if (!oem) continue;

    if (asParts) {
      const key = `part:${oem}`;
      if (!out.has(key)) {
        out.set(key, {
          kind: "part",
          title: oem,
          subtitle: [hit.category_name, hit.pnc_code].filter(Boolean).join(" · ") || undefined,
          oem,
        });
      }
    }

    const cat = hit.category_name?.trim();
    if (cat) {
      const key = `cat:${cat}`;
      if (!out.has(key)) {
        out.set(key, {
          kind: "category",
          title: cat,
          subtitle: hit.subcategory_name ?? undefined,
          filterQuery: cat,
        });
      }
    }

    const pnc = hit.pnc_code?.trim();
    if (pnc) {
      const key = `pnc:${pnc}`;
      if (!out.has(key)) {
        out.set(key, {
          kind: "category",
          title: pnc,
          subtitle: "PNC",
          filterQuery: pnc,
        });
      }
    }

    for (const model of [hit.chassis_code, hit.engine_code]) {
      const m = model?.trim();
      if (!m) continue;
      const key = `model:${m}`;
      if (!out.has(key)) {
        out.set(key, {
          kind: "model",
          title: m,
          subtitle: oem,
          filterQuery: m,
        });
      }
    }
  }
}

function flattenParts(results: SearchResult[]): PartHit[] {
  const parts: PartHit[] = [];
  for (const hit of results) {
    if (hit.type === "part") {
      parts.push(hit);
    } else if (hit.type === "vehicle" || hit.type === "pnc") {
      for (const f of hit.fitments ?? []) {
        if (f.type === "part") parts.push(f);
      }
    }
  }
  return parts;
}

function facetChipsFromDistribution(
  distribution: Record<string, Record<string, number>> | undefined,
  max = 8,
): FacetChip[] {
  if (!distribution) return [];
  const chips: FacetChip[] = [];
  for (const [facet, values] of Object.entries(distribution)) {
    for (const [value, count] of Object.entries(values)) {
      if (value.trim()) chips.push({ facet, value, count });
    }
  }
  return chips.sort((a, b) => b.count - a.count).slice(0, max);
}

/**
 * Debounced typeahead fetch — Meili first, FTS per-mode fallback when empty.
 * Matches Android `InlineCatalogSearch` / iOS catalog typeahead contract.
 */
export async function fetchSearchSuggestions(
  client: SupabaseClient,
  query: string,
): Promise<SearchFetchResult> {
  const trimmed = query.trim();
  if (trimmed.length < 2) {
    return { suggestions: [], facetChips: [] };
  }

  const out = new Map<string, SearchSuggestion>();
  const facetCounts = new Map<string, Map<string, number>>();
  let backend: "meili" | "fts" | undefined;

  async function absorbMeili(mode: SearchMode) {
    const result = await searchCatalog(client, mode, trimmed, {
      limit: 20,
      facets: [...MEILI_FACETS],
    });
    if (!result.ok) return;
    backend = result.data.backend ?? backend;
    absorbPartSuggestions(out, flattenParts(result.data.results), mode === "part" || mode === "vin");
    if (result.data.facetDistribution) {
      for (const [facet, values] of Object.entries(result.data.facetDistribution)) {
        const bucket = facetCounts.get(facet) ?? new Map<string, number>();
        for (const [label, count] of Object.entries(values)) {
          bucket.set(label, (bucket.get(label) ?? 0) + count);
        }
        facetCounts.set(facet, bucket);
      }
    }
  }

  await absorbMeili("part");
  if (![...out.values()].some((s) => s.kind === "model")) {
    await absorbMeili("model");
  }
  await absorbMeili("pnc");

  const vinLike =
    trimmed.length >= 11 &&
    trimmed.length <= 17 &&
    /^[a-z0-9]+$/i.test(trimmed);
  if (vinLike) {
    await absorbMeili("vin");
  }

  if (out.size === 0) {
    for (const mode of ["part", "model", "pnc"] as const) {
      const result = await searchCatalog(client, mode, trimmed, { limit: 20 });
      if (!result.ok) continue;
      backend = result.data.backend ?? "fts";
      absorbPartSuggestions(out, flattenParts(result.data.results), mode === "part");
    }
  }

  const mergedDistribution: Record<string, Record<string, number>> = {};
  for (const [facet, values] of facetCounts) {
    mergedDistribution[facet] = Object.fromEntries(values);
  }

  return {
    suggestions: [...out.values()].slice(0, 24),
    facetChips: facetChipsFromDistribution(mergedDistribution),
    backend,
  };
}
