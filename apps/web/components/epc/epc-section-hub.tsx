"use client";

import { useEffect, useState } from "react";
import { EpcAuthGate, EpcEmptyState } from "@/components/epc/epc-auth-gate";
import { EpcBreadcrumb } from "@/components/epc/epc-breadcrumb";
import { EpcBrowseLayout, EpcSkeleton } from "@/components/epc/epc-browse-layout";
import { EpcSectionGrid } from "@/components/epc/epc-section-grid";
import {
  catalogPath,
  listCatalogSections,
  saveEpcContext,
  type CatalogSection,
} from "@/lib/catalog-hierarchy";
import { createWebClient } from "@/lib/supabase";

type Status =
  | { kind: "loading" }
  | { kind: "auth" }
  | { kind: "error"; message: string }
  | { kind: "ready"; sections: CatalogSection[] };

export function EpcSectionHub({
  maker,
  model,
  variant,
}: {
  maker: string;
  model: string;
  variant: string;
}) {
  const [status, setStatus] = useState<Status>({ kind: "loading" });
  const path = catalogPath({ maker, model, variant });

  useEffect(() => {
    saveEpcContext({ maker, model, variant });
  }, [maker, model, variant]);

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
      const result = await listCatalogSections(client, maker, model, variant);
      if (cancelled) return;
      if (!result.ok) {
        setStatus({ kind: "error", message: result.error });
        return;
      }
      setStatus({ kind: "ready", sections: result.data });
    }
    void run();
    return () => {
      cancelled = true;
    };
  }, [maker, model, variant]);

  if (status.kind === "loading") {
    return (
      <EpcBrowseLayout title={variant}>
        <EpcSkeleton />
      </EpcBrowseLayout>
    );
  }
  if (status.kind === "auth") return <EpcAuthGate nextPath={path} />;
  if (status.kind === "error") {
    return (
      <EpcBrowseLayout title={variant}>
        <EpcEmptyState message={status.message} />
      </EpcBrowseLayout>
    );
  }

  return (
    <EpcBrowseLayout title={variant} lede="Choose a diagram section.">
      <EpcBreadcrumb
        ctx={{ maker, model, variant }}
        labels={{ maker, model, variant }}
      />
      {status.sections.length === 0 ? (
        <EpcEmptyState message="No sections for this variant." />
      ) : (
        <EpcSectionGrid
          maker={maker}
          model={model}
          variant={variant}
          sections={status.sections}
        />
      )}
    </EpcBrowseLayout>
  );
}
