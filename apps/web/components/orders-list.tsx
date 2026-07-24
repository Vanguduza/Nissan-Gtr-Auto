"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import {
  formatMoney,
  fulfillmentLabel,
  listOwnInvoices,
  requireSession,
  type InvoiceRow,
} from "@/lib/customer-storefront";
import { createWebClient } from "@/lib/supabase";
import styles from "@/components/account.module.css";

type Status =
  | { kind: "loading" }
  | { kind: "auth" }
  | { kind: "error"; message: string }
  | { kind: "ready"; invoices: InvoiceRow[] };

export function OrdersList() {
  const [status, setStatus] = useState<Status>({ kind: "loading" });

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
    setStatus({ kind: "ready", invoices: invoices.data });
  }, []);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  if (status.kind === "loading") {
    return <p className={styles.muted}>Loading orders…</p>;
  }

  if (status.kind === "auth") {
    return (
      <p className={styles.lede}>
        <Link href="/login">Sign in</Link> to see your invoices.
      </p>
    );
  }

  if (status.kind === "error") {
    return (
      <p className={styles.lede} role="alert">
        {status.message}{" "}
        <button type="button" className={styles.btnGhost} onClick={() => void refresh()}>
          Retry
        </button>
      </p>
    );
  }

  if (status.invoices.length === 0) {
    return <p className={styles.muted}>No orders yet.</p>;
  }

  return (
    <ul className={styles.list}>
      {status.invoices.map((inv) => {
        const open = Number(inv.total) - Number(inv.amount_paid);
        return (
          <li key={inv.id}>
            <strong>{inv.document_number ?? inv.id}</strong>
            {" · "}
            {fulfillmentLabel(inv.fulfillment_mode)}
            {" · "}
            {inv.status}
            <br />
            <span className={styles.muted}>
              {formatMoney(Number(inv.total), inv.currency)}
              {open > 0
                ? ` · open ${formatMoney(open, inv.currency)}`
                : " · paid"}
            </span>
            <br />
            <Link href={`/account/orders/${inv.id}`} className={styles.btn}>
              View status
            </Link>
          </li>
        );
      })}
    </ul>
  );
}
