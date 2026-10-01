"use client";

import { useRouter } from "next/navigation";
import { useState, type CSSProperties } from "react";
import { CatalogStorageImage } from "@/components/catalog-storage-image";
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
  imageWidth,
  imageHeight,
}: {
  imageUrl: string | null;
  title?: string;
  hotspots: CatalogDiagramHotspot[];
  activeOem: string | null;
  onHoverOem: (oem: string | null) => void;
  onSelectOem?: (oem: string) => void;
  imageWidth?: number | null;
  imageHeight?: number | null;
}) {
  const router = useRouter();
  const [natural, setNatural] = useState<{ src: string; w: number; h: number } | null>(null);
  const size = natural?.src === imageUrl ? natural : null;
  // Pixel callouts are in source-image pixels: the stored size wins, else the loaded image's.
  const sourceW = imageWidth && imageWidth > 0 ? imageWidth : size?.w;
  const sourceH = imageHeight && imageHeight > 0 ? imageHeight : size?.h;

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
      const style = hotspotStyle(
        {
          x: h.bbox_x,
          y: h.bbox_y,
          width: h.bbox_width,
          height: h.bbox_height,
        },
        sourceW,
        sourceH,
      );
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
        <div className={styles.stage} style={stageStyle(size)}>
          <CatalogStorageImage
            className={styles.diagramImg}
            src={imageUrl}
            alt={title ? `Diagram: ${title}` : "EPC diagram"}
            priority
            variant="diagram"
            width={imageWidth && imageWidth > 0 ? imageWidth : undefined}
            height={imageHeight && imageHeight > 0 ? imageHeight : undefined}
            onNaturalSize={(w, h) => setNatural({ src: imageUrl, w, h })}
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
        </div>
        <p className={styles.caption}>
          {boxed.length
            ? `${boxed.length} hotspot${boxed.length === 1 ? "" : "s"}`
            : "No hotspots on this diagram"}
        </p>
      </div>
    </div>
  );
}

/** Shrink the stage to the drawn image so callout percentages track it (no letterboxing). */
function stageStyle(size: { w: number; h: number } | null): CSSProperties | undefined {
  if (!size || !(size.w > 0) || !(size.h > 0)) return undefined;
  return { width: `min(100%, calc(var(--epc-max-h) * ${(size.w / size.h).toFixed(4)}))` };
}
