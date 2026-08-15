"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import styles from "@/components/account.module.css";
import {
  loadFinanceTransactionDetail,
  type FinanceTransactionDetail,
  type SalesReferenceAccountTab,
  isSalesReferenceAccountTab,
  SALES_REFERENCE_ACCOUNT_META,
} from "@/lib/staff-finance";
import { createWebClient } from "@/lib/supabase";

type Boot =
  | { kind: "loading" }
  | { kind: "auth" }
  | { kind: "error"; message: string }
  | { kind: "ready"; detail: FinanceTransactionDetail };

type Props = {
  journalEntryId: string;
  returnTab?: string | null;
};

export function StaffFinanceTransactionDetail({
  journalEntryId,
  returnTab,
}: Props) {
  const [boot, setBoot] = useState<Boot>({ kind: "loading" });

  const backHref =
    returnTab === "petty-cash"
      ? "/staff/finance?tab=petty-cash"
      : returnTab === "accounts"
        ? "/staff/finance?tab=accounts"
        : returnTab && isSalesReferenceAccountTab(returnTab)
          ? `/staff/finance?tab=${returnTab}`
          : "/staff/finance?tab=cash-sales";
  const backLabel =
    returnTab === "petty-cash"
      ? "Petty cash"
      : returnTab === "accounts"
        ? "Online sales"
        : returnTab && isSalesReferenceAccountTab(returnTab)
          ? SALES_REFERENCE_ACCOUNT_META[returnTab as SalesReferenceAccountTab]
              .title
          : "Cash";

  const refresh = useCallback(async () => {
    const client = createWebClient();
    if (!client) {
      setBoot({
        kind: "error",
        message: "Supabase is not configured on this environment.",
      });
      return;
    }
    const session = await client.auth.getSession();
    if (!session.data.session) {
      setBoot({ kind: "auth" });
      return;
    }
    const res = await loadFinanceTransactionDetail(client, journalEntryId);
    if (!res.ok) {
      setBoot({ kind: "error", message: res.error });
      return;
    }
    setBoot({ kind: "ready", detail: res.data });
  }, [journalEntryId]);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  if (boot.kind === "loading") {
    return <p className={styles.emptyState}>Loading transaction…</p>;
  }
  if (boot.kind === "auth") {
    return (
      <p className={styles.lede}>
        <Link href={`/login?next=/staff/finance/transactions/${journalEntryId}`}>
          Sign in
        </Link>{" "}
        with finance/admin staff.
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

  const { journal, lines, payment, invoices } = boot.detail;
  const transactionId = journal.document_number || journal.id;

  return (
    <div className={styles.form}>
      <p className={styles.muted} style={{ marginBottom: "0.75rem" }}>
        <Link href={backHref} className={styles.navLink} style={{ display: "inline", padding: 0, minHeight: 0 }}>
          ← {backLabel}
        </Link>
      </p>

      <fieldset className={styles.fieldset}>
        <legend className={styles.legend}>Transaction</legend>
        <dl className={styles.formGrid}>
          <div className={styles.field}>
            <dt className={styles.muted}>Transaction ID</dt>
            <dd>
              <code>{transactionId}</code>
            </dd>
          </div>
          {journal.document_number ? (
            <div className={styles.field}>
              <dt className={styles.muted}>Journal id</dt>
              <dd>
                <code>{journal.id}</code>
              </dd>
            </div>
          ) : null}
          <div className={styles.field}>
            <dt className={styles.muted}>Date</dt>
            <dd>{journal.entry_date}</dd>
          </div>
          <div className={styles.field}>
            <dt className={styles.muted}>Status</dt>
            <dd>{journal.status}</dd>
          </div>
          <div className={styles.field}>
            <dt className={styles.muted}>Currency</dt>
            <dd>
              {journal.currency}
              {journal.exchange_rate_applied != null &&
              journal.currency === "ZIG"
                ? ` · rate ${journal.exchange_rate_applied}`
                : ""}
            </dd>
          </div>
          <div className={styles.field}>
            <dt className={styles.muted}>Posted</dt>
            <dd>{journal.posted_at}</dd>
          </div>
        </dl>
        {journal.description?.trim() ? (
          <p style={{ marginTop: "0.75rem" }}>{journal.description.trim()}</p>
        ) : null}
        {journal.is_reversal ? (
          <p className={styles.muted} style={{ marginTop: "0.5rem" }}>
            Reversal of{" "}
            <code>{journal.reverses_entry_id ?? "—"}</code>
          </p>
        ) : null}
      </fieldset>

      {payment ? (
        <fieldset className={styles.fieldset}>
          <legend className={styles.legend}>Receipt</legend>
          <dl className={styles.formGrid}>
            <div className={styles.field}>
              <dt className={styles.muted}>Payment ID</dt>
              <dd>
                <code>{payment.document_number || payment.id}</code>
              </dd>
            </div>
            <div className={styles.field}>
              <dt className={styles.muted}>Tender</dt>
              <dd>{payment.tender}</dd>
            </div>
            <div className={styles.field}>
              <dt className={styles.muted}>Amount</dt>
              <dd>
                {Number(payment.amount).toFixed(2)} {payment.currency}
              </dd>
            </div>
            <div className={styles.field}>
              <dt className={styles.muted}>Status</dt>
              <dd>{payment.status}</dd>
            </div>
            <div className={styles.field}>
              <dt className={styles.muted}>Customer</dt>
              <dd>{payment.customer_name || payment.customer_id}</dd>
            </div>
            {payment.posted_at ? (
              <div className={styles.field}>
                <dt className={styles.muted}>Payment posted</dt>
                <dd>{payment.posted_at}</dd>
              </div>
            ) : null}
          </dl>
          {payment.notes?.trim() ? (
            <p className={styles.muted} style={{ marginTop: "0.5rem" }}>
              {payment.notes.trim()}
            </p>
          ) : null}
        </fieldset>
      ) : (
        <p className={styles.muted}>
          No payment receipt linked to this journal (transfer or manual entry).
        </p>
      )}

      {invoices.length > 0 ? (
        <fieldset className={styles.fieldset}>
          <legend className={styles.legend}>Allocated invoices</legend>
          {invoices.map((inv) => (
            <div key={inv.id} style={{ marginBottom: "1rem" }}>
              <p>
                <strong>{inv.document_number || inv.id}</strong>
                {" · "}
                {inv.status}
                {" · "}
                allocated {inv.allocated_amount.toFixed(2)} {inv.currency}
                {" · "}
                invoice total {inv.total.toFixed(2)}
              </p>
              {(inv.customer_email || inv.customer_phone_e164) && (
                <p className={styles.muted}>
                  {[inv.customer_email, inv.customer_phone_e164]
                    .filter(Boolean)
                    .join(" · ")}
                </p>
              )}
              {inv.lines.length === 0 ? (
                <p className={styles.muted}>No line items.</p>
              ) : (
                <div className={styles.tableWrap}>
                  <table className={styles.table}>
                    <thead>
                      <tr>
                        <th>OEM / item</th>
                        <th>Qty</th>
                        <th>Unit</th>
                        <th>Line</th>
                      </tr>
                    </thead>
                    <tbody>
                      {inv.lines.map((line) => (
                        <tr key={line.id}>
                          <td>
                            {line.oem_part_number || "—"}
                            {line.is_core_charge ? " · core" : ""}
                            {line.description ? (
                              <span className={styles.muted}>
                                {" "}
                                · {line.description}
                              </span>
                            ) : null}
                          </td>
                          <td>{line.qty}</td>
                          <td>{line.unit_price.toFixed(2)}</td>
                          <td>{line.line_total.toFixed(2)}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </div>
          ))}
        </fieldset>
      ) : null}

      <fieldset className={styles.fieldset}>
        <legend className={styles.legend}>Ledger lines</legend>
        {lines.length === 0 ? (
          <p className={styles.emptyState}>No journal lines.</p>
        ) : (
          <div className={styles.tableWrap}>
            <table className={styles.table}>
              <thead>
                <tr>
                  <th>Account</th>
                  <th>Debit</th>
                  <th>Credit</th>
                  <th>Currency</th>
                </tr>
              </thead>
              <tbody>
                {lines.map((line) => (
                  <tr key={line.id}>
                    <td>
                      {line.account_code} · {line.account_label}
                    </td>
                    <td>{line.debit.toFixed(2)}</td>
                    <td>{line.credit.toFixed(2)}</td>
                    <td>{line.currency}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </fieldset>
    </div>
  );
}
