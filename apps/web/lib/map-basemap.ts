import type { StyleSpecification } from "maplibre-gl";
import { configuredMapStyleUrl } from "@/lib/customer-delivery-track";

/**
 * Default keyless MapLibre style (CARTO Positron). Covers Harare; no API key.
 * Override with NEXT_PUBLIC_MAP_STYLE_URL (MapTiler / self-hosted / etc.).
 */
export const MAP_STYLE_DEFAULT_URL =
  "https://basemaps.cartocdn.com/gl/positron-gl-style/style.json";

/**
 * Last-resort inline raster style — CARTO light raster tiles (OSM data).
 * Used when the remote GL style JSON fails to load. Still keyless.
 */
export const MAP_STYLE_RASTER_FALLBACK: StyleSpecification = {
  version: 8,
  sources: {
    "carto-light": {
      type: "raster",
      tiles: [
        "https://a.basemaps.cartocdn.com/light_all/{z}/{x}/{y}.png",
        "https://b.basemaps.cartocdn.com/light_all/{z}/{x}/{y}.png",
        "https://c.basemaps.cartocdn.com/light_all/{z}/{x}/{y}.png",
      ],
      tileSize: 256,
      attribution: "© OpenStreetMap © CARTO",
      maxzoom: 20,
    },
  },
  layers: [
    {
      id: "carto-light",
      type: "raster",
      source: "carto-light",
    },
  ],
};

/** Staff live map: env override, else keyless CARTO Positron. */
export function mapStyleUrl(): string {
  return configuredMapStyleUrl() || MAP_STYLE_DEFAULT_URL;
}

export function isStyleLoadError(error: unknown): boolean {
  if (!error || typeof error !== "object") return false;
  const e = error as {
    status?: number;
    message?: string;
    url?: string;
  };
  const msg = (e.message ?? "").toLowerCase();
  if (e.status === 404 || e.status === 403 || e.status === 0) return true;
  if (msg.includes("failed to fetch") || msg.includes("load")) return true;
  if (msg.includes("style") || (e.url && e.url.includes("style"))) return true;
  return false;
}
