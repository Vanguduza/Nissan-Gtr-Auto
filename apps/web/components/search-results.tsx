"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useMemo, useState } from "react";
import {
  MEILI_FACETS,
  partHref,
  searchCatalog,
  type PartHit,
  type SearchMode,
  type SearchResult,
} from "@/lib/catalog-search";
import { createWebClient } from "@/lib/supabase";
import styles from "@/app/(storefront)/page.module.css";
import filterStyles from "./plp-filters.module.css";

type Status =
  | { kind: "loading" }
  | { kind: "auth" }
  | { kind: "error"; message: string }
  | { kind: "empty" }
  | {
      kind: "ready";
      results: SearchResult[];
      query: string;
      backend?: "meili" | "fts";
      facetDistribution?: Record<string, Record<string, number>>;
    };

type PartSort = "relevance" | "oem" | "category";

export function SearchResults({
  mode,
  query,
}: {
  mode: SearchMode;
  query: string;
}) {
  const router = useRouter();
  const [status, setStatus] = useState<Status>({ kind: "loading" });
  const [categoryFilter, setCategoryFilter] = useState<string | null>(null);
  const [sort, setSort] = useState<PartSort>("relevance");

  useEffect(() => {
    setCategoryFilter(null);
    setSort("relevance");
  }, [mode, query]);

  useEffect(() => {
    const trimmed = query.trim();
    if (!trimmed) {
      setStatus({ kind: "empty" });
      return;
    }

    let cancelled = false;
    setStatus({ kind: "loading" });

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

      const { data: sessionData } = await client.auth.getSession();
      if (!sessionData.session) {
        if (!cancelled) setStatus({ kind: "auth" });
        return;
      }

      const result = await searchCatalog(client, mode, trimmed, {
        limit: 60,
        facets: [...MEILI_FACETS],
      });
      if (cancelled) return;

      if (!result.ok) {
        setStatus({ kind: "error", message: result.error });
        return;
      }

      if (result.data.results.length === 0) {
        setStatus({ kind: "empty" });
        return;
      }

      setStatus({
        kind: "ready",
        results: result.data.results,
        query: result.data.query,
        backend: result.data.backend,
        facetDistribution: result.data.facetDistribution,
      });
    }

    void run();
    return () => {
      cancelled = true;
    };
  }, [mode, query]);

  const partCategories = useMemo(() => {
    if (status.kind !== "ready" || mode !== "part") return [];
    const meiliCats = status.facetDistribution?.category_name;
    if (meiliCats && Object.keys(meiliCats).length > 0) {
      return Object.entries(meiliCats)
        .sort((a, b) => b[1] - a[1] || a[0].localeCompare(b[0]))
        .map(([name]) => name);
    }
    const cats = new Set<string>();
    for (const r of status.results) {
      if (r.type === "part" && r.category_name?.trim()) {
        cats.add(r.category_name.trim());
      }
    }
    return [...cats].sort((a, b) => a.localeCompare(b));
  }, [status, mode]);

  const pncFacets = useMemo(() => {
    if (status.kind !== "ready" || mode !== "pnc") return [];
    const dist = status.facetDistribution?.pnc_code;
    if (!dist) return [];
    return Object.entries(dist)
      .sort((a, b) => b[1] - a[1] || a[0].localeCompare(b[0]))
      .slice(0, 12);
  }, [status, mode]);

  const modelFacets = useMemo(() => {
    if (status.kind !== "ready" || (mode !== "model" && mode !== "vin")) return [];
    const dist =
      status.facetDistribution?.model_variant ??
      status.facetDistribution?.chassis_code;
    if (!dist) return [];
    return Object.entries(dist)
      .sort((a, b) => b[1] - a[1] || a[0].localeCompare(b[0]))
      .slice(0, 12);
  }, [status, mode]);

  const filteredParts = useMemo(() => {
    if (status.kind !== "ready" || mode !== "part") return [];
    let parts = status.results.filter(
      (r): r is PartHit => r.type === "part",
    );
    if (categoryFilter) {
      parts = parts.filter(
        (p) =>
          p.category_name?.trim().toLowerCase() ===
          categoryFilter.toLowerCase(),
      );
    }
    if (sort === "oem") {
      parts = [...parts].sort((a, b) =>
        a.oem_part_number.localeCompare(b.oem_part_number),
      );
    } else if (sort === "category") {
      parts = [...parts].sort((a, b) =>
        (a.category_name ?? "").localeCompare(b.category_name ?? ""),
      );
    }
    return parts;
  }, [status, mode, categoryFilter, sort]);

  if (status.kind === "loading") {
    return (
      <div className={styles.resultStub} aria-live="polite">
        <p className={styles.muted}>Searching catalog…</p>
      </div>
    );
  }

  if (status.kind === "auth") {
    const returnTo = `/search?mode=${mode}&q=${encodeURIComponent(query.trim())}`;
    return (
      <div className={styles.resultStub}>
        <p>
          Catalog search requires a signed-in account.{" "}
          <Link href={`/login?next=${encodeURIComponent(returnTo)}`}>
            Sign in
          </Link>{" "}
          to run live lookups.
        </p>
      </div>
    );
  }

  if (status.kind === "error") {
    return (
      <div className={styles.resultStub} role="alert">
        <p className={styles.muted}>Search unavailable: {status.message}</p>
      </div>
    );
  }

  if (status.kind === "empty") {
    return (
      <div className={styles.resultStub}>
        <p className={styles.muted}>
          No results for <strong>{query.trim()}</strong> in{" "}
          <strong>{mode}</strong> mode.
        </p>
      </div>
    );
  }

  return (
    <div className={styles.resultStub}>
      <p className={styles.muted}>
        {status.results.length} result
        {status.results.length === 1 ? "" : "s"} for{" "}
        <strong>{status.query}</strong>
        {status.backend ? (
          <>
            {" "}
            · index <strong>{status.backend}</strong>
          </>
        ) : null}
      </p>
      {mode === "part" ? (
        <>
          <div className={filterStyles.searchFilters}>
            <div className={filterStyles.searchFiltersRow}>
              <label className={filterStyles.sort}>
                Sort
                <select
                  value={sort}
                  onChange={(e) => setSort(e.target.value as PartSort)}
                  aria-label="Sort search results"
                >
                  <option value="relevance">Relevance</option>
                  <option value="oem">OEM</option>
                  <option value="category">Category</option>
                </select>
              </label>
              <p className={styles.muted}>
                Showing {filteredParts.length}
                {categoryFilter ? ` in ${categoryFilter}` : ""}
              </p>
            </div>
            {partCategories.length > 0 ? (
              <div>
                <p className={styles.muted} style={{ marginBottom: "0.4rem" }}>
                  Categories
                </p>
                <div className={filterStyles.chipGroup}>
                  <button
                    type="button"
                    className={
                      categoryFilter == null
                        ? filterStyles.chipBtnActive
                        : filterStyles.chipBtn
                    }
                    onClick={() => setCategoryFilter(null)}
                  >
                    All
                  </button>
                  {partCategories.map((c) => (
                    <button
                      key={c}
                      type="button"
                      className={
                        categoryFilter === c
                          ? filterStyles.chipBtnActive
                          : filterStyles.chipBtn
                      }
                      onClick={() =>
                        setCategoryFilter((prev) => (prev === c ? null : c))
                      }
                    >
                      {c}
                    </button>
                  ))}
                </div>
              </div>
            ) : null}
            <p className={styles.muted}>
              USD price range filters apply on{" "}
              <Link href="/catalog">Catalog</Link> (priced inventory). Search
              hits are OEM/fitment first.
            </p>
          </div>
          <PartResultsTable results={filteredParts} />
        </>
      ) : mode === "model" || mode === "vin" ? (
        <>
          {modelFacets.length > 0 ? (
            <FacetChipRow
              label="Models / chassis"
              entries={modelFacets}
              onPick={(value) => {
                router.push(
                  `/search?mode=model&q=${encodeURIComponent(value)}`,
                );
              }}
            />
          ) : null}
          <VehicleResultsList results={status.results} />
        </>
      ) : (
        <>
          {pncFacets.length > 0 ? (
            <FacetChipRow
              label="PNC codes"
              entries={pncFacets}
              onPick={(value) => {
                router.push(
                  `/search?mode=pnc&q=${encodeURIComponent(value)}`,
                );
              }}
            />
          ) : null}
          <PncResultsList results={status.results} />
        </>
      )}
    </div>
  );
}

function FacetChipRow({
  label,
  entries,
  onPick,
}: {
  label: string;
  entries: [string, number][];
  onPick: (value: string) => void;
}) {
  return (
    <div className={filterStyles.searchFilters}>
      <p className={styles.muted} style={{ marginBottom: "0.4rem" }}>
        {label}
      </p>
      <div className={filterStyles.chipGroup}>
        {entries.map(([value, count]) => (
          <button
            key={value}
            type="button"
            className={filterStyles.chipBtn}
            onClick={() => onPick(value)}
          >
            {value} ({count})
          </button>
        ))}
      </div>
    </div>
  );
}

function PartResultsTable({ results }: { results: PartHit[] }) {
  return (
    <div className={styles.tableWrap}>
      <table className={styles.table}>
        <thead>
          <tr>
            <th>OEM</th>
            <th>PNC</th>
            <th>Category</th>
            <th>Chassis</th>
            <th />
          </tr>
        </thead>
        <tbody>
          {results.length === 0 ? (
            <tr>
              <td colSpan={5} className={styles.muted}>
                No parts match this category filter.
              </td>
            </tr>
          ) : (
            results.map((row) => {
              const href = partHref(row.oem_part_number);
              const key = `${row.oem_part_number}-${row.pnc_code ?? ""}`;
              return (
                <tr key={key}>
                  <td>
                    <code className={styles.sku}>{row.oem_part_number}</code>
                    {row.matched_oe_number ? (
                      <span className={styles.muted}>
                        {" "}
                        (matched {row.matched_oe_number}
                        {row.matched_brand ? ` · ${row.matched_brand}` : ""})
                      </span>
                    ) : null}
                  </td>
                  <td>{row.pnc_code ?? "—"}</td>
                  <td>
                    {[row.category_name, row.subcategory_name]
                      .filter(Boolean)
                      .join(" · ") || "—"}
                  </td>
                  <td>{row.chassis_code ?? "—"}</td>
                  <td>
                    {href ? (
                      <Link href={href} className={styles.rowCta}>
                        View
                      </Link>
                    ) : (
                      "—"
                    )}
                  </td>
                </tr>
              );
            })
          )}
        </tbody>
      </table>
    </div>
  );
}

function VehicleResultsList({ results }: { results: SearchResult[] }) {
  const vehicles = results.filter(
    (r): r is Extract<SearchResult, { type: "vehicle" }> =>
      r.type === "vehicle",
  );

  return (
    <ul className={styles.simpleList}>
      {vehicles.map((row) => {
        const label =
          row.model_variant ??
          [row.chassis_code, row.engine_code].filter(Boolean).join(" · ") ??
          row.vin_prefix ??
          "Vehicle";
        const key = `${row.vin_prefix ?? ""}-${row.chassis_code ?? ""}-${row.engine_code ?? ""}`;
        return (
          <li key={key}>
            <strong>{label}</strong>
            {row.production_year ? ` (${row.production_year})` : null}
            {row.chassis_code ? ` · ${row.chassis_code}` : null}
            {row.engine_code ? ` · ${row.engine_code}` : null}
            {row.vin_prefix ? ` · VIN prefix ${row.vin_prefix}` : null}
            {row.fitments?.length ? (
              <FitmentLinks fitments={row.fitments} />
            ) : (
              <p className={styles.muted}>No fitment lines listed.</p>
            )}
          </li>
        );
      })}
    </ul>
  );
}

function PncResultsList({ results }: { results: SearchResult[] }) {
  const rows = results.filter(
    (r): r is Extract<SearchResult, { type: "pnc" }> => r.type === "pnc",
  );

  return (
    <ul className={styles.simpleList}>
      {rows.map((row) => (
        <li key={row.pnc_code}>
          <strong>{row.pnc_code}</strong>
          {row.category_name ? ` — ${row.category_name}` : null}
          {row.subcategory_name ? ` · ${row.subcategory_name}` : null}
          {row.fitments?.length ? (
            <FitmentLinks fitments={row.fitments} />
          ) : (
            <p className={styles.muted}>No parts listed for this PNC.</p>
          )}
        </li>
      ))}
    </ul>
  );
}

function FitmentLinks({
  fitments,
}: {
  fitments: Extract<SearchResult, { type: "part" }>[];
}) {
  return (
    <ul className={styles.simpleList}>
      {fitments.map((part) => {
        const href = partHref(part.oem_part_number);
        return (
          <li key={`${part.oem_part_number}-${part.pnc_code ?? ""}`}>
            {href ? (
              <Link href={href}>
                <code className={styles.sku}>{part.oem_part_number}</code>
              </Link>
            ) : (
              <code className={styles.sku}>{part.oem_part_number}</code>
            )}
            {part.chassis_code ? ` · ${part.chassis_code}` : null}
            {part.engine_code ? ` · ${part.engine_code}` : null}
          </li>
        );
      })}
    </ul>
  );
}
