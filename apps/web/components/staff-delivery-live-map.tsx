"use client";

import { useEffect, useRef } from "react";
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
  mapStyleUrl,
  type DeliveryLocationPoint,
} from "@/lib/staff-delivery-tracking";

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

  useEffect(() => {
    if (!containerRef.current || mapRef.current) return;

    const map = new MapLibreMap({
      container: containerRef.current,
      style: mapStyleUrl(),
      center: HARARE_CENTER,
      zoom: DEFAULT_ZOOM,
    });
    map.addControl(
      new NavigationControl({ showCompass: false }),
      "top-right",
    );
    mapRef.current = map;

    map.on("load", () => {
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
      readyRef.current = true;
    });

    return () => {
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
          {live ? "Realtime subscribed" : "Not subscribed"}
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
