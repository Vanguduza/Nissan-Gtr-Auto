"use client";

import Link from "next/link";
import { FormEvent, useCallback, useEffect, useState } from "react";
import styles from "@/components/account.module.css";
import {
  approveStockTransfer,
  createStockTransfer,
  listPendingTransfers,
  listWarehouses,
  rejectStockTransfer,
  requireSession,
  searchStockItems,
  type StockEntryOption,
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
      pending: StockEntryOption[];
    };

type DraftLine = {
  key: string;
  item: StockItemOption;
  qty: string;
};

export function StaffWarehouseTransfersPanel() {
  const [boot, setBoot] = useState<Boot>({ kind: "loading" });
  const [fromId, setFromId] = useState("");
  const [toId, setToId] = useState("");
  const [notes, setNotes] = useState("");
  const [itemQuery, setItemQuery] = useState("");
  const [hits, setHits] = useState<StockItemOption[]>([]);
  const [pendingItem, setPendingItem] = useState<StockItemOption | null>(null);
  const [qty, setQty] = useState("1");
  const [draftLines, setDraftLines] = useState<DraftLine[]>([]);
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
    const [wh, pending] = await Promise.all([
      listWarehouses(client, { includeQuarantine: true }),
      listPendingTransfers(client),
    ]);
    if (!wh.ok) {
      setBoot({ kind: "error", message: wh.error });
      return;
    }
    if (!pending.ok) {
      setBoot({ kind: "error", message: pending.error });
      return;
    }
    setBoot({
      kind: "ready",
      warehouses: wh.data,
      pending: pending.data,
    });
    setFromId((prev) => prev || wh.data.find((w) => !w.is_quarantine)?.id || wh.data[0]?.id || "");
    setToId((prev) => {
      if (prev) return prev;
      const from = wh.data.find((w) => !w.is_quarantine)?.id || wh.data[0]?.id;
      return wh.data.find((w) => w.id !== from)?.id || "";
    });
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

  function queueLine(e: FormEvent) {
    e.preventDefault();
    if (!pendingItem?.base_uom_id) {
      setMessage("Select an item with a base UOM.");
      return;
    }
    const n = Number(qty);
    if (!Number.isFinite(n) || n <= 0) {
      setMessage("Qty must be positive.");
      return;
    }
    setDraftLines((prev) => [
      ...prev,
      { key: `${pendingItem.id}-${Date.now()}`, item: pendingItem, qty: String(n) },
    ]);
    setPendingItem(null);
    setItemQuery("");
    setHits([]);
    setQty("1");
    setMessage(null);
  }

  async function onCreate(e: FormEvent) {
    e.preventDefault();
    const client = createWebClient();
    if (!client || !fromId || !toId || draftLines.length === 0) return;
    if (fromId === toId) {
      setMessage("From and to warehouses must differ.");
      return;
    }
    setBusy(true);
    setMessage(null);
    const res = await createStockTransfer(client, {
      fromWarehouseId: fromId,
      toWarehouseId: toId,
      notes: notes.trim() || "Staff transfer",
      lines: draftLines.map((d) => ({
        stock_item_id: d.item.id,
        uom_id: d.item.base_uom_id!,
        qty: Number(d.qty),
      })),
    });
    setBusy(false);
    if (!res.ok) {
      setMessage(res.error);
      return;
    }
    setDraftLines([]);
    setNotes("");
    setMessage(`Transfer pending approval · ${res.data.slice(0, 8)}…`);
    await refresh();
  }

  async function onApprove(id: string) {
    const client = createWebClient();
    if (!client) return;
    setBusy(true);
    setMessage(null);
    const res = await approveStockTransfer(client, id);
    setBusy(false);
    if (!res.ok) {
      setMessage(res.error);
      return;
    }
    setMessage(`Transfer approved · ${res.data.slice(0, 8)}…`);
    await refresh();
  }

  async function onReject(id: string) {
    const client = createWebClient();
    if (!client) return;
    setBusy(true);
    setMessage(null);
    const res = await rejectStockTransfer(client, id);
    setBusy(false);
    if (!res.ok) {
      setMessage(res.error);
      return;
    }
    setMessage(`Transfer rejected · ${res.data.slice(0, 8)}…`);
    await refresh();
  }

  if (boot.kind === "loading") {
    return <p className={styles.muted}>Loading transfers…</p>;
  }
  if (boot.kind === "auth") {
    return (
      <p className={styles.lede}>
        <Link href="/login">Sign in</Link> with warehouse staff for dual-auth
        transfers.
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

  const whLabel = (id: string | null) => {
    if (!id) return "—";
    const w = boot.warehouses.find((x) => x.id === id);
    return w ? w.code : id.slice(0, 8);
  };

  return (
    <div className={styles.form}>
      <fieldset className={styles.fieldset}>
        <legend className={styles.legend}>Create transfer</legend>
        <p className={styles.muted} style={{ marginBottom: "0.75rem" }}>
          First signature is the creator; a different staff user must approve.
        </p>
        <div className={styles.formGrid}>
          <label className={styles.field}>
            From
            <select value={fromId} onChange={(e) => setFromId(e.target.value)} disabled={busy}>
              {boot.warehouses.map((w) => (
                <option key={w.id} value={w.id}>
                  {w.code} — {w.name}
                  {w.is_quarantine ? " (quarantine)" : ""}
                </option>
              ))}
            </select>
          </label>
          <label className={styles.field}>
            To
            <select value={toId} onChange={(e) => setToId(e.target.value)} disabled={busy}>
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
            <input value={notes} onChange={(e) => setNotes(e.target.value)} disabled={busy} />
          </label>
        </div>

        <form onSubmit={queueLine} style={{ marginTop: "0.75rem" }}>
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
                  </button>
                </li>
              ))}
            </ul>
          ) : null}
          <div className={styles.formActions}>
            <button type="submit" className={styles.btn} disabled={busy || !pendingItem}>
              Queue line
            </button>
          </div>
        </form>

        {draftLines.length > 0 ? (
          <ul className={styles.list}>
            {draftLines.map((d) => (
              <li key={d.key}>
                <code>{d.item.oem_part_number}</code> · qty {d.qty}
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
        ) : null}

        <form onSubmit={(e) => void onCreate(e)}>
          <div className={styles.formActions}>
            <button
              type="submit"
              className={styles.btn}
              disabled={busy || draftLines.length === 0}
            >
              Create transfer
            </button>
          </div>
        </form>
      </fieldset>

      <fieldset className={styles.fieldset}>
        <legend className={styles.legend}>Pending approval</legend>
        {boot.pending.length === 0 ? (
          <p className={styles.muted}>No pending transfers.</p>
        ) : (
          <ul className={styles.list}>
            {boot.pending.map((t) => (
              <li key={t.id}>
                <div>
                  {t.document_number ?? t.id.slice(0, 8)} · {whLabel(t.from_warehouse_id)} →{" "}
                  {whLabel(t.to_warehouse_id)}
                  {t.notes ? ` · ${t.notes}` : ""}
                </div>
                <div className={styles.formActions}>
                  <button
                    type="button"
                    className={styles.btn}
                    disabled={busy}
                    onClick={() => void onApprove(t.id)}
                  >
                    Approve
                  </button>
                  <button
                    type="button"
                    className={styles.btnGhost}
                    disabled={busy}
                    onClick={() => void onReject(t.id)}
                  >
                    Reject
                  </button>
                </div>
              </li>
            ))}
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
