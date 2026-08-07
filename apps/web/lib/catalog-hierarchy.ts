import type { SupabaseClient } from "@gtr/supabase-client";
import {
  catalogPath,
  epcHref,
  parseCatalogParams,
  EPC_CONTEXT_STORAGE_KEY,
  type CatalogMaker,
  type CatalogModel,
  type CatalogVariant,
  type CatalogSection,
  type CatalogDiagramHotspot,
  type CatalogDiagramPart,
  type CatalogDiagramResponse,
  type CatalogBrowseContext,
} from "@gtr/shared";

export {
  catalogPath,
  epcHref,
  parseCatalogParams,
  EPC_CONTEXT_STORAGE_KEY,
  type CatalogMaker,
  type CatalogModel,
  type CatalogVariant,
  type CatalogSection,
  type CatalogDiagramHotspot,
  type CatalogDiagramPart,
  type CatalogDiagramResponse,
  type CatalogBrowseContext,
};

type Ok<T> = { ok: true; data: T };
type Err = { ok: false; error: string };
type Result<T> = Ok<T> | Err;

export async function listCatalogMakers(
  client: SupabaseClient,
): Promise<Result<CatalogMaker[]>> {
  const { data, error } = await client.rpc("list_catalog_makers");
  if (error) return { ok: false, error: error.message };
  return { ok: true, data: (data ?? []) as CatalogMaker[] };
}

export async function listCatalogModels(
  client: SupabaseClient,
  makerSlug: string,
): Promise<Result<CatalogModel[]>> {
  const { data, error } = await client.rpc("list_catalog_models", {
    p_maker_slug: makerSlug,
  });
  if (error) return { ok: false, error: error.message };
  return { ok: true, data: (data ?? []) as CatalogModel[] };
}

export async function listCatalogVariants(
  client: SupabaseClient,
  makerSlug: string,
  modelSlug: string,
): Promise<Result<CatalogVariant[]>> {
  const { data, error } = await client.rpc("list_catalog_variants", {
    p_maker_slug: makerSlug,
    p_model_slug: modelSlug,
  });
  if (error) return { ok: false, error: error.message };
  return { ok: true, data: (data ?? []) as CatalogVariant[] };
}

export async function listCatalogSections(
  client: SupabaseClient,
  makerSlug: string,
  modelSlug: string,
  variantSlug: string,
): Promise<Result<CatalogSection[]>> {
  const { data, error } = await client.rpc("list_catalog_sections", {
    p_maker_slug: makerSlug,
    p_model_slug: modelSlug,
    p_variant_slug: variantSlug,
  });
  if (error) return { ok: false, error: error.message };
  return { ok: true, data: (data ?? []) as CatalogSection[] };
}

export async function getCatalogDiagram(
  client: SupabaseClient,
  makerSlug: string,
  modelSlug: string,
  variantSlug: string,
  sectionSlug: string,
): Promise<Result<CatalogDiagramResponse>> {
  const { data, error } = await client.rpc("get_catalog_diagram", {
    p_maker_slug: makerSlug,
    p_model_slug: modelSlug,
    p_variant_slug: variantSlug,
    p_section_slug: sectionSlug,
  });
  if (error) return { ok: false, error: error.message };
  const row = data as CatalogDiagramResponse | null;
  return {
    ok: true,
    data: row ?? { diagram: null, hotspots: [], parts: [] },
  };
}

/**
 * Lookup catalog_variants by chassis_code for garage / vehicle-selector deep-links.
 * Returns the first matching variant context, or null.
 */
export async function lookupVariantByChassis(
  client: SupabaseClient,
  chassisCode: string,
): Promise<CatalogBrowseContext | null> {
  const code = chassisCode.trim().toUpperCase();
  if (!code) return null;
  const { data, error } = await client
    .from("catalog_variants")
    .select("maker_slug, model_slug, slug, chassis_code")
    .ilike("chassis_code", code)
    .limit(1);
  if (error || !data || data.length === 0) return null;
  const row = data[0];
  return {
    maker: row.maker_slug,
    model: row.model_slug,
    variant: row.slug,
  };
}

/** Persist EPC browse context into sessionStorage. */
export function saveEpcContext(ctx: CatalogBrowseContext): void {
  if (typeof window === "undefined") return;
  try {
    window.sessionStorage.setItem(
      EPC_CONTEXT_STORAGE_KEY,
      JSON.stringify(ctx),
    );
  } catch {
    // quota or SSR — ignore
  }
}

/** Read EPC browse context from sessionStorage. */
export function loadEpcContext(): CatalogBrowseContext | null {
  if (typeof window === "undefined") return null;
  try {
    const raw = window.sessionStorage.getItem(EPC_CONTEXT_STORAGE_KEY);
    if (!raw) return null;
    return JSON.parse(raw) as CatalogBrowseContext;
  } catch {
    return null;
  }
}
