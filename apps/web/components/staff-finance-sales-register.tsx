"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import styles from "@/components/account.module.css";
import {
  buildStatementExportHook,
  downloadBrandedStatementPdf,
  reportAccountRegister,
  type AccountRegisterRow,
  type CurrencyCode,
  type SalesReferenceAccountTab,
  SALES_REFERENCE_ACCOUNT_META,
} from "@/lib/staff-finance";
import { createWebClient } from "@/lib/supabase";

function todayInput(): string {
  return new Date().toISOString().slice(0, 10);
}

function monthStartInput(): string {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-01`;
}

type Props = {
  tab: SalesReferenceAccountTab;
};

export function StaffFinanceSalesRegister({ tab }: Props) {
  const meta = SALES_REFERENCE_ACCOUNT_META[tab];
  const [from, setFrom] = useState(monthStartInput);
  const [to, setTo] = useState(todayInput);
  const [currency, setCurrency] = useState<CurrencyCode>("USD");
  const [rows, setRows] = useState<AccountRegisterRow[]>([]);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [loaded, setLoaded] = useState(false);

  const load = useCallback(async () => {
    const client = createWebClient();
    if (!client) {
      setMessage("Supabase is not configured.");
      return;
    }
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
      accountCode: meta.code,
      from,
      to,
      currency,
    });
    setBusy(false);
    setLoaded(true);
    if (!res.ok) {
      setMessage(res.error);
      setRows([]);
      return;
    }
    setRows(res.data);
  }, [currency, from, meta.code, to]);

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
      documentLabel: `${meta.title} · ${from} → ${to}`,
      currency,
      asOf: to,
      lines: rows.map((r) => ({
        description: [
          r.entry_date,
          r.document_number,
          r.description?.trim() || r.journal_entry_id.slice(0, 8),
        ]
          .filter(Boolean)
          .join(" · "),
        lineTotal: Number(r.debit) - Number(r.credit),
      })),
      closingBalance:
        rows.length > 0
          ? Number(rows[rows.length - 1]?.running_balance ?? 0)
          : undefined,
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

  return (
    <fieldset className={styles.fieldset}>
      <legend className={styles.legend}>{meta.title}</legend>
      <p className={styles.muted}>
        Reference only — view posted {meta.title} activity ({meta.code}). Open /
        close periods and till ops stay on Petty cash; payments post from the
        till and Payments desk.
      </p>

      {message ? (
        <p className={styles.formStatus} role="status">
          {message}
        </p>
      ) : null}

      <div className={styles.formGrid}>
        <label className={styles.field}>
          From
          <input
            type="date"
            value={from}
            onChange={(e) => setFrom(e.target.value)}
            disabled={busy}
          />
        </label>
        <label className={styles.field}>
          To
          <input
            type="date"
            value={to}
            onChange={(e) => setTo(e.target.value)}
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
      </div>

      <div className={styles.formActions} style={{ flexWrap: "wrap" }}>
        <button
          type="button"
          className={styles.btnGhost}
          disabled={busy}
          onClick={() => void load()}
        >
          Refresh
        </button>
        <button
          type="button"
          className={styles.btn}
          style={{ marginTop: 0 }}
          disabled={busy || rows.length === 0}
          onClick={() => void onDownloadPdf()}
        >
          Download PDF
        </button>
      </div>

      {!loaded ? (
        <p className={styles.muted} style={{ marginTop: "0.75rem" }}>
          Loading transactions…
        </p>
      ) : rows.length === 0 ? (
        <p className={styles.emptyState}>
          No posted transactions in this period.
        </p>
      ) : (
        <div className={styles.tableWrap} style={{ marginTop: "0.75rem" }}>
          <table className={styles.table}>
            <thead>
              <tr>
                <th>Date</th>
                <th>Transaction</th>
                <th>Debit</th>
                <th>Credit</th>
                <th>Balance</th>
                <th>Currency</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((r) => {
                const label =
                  [
                    r.document_number,
                    r.description?.trim() || null,
                  ]
                    .filter(Boolean)
                    .join(" · ") || r.journal_entry_id;
                return (
                  <tr key={`${r.journal_entry_id}-${r.entry_date}-${r.debit}-${r.credit}`}>
                    <td>{r.entry_date}</td>
                    <td>
                      <Link
                        href={`/staff/finance/transactions/${r.journal_entry_id}?tab=${tab}`}
                        className={styles.navLink}
                        style={{ display: "inline", padding: 0, minHeight: 0 }}
                      >
                        {label}
                      </Link>
                    </td>
                    <td>{Number(r.debit).toFixed(2)}</td>
                    <td>{Number(r.credit).toFixed(2)}</td>
                    <td>{Number(r.running_balance).toFixed(2)}</td>
                    <td>{r.currency}</td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}
    </fieldset>
  );
}
