"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import {
  partHref,
  searchCatalog,
  type SearchMode,
  type SearchResult,
} from "@/lib/catalog-search";
import { createWebClient } from "@/lib/supabase";
import styles from "@/app/(storefront)/page.module.css";

type Status =
  | { kind: "loading" }
  | { kind: "auth" }
  | { kind: "error"; message: string }
  | { kind: "empty" }
  | { kind: "ready"; results: SearchResult[]; query: string };

export function SearchResults({
  mode,
  query,
}: {
  mode: SearchMode;
  query: string;
}) {
  const [status, setStatus] = useState<Status>({ kind: "loading" });

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

      const result = await searchCatalog(client, mode, trimmed);
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
      });
    }

    void run();
    return () => {
      cancelled = true;
    };
  }, [mode, query]);

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
      </p>
      {mode === "part" ? (
        <PartResultsTable results={status.results} />
      ) : mode === "model" || mode === "vin" ? (
        <VehicleResultsList results={status.results} />
      ) : (
        <PncResultsList results={status.results} />
      )}
    </div>
  );
}

function PartResultsTable({ results }: { results: SearchResult[] }) {
  const parts = results.filter(
    (r): r is Extract<SearchResult, { type: "part" }> => r.type === "part",
  );

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
          {parts.map((row) => {
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
          })}
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
