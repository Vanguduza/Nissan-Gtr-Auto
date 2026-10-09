"use client";

import Link from "next/link";
import { useCallback, useEffect, useMemo, useState } from "react";
import account from "@/components/account.module.css";
import stmt from "@/components/staff-petty-cash-statement.module.css";
import {
  buildStatementExportHook,
  downloadBrandedStatementPdf,
  reportAccountRegister,
  requireSession,
  type AccountRegisterRow,
  type CurrencyCode,
} from "@/lib/staff-finance";
import { createWebClient } from "@/lib/supabase";

const ACCOUNT_CODE = "1110";
const ACCOUNT_TITLE = "Petty cash";

function todayInput(): string {
  return new Date().toISOString().slice(0, 10);
}

function monthStartInput(): string {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-01`;
}

function formatMoney(n: number): string {
  return n.toLocaleString(undefined, {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  });
}

/**
 * Bank-statement columns (customer view): Debit = money out, Credit = money in.
 * Asset GL 1110 uses accounting debit=in / credit=out — swap for display.
 */
function bankDebit(row: AccountRegisterRow): number {
  return Number(row.credit);
}

function bankCredit(row: AccountRegisterRow): number {
  return Number(row.debit);
}

type Boot =
  | { kind: "loading" }
  | { kind: "auth" }
  | { kind: "error"; message: string }
  | { kind: "ready" };

export function StaffPettyCashStatement() {
  const [boot, setBoot] = useState<Boot>({ kind: "loading" });
  const [from, setFrom] = useState(monthStartInput);
  const [to, setTo] = useState(todayInput);
  const [currency, setCurrency] = useState<CurrencyCode>("USD");
  const [rows, setRows] = useState<AccountRegisterRow[]>([]);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<string | null>(null);

  const cashInHand = useMemo(() => {
    if (rows.length === 0) return null;
    return Number(rows[rows.length - 1]?.running_balance ?? 0);
  }, [rows]);

  const load = useCallback(async () => {
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
    setBoot({ kind: "ready" });
    if (!from || !to) {
      setMessage("Choose a from and to date.");
      return;
    }
    if (from > to) {
      setMessage("From date must be on or before to date.");
      return;
    }
    setBusy(true);
    setMessage(null);
    const res = await reportAccountRegister(client, {
      accountCode: ACCOUNT_CODE,
      from,
      to,
      currency,
    });
    setBusy(false);
    if (!res.ok) {
      setMessage(res.error);
      setRows([]);
      return;
    }
    setRows(res.data);
  }, [currency, from, to]);

  useEffect(() => {
    void load();
  }, [load]);

  async function onDownloadPdf() {
    const client = createWebClient();
    if (!client) {
      setMessage("Supabase is not configured.");
      return;
    }
    if (rows.length === 0) {
      setMessage("No transactions in this period to export.");
      return;
    }
    const session = await client.auth.getSession();
    const token = session.data.session?.access_token;
    if (!token) {
      setMessage("Sign in required for PDF export.");
      return;
    }
    const payload = buildStatementExportHook({
      storeName: "Nissan GTR Auto",
      documentLabel: `Petty cash statement · ${from} → ${to}`,
      currency,
      asOf: to,
      partyName: "Petty cash float (1110)",
      lines: rows.map((r) => {
        const out = bankDebit(r);
        const inn = bankCredit(r);
        const signed = inn - out;
        return {
          description: [
            r.entry_date,
            r.document_number,
            r.description?.trim() || r.journal_entry_id.slice(0, 8),
            out > 0 ? `Dr ${formatMoney(out)}` : null,
            inn > 0 ? `Cr ${formatMoney(inn)}` : null,
          ]
            .filter(Boolean)
            .join(" · "),
          lineTotal: signed,
        };
      }),
      closingBalance: cashInHand ?? undefined,
    });
    setBusy(true);
    const pdf = await downloadBrandedStatementPdf(token, payload);
    setBusy(false);
    setMessage(
      pdf.ok
        ? `PDF downloaded · ${payload.lines.length} transaction(s)`
        : pdf.error,
    );
  }

  if (boot.kind === "loading") {
    return <p className={account.emptyState}>Loading petty cash…</p>;
  }
  if (boot.kind === "auth") {
    return (
      <p className={account.lede}>
        <Link href="/login?next=/staff/finance?tab=petty-cash">Sign in</Link>{" "}
        with finance/admin staff.
      </p>
    );
  }
  if (boot.kind === "error") {
    return (
      <p className={account.lede} role="alert">
        {boot.message}
      </p>
    );
  }

  return (
    <div className={account.form}>
      {message ? (
        <p className={account.formStatus} role="status">
          {message}
        </p>
      ) : null}

      <div className={stmt.statement}>
        <header className={stmt.header}>
          <div className={stmt.brandBlock}>
            <p className={stmt.brandName}>Nissan GTR Auto</p>
            <p className={stmt.brandMeta}>
              Operational float · account {ACCOUNT_CODE}
              <br />
              Credits = funds approved into the box · Debits = expenses paid
            </p>
          </div>
          <aside className={stmt.summaryCard} aria-label="Statement summary">
            <p className={stmt.summaryTitle}>Your statement</p>
            <div className={stmt.summaryRow}>
              <span className={stmt.summaryLabel}>Account</span>
              <span className={stmt.summaryValue}>{ACCOUNT_CODE}</span>
            </div>
            <div className={stmt.summaryRow}>
              <span className={stmt.summaryLabel}>Period</span>
              <span className={stmt.summaryValue}>
                {from} – {to}
              </span>
            </div>
            <div className={stmt.summaryRow}>
              <span className={stmt.summaryLabel}>Currency</span>
              <span className={stmt.summaryValue}>{currency}</span>
            </div>
            <div className={`${stmt.summaryRow} ${stmt.summaryBalance}`}>
              <span className={stmt.summaryLabel}>Cash in hand</span>
              <span className={stmt.summaryValue}>
                {cashInHand == null
                  ? "—"
                  : `${formatMoney(cashInHand)} ${currency}`}
              </span>
            </div>
          </aside>
        </header>

        <h2 className={stmt.accountTitle}>{ACCOUNT_TITLE}</h2>
        <p className={stmt.accountNote}>
          Record operational spends and request float top-ups. Approval follows
          the finance requisition workflow; petty cash and float requests
          disburse to the ledger automatically on final approval.
        </p>

        <div className={stmt.toolbar}>
          <div className={stmt.filters}>
            <label className={account.field}>
              From
              <input
                type="date"
                value={from}
                onChange={(e) => setFrom(e.target.value)}
                disabled={busy}
              />
            </label>
            <label className={account.field}>
              To
              <input
                type="date"
                value={to}
                onChange={(e) => setTo(e.target.value)}
                disabled={busy}
              />
            </label>
            <label className={account.field}>
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
          </div>
          <div className={stmt.actions}>
            <Link
              href="/staff/finance/petty-cash/expense"
              className={account.btnGhost}
            >
              Add expense
            </Link>
            <Link
              href="/staff/finance/petty-cash/request"
              className={account.btnGhost}
            >
              Request funds
            </Link>
            <button
              type="button"
              className={account.btn}
              style={{ marginTop: 0 }}
              disabled={busy || rows.length === 0}
              onClick={() => void onDownloadPdf()}
            >
              Generate PDF
            </button>
          </div>
        </div>

        {rows.length === 0 ? (
          <p className={stmt.empty}>
            No posted activity in this period. Request funds to top up the box,
            or add an expense once float is available.
          </p>
        ) : (
          <div className={stmt.tableWrap}>
            <table className={stmt.table}>
              <thead>
                <tr>
                  <th>Date</th>
                  <th>Transaction</th>
                  <th className={stmt.num}>Debit</th>
                  <th className={stmt.num}>Credit</th>
                  <th className={stmt.num}>Balance</th>
                </tr>
              </thead>
              <tbody>
                {rows.map((r) => {
                  const out = bankDebit(r);
                  const inn = bankCredit(r);
                  const label =
                    [r.document_number, r.description?.trim() || null]
                      .filter(Boolean)
                      .join(" · ") || r.journal_entry_id;
                  return (
                    <tr
                      key={`${r.journal_entry_id}-${r.entry_date}-${r.debit}-${r.credit}`}
                    >
                      <td>{r.entry_date}</td>
                      <td>
                        <Link
                          href={`/staff/finance/transactions/${r.journal_entry_id}?tab=petty-cash`}
                          className={stmt.txnLink}
                        >
                          {label}
                        </Link>
                      </td>
                      <td className={stmt.num}>
                        {out > 0 ? formatMoney(out) : ""}
                      </td>
                      <td className={stmt.num}>
                        {inn > 0 ? formatMoney(inn) : ""}
                      </td>
                      <td className={stmt.num}>
                        {formatMoney(Number(r.running_balance))}{" "}
                        {r.currency}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
}
