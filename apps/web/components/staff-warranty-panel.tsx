"use client";

import Link from "next/link";
import { FormEvent, useCallback, useEffect, useState } from "react";
import styles from "@/components/account.module.css";
import {
  approveWarrantyClaim,
  closeWarrantyClaim,
  listWarrantyClaims,
  openWarrantyClaim,
  postReturnToQuarantine,
  rejectWarrantyClaim,
  requireSession,
  type WarrantyClaimOption,
  type WarrantyClaimResolution,
} from "@/lib/staff-warranty";
import {
  listWarehouses,
  searchStockItems,
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
      claims: WarrantyClaimOption[];
      warehouses: WarehouseOption[];
    };

export function StaffWarrantyPanel() {
  const [boot, setBoot] = useState<Boot>({ kind: "loading" });
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<string | null>(null);

  const [invoiceId, setInvoiceId] = useState("");
  const [serialId, setSerialId] = useState("");
  const [batchId, setBatchId] = useState("");
  const [notes, setNotes] = useState("");

  const [claimId, setClaimId] = useState("");
  const [resolution, setResolution] =
    useState<WarrantyClaimResolution>("return_only");
  const [rejectReason, setRejectReason] = useState("");

  const [fromWarehouseId, setFromWarehouseId] = useState("");
  const [quarNotes, setQuarNotes] = useState("Return to quarantine");
  const [itemQuery, setItemQuery] = useState("");
  const [hits, setHits] = useState<StockItemOption[]>([]);
  const [selectedItem, setSelectedItem] = useState<StockItemOption | null>(
    null,
  );
  const [qty, setQty] = useState("1");

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

    const [claims, warehouses] = await Promise.all([
      listWarrantyClaims(client),
      listWarehouses(client),
    ]);
    if (!claims.ok) {
      setBoot({ kind: "error", message: claims.error });
      return;
    }
    if (!warehouses.ok) {
      setBoot({ kind: "error", message: warehouses.error });
      return;
    }

    setBoot({
      kind: "ready",
      claims: claims.data,
      warehouses: warehouses.data,
    });
    setClaimId((prev) => prev || claims.data[0]?.id || "");
    setFromWarehouseId((prev) => prev || warehouses.data[0]?.id || "");
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

  async function onOpen(e: FormEvent) {
    e.preventDefault();
    const client = createWebClient();
    if (!client) return;
    if (!invoiceId.trim() && !serialId.trim() && !batchId.trim()) {
      setMessage("Provide invoice, serial, and/or batch id.");
      return;
    }
    setBusy(true);
    setMessage(null);
    const res = await openWarrantyClaim(client, {
      salesInvoiceId: invoiceId.trim() || undefined,
      stockSerialId: serialId.trim() || undefined,
      stockBatchId: batchId.trim() || undefined,
      notes: notes.trim() || undefined,
    });
    setBusy(false);
    if (!res.ok) {
      setMessage(res.error);
      return;
    }
    setMessage(`Opened claim ${res.data.slice(0, 8)}…`);
    await refresh();
  }

  async function onApprove() {
    const client = createWebClient();
    if (!client || !claimId) return;
    setBusy(true);
    setMessage(null);
    const res = await approveWarrantyClaim(client, {
      claimId,
      resolution,
    });
    setBusy(false);
    if (!res.ok) {
      setMessage(res.error);
      return;
    }
    setMessage(`Approved ${res.data.slice(0, 8)}… · ${resolution}`);
    await refresh();
  }

  async function onReject() {
    const client = createWebClient();
    if (!client || !claimId) return;
    setBusy(true);
    setMessage(null);
    const res = await rejectWarrantyClaim(client, {
      claimId,
      reason: rejectReason.trim() || undefined,
    });
    setBusy(false);
    if (!res.ok) {
      setMessage(res.error);
      return;
    }
    setMessage(`Rejected ${res.data.slice(0, 8)}…`);
    await refresh();
  }

  async function onClose() {
    const client = createWebClient();
    if (!client || !claimId) return;
    setBusy(true);
    setMessage(null);
    const res = await closeWarrantyClaim(client, claimId);
    setBusy(false);
    if (!res.ok) {
      setMessage(res.error);
      return;
    }
    setMessage(`Closed ${res.data.slice(0, 8)}…`);
    await refresh();
  }

  async function onQuarantine(e: FormEvent) {
    e.preventDefault();
    const client = createWebClient();
    if (!client || !selectedItem || !fromWarehouseId) return;
    if (!selectedItem.base_uom_id) {
      setMessage("Item has no base UOM.");
      return;
    }
    const n = Number(qty);
    if (!Number.isFinite(n) || n <= 0) {
      setMessage("Qty must be positive.");
      return;
    }
    setBusy(true);
    setMessage(null);
    const res = await postReturnToQuarantine(client, {
      fromWarehouseId,
      notes: quarNotes || "Return to quarantine",
      lines: [
        {
          stock_item_id: selectedItem.id,
          uom_id: selectedItem.base_uom_id,
          qty: n,
        },
      ],
    });
    setBusy(false);
    if (!res.ok) {
      setMessage(res.error);
      return;
    }
    setMessage(
      `Quarantine transfer ${res.data.slice(0, 8)}… (destination forced to quarantine warehouse)`,
    );
    setSelectedItem(null);
    setItemQuery("");
    setHits([]);
    setQty("1");
  }

  if (boot.kind === "loading") {
    return <p className={styles.muted}>Loading warranty…</p>;
  }
  if (boot.kind === "auth") {
    return (
      <p className={styles.lede}>
        <Link href="/login">Sign in</Link> with sales/warehouse/admin staff.
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
      <p className={styles.muted} style={{ marginBottom: "0.75rem" }}>
        Typed serial / invoice / OEM only. QR identification: use the management
        device bridge.
      </p>
      {message ? (
        <p className={styles.formStatus} role="status">
          {message}
        </p>
      ) : null}

      <fieldset className={styles.fieldset}>
        <legend className={styles.legend}>1 · Open claim</legend>
        <form onSubmit={(e) => void onOpen(e)}>
          <div className={styles.formGrid}>
            <label className={styles.field}>
              Sales invoice id
              <input
                value={invoiceId}
                onChange={(e) => setInvoiceId(e.target.value)}
                disabled={busy}
                placeholder="uuid"
              />
            </label>
            <label className={styles.field}>
              Stock serial id
              <input
                value={serialId}
                onChange={(e) => setSerialId(e.target.value)}
                disabled={busy}
                placeholder="uuid"
              />
            </label>
            <label className={styles.field}>
              Stock batch id
              <input
                value={batchId}
                onChange={(e) => setBatchId(e.target.value)}
                disabled={busy}
                placeholder="uuid"
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
          </div>
          <div className={styles.formActions}>
            <button type="submit" className={styles.btnGhost} disabled={busy}>
              Open claim
            </button>
          </div>
        </form>
      </fieldset>

      <fieldset className={styles.fieldset}>
        <legend className={styles.legend}>2 · Decide / close</legend>
        <div className={styles.formGrid}>
          <label className={styles.field}>
            Claim
            <select
              value={claimId}
              onChange={(e) => setClaimId(e.target.value)}
              disabled={busy}
            >
              {boot.claims.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.document_number} · {c.status}
                </option>
              ))}
            </select>
          </label>
          <label className={styles.field}>
            Resolution (approve)
            <select
              value={resolution}
              onChange={(e) =>
                setResolution(e.target.value as WarrantyClaimResolution)
              }
              disabled={busy}
            >
              <option value="return_only">return_only</option>
              <option value="credit_note">credit_note</option>
              <option value="replacement">replacement</option>
              <option value="reject_only">reject_only</option>
            </select>
          </label>
          <label className={styles.field} style={{ gridColumn: "1 / -1" }}>
            Reject reason
            <input
              value={rejectReason}
              onChange={(e) => setRejectReason(e.target.value)}
              disabled={busy}
            />
          </label>
        </div>
        <div className={styles.formActions}>
          <button
            type="button"
            className={styles.btnGhost}
            disabled={busy || !claimId}
            onClick={() => void onApprove()}
          >
            Approve
          </button>
          <button
            type="button"
            className={styles.btnGhost}
            disabled={busy || !claimId}
            onClick={() => void onReject()}
          >
            Reject
          </button>
          <button
            type="button"
            className={styles.btnGhost}
            disabled={busy || !claimId}
            onClick={() => void onClose()}
          >
            Close
          </button>
        </div>
        <p className={styles.muted} style={{ marginTop: "0.65rem" }}>
          Credit-note / replacement approvals may need line JSON from a fuller
          tool — return_only works with claim context alone when stock is on the
          claim.
        </p>
      </fieldset>

      <fieldset className={styles.fieldset}>
        <legend className={styles.legend}>3 · Return to quarantine</legend>
        <p className={styles.muted} style={{ marginBottom: "0.75rem" }}>
          <code>post_return_to_quarantine</code> — destination is always the
          quarantine warehouse (never a direct exchange to saleable stock).
        </p>
        <form onSubmit={(e) => void onQuarantine(e)}>
          <div className={styles.formGrid}>
            <label className={styles.field}>
              From warehouse
              <select
                value={fromWarehouseId}
                onChange={(e) => setFromWarehouseId(e.target.value)}
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
              Notes
              <input
                value={quarNotes}
                onChange={(e) => setQuarNotes(e.target.value)}
                disabled={busy}
              />
            </label>
            <label className={styles.field}>
              OEM / description
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
          </div>
          {hits.length ? (
            <ul className={styles.navList}>
              {hits.map((h) => (
                <li key={h.id}>
                  <button
                    type="button"
                    className={styles.btnGhost}
                    onClick={() => {
                      setSelectedItem(h);
                      setItemQuery(h.oem_part_number);
                      setHits([]);
                    }}
                  >
                    {h.oem_part_number} — {h.description ?? "—"}
                  </button>
                </li>
              ))}
            </ul>
          ) : null}
          {selectedItem ? (
            <p className={styles.muted}>
              Selected {selectedItem.oem_part_number}
            </p>
          ) : null}
          <div className={styles.formActions}>
            <button
              type="submit"
              className={styles.btnGhost}
              disabled={busy || !selectedItem}
            >
              Post to quarantine
            </button>
          </div>
        </form>
      </fieldset>
    </div>
  );
}
