"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { FormEvent, useEffect, useMemo, useState } from "react";
import type { ShopFacetOption } from "@gtr/shared";
import { PriceDual } from "@/components/price-dual";
import { StockBadge } from "@/components/stock-badge";
import {
  applyCatalogFiltersAndSort,
  listCatalogProducts,
  partHref,
  type CatalogListItem,
  type CatalogSort,
} from "@/lib/catalog-product";
import {
  catalogGatewayGet,
  type CustomerStockResponse,
} from "@/lib/catalog-live-gateway";
import {
  CUSTOMER_SELECTED_VEHICLE_KEY,
  loadCustomerVehicle,
} from "@/lib/customer-vehicle-session";
import type { SelectedFitmentVehicle } from "@/lib/vehicle-catalog";
import { createWebClient } from "@/lib/supabase";
import styles from "@/app/(storefront)/page.module.css";
import filterStyles from "./plp-filters.module.css";

const SORT_OPTIONS: { value: CatalogSort; label: string }[] = [
  { value: "name", label: "Name" },
  { value: "price_asc", label: "Price · low → high" },
  { value: "price_desc", label: "Price · high → low" },
  { value: "newest", label: "Newest" },
  { value: "movers", label: "Top movers" },
];

function parseSort(raw: string | undefined): CatalogSort {
  const hit = SORT_OPTIONS.find((o) => o.value === raw);
  return hit?.value ?? "name";
}

function slugify(value: string): string {
  return value
    .trim()
    .toLowerCase()
    .replace(/&/g, "and")
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "");
}

function vehicleScopedItems(payload: CustomerStockResponse): CatalogListItem[] {
  return payload.results
    .filter((row) => Boolean(row.internal_catalog_ref))
    .map((row) => ({
      id: row.stock_item_id,
      // Internal catalog identity is used only for cart identity. It is never rendered.
      oem: row.internal_catalog_ref as string,
      name: row.name,
      stock: row.stock.state,
      usd: row.price?.currency.toUpperCase() === "USD" ? row.price.amount : null,
      zig: row.price?.currency.toUpperCase() === "ZIG" ? row.price.amount : null,
      category: row.subcategory || row.category,
      qty: row.stock.qty,
      createdAt: null,
    }));
}

function facetsFromItems(items: CatalogListItem[]): ShopFacetOption[] {
  const bySlug = new Map<string, ShopFacetOption>();
  for (const item of items) {
    const label = item.category?.trim();
    if (!label) continue;
    const slug = slugify(label);
    if (!slug || bySlug.has(slug)) continue;
    bySlug.set(slug, { slug, label });
  }
  return [...bySlug.values()].sort((a, b) => a.label.localeCompare(b.label));
}

type Status =
  | { kind: "loading" }
  | { kind: "auth" }
  | { kind: "error"; message: string }
  | {
      kind: "ready";
      items: CatalogListItem[];
      categoryFacets: ShopFacetOption[];
      catalogSource: "r2_epc+supabase_commerce" | "supabase_commerce";
    };

export function CatalogBrowse({
  query,
  category,
  subcategory,
  sort: sortParam,
  minUsd: minParam,
  maxUsd: maxParam,
}: {
  /** Plain-text product search (matched against names and, in the background, part numbers). */
  query?: string;
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
  const [vehicle, setVehicle] = useState<SelectedFitmentVehicle | null>(null);
  const [vehicleEpoch, setVehicleEpoch] = useState(0);
  const [draftMin, setDraftMin] = useState(minParam ?? "");
  const [draftMax, setDraftMax] = useState(maxParam ?? "");

  useEffect(() => {
    setDraftMin(minParam ?? "");
    setDraftMax(maxParam ?? "");
  }, [minParam, maxParam]);

  useEffect(() => {
    setVehicle(loadCustomerVehicle());
    const refresh = () => {
      setVehicle(loadCustomerVehicle());
      setVehicleEpoch((value) => value + 1);
    };
    const onStorage = (event: StorageEvent) => {
      if (event.key === CUSTOMER_SELECTED_VEHICLE_KEY) refresh();
    };
    window.addEventListener("gtr:selected-vehicle", refresh as EventListener);
    window.addEventListener("storage", onStorage);
    return () => {
      window.removeEventListener("gtr:selected-vehicle", refresh as EventListener);
      window.removeEventListener("storage", onStorage);
    };
  }, []);

  useEffect(() => {
    let cancelled = false;

    async function run() {
      setStatus({ kind: "loading" });
      const client = createWebClient();
      if (!client) {
        if (!cancelled) {
          setStatus({ kind: "error", message: "Supabase is not configured on this environment." });
        }
        return;
      }

      const { data: sessionData } = await client.auth.getSession();
      if (!sessionData.session) {
        if (!cancelled) setStatus({ kind: "auth" });
        return;
      }

      const selected = loadCustomerVehicle();
      const vehicleId = selected?.vehicleMasterId?.trim();
      if (vehicleId && !query) {
        try {
          const payload = await catalogGatewayGet<CustomerStockResponse>(
            client,
            "customer-stock",
            {
              maker: "nissan",
              vehicle_id: vehicleId,
              category: subcategory || category || null,
              limit: 100,
            },
          );
          if (cancelled) return;
          const items = vehicleScopedItems(payload);
          setStatus({
            kind: "ready",
            items,
            categoryFacets: facetsFromItems(items),
            catalogSource: "r2_epc+supabase_commerce",
          });
          return;
        } catch (error) {
          if (!cancelled) {
            setStatus({
              kind: "error",
              message:
                error instanceof Error && error.message.includes("CATALOG_REPUBLISH_REQUIRED")
                  ? "Fitment for this vehicle is being updated. Please try again shortly."
                  : error instanceof Error
                    ? error.message
                    : "Vehicle fitment catalog is unavailable.",
            });
          }
          return;
        }
      }

      // No vehicle context: commercial browse is allowed, but no compatibility claim is made.
      const result = await listCatalogProducts(client, {
        query: query ?? null,
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
        catalogSource: "supabase_commerce",
      });
    }

    void run();
    return () => {
      cancelled = true;
    };
  }, [query, category, subcategory, sort, minUsd, maxUsd, vehicleEpoch]);

  const nextQuery = useMemo(() => {
    const q = new URLSearchParams();
    if (query) q.set("q", query);
    if (category) q.set("cat", category);
    if (subcategory) q.set("sub", subcategory);
    if (sort && sort !== "name") q.set("sort", sort);
    return q;
  }, [query, category, subcategory, sort]);

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
    if (query) q.set("q", query);
    if (category) q.set("cat", category);
    if (subcategory) q.set("sub", subcategory);
    if (next !== "name") q.set("sort", next);
    if (minParam) q.set("min", minParam);
    if (maxParam) q.set("max", maxParam);
    const qs = q.toString();
    router.push(qs ? `/shop?${qs}` : "/shop");
  }

  function facetHref(facet: ShopFacetOption): string {
    const q = new URLSearchParams();
    if (category) {
      q.set("cat", category);
      q.set("sub", facet.slug);
    } else {
      q.set("cat", facet.slug);
    }
    if (sort !== "name") q.set("sort", sort);
    if (minParam) q.set("min", minParam);
    if (maxParam) q.set("max", maxParam);
    return `/shop?${q.toString()}`;
  }

  function facetChecked(facet: ShopFacetOption): boolean {
    if (subcategory) return subcategory.toLowerCase() === facet.slug.toLowerCase();
    if (!category) return false;
    return category.toLowerCase() === facet.slug.toLowerCase();
  }

  if (status.kind === "loading") {
    return (
      <div className={styles.page}>
        <h1 className={styles.title}>Shop</h1>
        <p className={styles.lede}>Checking live stock…</p>
      </div>
    );
  }

  if (status.kind === "auth") {
    return (
      <div className={styles.page}>
        <h1 className={styles.title}>Shop</h1>
        <p className={styles.lede}>
          Sign in to browse live stock and verified vehicle fitment. {" "}
          <Link href={`/login?next=${encodeURIComponent("/shop")}`}>Sign in</Link>
        </p>
      </div>
    );
  }

  if (status.kind === "error") {
    return (
      <div className={styles.page}>
        <h1 className={styles.title}>Shop</h1>
        <p className={styles.lede} role="alert">{status.message}</p>
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
  const fitmentVerified = status.catalogSource === "r2_epc+supabase_commerce";

  return (
    <div className={styles.page}>
      <h1 className={styles.title}>{query ? `Results for “${query}”` : "Shop"}</h1>
      <p className={styles.lede}>
        {fitmentVerified ? (
          <>Showing parts in stock that fit your {vehicle?.model ?? "selected vehicle"}.</>
        ) : (
          <>Browse parts in stock. <Link href="/vehicle">Select your vehicle</Link> to see only parts that fit.</>
        )}
      </p>
      {catalogEmpty ? (
        <p className={styles.lede} role="status">
          No parts in stock right now. Ask the counter on WhatsApp and we will
          source it for you.
        </p>
      ) : null}

      <div className={styles.plp}>
        <aside className={styles.facets} aria-label="Filters">
          <h2>Filters</h2>
          <div className={styles.facetGroup}>
            <p>Category</p>
            {facetCats.length === 0 ? (
              <p className={styles.muted}>No additional category filters for these results.</p>
            ) : (
              facetCats.map((c) => (
                <label key={c.slug}>
                  <input type="checkbox" readOnly checked={facetChecked(c)} />{" "}
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
                <input type="number" min={0} step="0.01" inputMode="decimal" value={draftMin} onChange={(e) => setDraftMin(e.target.value)} placeholder="0" />
              </label>
              <label>
                Max
                <input type="number" min={0} step="0.01" inputMode="decimal" value={draftMax} onChange={(e) => setDraftMax(e.target.value)} placeholder="Any" />
              </label>
            </div>
            <button type="submit" className={styles.rowCta}>Apply price</button>
          </form>
          <Link href="/shop" className={styles.rowCta}>Clear</Link>
        </aside>

        <div>
          <div className={filterStyles.toolbar}>
            <label className={filterStyles.sort}>
              Sort
              <select value={sort} onChange={(e) => setSort(e.target.value as CatalogSort)} aria-label="Sort catalog">
                {SORT_OPTIONS.map((o) => <option key={o.value} value={o.value}>{o.label}</option>)}
              </select>
            </label>
            <p className={styles.muted}>{displayItems.length} part{displayItems.length === 1 ? "" : "s"}</p>
          </div>

          <div className={styles.tableWrap}>
            <table className={styles.table}>
              <thead>
                <tr>
                  <th>Part</th>
                  <th>Compatibility</th>
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
                        ? "No parts in stock right now."
                        : query
                        ? `No parts in stock match “${query}”.`
                        : filterLabel
                        ? `No saleable parts match “${filterLabel}” for the current filters.`
                        : fitmentVerified
                          ? "No parts in stock match your selected vehicle yet."
                          : "No stock matches the current filters."}
                    </td>
                  </tr>
                ) : (
                  displayItems.map((p) => (
                    <tr key={p.id ?? p.oem}>
                      <td>{p.name}</td>
                      <td>{fitmentVerified ? "Verified for selected vehicle" : "Select vehicle to confirm"}</td>
                      <td><StockBadge state={p.stock} /></td>
                      <td>
                        {p.usd != null ? <PriceDual usd={p.usd} zig={p.zig} /> : <span className={styles.muted}>On request</span>}
                      </td>
                      <td>
                        <Link href={partHref(p)} className={styles.rowCta}>View</Link>
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
        </div>
      </div>
    </div>
  );
}
