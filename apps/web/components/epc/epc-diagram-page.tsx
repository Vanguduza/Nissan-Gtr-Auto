"use client";

import { useEffect, useMemo, useState } from "react";
import { EpcBreadcrumb } from "@/components/epc/epc-breadcrumb";
import { EpcBrowseLayout } from "@/components/epc/epc-browse-layout";
import { EpcDiagramCanvas } from "@/components/epc/epc-diagram-canvas";
import {
  EpcPartsTable,
  type EpcPartRow,
} from "@/components/epc/epc-parts-table";
import { resolveDiagramImageUrl } from "@/lib/catalog-diagram";
import {
  saveEpcContext,
  type CatalogBrowseContext,
  type CatalogDiagramResponse,
} from "@/lib/catalog-hierarchy";
import { createWebClient } from "@/lib/supabase";
import styles from "./epc-diagram.module.css";

export function EpcDiagramPage({
  ctx,
  data,
  partExtras,
  labels,
}: {
  ctx: CatalogBrowseContext;
  data: CatalogDiagramResponse;
  /** Optional price/stock overlay keyed by OEM (uppercased). */
  partExtras?: Record<
    string,
    { usd?: number | null; stock?: EpcPartRow["stock"] }
  >;
  labels?: Partial<
    Record<"maker" | "model" | "variant" | "section", string>
  >;
}) {
  const [activeOem, setActiveOem] = useState<string | null>(null);
  const [imageUrl, setImageUrl] = useState<string | null>(null);

  useEffect(() => {
    saveEpcContext(ctx);
  }, [ctx]);

  useEffect(() => {
    const client = createWebClient();
    if (!client || !data.diagram) {
      setImageUrl(data.diagram?.image_url ?? null);
      return;
    }
    setImageUrl(resolveDiagramImageUrl(client, data.diagram));
  }, [data.diagram]);

  const parts: EpcPartRow[] = useMemo(
    () =>
      data.parts.map((p) => {
        const extra = partExtras?.[p.oem_part_number.toUpperCase()];
        return {
          ...p,
          usd: extra?.usd ?? null,
          stock: extra?.stock ?? (p.stock_item_id ? "in_stock" : null),
        };
      }),
    [data.parts, partExtras],
  );

  const diagramMissing = !data.diagram && parts.length > 0;

  return (
    <EpcBrowseLayout
      title={data.diagram?.title ?? labels?.section ?? "Diagram"}
      lede="Hover a hotspot or table row. Click to open the part page."
    >
      <EpcBreadcrumb ctx={ctx} labels={labels} />
      {diagramMissing ? (
        <p className={styles.banner} role="status">
          Diagram not published for this section — parts list only.
        </p>
      ) : null}
      <div className={styles.split}>
        <EpcDiagramCanvas
          imageUrl={imageUrl}
          title={data.diagram?.title}
          hotspots={data.hotspots}
          activeOem={activeOem}
          onHoverOem={setActiveOem}
        />
        <EpcPartsTable
          parts={parts}
          activeOem={activeOem}
          onHoverOem={setActiveOem}
        />
      </div>
    </EpcBrowseLayout>
  );
}
