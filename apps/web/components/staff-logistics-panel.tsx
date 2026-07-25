"use client";

import Link from "next/link";
import { FormEvent, useCallback, useEffect, useState } from "react";
import styles from "@/components/account.module.css";
import {
  confirmPickLines,
  createDeliveryJob,
  createDeliveryNote,
  createPickList,
  listDeliveryNotes,
  listDispatchInvoices,
  listPickLists,
  loadPickListLines,
  requireSession,
  submitDeliveryNote,
  type DeliveryNoteOption,
  type DispatchInvoiceOption,
  type PickListLineRow,
  type PickListOption,
} from "@/lib/staff-logistics";
import { createWebClient } from "@/lib/supabase";

type Boot =
  | { kind: "loading" }
  | { kind: "auth" }
  | { kind: "error"; message: string }
  | {
      kind: "ready";
      invoices: DispatchInvoiceOption[];
      pickLists: PickListOption[];
      deliveryNotes: DeliveryNoteOption[];
    };

export function StaffLogisticsPanel() {
  const [boot, setBoot] = useState<Boot>({ kind: "loading" });
  const [invoiceId, setInvoiceId] = useState("");
  const [pickListId, setPickListId] = useState("");
  const [deliveryNoteId, setDeliveryNoteId] = useState("");
  const [pickLines, setPickLines] = useState<PickListLineRow[]>([]);
  const [qtyDraft, setQtyDraft] = useState<Record<string, string>>({});
  const [jobNotes, setJobNotes] = useState("");
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

    const [inv, picks, dns] = await Promise.all([
      listDispatchInvoices(client),
      listPickLists(client),
      listDeliveryNotes(client),
    ]);
    if (!inv.ok) {
      setBoot({ kind: "error", message: inv.error });
      return;
    }
    if (!picks.ok) {
      setBoot({ kind: "error", message: picks.error });
      return;
    }
    if (!dns.ok) {
      setBoot({ kind: "error", message: dns.error });
      return;
    }

    setBoot({
      kind: "ready",
      invoices: inv.data,
      pickLists: picks.data,
      deliveryNotes: dns.data,
    });
    setInvoiceId((prev) => prev || inv.data[0]?.id || "");
    setPickListId((prev) => prev || picks.data[0]?.id || "");
    setDeliveryNoteId((prev) => prev || dns.data[0]?.id || "");
  }, []);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  async function loadLinesForPick(id: string) {
    const client = createWebClient();
    if (!client || !id) {
      setPickLines([]);
      setQtyDraft({});
      return;
    }
    const res = await loadPickListLines(client, id);
    if (!res.ok) {
      setMessage(res.error);
      setPickLines([]);
      setQtyDraft({});
      return;
    }
    setPickLines(res.data);
    const draft: Record<string, string> = {};
    for (const line of res.data) {
      draft[line.id] = String(
        line.qty_picked != null && line.qty_picked > 0
          ? line.qty_picked
          : line.qty_requested,
      );
    }
    setQtyDraft(draft);
  }

  useEffect(() => {
    if (boot.kind !== "ready" || !pickListId) return;
    void loadLinesForPick(pickListId);
  }, [boot.kind, pickListId]);

  async function onCreatePick(e: FormEvent) {
    e.preventDefault();
    const client = createWebClient();
    if (!client || !invoiceId) return;
    setBusy(true);
    setMessage(null);
    const res = await createPickList(client, invoiceId);
    setBusy(false);
    if (!res.ok) {
      setMessage(res.error);
      return;
    }
    setPickListId(res.data);
    setMessage(`Pick list created · ${res.data.slice(0, 8)}…`);
    await refresh();
  }

  async function onConfirmPick(e: FormEvent) {
    e.preventDefault();
    const client = createWebClient();
    if (!client || !pickListId) return;
    const lines = pickLines.map((line) => ({
      pick_list_line_id: line.id,
      qty_picked: Number(qtyDraft[line.id] ?? line.qty_requested),
    }));
    if (lines.some((l) => !Number.isFinite(l.qty_picked) || l.qty_picked < 0)) {
      setMessage("Each pick line needs qty_picked ≥ 0.");
      return;
    }
    setBusy(true);
    setMessage(null);
    const res = await confirmPickLines(client, pickListId, lines);
    setBusy(false);
    if (!res.ok) {
      setMessage(res.error);
      return;
    }
    setMessage(`Pick confirmed · ${res.data.slice(0, 8)}…`);
    await refresh();
    await loadLinesForPick(pickListId);
  }

  async function onCreateDn() {
    const client = createWebClient();
    if (!client || !pickListId) return;
    const pick = boot.kind === "ready"
      ? boot.pickLists.find((p) => p.id === pickListId)
      : null;
    const salesInvoiceId = pick?.sales_invoice_id || invoiceId;
    if (!salesInvoiceId) {
      setMessage("Need a sales invoice id (from pick list or selector).");
      return;
    }
    if (pickLines.length === 0) {
      setMessage("Load pick lines before creating a delivery note.");
      return;
    }
    const lines = pickLines
      .map((line) => {
        const qty = Number(qtyDraft[line.id] ?? line.qty_picked ?? line.qty_requested);
        return {
          sales_invoice_line_id: line.sales_invoice_line_id,
          qty,
        };
      })
      .filter((l) => Number.isFinite(l.qty) && l.qty > 0);
    if (lines.length === 0) {
      setMessage("DN requires at least one line with qty > 0.");
      return;
    }

    setBusy(true);
    setMessage(null);
    const res = await createDeliveryNote(client, {
      salesInvoiceId,
      pickListId,
      lines,
    });
    setBusy(false);
    if (!res.ok) {
      setMessage(res.error);
      return;
    }
    setDeliveryNoteId(res.data);
    setMessage(`Delivery note created · ${res.data.slice(0, 8)}…`);
    await refresh();
  }

  async function onSubmitDn() {
    const client = createWebClient();
    if (!client || !deliveryNoteId) return;
    setBusy(true);
    setMessage(null);
    const res = await submitDeliveryNote(client, deliveryNoteId);
    setBusy(false);
    if (!res.ok) {
      setMessage(res.error);
      return;
    }
    setMessage(`Delivery note submitted · ${res.data.slice(0, 8)}…`);
    await refresh();
  }

  async function onCreateJob(e: FormEvent) {
    e.preventDefault();
    const client = createWebClient();
    if (!client || !deliveryNoteId) return;
    setBusy(true);
    setMessage(null);
    const res = await createDeliveryJob(client, {
      deliveryNoteId,
      notes: jobNotes.trim() || undefined,
    });
    setBusy(false);
    if (!res.ok) {
      setMessage(res.error);
      return;
    }
    setMessage(`Delivery job created · ${res.data.slice(0, 8)}…`);
    setJobNotes("");
    await refresh();
  }

  if (boot.kind === "loading") {
    return <p className={styles.muted}>Loading logistics…</p>;
  }

  if (boot.kind === "auth") {
    return (
      <p className={styles.lede}>
        <Link href="/login">Sign in</Link> with warehouse/dispatcher/admin staff.
      </p>
    );
  }

  if (boot.kind === "error") {
    return (
      <p className={styles.lede} role="alert">
        {boot.message}{" "}
        <button
          type="button"
          className={styles.btnGhost}
          onClick={() => void refresh()}
        >
          Retry
        </button>
      </p>
    );
  }

  return (
    <div className={styles.form}>
      <fieldset className={styles.fieldset}>
        <legend className={styles.legend}>1 · Create pick list</legend>
        <p className={styles.muted} style={{ marginBottom: "0.75rem" }}>
          Posted dispatch invoices only. Opens all remaining lines via{" "}
          <code>create_pick_list</code>.
        </p>
        {boot.invoices.length === 0 ? (
          <p className={styles.muted}>No posted dispatch invoices visible.</p>
        ) : (
          <form onSubmit={(e) => void onCreatePick(e)}>
            <label className={styles.field}>
              Sales invoice
              <select
                value={invoiceId}
                onChange={(e) => setInvoiceId(e.target.value)}
                disabled={busy}
              >
                {boot.invoices.map((inv) => (
                  <option key={inv.id} value={inv.id}>
                    {inv.document_number ?? inv.id.slice(0, 8)}
                  </option>
                ))}
              </select>
            </label>
            <div className={styles.formActions} style={{ marginTop: "0.85rem" }}>
              <button type="submit" className={styles.btn} disabled={busy || !invoiceId}>
                Create pick list
              </button>
            </div>
          </form>
        )}
      </fieldset>

      <fieldset className={styles.fieldset}>
        <legend className={styles.legend}>2 · Confirm pick lines</legend>
        {boot.pickLists.length === 0 ? (
          <p className={styles.muted}>No pick lists yet.</p>
        ) : (
          <form onSubmit={(e) => void onConfirmPick(e)}>
            <label className={styles.field}>
              Pick list
              <select
                value={pickListId}
                onChange={(e) => setPickListId(e.target.value)}
                disabled={busy}
              >
                {boot.pickLists.map((pl) => (
                  <option key={pl.id} value={pl.id}>
                    {pl.document_number ?? pl.id.slice(0, 8)} · {pl.status}
                  </option>
                ))}
              </select>
            </label>
            {pickLines.length === 0 ? (
              <p className={styles.muted} style={{ marginTop: "0.75rem" }}>
                No lines on this pick list.
              </p>
            ) : (
              <ul className={styles.list} style={{ marginTop: "0.75rem" }}>
                {pickLines.map((line) => (
                  <li key={line.id}>
                    <strong>
                      {line.stock_items?.oem_part_number ??
                        line.stock_item_id.slice(0, 8)}
                    </strong>
                    {line.stock_items?.description
                      ? ` · ${line.stock_items.description}`
                      : null}
                    <br />
                    <span className={styles.muted}>
                      requested {line.qty_requested}
                      {line.qty_picked != null
                        ? ` · picked ${line.qty_picked}`
                        : ""}
                    </span>
                    <label
                      className={styles.field}
                      style={{ marginTop: "0.45rem", maxWidth: "10rem" }}
                    >
                      Qty picked
                      <input
                        type="number"
                        min={0}
                        step="any"
                        value={qtyDraft[line.id] ?? ""}
                        onChange={(e) =>
                          setQtyDraft((prev) => ({
                            ...prev,
                            [line.id]: e.target.value,
                          }))
                        }
                        disabled={busy}
                      />
                    </label>
                  </li>
                ))}
              </ul>
            )}
            <div className={styles.formActions} style={{ marginTop: "0.85rem" }}>
              <button
                type="submit"
                className={styles.btn}
                disabled={busy || !pickListId || pickLines.length === 0}
              >
                Confirm pick
              </button>
            </div>
          </form>
        )}
      </fieldset>

      <fieldset className={styles.fieldset}>
        <legend className={styles.legend}>3 · Delivery note</legend>
        <p className={styles.muted} style={{ marginBottom: "0.75rem" }}>
          Build a draft DN from the active pick lines, then submit to issue stock.
        </p>
        <div className={styles.formActions}>
          <button
            type="button"
            className={styles.btn}
            disabled={busy || !pickListId || pickLines.length === 0}
            onClick={() => void onCreateDn()}
          >
            Create DN from pick
          </button>
        </div>
        {boot.deliveryNotes.length === 0 ? (
          <p className={styles.muted} style={{ marginTop: "0.75rem" }}>
            No delivery notes yet.
          </p>
        ) : (
          <div style={{ marginTop: "0.85rem" }}>
            <label className={styles.field}>
              Delivery note
              <select
                value={deliveryNoteId}
                onChange={(e) => setDeliveryNoteId(e.target.value)}
                disabled={busy}
              >
                {boot.deliveryNotes.map((dn) => (
                  <option key={dn.id} value={dn.id}>
                    {dn.document_number ?? dn.id.slice(0, 8)} · {dn.status}
                  </option>
                ))}
              </select>
            </label>
            <div className={styles.formActions} style={{ marginTop: "0.85rem" }}>
              <button
                type="button"
                className={styles.btnGhost}
                disabled={busy || !deliveryNoteId}
                onClick={() => void onSubmitDn()}
              >
                Submit DN
              </button>
            </div>
          </div>
        )}
      </fieldset>

      <fieldset className={styles.fieldset}>
        <legend className={styles.legend}>4 · Delivery job</legend>
        <p className={styles.muted} style={{ marginBottom: "0.75rem" }}>
          Requires a submitted DN. Dispatcher/admin via{" "}
          <code>create_delivery_job</code>. Assign + mark dispatched on{" "}
          <Link href="/staff/logistics/tracking">Live tracking</Link>{" "}
          (Realtime subscribe-only; GPS from delivery Android app).
        </p>
        <form onSubmit={(e) => void onCreateJob(e)}>
          <label className={styles.field}>
            Notes (optional)
            <input
              type="text"
              value={jobNotes}
              onChange={(e) => setJobNotes(e.target.value)}
              disabled={busy}
              placeholder="Route notes"
            />
          </label>
          <div className={styles.formActions} style={{ marginTop: "0.85rem" }}>
            <button
              type="submit"
              className={styles.btn}
              disabled={busy || !deliveryNoteId}
            >
              Create delivery job
            </button>
          </div>
        </form>
      </fieldset>

      {message ? (
        <p className={styles.lede} role="status">
          {message}
        </p>
      ) : null}
    </div>
  );
}
