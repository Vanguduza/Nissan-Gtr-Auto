"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import styles from "@/components/account.module.css";
import {
  listOnlinePrepQueue,
  listStaffOpsNotifications,
  markStaffOpsNotificationRead,
  type OnlinePrepQueueRow,
  type StaffOpsNotification,
} from "@/lib/staff-ops";
import { requireSession } from "@/lib/staff-pos";
import { createWebClient } from "@/lib/supabase";

type Boot =
  | { kind: "loading" }
  | { kind: "auth" }
  | { kind: "error"; message: string }
  | {
      kind: "ready";
      queue: OnlinePrepQueueRow[];
      notifications: StaffOpsNotification[];
    };

export function StaffOnlinePrepPanel() {
  const [boot, setBoot] = useState<Boot>({ kind: "loading" });
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<string | null>(null);

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
    const [queue, notes] = await Promise.all([
      listOnlinePrepQueue(client),
      listStaffOpsNotifications(client),
    ]);
    if (!queue.ok) {
      setBoot({ kind: "error", message: queue.error });
      return;
    }
    if (!notes.ok) {
      setBoot({ kind: "error", message: notes.error });
      return;
    }
    setBoot({
      kind: "ready",
      queue: queue.data,
      notifications: notes.data,
    });
  }, []);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  async function onMarkRead(id: string) {
    setBusy(true);
    setMessage(null);
    try {
      const client = createWebClient();
      if (!client) return;
      const res = await markStaffOpsNotificationRead(client, id);
      if (!res.ok) {
        setMessage(res.error);
        return;
      }
      await refresh();
    } finally {
      setBusy(false);
    }
  }

  if (boot.kind === "loading") {
    return <p className={styles.muted}>Loading prep queue…</p>;
  }
  if (boot.kind === "auth") {
    return (
      <p className={styles.lede}>
        <Link href="/login">Sign in</Link> with sales / warehouse / admin.
      </p>
    );
  }
  if (boot.kind === "error") {
    return (
      <p className={styles.lede} role="alert">
        {boot.message}{" "}
        <button type="button" className={styles.btnGhost} onClick={() => void refresh()}>
          Retry
        </button>
      </p>
    );
  }

  const unread = boot.notifications.filter((n) => !n.read_at);

  return (
    <div className={styles.form}>
      {message ? (
        <p className={styles.formStatus} role="status">
          {message}
        </p>
      ) : null}

      <fieldset className={styles.fieldset}>
        <legend className={styles.legend}>Notifications</legend>
        {unread.length === 0 ? (
          <p className={styles.muted}>No unread prep / assign alerts.</p>
        ) : (
          <ul className={styles.navList}>
            {unread.map((n) => (
              <li key={n.id} style={{ marginBottom: "0.65rem" }}>
                <strong>{n.title}</strong>
                <p className={styles.muted} style={{ margin: "0.2rem 0" }}>
                  {n.body}
                </p>
                <button
                  type="button"
                  className={styles.btnGhost}
                  disabled={busy}
                  onClick={() => void onMarkRead(n.id)}
                >
                  Mark read
                </button>
              </li>
            ))}
          </ul>
        )}
      </fieldset>

      <fieldset className={styles.fieldset}>
        <legend className={styles.legend}>Online dispatch prep queue</legend>
        <p className={styles.muted} style={{ marginBottom: "0.75rem" }}>
          Storefront dispatch orders awaiting pick, ship, or driver assignment.
          Confirm pick in{" "}
          <Link href="/staff/logistics">Jobs / pick</Link> — driver auto-assigns
          when prep completes.
        </p>
        {boot.queue.length === 0 ? (
          <p className={styles.muted}>Queue empty.</p>
        ) : (
          <table className={styles.table}>
            <thead>
              <tr>
                <th>Invoice</th>
                <th>Total</th>
                <th>Pick</th>
                <th>Job</th>
                <th>Driver</th>
              </tr>
            </thead>
            <tbody>
              {boot.queue.map((row) => (
                <tr key={row.invoice_id}>
                  <td>
                    {row.document_number ?? row.invoice_id.slice(0, 8)}
                    <div className={styles.muted}>
                      {row.posted_at
                        ? new Date(row.posted_at).toLocaleString()
                        : "—"}
                    </div>
                  </td>
                  <td>
                    {row.currency} {Number(row.total).toFixed(2)}
                  </td>
                  <td>{row.pick_status ?? "none"}</td>
                  <td>{row.delivery_job_status ?? "none"}</td>
                  <td>
                    {row.assignee_user_id
                      ? row.assignee_user_id.slice(0, 8)
                      : "unassigned"}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
        <div className={styles.formActions}>
          <button
            type="button"
            className={styles.btnGhost}
            disabled={busy}
            onClick={() => void refresh()}
          >
            Refresh
          </button>
        </div>
      </fieldset>
    </div>
  );
}
