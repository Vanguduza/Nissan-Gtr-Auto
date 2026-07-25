"use client";

import Link from "next/link";
import { FormEvent, useCallback, useEffect, useState } from "react";
import styles from "@/components/account.module.css";
import {
  approveStockReconciliation,
  cancelStockReconciliation,
  createStockReconciliationDraft,
  listReconciliations,
  listWarehouses,
  loadReconciliationLines,
  requireSession,
  searchStockItems,
  submitStockReconciliation,
  upsertStockReconciliationLines,
  zigExchangeRate,
  type CurrencyCode,
  type ReconciliationLineRow,
  type ReconciliationOption,
  type ReconciliationScope,
  type StockItemOption,
  type WarehouseOption,
} from "@/lib/staff-warehouse";
import { createWebClient } from "@/lib/supabase";

type Boot =
  | { kind: "loading" }
  | { kind: "auth" }
  | { kind: "error"; message: string }
  | {
      kind: "ready";
      warehouses: WarehouseOption[];
      recons: ReconciliationOption[];
    };

export function StaffWarehouseCycleCountPanel() {
  const [boot, setBoot] = useState<Boot>({ kind: "loading" });
  const [warehouseId, setWarehouseId] = useState("");
  const [scope, setScope] = useState<ReconciliationScope>("full");
  const [currency, setCurrency] = useState<CurrencyCode>("USD");
  const [notes, setNotes] = useState("");
  const [itemQuery, setItemQuery] = useState("");
  const [hits, setHits] = useState<StockItemOption[]>([]);
  const [partialItems, setPartialItems] = useState<StockItemOption[]>([]);
  const [reconId, setReconId] = useState("");
  const [lines, setLines] = useState<ReconciliationLineRow[]>([]);
  const [countDraft, setCountDraft] = useState<Record<string, string>>({});
  const [cancelNotes, setCancelNotes] = useState("");
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
    const [wh, recons] = await Promise.all([
      listWarehouses(client, { includeQuarantine: true }),
      listReconciliations(client),
    ]);
    if (!wh.ok) {
      setBoot({ kind: "error", message: wh.error });
      return;
    }
    if (!recons.ok) {
      setBoot({ kind: "error", message: recons.error });
      return;
    }
    setBoot({ kind: "ready", warehouses: wh.data, recons: recons.data });
    setWarehouseId((prev) => prev || wh.data[0]?.id || "");
    setReconId((prev) => prev || recons.data[0]?.id || "");
  }, []);

  const loadLines = useCallback(async (id: string) => {
    const client = createWebClient();
    if (!client || !id) {
      setLines([]);
      setCountDraft({});
      return;
    }
    const res = await loadReconciliationLines(client, id);
    if (!res.ok) {
      setMessage(res.error);
      setLines([]);
      setCountDraft({});
      return;
    }
    setLines(res.data);
    const draft: Record<string, string> = {};
    for (const line of res.data) {
      draft[line.stock_item_id] = String(line.counted_qty);
    }
    setCountDraft(draft);
  }, []);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  useEffect(() => {
    if (boot.kind !== "ready" || !reconId) return;
    void loadLines(reconId);
  }, [boot.kind, reconId, loadLines]);

  useEffect(() => {
    if (boot.kind !== "ready" || scope !== "partial") return;
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
          return;
        }
        setHits(res.data);
      })();
    }, 250);
    return () => window.clearTimeout(t);
  }, [boot.kind, itemQuery, scope]);

  async function onCreateDraft(e: FormEvent) {
    e.preventDefault();
    const client = createWebClient();
    if (!client || !warehouseId) return;
    if (scope === "partial" && partialItems.length === 0) {
      setMessage("Partial scope needs at least one item.");
      return;
    }
    setBusy(true);
    setMessage(null);
    const res = await createStockReconciliationDraft(client, {
      warehouseId,
      scope,
      itemIds: partialItems.map((i) => i.id),
      notes: notes.trim() || undefined,
      currency,
      exchangeRate: currency === "ZIG" ? zigExchangeRate() : 1,
    });
    setBusy(false);
    if (!res.ok) {
      setMessage(res.error);
      return;
    }
    setReconId(res.data);
    setMessage(`Draft created · ${res.data.slice(0, 8)}… · ${currency}`);
    setPartialItems([]);
    setNotes("");
    await refresh();
  }

  async function onSaveCounts(e: FormEvent) {
    e.preventDefault();
    const client = createWebClient();
    if (!client || !reconId || lines.length === 0) return;
    const payload = lines.map((line) => ({
      stock_item_id: line.stock_item_id,
      counted_qty: Number(countDraft[line.stock_item_id] ?? line.counted_qty),
    }));
    if (payload.some((l) => !Number.isFinite(l.counted_qty) || l.counted_qty < 0)) {
      setMessage("Counted qty must be >= 0 for every line.");
      return;
    }
    setBusy(true);
    setMessage(null);
    const res = await upsertStockReconciliationLines(client, {
      reconciliationId: reconId,
      lines: payload,
    });
    setBusy(false);
    if (!res.ok) {
      setMessage(res.error);
      return;
    }
    setMessage(`Updated ${res.data} line(s).`);
    await loadLines(reconId);
  }

  async function onSubmit() {
    const client = createWebClient();
    if (!client || !reconId) return;
    setBusy(true);
    setMessage(null);
    const res = await submitStockReconciliation(client, reconId);
    setBusy(false);
    if (!res.ok) {
      setMessage(res.error);
      return;
    }
    setMessage(`Submitted · ${res.data.slice(0, 8)}…`);
    await refresh();
    await loadLines(reconId);
  }

  async function onApprove() {
    const client = createWebClient();
    if (!client || !reconId) return;
    setBusy(true);
    setMessage(null);
    const res = await approveStockReconciliation(client, reconId);
    setBusy(false);
    if (!res.ok) {
      setMessage(res.error);
      return;
    }
    setMessage(`Approved · ${res.data.slice(0, 8)}…`);
    await refresh();
  }

  async function onCancel(e: FormEvent) {
    e.preventDefault();
    const client = createWebClient();
    if (!client || !reconId) return;
    setBusy(true);
    setMessage(null);
    const res = await cancelStockReconciliation(client, {
      reconciliationId: reconId,
      notes: cancelNotes.trim() || undefined,
    });
    setBusy(false);
    if (!res.ok) {
      setMessage(res.error);
      return;
    }
    setMessage(`Cancelled · ${res.data.slice(0, 8)}…`);
    setCancelNotes("");
    await refresh();
  }

  if (boot.kind === "loading") {
    return <p className={styles.muted}>Loading cycle count…</p>;
  }
  if (boot.kind === "auth") {
    return (
      <p className={styles.lede}>
        <Link href="/login">Sign in</Link> with warehouse staff for cycle counts.
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

  const selected = boot.recons.find((r) => r.id === reconId) ?? null;

  return (
    <div className={styles.form}>
      <p className={styles.muted} style={{ marginBottom: "0.75rem" }}>
        Typed OEM / counted qty. QR cycle-count scan: use the management device
        bridge. Currency on the draft is explicit <code>USD</code> |{" "}
        <code>ZIG</code>.
      </p>
      <fieldset className={styles.fieldset}>
        <legend className={styles.legend}>Create draft</legend>
        <form onSubmit={(e) => void onCreateDraft(e)}>
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
                  </option>
                ))}
              </select>
            </label>
            <label className={styles.field}>
              Scope
              <select
                value={scope}
                onChange={(e) => setScope(e.target.value as ReconciliationScope)}
                disabled={busy}
              >
                <option value="full">Full</option>
                <option value="partial">Partial</option>
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
              Notes
              <input value={notes} onChange={(e) => setNotes(e.target.value)} disabled={busy} />
            </label>
          </div>
          {currency === "ZIG" ? (
            <p className={styles.muted} style={{ marginTop: "0.65rem" }}>
              ZIG exchange rate applied: {zigExchangeRate()}
            </p>
          ) : null}
          {scope === "partial" ? (
            <>
              <label className={styles.field} style={{ marginTop: "0.75rem" }}>
                Add OEM
                <input
                  value={itemQuery}
                  onChange={(e) => setItemQuery(e.target.value)}
                  disabled={busy}
                  autoComplete="off"
                />
              </label>
              {hits.length > 0 ? (
                <ul className={styles.list}>
                  {hits.map((item) => (
                    <li key={item.id}>
                      <button
                        type="button"
                        className={styles.btnGhost}
                        onClick={() => {
                          setPartialItems((prev) =>
                            prev.some((p) => p.id === item.id)
                              ? prev
                              : [...prev, item],
                          );
                          setItemQuery("");
                          setHits([]);
                        }}
                      >
                        {item.oem_part_number}
                      </button>
                    </li>
                  ))}
                </ul>
              ) : null}
              {partialItems.length > 0 ? (
                <ul className={styles.list}>
                  {partialItems.map((item) => (
                    <li key={item.id}>
                      <code>{item.oem_part_number}</code>
                      <button
                        type="button"
                        className={styles.btnGhost}
                        style={{ marginLeft: "0.5rem" }}
                        onClick={() =>
                          setPartialItems((prev) =>
                            prev.filter((p) => p.id !== item.id),
                          )
                        }
                      >
                        Remove
                      </button>
                    </li>
                  ))}
                </ul>
              ) : null}
            </>
          ) : null}
          <div className={styles.formActions}>
            <button type="submit" className={styles.btn} disabled={busy || !warehouseId}>
              Create draft
            </button>
          </div>
        </form>
      </fieldset>

      <fieldset className={styles.fieldset}>
        <legend className={styles.legend}>Count · submit · approve</legend>
        {boot.recons.length === 0 ? (
          <p className={styles.muted}>No reconciliations visible.</p>
        ) : (
          <>
            <label className={styles.field}>
              Reconciliation
              <select
                value={reconId}
                onChange={(e) => setReconId(e.target.value)}
                disabled={busy}
              >
                {boot.recons.map((r) => (
                  <option key={r.id} value={r.id}>
                    {r.document_number ?? r.id.slice(0, 8)} · {r.status} ·{" "}
                    {r.currency}
                  </option>
                ))}
              </select>
            </label>
            {selected ? (
              <p className={styles.muted} style={{ marginTop: "0.65rem" }}>
                {selected.scope} · {selected.currency}
                {selected.variance_value_abs != null
                  ? ` · variance abs ${selected.variance_value_abs}`
                  : ""}
              </p>
            ) : null}

            {lines.length === 0 ? (
              <p className={styles.muted}>No lines on this draft.</p>
            ) : (
              <form onSubmit={(e) => void onSaveCounts(e)}>
                <ul className={styles.list}>
                  {lines.map((line) => (
                    <li key={line.id}>
                      <label className={styles.field}>
                        {line.stock_items?.oem_part_number ?? line.stock_item_id}{" "}
                        · system {line.system_qty} · cost {line.unit_cost}{" "}
                        {line.currency}
                        <input
                          type="number"
                          min="0"
                          step="any"
                          value={countDraft[line.stock_item_id] ?? ""}
                          onChange={(e) =>
                            setCountDraft((prev) => ({
                              ...prev,
                              [line.stock_item_id]: e.target.value,
                            }))
                          }
                          disabled={busy || selected?.status !== "draft"}
                        />
                      </label>
                    </li>
                  ))}
                </ul>
                <div className={styles.formActions}>
                  <button
                    type="submit"
                    className={styles.btn}
                    disabled={busy || selected?.status !== "draft"}
                  >
                    Save counts
                  </button>
                  <button
                    type="button"
                    className={styles.btnGhost}
                    disabled={busy || selected?.status !== "draft"}
                    onClick={() => void onSubmit()}
                  >
                    Submit
                  </button>
                  <button
                    type="button"
                    className={styles.btnGhost}
                    disabled={busy || selected?.status !== "pending_approval"}
                    onClick={() => void onApprove()}
                  >
                    Approve
                  </button>
                </div>
              </form>
            )}
          </>
        )}
      </fieldset>

      <fieldset className={styles.fieldset}>
        <legend className={styles.legend}>Cancel posted</legend>
        <form onSubmit={(e) => void onCancel(e)}>
          <label className={styles.field}>
            Cancel notes
            <input
              value={cancelNotes}
              onChange={(e) => setCancelNotes(e.target.value)}
              disabled={busy}
            />
          </label>
          <div className={styles.formActions}>
            <button
              type="submit"
              className={styles.btnGhost}
              disabled={busy || !reconId || selected?.status !== "posted"}
            >
              Cancel reconciliation
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
