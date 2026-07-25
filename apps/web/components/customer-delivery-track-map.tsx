"use client";

import { useEffect, useRef } from "react";
import { Map as MapLibreMap, Marker, NavigationControl } from "maplibre-gl";
import "maplibre-gl/dist/maplibre-gl.css";
import styles from "@/components/staff-delivery-live-map.module.css";
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

  useEffect(() => {
    if (!containerRef.current || mapRef.current) return;
    const initial = initialRef.current;

    const map = new MapLibreMap({
      container: containerRef.current,
      style: styleUrl,
      center: [initial.lng, initial.lat],
      zoom: DEFAULT_ZOOM,
    });
    map.addControl(new NavigationControl({ showCompass: false }), "top-right");
    mapRef.current = map;

    markerRef.current = new Marker({ color: "#C8102E" })
      .setLngLat([initial.lng, initial.lat])
      .addTo(map);

    return () => {
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
