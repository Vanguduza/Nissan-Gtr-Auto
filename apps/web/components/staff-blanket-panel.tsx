"use client";

import Link from "next/link";
import { FormEvent, useCallback, useEffect, useState } from "react";
import styles from "@/components/account.module.css";
import {
  blanketAlerts,
  createBlanketPurchaseOrder,
  createBlanketRelease,
  listBlanketPurchaseOrders,
  loadSuppliers,
  loadWarehouses,
  requireSession,
  searchStockItems,
  submitPurchaseOrder,
  type BlanketSummary,
  type CurrencyCode,
  type StockItemOption,
  type SupplierOption,
  type WarehouseOption,
} from "@/lib/blanket-po";
import {
  approvePurchaseOrder,
  rejectPurchaseOrder,
} from "@/lib/procurement-approvals";
import { zigExchangeRate } from "@/lib/customer-storefront";
import { createWebClient } from "@/lib/supabase";

type Boot =
  | { kind: "loading" }
  | { kind: "auth" }
  | { kind: "error"; message: string }
  | {
      kind: "ready";
      blankets: BlanketSummary[];
      suppliers: SupplierOption[];
      warehouses: WarehouseOption[];
    };

type DraftLine = {
  key: string;
  item: StockItemOption;
  qty: string;
  unitPrice: string;
};

export function StaffBlanketPanel() {
  const [boot, setBoot] = useState<Boot>({ kind: "loading" });
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<string | null>(null);

  const [supplierId, setSupplierId] = useState("");
  const [warehouseId, setWarehouseId] = useState("");
  const [currency, setCurrency] = useState<CurrencyCode>("USD");
  const [maxValue, setMaxValue] = useState("");
  const [notes, setNotes] = useState("");
  const [expectedDate, setExpectedDate] = useState("");
  const [itemQuery, setItemQuery] = useState("");
  const [hits, setHits] = useState<StockItemOption[]>([]);
  const [draftLines, setDraftLines] = useState<DraftLine[]>([]);
  const [qty, setQty] = useState("1");
  const [unitPrice, setUnitPrice] = useState("0");

  const [releaseQtys, setReleaseQtys] = useState<Record<string, string>>({});
  const [rejectReason, setRejectReason] = useState("");

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
    const [blankets, suppliers, warehouses] = await Promise.all([
      listBlanketPurchaseOrders(client),
      loadSuppliers(client),
      loadWarehouses(client),
    ]);
    if (!blankets.ok) {
      setBoot({ kind: "error", message: blankets.error });
      return;
    }
    if (!suppliers.ok) {
      setBoot({ kind: "error", message: suppliers.error });
      return;
    }
    if (!warehouses.ok) {
      setBoot({ kind: "error", message: warehouses.error });
      return;
    }
    setBoot({
      kind: "ready",
      blankets: blankets.data,
      suppliers: suppliers.data,
      warehouses: warehouses.data,
    });
    setSupplierId((prev) => prev || suppliers.data[0]?.id || "");
    setWarehouseId((prev) => prev || warehouses.data[0]?.id || "");
  }, []);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  useEffect(() => {
    if (boot.kind !== "ready") return;
    const q = itemQuery.trim();
    if (q.length < 2) {
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
          setHits([]);
          return;
        }
        setHits(res.data);
      })();
    }, 250);
    return () => window.clearTimeout(t);
  }, [boot.kind, itemQuery]);

  function addDraftLine(item: StockItemOption) {
    if (!item.base_uom_id) {
      setMessage("Selected item has no base UOM.");
      return;
    }
    setDraftLines((prev) => [
      ...prev,
      {
        key: `${item.id}-${Date.now()}`,
        item,
        qty,
        unitPrice,
      },
    ]);
    setItemQuery("");
    setHits([]);
    setQty("1");
    setUnitPrice("0");
  }

  async function onCreate(e: FormEvent) {
    e.preventDefault();
    const client = createWebClient();
    if (!client || boot.kind !== "ready") return;
    const max = Number(maxValue);
    if (!Number.isFinite(max) || max < 0) {
      setMessage("Blanket max value required.");
      return;
    }
    if (!draftLines.length) {
      setMessage("Add at least one line.");
      return;
    }
    setBusy(true);
    setMessage(null);
    const lines = draftLines.map((d) => ({
      stock_item_id: d.item.id,
      uom_id: d.item.base_uom_id!,
      qty: Number(d.qty),
      unit_price: Number(d.unitPrice),
      currency,
    }));
    if (lines.some((l) => !Number.isFinite(l.qty) || l.qty <= 0)) {
      setMessage("Line qty must be positive.");
      setBusy(false);
      return;
    }
    const res = await createBlanketPurchaseOrder(client, {
      supplierId,
      warehouseId,
      currency,
      exchangeRate: currency === "ZIG" ? zigExchangeRate() : 1,
      blanketMaxValue: max,
      lines,
      notes: notes.trim() || undefined,
      expectedDate: expectedDate.trim() || undefined,
    });
    setBusy(false);
    if (!res.ok) {
      setMessage(res.error);
      return;
    }
    setDraftLines([]);
    setMaxValue("");
    setNotes("");
    setExpectedDate("");
    setMessage(`Blanket PO created · ${res.data.slice(0, 8)}… (submit to enable releases)`);
    await refresh();
  }

  async function onSubmitBlanket(id: string) {
    const client = createWebClient();
    if (!client) return;
    setBusy(true);
    setMessage(null);
    const res = await submitPurchaseOrder(client, id);
    setBusy(false);
    if (!res.ok) {
      setMessage(res.error);
      return;
    }
    setMessage("Blanket PO submitted — call-off releases unlocked.");
    await refresh();
  }

  async function onRelease(blanket: BlanketSummary) {
    const client = createWebClient();
    if (!client) return;
    const lines = blanket.lines
      .map((line) => {
        const raw = releaseQtys[line.id]?.trim();
        if (!raw) return null;
        const n = Number(raw);
        if (!Number.isFinite(n) || n <= 0) return null;
        return { blanket_line_id: line.id, qty: n };
      })
      .filter((x): x is { blanket_line_id: string; qty: number } => !!x);
    if (!lines.length) {
      setMessage("Enter release qty on at least one line.");
      return;
    }
    setBusy(true);
    setMessage(null);
    const res = await createBlanketRelease(client, {
      blanketPurchaseOrderId: blanket.po.id,
      lines,
    });
    setBusy(false);
    if (!res.ok) {
      setMessage(res.error);
      return;
    }
    setReleaseQtys({});
    setMessage(`Release PO created · ${res.data.slice(0, 8)}…`);
    await refresh();
  }

  if (boot.kind === "loading") {
    return <p className={styles.muted}>Loading blanket POs…</p>;
  }
  if (boot.kind === "auth") {
    return (
      <p className={styles.lede}>
        <Link href="/login">Sign in</Link> with procurement staff (admin,
        warehouse, or finance) to manage blanket contracts.
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
        <legend className={styles.legend}>Create blanket PO</legend>
        <form onSubmit={(e) => void onCreate(e)}>
          <div className={styles.formGrid}>
            <label className={styles.field}>
              Supplier
              <select
                value={supplierId}
                onChange={(e) => setSupplierId(e.target.value)}
                disabled={busy}
              >
                {boot.suppliers.map((s) => (
                  <option key={s.id} value={s.id}>
                    {s.code} — {s.name}
                  </option>
                ))}
              </select>
            </label>
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
                  </option>
                ))}
              </select>
            </label>
            <label className={styles.field}>
              Currency
              <select
                value={currency}
                onChange={(e) => setCurrency(e.target.value as CurrencyCode)}
                disabled={busy}
              >
                <option value="USD">USD</option>
                <option value="ZIG">ZIG</option>
              </select>
            </label>
            <label className={styles.field}>
              Max value ({currency})
              <input
                value={maxValue}
                onChange={(e) => setMaxValue(e.target.value)}
                disabled={busy}
                inputMode="decimal"
                required
              />
            </label>
            <label className={styles.field} style={{ gridColumn: "1 / -1" }}>
              Notes
              <input
                value={notes}
                onChange={(e) => setNotes(e.target.value)}
                disabled={busy}
              />
            </label>
            <label className={styles.field}>
              Expected / expiry date
              <input
                type="date"
                value={expectedDate}
                onChange={(e) => setExpectedDate(e.target.value)}
                disabled={busy}
              />
            </label>
          </div>

          <div className={styles.formGrid} style={{ marginTop: "0.75rem" }}>
            <label className={styles.field}>
              OEM / stock
              <input
                value={itemQuery}
                onChange={(e) => setItemQuery(e.target.value)}
                disabled={busy}
                placeholder="Type OEM…"
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
              Unit price ({currency})
              <input
                value={unitPrice}
                onChange={(e) => setUnitPrice(e.target.value)}
                disabled={busy}
                inputMode="decimal"
              />
            </label>
          </div>
          {hits.length > 0 ? (
            <ul className={styles.list}>
              {hits.map((item) => (
                <li key={item.id}>
                  <button
                    type="button"
                    className={styles.btnGhost}
                    onClick={() => addDraftLine(item)}
                  >
                    {item.oem_part_number}
                    {item.description ? ` — ${item.description}` : ""}
                  </button>
                </li>
              ))}
            </ul>
          ) : null}
          {draftLines.length ? (
            <ul className={styles.list}>
              {draftLines.map((d) => (
                <li key={d.key}>
                  {d.item.oem_part_number} · qty {d.qty} @ {d.unitPrice}{" "}
                  {currency}{" "}
                  <button
                    type="button"
                    className={styles.btnGhost}
                    onClick={() =>
                      setDraftLines((prev) => prev.filter((x) => x.key !== d.key))
                    }
                  >
                    Remove
                  </button>
                </li>
              ))}
            </ul>
          ) : (
            <p className={styles.muted}>No draft lines yet — search OEM above.</p>
          )}
          <div className={styles.formActions}>
            <button
              type="submit"
              className={styles.btn}
              disabled={busy || !supplierId || !warehouseId}
            >
              Create blanket
            </button>
          </div>
        </form>
      </fieldset>

      <fieldset className={styles.fieldset}>
        <legend className={styles.legend}>Open blankets · call-off</legend>
        {boot.blankets.length === 0 ? (
          <p className={styles.muted}>
            No blanket purchase orders yet. Create one above.
          </p>
        ) : (
          <ul className={styles.list}>
            {boot.blankets.map((b) => {
              const alerts = blanketAlerts(b);
              return (
              <li key={b.po.id}>
                <strong>
                  {b.po.document_number ?? b.po.id.slice(0, 8)}
                </strong>{" "}
                · {b.po.status}
                <br />
                <span className={styles.muted}>
                  {b.supplier
                    ? `${b.supplier.code} — ${b.supplier.name}`
                    : "Supplier"}
                  {" · "}
                  {b.warehouse
                    ? `${b.warehouse.code} — ${b.warehouse.name}`
                    : "Warehouse"}
                  {" · remaining "}
                  {b.remainingValue.toFixed(2)} {b.po.currency}
                  {" · qty left "}
                  {b.remainingQty}
                  {" / max "}
                  {Number(b.po.blanket_max_value ?? 0).toFixed(2)}
                  {b.po.expected_date
                    ? ` · expected ${b.po.expected_date.slice(0, 10)}`
                    : ""}
                </span>
                {alerts.length > 0 ? (
                  <ul className={styles.list}>
                    {alerts.map((a) => (
                      <li key={`${b.po.id}-${a.kind}-${a.message}`}>
                        <span role="status">
                          {a.severity === "critical" ? "Alert" : "Notice"}:{" "}
                          {a.message}
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
                        {" · ordered "}
                        {line.qty_ordered}
                        {" · released "}
                        {line.qty_released ?? 0}
                        {" · left "}
                        {rem}
                        {b.po.status === "submitted" && rem > 0 ? (
                          <>
                            {" · release "}
                            <input
                              style={{ width: "5rem" }}
                              value={releaseQtys[line.id] ?? ""}
                              onChange={(e) =>
                                setReleaseQtys((prev) => ({
                                  ...prev,
                                  [line.id]: e.target.value,
                                }))
                              }
                              disabled={busy}
                              inputMode="decimal"
                              placeholder="qty"
                            />
                          </>
                        ) : null}
                      </li>
                    );
                  })}
                </ul>
                <div className={styles.formActions}>
                  {b.po.status === "draft" ? (
                    <button
                      type="button"
                      className={styles.btn}
                      disabled={busy}
                      onClick={() => void onSubmitBlanket(b.po.id)}
                    >
                      Submit blanket
                    </button>
                  ) : null}
                  {b.po.status === "submitted" ? (
                    <button
                      type="button"
                      className={styles.btn}
                      disabled={busy}
                      onClick={() => void onRelease(b)}
                    >
                      Create call-off release
                    </button>
                  ) : null}
                </div>
              </li>
            );
            })}
          </ul>
        )}
      </fieldset>

      {message ? (
        <p className={styles.formStatus} role="status">
          {message}
        </p>
      ) : null}
    </div>
  );
}
