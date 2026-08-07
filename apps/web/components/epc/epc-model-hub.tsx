"use client";

import { useEffect, useState } from "react";
import { EpcAuthGate, EpcEmptyState } from "@/components/epc/epc-auth-gate";
import { EpcBreadcrumb } from "@/components/epc/epc-breadcrumb";
import { EpcBrowseLayout, EpcSkeleton } from "@/components/epc/epc-browse-layout";
import { EpcModelGrid } from "@/components/epc/epc-model-grid";
import {
  catalogPath,
  listCatalogModels,
  type CatalogModel,
} from "@/lib/catalog-hierarchy";

type Status =
  | { kind: "loading" }
  | { kind: "auth" }
  | { kind: "error"; message: string }
  | { kind: "ready"; models: CatalogModel[] };

export function EpcModelHub({ maker }: { maker: string }) {
  const [status, setStatus] = useState<Status>({ kind: "loading" });
  const path = catalogPath({ maker });

  useEffect(() => {
    let cancelled = false;
    async function run() {
      const { createWebClient } = await import("@/lib/supabase");
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
      const result = await listCatalogModels(client, maker);
      if (cancelled) return;
      if (!result.ok) {
        setStatus({ kind: "error", message: result.error });
        return;
      }
      setStatus({ kind: "ready", models: result.data });
    }
    void run();
    return () => {
      cancelled = true;
    };
  }, [maker]);

  if (status.kind === "loading") {
    return (
      <EpcBrowseLayout title={maker}>
        <EpcSkeleton />
      </EpcBrowseLayout>
    );
  }
  if (status.kind === "auth") return <EpcAuthGate nextPath={path} />;
  if (status.kind === "error") {
    return (
      <EpcBrowseLayout title={maker}>
        <EpcEmptyState message={status.message} />
      </EpcBrowseLayout>
    );
  }

  return (
    <EpcBrowseLayout title={maker} lede="Select a model (A–Z).">
      <EpcBreadcrumb ctx={{ maker }} labels={{ maker }} />
      {status.models.length === 0 ? (
        <EpcEmptyState message="No models for this maker." />
      ) : (
        <EpcModelGrid maker={maker} models={status.models} />
      )}
    </EpcBrowseLayout>
  );
}
