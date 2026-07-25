"use client";

import Link from "next/link";
import { FormEvent, useCallback, useEffect, useState } from "react";
import styles from "@/components/account.module.css";
import {
  addBankStatementLine,
  allocatePayment,
  cancelPaymentEntry,
  clearBankMatches,
  createAccountingPeriod,
  createJournalDraft,
  createPaymentEntry,
  importBankStatement,
  listAccountingPeriods,
  listBankReconMatches,
  listBankStatementLines,
  listBankStatements,
  listChartAccounts,
  listDraftPayments,
  listJournalEntries,
  listJournalLinesForAccount,
  lockAccountingPeriod,
  matchBankLine,
  postJournal,
  postPaymentEntry,
  reportBalanceSheet,
  reportCashFlow,
  reportProfitAndLoss,
  requireSession,
  reverseJournal,
  zigExchangeRate,
  type AccountOption,
  type AccountingPeriodOption,
  type BalanceSheetRow,
  type BankReconMatchOption,
  type BankStatementLineOption,
  type BankStatementOption,
  type CashFlowRow,
  type CurrencyCode,
  type JournalEntryOption,
  type JournalLineOption,
  type PaymentEntryOption,
  type PaymentTender,
  type PnLRow,
} from "@/lib/staff-finance";
import { createWebClient } from "@/lib/supabase";

type Boot =
  | { kind: "loading" }
  | { kind: "auth" }
  | { kind: "error"; message: string }
  | {
      kind: "ready";
      accounts: AccountOption[];
      journals: JournalEntryOption[];
      payments: PaymentEntryOption[];
      periods: AccountingPeriodOption[];
      statements: BankStatementOption[];
    };

type ReportKind = "pnl" | "bs" | "cf";

function todayInput(): string {
  return new Date().toISOString().slice(0, 10);
}

function monthStartInput(): string {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-01`;
}

export function StaffFinancePanel() {
  const [boot, setBoot] = useState<Boot>({ kind: "loading" });
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<string | null>(null);

  const [entryDate, setEntryDate] = useState(todayInput);
  const [description, setDescription] = useState("");
  const [currency, setCurrency] = useState<CurrencyCode>("USD");
  const [debitAccount, setDebitAccount] = useState("");
  const [creditAccount, setCreditAccount] = useState("");
  const [amount, setAmount] = useState("");

  const [reportKind, setReportKind] = useState<ReportKind>("pnl");
  const [from, setFrom] = useState(monthStartInput);
  const [to, setTo] = useState(todayInput);
  const [reportCurrency, setReportCurrency] = useState<CurrencyCode>("USD");
  const [pnlRows, setPnlRows] = useState<PnLRow[]>([]);
  const [bsRows, setBsRows] = useState<BalanceSheetRow[]>([]);
  const [cfRows, setCfRows] = useState<CashFlowRow[]>([]);

  const [customerId, setCustomerId] = useState("");
  const [payAmount, setPayAmount] = useState("");
  const [payCurrency, setPayCurrency] = useState<CurrencyCode>("USD");
  const [tender, setTender] = useState<PaymentTender>("cash");
  const [allocPaymentId, setAllocPaymentId] = useState("");
  const [allocInvoiceId, setAllocInvoiceId] = useState("");
  const [allocAmount, setAllocAmount] = useState("");

  const [reverseReason, setReverseReason] = useState("");

  const [periodStart, setPeriodStart] = useState(monthStartInput);
  const [periodEnd, setPeriodEnd] = useState(todayInput);
  const [periodLabel, setPeriodLabel] = useState("");

  const [stmtAccount, setStmtAccount] = useState("1100");
  const [stmtCurrency, setStmtCurrency] = useState<CurrencyCode>("USD");
  const [stmtDate, setStmtDate] = useState(todayInput);
  const [stmtOpen, setStmtOpen] = useState("0");
  const [stmtClose, setStmtClose] = useState("0");
  const [stmtDoc, setStmtDoc] = useState("");
  const [stmtLineDate, setStmtLineDate] = useState(todayInput);
  const [stmtLineDesc, setStmtLineDesc] = useState("");
  const [stmtLineAmount, setStmtLineAmount] = useState("");
  const [selectedStmtId, setSelectedStmtId] = useState("");
  const [stmtLines, setStmtLines] = useState<BankStatementLineOption[]>([]);
  const [stmtMatches, setStmtMatches] = useState<BankReconMatchOption[]>([]);
  const [jeLines, setJeLines] = useState<JournalLineOption[]>([]);
  const [matchLineId, setMatchLineId] = useState("");
  const [matchJeLineId, setMatchJeLineId] = useState("");
  const [addLineAmount, setAddLineAmount] = useState("");
  const [addLineDesc, setAddLineDesc] = useState("");
  const [addLineDate, setAddLineDate] = useState(todayInput);

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

    const [accounts, journals, payments, periods, statements] =
      await Promise.all([
        listChartAccounts(client),
        listJournalEntries(client),
        listDraftPayments(client),
        listAccountingPeriods(client),
        listBankStatements(client),
      ]);
    if (!accounts.ok) {
      setBoot({ kind: "error", message: accounts.error });
      return;
    }
    if (!journals.ok) {
      setBoot({ kind: "error", message: journals.error });
      return;
    }
    if (!payments.ok) {
      setBoot({ kind: "error", message: payments.error });
      return;
    }
    if (!periods.ok) {
      setBoot({ kind: "error", message: periods.error });
      return;
    }
    if (!statements.ok) {
      setBoot({ kind: "error", message: statements.error });
      return;
    }

    setBoot({
      kind: "ready",
      accounts: accounts.data,
      journals: journals.data,
      payments: payments.data,
      periods: periods.data,
      statements: statements.data,
    });
    setDebitAccount((prev) => prev || accounts.data[0]?.code || "");
    setCreditAccount(
      (prev) => prev || accounts.data[1]?.code || accounts.data[0]?.code || "",
    );
    setAllocPaymentId((prev) => prev || payments.data[0]?.id || "");
    setStmtAccount((prev) => prev || accounts.data[0]?.code || "1100");
    setSelectedStmtId((prev) => prev || statements.data[0]?.id || "");
  }, []);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  async function onCreateDraft(e: FormEvent) {
    e.preventDefault();
    const client = createWebClient();
    if (!client) return;
    const n = Number(amount);
    if (!Number.isFinite(n) || n <= 0) {
      setMessage("Amount must be a positive number.");
      return;
    }
    if (!debitAccount || !creditAccount || debitAccount === creditAccount) {
      setMessage("Choose distinct debit and credit accounts.");
      return;
    }
    setBusy(true);
    setMessage(null);
    const res = await createJournalDraft(client, {
      entryDate,
      description: description || "Staff journal draft",
      currency,
      exchangeRate: currency === "ZIG" ? zigExchangeRate() : 1,
      lines: [
        {
          account_code: debitAccount,
          debit: n,
          credit: 0,
          currency,
        },
        {
          account_code: creditAccount,
          debit: 0,
          credit: n,
          currency,
        },
      ],
    });
    setBusy(false);
    if (!res.ok) {
      setMessage(res.error);
      return;
    }
    setMessage(`Draft journal ${res.data.slice(0, 8)}… · ${currency}`);
    setAmount("");
    await refresh();
  }

  async function onPostJournal(id: string) {
    const client = createWebClient();
    if (!client) return;
    setBusy(true);
    setMessage(null);
    const res = await postJournal(client, id);
    setBusy(false);
    if (!res.ok) {
      setMessage(res.error);
      return;
    }
    setMessage(`Posted journal ${res.data.slice(0, 8)}…`);
    await refresh();
  }

  async function onRunReport(e: FormEvent) {
    e.preventDefault();
    const client = createWebClient();
    if (!client) return;
    setBusy(true);
    setMessage(null);
    setPnlRows([]);
    setBsRows([]);
    setCfRows([]);

    if (reportKind === "pnl") {
      const res = await reportProfitAndLoss(client, {
        from,
        to,
        currency: reportCurrency,
      });
      setBusy(false);
      if (!res.ok) {
        setMessage(res.error);
        return;
      }
      setPnlRows(res.data);
      setMessage(`P&L · ${res.data.length} rows · report currency ${reportCurrency}`);
      return;
    }
    if (reportKind === "bs") {
      const res = await reportBalanceSheet(client, {
        asOf: to,
        currency: reportCurrency,
      });
      setBusy(false);
      if (!res.ok) {
        setMessage(res.error);
        return;
      }
      setBsRows(res.data);
      setMessage(`Balance sheet · ${res.data.length} rows · ${reportCurrency}`);
      return;
    }
    const res = await reportCashFlow(client, {
      from,
      to,
      currency: reportCurrency,
    });
    setBusy(false);
    if (!res.ok) {
      setMessage(res.error);
      return;
    }
    setCfRows(res.data);
    setMessage(`Cash flow · ${res.data.length} rows · amounts in USD`);
  }

  async function onCreatePayment(e: FormEvent) {
    e.preventDefault();
    const client = createWebClient();
    if (!client) return;
    const n = Number(payAmount);
    if (!customerId.trim()) {
      setMessage("Customer id required.");
      return;
    }
    if (!Number.isFinite(n) || n <= 0) {
      setMessage("Payment amount must be positive.");
      return;
    }
    setBusy(true);
    setMessage(null);
    const res = await createPaymentEntry(client, {
      customerId: customerId.trim(),
      amount: n,
      currency: payCurrency,
      tender,
      exchangeRate: payCurrency === "ZIG" ? zigExchangeRate() : 1,
    });
    setBusy(false);
    if (!res.ok) {
      setMessage(res.error);
      return;
    }
    setMessage(`Payment draft ${res.data.slice(0, 8)}… · ${payCurrency}`);
    setPayAmount("");
    await refresh();
  }

  async function onAllocate(e: FormEvent) {
    e.preventDefault();
    const client = createWebClient();
    if (!client || !allocPaymentId) return;
    const n = Number(allocAmount);
    if (!allocInvoiceId.trim() || !Number.isFinite(n) || n <= 0) {
      setMessage("Invoice id and positive amount required.");
      return;
    }
    setBusy(true);
    setMessage(null);
    const res = await allocatePayment(client, {
      paymentEntryId: allocPaymentId,
      allocations: [
        { sales_invoice_id: allocInvoiceId.trim(), amount: n },
      ],
    });
    setBusy(false);
    if (!res.ok) {
      setMessage(res.error);
      return;
    }
    setMessage(`Allocated on payment ${res.data.slice(0, 8)}…`);
  }

  async function onPostPayment(id: string) {
    const client = createWebClient();
    if (!client) return;
    setBusy(true);
    setMessage(null);
    const res = await postPaymentEntry(client, id);
    setBusy(false);
    if (!res.ok) {
      setMessage(res.error);
      return;
    }
    setMessage(`Posted payment ${res.data.slice(0, 8)}…`);
    await refresh();
  }

  async function onCancelPayment(id: string) {
    const client = createWebClient();
    if (!client) return;
    setBusy(true);
    setMessage(null);
    const res = await cancelPaymentEntry(client, id);
    setBusy(false);
    if (!res.ok) {
      setMessage(res.error);
      return;
    }
    setMessage(`Cancelled payment ${res.data.slice(0, 8)}…`);
    await refresh();
  }

  if (boot.kind === "loading") {
    return <p className={styles.muted}>Loading finance…</p>;
  }
  if (boot.kind === "auth") {
    return (
      <p className={styles.lede}>
        <Link href="/login">Sign in</Link> with finance/admin staff.
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

  const drafts = boot.journals.filter((j) => j.status === "draft");

  return (
    <div className={styles.form}>
      {message ? (
        <p className={styles.formStatus} role="status">
          {message}
        </p>
      ) : null}

      <fieldset className={styles.fieldset}>
        <legend className={styles.legend}>1 · Journal draft</legend>
        <p className={styles.muted} style={{ marginBottom: "0.75rem" }}>
          Balanced two-line draft via <code>create_journal_draft</code>, then{" "}
          <code>post_journal</code>. Amounts show explicit <code>USD</code> |{" "}
          <code>ZIG</code>.
        </p>
        <form onSubmit={(e) => void onCreateDraft(e)}>
          <div className={styles.formGrid}>
            <label className={styles.field}>
              Entry date
              <input
                type="date"
                value={entryDate}
                onChange={(e) => setEntryDate(e.target.value)}
                disabled={busy}
              />
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
            <label className={styles.field} style={{ gridColumn: "1 / -1" }}>
              Description
              <input
                value={description}
                onChange={(e) => setDescription(e.target.value)}
                disabled={busy}
                placeholder="Staff journal"
              />
            </label>
            <label className={styles.field}>
              Debit account
              <select
                value={debitAccount}
                onChange={(e) => setDebitAccount(e.target.value)}
                disabled={busy}
              >
                {boot.accounts.map((a) => (
                  <option key={a.code} value={a.code}>
                    {a.code} — {a.name}
                  </option>
                ))}
              </select>
            </label>
            <label className={styles.field}>
              Credit account
              <select
                value={creditAccount}
                onChange={(e) => setCreditAccount(e.target.value)}
                disabled={busy}
              >
                {boot.accounts.map((a) => (
                  <option key={`c-${a.code}`} value={a.code}>
                    {a.code} — {a.name}
                  </option>
                ))}
              </select>
            </label>
            <label className={styles.field}>
              Amount ({currency})
              <input
                value={amount}
                onChange={(e) => setAmount(e.target.value)}
                disabled={busy}
                inputMode="decimal"
              />
            </label>
          </div>
          <div className={styles.formActions}>
            <button type="submit" className={styles.btnGhost} disabled={busy}>
              Create draft
            </button>
          </div>
        </form>

        {drafts.length ? (
          <ul className={styles.navList} style={{ marginTop: "1rem" }}>
            {drafts.map((j) => (
              <li key={j.id} className={styles.muted}>
                {j.document_number ?? j.id.slice(0, 8)} · {j.currency} ·{" "}
                {j.description ?? "—"}{" "}
                <button
                  type="button"
                  className={styles.btnGhost}
                  disabled={busy}
                  onClick={() => void onPostJournal(j.id)}
                >
                  Post
                </button>
              </li>
            ))}
          </ul>
        ) : (
          <p className={styles.muted}>No draft journals loaded.</p>
        )}
      </fieldset>

      <fieldset className={styles.fieldset}>
        <legend className={styles.legend}>2 · Reports</legend>
        <form onSubmit={(e) => void onRunReport(e)}>
          <div className={styles.formGrid}>
            <label className={styles.field}>
              Report
              <select
                value={reportKind}
                onChange={(e) => setReportKind(e.target.value as ReportKind)}
                disabled={busy}
              >
                <option value="pnl">Profit &amp; loss</option>
                <option value="bs">Balance sheet</option>
                <option value="cf">Cash flow</option>
              </select>
            </label>
            <label className={styles.field}>
              Currency
              <select
                value={reportCurrency}
                onChange={(e) =>
                  setReportCurrency(e.target.value as CurrencyCode)
                }
                disabled={busy}
              >
                <option value="USD">USD</option>
                <option value="ZIG">ZIG</option>
              </select>
            </label>
            <label className={styles.field}>
              From
              <input
                type="date"
                value={from}
                onChange={(e) => setFrom(e.target.value)}
                disabled={busy || reportKind === "bs"}
              />
            </label>
            <label className={styles.field}>
              {reportKind === "bs" ? "As of" : "To"}
              <input
                type="date"
                value={to}
                onChange={(e) => setTo(e.target.value)}
                disabled={busy}
              />
            </label>
          </div>
          <div className={styles.formActions}>
            <button type="submit" className={styles.btnGhost} disabled={busy}>
              Run report
            </button>
          </div>
        </form>

        {pnlRows.length ? (
          <div style={{ overflowX: "auto", marginTop: "0.75rem" }}>
            <table style={{ width: "100%", fontSize: "0.88rem" }}>
              <thead>
                <tr>
                  <th align="left">Account</th>
                  <th align="right">Amount ({reportCurrency})</th>
                  <th align="right">USD</th>
                </tr>
              </thead>
              <tbody>
                {pnlRows.map((r) => (
                  <tr key={`${r.account_code}-${r.account_type}`}>
                    <td>
                      {r.account_code} {r.account_name}
                    </td>
                    <td align="right">{Number(r.amount).toFixed(2)}</td>
                    <td align="right">{Number(r.amount_usd).toFixed(2)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : null}

        {bsRows.length ? (
          <div style={{ overflowX: "auto", marginTop: "0.75rem" }}>
            <table style={{ width: "100%", fontSize: "0.88rem" }}>
              <thead>
                <tr>
                  <th align="left">Account</th>
                  <th align="right">Balance ({reportCurrency})</th>
                  <th align="right">USD</th>
                </tr>
              </thead>
              <tbody>
                {bsRows.map((r) => (
                  <tr key={`${r.account_code}-bs`}>
                    <td>
                      {r.account_code} {r.account_name}
                    </td>
                    <td align="right">{Number(r.balance).toFixed(2)}</td>
                    <td align="right">{Number(r.balance_usd).toFixed(2)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : null}

        {cfRows.length ? (
          <div style={{ overflowX: "auto", marginTop: "0.75rem" }}>
            <table style={{ width: "100%", fontSize: "0.88rem" }}>
              <thead>
                <tr>
                  <th align="left">Section</th>
                  <th align="left">Label</th>
                  <th align="right">USD</th>
                </tr>
              </thead>
              <tbody>
                {cfRows.map((r, i) => (
                  <tr key={`${r.section}-${r.label}-${i}`}>
                    <td>{r.section}</td>
                    <td>{r.label}</td>
                    <td align="right">{Number(r.amount_usd).toFixed(2)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : null}
      </fieldset>

      <fieldset className={styles.fieldset}>
        <legend className={styles.legend}>3 · Payments</legend>
        <form onSubmit={(e) => void onCreatePayment(e)}>
          <div className={styles.formGrid}>
            <label className={styles.field}>
              Customer id
              <input
                value={customerId}
                onChange={(e) => setCustomerId(e.target.value)}
                disabled={busy}
                placeholder="uuid"
              />
            </label>
            <label className={styles.field}>
              Amount ({payCurrency})
              <input
                value={payAmount}
                onChange={(e) => setPayAmount(e.target.value)}
                disabled={busy}
                inputMode="decimal"
              />
            </label>
            <label className={styles.field}>
              Currency
              <select
                value={payCurrency}
                onChange={(e) => setPayCurrency(e.target.value as CurrencyCode)}
                disabled={busy}
              >
                <option value="USD">USD</option>
                <option value="ZIG">ZIG</option>
              </select>
            </label>
            <label className={styles.field}>
              Tender
              <select
                value={tender}
                onChange={(e) => setTender(e.target.value as PaymentTender)}
                disabled={busy}
              >
                <option value="cash">cash</option>
                <option value="bank">bank</option>
                <option value="contipay">contipay</option>
                <option value="paynow">paynow</option>
                <option value="store_credit">store_credit</option>
              </select>
            </label>
          </div>
          <div className={styles.formActions}>
            <button type="submit" className={styles.btnGhost} disabled={busy}>
              Create payment draft
            </button>
          </div>
        </form>

        <form
          onSubmit={(e) => void onAllocate(e)}
          style={{ marginTop: "0.85rem" }}
        >
          <div className={styles.formGrid}>
            <label className={styles.field}>
              Draft payment
              <select
                value={allocPaymentId}
                onChange={(e) => setAllocPaymentId(e.target.value)}
                disabled={busy}
              >
                {boot.payments.map((p) => (
                  <option key={p.id} value={p.id}>
                    {p.document_number ?? p.id.slice(0, 8)} · {p.amount}{" "}
                    {p.currency}
                  </option>
                ))}
              </select>
            </label>
            <label className={styles.field}>
              Invoice id
              <input
                value={allocInvoiceId}
                onChange={(e) => setAllocInvoiceId(e.target.value)}
                disabled={busy}
                placeholder="sales_invoice uuid"
              />
            </label>
            <label className={styles.field}>
              Allocate amount
              <input
                value={allocAmount}
                onChange={(e) => setAllocAmount(e.target.value)}
                disabled={busy}
                inputMode="decimal"
              />
            </label>
          </div>
          <div className={styles.formActions}>
            <button type="submit" className={styles.btnGhost} disabled={busy}>
              Allocate
            </button>
          </div>
        </form>

        {boot.payments.length ? (
          <ul className={styles.navList} style={{ marginTop: "1rem" }}>
            {boot.payments.map((p) => (
              <li key={p.id} className={styles.muted}>
                {p.document_number ?? p.id.slice(0, 8)} · {p.amount} {p.currency}{" "}
                · {p.tender}{" "}
                <button
                  type="button"
                  className={styles.btnGhost}
                  disabled={busy}
                  onClick={() => void onPostPayment(p.id)}
                >
                  Post
                </button>{" "}
                <button
                  type="button"
                  className={styles.btnGhost}
                  disabled={busy}
                  onClick={() => void onCancelPayment(p.id)}
                >
                  Cancel
                </button>
              </li>
            ))}
          </ul>
        ) : (
          <p className={styles.muted}>No draft payments.</p>
        )}
      </fieldset>
    </div>
  );
}
