"use client";

import { useEffect, useRef, useState } from "react";
import {
  LngLatBounds,
  Map as MapLibreMap,
  Marker,
  NavigationControl,
  type GeoJSONSource,
} from "maplibre-gl";
import "maplibre-gl/dist/maplibre-gl.css";
import styles from "@/components/staff-delivery-live-map.module.css";
import {
  isStyleLoadError,
  MAP_STYLE_RASTER_FALLBACK,
  mapStyleUrl,
} from "@/lib/map-basemap";
import type { DeliveryLocationPoint } from "@/lib/staff-delivery-tracking";

const TRAIL_SOURCE = "delivery-trail";
const TRAIL_LAYER = "delivery-trail-line";
/** Harare CBD — default view when a job has no GPS points yet. */
export const HARARE_CENTER: [number, number] = [31.0522, -17.8292];
const DEFAULT_ZOOM = 12;

// Intentionally omit MapLibre GeolocateControl — web must never use browser GPS.

type Props = {
  points: DeliveryLocationPoint[];
  /** When false, map still renders historical trail but status shows offline. */
  live: boolean;
  /** Job ETA from delivery_jobs (staff panel). */
  etaLabel?: string | null;
};

type TrailGeoJSON = {
  type: "FeatureCollection";
  features: Array<{
    type: "Feature";
    properties: Record<string, never>;
    geometry: {
      type: "LineString";
      coordinates: [number, number][];
    };
  }>;
};

function toLngLat(point: DeliveryLocationPoint): [number, number] {
  return [point.lng, point.lat];
}

function trailFeatureCollection(points: DeliveryLocationPoint[]): TrailGeoJSON {
  if (points.length < 2) {
    return { type: "FeatureCollection", features: [] };
  }
  return {
    type: "FeatureCollection",
    features: [
      {
        type: "Feature",
        properties: {},
        geometry: {
          type: "LineString",
          coordinates: points.map(toLngLat),
        },
      },
    ],
  };
}

function ensureTrailLayers(map: MapLibreMap) {
  if (map.getSource(TRAIL_SOURCE)) return;
  map.addSource(TRAIL_SOURCE, {
    type: "geojson",
    data: { type: "FeatureCollection", features: [] },
  });
  map.addLayer({
    id: TRAIL_LAYER,
    type: "line",
    source: TRAIL_SOURCE,
    layout: { "line-join": "round", "line-cap": "round" },
    paint: {
      "line-color": "#C8102E",
      "line-width": 3,
      "line-opacity": 0.85,
    },
  });
}

/**
 * Staff dispatcher map: renders bridge-fed points only.
 * Does NOT call navigator.geolocation or any browser GPS API.
 */
export function StaffDeliveryLiveMap({ points, live, etaLabel }: Props) {
  const containerRef = useRef<HTMLDivElement | null>(null);
  const mapRef = useRef<MapLibreMap | null>(null);
  const markerRef = useRef<Marker | null>(null);
  const readyRef = useRef(false);
  const fittedJobKeyRef = useRef<string | null>(null);
  const usedRasterFallbackRef = useRef(false);
  const [mapError, setMapError] = useState<string | null>(null);
  const [usingRasterFallback, setUsingRasterFallback] = useState(false);

  useEffect(() => {
    if (!containerRef.current || mapRef.current) return;

    const container = containerRef.current;
    usedRasterFallbackRef.current = false;
    setMapError(null);
    setUsingRasterFallback(false);

    const map = new MapLibreMap({
      container,
      style: mapStyleUrl(),
      center: HARARE_CENTER,
      zoom: DEFAULT_ZOOM,
    });
    map.addControl(
      new NavigationControl({ showCompass: false }),
      "top-right",
    );
    mapRef.current = map;

    const markReady = () => {
      map.resize();
      ensureTrailLayers(map);
      readyRef.current = true;
      setMapError(null);
    };

    const applyRasterFallback = (reason: string) => {
      if (usedRasterFallbackRef.current) {
        setMapError(
          `Map basemap failed to load (${reason}). Set NEXT_PUBLIC_MAP_STYLE_URL to a MapLibre style JSON URL, or check network access to basemaps.cartocdn.com.`,
        );
        return;
      }
      usedRasterFallbackRef.current = true;
      setUsingRasterFallback(true);
      readyRef.current = false;
      map.setStyle(MAP_STYLE_RASTER_FALLBACK);
    };

    map.on("load", markReady);
    map.on("style.load", () => {
      map.resize();
      ensureTrailLayers(map);
      readyRef.current = true;
    });

    map.on("error", (ev) => {
      const err = ev.error;
      const detail =
        err && typeof err === "object" && "message" in err
          ? String((err as { message?: string }).message)
          : "unknown map error";
      if (usedRasterFallbackRef.current && !map.isStyleLoaded()) {
        setMapError(
          `Map basemap failed to load (${detail}). Set NEXT_PUBLIC_MAP_STYLE_URL to a MapLibre style JSON URL, or check network access to basemaps.cartocdn.com.`,
        );
        return;
      }
      if (isStyleLoadError(err)) {
        applyRasterFallback(detail);
      }
    });

    const styleWatchdog = window.setTimeout(() => {
      if (!map.isStyleLoaded() && !usedRasterFallbackRef.current) {
        applyRasterFallback("style load timed out");
      }
    }, 10_000);

    const ro = new ResizeObserver(() => {
      map.resize();
    });
    ro.observe(container);

    return () => {
      window.clearTimeout(styleWatchdog);
      ro.disconnect();
      readyRef.current = false;
      fittedJobKeyRef.current = null;
      markerRef.current?.remove();
      markerRef.current = null;
      map.remove();
      mapRef.current = null;
    };
  }, []);

  useEffect(() => {
    const map = mapRef.current;
    if (!map) return;

    const apply = () => {
      const source = map.getSource(TRAIL_SOURCE) as GeoJSONSource | undefined;
      if (source) {
        source.setData(trailFeatureCollection(points));
      }

      const last = points[points.length - 1];
      if (!last) {
        markerRef.current?.remove();
        markerRef.current = null;
        fittedJobKeyRef.current = null;
        map.easeTo({
          center: HARARE_CENTER,
          zoom: DEFAULT_ZOOM,
          duration: 400,
        });
        return;
      }

      const lngLat = toLngLat(last);
      if (!markerRef.current) {
        markerRef.current = new Marker({ color: "#C8102E" })
          .setLngLat(lngLat)
          .addTo(map);
      } else {
        markerRef.current.setLngLat(lngLat);
      }

      const jobKey = last.delivery_job_id;
      const firstPaint = fittedJobKeyRef.current !== jobKey;
      if (firstPaint) {
        fittedJobKeyRef.current = jobKey;
        if (points.length === 1) {
          map.easeTo({ center: lngLat, zoom: Math.max(map.getZoom(), 13) });
        } else {
          const bounds = new LngLatBounds(lngLat, lngLat);
          for (const p of points) bounds.extend(toLngLat(p));
          map.fitBounds(bounds, { padding: 48, maxZoom: 16, duration: 600 });
        }
      } else {
        map.panTo(lngLat, { duration: 400 });
      }
    };

    if (readyRef.current && map.isStyleLoaded()) {
      apply();
    } else {
      map.once("load", apply);
      map.once("style.load", apply);
    }
  }, [points]);

  const last = points[points.length - 1];

  return (
    <div className={styles.wrap}>
      <p className={styles.statusRow}>
        <span>
          <span
            className={live ? styles.liveDot : styles.liveDotOff}
            aria-hidden
          />
          {live ? "Live" : "Offline"}
        </span>
        <span>
          <strong>Points</strong>
          {points.length}
        </span>
        {etaLabel ? (
          <span>
            <strong>ETA</strong>
            {etaLabel}
          </span>
        ) : null}
        {last ? (
          <span>
            <strong>Last</strong>
            {last.lat.toFixed(5)}, {last.lng.toFixed(5)} ·{" "}
            {new Date(last.recorded_at).toLocaleString()}
          </span>
        ) : (
          <span className={styles.emptyHint}>
            Showing Harare — waiting for delivery-app GPS inserts…
          </span>
        )}
      </p>
      {usingRasterFallback && !mapError ? (
        <p className={styles.mapNotice} role="status">
          Vector basemap unavailable — using raster OSM/CARTO tiles.
        </p>
      ) : null}
      {mapError ? (
        <p className={styles.mapError} role="alert">
          {mapError}
        </p>
      ) : null}
      <div className={styles.mapFrame}>
        <div
          ref={containerRef}
          className={styles.mapCanvas}
          role="img"
          aria-label="Delivery job live map (staff subscribe-only)"
        />
      </div>
    </div>
  );
}
