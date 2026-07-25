"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import { CustomerDeliveryTrackMap } from "@/components/customer-delivery-track-map";
import styles from "@/app/track/[token]/track.module.css";
import {
  configuredMapStyleUrl,
  fetchCustomerTrackPoint,
  formatEtaLabel,
  type CustomerTrackPoint,
} from "@/lib/customer-delivery-track";
import { createWebClient } from "@/lib/supabase";

const POLL_MS = 15_000;

type Props = { token: string };

type State =
  | { kind: "loading" }
  | { kind: "error"; message: string }
  | { kind: "empty" }
  | { kind: "ready"; point: CustomerTrackPoint };

export function CustomerDeliveryTrackPanel({ token }: Props) {
  const [state, setState] = useState<State>({ kind: "loading" });
  const mapStyle = configuredMapStyleUrl();

  const refresh = useCallback(async () => {
    const client = createWebClient();
    if (!client) {
      setState({
        kind: "error",
        message: "Tracking is unavailable on this environment.",
      });
      return;
    }
    const res = await fetchCustomerTrackPoint(client, token);
    if (!res.ok) {
      setState({ kind: "error", message: res.error });
      return;
    }
    if (!res.data) {
      setState({ kind: "empty" });
      return;
    }
    setState({ kind: "ready", point: res.data });
  }, [token]);

  useEffect(() => {
    void refresh();
    const id = window.setInterval(() => void refresh(), POLL_MS);
    return () => window.clearInterval(id);
  }, [refresh]);

  if (state.kind === "loading") {
    return <p className={styles.muted}>Loading live location…</p>;
  }

  if (state.kind === "error") {
    return (
      <p className={styles.alert} role="alert">
        {state.message}{" "}
        <button type="button" className={styles.linkBtn} onClick={() => void refresh()}>
          Retry
        </button>
      </p>
    );
  }

  if (state.kind === "empty") {
    return (
      <div className={styles.empty}>
        <p className={styles.lede} role="status">
          This tracking link is inactive, expired, or the delivery is not out
          for delivery yet. Last known location is only available while a job
          is actively dispatched.
        </p>
        <p className={styles.muted}>
          <Link href="/account/orders">View your orders</Link> ·{" "}
          <Link href="/">Back to shop</Link>
        </p>
      </div>
    );
  }

  const { point } = state;
  const eta = formatEtaLabel(point.eta_at, point.eta_seconds);

  return (
    <div className={styles.panelInner}>
      <dl className={styles.meta}>
        <div>
          <dt>Status</dt>
          <dd>Out for delivery</dd>
        </div>
        <div>
          <dt>ETA</dt>
          <dd>{eta ?? "Updating…"}</dd>
        </div>
        <div>
          <dt>Last update</dt>
          <dd>{new Date(point.recorded_at).toLocaleString()}</dd>
        </div>
      </dl>

      {mapStyle ? (
        <CustomerDeliveryTrackMap point={point} styleUrl={mapStyle} />
      ) : (
        <div className={styles.fallback} role="status">
          <p className={styles.lede}>
            Map tiles are not configured (
            <code>NEXT_PUBLIC_MAP_STYLE_URL</code>). Showing last coordinates
            only — no historical trail is ever shared.
          </p>
          <p className={styles.coords}>
            {point.lat.toFixed(5)}, {point.lng.toFixed(5)}
          </p>
        </div>
      )}

      <p className={styles.privacy}>
        Privacy: this page shows the driver&apos;s <strong>last point</strong>{" "}
        and ETA only. Full GPS history is never exposed to customers.
      </p>
    </div>
  );
}
