import type { EpcDiagram, EpcSection, VehicleModel, VehicleVariant } from "../types";

/**
 * POS offline catalogue bundle, format `gtr-pos-offline/1` (built by
 * `data-pipeline/scripts/build_pos_offline_bundle.py`). Pure helpers shared by the downloader and
 * the reader; the derivations mirror `catalog-live-r2` so offline answers match the live ones.
 */
export const OFFLINE_FORMAT = "gtr-pos-offline/1";

/** Where one stored object sits: pack index, byte offset, byte length. */
export type Loc = [pack: number, offset: number, length: number];
export type BundleFileKind = "pack" | "vehicles" | "routes";
export type BundleFile = { path: string; kind: BundleFileKind; bytes: number; sha256: string; url?: string };
export type BundleManifest = {
  format: string;
  maker: string;
  release: string;
  build: string;
  built_at: string;
  route_buckets: number;
  counts: { vehicles: number; vehicle_shards: number; diagrams: number; diagram_images: number };
  total_bytes: number;
  files: BundleFile[];
  expires_in?: number;
};

/** A `list_customer_vehicle_master` row; id is the catalogue vehicle_id. */
export type VehicleMasterRow = {
  id: string;
  model_family: string;
  chassis_code: string;
  engine_code: string | null;
  year_start: number | null;
  year_end: number | null;
  sales_region: string | null;
};
export type VehiclesFile = { vehicles: VehicleMasterRow[]; shards: Record<string, Loc[]> };
/** Part-list pages (`f` = rows of a shared section shard, keep this diagram's only) and image. */
export type DiagramRoute = { p?: Loc[]; f?: 1; i?: Loc };
export type Row = Record<string, unknown>;

/** A `pull_pos_offline_snapshot` item: what the shop stocks and sells, with price and stock. */
export type StockItem = {
  stock_item_id: string;
  oem_part_number: string;
  description: string | null;
  uom_id: string | null;
  unit_price: number;
  core_charge: number;
  saleable_qty: number;
  currency: string;
};

export function packPath(index: number): string {
  return `pack-${String(index).padStart(4, "0")}.bin`;
}

/** 32-bit FNV-1a over UTF-8, as the builder uses to place a diagram's route. */
export function fnv1a32(text: string): number {
  let h = 0x811c9dc5;
  for (const b of new TextEncoder().encode(text)) {
    h ^= b;
    h = Math.imul(h, 0x01000193) >>> 0;
  }
  return h >>> 0;
}

export function routesPath(diagramId: string, buckets: number): string {
  return `routes-${String(fnv1a32(diagramId) % buckets).padStart(2, "0")}.json.gz`;
}

export async function gunzip(bytes: Uint8Array): Promise<Uint8Array> {
  if (bytes.length < 2 || bytes[0] !== 0x1f || bytes[1] !== 0x8b) return bytes;
  const stream = new Blob([bytes as BlobPart]).stream().pipeThrough(new DecompressionStream("gzip"));
  return new Uint8Array(await new Response(stream).arrayBuffer());
}

export async function gunzipJson<T>(bytes: Uint8Array): Promise<T> {
  return JSON.parse(new TextDecoder().decode(await gunzip(bytes))) as T;
}

export function parseRows(text: string): Row[] {
  const t = text.trim();
  if (!t) return [];
  if (t.startsWith("[")) {
    const parsed = JSON.parse(t);
    return Array.isArray(parsed) ? (parsed as Row[]) : [];
  }
  return t.split(/\r?\n/).filter(Boolean).map((line) => JSON.parse(line) as Row);
}

export async function sha256Hex(bytes: Uint8Array): Promise<string> {
  const digest = await crypto.subtle.digest("SHA-256", bytes as BufferSource);
  return [...new Uint8Array(digest)].map((b) => b.toString(16).padStart(2, "0")).join("");
}

export function normalizePart(value: unknown): string {
  return String(value ?? "").toUpperCase().replace(/[^A-Z0-9]/g, "");
}

/** Same family key as the catalogue gateway (`staff-families`). */
export function familySlug(model: string): string {
  return model.trim().toLowerCase().replace(/[^a-z0-9]+/g, "-").replace(/^-+|-+$/g, "");
}

function minYear(a: number | null, b: number | null): number | null {
  return a == null ? b : b == null ? a : Math.min(a, b);
}
function maxYear(a: number | null, b: number | null): number | null {
  return a == null ? b : b == null ? a : Math.max(a, b);
}
export function yearRange(start: number | null, end: number | null): string | null {
  if (start == null && end == null) return null;
  return `${start ?? ""}–${end ?? ""}`;
}

export function modelsFrom(rows: VehicleMasterRow[]): VehicleModel[] {
  const models = new Map<string, VehicleModel>();
  for (const r of rows) {
    const slug = familySlug(r.model_family);
    const m = models.get(slug);
    models.set(slug, {
      slug,
      name: r.model_family,
      yearStart: minYear(m?.yearStart ?? null, r.year_start),
      yearEnd: maxYear(m?.yearEnd ?? null, r.year_end),
    });
  }
  return [...models.values()].sort((a, b) => a.name.localeCompare(b.name));
}

export function variantsFrom(rows: VehicleMasterRow[], modelSlug: string): VehicleVariant[] {
  return rows
    .filter((r) => familySlug(r.model_family) === modelSlug)
    .map((r) => ({
      slug: r.id,
      chassisCode: r.chassis_code,
      engineCode: r.engine_code,
      yearLabel: [yearRange(r.year_start, r.year_end), r.sales_region].filter(Boolean).join(" · ") || null,
    }));
}

/** The vehicle-master id for model + chassis + engine (first by id, as the live search picks). */
export function vehicleIdFor(rows: VehicleMasterRow[], modelSlug: string, chassisCode: string, engineCode: string): string | null {
  const ids = rows
    .filter(
      (r) =>
        familySlug(r.model_family) === modelSlug &&
        r.chassis_code.trim().toUpperCase() === chassisCode.trim().toUpperCase() &&
        (r.engine_code ?? "").trim().toUpperCase() === engineCode.trim().toUpperCase(),
    )
    .map((r) => r.id)
    .sort();
  return ids[0] ?? null;
}

/** Most frequent label wins (ties alphabetical), as in the gateway. */
function topLabel(labels: Map<string, number>, fallback: string): string {
  return [...labels.entries()].sort((a, b) => b[1] - a[1] || a[0].localeCompare(b[0]))[0]?.[0] ?? fallback;
}

/** `staff-sections` over a vehicle's shard rows. Slug is the catalogue section id. */
export function sectionsFrom(rows: Row[]): EpcSection[] {
  const map = new Map<string, { id: string; slug: string; labels: Map<string, number> }>();
  for (const row of rows) {
    const id = String(row.section_id ?? "").trim();
    if (!id) continue;
    const current = map.get(id) ?? { id, slug: String(row.section_slug ?? "").trim(), labels: new Map<string, number>() };
    const label = String(row.subcategory_name ?? row.section_slug ?? id).trim();
    if (label) current.labels.set(label, (current.labels.get(label) ?? 0) + 1);
    map.set(id, current);
  }
  return [...map.values()]
    .map((s) => ({ slug: s.id, name: topLabel(s.labels, s.slug || s.id), thumbnailUrl: null }))
    .sort((a, b) => a.name.localeCompare(b.name));
}

/** `staff-diagrams` for one section of a vehicle's shard rows. */
export function diagramsFrom(rows: Row[], sectionId: string): Array<{ id: string; title: string }> {
  const map = new Map<string, Map<string, number>>();
  for (const row of rows) {
    if (String(row.section_id ?? "") !== sectionId) continue;
    const id = String(row.diagram_id ?? "").trim();
    if (!id) continue;
    const titles = map.get(id) ?? new Map<string, number>();
    const title = String(row.category_name ?? row.subcategory_name ?? id).trim();
    if (title) titles.set(title, (titles.get(title) ?? 0) + 1);
    map.set(id, titles);
  }
  return [...map.entries()].map(([id, titles]) => ({ id, title: topLabel(titles, id) })).sort((a, b) => a.title.localeCompare(b.title));
}

function searchable(row: Row): string {
  return [
    row.search_text,
    row.name,
    row.description,
    row.category_name,
    row.subcategory_name,
    row.pnc_code,
    row.display_oem_number,
    ...(Array.isArray(row.aliases) ? row.aliases : []),
  ]
    .filter(Boolean)
    .join(" ")
    .toLowerCase();
}

export function queryMatch(row: Row, q: string): boolean {
  const hay = searchable(row);
  return q.toLowerCase().split(/\s+/).filter(Boolean).every((term) => hay.includes(term));
}

/**
 * `customer-search` / `customer-stock` offline: the vehicle's catalogue rows (matching the query
 * when given) joined to what the shop stocks, by normalised part number.
 */
export function vehicleStockHits(rows: Row[], q: string, stockByOem: Map<string, StockItem>, limit: number): Array<{ row: Row; stock: StockItem }> {
  const out: Array<{ row: Row; stock: StockItem }> = [];
  const seen = new Set<string>();
  for (const row of rows) {
    const key = normalizePart(row.normalized_oem_number ?? row.display_oem_number);
    if (!key || seen.has(key)) continue;
    const stock = stockByOem.get(key);
    if (!stock || (q && !queryMatch(row, q))) continue;
    seen.add(key);
    out.push({ row, stock });
    if (out.length >= limit) break;
  }
  return out;
}

/** Shop stock by part number or description words, for a search with no vehicle chosen. */
export function stockHits(items: StockItem[], q: string, limit: number): StockItem[] {
  const terms = q.toLowerCase().split(/\s+/).filter(Boolean);
  const qPart = normalizePart(q);
  if (!terms.length) return [];
  return items
    .filter((s) => {
      if (qPart.length >= 3 && normalizePart(s.oem_part_number).includes(qPart)) return true;
      const hay = `${s.oem_part_number} ${s.description ?? ""}`.toLowerCase();
      return terms.every((t) => hay.includes(t));
    })
    .slice(0, limit);
}

type DiagramPartRow = {
  display_oem_number?: string | null;
  normalized_oem_number?: string | null;
  pnc_code?: string | null;
  callout_ref?: string | null;
  name?: string | null;
  description?: string | null;
  category_name?: string | null;
  subcategory_name?: string | null;
  bbox_x?: number | null;
  bbox_y?: number | null;
  bbox_width?: number | null;
  bbox_height?: number | null;
};

/** An EPC diagram from its part rows: callout boxes (fractions of the image) and the part list. */
export function diagramFromParts(title: string, imageUrl: string | null, rawParts: DiagramPartRow[], notice: string | null): EpcDiagram {
  const seen = new Set<string>();
  const hotspots = rawParts.flatMap((p) => {
    const oem = (p.display_oem_number ?? p.normalized_oem_number ?? "").trim();
    if (!oem || p.bbox_x == null || p.bbox_y == null || !p.bbox_width || !p.bbox_height) return [];
    return [{ oem, pnc: p.pnc_code ?? null, x: p.bbox_x, y: p.bbox_y, w: p.bbox_width, h: p.bbox_height }];
  });
  const parts = rawParts.flatMap((p) => {
    const oem = (p.display_oem_number ?? p.normalized_oem_number ?? "").trim();
    const key = normalizePart(oem);
    if (!oem || seen.has(key)) return [];
    seen.add(key);
    return [{
      oemPartNumber: oem,
      pnc: p.pnc_code ?? null,
      ref: p.callout_ref ?? null,
      name: p.name ?? p.description ?? p.subcategory_name ?? oem,
      categoryName: p.category_name ?? null,
      subcategoryName: p.subcategory_name ?? null,
    }];
  });
  return { title, imageUrl, width: null, height: null, hotspots, parts, notice };
}
