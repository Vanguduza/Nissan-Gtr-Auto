"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { CatalogCanvasStub } from "@/components/catalog-canvas-stub";
import { PriceDual } from "@/components/price-dual";
import { StockBadge } from "@/components/stock-badge";
import {
  listCatalogProducts,
  type CatalogListItem,
} from "@/lib/catalog-product";
import { createWebClient } from "@/lib/supabase";
import styles from "@/app/(storefront)/page.module.css";

type Status =
  | { kind: "loading" }
  | { kind: "auth" }
  | { kind: "error"; message: string }
  | {
      kind: "ready";
      items: CatalogListItem[];
      categories: string[];
    };

export function CatalogBrowse({ category }: { category?: string }) {
  const [status, setStatus] = useState<Status>({ kind: "loading" });

  useEffect(() => {
    let cancelled = false;

    async function run() {
      setStatus({ kind: "loading" });
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

      const { data: sessionData } = await client.auth.getSession();
      if (!sessionData.session) {
        if (!cancelled) setStatus({ kind: "auth" });
        return;
      }

      const result = await listCatalogProducts(client, {
        category: category ?? null,
      });
      if (cancelled) return;

      if (!result.ok) {
        setStatus({ kind: "error", message: result.error });
        return;
      }

      setStatus({
        kind: "ready",
        items: result.data,
        categories: result.categories,
      });
    }

    void run();
    return () => {
      cancelled = true;
    };
  }, [category]);

  if (status.kind === "loading") {
    return (
      <div className={styles.page}>
        <h1 className={styles.title}>Catalog</h1>
        <p className={styles.lede}>Loading live inventory…</p>
      </div>
    );
  }

  if (status.kind === "auth") {
    const next = category
      ? `/catalog?cat=${encodeURIComponent(category)}`
      : "/catalog";
    return (
      <div className={styles.page}>
        <h1 className={styles.title}>Catalog</h1>
        <p className={styles.lede}>
          Sign in to browse live stock and prices.{" "}
          <Link href={`/login?next=${encodeURIComponent(next)}`}>Sign in</Link>
        </p>
      </div>
    );
  }

  if (status.kind === "error") {
    return (
      <div className={styles.page}>
        <h1 className={styles.title}>Catalog</h1>
        <p className={styles.lede} role="alert">
          {status.message}
        </p>
      </div>
    );
  }

  const facetCats =
    status.categories.length > 0
      ? status.categories
      : ["Brakes", "Filters", "Cooling", "Engine"];

  return (
    <div className={styles.page}>
      <h1 className={styles.title}>Catalog</h1>
      <p className={styles.lede}>
        Live stock list from inventory. Use search for VIN / PNC / OEM lookups.
      </p>
      <div className={styles.plp}>
        <aside className={styles.facets} aria-label="Filters">
          <h2>Filters</h2>
          <div className={styles.facetGroup}>
            <p>Category</p>
            {facetCats.map((c) => {
              const slug = c.toLowerCase();
              return (
                <label key={c}>
                  <input
                    type="checkbox"
                    readOnly
                    checked={category?.toLowerCase() === slug}
                  />{" "}
                  <Link href={`/catalog?cat=${encodeURIComponent(slug)}`}>
                    {c}
                  </Link>
                </label>
              );
            })}
          </div>
          <div className={styles.facetGroup}>
            <p>Brand</p>
            <label>
              <input type="checkbox" readOnly checked defaultChecked /> Nissan
              OE
            </label>
          </div>
          <div className={styles.facetGroup}>
            <p>Availability</p>
            <label>
              <input type="checkbox" readOnly /> In stock
            </label>
            <label>
              <input type="checkbox" readOnly /> Counter only
            </label>
          </div>
          <Link href="/catalog" className={styles.rowCta}>
            Clear
          </Link>
        </aside>
        <div>
          <div className={styles.tableWrap}>
            <table className={styles.table}>
              <thead>
                <tr>
                  <th>OEM</th>
                  <th>Description</th>
                  <th>Stock</th>
                  <th>Price</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {status.items.length === 0 ? (
                  <tr>
                    <td colSpan={5} className={styles.muted}>
                      No parts in inventory
                      {category ? ` for category “${category}”` : ""}.
                    </td>
                  </tr>
                ) : (
                  status.items.map((p) => (
                    <tr key={p.oem}>
                      <td>
                        <code className={styles.sku}>{p.oem}</code>
                      </td>
                      <td>{p.name}</td>
                      <td>
                        <StockBadge state={p.stock} />
                      </td>
                      <td>
                        {p.usd != null ? (
                          <PriceDual usd={p.usd} zig={p.zig} />
                        ) : (
                          <span className={styles.muted}>On request</span>
                        )}
                      </td>
                      <td>
                        <Link
                          href={`/parts/${encodeURIComponent(p.oem)}`}
                          className={styles.rowCta}
                        >
                          View
                        </Link>
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
          <div style={{ marginTop: "1.5rem" }}>
            <CatalogCanvasStub sample />
          </div>
        </div>
      </div>
    </div>
  );
}
