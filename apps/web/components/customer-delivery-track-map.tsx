"use client";

import { useEffect, useRef, useState } from "react";
import { Map as MapLibreMap, Marker, NavigationControl } from "maplibre-gl";
import "maplibre-gl/dist/maplibre-gl.css";
import styles from "@/components/staff-delivery-live-map.module.css";
import {
  isStyleLoadError,
  MAP_STYLE_RASTER_FALLBACK,
} from "@/lib/map-basemap";
import type { CustomerTrackPoint } from "@/lib/customer-delivery-track";

const DEFAULT_ZOOM = 13;

type Props = {
  point: CustomerTrackPoint;
  styleUrl: string;
};

/**
 * Customer last-point map only — no trail layers, no browser GPS.
 */
export function CustomerDeliveryTrackMap({ point, styleUrl }: Props) {
  const containerRef = useRef<HTMLDivElement | null>(null);
  const mapRef = useRef<MapLibreMap | null>(null);
  const markerRef = useRef<Marker | null>(null);
  const initialRef = useRef(point);
  const usedRasterFallbackRef = useRef(false);
  const [mapError, setMapError] = useState<string | null>(null);

  useEffect(() => {
    if (!containerRef.current || mapRef.current) return;
    const initial = initialRef.current;
    const container = containerRef.current;
    usedRasterFallbackRef.current = false;
    setMapError(null);

    const map = new MapLibreMap({
      container,
      style: styleUrl,
      center: [initial.lng, initial.lat],
      zoom: DEFAULT_ZOOM,
    });
    map.addControl(new NavigationControl({ showCompass: false }), "top-right");
    mapRef.current = map;

    markerRef.current = new Marker({ color: "#C8102E" })
      .setLngLat([initial.lng, initial.lat])
      .addTo(map);

    const applyRasterFallback = (reason: string) => {
      if (usedRasterFallbackRef.current) {
        setMapError(
          `Map basemap failed to load (${reason}). Check NEXT_PUBLIC_MAP_STYLE_URL or network access to tile hosts.`,
        );
        return;
      }
      usedRasterFallbackRef.current = true;
      map.setStyle(MAP_STYLE_RASTER_FALLBACK);
    };

    map.on("load", () => {
      map.resize();
      setMapError(null);
    });
    map.on("style.load", () => map.resize());
    map.on("error", (ev) => {
      const err = ev.error;
      const detail =
        err && typeof err === "object" && "message" in err
          ? String((err as { message?: string }).message)
          : "style error";
      if (usedRasterFallbackRef.current && !map.isStyleLoaded()) {
        setMapError(
          `Map basemap failed to load (${detail}). Check NEXT_PUBLIC_MAP_STYLE_URL or network access to tile hosts.`,
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

    const ro = new ResizeObserver(() => map.resize());
    ro.observe(container);

    return () => {
      window.clearTimeout(styleWatchdog);
      ro.disconnect();
      markerRef.current?.remove();
      markerRef.current = null;
      map.remove();
      mapRef.current = null;
    };
  }, [styleUrl]);

  useEffect(() => {
    const map = mapRef.current;
    if (!map) return;
    const lngLat: [number, number] = [point.lng, point.lat];
    if (!markerRef.current) {
      markerRef.current = new Marker({ color: "#C8102E" })
        .setLngLat(lngLat)
        .addTo(map);
    } else {
      markerRef.current.setLngLat(lngLat);
    }
    map.panTo(lngLat, { duration: 400 });
  }, [point.lat, point.lng]);

  return (
    <div className={styles.wrap}>
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
          aria-label="Delivery last known location"
        />
      </div>
    </div>
  );
}
