"use client";

import Link from "next/link";
import { useCallback, useEffect, useMemo, useState } from "react";
import { PriceDual } from "@/components/price-dual";
import { StockBadge } from "@/components/stock-badge";
import styles from "@/components/account.module.css";
import {
  clearCompareTray,
  removeOemFromCompareTray,
  syncLocalCompareToServer,
} from "@/lib/customer-compare";
import { MAX_COMPARE, readCompareOems } from "@/lib/compare-selection";
import {
  fitmentLabel,
  loadCatalogProduct,
  type CatalogProduct,
} from "@/lib/catalog-product";
import { requireSession } from "@/lib/customer-storefront";
import { createWebClient } from "@/lib/supabase";

type Status =
  | { kind: "loading" }
  | { kind: "auth"; oems: string[] }
  | { kind: "error"; message: string }
  | { kind: "ready"; products: CatalogProduct[]; missing: string[] };

type MatrixRow = {
  key: string;
  label: string;
  values: (string | null)[];
};

function buildMatrix(products: CatalogProduct[]): MatrixRow[] {
  const fitmentSummary = (p: CatalogProduct) => {
    const labels = [
      ...new Set(
        p.fitments.map(fitmentLabel).filter((label) => label.length > 0),
      ),
    ];
    if (!labels.length) return "—";
    return labels.slice(0, 3).join("; ") + (labels.length > 3 ? "…" : "");
  };

  return [
    {
      key: "oem",
      label: "OEM",
      values: products.map((p) => p.oem),
    },
    {
      key: "name",
      label: "Name",
      values: products.map((p) => p.name),
    },
    {
      key: "brand",
      label: "Brand",
      values: products.map((p) => p.brand),
    },
    {
      key: "category",
      label: "Category",
      values: products.map((p) => p.category ?? "—"),
    },
    {
      key: "stock",
      label: "Stock",
      values: products.map((p) => p.stock.replace("_", " ")),
    },
    {
      key: "usd",
      label: "Price USD",
      values: products.map((p) =>
        p.usd != null ? p.usd.toFixed(2) : "On request",
      ),
    },
    {
      key: "core",
      label: "Core charge USD",
      values: products.map((p) =>
        p.coreCharge > 0 ? p.coreCharge.toFixed(2) : "—",
      ),
    },
    {
      key: "fitments",
      label: "Fitments",
      values: products.map((p) => fitmentSummary(p)),
    },
    {
      key: "specs",
      label: "Specs",
      values: products.map((p) =>
        p.specs.length ? p.specs.slice(0, 4).join("; ") : "—",
      ),
    },
    {
      key: "oe",
      label: "OE cross-refs",
      values: products.map((p) =>
        p.replaces.length ? p.replaces.slice(0, 4).join(", ") : "—",
      ),
    },
    {
      key: "alts",
      label: "Alternatives",
      values: products.map((p) =>
        p.alternatives.length
          ? p.alternatives
              .slice(0, 3)
              .map((a) => a.oem)
              .join(", ")
          : "—",
      ),
    },
  ];
}

export function ComparePanel() {
  const [status, setStatus] = useState<Status>({ kind: "loading" });
  const [oems, setOems] = useState<string[]>([]);
  const [busy, setBusy] = useState(false);

  const refresh = useCallback(async () => {
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
      const guest = readCompareOems();
      setOems(guest);
      setStatus({ kind: "auth", oems: guest });
      return;
    }

    const synced = await syncLocalCompareToServer(client);
    const selected = synced.ok ? synced.data : readCompareOems();
    setOems(selected);

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

  const matrix = useMemo(
    () =>
      status.kind === "ready" && status.products.length > 0
        ? buildMatrix(status.products)
        : [],
    [status],
  );

  async function onRemove(oem: string) {
    setBusy(true);
    const client = createWebClient();
    const session = client ? await requireSession(client) : null;
    await removeOemFromCompareTray(client, oem, !!session?.ok);
    setBusy(false);
    void refresh();
  }

  async function onClear() {
    setBusy(true);
    const client = createWebClient();
    const session = client ? await requireSession(client) : null;
    await clearCompareTray(client, !!session?.ok);
    setBusy(false);
    void refresh();
  }

  if (status.kind === "loading") {
    return <p className={styles.muted}>Loading compare…</p>;
  }
  if (status.kind === "auth") {
    return (
      <p className={styles.lede}>
        <Link href="/login?next=/account/compare">Sign in</Link> to sync compare
        to your account and load live catalog rows. Guest selection stays in
        this browser ({status.oems.length || oems.length} saved
        {status.oems.length
          ? `: ${status.oems.slice(0, 4).join(", ")}${status.oems.length > 4 ? "…" : ""}`
          : ""}
        ).
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
        then return here. Signed-in lists sync via server RPCs; guests use
        browser storage.
      </p>
    );
  }

  return (
    <div>
      <div className={styles.formActions} style={{ marginBottom: "1rem" }}>
        <button
          type="button"
          className={styles.btnGhost}
          disabled={busy}
          onClick={() => void onClear()}
        >
          Clear all
        </button>
      </div>
      {status.missing.length > 0 ? (
        <p className={styles.muted} role="status">
          Could not load: {status.missing.join(", ")}
        </p>
      ) : null}

      {matrix.length > 0 ? (
        <div style={{ overflowX: "auto", marginBottom: "1.25rem" }}>
          <table
            style={{ width: "100%", fontSize: "0.88rem", borderCollapse: "collapse" }}
            aria-label="Compare attribute matrix"
          >
            <thead>
              <tr>
                <th align="left" style={{ padding: "0.35rem 0.5rem" }}>
                  Attribute
                </th>
                {status.products.map((p) => (
                  <th
                    key={p.id}
                    align="left"
                    style={{ padding: "0.35rem 0.5rem", minWidth: "9rem" }}
                  >
                    <code>{p.oem}</code>
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {matrix.map((row) => (
                <tr key={row.key}>
                  <td
                    style={{
                      padding: "0.35rem 0.5rem",
                      fontWeight: 600,
                      verticalAlign: "top",
                    }}
                  >
                    {row.label}
                  </td>
                  {row.values.map((v, i) => (
                    <td
                      key={`${row.key}-${status.products[i]?.id ?? i}`}
                      style={{ padding: "0.35rem 0.5rem", verticalAlign: "top" }}
                    >
                      {v}
                    </td>
                  ))}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
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
              disabled={busy}
              onClick={() => void onRemove(p.oem)}
            >
              Remove
            </button>
          </div>
        ))}
      </div>
    </div>
  );
}
