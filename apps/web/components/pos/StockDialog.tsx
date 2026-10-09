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
  const noneHere = rows != null && !rows.some((r) => r.warehouseId === here && r.available > 0);
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
              <tr key={r.warehouseId} style={{ textAlign: "right", borderTop: "1px solid var(--gtr-color-neutral-borderSubtle)" }}>
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
      {noneHere && here && part.stockItemId && pos.online ? <LostDemand pos={pos} stockItemId={part.stockItemId} warehouseId={here} /> : null}
    </Modal>
  );
}

/** None free here: note that a customer wanted it, so restocking counts the demand the till never saw. */
function LostDemand({ pos, stockItemId, warehouseId }: { pos: PosStore; stockItemId: string; warehouseId: string }) {
  const [qty, setQty] = useState("1");
  const [done, setDone] = useState(false);
  const [busy, setBusy] = useState(false);
  if (done) return <p className={styles.muted}>Noted: restocking counts it.</p>;
  return (
    <form
      className={styles.row}
      style={{ gap: 8, marginTop: 12, flexWrap: "wrap" }}
      onSubmit={async (e) => {
        e.preventDefault();
        const n = Number(qty);
        if (!Number.isFinite(n) || n <= 0) return pos.showError("Enter how many the customer wanted.");
        setBusy(true);
        const res = await pos.gateway.recordLostDemand(stockItemId, warehouseId, n, "customer wanted it, none free here");
        setBusy(false);
        if (!res.ok) return pos.showError(res.error);
        setDone(true);
      }}
    >
      <span className={styles.muted} style={{ flex: "1 1 220px" }}>
        None free here. If the customer leaves without it, note it so this part is restocked:
      </span>
      <input className={styles.input} style={{ width: 80 }} inputMode="numeric" aria-label="How many the customer wanted" value={qty} onChange={(e) => setQty(e.target.value)} />
      <button type="submit" className={styles.softButton} disabled={busy}>
        Customer wanted it
      </button>
    </form>
  );
}

