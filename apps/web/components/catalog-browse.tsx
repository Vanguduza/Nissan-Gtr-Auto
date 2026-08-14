"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { FormEvent, useEffect, useMemo, useState } from "react";
import type { ShopFacetOption } from "@gtr/shared";
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
      categoryFacets: ShopFacetOption[];
    };

export function CatalogBrowse({
  category,
  subcategory,
  sort: sortParam,
  minUsd: minParam,
  maxUsd: maxParam,
}: {
  category?: string;
  subcategory?: string;
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
        subcategory: subcategory ?? null,
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
        categoryFacets: result.categoryFacets,
      });
    }

    void run();
    return () => {
      cancelled = true;
    };
  }, [category, subcategory, sort, minUsd, maxUsd]);

  const nextQuery = useMemo(() => {
    const q = new URLSearchParams();
    if (category) q.set("cat", category);
    if (subcategory) q.set("sub", subcategory);
    if (sort && sort !== "oem") q.set("sort", sort);
    return q;
  }, [category, subcategory, sort]);

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
    if (subcategory) q.set("sub", subcategory);
    if (next !== "oem") q.set("sort", next);
    if (minParam) q.set("min", minParam);
    if (maxParam) q.set("max", maxParam);
    const qs = q.toString();
    router.push(qs ? `/shop?${qs}` : "/shop");
  }

  function facetHref(facet: ShopFacetOption): string {
    const q = new URLSearchParams();
    if (category) {
      // Parent already selected → treat facet as leaf subcategory
      q.set("cat", category);
      q.set("sub", facet.slug);
    } else {
      q.set("cat", facet.slug);
    }
    if (sort !== "oem") q.set("sort", sort);
    if (minParam) q.set("min", minParam);
    if (maxParam) q.set("max", maxParam);
    return `/shop?${q.toString()}`;
  }

  function facetChecked(facet: ShopFacetOption): boolean {
    if (subcategory) {
      return subcategory.toLowerCase() === facet.slug.toLowerCase();
    }
    if (!category) return false;
    // Top-level browse: parent slug matches facet
    return category.toLowerCase() === facet.slug.toLowerCase();
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
      ? `/shop?cat=${encodeURIComponent(category)}${
          subcategory ? `&sub=${encodeURIComponent(subcategory)}` : ""
        }`
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

  const facetCats = status.categoryFacets;
  const catalogEmpty =
    status.items.length === 0 && facetCats.length === 0 && !category;

  const displayItems = applyCatalogFiltersAndSort(status.items, {
    sort,
    minUsd: Number.isFinite(minUsd as number) ? minUsd : null,
    maxUsd: Number.isFinite(maxUsd as number) ? maxUsd : null,
  });

  const filterLabel = subcategory || category;

  return (
    <div className={styles.page}>
      <h1 className={styles.title}>Shop stock</h1>
      <p className={styles.lede}>
        Only parts that are in stock and priced appear here. Filter by category
        and USD price; sort like the KMP PLP (price, newest, movers). For vehicle
        diagrams use <Link href="/catalog">Parts catalog (EPC)</Link>. Staff set
        price and photos under{" "}
        <Link href="/staff/crm/product-pages">Product pages</Link>.
      </p>
      {catalogEmpty ? (
        <p className={styles.lede} role="status">
          No in-stock priced items yet. Receive stock, set a retail price on
          Product pages, then refresh. EPC search still finds unpriced catalog
          parts.
        </p>
      ) : null}
      <div className={styles.plp}>
        <aside className={styles.facets} aria-label="Filters">
          <h2>Filters</h2>
          <div className={styles.facetGroup}>
            <p>Category</p>
            {facetCats.length === 0 ? (
              <p className={styles.muted}>
                {category
                  ? `No merchandising subcategories for “${category}”. Clear the filter or pick a top category from the nav.`
                  : "No categories loaded — filters unavailable until catalog data is imported."}
              </p>
            ) : (
              facetCats.map((c) => (
                <label key={c.slug}>
                  <input
                    type="checkbox"
                    readOnly
                    checked={facetChecked(c)}
                  />{" "}
                  <Link href={facetHref(c)}>{c.label}</Link>
                </label>
              ))
            )}
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
                      {catalogEmpty
                        ? "No in-stock priced items yet — set price on Product pages after receiving stock."
                        : filterLabel
                          ? `No in-stock priced parts match “${filterLabel}”${
                              minUsd != null || maxUsd != null
                                ? " in this price range"
                                : ""
                            }. Try another subcategory or clear filters.`
                          : `No in-stock priced parts${
                              minUsd != null || maxUsd != null
                                ? " matching this price range"
                                : ""
                            }.`}
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
