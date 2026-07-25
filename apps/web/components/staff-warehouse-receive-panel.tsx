"use client";

import Link from "next/link";
import { FormEvent, useCallback, useEffect, useState } from "react";
import styles from "@/components/account.module.css";
import {
  listWarehouses,
  postStockReceipt,
  requireSession,
  searchStockItems,
  type CurrencyCode,
  type StockItemOption,
  type WarehouseOption,
} from "@/lib/staff-warehouse";
import { createWebClient } from "@/lib/supabase";

type Boot =
  | { kind: "loading" }
  | { kind: "auth" }
  | { kind: "error"; message: string }
  | { kind: "ready"; warehouses: WarehouseOption[] };

type DraftLine = {
  key: string;
  item: StockItemOption;
  qty: string;
  unitCost: string;
  currency: CurrencyCode;
  serials: string;
};

export function StaffWarehouseReceivePanel() {
  const [boot, setBoot] = useState<Boot>({ kind: "loading" });
  const [warehouseId, setWarehouseId] = useState("");
  const [notes, setNotes] = useState("");
  const [itemQuery, setItemQuery] = useState("");
  const [hits, setHits] = useState<StockItemOption[]>([]);
  const [draftLines, setDraftLines] = useState<DraftLine[]>([]);
  const [qty, setQty] = useState("1");
  const [unitCost, setUnitCost] = useState("0");
  const [lineCurrency, setLineCurrency] = useState<CurrencyCode>("USD");
  const [serials, setSerials] = useState("");
  const [pendingItem, setPendingItem] = useState<StockItemOption | null>(null);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<string | null>(null);

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
    const wh = await listWarehouses(client, { includeQuarantine: true });
    if (!wh.ok) {
      setBoot({ kind: "error", message: wh.error });
      return;
    }
    setBoot({ kind: "ready", warehouses: wh.data });
    setWarehouseId((prev) => prev || wh.data[0]?.id || "");
  }, []);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  useEffect(() => {
    if (boot.kind !== "ready") return;
    const q = itemQuery.trim();
    if (q.length < 2 || pendingItem) {
      setHits([]);
      return;
    }
    const t = window.setTimeout(() => {
      void (async () => {
        const client = createWebClient();
        if (!client) return;
        const res = await searchStockItems(client, q);
        if (!res.ok) {
          setMessage(res.error);
          return;
        }
        setHits(res.data);
      })();
    }, 250);
    return () => window.clearTimeout(t);
  }, [boot.kind, itemQuery, pendingItem]);

  function addDraftLine(e: FormEvent) {
    e.preventDefault();
    if (!pendingItem) {
      setMessage("Select a stock item from search.");
      return;
    }
    if (!pendingItem.base_uom_id) {
      setMessage("Item has no base UOM.");
      return;
    }
    const n = Number(qty);
    const cost = Number(unitCost);
    if (!Number.isFinite(n) || n <= 0) {
      setMessage("Qty must be positive.");
      return;
    }
    if (!Number.isFinite(cost) || cost < 0) {
      setMessage("Unit cost must be >= 0.");
      return;
    }
    setDraftLines((prev) => [
      ...prev,
      {
        key: `${pendingItem.id}-${Date.now()}`,
        item: pendingItem,
        qty: String(n),
        unitCost: String(cost),
        currency: lineCurrency,
        serials: serials.trim(),
      },
    ]);
    setPendingItem(null);
    setItemQuery("");
    setHits([]);
    setQty("1");
    setUnitCost("0");
    setSerials("");
    setMessage(null);
  }

  async function onPost(e: FormEvent) {
    e.preventDefault();
    const client = createWebClient();
    if (!client || !warehouseId || draftLines.length === 0) return;
    setBusy(true);
    setMessage(null);
    const res = await postStockReceipt(client, {
      toWarehouseId: warehouseId,
      notes: notes.trim() || "Staff receive",
      lines: draftLines.map((d) => ({
        stock_item_id: d.item.id,
        uom_id: d.item.base_uom_id!,
        qty: Number(d.qty),
        unit_cost: Number(d.unitCost),
        currency: d.currency,
        serials: d.serials
          ? d.serials.split(/[\s,]+/).filter(Boolean)
          : undefined,
      })),
    });
    setBusy(false);
    if (!res.ok) {
      setMessage(res.error);
      return;
    }
    setDraftLines([]);
    setNotes("");
    setMessage(`Receipt posted · ${res.data.slice(0, 8)}…`);
  }

  if (boot.kind === "loading") {
    return <p className={styles.muted}>Loading receive…</p>;
  }
  if (boot.kind === "auth") {
    return (
      <p className={styles.lede}>
        <Link href="/login">Sign in</Link> with warehouse staff to post
        receipts.
      </p>
    );
  }
  if (boot.kind === "error") {
    return (
      <p className={styles.lede} role="alert">
        {boot.message}{" "}
        <button type="button" className={styles.btnGhost} onClick={() => void refresh()}>
          Retry
        </button>
      </p>
    );
  }

  return (
    <div className={styles.form}>
      <fieldset className={styles.fieldset}>
        <legend className={styles.legend}>Destination · notes</legend>
        <div className={styles.formGrid}>
          <label className={styles.field}>
            Warehouse
            <select
              value={warehouseId}
              onChange={(e) => setWarehouseId(e.target.value)}
              disabled={busy}
            >
              {boot.warehouses.map((w) => (
                <option key={w.id} value={w.id}>
                  {w.code} — {w.name}
                  {w.is_quarantine ? " (quarantine)" : ""}
                </option>
              ))}
            </select>
          </label>
          <label className={styles.field}>
            Notes
            <input
              value={notes}
              onChange={(e) => setNotes(e.target.value)}
              disabled={busy}
              placeholder="Optional"
            />
          </label>
        </div>
      </fieldset>

      <fieldset className={styles.fieldset}>
        <legend className={styles.legend}>Add line (OEM typed)</legend>
        <form onSubmit={addDraftLine}>
          <div className={styles.formGrid}>
            <label className={styles.field}>
              OEM / stock id
              <input
                value={itemQuery}
                onChange={(e) => {
                  setItemQuery(e.target.value);
                  setPendingItem(null);
                }}
                disabled={busy}
                autoComplete="off"
              />
            </label>
            <label className={styles.field}>
              Qty
              <input
                type="number"
                min="0.0001"
                step="any"
                value={qty}
                onChange={(e) => setQty(e.target.value)}
                disabled={busy}
              />
            </label>
            <label className={styles.field}>
              Unit cost
              <input
                type="number"
                min="0"
                step="any"
                value={unitCost}
                onChange={(e) => setUnitCost(e.target.value)}
                disabled={busy}
              />
            </label>
            <label className={styles.field}>
              Currency
              <select
                value={lineCurrency}
                onChange={(e) => setLineCurrency(e.target.value as CurrencyCode)}
                disabled={busy}
              >
                <option value="USD">USD</option>
                <option value="ZIG">ZIG</option>
              </select>
            </label>
            <label className={styles.field}>
              Serials (comma-separated, if required)
              <input
                value={serials}
                onChange={(e) => setSerials(e.target.value)}
                disabled={busy}
                placeholder="Optional"
              />
            </label>
          </div>
          {hits.length > 0 && !pendingItem ? (
            <ul className={styles.list}>
              {hits.map((item) => (
                <li key={item.id}>
                  <button
                    type="button"
                    className={styles.btnGhost}
                    onClick={() => {
                      setPendingItem(item);
                      setItemQuery(item.oem_part_number);
                      setHits([]);
                    }}
                  >
                    {item.oem_part_number}
                    {item.requires_serial ? " · serials" : ""}
                  </button>
                </li>
              ))}
            </ul>
          ) : null}
          {pendingItem ? (
            <p className={styles.muted} style={{ marginTop: "0.65rem" }}>
              {pendingItem.oem_part_number}
              {pendingItem.requires_serial ? " · requires serials" : ""}
            </p>
          ) : null}
          <div className={styles.formActions}>
            <button type="submit" className={styles.btn} disabled={busy || !pendingItem}>
              Queue line
            </button>
          </div>
        </form>
      </fieldset>

      <fieldset className={styles.fieldset}>
        <legend className={styles.legend}>Queued · post</legend>
        {draftLines.length === 0 ? (
          <p className={styles.muted}>No lines queued.</p>
        ) : (
          <ul className={styles.list}>
            {draftLines.map((d) => (
              <li key={d.key}>
                <code>{d.item.oem_part_number}</code> · qty {d.qty} · cost{" "}
                {d.unitCost} {d.currency}
                <button
                  type="button"
                  className={styles.btnGhost}
                  style={{ marginLeft: "0.5rem" }}
                  disabled={busy}
                  onClick={() =>
                    setDraftLines((prev) => prev.filter((x) => x.key !== d.key))
                  }
                >
                  Remove
                </button>
              </li>
            ))}
          </ul>
        )}
        <form onSubmit={(e) => void onPost(e)}>
          <div className={styles.formActions}>
            <button
              type="submit"
              className={styles.btn}
              disabled={busy || draftLines.length === 0 || !warehouseId}
            >
              Post receipt
            </button>
          </div>
        </form>
      </fieldset>

      {message ? (
        <p className={styles.formStatus} role="status">
          {message}
        </p>
      ) : null}
    </div>
  );
}
