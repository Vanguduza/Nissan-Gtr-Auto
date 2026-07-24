"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import styles from "@/components/account.module.css";
import {
  listSupplierRfqs,
  requireSession,
  statusLabel,
  type RfqRow,
} from "@/lib/rfq-portal";
import { createWebClient } from "@/lib/supabase";

type Status =
  | { kind: "loading" }
  | { kind: "auth" }
  | { kind: "error"; message: string }
  | { kind: "ready"; rfqs: RfqRow[] };

export function SupplierRfqList() {
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
    const rfqs = await listSupplierRfqs(client);
    if (!rfqs.ok) {
      setStatus({ kind: "error", message: rfqs.error });
      return;
    }
    setStatus({ kind: "ready", rfqs: rfqs.data });
  }, []);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  if (status.kind === "loading") {
    return <p className={styles.muted}>Loading invited RFQs…</p>;
  }
  if (status.kind === "auth") {
    return (
      <p className={styles.lede}>
        <Link href="/login">Sign in</Link> with a linked supplier profile to see
        invitations.
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
  if (status.rfqs.length === 0) {
    return (
      <p className={styles.muted}>
        No invited RFQs for this supplier account.
      </p>
    );
  }

  return (
    <ul className={styles.list}>
      {status.rfqs.map((rfq) => (
        <li key={rfq.id}>
          <strong>{rfq.document_number ?? rfq.id.slice(0, 8)}</strong>
          {" · "}
          {statusLabel(rfq.status)}
          {rfq.needed_by ? ` · needed ${rfq.needed_by}` : null}
          <br />
          <Link href={`/supplier/rfqs/${rfq.id}`} className={styles.btn}>
            Quote
          </Link>
        </li>
      ))}
    </ul>
  );
}
