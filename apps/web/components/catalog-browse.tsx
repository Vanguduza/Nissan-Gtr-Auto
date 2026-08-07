"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { FormEvent, useEffect, useMemo, useState } from "react";
import { CatalogCanvasStub } from "@/components/catalog-canvas-stub";
import { PriceDual } from "@/components/price-dual";
import { StockBadge } from "@/components/stock-badge";
import {
  applyCatalogFiltersAndSort,
  listCatalogProducts,
  type CatalogListItem,
  type CatalogSort,
} from "@/lib/catalog-product";
import { createWebClient } from "@/lib/supabase";
import styles from "@/app/(storefront)/page.module.css";
import filterStyles from "./plp-filters.module.css";

const SORT_OPTIONS: { value: CatalogSort; label: string }[] = [
  { value: "oem", label: "OEM" },
  { value: "name", label: "Name" },
  { value: "price_asc", label: "Price · low → high" },
  { value: "price_desc", label: "Price · high → low" },
  { value: "newest", label: "Newest" },
  { value: "movers", label: "Top movers" },
];

function parseSort(raw: string | undefined): CatalogSort {
  const hit = SORT_OPTIONS.find((o) => o.value === raw);
  return hit?.value ?? "oem";
}

type Status =
  | { kind: "loading" }
  | { kind: "auth" }
  | { kind: "error"; message: string }
  | {
      kind: "ready";
      items: CatalogListItem[];
      categories: string[];
    };

export function CatalogBrowse({
  category,
  sort: sortParam,
  minUsd: minParam,
  maxUsd: maxParam,
}: {
  category?: string;
  sort?: string;
  minUsd?: string;
  maxUsd?: string;
}) {
  const router = useRouter();
  const sort = parseSort(sortParam);
  const minUsd = minParam != null && minParam !== "" ? Number(minParam) : null;
  const maxUsd = maxParam != null && maxParam !== "" ? Number(maxParam) : null;
  const [status, setStatus] = useState<Status>({ kind: "loading" });
  const [draftMin, setDraftMin] = useState(minParam ?? "");
  const [draftMax, setDraftMax] = useState(maxParam ?? "");

  useEffect(() => {
    setDraftMin(minParam ?? "");
    setDraftMax(maxParam ?? "");
  }, [minParam, maxParam]);

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
        sort,
        minUsd: Number.isFinite(minUsd) ? minUsd : null,
        maxUsd: Number.isFinite(maxUsd) ? maxUsd : null,
        limit: 60,
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
  }, [category, sort, minUsd, maxUsd]);

  const nextQuery = useMemo(() => {
    const q = new URLSearchParams();
    if (category) q.set("cat", category);
    if (sort && sort !== "oem") q.set("sort", sort);
    return q;
  }, [category, sort]);

  function pushFilters(e: FormEvent) {
    e.preventDefault();
    const q = new URLSearchParams(nextQuery);
    if (draftMin.trim()) q.set("min", draftMin.trim());
    else q.delete("min");
    if (draftMax.trim()) q.set("max", draftMax.trim());
    else q.delete("max");
    const qs = q.toString();
    router.push(qs ? `/shop?${qs}` : "/shop");
  }

  function setSort(next: CatalogSort) {
    const q = new URLSearchParams();
    if (category) q.set("cat", category);
    if (next !== "oem") q.set("sort", next);
    if (minParam) q.set("min", minParam);
    if (maxParam) q.set("max", maxParam);
    const qs = q.toString();
    router.push(qs ? `/shop?${qs}` : "/shop");
  }

  if (status.kind === "loading") {
    return (
      <div className={styles.page}>
        <h1 className={styles.title}>Shop stock</h1>
        <p className={styles.lede}>Loading live inventory…</p>
      </div>
    );
  }

  if (status.kind === "auth") {
    const next = category
      ? `/shop?cat=${encodeURIComponent(category)}`
      : "/shop";
    return (
      <div className={styles.page}>
        <h1 className={styles.title}>Shop stock</h1>
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
        <h1 className={styles.title}>Shop stock</h1>
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

  const displayItems = applyCatalogFiltersAndSort(status.items, {
    sort,
    minUsd: Number.isFinite(minUsd as number) ? minUsd : null,
    maxUsd: Number.isFinite(maxUsd as number) ? maxUsd : null,
  });

  return (
    <div className={styles.page}>
      <h1 className={styles.title}>Shop stock</h1>
      <p className={styles.lede}>
        Live stock list from inventory. Filter by category and USD price; sort
        like the KMP PLP (price, newest, movers). For vehicle diagrams use{" "}
        <Link href="/catalog">Parts catalog (EPC)</Link>.
      </p>
      <div className={styles.plp}>
        <aside className={styles.facets} aria-label="Filters">
          <h2>Filters</h2>
          <div className={styles.facetGroup}>
            <p>Category</p>
            {facetCats.map((c) => {
              const slug = c.toLowerCase();
              const q = new URLSearchParams();
              q.set("cat", slug);
              if (sort !== "oem") q.set("sort", sort);
              if (minParam) q.set("min", minParam);
              if (maxParam) q.set("max", maxParam);
              return (
                <label key={c}>
                  <input
                    type="checkbox"
                    readOnly
                    checked={category?.toLowerCase() === slug}
                  />{" "}
                  <Link href={`/shop?${q.toString()}`}>{c}</Link>
                </label>
              );
            })}
          </div>
          <form className={styles.facetGroup} onSubmit={pushFilters}>
            <p>Price (USD)</p>
            <div className={filterStyles.priceRow}>
              <label>
                Min
                <input
                  type="number"
                  min={0}
                  step="0.01"
                  inputMode="decimal"
                  value={draftMin}
                  onChange={(e) => setDraftMin(e.target.value)}
                  placeholder="0"
                />
              </label>
              <label>
                Max
                <input
                  type="number"
                  min={0}
                  step="0.01"
                  inputMode="decimal"
                  value={draftMax}
                  onChange={(e) => setDraftMax(e.target.value)}
                  placeholder="Any"
                />
              </label>
            </div>
            <button type="submit" className={styles.rowCta}>
              Apply price
            </button>
          </form>
          <div className={styles.facetGroup}>
            <p>Brand</p>
            <label>
              <input type="checkbox" readOnly checked defaultChecked /> Nissan
              OE
            </label>
          </div>
          <Link href="/shop" className={styles.rowCta}>
            Clear
          </Link>
        </aside>
        <div>
          <div className={filterStyles.toolbar}>
            <label className={filterStyles.sort}>
              Sort
              <select
                value={sort}
                onChange={(e) => setSort(e.target.value as CatalogSort)}
                aria-label="Sort catalog"
              >
                {SORT_OPTIONS.map((o) => (
                  <option key={o.value} value={o.value}>
                    {o.label}
                  </option>
                ))}
              </select>
            </label>
            <p className={styles.muted}>
              {displayItems.length} part{displayItems.length === 1 ? "" : "s"}
            </p>
          </div>
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
                {displayItems.length === 0 ? (
                  <tr>
                    <td colSpan={5} className={styles.muted}>
                      No parts in inventory
                      {category ? ` for category “${category}”` : ""}
                      {(minUsd != null || maxUsd != null) &&
                        " matching this price range"}
                      .
                    </td>
                  </tr>
                ) : (
                  displayItems.map((p) => (
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
