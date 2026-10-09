"use client";

import Link from "next/link";
import { useCallback, useEffect, useMemo, useState } from "react";
import styles from "@/components/account.module.css";
import { money } from "@/lib/staff-dashboard";
import { loadSuppliers } from "@/lib/rfq-portal";
import {
  LEAD_SOURCE_LABEL,
  URGENCY_LABEL,
  createPurchaseOrderDraft,
  getRestockPlan,
  markdownStockItem,
  setBranchReorderPoint,
  type SlowStock,
  requireSession,
  type RestockPlan,
  type RestockSettings,
  type RestockSuggestion,
} from "@/lib/staff-restock";
import { createStockTransfer, listWarehouses, type WarehouseOption } from "@/lib/staff-warehouse";
import { createWebClient } from "@/lib/supabase";
import { downloadCsv, toCsv } from "@/lib/csv";

type Boot =
  | { kind: "loading" }
  | { kind: "auth" }
  | { kind: "error"; message: string }
  | { kind: "ready"; plan: RestockPlan };

const key = (s: RestockSuggestion) => `${s.stockItemId}:${s.warehouseId}`;
const fmt = (n: number) => (Number.isInteger(n) ? String(n) : Math.abs(n) < 1 ? n.toFixed(2) : n.toFixed(1));

/**
 * What to move between branches and what to buy. Transfers come first (spare stock at another
 * branch); the rest goes on draft purchase orders per supplier, which procurement then approves.
 */
export function StaffRestockPanel() {
  const [boot, setBoot] = useState<Boot>({ kind: "loading" });
  const [warehouseId, setWarehouseId] = useState("");
  const [warehouses, setWarehouses] = useState<WarehouseOption[]>([]);
  const [suppliers, setSuppliers] = useState<{ id: string; code: string; name: string }[]>([]);
  const [settings, setSettings] = useState<RestockSettings>({ salesDays: 28, leadDays: 14, safetyDays: 7, coverDays: 28 });
  const [picked, setPicked] = useState<Set<string>>(new Set());
  const [supplierFor, setSupplierFor] = useState<Record<string, string>>({});
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  /** The row whose branch reorder point is being edited, and the typed values. */
  const [ropEdit, setRopEdit] = useState<{ key: string; point: string; qty: string } | null>(null);

  const refresh = useCallback(async () => {
    const client = createWebClient();
    if (!client) {
      setBoot({ kind: "error", message: "Supabase is not configured on this environment." });
      return;
    }
    const session = await requireSession(client);
    if (!session.ok) {
      setBoot({ kind: "auth" });
      return;
    }
    const res = await getRestockPlan(client, warehouseId || null, settings);
    if (!res.ok) {
      setBoot({ kind: "error", message: res.error });
      return;
    }
    setBoot({ kind: "ready", plan: res.data });
    // Urgent ones ticked by default.
    setPicked(new Set(res.data.suggestions.filter((s) => s.urgency !== "low").map(key)));
  }, [warehouseId, settings]);

  useEffect(() => {
    const client = createWebClient();
    if (!client) return;
    void listWarehouses(client).then((r) => r.ok && setWarehouses(r.data));
    void loadSuppliers(client).then((r) => r.ok && setSuppliers(r.data as { id: string; code: string; name: string }[]));
  }, []);
  useEffect(() => {
    void refresh();
  }, [refresh]);

  const chosen = useMemo(
    () => (boot.kind === "ready" ? boot.plan.suggestions.filter((s) => picked.has(key(s))) : []),
    [boot, picked],
  );
  const transferGroups = useMemo(() => {
    const g = new Map<string, { from: string; fromName: string; to: string; toName: string; rows: RestockSuggestion[] }>();
    for (const s of chosen) {
      if (s.transferQty <= 0 || !s.transferFrom) continue;
      const k = `${s.transferFrom.warehouseId}>${s.warehouseId}`;
      const e = g.get(k) ?? { from: s.transferFrom.warehouseId, fromName: s.transferFrom.warehouse, to: s.warehouseId, toName: s.warehouse, rows: [] };
      e.rows.push(s);
      g.set(k, e);
    }
    return [...g.values()];
  }, [chosen]);
  const buyRows = chosen.filter((s) => s.buyQty > 0);
  const supplierOf = (s: RestockSuggestion) => supplierFor[key(s)] ?? s.supplierId ?? "";

  async function createTransfers() {
    const client = createWebClient();
    if (!client) return;
    setBusy(true);
    setMessage(null);
    const done: string[] = [];
    for (const g of transferGroups) {
      const r = await createStockTransfer(client, {
        fromWarehouseId: g.from,
        toWarehouseId: g.to,
        notes: "Restock suggestion",
        lines: g.rows.map((s) => ({ stock_item_id: s.stockItemId, uom_id: s.uomId, qty: s.transferQty })),
      });
      if (!r.ok) {
        setBusy(false);
        setMessage(`${g.fromName} → ${g.toName}: ${r.error}`);
        return;
      }
      done.push(`${g.fromName} → ${g.toName} (${g.rows.length} parts)`);
    }
    setBusy(false);
    setMessage(`Transfers created, waiting for the receiving branch to approve: ${done.join("; ")}.`);
    await refresh();
  }

  async function saveReorderPoint(s: RestockSuggestion, clear: boolean) {
    const client = createWebClient();
    if (!client || !ropEdit) return;
    const point = clear ? null : Number(ropEdit.point.replace(",", "."));
    const qty = clear || !ropEdit.qty.trim() ? null : Number(ropEdit.qty.replace(",", "."));
    if (!clear && (point == null || !Number.isFinite(point) || point < 0)) return setMessage("Enter a reorder point of 0 or more.");
    if (qty != null && (!Number.isFinite(qty) || qty <= 0)) return setMessage("Reorder quantity must be more than 0, or left empty.");
    setBusy(true);
    const r = await setBranchReorderPoint(client, s.stockItemId, s.warehouseId, point, qty);
    setBusy(false);
    if (!r.ok) return setMessage(r.error);
    setRopEdit(null);
    setMessage(clear ? `${s.oemPartNumber} at ${s.warehouse} is back to the part's rule.` : `${s.oemPartNumber} at ${s.warehouse} now reorders at ${point}.`);
    await refresh();
  }

  async function moveSlow(x: SlowStock) {
    const client = createWebClient();
    if (!client || !x.moveTo || !x.moveQty) return;
    setBusy(true);
    setMessage(null);
    const r = await createStockTransfer(client, {
      fromWarehouseId: x.warehouseId,
      toWarehouseId: x.moveTo.warehouseId,
      notes: "Slow stock: moved to the branch that sells it",
      lines: [{ stock_item_id: x.stockItemId, uom_id: x.uomId, qty: x.moveQty }],
    });
    setBusy(false);
    setMessage(r.ok ? `Transfer of ${x.moveQty} ${x.oemPartNumber} from ${x.warehouse} to ${x.moveTo.warehouse} created; ${x.moveTo.warehouse} approves it on arrival.` : r.error);
    if (r.ok) await refresh();
  }

  async function markDown(x: SlowStock) {
    const client = createWebClient();
    if (!client || !x.markdownPct) return;
    const reason = x.lastSoldAt ? `Not sold since ${new Date(x.lastSoldAt).toLocaleDateString()}` : "Never sold";
    if (!window.confirm(`Lower every price of ${x.oemPartNumber} by ${x.markdownPct}%? (${reason})`)) return;
    setBusy(true);
    setMessage(null);
    const r = await markdownStockItem(client, x.stockItemId, x.markdownPct, reason);
    setBusy(false);
    setMessage(r.ok ? `${x.oemPartNumber} marked down ${x.markdownPct}%: ${r.data.map((p) => `${p.priceList} ${p.old.toFixed(2)} → ${p.new.toFixed(2)}`).join(", ")}.` : /manager/i.test(r.error) ? "Only a manager, finance or admin can mark prices down." : r.error);
    if (r.ok) await refresh();
  }

  async function createOrders() {
    const client = createWebClient();
    if (!client) return;
    const missing = buyRows.filter((s) => !supplierOf(s));
    if (missing.length) {
      setMessage(`Choose a supplier for ${missing.map((s) => s.oemPartNumber).join(", ")}.`);
      return;
    }
    const groups = new Map<string, RestockSuggestion[]>();
    for (const s of buyRows) {
      const k = `${supplierOf(s)}|${s.warehouseId}|${s.costCurrency ?? "USD"}`;
      groups.set(k, [...(groups.get(k) ?? []), s]);
    }
    setBusy(true);
    setMessage(null);
    let count = 0;
    for (const [k, list] of groups) {
      const [supplierId, wh, currency] = k.split("|");
      const lead = Math.max(...list.map((s) => s.leadDays));
      const expected = new Date(Date.now() + lead * 86_400_000).toISOString().slice(0, 10);
      const r = await createPurchaseOrderDraft(client, {
        supplierId,
        warehouseId: wh,
        currency,
        expectedDate: expected,
        notes: "Draft from restock suggestions",
        lines: list.map((s) => ({ stockItemId: s.stockItemId, uomId: s.uomId, qty: s.buyQty, unitPrice: s.unitCost ?? 0 })),
      });
      if (!r.ok) {
        setBusy(false);
        setMessage(r.error);
        return;
      }
      count += 1;
    }
    setBusy(false);
    setMessage(`${count} draft purchase order${count === 1 ? "" : "s"} created. Procurement reviews prices and approves them.`);
    await refresh();
  }

  const settingInput = (label: string, field: keyof RestockSettings, title: string) => (
    <label className={styles.muted} title={title}>
      {label}{" "}
      <input
        type="number"
        min={1}
        max={365}
        className={styles.input}
        style={{ width: 70 }}
        value={settings[field]}
        onChange={(e) => {
          const v = Number(e.target.value);
          if (v > 0) setSettings((s) => ({ ...s, [field]: v }));
        }}
      />
    </label>
  );

  const filters = (
    <div className={styles.formActions} role="group" aria-label="Branch and settings" style={{ flexWrap: "wrap", alignItems: "center" }}>
      <select className={styles.input} aria-label="Branch" value={warehouseId} onChange={(e) => setWarehouseId(e.target.value)} style={{ flex: "0 1 220px" }}>
        <option value="">All branches</option>
        {warehouses.map((w) => (
          <option key={w.id} value={w.id}>
            {w.name}
          </option>
        ))}
      </select>
      {settingInput("Sales over (days)", "salesDays", "How far back to measure how fast a part sells")}
      {settingInput("Supplier lead time", "leadDays", "Days from order to delivery when the supplier gives none")}
      {settingInput("Safety days", "safetyDays", "Extra days of stock to hold against surprises")}
      {settingInput("Order for (days)", "coverDays", "How many days of sales an order should cover")}
    </div>
  );

  if (boot.kind === "loading") return <>{filters}<p className={styles.muted}>Working out what to restock…</p></>;
  if (boot.kind === "auth") {
    return (
      <p className={styles.lede} role="status">
        <Link href="/login">Sign in</Link> as warehouse, finance, a manager or admin.
      </p>
    );
  }
  if (boot.kind === "error") {
    return (
      <>
        {filters}
        <p className={styles.lede} role="alert">
          {boot.message}
        </p>
      </>
    );
  }

  const { plan } = boot;
  const toggle = (k: string) =>
    setPicked((p) => {
      const n = new Set(p);
      if (n.has(k)) n.delete(k);
      else n.add(k);
      return n;
    });

  return (
    <div className={styles.form}>
      {filters}

      {plan.suggestions.length === 0 ? (
        <p className={styles.lede} role="status">
          Nothing needs restocking with these settings.
        </p>
      ) : (
        <>
          <div className={styles.tableWrap}>
            <table className={styles.table}>
              <thead>
                <tr>
                  <th aria-label="Choose" />
                  <th style={{ minWidth: 230 }}>Part · branch</th>
                  <th className={styles.num}>Free</th>
                  <th className={styles.num}>Sells / day</th>
                  <th className={styles.num}>Days left</th>
                  <th className={styles.num}>Move in</th>
                  <th className={styles.num}>Buy</th>
                </tr>
              </thead>
              <tbody>
                {plan.suggestions.map((s) => {
                  const k = key(s);
                  return (
                    <tr key={k}>
                      <td>
                        <input type="checkbox" aria-label={`Include ${s.oemPartNumber} at ${s.warehouse}`} checked={picked.has(k)} onChange={() => toggle(k)} />
                      </td>
                      <td>
                        <strong>{s.description ?? s.oemPartNumber}</strong>
                        {s.urgency !== "low" ? (
                          <span
                            className={styles.urgentTag}
                            style={{ marginLeft: 6, ...(s.urgency === "before_delivery" ? { background: "var(--gtr-steel)" } : {}) }}
                            title={s.urgency === "out" ? "None free, or customers waiting" : "Runs out before a new delivery could arrive"}
                          >
                            {URGENCY_LABEL[s.urgency]}
                          </span>
                        ) : null}
                        <br />
                        <span className={styles.muted}>
                          {s.oemPartNumber} · {s.warehouse} · reorder at {fmt(s.reorderPoint)}
                          {s.reorderPointSource === "from_sales" ? "*" : s.reorderPointSource === "branch" ? " (this branch's)" : ""}
                          {s.lost ? ` · ${fmt(s.lost)} asked for and not had` : ""}
                          {s.buyQty > 0 ? ` · lead ${s.leadDays} days (${LEAD_SOURCE_LABEL[s.leadSource]}${s.leadSamples ? `, ${s.leadSamples} orders` : ""})` : ""}
                          {s.onOrder || s.transferIn ? ` · on its way ${fmt(s.onOrder + s.transferIn)}` : ""}
                          {s.backordered ? ` · ${fmt(s.backordered)} back-ordered` : ""}
                          {s.inDraft ? ` · ${fmt(s.inDraft)} already in a draft order` : ""}
                        </span>{" "}
                        <button
                          type="button"
                          className={styles.linkButton}
                          aria-expanded={ropEdit?.key === k}
                          onClick={() =>
                            setRopEdit(ropEdit?.key === k ? null : { key: k, point: String(s.reorderPoint), qty: s.reorderQty != null ? String(s.reorderQty) : "" })
                          }
                        >
                          Reorder point here
                        </button>
                        {ropEdit?.key === k ? (
                          <span className={styles.formActions} style={{ marginTop: 4 }}>
                            <input
                              className={styles.input}
                              style={{ width: 90 }}
                              inputMode="decimal"
                              aria-label={`Reorder point for ${s.oemPartNumber} at ${s.warehouse}`}
                              value={ropEdit.point}
                              onChange={(e) => setRopEdit({ ...ropEdit, point: e.target.value })}
                            />
                            <input
                              className={styles.input}
                              style={{ width: 90 }}
                              inputMode="decimal"
                              placeholder="Order qty"
                              aria-label={`Order quantity for ${s.oemPartNumber} at ${s.warehouse}`}
                              value={ropEdit.qty}
                              onChange={(e) => setRopEdit({ ...ropEdit, qty: e.target.value })}
                            />
                            <button type="button" className={styles.btn} disabled={busy} onClick={() => void saveReorderPoint(s, false)}>
                              Save for {s.warehouse}
                            </button>
                            {s.reorderPointSource === "branch" ? (
                              <button type="button" className={styles.btnGhost} disabled={busy} onClick={() => void saveReorderPoint(s, true)}>
                                Clear
                              </button>
                            ) : null}
                          </span>
                        ) : null}
                      </td>
                      <td className={styles.num}>{fmt(s.available)}</td>
                      <td className={styles.num}>{s.daily ? fmt(s.daily) : "—"}</td>
                      <td className={styles.num}>{s.daysOfCover != null ? fmt(s.daysOfCover) : "—"}</td>
                      <td className={styles.num}>
                        {s.transferQty > 0 ? (
                          <>
                            {fmt(s.transferQty)}
                            <br />
                            <span className={styles.muted}>from {s.transferFrom?.warehouse}</span>
                          </>
                        ) : (
                          "—"
                        )}
                      </td>
                      <td className={styles.num}>
                        {s.buyQty > 0 ? fmt(s.buyQty) : "—"}
                        {s.buyQty > 0 && s.unitCost != null && s.costCurrency ? (
                          <>
                            <br />
                            <span className={styles.muted}>≈ {money(s.buyQty * s.unitCost, s.costCurrency)}</span>
                          </>
                        ) : null}
                        {s.buyQty > 0 ? (
                          <>
                            <br />
                            <select
                                className={styles.input}
                                style={{ width: "100%", maxWidth: 150, marginTop: 4 }}
                                aria-label={`Supplier for ${s.oemPartNumber}`}
                                value={supplierOf(s)}
                                onChange={(e) => setSupplierFor((m) => ({ ...m, [k]: e.target.value }))}
                              >
                                <option value="">{suppliers.length ? "Choose…" : "No suppliers yet"}</option>
                                {suppliers.map((x) => (
                                  <option key={x.id} value={x.id}>
                                    {x.name}
                                  </option>
                                ))}
                              </select>
                          </>
                        ) : null}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
          <p className={styles.muted}>
            * reorder point worked out from sales: daily sales × ({plan.settings.leadDays} days lead time + {plan.settings.safetyDays} safety days).
            Orders top up to the reorder point plus {plan.settings.coverDays} days of sales.
          </p>

          <div className={styles.formActions} style={{ flexWrap: "wrap", marginTop: "0.75rem" }} data-noprint>
            <button type="button" className={styles.btn} disabled={busy || transferGroups.length === 0} onClick={() => void createTransfers()}>
              Create {transferGroups.length || ""} transfer{transferGroups.length === 1 ? "" : "s"}
            </button>
            <button type="button" className={styles.btn} disabled={busy || buyRows.length === 0} onClick={() => void createOrders()}>
              Draft purchase orders ({buyRows.length} part{buyRows.length === 1 ? "" : "s"})
            </button>
            <button
              type="button"
              className={styles.btnGhost}
              onClick={() => {
                const day = new Date().toISOString().slice(0, 10);
                downloadCsv(
                  `restock-${day}.csv`,
                  toCsv(
                    ["Part", "Description", "Branch", "Urgency", "Free", "Sells per day", "Lost demand", "Days left", "Reorder point", "Reorder point from", "Move in", "Move from", "Buy", "Supplier", "Lead days", "Lead from", "Unit cost", "Currency"],
                    plan.suggestions.map((x) => [
                      x.oemPartNumber, x.description, x.warehouse, URGENCY_LABEL[x.urgency], x.available, x.daily, x.lost, x.daysOfCover, x.reorderPoint,
                      x.reorderPointSource, x.transferQty, x.transferFrom?.warehouse, x.buyQty, x.supplier, x.leadDays, x.leadSource, x.unitCost, x.costCurrency,
                    ]),
                  ),
                );
              }}
            >
              Download CSV
            </button>
            <button type="button" className={styles.btnGhost} onClick={() => window.print()}>
              Print
            </button>
            {suppliers.length === 0 ? (
              <span className={styles.muted}>
                Add suppliers in <Link href="/procurement">Procurement</Link> to draft orders.
              </span>
            ) : null}
          </div>
        </>
      )}

      {message ? (
        <p className={styles.lede} role="status">
          {message}
        </p>
      ) : null}

      {plan.slowStock.length > 0 ? (
        <>
          <h2 className={styles.sectionTitle}>Not selling (nothing sold in 90 days)</h2>
          <p data-noprint>
            <button
              type="button"
              className={styles.btnGhost}
              onClick={() =>
                downloadCsv(
                  `slow-stock-${new Date().toISOString().slice(0, 10)}.csv`,
                  toCsv(
                    ["Part", "Description", "Branch", "On hand", "Value", "Currency", "Last sold", "Move to", "Move qty", "Suggested markdown %"],
                    plan.slowStock.map((x) => [x.oemPartNumber, x.description, x.warehouse, x.onHand, x.value, x.currency, x.lastSoldAt, x.moveTo?.warehouse, x.moveQty, x.markdownPct]),
                  ),
                )
              }
            >
              Download slow stock CSV
            </button>
          </p>
          <table className={styles.table}>
            <thead>
              <tr>
                <th>Part · branch</th>
                <th className={styles.num}>On hand</th>
                <th className={styles.num}>Value</th>
                <th>What to do</th>
              </tr>
            </thead>
            <tbody>
              {plan.slowStock.map((x) => (
                <tr key={x.stockItemId + x.warehouse}>
                  <td>
                    {x.description ?? x.oemPartNumber}
                    <br />
                    <span className={styles.muted}>
                      {x.oemPartNumber} · {x.warehouse}
                      {x.lastSoldAt ? ` · last sold ${new Date(x.lastSoldAt).toLocaleDateString()}` : " · never sold here"}
                    </span>
                  </td>
                  <td className={styles.num}>{fmt(x.onHand)}</td>
                  <td className={styles.num}>{x.currency ? money(x.value, x.currency) : ""}</td>
                  <td>
                    {x.moveTo && x.moveQty ? (
                      <button type="button" className={styles.btnGhost} disabled={busy} onClick={() => void moveSlow(x)}>
                        Move {fmt(x.moveQty)} to {x.moveTo.warehouse}
                      </button>
                    ) : x.markdownPct ? (
                      <button type="button" className={styles.btnGhost} disabled={busy} onClick={() => void markDown(x)}>
                        Mark down {x.markdownPct}%
                      </button>
                    ) : null}
                    {x.moveTo ? (
                      <>
                        <br />
                        <span className={styles.muted}>
                          {x.moveTo.warehouse} sold {fmt(x.moveTo.sold)} in {plan.settings.salesDays} days
                        </span>
                      </>
                    ) : null}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </>
      ) : null}
    </div>
  );
}
