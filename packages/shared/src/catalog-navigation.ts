/**
 * Shared catalog navigation contract — hierarchy-first (Megazip-style) + search-second.
 * Used by web; mirror shapes in iOS/Android RPC clients.
 */

export type CatalogMaker = {
  slug: string;
  name: string;
  sort_order?: number;
  model_count?: number;
};

export type CatalogModel = {
  slug: string;
  display_name: string;
  body_type?: string | null;
  sort_key: string;
  year_start?: number | null;
  year_end?: number | null;
  source_url?: string | null;
};

export type CatalogVariant = {
  slug: string;
  chassis_code: string;
  frame?: string | null;
  grade?: string | null;
  sales_region?: string | null;
  year_label?: string | null;
  engine_code?: string | null;
  source_url?: string | null;
};

export type CatalogSection = {
  slug: string;
  name: string;
  thumbnail_url?: string | null;
  sort_order?: number;
  assembly_group_id?: string | null;
  source_url?: string | null;
};

export type CatalogDiagramHotspot = {
  oem: string;
  pnc_code?: string | null;
  bbox_x: number;
  bbox_y: number;
  bbox_width: number;
  bbox_height: number;
};

export type CatalogDiagramPart = {
  oem_part_number: string;
  pnc_code?: string | null;
  chassis_code?: string | null;
  engine_code?: string | null;
  category_name?: string | null;
  subcategory_name?: string | null;
  pcdb_part_type_id?: number | null;
  stock_item_id?: string | null;
  stock_description?: string | null;
  diagram_path?: string | null;
};

export type CatalogDiagramResponse = {
  diagram: {
    slug: string;
    title: string;
    storage_path: string | null;
    image_url?: string | null;
    width?: number | null;
    height?: number | null;
  } | null;
  hotspots: CatalogDiagramHotspot[];
  parts: CatalogDiagramPart[];
};

/** Breadcrumb stack for /catalog/[maker]/[model]/[variant]/[section] */
export type CatalogBrowseContext = {
  maker: string;
  model?: string;
  variant?: string;
  section?: string;
};

/** SessionStorage key for last EPC browse context (web + mobile WebView shells). */
export const EPC_CONTEXT_STORAGE_KEY = "gtr:epc-context";

/** Build storefront path `/catalog/...` from browse context. */
export function catalogPath(ctx: CatalogBrowseContext): string {
  const parts = ["/catalog", encodeURIComponent(ctx.maker)];
  if (ctx.model) parts.push(encodeURIComponent(ctx.model));
  if (ctx.variant) parts.push(encodeURIComponent(ctx.variant));
  if (ctx.section) parts.push(encodeURIComponent(ctx.section));
  return parts.join("/");
}

/** Alias used by search / garage deep-links. */
export function epcHref(ctx: CatalogBrowseContext): string {
  return catalogPath(ctx);
}

/** Parse App Router dynamic params into CatalogBrowseContext. */
export function parseCatalogParams(params: {
  maker?: string;
  model?: string;
  variant?: string;
  section?: string;
}): CatalogBrowseContext | null {
  const maker = params.maker?.trim();
  if (!maker) return null;
  const ctx: CatalogBrowseContext = { maker: decodeURIComponent(maker) };
  if (params.model?.trim()) ctx.model = decodeURIComponent(params.model.trim());
  if (params.variant?.trim()) {
    ctx.variant = decodeURIComponent(params.variant.trim());
  }
  if (params.section?.trim()) {
    ctx.section = decodeURIComponent(params.section.trim());
  }
  return ctx;
}
