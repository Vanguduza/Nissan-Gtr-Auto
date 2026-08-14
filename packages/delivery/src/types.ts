/**
 * Delivery dispatch contracts aligned with DIAL D-44 / D-45.
 *
 * Job/table SoR remains Postgres `delivery_jobs` (+ pick/DN). This package owns
 * workflow names, offer SM vocabulary, and routing SoR identifiers so Temporal
 * workers and clients do not invent a second stack.
 *
 * Maps render SoR = MapLibre. Distance/route SoR = OSRM (+ VROOM post-accept).
 * Never Fleetbase runtime; never Google/Mapbox as distance SoR.
 */

export const DELIVERY_DISPATCH_WORKFLOW = "DeliveryDispatchWorkflow" as const;

export type DeliveryEtaSource = "haversine" | "osrm" | "vroom" | "manual";

export type DeliveryOfferDecision = "accept" | "reject" | "timeout";

/** States for a dispatch offer cycle (AWS Last Mile algorithm pattern — reimplement). */
export type DeliveryOfferState =
  | "pending"
  | "offered"
  | "accepted"
  | "rejected"
  | "timed_out"
  | "requeued"
  | "fifo_queued"
  | "in_run"
  | "pod_submitted"
  | "completed"
  | "failed";

export type LatLng = {
  lat: number;
  lng: number;
};

export type RouteRequest = {
  origin: LatLng;
  destination: LatLng;
  waypoints?: LatLng[];
};

export type RouteResult = {
  /** Encoded or decoded polyline points (adapter-specific). */
  points: LatLng[];
  distanceMeters: number | null;
  durationSeconds: number | null;
  etaSource: DeliveryEtaSource;
  summary?: string | null;
};

export type DispatchWorkflowInput = {
  deliveryJobId: string;
  /** Optional preferred driver; else eligibility rank. */
  preferredDriverId?: string | null;
  offerTimeoutSeconds: number;
};

export type DispatchWorkflowResult = {
  deliveryJobId: string;
  finalState: DeliveryOfferState;
  assigneeDriverId: string | null;
  etaSource: DeliveryEtaSource | null;
};

/** Routing provider id — OSRM is default SoR when configured. */
export type RoutingProviderId = "osrm" | "google_deprecated" | "haversine";

export function preferRoutingProvider(env: {
  osrmUrl?: string | null;
  googleMapsKey?: string | null;
}): RoutingProviderId {
  if (env.osrmUrl?.trim()) return "osrm";
  if (env.googleMapsKey?.trim()) return "google_deprecated";
  return "haversine";
}
