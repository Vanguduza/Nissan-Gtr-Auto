"use client";

import Link from "next/link";
import { useEffect, useState, type CSSProperties } from "react";
import {
  loadOemCatalogDiagram,
  loadSampleCatalogDiagram,
  type CatalogDiagram,
  type DiagramHotspot,
} from "@/lib/catalog-diagram";
import { createWebClient } from "@/lib/supabase";
import styles from "./catalog-canvas-stub.module.css";

type Props = {
  /** When set, load diagram for this OEM from part_fitment + Storage. */
  oem?: string;
  /** Preloaded diagram (skips fetch). */
  diagram?: CatalogDiagram | null;
  /** Fetch any sample diagram (catalog home / PLP teaser). */
  sample?: boolean;
  className?: string;
};

function hotspotStyle(h: DiagramHotspot): CSSProperties | null {
  if (
    h.x == null ||
    h.y == null ||
    h.width == null ||
    h.height == null ||
    !(h.width > 0) ||
    !(h.height > 0)
  ) {
    return null;
  }
  // Values may be 0–1 fractions or absolute pixels; prefer fraction layout.
  const asFraction = h.x <= 1 && h.y <= 1 && h.width <= 1 && h.height <= 1;
  if (asFraction) {
    return {
      left: `${h.x * 100}%`,
      top: `${h.y * 100}%`,
      width: `${h.width * 100}%`,
      height: `${h.height * 100}%`,
    };
  }
  return {
    left: h.x,
    top: h.y,
    width: h.width,
    height: h.height,
  };
}

function GapFrame({ message }: { message: string }) {
  return (
    <div className={styles.wrap} role="img" aria-label="Parts diagram canvas">
      <div className={styles.frame}>
        <span className={styles.callout} style={{ left: "22%", top: "28%" }}>
          01
        </span>
        <span className={styles.callout} style={{ left: "58%", top: "42%" }}>
          02
        </span>
        <span className={styles.callout} style={{ left: "40%", top: "68%" }}>
          03
        </span>
        <p className={styles.caption}>{message}</p>
      </div>
    </div>
  );
}

function DiagramFrame({
  diagram,
  className,
}: {
  diagram: CatalogDiagram;
  className?: string;
}) {
  const boxed = diagram.hotspots.filter((h) => hotspotStyle(h));
  return (
    <div
      className={[styles.wrap, className].filter(Boolean).join(" ")}
      aria-label="Parts diagram canvas"
    >
      <div className={`${styles.frame} ${styles.frameLive}`}>
        {/* Storage public URL — next/image needs remotePatterns per project; use img. */}
        {/* eslint-disable-next-line @next/next/no-img-element */}
        <img
          className={styles.diagramImg}
          src={diagram.publicUrl}
          alt={`Catalog diagram ${diagram.path}`}
        />
        {boxed.map((h, i) => {
          const style = hotspotStyle(h);
          if (!style) return null;
          return (
            <Link
              key={h.id}
              href={`/parts/${encodeURIComponent(h.oem)}`}
              className={styles.hotspot}
              style={style}
              title={h.oem}
            >
              <span className={styles.hotspotLabel}>
                {String(i + 1).padStart(2, "0")}
              </span>
            </Link>
          );
        })}
        <p className={styles.caption}>
          {boxed.length
            ? `${boxed.length} hotspot${boxed.length === 1 ? "" : "s"} · catalog-diagrams`
            : "Diagram from catalog-diagrams (no bbox hotspots on this asset yet)."}
        </p>
      </div>
    </div>
  );
}

const GAP_COPY =
  "No diagram image yet. After db reset, run: node supabase/seed_catalog_diagrams.mjs --docker (Navara fixtures → catalog-diagrams). part_fitment.diagram_path may already be set.";

/**
 * Visual catalog canvas. Renders real Storage diagram + hotspots when
 * `part_fitment.diagram_path` is set; otherwise documents the pipeline gap.
 */
export function CatalogCanvasStub({
  oem,
  diagram: diagramProp,
  sample = false,
  className,
}: Props) {
  const [diagram, setDiagram] = useState<CatalogDiagram | null | undefined>(
    diagramProp !== undefined ? diagramProp : undefined,
  );
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (diagramProp !== undefined) {
      setDiagram(diagramProp);
      return;
    }
    if (!oem && !sample) {
      setDiagram(null);
      return;
    }

    let cancelled = false;
    async function run() {
      const client = createWebClient();
      if (!client) {
        if (!cancelled) setDiagram(null);
        return;
      }
      const { data: session } = await client.auth.getSession();
      if (!session.session) {
        // part_fitment SELECT is authenticated-only — keep gap stub.
        if (!cancelled) setDiagram(null);
        return;
      }
      const result = oem
        ? await loadOemCatalogDiagram(client, oem)
        : await loadSampleCatalogDiagram(client);
      if (cancelled) return;
      if (!result.ok) {
        setError(result.error);
        setDiagram(null);
        return;
      }
      setError(null);
      setDiagram(result.data);
    }
    void run();
    return () => {
      cancelled = true;
    };
  }, [oem, sample, diagramProp]);

  if (diagram === undefined) {
    return (
      <div className={styles.wrap}>
        <div className={`${styles.frame} ${styles.frameLoading}`}>
          <p className={styles.caption}>Loading diagram…</p>
        </div>
      </div>
    );
  }

  if (diagram) {
    return <DiagramFrame diagram={diagram} className={className} />;
  }

  return (
    <GapFrame
      message={
        error
          ? `Diagram unavailable (${error}). ${GAP_COPY}`
          : GAP_COPY
      }
    />
  );
}
