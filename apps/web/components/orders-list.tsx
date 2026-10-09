"use client";

import Link from "next/link";
import { useCallback, useEffect, useMemo, useState } from "react";
import {
  formatMoney,
  fulfillmentLabel,
  listOwnOrderSummaries,
  requireSession,
  type CustomerOrderListItem,
} from "@/lib/customer-storefront";
import { createWebClient } from "@/lib/supabase";
import styles from "@/components/account.module.css";

type Status =
  | { kind: "loading" }
  | { kind: "auth" }
  | { kind: "error"; message: string }
  | { kind: "ready"; orders: CustomerOrderListItem[] };

type OrderTab = "active" | "success" | "failed";

function orderTab(order: CustomerOrderListItem): OrderTab {
  if (["cancelled", "payment_expired"].includes(order.status)) return "failed";
  if (order.amount_open <= 0.001) return "success";
  return "active";
}

const TABS: { id: OrderTab; label: string }[] = [
  { id: "active", label: "Active" },
  { id: "success", label: "Paid" },
  { id: "failed", label: "Cancelled" },
];

export function OrdersList() {
  const [status, setStatus] = useState<Status>({ kind: "loading" });
  const [tab, setTab] = useState<OrderTab>("active");

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

    const orders = await listOwnOrderSummaries(client);
    if (!orders.ok) {
      setStatus({ kind: "error", message: orders.error });
      return;
    }
    setStatus({ kind: "ready", orders: orders.data });
  }, []);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  const filtered = useMemo(() => {
    if (status.kind !== "ready") return [];
    return status.orders.filter((order) => orderTab(order) === tab);
  }, [status, tab]);

  const counts = useMemo(() => {
    const c = { active: 0, success: 0, failed: 0 };
    if (status.kind !== "ready") return c;
    for (const order of status.orders) {
      c[orderTab(order)] += 1;
    }
    return c;
  }, [status]);

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

  if (status.orders.length === 0) {
    return <p className={styles.muted}>No orders yet.</p>;
  }

  return (
    <div>
      <div className={styles.orderTabs} role="tablist" aria-label="Order status">
        {TABS.map((t) => (
          <button
            key={t.id}
            type="button"
            role="tab"
            aria-selected={tab === t.id}
            className={tab === t.id ? styles.orderTabActive : styles.orderTab}
            onClick={() => setTab(t.id)}
          >
            {t.label}
            <span className={styles.orderTabCount}>{counts[t.id]}</span>
          </button>
        ))}
      </div>
      {filtered.length === 0 ? (
        <p className={styles.muted}>No {tab} orders.</p>
      ) : (
        <ul className={styles.list}>
          {filtered.map((order) => (
            <li key={order.id}>
              <strong>{order.document_number ?? order.id}</strong>
              {" · "}
              {fulfillmentLabel(order.fulfillment_mode)}
              {" · "}
              {order.status}
              <br />
              <span className={styles.muted}>
                {formatMoney(order.total, order.currency)}
                {order.amount_open > 0
                  ? ` · awaiting ${formatMoney(order.amount_open, order.currency)}`
                  : " · paid"}
                {order.reservation_expires_at && order.amount_open > 0
                  ? ` · reserved until ${new Date(order.reservation_expires_at).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })}`
                  : ""}
              </span>
              <br />
              <Link href={`/account/orders/${order.id}`} className={styles.btn}>
                View status
              </Link>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
