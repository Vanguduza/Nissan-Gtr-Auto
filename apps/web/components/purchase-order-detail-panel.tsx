"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import styles from "@/components/account.module.css";
import { ProcurementProgressTracker } from "@/components/procurement-progress-tracker";
import {
  listPoLines,
  loadPurchaseOrderProgress,
  requireSession,
  type PurchaseOrderProgress,
} from "@/lib/preferred-po";
import { createWebClient } from "@/lib/supabase";

type Line = {
  id: string;
  oem_part_number: string;
  description: string | null;
  qty_ordered: number;
  qty_received: number;
  unit_price: number;
};

type Boot =
  | { kind: "loading" }
  | { kind: "auth" }
  | { kind: "error"; message: string }
  | { kind: "ready"; po: PurchaseOrderProgress; lines: Line[] };

export function PurchaseOrderDetailPanel({ purchaseOrderId }: { purchaseOrderId: string }) {
  const [boot, setBoot] = useState<Boot>({ kind: "loading" });

  const refresh = useCallback(async () => {
    const client = createWebClient();
    if (!client) {
      setBoot({
        kind: "error",
        message: "Supabase is not configured on this environment.",
      });
      return;
    }
    const session = await requireSession(client);
    if (!session.ok) {
      setBoot({ kind: "auth" });
      return;
    }
    const [poRes, linesRes] = await Promise.all([
      loadPurchaseOrderProgress(client, purchaseOrderId),
      listPoLines(client, purchaseOrderId),
    ]);
    if (!poRes.ok) {
      setBoot({ kind: "error", message: poRes.error });
      return;
    }
    if (!linesRes.ok) {
      setBoot({ kind: "error", message: linesRes.error });
      return;
    }
    setBoot({
      kind: "ready",
      po: poRes.data,
      lines: linesRes.data.map((l) => ({
        id: l.id,
        oem_part_number: l.oem_part_number,
        description: l.description,
        qty_ordered: l.qty_ordered,
        qty_received: l.qty_received,
        unit_price: l.unit_price,
      })),
    });
  }, [purchaseOrderId]);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  if (boot.kind === "loading") {
    return <p className={styles.formStatus}>Loading purchase order…</p>;
  }
  if (boot.kind === "auth") {
    return (
      <p className={styles.lede}>
        <Link href="/login">Sign in</Link> to view this PO.
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

  const { po, lines } = boot;
  const label = po.document_number ?? po.id.slice(0, 8);
  const canGrn = po.status === "approved" && po.step !== "closed";

  return (
    <div className={styles.form}>
      <p className={styles.muted}>
        {label} · status <strong>{po.status}</strong>
        {po.progress_step ? ` · progress_step ${po.progress_step}` : ""}
        {po.funds_released_at
          ? ` · funds ${po.funds_released_at.slice(0, 10)}`
          : ""}
      </p>
      {po.supplier ? (
        <p className={styles.lede}>
          {po.supplier.code} — {po.supplier.name}
        </p>
      ) : null}

      <ProcurementProgressTracker step={po.step} documentLabel={label} />

      <div className={styles.tableWrap} style={{ marginTop: "1rem" }}>
        <table className={styles.table}>
          <thead>
            <tr>
              <th>OEM</th>
              <th>Ordered</th>
              <th>Received</th>
              <th>Unit</th>
            </tr>
          </thead>
          <tbody>
            {lines.length === 0 ? (
              <tr>
                <td colSpan={4}>No lines.</td>
              </tr>
            ) : (
              lines.map((l) => (
                <tr key={l.id}>
                  <td>
                    {l.oem_part_number}
                    {l.description ? ` — ${l.description}` : ""}
                  </td>
                  <td>{l.qty_ordered}</td>
                  <td>{l.qty_received}</td>
                  <td>{l.unit_price}</td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>

      <div className={styles.formActions}>
        {canGrn ? (
          <Link href="/procurement/grn" className={styles.btn}>
            Receive (GRN)
          </Link>
        ) : null}
        {po.status === "submitted" ? (
          <Link href="/procurement/approvals" className={styles.btn}>
            Approvals queue
          </Link>
        ) : null}
        <Link href="/procurement/orders/new" className={styles.btnGhost}>
          New PO
        </Link>
        <button type="button" className={styles.btnGhost} onClick={() => void refresh()}>
          Refresh
        </button>
      </div>
    </div>
  );
}
