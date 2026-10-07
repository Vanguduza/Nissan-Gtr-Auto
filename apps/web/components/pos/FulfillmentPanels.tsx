"use client";

import { ArrowRightLeft, PackageCheck, Search, Store, Truck } from "lucide-react";
import { useCallback, useEffect, useState } from "react";
import type { FulfillmentInput, FulfillmentKind, FulfillmentRequest, FulfillmentStep, PosPart, StockAvailability } from "@/lib/pos/types";
import type { PosStore } from "@/lib/pos/use-pos";
import styles from "./pos.module.css";

const when = (iso: string | null) => (iso ? new Date(iso).toLocaleString(undefined, { dateStyle: "medium", timeStyle: "short" }) : null);

export const KIND_LABEL: Record<FulfillmentKind, string> = {
  customer_collection: "Held for collection",
  alternate_pickup: "Collect at another branch",
  branch_transfer: "Branch transfer",
  backorder: "Back-order",
};

export const STATUS_LABEL: Record<FulfillmentRequest["status"], string> = {
  requested: "Waiting for stock",
  reserved: "Held",
  awaiting_transfer_approval: "Transfer sent to the warehouse",
  ready: "Ready",
  collected: "Collected",
  cancelled: "Cancelled",
  rejected: "Rejected by the warehouse",
};

/**
 * Getting a part to the customer from "Stock by branch" (phase 7): hold it here, hold it at another
 * branch for the customer to collect there, bring it here by branch transfer, or back-order it. A hold
 * goes with the current sale: when that sale is paid, the hold becomes ready with its invoice.
 */
export function FulfillmentActions({ pos, part, rows, here }: { pos: PosStore; part: PosPart; rows: StockAvailability[]; here: string | null }) {
  const [qty, setQty] = useState(1);
  const [working, setWorking] = useState<string | null>(null);
  const cart = pos.cart;
  const inSale = Boolean(cart && cart.lines.some((l) => l.stockItemId === part.stockItemId));
  const freeHere = rows.find((r) => r.warehouseId === here)?.available ?? 0;
  const holdWhy = !cart ? "Start a sale with this part first" : !inSale ? "Add the part to the sale first, so the hold is paid with it" : null;

  const request = async (key: string, input: Omit<FulfillmentInput, "stockItemId" | "qty" | "customerId" | "notes" | "holdMinutes">, done: string) => {
    if (!part.stockItemId || !pos.online) return;
    setWorking(key);
    const res = await pos.gateway.createFulfillment({
      ...input,
      stockItemId: part.stockItemId,
      qty,
      customerId: cart?.customerId ?? null,
      notes: null,
      holdMinutes: 120,
    });
    setWorking(null);
    if (!res.ok) return pos.showError(res.error);
    pos.showNotice(done);
    pos.closeStock();
  };

  if (!part.stockItemId) return null;
  return (
    <div className={styles.panelInset} style={{ marginTop: 14 }}>
      <div className={styles.row} style={{ justifyContent: "space-between", flexWrap: "wrap", gap: 8 }}>
        <strong>Get it for the customer</strong>
        <label className={styles.row} style={{ gap: 8 }}>
          <span className={styles.fieldLabel}>Quantity</span>
          <input className={styles.input} style={{ width: 80 }} type="number" min={1} step={1} value={qty} onChange={(e) => setQty(Math.max(1, Math.floor(Number(e.target.value) || 1)))} />
        </label>
      </div>
      <div className={styles.list}>
        {rows.map((r) => {
          const enough = r.available >= qty;
          if (r.warehouseId === here) {
            return (
              <div key={r.warehouseId} className={styles.listRow} style={{ flexWrap: "wrap" }}>
                <span>
                  <div className={styles.listTitle}>Hold here for collection</div>
                  <div className={styles.muted}>{!enough ? `Only ${r.available} free here` : holdWhy ?? "Kept for 2 hours; ready as soon as the sale is paid."}</div>
                </span>
                <button
                  type="button"
                  className={`${styles.softButton} ${styles.inlineButton}`}
                  disabled={!enough || Boolean(holdWhy) || working !== null || !pos.online}
                  onClick={() => void request(`h-${r.warehouseId}`, { kind: "customer_collection", sourceWarehouseId: r.warehouseId, destinationWarehouseId: null, cartId: cart?.id ?? null }, "Held for collection. It becomes ready when the sale is paid.")}
                >
                  <PackageCheck size={16} aria-hidden /> Hold
                </button>
              </div>
            );
          }
          return (
            <div key={r.warehouseId} className={styles.listRow} style={{ flexWrap: "wrap" }}>
              <span>
                <div className={styles.listTitle}>{r.name || r.code}</div>
                <div className={styles.muted}>{enough ? `${r.available} free` : `Only ${r.available} free`}</div>
              </span>
              <span className={styles.row} style={{ gap: 8, flexWrap: "wrap" }}>
                <button
                  type="button"
                  className={`${styles.softButton} ${styles.inlineButton}`}
                  title={holdWhy ?? undefined}
                  disabled={!enough || Boolean(holdWhy) || working !== null || !pos.online}
                  onClick={() =>
                    void request(`p-${r.warehouseId}`, { kind: "alternate_pickup", sourceWarehouseId: r.warehouseId, destinationWarehouseId: null, cartId: cart?.id ?? null }, `Held at ${r.name || r.code}. The customer collects it there once the sale is paid.`)
                  }
                >
                  <Store size={16} aria-hidden /> Collect there
                </button>
                <button
                  type="button"
                  className={`${styles.softButton} ${styles.inlineButton}`}
                  title={!here ? "Choose this till's branch first" : undefined}
                  disabled={!enough || !here || working !== null || !pos.online}
                  onClick={() =>
                    void request(`t-${r.warehouseId}`, { kind: "branch_transfer", sourceWarehouseId: r.warehouseId, destinationWarehouseId: here, cartId: cart?.id ?? null }, `Transfer from ${r.name || r.code} requested. The warehouse approves and sends it.`)
                  }
                >
                  <ArrowRightLeft size={16} aria-hidden /> Bring here
                </button>
              </span>
            </div>
          );
        })}
        <div className={styles.listRow} style={{ flexWrap: "wrap" }}>
          <span>
            <div className={styles.listTitle}>Back-order</div>
            <div className={styles.muted}>{freeHere >= qty ? "There is stock here: sell or hold it instead." : "Order it in; mark it ready when it arrives."}</div>
          </span>
          <button
            type="button"
            className={`${styles.softButton} ${styles.inlineButton}`}
            disabled={freeHere >= qty || working !== null || !pos.online}
            onClick={() => void request("b", { kind: "backorder", sourceWarehouseId: null, destinationWarehouseId: here, cartId: cart?.id ?? null }, "Back-order recorded.")}
          >
            <Truck size={16} aria-hidden /> Back-order
          </button>
        </div>
      </div>
    </div>
  );
}

const FILTERS: { value: string | null; label: string }[] = [
  { value: null, label: "All" },
  { value: "reserved", label: "Held" },
  { value: "awaiting_transfer_approval", label: "In transfer" },
  { value: "requested", label: "Waiting for stock" },
  { value: "ready", label: "Ready" },
  { value: "collected", label: "Collected" },
];

/** Orders → Collections & transfers: everything held, in transfer or on order, and its next step. */
export function FulfillmentList({ pos }: { pos: PosStore }) {
  const [status, setStatus] = useState<string | null>(null);
  const [query, setQuery] = useState("");
  const [rows, setRows] = useState<FulfillmentRequest[]>([]);
  const [busy, setBusy] = useState<string | null>(null);
  const load = useCallback(async () => {
    const res = await pos.gateway.listFulfillment(query, status);
    if (res.ok) setRows(res.data);
    else pos.showError(res.error);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [pos.gateway, query, status]);
  useEffect(() => {
    void load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [status, pos.notice]);

  const step = async (f: FulfillmentRequest, s: FulfillmentStep, done: string) => {
    setBusy(f.id + s);
    const res = await pos.gateway.fulfillmentStep(f.id, s, null);
    setBusy(null);
    if (!res.ok) return pos.showError(s === "approve" && /warehouse/i.test(res.error) ? "Only warehouse staff can send a branch transfer." : res.error);
    pos.showNotice(done);
    void load();
  };

  /** The back-order is tied to the open sale and its part is on it. */
  /** Arrived and waiting for this customer to buy it: a back-order, or a transfer made for a customer. */
  const sellable = (f: FulfillmentRequest) =>
    f.status === "ready" && !f.invoiceId && (f.kind === "backorder" || (f.kind === "branch_transfer" && !!f.customerId));

  const onThisSale = (f: FulfillmentRequest) =>
    !!f.cartId && f.cartId === pos.cart?.id && pos.cart.lines.some((l) => l.stockItemId === f.stockItemId);

  const route = (f: FulfillmentRequest) =>
    f.kind === "branch_transfer" ? `${f.sourceName ?? "?"} → ${f.destinationName ?? "?"}` : f.kind === "backorder" ? f.destinationName : f.sourceName;

  return (
    <>
      <div className={styles.row} style={{ flexWrap: "wrap", gap: 8 }}>
        <div className={styles.segment} aria-label="Status">
          {FILTERS.map((f) => (
            <button key={f.label} type="button" className={`${styles.segmentItem} ${status === f.value ? styles.segmentActive : ""}`} onClick={() => setStatus(f.value)}>
              {f.label}
            </button>
          ))}
        </div>
        <form
          className={styles.row}
          style={{ flex: 1, minWidth: 220 }}
          onSubmit={(e) => {
            e.preventDefault();
            void load();
          }}
        >
          <input className={styles.input} style={{ flex: 1 }} placeholder="Request or part number" value={query} onChange={(e) => setQuery(e.target.value)} aria-label="Search requests" />
          <button type="submit" className={`${styles.softButton} ${styles.inlineButton}`}>
            <Search size={16} aria-hidden /> Search
          </button>
        </form>
      </div>
      {rows.length === 0 ? <div className={styles.emptyCard}>Nothing held, in transfer or on order. Use Stock by branch on a part card.</div> : null}
      {rows.map((f) => {
        const active = !["collected", "cancelled", "rejected"].includes(f.status);
        return (
          <div key={f.id} className={styles.listRow} style={{ flexWrap: "wrap" }}>
            <span style={{ flex: "1 1 260px" }}>
              <div className={styles.listTitle}>
                {f.documentNumber ?? f.id} · {f.description ?? f.partNumber} × {f.qty}
              </div>
              <div className={styles.muted}>
                {[KIND_LABEL[f.kind], STATUS_LABEL[f.status], route(f), f.status === "reserved" && f.expiresAt ? `held until ${when(f.expiresAt)}` : null, f.status === "ready" && !f.invoiceId && f.kind !== "branch_transfer" ? "not paid yet" : null]
                  .filter(Boolean)
                  .join(" · ")}
              </div>
            </span>
            <span className={styles.row} style={{ gap: 8, flexWrap: "wrap" }}>
              {f.kind === "branch_transfer" && f.status === "reserved" ? (
                <button type="button" className={styles.primaryButton} disabled={busy !== null || !pos.online} onClick={() => void step(f, "approve", "Transfer sent to the warehouse. It is ready here when the transfer is posted.")}>
                  Send transfer
                </button>
              ) : null}
              {/* Holds become ready when their sale is paid (the invoice links then); only a back-order is marked ready by hand. */}
              {f.kind === "backorder" && f.status === "requested" ? (
                <button type="button" className={styles.softButton} disabled={busy !== null || !pos.online} onClick={() => void step(f, "ready", "Marked ready: the part is held for this customer for 14 days.")}>
                  Mark ready
                </button>
              ) : null}
              {/* An arrived back-order (or transfer for a customer) is held for them, sold on the current sale, then handed over once paid. */}
              {sellable(f) && onThisSale(f) ? (
                <span className={styles.muted}>On this sale: take payment, then hand it over.</span>
              ) : null}
              {sellable(f) && !onThisSale(f) ? (
                <button
                  type="button"
                  className={styles.primaryButton}
                  disabled={busy !== null || !pos.online}
                  onClick={async () => {
                    setBusy(f.id + "sell");
                    const done = await pos.sellBackorder(f);
                    setBusy(null);
                    if (done) void load();
                  }}
                >
                  Add to sale
                </button>
              ) : null}
              {f.status === "ready" && !sellable(f) ? (
                <button
                  type="button"
                  className={styles.primaryButton}
                  disabled={busy !== null || !pos.online || (!f.invoiceId && f.kind !== "branch_transfer")}
                  title={!f.invoiceId && f.kind !== "branch_transfer" ? "Take payment on the sale first" : undefined}
                  onClick={() => void step(f, "collect", f.kind === "branch_transfer" ? "Transfer received here." : "Handed over to the customer.")}
                >
                  {f.kind === "branch_transfer" ? "Received here" : "Handed over"}
                </button>
              ) : null}
              {/* A paid hold is handed over (or returned through Returns), never just released. */}
              {active && f.status !== "awaiting_transfer_approval" && !(f.status === "ready" && f.invoiceId) ? (
                <button type="button" className={styles.linkButton} disabled={busy !== null || !pos.online} onClick={() => void step(f, "cancel", "Released.")}>
                  Release
                </button>
              ) : null}
            </span>
          </div>
        );
      })}
    </>
  );
}
