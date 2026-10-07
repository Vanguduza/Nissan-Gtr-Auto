"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import styles from "@/components/account.module.css";
import {
  approveDriverCashVariance,
  hoursHeld,
  listDriverCash,
  listVarianceReasons,
  receiveDriverCash,
  requireSession,
  type DriverCashBoard,
  type DriverCashHandin,
  type VarianceReason,
} from "@/lib/staff-driver-cash";
import { waitedFor } from "@/lib/staff-approvals";
import { createWebClient } from "@/lib/supabase";

type Boot =
  | { kind: "loading" }
  | { kind: "auth" }
  | { kind: "error"; message: string }
  | { kind: "ready"; board: DriverCashBoard; reasons: VarianceReason[] };

const FILTERS: { label: string; value: string | null }[] = [
  { label: "To count", value: "submitted" },
  { label: "Differences", value: "variance_pending" },
  { label: "All", value: null },
];

const STATUS: Record<string, string> = {
  submitted: "Waiting to be counted",
  received: "Counted — matches",
  variance_pending: "Difference — manager to review",
  approved: "Difference signed off",
};

/** Cash held longer than this is flagged: it should come in at the end of the run. */
const OVERDUE_HOURS = 24;

function money(amount: number, currency: string) {
  return `${currency} ${amount.toFixed(2)}`;
}

type Draft = { counted: string; reason: string; notes: string };

/**
 * Driver cash: what each driver still holds from cash-on-delivery (and for how long), hand-ins to
 * count, and differences for a manager to sign off. Whoever counts cannot also approve a difference.
 */
export function StaffDriverCashPanel() {
  const [boot, setBoot] = useState<Boot>({ kind: "loading" });
  const [status, setStatus] = useState<string | null>("submitted");
  const [drafts, setDrafts] = useState<Record<string, Draft>>({});
  const [busyId, setBusyId] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);

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
    const [res, reasons] = await Promise.all([listDriverCash(client, status), listVarianceReasons(client)]);
    if (!res.ok) {
      setBoot({ kind: "error", message: res.error });
      return;
    }
    setBoot({ kind: "ready", board: res.data, reasons: reasons.ok ? reasons.data : [] });
  }, [status]);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  const draft = (h: DriverCashHandin): Draft =>
    drafts[h.id] ?? { counted: h.declaredAmount.toFixed(2), reason: h.reasonCode ?? "", notes: "" };
  const setDraft = (id: string, patch: Partial<Draft>, h: DriverCashHandin) =>
    setDrafts((d) => ({ ...d, [id]: { ...draft(h), ...patch } }));

  async function receive(h: DriverCashHandin) {
    const client = createWebClient();
    if (!client) return;
    const d = draft(h);
    const counted = Number(d.counted.replace(",", "."));
    if (!Number.isFinite(counted) || counted < 0) {
      setMessage("Enter the amount you counted.");
      return;
    }
    if (Math.abs(counted - h.expectedAmount) > 0.009 && !d.reason) {
      setMessage("Pick a reason for the difference — a manager reviews it.");
      return;
    }
    setBusyId(h.id);
    setMessage(null);
    const res = await receiveDriverCash(client, h.id, counted, d.reason || null, d.notes);
    setBusyId(null);
    if (!res.ok) {
      setMessage(res.error);
      return;
    }
    setMessage(
      res.data.status === "received"
        ? `${money(counted, h.currency)} received from ${h.driverName ?? "the driver"}. It matches.`
        : `Received with a difference of ${money(res.data.variance ?? 0, h.currency)}. A different manager must sign it off.`,
    );
    await refresh();
  }

  async function approve(h: DriverCashHandin) {
    const client = createWebClient();
    if (!client) return;
    const d = draft(h);
    setBusyId(h.id);
    setMessage(null);
    const res = await approveDriverCashVariance(client, h.id, d.reason || h.reasonCode || "", d.notes);
    setBusyId(null);
    if (!res.ok) {
      setMessage(res.error);
      return;
    }
    setMessage(`Difference of ${money(h.variance ?? 0, h.currency)} signed off.`);
    await refresh();
  }

  if (boot.kind === "loading") return <p className={styles.muted}>Loading driver cash…</p>;
  if (boot.kind === "auth") {
    return (
      <p className={styles.lede} role="status">
        <Link href="/login">Sign in</Link> as a cashier, finance, dispatcher or manager to receive driver cash.
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

  const { board, reasons } = boot;
  const reasonLabel = (code: string | null) => reasons.find((r) => r.code === code)?.label ?? code;

  return (
    <div className={styles.form}>
      <h2 className={styles.sectionTitle}>Still with drivers</h2>
      {board.holding.length === 0 ? (
        <p className={styles.muted} role="status">
          No driver is holding delivery cash.
        </p>
      ) : (
        <ul className={styles.list}>
          {board.holding.map((h) => {
            const hours = hoursHeld(h.oldestAt);
            const overdue = hours != null && hours >= OVERDUE_HOURS;
            return (
              <li key={`${h.driverUserId}-${h.currency}`}>
                <strong>
                  {h.driverName ?? "Driver"} · {money(h.amount, h.currency)}
                </strong>
                <br />
                <span className={overdue ? undefined : styles.muted} role={overdue ? "alert" : undefined}>
                  {h.count} collection{h.count === 1 ? "" : "s"} not handed in
                  {h.oldestAt ? ` · oldest ${waitedFor(h.oldestAt)} ago` : ""}
                  {overdue ? " — overdue, ask the driver to hand it in" : ""}
                </span>
              </li>
            );
          })}
        </ul>
      )}

      <h2 className={styles.sectionTitle}>Hand-ins</h2>
      <div className={styles.formActions} role="group" aria-label="Show">
        {FILTERS.map((f) => (
          <button
            key={f.label}
            type="button"
            className={status === f.value ? styles.btn : styles.btnGhost}
            aria-pressed={status === f.value}
            onClick={() => setStatus(f.value)}
          >
            {f.label}
          </button>
        ))}
      </div>

      {board.handins.length === 0 ? (
        <p className={styles.muted} role="status">
          {status === "submitted" ? "No cash waiting to be counted." : status === "variance_pending" ? "No differences to review." : "No hand-ins yet."}
        </p>
      ) : (
        <ul className={styles.list}>
          {board.handins.map((h) => {
            const d = draft(h);
            const counted = Number(d.counted.replace(",", "."));
            const differs = Number.isFinite(counted) && Math.abs(counted - h.expectedAmount) > 0.009;
            return (
              <li key={h.id}>
                <strong>
                  {h.documentNumber ?? "Hand-in"} · {h.driverName ?? "Driver"} · {money(h.expectedAmount, h.currency)} expected
                </strong>
                <br />
                <span className={styles.muted}>
                  {[
                    STATUS[h.status] ?? h.status,
                    `${h.collectionCount} collection${h.collectionCount === 1 ? "" : "s"}`,
                    `driver counted ${money(h.declaredAmount, h.currency)}`,
                    h.receivedAmount != null ? `received ${money(h.receivedAmount, h.currency)}` : null,
                    h.variance != null && Math.abs(h.variance) > 0.009 ? `difference ${money(h.variance, h.currency)}` : null,
                    new Date(h.submittedAt).toLocaleString(),
                  ]
                    .filter(Boolean)
                    .join(" · ")}
                </span>
                {h.driverNotes ? (
                  <>
                    <br />
                    <span>Driver says: “{h.driverNotes}”</span>
                  </>
                ) : null}
                {h.status === "submitted" ? (
                  <div className={styles.formActions} style={{ marginTop: "0.45rem", flexWrap: "wrap" }}>
                    <input
                      className={styles.input}
                      style={{ flex: "0 1 140px" }}
                      inputMode="decimal"
                      aria-label="Amount counted"
                      value={d.counted}
                      onChange={(e) => setDraft(h.id, { counted: e.target.value }, h)}
                    />
                    {differs ? (
                      <select
                        className={styles.input}
                        style={{ flex: "1 1 200px" }}
                        aria-label="Reason for the difference"
                        value={d.reason}
                        onChange={(e) => setDraft(h.id, { reason: e.target.value }, h)}
                      >
                        <option value="">Reason for the difference…</option>
                        {reasons.map((r) => (
                          <option key={r.code} value={r.code}>
                            {r.label}
                          </option>
                        ))}
                      </select>
                    ) : null}
                    <input
                      className={styles.input}
                      style={{ flex: "1 1 200px" }}
                      placeholder="Note (optional)"
                      aria-label="Note"
                      value={d.notes}
                      onChange={(e) => setDraft(h.id, { notes: e.target.value }, h)}
                    />
                    <button type="button" className={styles.btn} disabled={busyId === h.id} onClick={() => void receive(h)}>
                      {differs ? "Receive with difference" : "Receive — matches"}
                    </button>
                  </div>
                ) : h.status === "variance_pending" ? (
                  <>
                    <br />
                    <span className={styles.muted}>
                      Counted by {h.receivedByName ?? "staff"} · {reasonLabel(h.reasonCode)}
                      {h.receiverNotes ? ` — “${h.receiverNotes}”` : ""}
                    </span>
                    <div className={styles.formActions} style={{ marginTop: "0.45rem", flexWrap: "wrap" }}>
                      <select
                        className={styles.input}
                        style={{ flex: "1 1 200px" }}
                        aria-label="Reason"
                        value={d.reason || h.reasonCode || ""}
                        onChange={(e) => setDraft(h.id, { reason: e.target.value }, h)}
                      >
                        {reasons.map((r) => (
                          <option key={r.code} value={r.code}>
                            {r.label}
                          </option>
                        ))}
                      </select>
                      <input
                        className={styles.input}
                        style={{ flex: "1 1 200px" }}
                        placeholder="Manager note (e.g. how it will be recovered)"
                        aria-label="Manager note"
                        value={d.notes}
                        onChange={(e) => setDraft(h.id, { notes: e.target.value }, h)}
                      />
                      <button type="button" className={styles.btn} disabled={busyId === h.id} onClick={() => void approve(h)}>
                        Sign off difference
                      </button>
                    </div>
                  </>
                ) : (
                  <>
                    <br />
                    <span className={styles.muted}>
                      {[
                        h.receivedByName ? `Counted by ${h.receivedByName}` : null,
                        h.approvedByName ? `signed off by ${h.approvedByName}` : null,
                        h.reasonCode ? reasonLabel(h.reasonCode) : null,
                        h.approverNotes,
                      ]
                        .filter(Boolean)
                        .join(" · ")}
                    </span>
                  </>
                )}
              </li>
            );
          })}
        </ul>
      )}

      {message ? (
        <p className={styles.lede} role="status">
          {message}
        </p>
      ) : null}
    </div>
  );
}
