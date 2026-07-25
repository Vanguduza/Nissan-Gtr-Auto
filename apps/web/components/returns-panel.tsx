"use client";

import Link from "next/link";
import { FormEvent, useCallback, useEffect, useState } from "react";
import {
  formatMoney,
  listInvoiceLines,
  listOwnInvoices,
  requestReturnCreditNote,
  requireSession,
  type InvoiceLineRow,
  type InvoiceRow,
} from "@/lib/customer-storefront";
import { createWebClient } from "@/lib/supabase";
import styles from "@/components/account.module.css";

type Status =
  | { kind: "loading" }
  | { kind: "auth" }
  | { kind: "error"; message: string }
  | { kind: "ready"; invoices: InvoiceRow[] };

type LinePick = {
  line: InvoiceLineRow;
  qty: string;
  selected: boolean;
};

export function ReturnsPanel() {
  const [status, setStatus] = useState<Status>({ kind: "loading" });
  const [invoiceId, setInvoiceId] = useState("");
  const [invoiceCurrency, setInvoiceCurrency] = useState<"USD" | "ZIG">("USD");
  const [picks, setPicks] = useState<LinePick[]>([]);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<string | null>(null);

  const refresh = useCallback(async () => {
    const client = createWebClient();
    if (!client) {
      setStatus({
        kind: "error",
        message: "Supabase is not configured on this environment.",
      });
      return;
    }
    const session = await requireSession(client);
    if (!session.ok) {
      setStatus({ kind: "auth" });
      return;
    }
    const invoices = await listOwnInvoices(client);
    if (!invoices.ok) {
      setStatus({ kind: "error", message: invoices.error });
      return;
    }
    const posted = invoices.data.filter((i) => i.status === "posted");
    setStatus({ kind: "ready", invoices: posted });
  }, []);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  async function onSelectInvoice(id: string) {
    setInvoiceId(id);
    setPicks([]);
    setMessage(null);
    if (!id) return;
    const inv = status.kind === "ready"
      ? status.invoices.find((i) => i.id === id)
      : undefined;
    if (inv) setInvoiceCurrency(inv.currency);
    const client = createWebClient();
    if (!client) return;
    const lines = await listInvoiceLines(client, id);
    if (!lines.ok) {
      setMessage(lines.error);
      return;
    }
    setPicks(
      lines.data
        .filter((l) => !l.is_core_charge)
        .map((line) => ({
          line,
          qty: String(line.qty),
          selected: false,
        })),
    );
  }

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setMessage(null);
    const client = createWebClient();
    if (!client) {
      setMessage("Supabase is not configured.");
      setBusy(false);
      return;
    }

    const selected = picks.filter((p) => p.selected);
    if (!invoiceId || selected.length === 0) {
      setMessage("Select an invoice and at least one line to return.");
      setBusy(false);
      return;
    }

    const payload = selected.map((p) => {
      const qty = Number(p.qty);
      return {
        stock_item_id: p.line.stock_item_id,
        uom_id: p.line.uom_id,
        qty,
      };
    });

    if (payload.some((l) => !(l.qty > 0))) {
      setMessage("Return quantities must be greater than zero.");
      setBusy(false);
      return;
    }

    const result = await requestReturnCreditNote(client, invoiceId, payload);
    setBusy(false);
    if (!result.ok) {
      setMessage(result.error);
      return;
    }
    setMessage(
      `Credit note ${result.data} posted. Stock routed to Quarantine (no direct exchange).`,
    );
    setInvoiceId("");
    setPicks([]);
    await refresh();
  }

  if (status.kind === "loading") {
    return <p className={styles.muted}>Loading invoices…</p>;
  }

  if (status.kind === "auth") {
    return (
      <p className={styles.lede}>
        <Link href="/login">Sign in</Link> to request a return against your
        invoices.
      </p>
    );
  }

  if (status.kind === "error") {
    return (
      <p className={styles.lede} role="alert">
        {status.message}{" "}
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

  if (status.invoices.length === 0) {
    return (
      <p className={styles.muted}>
        No posted invoices available for return.{" "}
        <Link href="/account/orders">View orders</Link>
      </p>
    );
  }

  return (
    <div>
      <form className={styles.form} onSubmit={(e) => void onSubmit(e)}>
        <fieldset className={styles.fieldset}>
          <legend className={styles.legend}>1 · Invoice</legend>
          <label className={styles.field}>
            Posted invoice
            <select
              value={invoiceId}
              onChange={(e) => void onSelectInvoice(e.target.value)}
              disabled={busy}
              required
            >
              <option value="">Select…</option>
              {status.invoices.map((inv) => (
                <option key={inv.id} value={inv.id}>
                  {inv.document_number ?? inv.id.slice(0, 8)} ·{" "}
                  {formatMoney(Number(inv.total), inv.currency)}
                </option>
              ))}
            </select>
          </label>
        </fieldset>

        {picks.length > 0 ? (
          <fieldset className={styles.fieldset}>
            <legend className={styles.legend}>2 · Lines to quarantine</legend>
            <p className={styles.muted} style={{ marginBottom: "0.75rem" }}>
              Returned SKUs go to Quarantine only — never exchanged into saleable
              stock.
            </p>
            <ul className={styles.list}>
              {picks.map((p, idx) => {
                const oem =
                  p.line.stock_items?.oem_part_number ?? p.line.stock_item_id;
                const name = p.line.stock_items?.description?.trim() || oem;
                return (
                  <li key={p.line.id}>
                    <label className={styles.checkField}>
                      <input
                        type="checkbox"
                        checked={p.selected}
                        disabled={busy}
                        onChange={(e) => {
                          const selected = e.target.checked;
                          setPicks((list) =>
                            list.map((row, i) =>
                              i === idx ? { ...row, selected } : row,
                            ),
                          );
                        }}
                      />
                      <strong>{oem}</strong> — {name}
                    </label>
                    <p className={styles.muted}>
                      Invoiced qty {p.line.qty} ·{" "}
                      {formatMoney(Number(p.line.unit_price), invoiceCurrency)}
                    </p>
                    {p.selected ? (
                      <label className={styles.field}>
                        Return qty
                        <input
                          value={p.qty}
                          inputMode="decimal"
                          disabled={busy}
                          onChange={(e) => {
                            const qty = e.target.value;
                            setPicks((list) =>
                              list.map((row, i) =>
                                i === idx ? { ...row, qty } : row,
                              ),
                            );
                          }}
                        />
                      </label>
                    ) : null}
                  </li>
                );
              })}
            </ul>
          </fieldset>
        ) : null}

        <div className={styles.formActions}>
          <button
            type="submit"
            className={styles.btn}
            disabled={busy || !invoiceId || !picks.some((p) => p.selected)}
          >
            {busy ? "Submitting…" : "Request return (credit note)"}
          </button>
        </div>
      </form>
      {message ? (
        <p className={styles.formStatus} role="status">
          {message}
        </p>
      ) : null}
    </div>
  );
}
