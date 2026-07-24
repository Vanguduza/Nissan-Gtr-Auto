"use client";

import Link from "next/link";
import { FormEvent, useCallback, useEffect, useState } from "react";
import styles from "@/components/account.module.css";
import { zigExchangeRate } from "@/lib/customer-storefront";
import {
  loadOwnQuotation,
  loadRfqDetail,
  requireSession,
  statusLabel,
  submitSupplierQuotation,
  upsertSupplierQuotation,
  type CurrencyCode,
  type QuoteLineInput,
  type RfqDetail,
  type SupplierQuotationRow,
} from "@/lib/rfq-portal";
import { createWebClient } from "@/lib/supabase";

type LineDraft = {
  rfq_line_id: string;
  stock_item_id: string;
  uom_id: string;
  oem: string;
  qty: string;
  unit_price: string;
};

type Status =
  | { kind: "loading" }
  | { kind: "auth" }
  | { kind: "error"; message: string }
  | {
      kind: "ready";
      detail: RfqDetail;
      quotation: SupplierQuotationRow | null;
      lines: LineDraft[];
    };

export function SupplierQuoteForm({ rfqId }: { rfqId: string }) {
  const [status, setStatus] = useState<Status>({ kind: "loading" });
  const [currency, setCurrency] = useState<CurrencyCode>("USD");
  const [exchangeRate, setExchangeRate] = useState("1");
  const [validUntil, setValidUntil] = useState("");
  const [notes, setNotes] = useState("");
  const [lineDrafts, setLineDrafts] = useState<LineDraft[]>([]);
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
    const detail = await loadRfqDetail(client, rfqId);
    if (!detail.ok) {
      setStatus({ kind: "error", message: detail.error });
      return;
    }
    const own = await loadOwnQuotation(client, rfqId);
    if (!own.ok) {
      setStatus({ kind: "error", message: own.error });
      return;
    }

    const drafts: LineDraft[] = detail.data.lines.map((line) => {
      const existing = own.data.lines.find((l) => l.rfq_line_id === line.id);
      return {
        rfq_line_id: line.id,
        stock_item_id: line.stock_item_id,
        uom_id: line.uom_id,
        oem: line.stock_items?.oem_part_number ?? line.stock_item_id,
        qty: String(existing?.qty ?? line.qty),
        unit_price: existing ? String(existing.unit_price) : "",
      };
    });

    if (own.data.quotation) {
      setCurrency(own.data.quotation.currency);
      setExchangeRate(String(own.data.quotation.exchange_rate_applied));
      setValidUntil(own.data.quotation.valid_until ?? "");
      setNotes(own.data.quotation.notes ?? "");
    }

    setLineDrafts(drafts);
    setStatus({
      kind: "ready",
      detail: detail.data,
      quotation: own.data.quotation,
      lines: drafts,
    });
  }, [rfqId]);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  function onCurrencyChange(next: CurrencyCode) {
    setCurrency(next);
    if (next === "USD") setExchangeRate("1");
    else setExchangeRate(String(zigExchangeRate()));
  }

  async function onSave(e: FormEvent) {
    e.preventDefault();
    setMessage(null);
    const client = createWebClient();
    if (!client) return;

    const lines: QuoteLineInput[] = [];
    for (const line of lineDrafts) {
      const qty = Number(line.qty);
      const unit_price = Number(line.unit_price);
      if (!(qty > 0) || !(unit_price >= 0) || Number.isNaN(unit_price)) {
        setMessage("Each line needs qty > 0 and a unit price.");
        return;
      }
      lines.push({
        rfq_line_id: line.rfq_line_id,
        stock_item_id: line.stock_item_id,
        uom_id: line.uom_id,
        qty,
        unit_price,
      });
    }

    const rate = Number(exchangeRate);
    if (!(rate > 0)) {
      setMessage("Exchange rate must be > 0.");
      return;
    }

    setBusy(true);
    const res = await upsertSupplierQuotation(client, {
      rfqId,
      currency,
      exchangeRate: rate,
      lines,
      validUntil: validUntil || undefined,
      notes: notes.trim() || undefined,
    });
    setBusy(false);
    if (!res.ok) {
      setMessage(res.error);
      return;
    }
    setMessage("Quotation saved as draft.");
    await refresh();
  }

  async function onSubmitQuote() {
    setMessage(null);
    if (!status.kind || status.kind !== "ready" || !status.quotation) {
      setMessage("Save a draft quotation before submitting.");
      return;
    }
    const client = createWebClient();
    if (!client) return;
    setBusy(true);
    const res = await submitSupplierQuotation(client, status.quotation.id);
    setBusy(false);
    if (!res.ok) {
      setMessage(res.error);
      return;
    }
    setMessage("Quotation submitted.");
    await refresh();
  }

  if (status.kind === "loading") {
    return <p className={styles.muted}>Loading RFQ…</p>;
  }
  if (status.kind === "auth") {
    return (
      <p className={styles.lede}>
        <Link href="/login">Sign in</Link> as a linked supplier to quote.
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

  const { detail, quotation } = status;
  const rfq = detail.rfq;
  const locked = quotation?.status === "submitted" || quotation?.status === "cancelled";
  const canQuote = rfq.status === "submitted" && !rfq.awarded_quotation_id;

  return (
    <div className={styles.addrWrap}>
      <div>
        <p className={styles.muted}>
          {rfq.document_number ?? rfq.id} · RFQ {statusLabel(rfq.status)}
          {quotation
            ? ` · your quote ${statusLabel(quotation.status)}`
            : " · no quote yet"}
        </p>
        {rfq.notes ? <p className={styles.lede}>{rfq.notes}</p> : null}
        {!canQuote ? (
          <p className={styles.muted}>
            {rfq.status === "draft"
              ? "RFQ is still a draft — wait for staff to submit."
              : rfq.awarded_quotation_id
                ? "This RFQ has already been awarded."
                : `RFQ status ${rfq.status} — quoting closed.`}
          </p>
        ) : null}
      </div>

      <form className={styles.form} onSubmit={onSave}>
        <fieldset className={styles.fieldset}>
          <legend className={styles.legend}>Quotation header</legend>
          <div className={styles.formGrid}>
            <label className={styles.field}>
              Currency
              <select
                value={currency}
                onChange={(e) =>
                  onCurrencyChange(e.target.value as CurrencyCode)
                }
                disabled={locked || !canQuote}
              >
                <option value="USD">USD</option>
                <option value="ZIG">ZiG</option>
              </select>
            </label>
            <label className={styles.field}>
              Exchange rate
              <input
                type="number"
                min="0.0001"
                step="any"
                value={exchangeRate}
                onChange={(e) => setExchangeRate(e.target.value)}
                disabled={locked || !canQuote || currency === "USD"}
                required
              />
            </label>
            <label className={styles.field}>
              Valid until
              <input
                type="date"
                value={validUntil}
                onChange={(e) => setValidUntil(e.target.value)}
                disabled={locked || !canQuote}
              />
            </label>
            <label className={styles.field} style={{ gridColumn: "1 / -1" }}>
              Notes
              <textarea
                value={notes}
                onChange={(e) => setNotes(e.target.value)}
                rows={2}
                disabled={locked || !canQuote}
              />
            </label>
          </div>
        </fieldset>

        <fieldset className={styles.fieldset}>
          <legend className={styles.legend}>Line prices</legend>
          {lineDrafts.length === 0 ? (
            <p className={styles.muted}>No RFQ lines.</p>
          ) : (
            lineDrafts.map((line, index) => (
              <div
                key={line.rfq_line_id}
                className={styles.formGrid}
                style={{ marginBottom: "0.85rem" }}
              >
                <label className={styles.field}>
                  Item
                  <input value={line.oem} readOnly />
                </label>
                <label className={styles.field}>
                  Qty
                  <input
                    type="number"
                    min="0.0001"
                    step="any"
                    value={line.qty}
                    onChange={(e) =>
                      setLineDrafts((prev) =>
                        prev.map((l, i) =>
                          i === index ? { ...l, qty: e.target.value } : l,
                        ),
                      )
                    }
                    disabled={locked || !canQuote}
                    required
                  />
                </label>
                <label className={styles.field}>
                  Unit price ({currency})
                  <input
                    type="number"
                    min="0"
                    step="any"
                    value={line.unit_price}
                    onChange={(e) =>
                      setLineDrafts((prev) =>
                        prev.map((l, i) =>
                          i === index ? { ...l, unit_price: e.target.value } : l,
                        ),
                      )
                    }
                    disabled={locked || !canQuote}
                    required
                  />
                </label>
              </div>
            ))
          )}
        </fieldset>

        <div className={styles.formActions}>
          {canQuote && !locked ? (
            <button className={styles.btn} type="submit" disabled={busy}>
              {busy ? "Saving…" : "Save draft"}
            </button>
          ) : null}
          {canQuote && quotation?.status === "draft" ? (
            <button
              type="button"
              className={styles.btnGhost}
              disabled={busy}
              onClick={() => void onSubmitQuote()}
            >
              Submit quotation
            </button>
          ) : null}
          {message ? <p className={styles.formStatus}>{message}</p> : null}
        </div>
      </form>
    </div>
  );
}
