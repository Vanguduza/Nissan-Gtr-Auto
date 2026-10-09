"use client";

import Link from "next/link";
import { FormEvent, useCallback, useEffect, useState } from "react";
import styles from "@/components/account.module.css";
import {
  addConsignmentLine,
  cancelConsignmentEntry,
  createConsignmentDraft,
  listConsignmentEntries,
  listConsignmentLines,
  listWarehouses,
  loadSuppliers,
  requireSession,
  searchCustomers,
  searchStockItems,
  submitConsignmentEntry,
  type ConsignmentEntryRow,
  type ConsignmentKind,
  type ConsignmentLineRow,
  type ConsignmentPurpose,
  type CurrencyCode,
  type CustomerOption,
  type StockItemOption,
  type SupplierOption,
  type WarehouseOption,
} from "@/lib/staff-consignment";
import { zigExchangeRate } from "@/lib/customer-storefront";
import { createWebClient } from "@/lib/supabase";

type Boot =
  | { kind: "loading" }
  | { kind: "auth" }
  | { kind: "error"; message: string }
  | {
      kind: "ready";
      entries: ConsignmentEntryRow[];
      warehouses: WarehouseOption[];
      suppliers: SupplierOption[];
    };

const PURPOSES: ConsignmentPurpose[] = [
  "receive",
  "return_to_supplier",
  "place_at_customer",
  "return_from_customer",
  "take_ownership",
  "recognize_sale",
];

export function StaffConsignmentPanel() {
  const [boot, setBoot] = useState<Boot>({ kind: "loading" });
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<string | null>(null);

  const [kind, setKind] = useState<ConsignmentKind>("supplier_owned");
  const [purpose, setPurpose] = useState<ConsignmentPurpose>("receive");
  const [warehouseId, setWarehouseId] = useState("");
  const [supplierId, setSupplierId] = useState("");
  const [customerQuery, setCustomerQuery] = useState("");
  const [customerHits, setCustomerHits] = useState<CustomerOption[]>([]);
  const [customerId, setCustomerId] = useState("");
  const [customerLabel, setCustomerLabel] = useState("");
  const [currency, setCurrency] = useState<CurrencyCode>("USD");
  const [notes, setNotes] = useState("");

  const [selectedId, setSelectedId] = useState("");
  const [lines, setLines] = useState<ConsignmentLineRow[]>([]);
  const [itemQuery, setItemQuery] = useState("");
  const [hits, setHits] = useState<StockItemOption[]>([]);
  const [selectedItem, setSelectedItem] = useState<StockItemOption | null>(null);
  const [qty, setQty] = useState("1");
  const [unitCost, setUnitCost] = useState("0");
  const [unitPrice, setUnitPrice] = useState("0");

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
    const [entries, warehouses, suppliers] = await Promise.all([
      listConsignmentEntries(client),
      listWarehouses(client),
      loadSuppliers(client),
    ]);
    if (!entries.ok) {
      setBoot({ kind: "error", message: entries.error });
      return;
    }
    if (!warehouses.ok) {
      setBoot({ kind: "error", message: warehouses.error });
      return;
    }
    if (!suppliers.ok) {
      setBoot({ kind: "error", message: suppliers.error });
      return;
    }
    setBoot({
      kind: "ready",
      entries: entries.data,
      warehouses: warehouses.data,
      suppliers: suppliers.data,
    });
    setWarehouseId((prev) => prev || warehouses.data[0]?.id || "");
    setSupplierId((prev) => prev || suppliers.data[0]?.id || "");
  }, []);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  useEffect(() => {
    if (!selectedId) {
      setLines([]);
      return;
    }
    void (async () => {
      const client = createWebClient();
      if (!client) return;
      const res = await listConsignmentLines(client, selectedId);
      if (!res.ok) {
        setMessage(res.error);
        return;
      }
      setLines(res.data);
    })();
  }, [selectedId]);

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

  useEffect(() => {
    if (boot.kind !== "ready" || kind !== "customer_held") return;
    const q = customerQuery.trim();
    if (q.length < 2) {
      setCustomerHits([]);
      return;
    }
    const t = window.setTimeout(() => {
      void (async () => {
        const client = createWebClient();
        if (!client) return;
        const res = await searchCustomers(client, q);
        if (!res.ok) {
          setMessage(res.error);
          setCustomerHits([]);
          return;
        }
        setCustomerHits(res.data);
      })();
    }, 250);
    return () => window.clearTimeout(t);
  }, [boot.kind, customerQuery, kind]);

  async function onCreateDraft(e: FormEvent) {
    e.preventDefault();
    const client = createWebClient();
    if (!client) return;
    setBusy(true);
    setMessage(null);
    const exchangeRate = currency === "ZIG" ? zigExchangeRate() : 1;
    if (exchangeRate == null) {
      setMessage("ZiG exchange rate is not configured. Finance must publish a verified rate before ZiG transactions are enabled.");
      setBusy(false);
      return;
    }
    const res = await createConsignmentDraft(client, {
      kind,
      purpose,
      warehouseId,
      supplierId: kind === "supplier_owned" ? supplierId : undefined,
      customerId: kind === "customer_held" ? customerId : undefined,
      currency,
      exchangeRate,
      notes: notes.trim() || undefined,
    });
    setBusy(false);
    if (!res.ok) {
      setMessage(res.error);
      return;
    }
    setSelectedId(res.data);
    setMessage(`Draft ${res.data.slice(0, 8)}… created`);
    await refresh();
  }

  async function onAddLine(e: FormEvent) {
    e.preventDefault();
    const client = createWebClient();
    if (!client || !selectedItem || !selectedId) return;
    if (!selectedItem.base_uom_id) {
      setMessage("Selected item has no base UOM.");
      return;
    }
    const n = Number(qty);
    if (!Number.isFinite(n) || n <= 0) {
      setMessage("Qty must be positive.");
      return;
    }
    setBusy(true);
    setMessage(null);
    const res = await addConsignmentLine(client, {
      entryId: selectedId,
      stockItemId: selectedItem.id,
      uomId: selectedItem.base_uom_id,
      qty: n,
      unitCost: Number(unitCost) || 0,
      unitPrice: Number(unitPrice) || 0,
      currency,
    });
    setBusy(false);
    if (!res.ok) {
      setMessage(res.error);
      return;
    }
    setMessage(`Line added · ${selectedItem.oem_part_number}`);
    setSelectedItem(null);
    setItemQuery("");
    setHits([]);
    const linesRes = await listConsignmentLines(client, selectedId);
    if (linesRes.ok) setLines(linesRes.data);
  }

  async function onSubmit() {
    const client = createWebClient();
    if (!client || !selectedId) return;
    setBusy(true);
    setMessage(null);
    const res = await submitConsignmentEntry(client, selectedId);
    setBusy(false);
    if (!res.ok) {
      setMessage(res.error);
      return;
    }
    setMessage("Consignment entry submitted.");
    await refresh();
  }

  async function onCancel() {
    const client = createWebClient();
    if (!client || !selectedId) return;
    setBusy(true);
    setMessage(null);
    const res = await cancelConsignmentEntry(client, selectedId);
    setBusy(false);
    if (!res.ok) {
      setMessage(res.error);
      return;
    }
    setMessage("Consignment entry cancelled.");
    await refresh();
  }

  if (boot.kind === "loading") {
    return <p className={styles.muted}>Loading consignment…</p>;
  }
  if (boot.kind === "auth") {
    return (
      <p className={styles.lede}>
        <Link href="/login">Sign in</Link> with warehouse/admin staff for
        consignment entries.
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

  const selected = boot.entries.find((e) => e.id === selectedId);

  return (
    <div className={styles.form}>
      <fieldset className={styles.fieldset}>
        <legend className={styles.legend}>New draft</legend>
        <form onSubmit={(e) => void onCreateDraft(e)}>
          <div className={styles.formGrid}>
            <label className={styles.field}>
              Kind
              <select
                value={kind}
                onChange={(e) => setKind(e.target.value as ConsignmentKind)}
                disabled={busy}
              >
                <option value="supplier_owned">supplier_owned</option>
                <option value="customer_held">customer_held</option>
              </select>
            </label>
            <label className={styles.field}>
              Purpose
              <select
                value={purpose}
                onChange={(e) => setPurpose(e.target.value as ConsignmentPurpose)}
                disabled={busy}
              >
                {PURPOSES.map((p) => (
                  <option key={p} value={p}>
                    {p}
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
            {kind === "supplier_owned" ? (
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
            ) : (
              <label className={styles.field}>
                Customer
                <input
                  value={customerQuery}
                  onChange={(e) => {
                    setCustomerQuery(e.target.value);
                    setCustomerId("");
                    setCustomerLabel("");
                  }}
                  disabled={busy}
                  placeholder="Search display name…"
                />
              </label>
            )}
            <label className={styles.field} style={{ gridColumn: "1 / -1" }}>
              Notes
              <input
                value={notes}
                onChange={(e) => setNotes(e.target.value)}
                disabled={busy}
              />
            </label>
          </div>
          {kind === "customer_held" && customerHits.length > 0 && !customerId ? (
            <ul className={styles.list}>
              {customerHits.map((c) => (
                <li key={c.id}>
                  <button
                    type="button"
                    className={styles.btnGhost}
                    onClick={() => {
                      setCustomerId(c.id);
                      setCustomerLabel(c.display_name);
                      setCustomerQuery(c.display_name);
                      setCustomerHits([]);
                    }}
                  >
                    {c.display_name}
                  </button>
                </li>
              ))}
            </ul>
          ) : null}
          {customerId ? (
            <p className={styles.muted}>Customer: {customerLabel || customerId.slice(0, 8)}</p>
          ) : null}
          <div className={styles.formActions}>
            <button
              type="submit"
              className={styles.btn}
              disabled={
                busy ||
                !warehouseId ||
                (kind === "supplier_owned" ? !supplierId : !customerId)
              }
            >
              Create draft
            </button>
          </div>
        </form>
      </fieldset>

      <fieldset className={styles.fieldset}>
        <legend className={styles.legend}>Entries</legend>
        {boot.entries.length === 0 ? (
          <p className={styles.muted}>No consignment entries yet.</p>
        ) : (
          <ul className={styles.list}>
            {boot.entries.map((e) => {
              const party =
                e.kind === "supplier_owned"
                  ? e.suppliers
                    ? `${e.suppliers.code} — ${e.suppliers.name}`
                    : "Supplier"
                  : e.customers?.display_name ?? "Customer";
              const wh = e.warehouses
                ? `${e.warehouses.code} — ${e.warehouses.name}`
                : "Warehouse";
              return (
                <li key={e.id}>
                  <button
                    type="button"
                    className={styles.btnGhost}
                    onClick={() => setSelectedId(e.id)}
                  >
                    <strong>
                      {e.document_number ?? e.id.slice(0, 8)}
                    </strong>
                  </button>
                  {" · "}
                  {e.status} · {e.kind} · {e.purpose}
                  <br />
                  <span className={styles.muted}>
                    {party} · {wh} · {e.currency}
                  </span>
                </li>
              );
            })}
          </ul>
        )}
      </fieldset>

      {selected ? (
        <fieldset className={styles.fieldset}>
          <legend className={styles.legend}>
            Selected · {selected.document_number ?? selected.id.slice(0, 8)}
          </legend>
          <p className={styles.muted}>
            {selected.status} · {selected.kind} · {selected.purpose} ·{" "}
            {selected.currency}
          </p>
          {selected.status === "draft" ? (
            <form onSubmit={(e) => void onAddLine(e)}>
              <div className={styles.formGrid}>
                <label className={styles.field}>
                  OEM
                  <input
                    value={itemQuery}
                    onChange={(e) => {
                      setItemQuery(e.target.value);
                      setSelectedItem(null);
                    }}
                    disabled={busy}
                    placeholder="Type OEM…"
                  />
                </label>
                <label className={styles.field}>
                  Qty
                  <input
                    value={qty}
                    onChange={(e) => setQty(e.target.value)}
                    disabled={busy}
                    inputMode="decimal"
                  />
                </label>
                <label className={styles.field}>
                  Unit cost ({selected.currency})
                  <input
                    value={unitCost}
                    onChange={(e) => setUnitCost(e.target.value)}
                    disabled={busy}
                    inputMode="decimal"
                  />
                </label>
                <label className={styles.field}>
                  Unit price ({selected.currency})
                  <input
                    value={unitPrice}
                    onChange={(e) => setUnitPrice(e.target.value)}
                    disabled={busy}
                    inputMode="decimal"
                  />
                </label>
              </div>
              {hits.length > 0 && !selectedItem ? (
                <ul className={styles.list}>
                  {hits.map((item) => (
                    <li key={item.id}>
                      <button
                        type="button"
                        className={styles.btnGhost}
                        onClick={() => {
                          setSelectedItem(item);
                          setItemQuery(item.oem_part_number);
                          setHits([]);
                        }}
                      >
                        {item.oem_part_number}
                        {item.description ? ` — ${item.description}` : ""}
                      </button>
                    </li>
                  ))}
                </ul>
              ) : null}
              <div className={styles.formActions}>
                <button
                  type="submit"
                  className={styles.btn}
                  disabled={busy || !selectedItem}
                >
                  Add line
                </button>
                <button
                  type="button"
                  className={styles.btn}
                  disabled={busy || lines.length === 0}
                  onClick={() => void onSubmit()}
                >
                  Submit
                </button>
              </div>
            </form>
          ) : (
            <div className={styles.formActions}>
              {selected.status !== "cancelled" ? (
                <button
                  type="button"
                  className={styles.btnGhost}
                  disabled={busy}
                  onClick={() => void onCancel()}
                >
                  Cancel entry
                </button>
              ) : null}
            </div>
          )}
          {lines.length === 0 ? (
            <p className={styles.muted}>No lines on this entry.</p>
          ) : (
            <ul className={styles.list}>
              {lines.map((line) => (
                <li key={line.id}>
                  {line.stock_items?.oem_part_number ?? line.stock_item_id.slice(0, 8)}
                  {line.stock_items?.description
                    ? ` — ${line.stock_items.description}`
                    : ""}
                  {" · qty "}
                  {line.qty}
                  {" · cost "}
                  {Number(line.unit_cost).toFixed(2)} {line.currency}
                  {" · price "}
                  {Number(line.unit_price).toFixed(2)} {line.currency}
                </li>
              ))}
            </ul>
          )}
        </fieldset>
      ) : null}

      {message ? (
        <p className={styles.formStatus} role="status">
          {message}
        </p>
      ) : null}
    </div>
  );
}
