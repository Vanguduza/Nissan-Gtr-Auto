"use client";

import { useEffect, useState } from "react";
import type { StockAvailability } from "@/lib/pos/types";
import type { PosStore } from "@/lib/pos/use-pos";
import { FulfillmentActions } from "./FulfillmentPanels";
import { Modal } from "./PosDialogs";
import styles from "./pos.module.css";

/** Stock of one part at every branch: what is on the shelf, held for orders, free to sell and on the way. */
export function StockDialog({ pos }: { pos: PosStore }) {
  const part = pos.stockPart;
  const [rows, setRows] = useState<StockAvailability[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  useEffect(() => {
    setRows(null);
    setError(null);
    if (!part?.stockItemId) return;
    let live = true;
    void pos.gateway.listStockAvailability(part.stockItemId).then((res) => {
      if (!live) return;
      if (res.ok) setRows(res.data);
      else setError(res.error);
    });
    return () => {
      live = false;
    };
  }, [pos.gateway, part?.stockItemId]);
  if (!part) return null;
  const here = pos.setup?.warehouseId ?? null;
  return (
    <Modal title="Stock by branch" onClose={pos.closeStock}>
      <p className={styles.muted}>
        {part.name} · {part.oemPartNumber}
      </p>
      {!pos.online ? <div className={styles.emptyCard}>Stock by branch needs a connection.</div> : null}
      {error ? <div className={styles.emptyCard}>{error}</div> : null}
      {pos.online && !error && rows == null ? <div className={styles.emptyCard}>Loading…</div> : null}
      {rows && rows.length === 0 ? <div className={styles.emptyCard}>No branch holds this part.</div> : null}
      {rows && rows.length > 0 ? (
        <table style={{ width: "100%", borderCollapse: "collapse", marginTop: 12, fontSize: 14 }}>
          <thead>
            <tr className={styles.muted} style={{ textAlign: "right" }}>
              <th style={{ textAlign: "left", fontWeight: 600, padding: "6px 0" }}>Branch</th>
              <th style={{ fontWeight: 600 }}>On hand</th>
              <th style={{ fontWeight: 600 }}>Held</th>
              <th style={{ fontWeight: 600 }}>Free</th>
              <th style={{ fontWeight: 600 }}>On the way</th>
            </tr>
          </thead>
          <tbody>
            {rows.map((r) => (
              <tr key={r.warehouseId} style={{ textAlign: "right", borderTop: "1px solid var(--gtr-color-neutral-border, #e5e7eb)" }}>
                <td style={{ textAlign: "left", padding: "8px 0" }}>
                  <div className={styles.listTitle}>{r.name || r.code}</div>
                  <div className={styles.muted}>
                    {r.code}
                    {r.warehouseId === here ? " · this branch" : ""}
                  </div>
                </td>
                <td>{r.onHand}</td>
                <td>{r.reserved}</td>
                <td className={r.available > 0 ? styles.stockIn : styles.stockOut} style={{ fontWeight: 700, fontSize: "inherit" }}>
                  {r.available}
                </td>
                <td>{r.incoming > 0 ? r.incoming : "–"}</td>
              </tr>
            ))}
          </tbody>
        </table>
      ) : null}
      {rows && rows.length > 0 && pos.online ? <FulfillmentActions pos={pos} part={part} rows={rows} here={here} /> : null}
    </Modal>
  );
}
