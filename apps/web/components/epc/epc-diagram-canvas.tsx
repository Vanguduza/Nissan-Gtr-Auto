"use client";

import { useRouter } from "next/navigation";
import type { CSSProperties } from "react";
import { hotspotStyle } from "@/lib/catalog-diagram";
import { partHref } from "@/lib/catalog-search";
import type { CatalogDiagramHotspot } from "@/lib/catalog-hierarchy";
import styles from "./epc-diagram.module.css";

export function EpcDiagramCanvas({
  imageUrl,
  title,
  hotspots,
  activeOem,
  onHoverOem,
  onSelectOem,
}: {
  imageUrl: string | null;
  title?: string;
  hotspots: CatalogDiagramHotspot[];
  activeOem: string | null;
  onHoverOem: (oem: string | null) => void;
  onSelectOem?: (oem: string) => void;
}) {
  const router = useRouter();

  if (!imageUrl) {
    return (
      <div className={styles.canvasWrap}>
        <div className={styles.frame}>
          <p className={styles.caption}>
            Diagram image unavailable. Parts list still works below.
          </p>
        </div>
      </div>
    );
  }

  const boxed = hotspots
    .map((h, i) => {
      const style = hotspotStyle({
        x: h.bbox_x,
        y: h.bbox_y,
        width: h.bbox_width,
        height: h.bbox_height,
      });
      return style ? { h, style, i } : null;
    })
    .filter(Boolean) as {
    h: CatalogDiagramHotspot;
    style: CSSProperties;
    i: number;
  }[];

  return (
    <div className={styles.canvasWrap}>
      <div className={styles.frame}>
        {/* eslint-disable-next-line @next/next/no-img-element */}
        <img
          className={styles.diagramImg}
          src={imageUrl}
          alt={title ? `Diagram: ${title}` : "EPC diagram"}
        />
        {boxed.map(({ h, style, i }) => (
          <button
            key={`${h.oem}-${i}`}
            type="button"
            className={[
              styles.hotspot,
              activeOem === h.oem ? styles.hotspotActive : "",
            ]
              .filter(Boolean)
              .join(" ")}
            style={style}
            title={h.oem}
            onMouseEnter={() => onHoverOem(h.oem)}
            onMouseLeave={() => onHoverOem(null)}
            onFocus={() => onHoverOem(h.oem)}
            onBlur={() => onHoverOem(null)}
            onClick={() => {
              onSelectOem?.(h.oem);
              const base = partHref(h.oem);
              if (!base) return;
              router.push(`${base}${base.includes("?") ? "&" : "?"}from=epc`);
            }}
          >
            <span className={styles.hotspotLabel}>
              {String(i + 1).padStart(2, "0")}
            </span>
          </button>
        ))}
        <p className={styles.caption}>
          {boxed.length
            ? `${boxed.length} hotspot${boxed.length === 1 ? "" : "s"}`
            : "No hotspots on this diagram"}
        </p>
      </div>
    </div>
  );
}
