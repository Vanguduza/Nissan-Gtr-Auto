"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import {
  formatMoney,
  getLoyaltyBalance,
  listLoyaltyLedger,
  loadOwnCustomer,
  requireSession,
  type LoyaltyBalance,
  type LoyaltyLedgerRow,
} from "@/lib/customer-storefront";
import { createWebClient } from "@/lib/supabase";
import styles from "@/components/account.module.css";

type Status =
  | { kind: "loading" }
  | { kind: "auth" }
  | { kind: "error"; message: string }
  | { kind: "nocustomer" }
  | {
      kind: "ready";
      balance: LoyaltyBalance;
      ledger: LoyaltyLedgerRow[];
    };

export function LoyaltyPanel() {
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
    const customer = await loadOwnCustomer(client);
    if (!customer.ok) {
      setStatus({ kind: "error", message: customer.error });
      return;
    }
    if (!customer.data) {
      setStatus({ kind: "nocustomer" });
      return;
    }

    const [balance, ledger] = await Promise.all([
      getLoyaltyBalance(client, customer.data.id),
      listLoyaltyLedger(client, customer.data.id),
    ]);
    if (!balance.ok) {
      setStatus({ kind: "error", message: balance.error });
      return;
    }
    if (!ledger.ok) {
      setStatus({ kind: "error", message: ledger.error });
      return;
    }
    setStatus({
      kind: "ready",
      balance: balance.data,
      ledger: ledger.data,
    });
  }, []);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  if (status.kind === "loading") {
    return <p className={styles.muted}>Loading loyalty…</p>;
  }
  if (status.kind === "auth") {
    return (
      <p className={styles.lede}>
        <Link href="/login">Sign in</Link> to see your points balance.
      </p>
    );
  }
  if (status.kind === "nocustomer") {
    return (
      <p className={styles.lede}>
        No customer profile linked to this account yet — loyalty activates after
        the first storefront checkout or staff link.
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

  const { balance, ledger } = status;
  return (
    <div>
      <p className={styles.lede}>
        <strong>{Number(balance.points_balance).toFixed(0)} pts</strong>
        {" · "}
        est. value{" "}
        {formatMoney(Number(balance.estimated_liability), balance.currency)}
        {" · "}
        {formatMoney(Number(balance.liability_per_point), balance.currency)}
        /pt
      </p>
      {ledger.length === 0 ? (
        <p className={styles.muted}>No loyalty movements yet.</p>
      ) : (
        <ul className={styles.list}>
          {ledger.map((row) => (
            <li key={row.id}>
              <strong>{row.movement}</strong>
              {" · "}
              {Number(row.points) > 0 ? "+" : ""}
              {Number(row.points).toFixed(0)} pts
              {" · bal "}
              {Number(row.points_balance_after).toFixed(0)}
              {row.reason ? (
                <>
                  <br />
                  <span className={styles.muted}>{row.reason}</span>
                </>
              ) : null}
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
