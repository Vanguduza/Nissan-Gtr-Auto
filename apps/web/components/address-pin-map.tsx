"use client";

import { useEffect, useRef, useState } from "react";
import { Map as MapLibreMap, Marker, NavigationControl } from "maplibre-gl";
import "maplibre-gl/dist/maplibre-gl.css";
import styles from "@/components/staff-delivery-live-map.module.css";
import {
  isStyleLoadError,
  MAP_STYLE_RASTER_FALLBACK,
  mapStyleUrl,
} from "@/lib/map-basemap";

const DEFAULT_CENTER: [number, number] = [31.0522, -17.8292]; // Harare
const DEFAULT_ZOOM = 12;

type Props = {
  lat: number | null;
  lng: number | null;
  onPick: (lat: number, lng: number) => void;
  disabled?: boolean;
};

/**
 * Click-to-pin MapLibre for customer address drafts — no browser GPS SoR.
 * Adopts patterns from CustomerDeliveryTrackMap.
 */
export function AddressPinMap({ lat, lng, onPick, disabled }: Props) {
  const containerRef = useRef<HTMLDivElement | null>(null);
  const mapRef = useRef<MapLibreMap | null>(null);
  const markerRef = useRef<Marker | null>(null);
  const usedRasterFallbackRef = useRef(false);
  const [mapError, setMapError] = useState<string | null>(null);
  const styleUrl = mapStyleUrl();
  const onPickRef = useRef(onPick);
  onPickRef.current = onPick;

  useEffect(() => {
    if (!containerRef.current || mapRef.current) return;
    const container = containerRef.current;
    usedRasterFallbackRef.current = false;
    setMapError(null);

    const center: [number, number] =
      lat != null && lng != null ? [lng, lat] : DEFAULT_CENTER;

    const map = new MapLibreMap({
      container,
      style: styleUrl,
      center,
      zoom: DEFAULT_ZOOM,
    });
    map.addControl(new NavigationControl({ showCompass: false }), "top-right");
    mapRef.current = map;

    if (lat != null && lng != null) {
      markerRef.current = new Marker({ color: "#C8102E" })
        .setLngLat([lng, lat])
        .addTo(map);
    }

    const applyRasterFallback = (reason: string) => {
      if (usedRasterFallbackRef.current) {
        setMapError(
          `Map basemap failed (${reason}). Check NEXT_PUBLIC_MAP_STYLE_URL.`,
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
      if (isStyleLoadError(err)) applyRasterFallback(detail);
    });

    map.on("click", (e) => {
      if (disabled) return;
      const { lat: clickLat, lng: clickLng } = e.lngLat;
      onPickRef.current(clickLat, clickLng);
    });

    const ro = new ResizeObserver(() => map.resize());
    ro.observe(container);

    return () => {
      ro.disconnect();
      markerRef.current?.remove();
      markerRef.current = null;
      map.remove();
      mapRef.current = null;
    };
    // styleUrl only — pin moves via separate effect
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [styleUrl, disabled]);

  useEffect(() => {
    const map = mapRef.current;
    if (!map || lat == null || lng == null) return;
    const lngLat: [number, number] = [lng, lat];
    if (!markerRef.current) {
      markerRef.current = new Marker({ color: "#C8102E" })
        .setLngLat(lngLat)
        .addTo(map);
    } else {
      markerRef.current.setLngLat(lngLat);
    }
  }, [lat, lng]);

  return (
    <div className={styles.wrap}>
      <p className={styles.mapHint ?? undefined} style={{ marginBottom: 8 }}>
        Tap the map to set a delivery pin (optional). Stored as geo tag on
        apartment / line 2 — same as the Android app.
      </p>
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
          aria-label="Tap to set address pin"
          style={{ minHeight: 220 }}
        />
      </div>
      {lat != null && lng != null ? (
        <p style={{ marginTop: 8, fontSize: "0.85rem", opacity: 0.8 }}>
          Pin: {lat.toFixed(5)}, {lng.toFixed(5)}
        </p>
      ) : null}
    </div>
  );
}
