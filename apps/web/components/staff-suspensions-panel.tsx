"use client";

import { useCallback, useEffect, useState } from "react";
import styles from "@/components/account.module.css";
import { searchCustomers, type CustomerOption } from "@/lib/staff-credit";
import { liftSuspension, listSuspensions, suspendCustomer, type CustomerSuspension } from "@/lib/staff-suspensions";
import { createWebClient } from "@/lib/supabase";
import { owedText } from "@/lib/money-owed";

const RULE: Record<string, string> = {
  on_account_overdue: "Balance left on account at delivery, unpaid after 7 days",
  invoice_overdue: "Invoice unpaid 30 days after it was issued",
  refused_cod: "Pay-on-delivery delivery refused or customer absent",
};

function money(amount: number, currency = "USD") {
  return `${currency} ${amount.toFixed(2)}`;
}

function day(iso: string | null) {
  if (!iso) return "";
  const d = new Date(iso);
  return Number.isNaN(d.getTime()) ? iso : d.toLocaleDateString();
}

/**
 * Customers suspended for failing to settle: no credit (pay on delivery, balance on account, sales on
 * account, holds and back-orders) until a manager lifts it. Upfront payment still works.
 */
export function StaffSuspensionsPanel() {
  const [show, setShow] = useState<string | null>("active");
  const [rows, setRows] = useState<CustomerSuspension[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [busy, setBusy] = useState<string | null>(null);
  const [liftReason, setLiftReason] = useState<Record<string, string>>({});
  const [query, setQuery] = useState("");
  const [hits, setHits] = useState<CustomerOption[]>([]);
  const [pick, setPick] = useState<CustomerOption | null>(null);
  const [suspendReason, setSuspendReason] = useState("");

  const load = useCallback(async () => {
    const client = createWebClient();
    if (!client) return;
    const res = await listSuspensions(client, show);
    if (res.ok) {
      setRows(res.data);
      setError(null);
    } else setError(res.error);
  }, [show]);

  useEffect(() => {
    void load();
  }, [load]);

  useEffect(() => {
    const q = query.trim();
    if (q.length < 2 || pick) {
      setHits([]);
      return;
    }
    const t = setTimeout(async () => {
      const client = createWebClient();
      if (!client) return;
      const res = await searchCustomers(client, q);
      if (res.ok) setHits(res.data.slice(0, 8));
    }, 250);
    return () => clearTimeout(t);
  }, [query, pick]);

  async function lift(s: CustomerSuspension) {
    const client = createWebClient();
    const reason = liftReason[s.id] ?? "";
    if (!client) return;
    if (!reason.trim()) {
      setMessage("Say why the suspension is lifted.");
      return;
    }
    setBusy(s.id);
    setMessage(null);
    const res = await liftSuspension(client, s.id, reason);
    setBusy(null);
    if (!res.ok) {
      setMessage(/manager/i.test(res.error) ? "Only a manager (POS approver) or an admin can lift a suspension." : res.error);
      return;
    }
    setMessage(`${s.customerName ?? "Customer"} can use credit again. What they owed now is accepted; new overdue debts suspend them again.`);
    void load();
  }

  async function suspend() {
    const client = createWebClient();
    if (!client || !pick) return;
    if (!suspendReason.trim()) {
      setMessage("Say why the customer is suspended.");
      return;
    }
    setBusy("suspend");
    setMessage(null);
    const res = await suspendCustomer(client, pick.id, suspendReason);
    setBusy(null);
    if (!res.ok) {
      setMessage(/admin, finance or manager/i.test(res.error) ? "Only admin, finance or a manager can suspend a customer." : res.error);
      return;
    }
    setMessage(`${pick.display_name} is suspended.`);
    setPick(null);
    setQuery("");
    setSuspendReason("");
    void load();
  }

  return (
    <section className={styles.form} aria-labelledby="suspensions-heading" style={{ marginTop: "2rem" }}>
      <h2 id="suspensions-heading" className={styles.legend}>
        Suspended customers
      </h2>
      <p className={styles.muted}>
        A customer is suspended automatically when a balance left on account at delivery is unpaid after 7 days, an invoice is unpaid
        30 days after it was issued, or 2 pay-on-delivery deliveries are refused within 90 days. While suspended they cannot buy on
        credit or pay on delivery, and holds and back-orders are refused; paying upfront and paying what they owe still work. Only a
        manager (POS approver) or an admin can lift a suspension.
      </p>

      <div className={styles.formActions} role="group" aria-label="Show">
        {[
          { label: "Suspended now", value: "active" },
          { label: "History", value: null },
        ].map((f) => (
          <button
            key={f.label}
            type="button"
            className={show === f.value ? styles.btn : styles.btnGhost}
            aria-pressed={show === f.value}
            onClick={() => setShow(f.value)}
          >
            {f.label}
          </button>
        ))}
      </div>

      {error ? (
        <p className={styles.lede} role="alert">
          {error}
        </p>
      ) : null}
      {rows?.length === 0 ? <p className={styles.muted}>{show === "active" ? "No customer is suspended." : "No suspensions yet."}</p> : null}
      {rows && rows.length > 0 ? (
        <ul className={styles.list}>
          {rows.map((s) => (
            <li key={s.id}>
              <strong>{s.customerName ?? s.customerId}</strong>
              {" · "}
              <span>{s.status === "active" ? "Suspended" : "Lifted"}</span>
              {" · owing "}
              {owedText(s.owed)}
              <br />
              <span className={styles.muted}>
                {s.reason} · {s.source === "automatic" ? "automatic" : `by ${s.suspendedByName ?? "staff"}`} · {day(s.suspendedAt)}
              </span>
              {s.findings.length > 0 ? (
                <ul className={styles.muted} style={{ margin: "0.3rem 0 0 1rem" }}>
                  {s.findings.map((f, i) => (
                    <li key={i}>
                      {f.label || RULE[f.rule] || f.rule} · {money(f.amount, f.currency)}
                      {f.since ? ` · since ${day(f.since)}` : ""}
                    </li>
                  ))}
                </ul>
              ) : null}
              {s.status === "active" ? (
                <div className={styles.formActions} style={{ marginTop: "0.45rem", flexWrap: "wrap" }}>
                  <input
                    className={styles.input}
                    style={{ flex: "1 1 260px" }}
                    placeholder="Why lift it? e.g. paid at the branch, payment plan agreed"
                    aria-label="Reason to lift"
                    value={liftReason[s.id] ?? ""}
                    onChange={(e) => setLiftReason((r) => ({ ...r, [s.id]: e.target.value }))}
                  />
                  <button type="button" className={styles.btn} disabled={busy === s.id} onClick={() => void lift(s)}>
                    Lift (manager)
                  </button>
                </div>
              ) : (
                <>
                  <br />
                  <span className={styles.muted}>
                    Lifted by {s.liftedByName ?? "a manager"} · {day(s.liftedAt)} · {s.liftReason}
                  </span>
                </>
              )}
            </li>
          ))}
        </ul>
      ) : null}

      <fieldset className={styles.fieldset}>
        <legend className={styles.legend}>Suspend a customer</legend>
        <label className={styles.field}>
          Customer
          <input
            className={styles.input}
            value={pick ? pick.display_name : query}
            placeholder="Search by name"
            onChange={(e) => {
              setPick(null);
              setQuery(e.target.value);
            }}
          />
        </label>
        {hits.length > 0 && !pick ? (
          <ul className={styles.list}>
            {hits.map((h) => (
              <li key={h.id}>
                <button type="button" className={styles.btnGhost} onClick={() => setPick(h)}>
                  {h.display_name}
                </button>
              </li>
            ))}
          </ul>
        ) : null}
        <label className={styles.field}>
          Reason
          <input className={styles.input} value={suspendReason} onChange={(e) => setSuspendReason(e.target.value)} placeholder="e.g. cheque returned unpaid" />
        </label>
        <div className={styles.formActions}>
          <button type="button" className={styles.btn} disabled={!pick || busy === "suspend"} onClick={() => void suspend()}>
            Suspend
          </button>
        </div>
      </fieldset>

      {message ? (
        <p className={styles.lede} role="status">
          {message}
        </p>
      ) : null}
    </section>
  );
}
