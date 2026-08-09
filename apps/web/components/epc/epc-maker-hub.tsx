"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { EpcAuthGate, EpcEmptyState } from "@/components/epc/epc-auth-gate";
import { EpcBrowseLayout, EpcSkeleton } from "@/components/epc/epc-browse-layout";
import { EpcMakerGrid } from "@/components/epc/epc-maker-grid";
import { listCatalogMakers, type CatalogMaker } from "@/lib/catalog-hierarchy";
import { createWebClient } from "@/lib/supabase";
import layoutStyles from "./epc-browse-layout.module.css";

type Status =
  | { kind: "loading" }
  | { kind: "auth" }
  | { kind: "error"; message: string }
  | { kind: "ready"; makers: CatalogMaker[] };

export function EpcMakerHub() {
  const [status, setStatus] = useState<Status>({ kind: "loading" });

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
      const result = await listCatalogMakers(client);
      if (cancelled) return;
      if (!result.ok) {
        setStatus({ kind: "error", message: result.error });
        return;
      }
      setStatus({ kind: "ready", makers: result.data });
    }
    void run();
    return () => {
      cancelled = true;
    };
  }, []);

  if (status.kind === "loading") {
    return (
      <EpcBrowseLayout title="Parts catalog">
        <EpcSkeleton />
      </EpcBrowseLayout>
    );
  }
  if (status.kind === "auth") return <EpcAuthGate nextPath="/catalog" />;
  if (status.kind === "error") {
    return (
      <EpcBrowseLayout title="Parts catalog">
        <EpcEmptyState message={status.message} />
      </EpcBrowseLayout>
    );
  }
  if (status.makers.length === 0) {
    return (
      <EpcBrowseLayout title="Parts catalog">
        <EpcEmptyState
          title="EPC catalog empty"
          message="No makers in catalog_makers yet. Reload hierarchy via docs/guides/megazip-multivehicle-catalog.md (--live-import) or the hierarchy seed migration, then refresh."
        />
      </EpcBrowseLayout>
    );
  }

  return (
    <EpcBrowseLayout
      title="Parts catalog"
      lede="Browse by maker → model → variant → diagram. Search remains available for part / VIN lookup."
    >
      <Link href="/shop" className={layoutStyles.shopLink}>
        Shop stock inventory →
      </Link>
      <EpcMakerGrid makers={status.makers} />
    </EpcBrowseLayout>
  );
}
