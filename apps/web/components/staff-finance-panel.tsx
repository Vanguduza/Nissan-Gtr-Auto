"use client";

import Link from "next/link";
import { FormEvent, useCallback, useEffect, useState } from "react";
import styles from "@/components/account.module.css";
import { StaffModuleTabs, type StaffModuleTab } from "@/components/staff-module-tabs";
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
  searchCustomers,
  zigExchangeRate,
  type AccountOption,
  type AccountingPeriodOption,
  type BalanceSheetRow,
  type BankReconMatchOption,
  type BankStatementLineOption,
  type BankStatementOption,
  type CashFlowRow,
  type CurrencyCode,
  type CustomerOption,
  type JournalEntryOption,
  type JournalLineOption,
  type PaymentEntryOption,
  type PaymentTender,
  type PnLRow,
} from "@/lib/staff-finance";
import { createWebClient } from "@/lib/supabase";

const FINANCE_TABS: StaffModuleTab[] = [
  { id: "petty-cash", label: "Petty cash" },
  { id: "cash-sales", label: "Cash sales" },
  { id: "online-sales", label: "Online sales" },
  { id: "journals", label: "Journals" },
  { id: "payments", label: "Payments" },
  { id: "reports", label: "Reports" },
  { id: "bank-recon", label: "Bank recon" },
  { id: "periods", label: "Periods" },
];

const ACCOUNT_TAB_CODES: Record<string, { code: string; title: string }> = {
  "petty-cash": { code: "1110", title: "Petty Cash (1110)" },
  "cash-sales": { code: "1120", title: "Cash Sales Till (1120)" },
  "online-sales": { code: "1130", title: "Online Payment Clearing (1130)" },
};

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
  const [tab, setTab] = useState("journals");
  const [accountLines, setAccountLines] = useState<JournalLineOption[]>([]);
  const [boot, setBoot] = useState<Boot>({ kind: "loading" });
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<string | null>(null);

  useEffect(() => {
    if (typeof window === "undefined") return;
    const t = new URLSearchParams(window.location.search).get("tab");
    if (t && FINANCE_TABS.some((x) => x.id === t)) setTab(t);
  }, []);

  function selectTab(id: string) {
    setTab(id);
    if (typeof window === "undefined") return;
    const url = new URL(window.location.href);
    url.searchParams.set("tab", id);
    window.history.replaceState({}, "", url);
  }

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
  const [customerQuery, setCustomerQuery] = useState("");
  const [customerHits, setCustomerHits] = useState<CustomerOption[]>([]);
  const [customerLabel, setCustomerLabel] = useState("");
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
  const [selectedStmtAccount, setSelectedStmtAccount] = useState("1100");
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
    setSelectedStmtAccount((prev) => {
      if (prev && prev !== "1100") return prev;
      const first = statements.data[0];
      return first?.account_code || accounts.data[0]?.code || "1100";
    });
  }, []);

  const loadStatementDetail = useCallback(
    async (statementId: string, accountCode?: string) => {
      const client = createWebClient();
      if (!client || !statementId) {
        setStmtLines([]);
        setStmtMatches([]);
        setJeLines([]);
        return;
      }
      const linesRes = await listBankStatementLines(client, statementId);
      if (!linesRes.ok) {
        setMessage(linesRes.error);
        setStmtLines([]);
        setStmtMatches([]);
        return;
      }
      setStmtLines(linesRes.data);
      setMatchLineId((prev) => {
        const open = linesRes.data.find((l) => l.status === "open");
        return prev && linesRes.data.some((l) => l.id === prev)
          ? prev
          : open?.id || "";
      });

      const matchRes = await listBankReconMatches(
        client,
        linesRes.data.map((l) => l.id),
      );
      if (!matchRes.ok) {
        setMessage(matchRes.error);
        setStmtMatches([]);
      } else {
        setStmtMatches(matchRes.data);
      }

      if (accountCode) {
        const jeRes = await listJournalLinesForAccount(client, accountCode);
        if (jeRes.ok) {
          setJeLines(jeRes.data);
          setMatchJeLineId((prev) => prev || jeRes.data[0]?.id || "");
        }
      }
    },
    [],
  );

  useEffect(() => {
    void refresh();
  }, [refresh]);

  useEffect(() => {
    const meta = ACCOUNT_TAB_CODES[tab];
    if (!meta || boot.kind !== "ready") {
      setAccountLines([]);
      return;
    }
    void (async () => {
      const client = createWebClient();
      if (!client) return;
      const res = await listJournalLinesForAccount(client, meta.code);
      if (res.ok) setAccountLines(res.data);
      else setMessage(res.error);
    })();
  }, [tab, boot.kind]);

  useEffect(() => {
    if (boot.kind !== "ready") return;
    const q = customerQuery.trim();
    if (q.length < 2) {
      setCustomerHits([]);
      return;
    }
    const t = window.setTimeout(() => {
      void (async () => {
        const client = createWebClient();
        if (!client) return;
        const res = await searchCustomers(client, q);
        if (!res.ok) {
          setMessage(res.error);
          setCustomerHits([]);
          return;
        }
        setCustomerHits(res.data);
      })();
    }, 250);
    return () => window.clearTimeout(t);
  }, [boot.kind, customerQuery]);

  useEffect(() => {
    if (boot.kind !== "ready" || !selectedStmtId) return;
    const stmt = boot.statements.find((s) => s.id === selectedStmtId);
    if (stmt) setSelectedStmtAccount(stmt.account_code);
    void loadStatementDetail(selectedStmtId, stmt?.account_code);
  }, [boot, selectedStmtId, loadStatementDetail]);

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

  async function onReverseJournal(id: string) {
    const client = createWebClient();
    if (!client) return;
    setBusy(true);
    setMessage(null);
    const res = await reverseJournal(client, {
      entryId: id,
      reason: reverseReason.trim() || undefined,
    });
    setBusy(false);
    if (!res.ok) {
      setMessage(res.error);
      return;
    }
    setMessage(`Reversal posted ${res.data.slice(0, 8)}…`);
    setReverseReason("");
    await refresh();
  }

  async function onCreatePeriod(e: FormEvent) {
    e.preventDefault();
    const client = createWebClient();
    if (!client) return;
    if (!periodLabel.trim()) {
      setMessage("Period label required.");
      return;
    }
    setBusy(true);
    setMessage(null);
    const res = await createAccountingPeriod(client, {
      periodStart,
      periodEnd,
      label: periodLabel.trim(),
    });
    setBusy(false);
    if (!res.ok) {
      setMessage(res.error);
      return;
    }
    setMessage(`Period ${res.data.slice(0, 8)}… created (open until locked).`);
    setPeriodLabel("");
    await refresh();
  }

  async function onLockPeriod(id: string) {
    const client = createWebClient();
    if (!client) return;
    setBusy(true);
    setMessage(null);
    const res = await lockAccountingPeriod(client, id);
    setBusy(false);
    if (!res.ok) {
      setMessage(res.error);
      return;
    }
    setMessage(`Period ${id.slice(0, 8)}… locked.`);
    await refresh();
  }

  async function onImportStatement(e: FormEvent) {
    e.preventDefault();
    const client = createWebClient();
    if (!client) return;
    const openBal = Number(stmtOpen);
    const closeBal = Number(stmtClose);
    const lineAmt = stmtLineAmount.trim() ? Number(stmtLineAmount) : null;
    if (!Number.isFinite(openBal) || !Number.isFinite(closeBal)) {
      setMessage("Opening/closing balances must be numbers.");
      return;
    }
    if (lineAmt !== null && !Number.isFinite(lineAmt)) {
      setMessage("Statement line amount must be a number.");
      return;
    }
    setBusy(true);
    setMessage(null);
    const res = await importBankStatement(client, {
      accountCode: stmtAccount,
      currency: stmtCurrency,
      statementDate: stmtDate,
      openingBalance: openBal,
      closingBalance: closeBal,
      documentNumber: stmtDoc.trim() || undefined,
      line:
        lineAmt !== null
          ? {
              lineDate: stmtLineDate,
              description: stmtLineDesc,
              amount: lineAmt,
            }
          : undefined,
    });
    setBusy(false);
    if (!res.ok) {
      setMessage(res.error);
      return;
    }
    setMessage(`Statement ${res.data.slice(0, 8)}… · ${stmtCurrency}`);
    setStmtLineAmount("");
    setStmtLineDesc("");
    setSelectedStmtId(res.data);
    setSelectedStmtAccount(stmtAccount);
    await refresh();
    await loadStatementDetail(res.data, stmtAccount);
  }

  async function onAddStmtLine(e: FormEvent) {
    e.preventDefault();
    const client = createWebClient();
    if (!client || !selectedStmtId) return;
    const n = Number(addLineAmount);
    if (!Number.isFinite(n)) {
      setMessage("Line amount required.");
      return;
    }
    setBusy(true);
    setMessage(null);
    const res = await addBankStatementLine(client, {
      statementId: selectedStmtId,
      lineDate: addLineDate,
      description: addLineDesc,
      amount: n,
    });
    setBusy(false);
    if (!res.ok) {
      setMessage(res.error);
      return;
    }
    setMessage(`Line ${res.data.slice(0, 8)}… added.`);
    setAddLineAmount("");
    setAddLineDesc("");
    await loadStatementDetail(selectedStmtId, selectedStmtAccount);
  }

  async function onMatchLine(e: FormEvent) {
    e.preventDefault();
    const client = createWebClient();
    if (!client || !matchLineId || !matchJeLineId) {
      setMessage("Select statement line and journal entry line.");
      return;
    }
    setBusy(true);
    setMessage(null);
    const res = await matchBankLine(client, {
      statementLineId: matchLineId,
      journalEntryLineId: matchJeLineId,
    });
    setBusy(false);
    if (!res.ok) {
      setMessage(res.error);
      return;
    }
    setMessage(`Matched ${res.data.slice(0, 8)}…`);
    await loadStatementDetail(selectedStmtId, selectedStmtAccount);
  }

  async function onClearMatch(matchId: string) {
    const client = createWebClient();
    if (!client) return;
    setBusy(true);
    setMessage(null);
    const res = await clearBankMatches(client, [matchId]);
    setBusy(false);
    if (!res.ok) {
      setMessage(res.error);
      return;
    }
    setMessage(`Cleared ${res.data} match(es).`);
    await loadStatementDetail(selectedStmtId, selectedStmtAccount);
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
  const posted = boot.journals.filter(
    (j) => j.status === "posted" && !j.is_reversal,
  );
  const selectedStmt = boot.statements.find((s) => s.id === selectedStmtId);

  return (
    <div className={styles.form}>
      <StaffModuleTabs
        tabs={FINANCE_TABS}
        active={tab}
        onChange={selectTab}
        ariaLabel="Finance sections"
      />

      {message ? (
        <p className={styles.formStatus} role="status">
          {message}
        </p>
      ) : null}

      {ACCOUNT_TAB_CODES[tab] ? (
        <fieldset className={styles.fieldset}>
          <legend className={styles.legend}>
            {ACCOUNT_TAB_CODES[tab].title}
          </legend>
          <p className={styles.muted} style={{ marginBottom: "0.75rem" }}>
            Recent journal lines for this cash account. Post movements via the
            Journals tab (debit/credit this code).
          </p>
          {accountLines.length === 0 ? (
            <p className={styles.muted}>No lines yet for this account.</p>
          ) : (
            <table className={styles.table}>
              <thead>
                <tr>
                  <th>Entry</th>
                  <th>Debit</th>
                  <th>Credit</th>
                  <th>Currency</th>
                </tr>
              </thead>
              <tbody>
                {accountLines.map((l) => (
                  <tr key={l.id}>
                    <td>{l.journal_entry_id.slice(0, 8)}</td>
                    <td>{Number(l.debit).toFixed(2)}</td>
                    <td>{Number(l.credit).toFixed(2)}</td>
                    <td>{l.currency}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </fieldset>
      ) : null}

      {tab === "journals" ? (
      <fieldset className={styles.fieldset}>
        <legend className={styles.legend}>Journal draft</legend>
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
                {j.document_number ?? `Draft ${j.id.slice(0, 8)}`} · {j.currency} ·{" "}
                {j.description?.trim() || "No description"}{" "}
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
          <p className={styles.muted}>
            No draft journals. Create a balanced debit/credit draft above.
          </p>
        )}

        <p className={styles.muted} style={{ marginTop: "1rem" }}>
          Reverse posted journals via <code>reverse_journal</code> (ledger
          immutability — contra entry, not edit).
        </p>
        <label className={styles.field} style={{ marginBottom: "0.5rem" }}>
          Reversal reason (optional)
          <input
            value={reverseReason}
            onChange={(e) => setReverseReason(e.target.value)}
            disabled={busy}
            placeholder="Reason / description"
          />
        </label>
        {posted.length ? (
          <ul className={styles.navList}>
            {posted.map((j) => (
              <li key={j.id} className={styles.muted}>
                {j.document_number ?? `JE ${j.id.slice(0, 8)}`} · {j.currency} ·{" "}
                {j.entry_date} · {j.description?.trim() || "No description"}{" "}
                <button
                  type="button"
                  className={styles.btnGhost}
                  disabled={busy}
                  onClick={() => void onReverseJournal(j.id)}
                >
                  Reverse
                </button>
              </li>
            ))}
          </ul>
        ) : (
          <p className={styles.muted}>No posted journals to reverse yet.</p>
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
              Customer
              <input
                value={customerQuery}
                onChange={(e) => {
                  setCustomerQuery(e.target.value);
                  setCustomerId("");
                  setCustomerLabel("");
                }}
                disabled={busy}
                placeholder="Search name or paste id…"
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
          {customerHits.length > 0 && !customerId ? (
            <ul className={styles.list}>
              {customerHits.map((c) => (
                <li key={c.id}>
                  <button
                    type="button"
                    className={styles.btnGhost}
                    onClick={() => {
                      setCustomerId(c.id);
                      setCustomerLabel(c.display_name);
                      setCustomerQuery(c.display_name);
                      setCustomerHits([]);
                    }}
                  >
                    {c.display_name}
                  </button>
                </li>
              ))}
            </ul>
          ) : null}
          {customerId ? (
            <p className={styles.muted} style={{ marginTop: "0.5rem" }}>
              Selected {customerLabel || customerId.slice(0, 8)}
            </p>
          ) : null}
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
                    {p.document_number ?? p.id.slice(0, 8)} ·{" "}
                    {p.customers?.display_name ?? "Customer"} · {p.amount}{" "}
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
                {p.document_number ?? p.id.slice(0, 8)} ·{" "}
                {p.customers?.display_name ?? "Customer"} · {p.amount}{" "}
                {p.currency} · {p.tender}{" "}
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
          <p className={styles.muted}>
            No draft payments. Search a customer by name above to create one.
          </p>
        )}
      </fieldset>

      <fieldset className={styles.fieldset}>
        <legend className={styles.legend}>4 · Accounting periods</legend>
        {/* No unlock_accounting_period RPC — locked periods stay locked. */}
        <p className={styles.muted} style={{ marginBottom: "0.75rem" }}>
          Create open periods via <code>accounting_periods</code>; lock with{" "}
          <code>lock_accounting_period</code>.
        </p>
        <form onSubmit={(e) => void onCreatePeriod(e)}>
          <div className={styles.formGrid}>
            <label className={styles.field}>
              Start
              <input
                type="date"
                value={periodStart}
                onChange={(e) => setPeriodStart(e.target.value)}
                disabled={busy}
              />
            </label>
            <label className={styles.field}>
              End
              <input
                type="date"
                value={periodEnd}
                onChange={(e) => setPeriodEnd(e.target.value)}
                disabled={busy}
              />
            </label>
            <label className={styles.field} style={{ gridColumn: "1 / -1" }}>
              Label
              <input
                value={periodLabel}
                onChange={(e) => setPeriodLabel(e.target.value)}
                disabled={busy}
                placeholder="FY2026-Q1"
              />
            </label>
          </div>
          <div className={styles.formActions}>
            <button type="submit" className={styles.btnGhost} disabled={busy}>
              Create period
            </button>
          </div>
        </form>
        {boot.periods.length ? (
          <ul className={styles.navList} style={{ marginTop: "1rem" }}>
            {boot.periods.map((p) => (
              <li key={p.id} className={styles.muted}>
                {p.label} · {p.period_start} → {p.period_end} ·{" "}
                {p.locked_at ? `locked ${p.locked_at.slice(0, 10)}` : "open"}{" "}
                {!p.locked_at ? (
                  <button
                    type="button"
                    className={styles.btnGhost}
                    disabled={busy}
                    onClick={() => void onLockPeriod(p.id)}
                  >
                    Lock
                  </button>
                ) : null}
              </li>
            ))}
          </ul>
        ) : (
          <p className={styles.muted}>No accounting periods.</p>
        )}
      </fieldset>

      <fieldset className={styles.fieldset}>
        <legend className={styles.legend}>5 · Bank reconciliation</legend>
        <p className={styles.muted} style={{ marginBottom: "0.75rem" }}>
          Tables <code>bank_statements</code> / <code>bank_statement_lines</code>{" "}
          / <code>bank_recon_matches</code>; clear via{" "}
          <code>clear_bank_matches</code>. Currency explicit USD | ZIG.
        </p>
        <form onSubmit={(e) => void onImportStatement(e)}>
          <div className={styles.formGrid}>
            <label className={styles.field}>
              Bank account
              <select
                value={stmtAccount}
                onChange={(e) => setStmtAccount(e.target.value)}
                disabled={busy}
              >
                {boot.accounts.map((a) => (
                  <option key={`ba-${a.code}`} value={a.code}>
                    {a.code} — {a.name}
                  </option>
                ))}
              </select>
            </label>
            <label className={styles.field}>
              Currency
              <select
                value={stmtCurrency}
                onChange={(e) =>
                  setStmtCurrency(e.target.value as CurrencyCode)
                }
                disabled={busy}
              >
                <option value="USD">USD</option>
                <option value="ZIG">ZIG</option>
              </select>
            </label>
            <label className={styles.field}>
              Statement date
              <input
                type="date"
                value={stmtDate}
                onChange={(e) => setStmtDate(e.target.value)}
                disabled={busy}
              />
            </label>
            <label className={styles.field}>
              Document #
              <input
                value={stmtDoc}
                onChange={(e) => setStmtDoc(e.target.value)}
                disabled={busy}
                placeholder="optional"
              />
            </label>
            <label className={styles.field}>
              Opening ({stmtCurrency})
              <input
                value={stmtOpen}
                onChange={(e) => setStmtOpen(e.target.value)}
                disabled={busy}
                inputMode="decimal"
              />
            </label>
            <label className={styles.field}>
              Closing ({stmtCurrency})
              <input
                value={stmtClose}
                onChange={(e) => setStmtClose(e.target.value)}
                disabled={busy}
                inputMode="decimal"
              />
            </label>
            <label className={styles.field}>
              First line date
              <input
                type="date"
                value={stmtLineDate}
                onChange={(e) => setStmtLineDate(e.target.value)}
                disabled={busy}
              />
            </label>
            <label className={styles.field}>
              First line amount ({stmtCurrency})
              <input
                value={stmtLineAmount}
                onChange={(e) => setStmtLineAmount(e.target.value)}
                disabled={busy}
                inputMode="decimal"
                placeholder="optional"
              />
            </label>
            <label className={styles.field} style={{ gridColumn: "1 / -1" }}>
              First line description
              <input
                value={stmtLineDesc}
                onChange={(e) => setStmtLineDesc(e.target.value)}
                disabled={busy}
              />
            </label>
          </div>
          <div className={styles.formActions}>
            <button type="submit" className={styles.btnGhost} disabled={busy}>
              Import statement
            </button>
          </div>
        </form>

        <label className={styles.field} style={{ marginTop: "0.85rem" }}>
          Active statement
          <select
            value={selectedStmtId}
            onChange={(e) => {
              const id = e.target.value;
              setSelectedStmtId(id);
              const s = boot.statements.find((x) => x.id === id);
              if (s) setSelectedStmtAccount(s.account_code);
              void loadStatementDetail(id, s?.account_code);
            }}
            disabled={busy}
          >
            {boot.statements.length === 0 ? (
              <option value="">No statements</option>
            ) : (
              boot.statements.map((s) => (
                <option key={s.id} value={s.id}>
                  {s.document_number ?? s.id.slice(0, 8)} · {s.statement_date} ·{" "}
                  {s.currency} · {s.account_code}
                </option>
              ))
            )}
          </select>
        </label>

        {selectedStmtId ? (
          <>
            <form
              onSubmit={(e) => void onAddStmtLine(e)}
              style={{ marginTop: "0.75rem" }}
            >
              <div className={styles.formGrid}>
                <label className={styles.field}>
                  Line date
                  <input
                    type="date"
                    value={addLineDate}
                    onChange={(e) => setAddLineDate(e.target.value)}
                    disabled={busy}
                  />
                </label>
                <label className={styles.field}>
                  Amount ({selectedStmt?.currency ?? stmtCurrency})
                  <input
                    value={addLineAmount}
                    onChange={(e) => setAddLineAmount(e.target.value)}
                    disabled={busy}
                    inputMode="decimal"
                  />
                </label>
                <label className={styles.field} style={{ gridColumn: "1 / -1" }}>
                  Description
                  <input
                    value={addLineDesc}
                    onChange={(e) => setAddLineDesc(e.target.value)}
                    disabled={busy}
                  />
                </label>
              </div>
              <div className={styles.formActions}>
                <button type="submit" className={styles.btnGhost} disabled={busy}>
                  Add line
                </button>
              </div>
            </form>

            {stmtLines.length ? (
              <ul className={styles.navList} style={{ marginTop: "0.75rem" }}>
                {stmtLines.map((l) => (
                  <li key={l.id} className={styles.muted}>
                    {l.line_date} · {Number(l.amount).toFixed(2)} ·{" "}
                    {l.description ?? "—"} · {l.status}
                  </li>
                ))}
              </ul>
            ) : (
              <p className={styles.muted}>No lines on this statement.</p>
            )}

            <form
              onSubmit={(e) => void onMatchLine(e)}
              style={{ marginTop: "0.75rem" }}
            >
              <div className={styles.formGrid}>
                <label className={styles.field}>
                  Open statement line
                  <select
                    value={matchLineId}
                    onChange={(e) => setMatchLineId(e.target.value)}
                    disabled={busy}
                  >
                    {stmtLines
                      .filter((l) => l.status === "open")
                      .map((l) => (
                        <option key={l.id} value={l.id}>
                          {l.line_date} · {Number(l.amount).toFixed(2)} ·{" "}
                          {l.description ?? l.id.slice(0, 8)}
                        </option>
                      ))}
                  </select>
                </label>
                <label className={styles.field}>
                  Journal entry line
                  <select
                    value={matchJeLineId}
                    onChange={(e) => setMatchJeLineId(e.target.value)}
                    disabled={busy}
                  >
                    {jeLines.map((l) => (
                      <option key={l.id} value={l.id}>
                        {l.account_code} · Dr {Number(l.debit).toFixed(2)} / Cr{" "}
                        {Number(l.credit).toFixed(2)} {l.currency} ·{" "}
                        {l.id.slice(0, 8)}
                      </option>
                    ))}
                  </select>
                </label>
              </div>
              <div className={styles.formActions}>
                <button type="submit" className={styles.btnGhost} disabled={busy}>
                  Match
                </button>
                <button
                  type="button"
                  className={styles.btnGhost}
                  disabled={busy || !selectedStmt}
                  onClick={() =>
                    void loadStatementDetail(
                      selectedStmtId,
                      selectedStmt?.account_code ?? selectedStmtAccount,
                    )
                  }
                >
                  Refresh lines
                </button>
              </div>
            </form>

            {stmtMatches.length ? (
              <ul className={styles.navList} style={{ marginTop: "0.75rem" }}>
                {stmtMatches.map((m) => (
                  <li key={m.id} className={styles.muted}>
                    Match {m.id.slice(0, 8)} · line{" "}
                    {m.statement_line_id.slice(0, 8)} ↔ JE line{" "}
                    {m.journal_entry_line_id.slice(0, 8)}{" "}
                    <button
                      type="button"
                      className={styles.btnGhost}
                      disabled={busy}
                      onClick={() => void onClearMatch(m.id)}
                    >
                      Clear
                    </button>
                  </li>
                ))}
              </ul>
            ) : (
              <p className={styles.muted}>No matches for this statement.</p>
            )}
          </>
        ) : null}
      </fieldset>
    </div>
  );
}
