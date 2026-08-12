"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import styles from "@/components/account.module.css";
import {
  awardQuotationToPo,
  loadRfqDetail,
  requireSession,
  statusLabel,
  submitRfq,
  type RfqDetail,
} from "@/lib/rfq-portal";
import { createWebClient } from "@/lib/supabase";

type Status =
  | { kind: "loading" }
  | { kind: "auth" }
  | { kind: "error"; message: string }
  | { kind: "ready"; detail: RfqDetail };

export function StaffRfqDetail({ rfqId }: { rfqId: string }) {
  const [status, setStatus] = useState<Status>({ kind: "loading" });
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
    setStatus({ kind: "ready", detail: detail.data });
  }, [rfqId]);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  async function onSubmitRfq() {
    setMessage(null);
    const client = createWebClient();
    if (!client) return;
    setBusy(true);
    const res = await submitRfq(client, rfqId);
    setBusy(false);
    if (!res.ok) {
      setMessage(res.error);
      return;
    }
    setMessage("RFQ submitted.");
    await refresh();
  }

  async function onAward(quotationId: string) {
    setMessage(null);
    const client = createWebClient();
    if (!client) return;
    setBusy(true);
    const res = await awardQuotationToPo(client, quotationId, "Awarded from web portal");
    setBusy(false);
    if (!res.ok) {
      setMessage(res.error);
      return;
    }
    setMessage(`Awarded — PO ${res.data}`);
    await refresh();
  }

  if (status.kind === "loading") {
    return <p className={styles.muted}>Loading RFQ…</p>;
  }
  if (status.kind === "auth") {
    return (
      <p className={styles.lede}>
        <Link href="/login">Sign in</Link> as staff to view this RFQ.
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

  const { rfq, lines, invites, quotations } = status.detail;
  const submittedQuotes = quotations.filter((q) => q.status === "submitted");

  return (
    <div className={styles.addrWrap}>
      <div>
        <p className={styles.muted}>
          {rfq.document_number ?? rfq.id} · {statusLabel(rfq.status)}
          {rfq.needed_by ? ` · needed ${rfq.needed_by}` : ""}
        </p>
        {rfq.notes ? <p className={styles.lede}>{rfq.notes}</p> : null}
        {rfq.status === "draft" ? (
          <button
            type="button"
            className={styles.btn}
            disabled={busy}
            onClick={() => void onSubmitRfq()}
          >
            {busy ? "Submitting…" : "Submit RFQ"}
          </button>
        ) : null}
      </div>

      <fieldset className={styles.fieldset}>
        <legend className={styles.legend}>Lines</legend>
        {lines.length === 0 ? (
          <p className={styles.muted}>No lines.</p>
        ) : (
          <ul className={styles.list}>
            {lines.map((line) => (
              <li key={line.id}>
                #{line.line_no}{" "}
                {line.stock_items?.oem_part_number ?? line.stock_item_id}
                {line.stock_items?.description
                  ? ` — ${line.stock_items.description}`
                  : ""}
                <br />
                <span className={styles.muted}>Qty {line.qty}</span>
              </li>
            ))}
          </ul>
        )}
      </fieldset>

      <fieldset className={styles.fieldset}>
        <legend className={styles.legend}>Invited suppliers</legend>
        {invites.length === 0 ? (
          <p className={styles.muted}>No invites.</p>
        ) : (
          <ul className={styles.list}>
            {invites.map((inv) => (
              <li key={inv.supplier_id}>
                {inv.suppliers
                  ? `${inv.suppliers.code} — ${inv.suppliers.name}`
                  : inv.supplier_id}
              </li>
            ))}
          </ul>
        )}
      </fieldset>

      <fieldset className={styles.fieldset}>
        <legend className={styles.legend}>Quotations</legend>
        {quotations.length === 0 ? (
          <p className={styles.muted}>No quotations yet.</p>
        ) : (
          <ul className={styles.list}>
            {quotations.map((q) => {
              const label = q.suppliers
                ? `${q.suppliers.code} — ${q.suppliers.name}`
                : q.supplier_id;
              const canAward =
                rfq.status === "submitted" &&
                !rfq.awarded_quotation_id &&
                q.status === "submitted";
              return (
                <li key={q.id}>
                  <strong>{q.document_number ?? q.id.slice(0, 8)}</strong>
                  {" · "}
                  {label}
                  {" · "}
                  {statusLabel(q.status)}
                  {" · "}
                  {q.currency}
                  <br />
                  <span className={styles.muted}>
                    rate {q.exchange_rate_applied}
                    {q.valid_until ? ` · valid until ${q.valid_until}` : ""}
                  </span>
                  {canAward ? (
                    <>
                      <br />
                      <button
                        type="button"
                        className={styles.btn}
                        disabled={busy}
                        onClick={() => void onAward(q.id)}
                      >
                        Award → spot-buy PO
                        <span className={styles.muted}>
                          {" "}
                          (optional; roster remains SoR)
                        </span>
                      </button>
                    </>
                  ) : null}
                  {rfq.awarded_quotation_id === q.id ? (
                    <>
                      <br />
                      <span className={styles.formStatus}>Awarded</span>
                    </>
                  ) : null}
                </li>
              );
            })}
          </ul>
        )}
        {rfq.status === "submitted" &&
        !rfq.awarded_quotation_id &&
        submittedQuotes.length === 0 ? (
          <p className={styles.muted}>
            Waiting for suppliers to submit quotations.
          </p>
        ) : null}
      </fieldset>

      {message ? (
        <p className={styles.formStatus} role="status">
          {message}
        </p>
      ) : null}
    </div>
  );
}
