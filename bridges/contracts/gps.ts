/**
 * Bridge contracts only — Phase 11–12.
 * No browser geolocation / WebView GPS — native FusedLocation / CoreLocation only in impl dirs.
 */

export type GpsCoordinate = {
  latitude: number;
  longitude: number;
  accuracyMeters?: number;
  /** ISO timestamp from device */
  capturedAt: string;
};

export type GpsWatchHandle = {
  stop(): Promise<void>;
};

export interface GpsBridge {
  /** One-shot current position (permission-gated on device). */
  getCurrentPosition(): Promise<GpsCoordinate>;
  /** Stream updates for delivery tracking; caller must stop(). */
  watchPosition(
    onUpdate: (coord: GpsCoordinate) => void,
    onError?: (message: string) => void,
  ): Promise<GpsWatchHandle>;
}
