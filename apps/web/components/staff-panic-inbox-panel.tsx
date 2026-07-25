"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import styles from "@/components/account.module.css";
import {
  acknowledgePanicEvent,
  listPanicEvents,
  panicEventsChannel,
  requireSession,
  type PanicEventRow,
} from "@/lib/staff-delivery-tracking";
import { createWebClient } from "@/lib/supabase";

type Boot =
  | { kind: "loading" }
  | { kind: "auth" }
  | { kind: "error"; message: string }
  | { kind: "ready"; userId: string; events: PanicEventRow[] };

function shortId(id: string) {
  return id.slice(0, 8);
}

function mergePanic(prev: PanicEventRow[], next: PanicEventRow): PanicEventRow[] {
  const without = prev.filter((e) => e.id !== next.id);
  const merged = [next, ...without];
  merged.sort(
    (a, b) =>
      new Date(b.created_at).getTime() - new Date(a.created_at).getTime(),
  );
  return merged;
}

export function StaffPanicInboxPanel() {
  const [boot, setBoot] = useState<Boot>({ kind: "loading" });
  const [unackedOnly, setUnackedOnly] = useState(true);
  const [busyId, setBusyId] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [live, setLive] = useState(false);

  const refresh = useCallback(async () => {
    const client = createWebClient();
    if (!client) {
      setBoot({
        kind: "error",
        message: "Supabase is not configured on this environment.",
      });
      return;
    }
    const session = await requireSession(client);
    if (!session.ok) {
      setBoot({ kind: "auth" });
      return;
    }
    const events = await listPanicEvents(client, {
      unackedOnly,
      limit: 50,
    });
    if (!events.ok) {
      setBoot({ kind: "error", message: events.error });
      return;
    }
    setBoot({
      kind: "ready",
      userId: session.data.userId,
      events: events.data,
    });
  }, [unackedOnly]);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  useEffect(() => {
    if (boot.kind !== "ready") return;
    const client = createWebClient();
    if (!client) return;

    const channel = panicEventsChannel(client, {
      onInsert: (row) => {
        setBoot((prev) => {
          if (prev.kind !== "ready") return prev;
          if (unackedOnly && row.acknowledged_at) return prev;
          return { ...prev, events: mergePanic(prev.events, row) };
        });
      },
      onUpdate: (row) => {
        setBoot((prev) => {
          if (prev.kind !== "ready") return prev;
          if (unackedOnly && row.acknowledged_at) {
            return {
              ...prev,
              events: prev.events.filter((e) => e.id !== row.id),
            };
          }
          return { ...prev, events: mergePanic(prev.events, row) };
        });
      },
    });

    channel.subscribe((status) => {
      setLive(status === "SUBSCRIBED");
    });

    return () => {
      setLive(false);
      void client.removeChannel(channel);
    };
  }, [boot.kind, unackedOnly]);

  async function onAck(id: string) {
    if (boot.kind !== "ready") return;
    const client = createWebClient();
    if (!client) return;
    setBusyId(id);
    setMessage(null);
    const res = await acknowledgePanicEvent(client, id, boot.userId);
    setBusyId(null);
    if (!res.ok) {
      setMessage(res.error);
      return;
    }
    setMessage(`Acknowledged ${shortId(id)}.`);
    await refresh();
  }

  if (boot.kind === "loading") {
    return <p className={styles.muted}>Loading panic events…</p>;
  }

  if (boot.kind === "auth") {
    return (
      <p className={styles.lede} role="status">
        <Link href="/login">Sign in</Link> as admin, warehouse, or dispatcher
        to view the panic inbox.
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

  return (
    <div className={styles.form}>
      <fieldset className={styles.fieldset}>
        <legend className={styles.legend}>Filters</legend>
        <p className={styles.muted} style={{ marginBottom: "0.75rem" }}>
          Driver SOS from <code>panic_events</code> (Realtime). Support phone
          lives in delivery-app env — web is acknowledge-only.{" "}
          {live ? "Realtime on." : "Realtime off."}
        </p>
        <label
          className={styles.field}
          style={{
            display: "flex",
            flexDirection: "row",
            alignItems: "center",
            gap: "0.5rem",
          }}
        >
          <input
            type="checkbox"
            checked={unackedOnly}
            onChange={(e) => setUnackedOnly(e.target.checked)}
          />
          Unacknowledged only
        </label>
      </fieldset>

      {boot.events.length === 0 ? (
        <p className={styles.muted} role="status">
          No panic events{unackedOnly ? " waiting" : ""}.
        </p>
      ) : (
        <ul className={styles.list}>
          {boot.events.map((ev) => (
            <li key={ev.id}>
              <strong>{new Date(ev.created_at).toLocaleString()}</strong>
              <br />
              <span className={styles.muted}>
                Driver {shortId(ev.driver_user_id)}
                {ev.delivery_job_id
                  ? ` · job ${shortId(ev.delivery_job_id)}`
                  : ""}
                {ev.lat != null && ev.lng != null
                  ? ` · ${ev.lat.toFixed(5)}, ${ev.lng.toFixed(5)}`
                  : ""}
                {ev.acknowledged_at
                  ? ` · acked ${new Date(ev.acknowledged_at).toLocaleString()}`
                  : ""}
              </span>
              {!ev.acknowledged_at ? (
                <div
                  className={styles.formActions}
                  style={{ marginTop: "0.45rem" }}
                >
                  <button
                    type="button"
                    className={styles.btn}
                    disabled={busyId === ev.id}
                    onClick={() => void onAck(ev.id)}
                  >
                    Acknowledge
                  </button>
                  {ev.delivery_job_id ? (
                    <Link
                      href="/staff/logistics/tracking"
                      className={styles.btnGhost}
                      style={{ display: "inline-flex", alignItems: "center" }}
                    >
                      Open live map
                    </Link>
                  ) : null}
                </div>
              ) : null}
            </li>
          ))}
        </ul>
      )}

      {message ? (
        <p className={styles.lede} role="status">
          {message}
        </p>
      ) : null}
    </div>
  );
}
