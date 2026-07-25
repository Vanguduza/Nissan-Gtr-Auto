/**
 * GPS / delivery-location bridge contracts — interfaces only.
 *
 * Aligns with RPC `public.ingest_delivery_location` (Phase 10 logistics):
 *   (p_delivery_job_id UUID,
 *    p_lat DOUBLE PRECISION,
 *    p_lng DOUBLE PRECISION,
 *    p_recorded_at TIMESTAMPTZ DEFAULT now(),
 *    p_accuracy_m NUMERIC DEFAULT NULL)
 *   → UUID (delivery_locations.id)
 *
 * Source column on insert is `'bridge'`. Server enforces ~5s rate limit per job.
 * Callers map GpsCoordinate → RPC args; do not INSERT into delivery_locations directly.
 *
 * Forbidden: browser geolocation (`navigator.geolocation`), WebView location APIs,
 * or HTML5 Geolocation. FusedLocationProvider / CoreLocation only in impl dirs.
 *
 * Impl ownership (see bridges/README.md):
 *   bridges/android/location-tracker/ → FusedLocationGpsBridge
 *   bridges/ios/LocationTracker/      → CoreLocationGpsBridge
 */

export type LocationPermissionStatus =
  | "granted"
  | "denied"
  | "restricted"
  | "not_determined"
  /** Android: approximate-only when fine was not granted. */
  | "approximate";

/** One native position fix from the device. */
export type GpsCoordinate = {
  latitude: number;
  longitude: number;
  /** Horizontal accuracy in meters when the OS provides it. */
  accuracyMeters?: number;
  /** ISO-8601 timestamp from the device (maps to p_recorded_at). */
  capturedAt: string;
};

/**
 * Payload shape for `ingest_delivery_location` after a bridge fix.
 * Field names mirror RPC parameters for management-app / service callers.
 */
export type DeliveryLocationIngest = {
  deliveryJobId: string;
  lat: number;
  lng: number;
  /** ISO-8601; omit to let the RPC default to `now()`. */
  recordedAt?: string;
  /** Meters; omit when accuracy unknown. */
  accuracyM?: number;
};

/** Convert a native fix into the RPC ingest shape for a delivery job. */
export type GpsToIngestMapper = (
  jobId: string,
  coord: GpsCoordinate,
) => DeliveryLocationIngest;

export type GpsWatchHandle = {
  stop(): Promise<void>;
};

/**
 * Battery-aware cadence for continuous delivery tracking.
 * - moving: high accuracy, ~5s (aligns with server ingest rate limit)
 * - idle: balanced power, ~30s + distance filter (parked / slow)
 * - auto: bridge switches moving ↔ idle from speed heuristics
 */
export type GpsWatchCadence = "moving" | "idle" | "auto";

export type GpsWatchOptions = {
  /** Default `auto` on Android delivery FGS; `moving` if omitted by older callers. */
  cadence?: GpsWatchCadence;
  /** Override min distance (meters) before an update is emitted. */
  minDistanceMeters?: number;
};

/**
 * Native GPS for delivery tracking.
 * Prefer watchPosition while a job is en route; throttle client-side to respect
 * the ~5s server ingest rate limit before calling ingest_delivery_location.
 *
 * Offline: bridge may keep an ephemeral in-memory ring buffer; durable offline
 * queue + flush on reconnect is the **app** responsibility (SQLite / WorkManager).
 */
export interface GpsBridge {
  getLocationPermissionStatus(): Promise<LocationPermissionStatus>;
  requestLocationPermission(): Promise<LocationPermissionStatus>;
  /** One-shot current position (permission-gated on device). */
  getCurrentPosition(): Promise<GpsCoordinate>;
  /**
   * Stream updates for delivery tracking; caller must stop().
   * Starts a location foreground service on Android.
   * Implementations should not call Supabase — UI/service layer posts via RPC.
   */
  watchPosition(
    onUpdate: (coord: GpsCoordinate) => void,
    onError?: (message: string) => void,
    options?: GpsWatchOptions,
  ): Promise<GpsWatchHandle>;
}

/** Helper type: map GpsCoordinate → DeliveryLocationIngest (shared UI may implement). */
export function toDeliveryLocationIngest(
  deliveryJobId: string,
  coord: GpsCoordinate,
): DeliveryLocationIngest {
  return {
    deliveryJobId,
    lat: coord.latitude,
    lng: coord.longitude,
    recordedAt: coord.capturedAt,
    accuracyM: coord.accuracyMeters,
  };
}
