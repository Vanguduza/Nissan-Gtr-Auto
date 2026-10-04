"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import styles from "@/components/account.module.css";
import {
  decideDeliveryBalance,
  deliveryBalanceChannel,
  listDeliveryBalanceApprovals,
  requireSession,
  type DeliveryBalanceApproval,
} from "@/lib/staff-delivery-balances";
import { createWebClient } from "@/lib/supabase";

type Boot =
  | { kind: "loading" }
  | { kind: "auth" }
  | { kind: "error"; message: string }
  | { kind: "ready"; rows: DeliveryBalanceApproval[] };

const FILTERS: { label: string; value: string | null }[] = [
  { label: "Waiting", value: "pending" },
  { label: "All", value: null },
];

const STATUS: Record<string, string> = {
  pending: "Waiting for a decision",
  auto_approved: "Approved — within credit limit",
  approved: "Approved",
  refused: "Refused",
  cancelled: "Replaced by a newer request",
};

function money(amount: number, currency: string) {
  return `${currency} ${amount.toFixed(2)}`;
}

/**
 * Dispatch decides when a cash/card-on-delivery customer paid part and cannot pay the rest: leave it
 * on their account (the driver completes) or refuse (the driver collects or brings the parts back).
 * Requests within the customer's credit limit are approved automatically and only listed here.
 */
export function StaffDeliveryBalancesPanel() {
  const [boot, setBoot] = useState<Boot>({ kind: "loading" });
  const [status, setStatus] = useState<string | null>("pending");
  const [notes, setNotes] = useState<Record<string, string>>({});
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
    const res = await listDeliveryBalanceApprovals(client, status);
    setBoot(res.ok ? { kind: "ready", rows: res.data } : { kind: "error", message: res.error });
  }, [status]);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  // A driver is waiting at the door: show new requests as they arrive.
  useEffect(() => {
    const client = createWebClient();
    if (!client) return;
    const channel = deliveryBalanceChannel(client, () => void refresh());
    return () => {
      void client.removeChannel(channel);
    };
  }, [refresh]);

  async function decide(row: DeliveryBalanceApproval, approve: boolean) {
    const client = createWebClient();
    if (!client) return;
    const note = notes[row.id] ?? "";
    if (!approve && !note.trim()) {
      setMessage("Say why it is refused — the driver sees it.");
      return;
    }
    setBusyId(row.id);
    setMessage(null);
    const res = await decideDeliveryBalance(client, row.id, approve, note);
    setBusyId(null);
    if (!res.ok) {
      setMessage(res.error);
      return;
    }
    setMessage(
      approve
        ? `${money(row.amount, row.currency)} left on ${row.customerName ?? "the customer"}'s account. The driver can complete.`
        : "Refused. The driver collects the balance or brings the parts back.",
    );
    await refresh();
  }

  if (boot.kind === "loading") return <p className={styles.muted}>Loading balance requests…</p>;
  if (boot.kind === "auth") {
    return (
      <p className={styles.lede} role="status">
        <Link href="/login">Sign in</Link> as a dispatcher or admin to decide balance requests.
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

      {boot.rows.length === 0 ? (
        <p className={styles.muted} role="status">
          {status === "pending" ? "No driver is waiting for a decision." : "No balance requests yet."}
        </p>
      ) : (
        <ul className={styles.list}>
          {boot.rows.map((r) => (
            <li key={r.id}>
              <strong>
                {money(r.amount, r.currency)} on account · {r.customerName ?? "Walk-in customer"}
              </strong>
              <br />
              <span className={styles.muted}>
                {[
                  STATUS[r.status] ?? r.status,
                  r.invoiceNumber ? `invoice ${r.invoiceNumber}` : null,
                  `paid ${money(r.amountPaid, r.currency)} of ${money(r.invoiceTotal, r.currency)}`,
                  r.jobNumber ? `job ${r.jobNumber}` : null,
                  r.requestedByName ? `driver ${r.requestedByName}` : null,
                  new Date(r.createdAt).toLocaleString(),
                ]
                  .filter(Boolean)
                  .join(" · ")}
              </span>
              <br />
              <span>Driver says: “{r.reason}”</span>
              {r.creditLimit != null ? (
                <>
                  <br />
                  <span className={styles.muted}>
                    {r.creditLimit > 0
                      ? `Credit limit ${money(r.creditLimit, r.currency)}; owing ${money(r.exposure ?? 0, r.currency)} including this invoice.`
                      : "No trade credit on this account."}
                  </span>
                </>
              ) : null}
              {r.status === "pending" ? (
                <div className={styles.formActions} style={{ marginTop: "0.45rem", flexWrap: "wrap" }}>
                  <input
                    className={styles.input}
                    style={{ flex: "1 1 240px" }}
                    placeholder="Note for the driver (required to refuse)"
                    aria-label="Decision note"
                    value={notes[r.id] ?? ""}
                    onChange={(e) => setNotes((n) => ({ ...n, [r.id]: e.target.value }))}
                  />
                  <button type="button" className={styles.btn} disabled={busyId === r.id} onClick={() => void decide(r, true)}>
                    Leave on account
                  </button>
                  <button type="button" className={styles.btnGhost} disabled={busyId === r.id} onClick={() => void decide(r, false)}>
                    Refuse
                  </button>
                </div>
              ) : r.decidedByName || r.decisionNote ? (
                <>
                  <br />
                  <span className={styles.muted}>
                    {[r.decidedByName ? `Decided by ${r.decidedByName}` : null, r.decisionNote].filter(Boolean).join(" — ")}
                  </span>
                </>
              ) : null}
            </li>
          ))}
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
