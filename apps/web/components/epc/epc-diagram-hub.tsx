"use client";

import { useEffect, useState } from "react";
import { EpcAuthGate, EpcEmptyState } from "@/components/epc/epc-auth-gate";
import { EpcBrowseLayout, EpcSkeleton } from "@/components/epc/epc-browse-layout";
import { EpcDiagramPage } from "@/components/epc/epc-diagram-page";
import type { EpcPartRow } from "@/components/epc/epc-parts-table";
import {
  catalogPath,
  getCatalogDiagram,
  type CatalogDiagramResponse,
} from "@/lib/catalog-hierarchy";
import { createWebClient } from "@/lib/supabase";

type Status =
  | { kind: "loading" }
  | { kind: "auth" }
  | { kind: "error"; message: string }
  | {
      kind: "ready";
      data: CatalogDiagramResponse;
      extras: Record<
        string,
        { usd?: number | null; stock?: EpcPartRow["stock"] }
      >;
    };

async function loadPriceExtras(
  client: NonNullable<ReturnType<typeof createWebClient>>,
  stockIds: string[],
): Promise<
  Record<string, { usd?: number | null; stock?: EpcPartRow["stock"] }>
> {
  const extras: Record<
    string,
    { usd?: number | null; stock?: EpcPartRow["stock"] }
  > = {};
  if (stockIds.length === 0) return extras;

  const { data: items } = await client
    .from("stock_items")
    .select("id, oem_part_number")
    .in("id", stockIds);
  if (!items?.length) return extras;

  const { data: prices } = await client
    .from("price_list_items")
    .select("stock_item_id, unit_price, price_lists!inner(code)")
    .in(
      "stock_item_id",
      items.map((i) => i.id),
    )
    .eq("price_lists.code", "RETAIL");

  const priceById = new Map<string, number>();
  for (const row of prices ?? []) {
    priceById.set(row.stock_item_id, Number(row.unit_price));
  }
  for (const item of items) {
    extras[item.oem_part_number.toUpperCase()] = {
      usd: priceById.get(item.id) ?? null,
      stock: "in_stock",
    };
  }
  return extras;
}

export function EpcDiagramHub({
  maker,
  model,
  variant,
  section,
}: {
  maker: string;
  model: string;
  variant: string;
  section: string;
}) {
  const [status, setStatus] = useState<Status>({ kind: "loading" });
  const path = catalogPath({ maker, model, variant, section });

  useEffect(() => {
    let cancelled = false;
    async function run() {
      const client = createWebClient();
      if (!client) {
        if (!cancelled) {
          setStatus({
            kind: "error",
            message: "Supabase is not configured on this environment.",
          });
        }
        return;
      }
      const { data: session } = await client.auth.getSession();
      if (!session.session) {
        if (!cancelled) setStatus({ kind: "auth" });
        return;
      }
      const result = await getCatalogDiagram(
        client,
        maker,
        model,
        variant,
        section,
      );
      if (cancelled) return;
      if (!result.ok) {
        setStatus({ kind: "error", message: result.error });
        return;
      }
      const stockIds = result.data.parts
        .map((p) => p.stock_item_id)
        .filter((id): id is string => Boolean(id));
      const extras = await loadPriceExtras(client, stockIds);
      if (cancelled) return;
      setStatus({ kind: "ready", data: result.data, extras });
    }
    void run();
    return () => {
      cancelled = true;
    };
  }, [maker, model, variant, section]);

  if (status.kind === "loading") {
    return (
      <EpcBrowseLayout title={section}>
        <EpcSkeleton label="Loading diagram…" />
      </EpcBrowseLayout>
    );
  }
  if (status.kind === "auth") return <EpcAuthGate nextPath={path} />;
  if (status.kind === "error") {
    return (
      <EpcBrowseLayout title={section}>
        <EpcEmptyState message={status.message} />
      </EpcBrowseLayout>
    );
  }

  return (
    <EpcDiagramPage
      ctx={{ maker, model, variant, section }}
      data={status.data}
      partExtras={status.extras}
      labels={{ maker, model, variant, section }}
    />
  );
}
