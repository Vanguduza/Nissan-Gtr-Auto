"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import { PriceDual } from "@/components/price-dual";
import { StockBadge } from "@/components/stock-badge";
import styles from "@/components/account.module.css";
import {
  clearCompare,
  MAX_COMPARE,
  readCompareOems,
  removeOemFromCompare,
} from "@/lib/compare-selection";
import {
  loadCatalogProduct,
  type CatalogProduct,
} from "@/lib/catalog-product";
import { requireSession } from "@/lib/customer-storefront";
import { createWebClient } from "@/lib/supabase";

type Status =
  | { kind: "loading" }
  | { kind: "auth" }
  | { kind: "error"; message: string }
  | { kind: "ready"; products: CatalogProduct[]; missing: string[] };

export function ComparePanel() {
  const [status, setStatus] = useState<Status>({ kind: "loading" });
  const [oems, setOems] = useState<string[]>([]);

  const refresh = useCallback(async () => {
    const selected = readCompareOems();
    setOems(selected);

    const client = createWebClient();
    if (!client) {
      setStatus({
        kind: "error",
        message: "Supabase is not configured on this environment.",
      });
      return;
    }
    const session = await requireSession(client);
    if (!session.ok) {
      setStatus({ kind: "auth" });
      return;
    }

    if (selected.length === 0) {
      setStatus({ kind: "ready", products: [], missing: [] });
      return;
    }

    const results = await Promise.all(
      selected.map((oem) => loadCatalogProduct(client, oem)),
    );
    const products: CatalogProduct[] = [];
    const missing: string[] = [];
    results.forEach((result, i) => {
      if (result.ok) products.push(result.data);
      else missing.push(selected[i]!);
    });
    setStatus({ kind: "ready", products, missing });
  }, []);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  function onRemove(oem: string) {
    removeOemFromCompare(oem);
    void refresh();
  }

  function onClear() {
    clearCompare();
    void refresh();
  }

  if (status.kind === "loading") {
    return <p className={styles.muted}>Loading compare…</p>;
  }
  if (status.kind === "auth") {
    return (
      <p className={styles.lede}>
        <Link href="/login?next=/account/compare">Sign in</Link> to load live
        catalog rows for your selected SKUs. Selection is kept in this browser
        ({oems.length || readCompareOems().length} saved).
      </p>
    );
  }
  if (status.kind === "error") {
    return (
      <p className={styles.lede} role="alert">
        {status.message}{" "}
        <button type="button" className={styles.btnGhost} onClick={() => void refresh()}>
          Retry
        </button>
      </p>
    );
  }

  if (status.products.length === 0 && status.missing.length === 0) {
    return (
      <p className={styles.muted}>
        No SKUs selected (max {MAX_COMPARE}). Add parts from a PDP with Compare,
        then return here.
      </p>
    );
  }

  return (
    <div>
      <div className={styles.formActions} style={{ marginBottom: "1rem" }}>
        <button type="button" className={styles.btnGhost} onClick={onClear}>
          Clear all
        </button>
      </div>
      {status.missing.length > 0 ? (
        <p className={styles.muted} role="status">
          Could not load: {status.missing.join(", ")}
        </p>
      ) : null}
      <div className={styles.cardGrid}>
        {status.products.map((p) => (
          <div key={p.id} className={styles.card}>
            <span className={styles.cardLabel}>{p.oem}</span>
            <span className={styles.cardBlurb}>{p.name}</span>
            <StockBadge state={p.stock} />
            {p.usd != null ? (
              <PriceDual usd={p.usd} zig={p.zig} />
            ) : (
              <span className={styles.muted}>Price on request</span>
            )}
            {p.coreCharge > 0 ? (
              <span className={styles.muted}>
                Core USD {p.coreCharge.toFixed(2)}
              </span>
            ) : null}
            <span className={styles.muted}>
              {p.category ?? "Uncategorized"}
              {p.fitments.length
                ? ` · ${p.fitments.length} fitment${p.fitments.length === 1 ? "" : "s"}`
                : ""}
            </span>
            <Link href={`/parts/${encodeURIComponent(p.oem)}`} className={styles.btn}>
              Open
            </Link>{" "}
            <button
              type="button"
              className={styles.btnGhost}
              onClick={() => onRemove(p.oem)}
            >
              Remove
            </button>
          </div>
        ))}
      </div>
    </div>
  );
}
