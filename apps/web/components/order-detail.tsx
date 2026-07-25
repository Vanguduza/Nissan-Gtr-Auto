"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import { CustomerDeliveryTrackPanel } from "@/components/customer-delivery-track-panel";
import {
  createCustomerContipayIntent,
  createCustomerPaynowIntent,
  formatMoney,
  fulfillmentLabel,
  getCustomerOrder,
  requireSession,
  type CustomerOrder,
} from "@/lib/customer-storefront";
import { createWebClient } from "@/lib/supabase";
import styles from "@/components/account.module.css";
import trackStyles from "@/app/track/[token]/track.module.css";

type Status =
  | { kind: "loading" }
  | { kind: "auth" }
  | { kind: "error"; message: string }
  | { kind: "ready"; order: CustomerOrder };

export function OrderDetail({ invoiceId }: { invoiceId: string }) {
  const [status, setStatus] = useState<Status>({ kind: "loading" });
  const [busy, setBusy] = useState<"contipay" | "paynow" | null>(null);
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

    const order = await getCustomerOrder(client, invoiceId);
    if (!order.ok) {
      setStatus({ kind: "error", message: order.error });
      return;
    }
    setStatus({ kind: "ready", order: order.data });
  }, [invoiceId]);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  async function pay(rail: "contipay" | "paynow") {
    setBusy(rail);
    setMessage(null);
    const client = createWebClient();
    if (!client) {
      setMessage("Supabase is not configured.");
      setBusy(null);
      return;
    }

    const result =
      rail === "contipay"
        ? await createCustomerContipayIntent(client, invoiceId)
        : await createCustomerPaynowIntent(client, invoiceId);

    setBusy(null);
    if (!result.ok) {
      setMessage(result.error);
      return;
    }
    if (result.data.checkoutUrl) {
      window.location.assign(result.data.checkoutUrl);
      return;
    }
    setMessage(
      `${rail === "contipay" ? "ContiPay" : "Paynow"} intent ${result.data.intentId} created. Settlement confirms via webhook — return URL is /checkout/return when the provider redirects.`,
    );
    await refresh();
  }

  if (status.kind === "loading") {
    return <p className={styles.muted}>Loading order…</p>;
  }

  if (status.kind === "auth") {
    return (
      <p className={styles.lede}>
        <Link href="/login">Sign in</Link> to view this order.
      </p>
    );
  }

  if (status.kind === "error") {
    return (
      <p className={styles.lede} role="alert">
        {status.message}
      </p>
    );
  }

  const { order } = status;
  const steps = [
    { label: "Order received", done: true },
    {
      label: "Picked",
      done: Boolean(
        order.pick_list_status &&
          ["picked", "completed", "done"].includes(order.pick_list_status),
      ),
    },
    {
      label:
        order.fulfillment_mode === "immediate"
          ? "Ready for counter pickup"
          : "Out for delivery",
      done: Boolean(
        order.delivery_note_status &&
          ["dispatched", "delivered", "completed"].includes(
            order.delivery_note_status,
          ),
      ),
    },
    {
      label: order.fulfillment_mode === "immediate" ? "Collected" : "Delivered",
      done: order.delivery_note_status === "delivered",
    },
  ];

  return (
    <>
      <h1 className={styles.title}>
        {order.document_number ?? order.invoice_id}
      </h1>
      <p className={styles.lede}>
        Fulfillment: <strong>{fulfillmentLabel(order.fulfillment_mode)}</strong>
        {" · "}
        Status: <strong>{order.status}</strong>
        {" · "}
        {formatMoney(order.total, order.currency)}
        {order.amount_open > 0
          ? ` · open ${formatMoney(order.amount_open, order.currency)}`
          : " · paid"}
      </p>
      {order.pick_list_status || order.delivery_note_status ? (
        <p className={styles.muted}>
          Pick: {order.pick_list_status ?? "—"} · Delivery:{" "}
          {order.delivery_note_status ?? "—"}
        </p>
      ) : null}

      <ol className={styles.list}>
        {steps.map((step) => (
          <li key={step.label} className={step.done ? undefined : styles.muted}>
            {step.done ? <strong>{step.label}</strong> : step.label}
          </li>
        ))}
      </ol>

      {order.amount_open > 0 ? (
        <div className={styles.formActions}>
          <button
            type="button"
            className={styles.btn}
            disabled={busy !== null}
            onClick={() => void pay("contipay")}
          >
            {busy === "contipay" ? "Starting…" : "Pay with ContiPay"}
          </button>
          <button
            type="button"
            className={styles.btn}
            disabled={busy !== null}
            onClick={() => void pay("paynow")}
          >
            {busy === "paynow" ? "Starting…" : "Pay with Paynow"}
          </button>
        </div>
      ) : null}

      {message ? (
        <p className={styles.formStatus} role="status">
          {message}
        </p>
      ) : null}

      {order.active_delivery_job_id ? (
        <section
          className={trackStyles.card}
          style={{ marginTop: "1.25rem" }}
          aria-label="Live delivery tracking"
        >
          <h2 className={trackStyles.title} style={{ fontSize: "1.15rem" }}>
            Live delivery
          </h2>
          <p className={trackStyles.lede}>
            Last known location and ETA while out for delivery. Historical GPS
            trail is never shown.
          </p>
          <CustomerDeliveryTrackPanel jobId={order.active_delivery_job_id} />
        </section>
      ) : null}

      <Link href="/account/orders" className={styles.btn} style={{ marginTop: "1rem" }}>
        Back to orders
      </Link>
    </>
  );
}
