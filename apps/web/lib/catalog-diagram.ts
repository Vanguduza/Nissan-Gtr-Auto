import type { SupabaseClient } from "@gtr/supabase-client";

export const CATALOG_DIAGRAMS_BUCKET = "catalog-diagrams";

export type DiagramHotspot = {
  id: string;
  oem: string;
  /** Normalized 0–1 fractions of diagram width/height when present. */
  x: number | null;
  y: number | null;
  width: number | null;
  height: number | null;
};

export type CatalogDiagram = {
  path: string;
  publicUrl: string;
  hotspots: DiagramHotspot[];
};

/** Build a public Storage URL for `catalog-diagrams` object paths. */
export function catalogDiagramPublicUrl(
  client: SupabaseClient,
  diagramPath: string,
): string | null {
  const path = diagramPath.trim().replace(/^\/+/, "");
  if (!path) return null;
  // Paths may already be full public URLs from older imports.
  if (/^https?:\/\//i.test(path)) return path;
  const { data } = client.storage
    .from(CATALOG_DIAGRAMS_BUCKET)
    .getPublicUrl(path);
  return data.publicUrl || null;
}

/**
 * Optional Supabase Image Transformation URL (Pro feature).
 * Hosted project currently returns FeatureNotEnabled — keep using
 * {@link catalogDiagramPublicUrl} + next/image until transforms are on.
 * Prefer high `quality` (80–90) for EPC line art; reserve small `width` for grid thumbs only.
 */
export function catalogDiagramTransformUrl(
  publicUrl: string,
  opts: { width: number; quality?: number; resize?: "cover" | "contain" | "fill" },
): string | null {
  const marker = "/storage/v1/object/public/";
  const i = publicUrl.indexOf(marker);
  if (i < 0) return null;
  const origin = publicUrl.slice(0, i);
  const objectPath = publicUrl.slice(i + marker.length);
  const q = new URLSearchParams({
    width: String(opts.width),
    quality: String(opts.quality ?? 85),
    resize: opts.resize ?? "contain",
  });
  return `${origin}/storage/v1/render/image/public/${objectPath}?${q}`;
}

type FitmentDiagramRow = {
  id: string;
  oem_part_number: string;
  diagram_path: string | null;
  bbox_x: number | null;
  bbox_y: number | null;
  bbox_width: number | null;
  bbox_height: number | null;
};

/**
 * Load diagram + hotspots for an OEM from `part_fitment.diagram_path`
 * (Storage bucket `catalog-diagrams`). Returns null when no path is set —
 * pipeline has not uploaded assets yet.
 *
 * Seed packs (idempotent): Navara D40 (`navara-d40/…`) and X-Trail T31
 * (`xtrail-t31/…`). PDP `CatalogCanvasStub` uses this loader unchanged —
 * no vehicle-specific canvas branch required.
 */
export async function loadOemCatalogDiagram(
  client: SupabaseClient,
  oem: string,
): Promise<
  | { ok: true; data: CatalogDiagram | null }
  | { ok: false; error: string }
> {
  const needle = oem.trim();
  if (!needle) return { ok: true, data: null };

  const { data, error } = await client
    .from("part_fitment")
    .select(
      "id, oem_part_number, diagram_path, bbox_x, bbox_y, bbox_width, bbox_height",
    )
    .ilike("oem_part_number", needle)
    .not("diagram_path", "is", null)
    .limit(40);

  if (error) return { ok: false, error: error.message };

  const rows = (data ?? []) as FitmentDiagramRow[];
  const withPath = rows.filter((r) => r.diagram_path?.trim());
  if (!withPath.length) return { ok: true, data: null };

  // Prefer a shared diagram_path that also has bbox; else first path.
  const primary =
    withPath.find(
      (r) =>
        r.bbox_x != null &&
        r.bbox_y != null &&
        r.bbox_width != null &&
        r.bbox_height != null,
    ) ?? withPath[0];

  const path = primary.diagram_path!.trim();
  const publicUrl = catalogDiagramPublicUrl(client, path);
  if (!publicUrl) return { ok: true, data: null };

  const sameDiagram = withPath.filter(
    (r) => (r.diagram_path ?? "").trim() === path,
  );

  const hotspots: DiagramHotspot[] = sameDiagram.map((r) => ({
    id: r.id,
    oem: r.oem_part_number,
    x: r.bbox_x != null ? Number(r.bbox_x) : null,
    y: r.bbox_y != null ? Number(r.bbox_y) : null,
    width: r.bbox_width != null ? Number(r.bbox_width) : null,
    height: r.bbox_height != null ? Number(r.bbox_height) : null,
  }));

  return {
    ok: true,
    data: { path, publicUrl, hotspots },
  };
}

/**
 * Optional teaser for catalog home — first fitment row that has a diagram.
 * Documents the Storage gap when the pipeline has not seeded assets.
 */
export async function loadSampleCatalogDiagram(
  client: SupabaseClient,
): Promise<
  | { ok: true; data: CatalogDiagram | null }
  | { ok: false; error: string }
> {
  const { data, error } = await client
    .from("part_fitment")
    .select(
      "id, oem_part_number, diagram_path, bbox_x, bbox_y, bbox_width, bbox_height",
    )
    .not("diagram_path", "is", null)
    .limit(24);

  if (error) return { ok: false, error: error.message };
  const rows = (data ?? []) as FitmentDiagramRow[];
  const first = rows.find((r) => r.diagram_path?.trim());
  if (!first?.diagram_path) return { ok: true, data: null };

  return loadOemCatalogDiagram(client, first.oem_part_number);
}

/**
 * Resolve hierarchy diagram image URL.
 * Order: storage_path → catalog-diagrams public URL → image_url fallback.
 */
export function resolveDiagramImageUrl(
  client: SupabaseClient,
  diagram: { storage_path?: string | null; image_url?: string | null },
): string | null {
  const storage = diagram.storage_path?.trim();
  if (storage) {
    const fromStorage = catalogDiagramPublicUrl(client, storage);
    if (fromStorage) return fromStorage;
  }
  const fallback = diagram.image_url?.trim();
  return fallback || null;
}

/** Hotspot layout: 0–1 fractions vs absolute pixels (shared by EPC canvas + stub). */
export function hotspotStyle(h: {
  x: number | null;
  y: number | null;
  width: number | null;
  height: number | null;
}): import("react").CSSProperties | null {
  if (
    h.x == null ||
    h.y == null ||
    h.width == null ||
    h.height == null ||
    !(h.width > 0) ||
    !(h.height > 0)
  ) {
    return null;
  }
  const asFraction = h.x <= 1 && h.y <= 1 && h.width <= 1 && h.height <= 1;
  if (asFraction) {
    return {
      left: `${h.x * 100}%`,
      top: `${h.y * 100}%`,
      width: `${h.width * 100}%`,
      height: `${h.height * 100}%`,
    };
  }
  return {
    left: h.x,
    top: h.y,
    width: h.width,
    height: h.height,
  };
}
