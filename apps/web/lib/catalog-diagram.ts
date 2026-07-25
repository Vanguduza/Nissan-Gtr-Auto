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
