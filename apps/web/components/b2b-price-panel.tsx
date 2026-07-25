"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import {
  formatMoney,
  listPriceListSample,
  loadCustomerPriceList,
  requireSession,
  type PriceListRow,
} from "@/lib/customer-storefront";
import { createWebClient } from "@/lib/supabase";
import styles from "@/app/(storefront)/page.module.css";
import local from "@/app/(b2b)/b2b-page.module.css";

type SampleRow = {
  stock_item_id: string;
  unit_price: number;
  core_charge: number;
  oem: string;
  name: string;
};

type Status =
  | { kind: "loading" }
  | { kind: "auth" }
  | { kind: "error"; message: string }
  | {
      kind: "ready";
      priceList: PriceListRow | null;
      isTrade: boolean;
      sample: SampleRow[];
      displayName: string | null;
      creditLimit: number;
      creditHold: boolean;
      openBalance: number;
      accountCurrency: "USD" | "ZIG";
    };

export function B2bPricePanel() {
  const [status, setStatus] = useState<Status>({ kind: "loading" });

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
      setStatus({ kind: "auth" });
      return;
    }
    const resolved = await loadCustomerPriceList(client);
    if (!resolved.ok) {
      setStatus({ kind: "error", message: resolved.error });
      return;
    }
    let sample: SampleRow[] = [];
    if (resolved.data.priceList) {
      const rows = await listPriceListSample(
        client,
        resolved.data.priceList.id,
      );
      if (!rows.ok) {
        setStatus({ kind: "error", message: rows.error });
        return;
      }
      sample = rows.data;
    }
    setStatus({
      kind: "ready",
      priceList: resolved.data.priceList,
      isTrade: resolved.data.isTrade,
      sample,
      displayName: resolved.data.customer?.display_name ?? null,
      creditLimit: Number(resolved.data.customer?.credit_limit ?? 0),
      creditHold: !!resolved.data.customer?.credit_hold,
      openBalance: Number(resolved.data.customer?.open_balance ?? 0),
      accountCurrency:
        resolved.data.customer?.currency === "ZIG" ? "ZIG" : "USD",
    });
  }, []);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  if (status.kind === "loading") {
    return <p className={styles.lede}>Resolving trade price list…</p>;
  }
  if (status.kind === "auth") {
    return (
      <p className={styles.lede}>
        <Link href="/login">Sign in</Link> with a linked trade customer to see
        B2B / Fleet net pricing. Catalog PDP also uses your assigned price list.
      </p>
    );
  }
  if (status.kind === "error") {
    return (
      <p className={styles.lede} role="alert">
        {status.message}
      </p>
    );
  }

  const list = status.priceList;
  const remaining =
    status.creditLimit > 0
      ? Math.max(0, status.creditLimit - status.openBalance)
      : null;
  const overLimit =
    status.creditLimit > 0 && status.openBalance > status.creditLimit;

  return (
    <div>
      <dl className={local.meta}>
        <div>
          <dt>Account</dt>
          <dd>{status.displayName ?? "Linked customer"}</dd>
        </div>
        <div>
          <dt>Price list</dt>
          <dd>
            {list
              ? `${list.code} — ${list.name}${status.isTrade ? " (trade)" : ""}`
              : "None assigned (defaults to RETAIL on catalog)"}
          </dd>
        </div>
        <div>
          <dt>Currency</dt>
          <dd>
            {list ? (
              list.currency === "ZIG" ? (
                <span className={local.zig}>ZiG</span>
              ) : (
                <span className={local.usd}>USD</span>
              )
            ) : (
              <>
                <span className={local.usd}>USD</span> /{" "}
                <span className={local.zig}>ZiG</span>
              </>
            )}
          </dd>
        </div>
        <div>
          <dt>Credit</dt>
          <dd>
            {status.creditHold ? (
              <span role="status">On hold — checkout posts invoices on hold</span>
            ) : status.creditLimit > 0 ? (
              <>
                Limit {formatMoney(status.creditLimit, status.accountCurrency)}
                {" · open "}
                {formatMoney(status.openBalance, status.accountCurrency)}
                {remaining != null
                  ? ` · available ${formatMoney(remaining, status.accountCurrency)}`
                  : ""}
                {overLimit ? " · over limit" : ""}
              </>
            ) : (
              <>
                No credit limit set · open balance{" "}
                {formatMoney(status.openBalance, status.accountCurrency)}
              </>
            )}
          </dd>
        </div>
      </dl>

      {status.creditHold || overLimit ? (
        <p className={styles.lede} style={{ marginTop: "1rem" }} role="status">
          {status.creditHold
            ? "This account is on credit hold. Storefront checkout still creates an invoice, but it stays on_hold until sales clears the hold."
            : "Open balance exceeds the credit limit. Checkout will post the order on_hold until the balance is brought under limit."}
        </p>
      ) : null}

      {!status.isTrade ? (
        <p className={styles.lede} style={{ marginTop: "1.25rem" }}>
          This account is not on B2B/Fleet yet. Ask counter/sales to set{" "}
          <code>customers.price_list_id</code> to the B2B list.
        </p>
      ) : null}

      {status.sample.length > 0 ? (
        <>
          <h2 className={styles.sectionTitle} style={{ marginTop: "1.5rem" }}>
            Net prices (sample)
          </h2>
          <ul className={styles.simpleList}>
            {status.sample.map((row) => (
              <li key={row.stock_item_id}>
                <Link href={`/parts/${encodeURIComponent(row.oem)}`}>
                  {row.name}
                </Link>
                {" — "}
                {formatMoney(row.unit_price, list?.currency ?? "USD")}
                {row.core_charge > 0
                  ? ` + core ${formatMoney(row.core_charge, list?.currency ?? "USD")}`
                  : ""}
              </li>
            ))}
          </ul>
        </>
      ) : (
        <p className={styles.lede} style={{ marginTop: "1.25rem" }}>
          No price list items to preview.
        </p>
      )}
    </div>
  );
}
