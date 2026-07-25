"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import styles from "@/components/account.module.css";
import {
  blanketAlerts,
  listBlanketPurchaseOrders,
  requireSession,
  type BlanketSummary,
} from "@/lib/blanket-po";
import { createWebClient } from "@/lib/supabase";

type Status =
  | { kind: "loading" }
  | { kind: "auth" }
  | { kind: "error"; message: string }
  | { kind: "ready"; blankets: BlanketSummary[] };

/** Read-only supplier view of blankets visible via RLS. */
export function SupplierBlanketPanel() {
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
    const blankets = await listBlanketPurchaseOrders(client);
    if (!blankets.ok) {
      setStatus({ kind: "error", message: blankets.error });
      return;
    }
    setStatus({ kind: "ready", blankets: blankets.data });
  }, []);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  if (status.kind === "loading") {
    return <p className={styles.muted}>Loading blanket contracts…</p>;
  }
  if (status.kind === "auth") {
    return (
      <p className={styles.lede}>
        <Link href="/login?next=/supplier/blankets">Sign in</Link> with a linked
        supplier profile to see your blanket POs.
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

  if (status.blankets.length === 0) {
    return (
      <p className={styles.muted}>
        No blanket purchase orders visible for this supplier account.
      </p>
    );
  }

  return (
    <ul className={styles.list}>
      {status.blankets.map((b) => {
        const alerts = blanketAlerts(b);
        return (
          <li key={b.po.id}>
            <strong>{b.po.document_number ?? b.po.id.slice(0, 8)}</strong> ·{" "}
            {b.po.status}
            <br />
            <span className={styles.muted}>
              {b.warehouse
                ? `${b.warehouse.code} — ${b.warehouse.name}`
                : "Warehouse"}
              {" · remaining "}
              {b.remainingValue.toFixed(2)} {b.po.currency}
              {" · qty left "}
              {b.remainingQty}
              {b.po.expected_date
                ? ` · expected ${b.po.expected_date.slice(0, 10)}`
                : ""}
            </span>
            {alerts.length > 0 ? (
              <ul className={styles.list}>
                {alerts.map((a) => (
                  <li key={`${b.po.id}-${a.kind}-${a.message}`}>
                    <span role="status">
                      {a.severity === "critical" ? "Alert" : "Notice"}: {a.message}
                    </span>
                  </li>
                ))}
              </ul>
            ) : null}
            <ul className={styles.list}>
              {b.lines.map((line) => {
                const rem =
                  Number(line.qty_ordered) - Number(line.qty_released ?? 0);
                const oem =
                  line.stock_items?.oem_part_number ??
                  line.stock_item_id.slice(0, 8);
                return (
                  <li key={line.id}>
                    {oem}
                    {line.stock_items?.description
                      ? ` — ${line.stock_items.description}`
                      : ""}
                    {" · left "}
                    {rem}
                  </li>
                );
              })}
            </ul>
          </li>
        );
      })}
    </ul>
  );
}
