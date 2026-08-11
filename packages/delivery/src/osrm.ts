import type { LatLng, RouteRequest, RouteResult } from "./types.ts";

export type OsrmConfig = {
  /** e.g. http://127.0.0.1:5000 */
  baseUrl: string;
  profile?: "driving" | "car";
};

/**
 * OSRM route HTTP client (distance/route SoR).
 * GET {base}/route/v1/{profile}/{coords}?overview=full&geometries=geojson
 */
export async function fetchOsrmRoute(
  cfg: OsrmConfig,
  req: RouteRequest,
  fetchImpl: typeof fetch = fetch,
): Promise<RouteResult> {
  const profile = cfg.profile ?? "driving";
  const coords = [req.origin, ...(req.waypoints ?? []), req.destination]
    .map((p) => `${p.lng},${p.lat}`)
    .join(";");
  const url =
    `${cfg.baseUrl.replace(/\/$/, "")}/route/v1/${profile}/${coords}` +
    "?overview=full&geometries=geojson";

  const res = await fetchImpl(url);
  const text = await res.text();
  if (!res.ok) {
    throw new Error(`OSRM ${res.status}: ${text.slice(0, 400)}`);
  }
  return parseOsrmRouteJson(text);
}

export function parseOsrmRouteJson(body: string): RouteResult {
  const raw = JSON.parse(body) as {
    code?: string;
    routes?: Array<{
      distance?: number;
      duration?: number;
      geometry?: { coordinates?: [number, number][] };
    }>;
  };
  if (raw.code !== "Ok" || !raw.routes?.[0]) {
    throw new Error(`OSRM route failed: ${raw.code ?? "unknown"}`);
  }
  const route = raw.routes[0];
  const points: LatLng[] = (route.geometry?.coordinates ?? []).map(
    ([lng, lat]) => ({ lat, lng }),
  );
  return {
    points,
    distanceMeters:
      typeof route.distance === "number" ? Math.round(route.distance) : null,
    durationSeconds:
      typeof route.duration === "number" ? Math.round(route.duration) : null,
    etaSource: "osrm",
    summary: "OSRM",
  };
}
