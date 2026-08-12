"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import styles from "@/components/account.module.css";
import { ProcurementProgressTracker } from "@/components/procurement-progress-tracker";
import {
  listRecentPurchaseOrderProgress,
  requireSession,
  type PurchaseOrderProgress,
} from "@/lib/preferred-po";
import { createWebClient } from "@/lib/supabase";

type Boot =
  | { kind: "loading" }
  | { kind: "auth" }
  | { kind: "error"; message: string }
  | { kind: "ready"; rows: PurchaseOrderProgress[] };

export function RecentPurchaseOrdersPanel() {
  const [boot, setBoot] = useState<Boot>({ kind: "loading" });

  const refresh = useCallback(async () => {
    const client = createWebClient();
    const session = await requireSession(client);
    if (!session.ok) {
      setBoot({ kind: "auth" });
      return;
    }
    const res = await listRecentPurchaseOrderProgress(client, 8);
    if (!res.ok) {
      setBoot({ kind: "error", message: res.error });
      return;
    }
    setBoot({ kind: "ready", rows: res.data });
  }, []);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  if (boot.kind === "loading") {
    return <p className={styles.formStatus}>Loading recent POs…</p>;
  }
  if (boot.kind === "auth") {
    return (
      <p className={styles.lede}>
        <Link href="/login">Sign in</Link> to see live PO progress.
      </p>
    );
  }
  if (boot.kind === "error") {
    return (
      <p className={styles.formStatus} role="alert">
        {boot.message}{" "}
        <button type="button" className={styles.btnGhost} onClick={() => void refresh()}>
          Retry
        </button>
      </p>
    );
  }

  if (boot.rows.length === 0) {
    return (
      <p className={styles.muted}>
        No purchase orders yet.{" "}
        <Link href="/procurement/orders/new">Create a preferred-supplier PO</Link>.
      </p>
    );
  }

  return (
    <div className={styles.form}>
      <h2 className={styles.title} style={{ fontSize: "1.1rem" }}>
        Live PO progress
      </h2>
      <p className={styles.lede}>
        Bound to <code>purchase_orders</code> status,{" "}
        <code>funds_released_at</code>, receive qty, and{" "}
        <code>progress_step</code> via <code>resolveProcurementProgress</code>.
      </p>
      <ul className={styles.list}>
        {boot.rows.map((po) => (
          <li key={po.id}>
            <Link href={`/procurement/orders/${po.id}`}>
              <strong>{po.document_number ?? po.id.slice(0, 8)}</strong>
            </Link>
            {po.supplier ? ` · ${po.supplier.code} ${po.supplier.name}` : ""}
            <span className={styles.muted}>
              {" "}
              · recv {po.qty_received}/{po.qty_ordered}
            </span>
            <ProcurementProgressTracker
              step={po.step}
              documentLabel={po.document_number ?? po.id.slice(0, 8)}
            />
          </li>
        ))}
      </ul>
    </div>
  );
}
