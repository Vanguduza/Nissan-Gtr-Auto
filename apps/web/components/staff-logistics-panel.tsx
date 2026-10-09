"use client";

import Link from "next/link";
import { FormEvent, useCallback, useEffect, useState } from "react";
import styles from "@/components/account.module.css";
import {
  cancelDeliveryNote,
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
import {
  assignDeliveryJob,
  listDeliveryJobs,
  suggestDeliveryAssignees,
  type AssigneeSuggestion,
  type DeliveryJobOption,
} from "@/lib/staff-delivery-tracking";
import { canOverrideAssignDeliveryJob } from "@gtr/supabase-client";
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
      deliveryJobs: DeliveryJobOption[];
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
  const [overrideJobId, setOverrideJobId] = useState<string | null>(null);
  const [overrideSuggestions, setOverrideSuggestions] = useState<
    AssigneeSuggestion[]
  >([]);
  const [overrideAssignee, setOverrideAssignee] = useState("");
  const [overrideBusy, setOverrideBusy] = useState(false);

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

    const [inv, picks, dns, jobs] = await Promise.all([
      listDispatchInvoices(client),
      listPickLists(client),
      listDeliveryNotes(client),
      listDeliveryJobs(client),
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
    if (!jobs.ok) {
      setBoot({ kind: "error", message: jobs.error });
      return;
    }

    setBoot({
      kind: "ready",
      invoices: inv.data,
      pickLists: picks.data,
      deliveryNotes: dns.data,
      deliveryJobs: jobs.data,
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

  async function onCancelDn() {
    const client = createWebClient();
    if (!client || !deliveryNoteId) {
      setMessage("Select a delivery note.");
      return;
    }
    setBusy(true);
    setMessage(null);
    const res = await cancelDeliveryNote(client, deliveryNoteId);
    setBusy(false);
    if (!res.ok) {
      setMessage(res.error);
      return;
    }
    setMessage(`Delivery note cancelled · ${res.data.slice(0, 8)}…`);
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

  async function onBeginOverrideAssign(job: DeliveryJobOption) {
    if (!canOverrideAssignDeliveryJob(job.assignee_user_id, job.status)) {
      setMessage(
        "Override assign only for unassigned/stuck jobs (auto-assign remains SoR).",
      );
      return;
    }
    const client = createWebClient();
    if (!client) return;
    setOverrideJobId(job.id);
    setDeliveryNoteId(job.delivery_note_id);
    setOverrideAssignee("");
    setOverrideBusy(true);
    setMessage(null);
    const res = await suggestDeliveryAssignees(client, job.id);
    setOverrideBusy(false);
    if (!res.ok) {
      setOverrideSuggestions([]);
      setMessage(res.error);
      return;
    }
    setOverrideSuggestions(res.data);
    setOverrideAssignee(res.data[0]?.user_id ?? "");
  }

  async function onConfirmOverrideAssign() {
    const client = createWebClient();
    if (!client || !overrideJobId || !overrideAssignee.trim()) return;
    const job =
      boot.kind === "ready"
        ? boot.deliveryJobs.find((j) => j.id === overrideJobId)
        : null;
    if (
      !job ||
      !canOverrideAssignDeliveryJob(job.assignee_user_id, job.status)
    ) {
      setMessage(
        "Override assign only for unassigned/stuck jobs (auto-assign remains SoR).",
      );
      return;
    }
    const ok = window.confirm(
      `Override-assign driver ${overrideAssignee.trim().slice(0, 8)}…? ` +
        "Auto-assign remains SoR — exception uses assign_delivery_job(p_override=true).",
    );
    if (!ok) return;
    setOverrideBusy(true);
    setMessage(null);
    const res = await assignDeliveryJob(
      client,
      overrideJobId,
      overrideAssignee.trim(),
      true,
    );
    setOverrideBusy(false);
    if (!res.ok) {
      setMessage(res.error);
      return;
    }
    setMessage(
      `Exception override assigned · ${overrideAssignee.trim().slice(0, 8)}…`,
    );
    setOverrideJobId(null);
    setOverrideSuggestions([]);
    setOverrideAssignee("");
    await refresh();
  }

  if (boot.kind === "loading") {
    return <p className={styles.emptyState}>Loading logistics…</p>;
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

  const selectedDn = boot.deliveryNotes.find((dn) => dn.id === deliveryNoteId);
  const canMutateDn =
    !!deliveryNoteId &&
    (selectedDn?.status === "draft" || selectedDn?.status === "submitted");

  return (
    <div className={styles.form}>
      <fieldset className={styles.fieldset}>
        <legend className={styles.legend}>1 · Create pick list</legend>
        {boot.invoices.length === 0 ? (
          <p className={styles.emptyState}>No posted dispatch invoices visible.</p>
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
          <p className={styles.emptyState}>No pick lists yet.</p>
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
          <p className={styles.emptyState}>No delivery notes yet.</p>
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
                disabled={
                  busy || !canMutateDn || selectedDn?.status !== "draft"
                }
                onClick={() => void onSubmitDn()}
              >
                Submit DN
              </button>
              <button
                type="button"
                className={styles.btnGhost}
                disabled={busy || !canMutateDn}
                onClick={() => void onCancelDn()}
              >
                Cancel DN
              </button>
            </div>
          </div>
        )}
      </fieldset>

      <fieldset className={styles.fieldset}>
        <legend className={styles.legend}>4 · Delivery jobs (visibility)</legend>
        <p className={styles.muted} style={{ marginBottom: "0.75rem" }}>
          Status + DN link · unassigned highlighted. Auto-assign remains SoR —
          Override assign only for stuck/unassigned (exception), not a
          pick-driver desk.{" "}
          <Link href="/staff/logistics/tracking">Live tracking</Link> for map /
          dispatch.
        </p>
        {boot.deliveryJobs.length === 0 ? (
          <p className={styles.emptyState}>No delivery jobs yet.</p>
        ) : (
          <ul className={styles.list}>
            {boot.deliveryJobs.map((job) => {
              const canOverride = canOverrideAssignDeliveryJob(
                job.assignee_user_id,
                job.status,
              );
              return (
                <li key={job.id}>
                  <button
                    type="button"
                    className={styles.btnGhost}
                    disabled={busy || overrideBusy}
                    onClick={() => setDeliveryNoteId(job.delivery_note_id)}
                  >
                    <strong>
                      {job.document_number ?? job.id.slice(0, 8)}
                    </strong>{" "}
                    · {job.status}
                    {canOverride ? " · unassigned" : ""} · dn=
                    {job.delivery_note_id.slice(0, 8)}…
                    {!canOverride && job.assignee_user_id
                      ? ` · driver=${job.assignee_user_id.slice(0, 8)}…`
                      : ""}
                  </button>
                  {canOverride ? (
                    <div
                      className={styles.formActions}
                      style={{ marginTop: "0.35rem" }}
                    >
                      <button
                        type="button"
                        className={styles.btn}
                        disabled={busy || overrideBusy}
                        onClick={() => void onBeginOverrideAssign(job)}
                      >
                        Override assign
                      </button>
                    </div>
                  ) : null}
                </li>
              );
            })}
          </ul>
        )}
        {overrideJobId ? (
          <div style={{ marginTop: "1rem" }}>
            <p className={styles.muted}>
              Exception override for {overrideJobId.slice(0, 8)}… — suggest +
              confirm calls <code>assign_delivery_job</code> with{" "}
              <code>p_override=true</code>.
            </p>
            {overrideSuggestions.length === 0 ? (
              <p className={styles.muted}>No eligible drivers suggested.</p>
            ) : (
              <ul className={styles.list}>
                {overrideSuggestions.map((s) => (
                  <li key={s.user_id}>
                    <button
                      type="button"
                      className={styles.btnGhost}
                      disabled={overrideBusy}
                      onClick={() => setOverrideAssignee(s.user_id)}
                    >
                      {s.user_id.slice(0, 8)}… · {s.status} · open{" "}
                      {s.open_jobs}/{s.capacity}
                      {overrideAssignee === s.user_id ? " · selected" : ""}
                    </button>
                  </li>
                ))}
              </ul>
            )}
            <label className={styles.field} style={{ marginTop: "0.75rem" }}>
              Assignee user id
              <input
                type="text"
                value={overrideAssignee}
                onChange={(e) => setOverrideAssignee(e.target.value)}
                disabled={overrideBusy}
                placeholder="uuid"
              />
            </label>
            <div className={styles.formActions} style={{ marginTop: "0.75rem" }}>
              <button
                type="button"
                className={styles.btn}
                disabled={overrideBusy || !overrideAssignee.trim()}
                onClick={() => void onConfirmOverrideAssign()}
              >
                Confirm override assign
              </button>
              <button
                type="button"
                className={styles.btnGhost}
                disabled={overrideBusy}
                onClick={() => {
                  setOverrideJobId(null);
                  setOverrideSuggestions([]);
                  setOverrideAssignee("");
                }}
              >
                Cancel
              </button>
            </div>
          </div>
        ) : null}
      </fieldset>

      <fieldset className={styles.fieldset}>
        <legend className={styles.legend}>5 · Create delivery job</legend>
        <p className={styles.muted} style={{ marginBottom: "0.75rem" }}>
          Requires a submitted DN. Auto-assign is SoR; exception override is on
          the job list above or{" "}
          <Link href="/staff/logistics/tracking">Live tracking</Link>.
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
        <p className={styles.formStatus} role="status">
          {message}
        </p>
      ) : null}
    </div>
  );
}
