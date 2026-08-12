"use client";

import { FormEvent, useCallback, useEffect, useState } from "react";
import Link from "next/link";
import type { ProcurementProgressStep } from "@gtr/procurement";
import styles from "@/components/account.module.css";
import { ProcurementProgressTracker } from "@/components/procurement-progress-tracker";
import { createWebClient } from "@/lib/supabase";
import {
  createPreferredPurchaseOrder,
  loadPreferredSuppliers,
  loadPurchaseOrderProgress,
  loadWarehouses,
  pickReceivingWarehouse,
  requireSession,
  searchStockItems,
  type CurrencyCode,
  type PoLineDraft,
  type PreferredSupplierOption,
  type StockItemOption,
  type WarehouseOption,
} from "@/lib/preferred-po";

type Boot =
  | { kind: "loading" }
  | { kind: "auth" }
  | { kind: "error"; message: string }
  | {
      kind: "ready";
      suppliers: PreferredSupplierOption[];
      warehouses: WarehouseOption[];
    };

export function ManualPurchaseOrderPanel() {
  const [boot, setBoot] = useState<Boot>({ kind: "loading" });
  const [supplierId, setSupplierId] = useState("");
  const [warehouseId, setWarehouseId] = useState("");
  const [currency, setCurrency] = useState<CurrencyCode>("USD");
  const [exchangeRate, setExchangeRate] = useState("1");
  const [notes, setNotes] = useState("");
  const [expectedDate, setExpectedDate] = useState("");
  const [itemQuery, setItemQuery] = useState("");
  const [hits, setHits] = useState<StockItemOption[]>([]);
  const [lines, setLines] = useState<PoLineDraft[]>([]);
  const [qty, setQty] = useState("1");
  const [unitPrice, setUnitPrice] = useState("0");
  const [pending, setPending] = useState<StockItemOption | null>(null);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [createdId, setCreatedId] = useState<string | null>(null);
  const [trackerStep, setTrackerStep] =
    useState<ProcurementProgressStep>("draft");
  const [trackerLabel, setTrackerLabel] = useState("New PO");

  const refreshCreatedProgress = useCallback(async (id: string) => {
    const client = createWebClient();
    if (!client) return;
    const res = await loadPurchaseOrderProgress(client, id);
    if (!res.ok) return;
    setTrackerStep(res.data.step);
    setTrackerLabel(res.data.document_number ?? id.slice(0, 8));
  }, []);

  const refresh = useCallback(async () => {
    const client = createWebClient();
    if (!client) {
      setBoot({ kind: "auth" });
      return;
    }
    const session = await requireSession(client);
    if (!session.ok) {
      setBoot({ kind: "auth" });
      return;
    }
    const [sup, wh] = await Promise.all([
      loadPreferredSuppliers(client),
      loadWarehouses(client),
    ]);
    if (!sup.ok) {
      setBoot({ kind: "error", message: sup.error });
      return;
    }
    if (!wh.ok) {
      setBoot({ kind: "error", message: wh.error });
      return;
    }
    const recv = pickReceivingWarehouse(wh.data);
    setBoot({ kind: "ready", suppliers: sup.data, warehouses: wh.data });
    setSupplierId((p) => p || sup.data[0]?.id || "");
    setWarehouseId((p) => p || recv?.id || "");
    if (sup.data[0]?.default_currency) {
      setCurrency(sup.data[0].default_currency);
    }
  }, []);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  useEffect(() => {
    if (boot.kind !== "ready") return;
    const q = itemQuery.trim();
    if (q.length < 2 || pending) {
      setHits([]);
      return;
    }
    const t = setTimeout(() => {
      void (async () => {
        const client = createWebClient();
        if (!client) return;
        const res = await searchStockItems(client, q);
        if (res.ok) setHits(res.data);
      })();
    }, 250);
    return () => clearTimeout(t);
  }, [boot.kind, itemQuery, pending]);

  function addLine() {
    if (!pending?.base_uom_id) {
      setMessage("Select a catalog item with a base UOM.");
      return;
    }
    const q = Number(qty);
    const p = Number(unitPrice);
    if (!(q > 0) || !(p >= 0)) {
      setMessage("Qty must be > 0 and unit price ≥ 0.");
      return;
    }
    setLines((prev) => [
      ...prev,
      {
        stock_item_id: pending.id,
        uom_id: pending.base_uom_id!,
        qty: q,
        unit_price: p,
        currency,
        oem_part_number: pending.oem_part_number,
        description: pending.description,
      },
    ]);
    setPending(null);
    setItemQuery("");
    setHits([]);
    setQty("1");
    setUnitPrice("0");
    setMessage(null);
  }

  async function onSubmit(e: FormEvent, submit: boolean) {
    e.preventDefault();
    if (lines.length === 0) {
      setMessage("Add at least one line.");
      return;
    }
    setBusy(true);
    setMessage(null);
    const client = createWebClient();
    const res = await createPreferredPurchaseOrder(client, {
      supplierId,
      warehouseId,
      currency,
      exchangeRate: Number(exchangeRate) || 1,
      lines,
      notes: notes || undefined,
      expectedDate: expectedDate || undefined,
      submit,
    });
    setBusy(false);
    if (!res.ok) {
      setMessage(res.error);
      return;
    }
    setCreatedId(res.data.purchaseOrderId);
    setLines([]);
    await refreshCreatedProgress(res.data.purchaseOrderId);
    setMessage(
      submit
        ? `PO submitted for approval (${res.data.purchaseOrderId.slice(0, 8)}…).`
        : `Draft PO saved (${res.data.purchaseOrderId.slice(0, 8)}…).`,
    );
  }

  if (boot.kind === "loading") {
    return <p className={styles.formStatus}>Loading…</p>;
  }
  if (boot.kind === "auth") {
    return (
      <p className={styles.lede}>
        <Link href="/login">Sign in</Link> as procurement staff.
      </p>
    );
  }
  if (boot.kind === "error") {
    return <p className={styles.formStatus}>{boot.message}</p>;
  }

  return (
    <div className={styles.panel}>
      <h2 className={styles.title}>New purchase order</h2>
      <p className={styles.lede}>
        Build from the preferred supplier roster with quoted figures. Not tied to
        RFQ awards. AI restock suggestions are advisory only.
      </p>
      <ProcurementProgressTracker
        step={createdId ? trackerStep : "draft"}
        documentLabel={createdId ? trackerLabel : "New PO"}
      />
      {createdId ? (
        <p className={styles.muted}>
          <Link href={`/procurement/orders/${createdId}`}>
            Open PO detail (live status through closed)
          </Link>
        </p>
      ) : null}
      {message ? <p className={styles.formStatus}>{message}</p> : null}

      <form className={styles.form} onSubmit={(e) => void onSubmit(e, true)}>
        <div className={styles.formGrid}>
          <label className={styles.field}>
            Preferred supplier
            <select
              required
              value={supplierId}
              onChange={(e) => setSupplierId(e.target.value)}
            >
              {boot.suppliers.map((s) => (
                <option key={s.id} value={s.id}>
                  {s.code} — {s.name}
                </option>
              ))}
            </select>
          </label>
          <label className={styles.field}>
            Receive into (WH1)
            <select
              required
              value={warehouseId}
              onChange={(e) => setWarehouseId(e.target.value)}
            >
              {boot.warehouses.map((w) => (
                <option key={w.id} value={w.id}>
                  {w.code} — {w.name}
                </option>
              ))}
            </select>
          </label>
          <label className={styles.field}>
            Currency
            <select
              value={currency}
              onChange={(e) => setCurrency(e.target.value as CurrencyCode)}
            >
              <option value="USD">USD</option>
              <option value="ZIG">ZIG</option>
            </select>
          </label>
          <label className={styles.field}>
            Exchange rate
            <input
              value={exchangeRate}
              onChange={(e) => setExchangeRate(e.target.value)}
              inputMode="decimal"
            />
          </label>
          <label className={styles.field}>
            Expected date
            <input
              type="date"
              value={expectedDate}
              onChange={(e) => setExpectedDate(e.target.value)}
            />
          </label>
          <label className={styles.field} style={{ gridColumn: "1 / -1" }}>
            Notes
            <textarea
              value={notes}
              onChange={(e) => setNotes(e.target.value)}
              rows={2}
            />
          </label>
        </div>

        <fieldset className={styles.fieldset}>
          <legend className={styles.legend}>Lines (OEM → catalog)</legend>
          <div className={styles.formGrid}>
            <label className={styles.field} style={{ gridColumn: "1 / -1" }}>
              Search part
              <input
                value={itemQuery}
                onChange={(e) => {
                  setPending(null);
                  setItemQuery(e.target.value);
                }}
                placeholder="OEM part number"
              />
            </label>
            {hits.length > 0 && !pending ? (
              <ul className={styles.list}>
                {hits.slice(0, 8).map((h) => (
                  <li key={h.id}>
                    <button
                      type="button"
                      className={styles.btnGhost}
                      onClick={() => {
                        setPending(h);
                        setItemQuery(h.oem_part_number);
                        setHits([]);
                      }}
                    >
                      {h.oem_part_number} — {h.description ?? "no description"}
                    </button>
                  </li>
                ))}
              </ul>
            ) : null}
            <label className={styles.field}>
              Qty
              <input value={qty} onChange={(e) => setQty(e.target.value)} />
            </label>
            <label className={styles.field}>
              Quoted unit price
              <input
                value={unitPrice}
                onChange={(e) => setUnitPrice(e.target.value)}
              />
            </label>
          </div>
          <div className={styles.formActions}>
            <button type="button" className={styles.btn} onClick={addLine}>
              Add line
            </button>
          </div>
          <div className={styles.tableWrap}>
            <table className={styles.table}>
              <thead>
                <tr>
                  <th>OEM</th>
                  <th>Qty</th>
                  <th>Unit</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {lines.map((l, i) => (
                  <tr key={`${l.stock_item_id}-${i}`}>
                    <td>
                      {l.oem_part_number}
                      {l.description ? ` — ${l.description}` : ""}
                    </td>
                    <td>{l.qty}</td>
                    <td>
                      {l.unit_price} {l.currency ?? currency}
                    </td>
                    <td>
                      <button
                        type="button"
                        className={styles.btnGhost}
                        onClick={() =>
                          setLines((prev) => prev.filter((_, j) => j !== i))
                        }
                      >
                        Remove
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </fieldset>

        <div className={styles.formActions}>
          <button
            type="button"
            className={styles.btnGhost}
            disabled={busy}
            onClick={(e) => void onSubmit(e, false)}
          >
            Save draft
          </button>
          <button type="submit" className={styles.btn} disabled={busy}>
            {busy ? "Submitting…" : "Submit for approval"}
          </button>
          <Link href="/procurement/approvals" className={styles.btnGhost}>
            Approvals queue
          </Link>
        </div>
      </form>
    </div>
  );
}
