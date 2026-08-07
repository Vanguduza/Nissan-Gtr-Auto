"use client";

import { useEffect, useState } from "react";
import { EpcAuthGate, EpcEmptyState } from "@/components/epc/epc-auth-gate";
import { EpcBreadcrumb } from "@/components/epc/epc-breadcrumb";
import { EpcBrowseLayout, EpcSkeleton } from "@/components/epc/epc-browse-layout";
import { EpcVariantList } from "@/components/epc/epc-variant-list";
import {
  catalogPath,
  listCatalogVariants,
  type CatalogVariant,
} from "@/lib/catalog-hierarchy";
import { createWebClient } from "@/lib/supabase";

type Status =
  | { kind: "loading" }
  | { kind: "auth" }
  | { kind: "error"; message: string }
  | { kind: "ready"; variants: CatalogVariant[] };

export function EpcVariantHub({
  maker,
  model,
}: {
  maker: string;
  model: string;
}) {
  const [status, setStatus] = useState<Status>({ kind: "loading" });
  const path = catalogPath({ maker, model });

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
      const result = await listCatalogVariants(client, maker, model);
      if (cancelled) return;
      if (!result.ok) {
        setStatus({ kind: "error", message: result.error });
        return;
      }
      setStatus({ kind: "ready", variants: result.data });
    }
    void run();
    return () => {
      cancelled = true;
    };
  }, [maker, model]);

  if (status.kind === "loading") {
    return (
      <EpcBrowseLayout title={model}>
        <EpcSkeleton />
      </EpcBrowseLayout>
    );
  }
  if (status.kind === "auth") return <EpcAuthGate nextPath={path} />;
  if (status.kind === "error") {
    return (
      <EpcBrowseLayout title={model}>
        <EpcEmptyState message={status.message} />
      </EpcBrowseLayout>
    );
  }

  return (
    <EpcBrowseLayout title={model} lede="Pick a chassis / variant.">
      <EpcBreadcrumb
        ctx={{ maker, model }}
        labels={{ maker, model }}
      />
      {status.variants.length === 0 ? (
        <EpcEmptyState message="No variants for this model." />
      ) : (
        <EpcVariantList maker={maker} model={model} variants={status.variants} />
      )}
    </EpcBrowseLayout>
  );
}
