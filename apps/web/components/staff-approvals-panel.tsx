"use client";

import Link from "next/link";
import { useCallback, useEffect, useRef, useState } from "react";
import styles from "@/components/account.module.css";
import {
  APPROVAL_KIND_LABEL,
  dismissApprovalAlert,
  listMyApprovals,
  requireSession,
  waitedFor,
  type ApprovalsInbox,
} from "@/lib/staff-approvals";
import { createWebClient } from "@/lib/supabase";

type Boot =
  | { kind: "loading" }
  | { kind: "auth" }
  | { kind: "error"; message: string }
  | { kind: "ready"; inbox: ApprovalsInbox };

const REFRESH_MS = 30_000;

function money(amount: number | null, currency: string | null) {
  if (amount == null) return null;
  return currency ? `${currency} ${amount.toFixed(2)}` : String(amount);
}

/**
 * Everything waiting for a decision this person can take, most urgent first, each linking to the
 * screen where it is decided. Refreshes every 30 s; a new urgent item raises a browser notification
 * when the person allowed them. Alerts are items that waited too long (sent every 5 minutes).
 */
export function StaffApprovalsPanel() {
  const [boot, setBoot] = useState<Boot>({ kind: "loading" });
  const [notify, setNotify] = useState<NotificationPermission | "unsupported">("default");
  const seenUrgent = useRef<Set<string> | null>(null);

  const refresh = useCallback(async () => {
    const client = createWebClient();
    if (!client) {
      setBoot({ kind: "error", message: "Supabase is not configured on this environment." });
      return;
    }
    const session = await requireSession(client);
    if (!session.ok) {
      setBoot({ kind: "auth" });
      return;
    }
    const res = await listMyApprovals(client);
    if (!res.ok) {
      setBoot({ kind: "error", message: res.error });
      return;
    }
    // New urgent items since the last look → browser notification (not on first load).
    const urgent = res.data.items.filter((i) => i.urgent);
    const seen = seenUrgent.current;
    if (seen && typeof Notification !== "undefined" && Notification.permission === "granted") {
      for (const i of urgent) {
        if (!seen.has(i.kind + i.ref)) new Notification(i.title, { body: i.detail, tag: i.kind + i.ref });
      }
    }
    seenUrgent.current = new Set(urgent.map((i) => i.kind + i.ref));
    setBoot({ kind: "ready", inbox: res.data });
  }, []);

  useEffect(() => {
    setNotify(typeof Notification === "undefined" ? "unsupported" : Notification.permission);
    void refresh();
    const t = window.setInterval(() => void refresh(), REFRESH_MS);
    return () => window.clearInterval(t);
  }, [refresh]);

  async function dismiss(id: string) {
    const client = createWebClient();
    if (!client) return;
    await dismissApprovalAlert(client, id);
    await refresh();
  }

  if (boot.kind === "loading") return <p className={styles.muted}>Loading approvals…</p>;
  if (boot.kind === "auth") {
    return (
      <p className={styles.lede} role="status">
        <Link href="/login">Sign in</Link> to see what is waiting for you.
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

  const { items, alerts } = boot.inbox;
  const urgent = items.filter((i) => i.urgent);
  const other = items.filter((i) => !i.urgent);

  return (
    <div className={styles.form}>
      {notify === "default" ? (
        <p className={styles.muted}>
          <button
            type="button"
            className={styles.btnGhost}
            onClick={async () => setNotify(await Notification.requestPermission())}
          >
            Turn on desktop alerts
          </button>{" "}
          to hear about urgent items (a driver waiting, a card payment to reconcile) while this page is open.
        </p>
      ) : null}

      {alerts.length > 0 ? (
        <>
          <h2 className={styles.sectionTitle}>Waiting too long</h2>
          <ul className={styles.list}>
            {alerts.map((a) => (
              <li key={a.id}>
                <strong>{a.title}</strong>
                <br />
                <span className={styles.muted}>{a.body.replace(/_/g, " ")}</span>
                <div className={styles.formActions} style={{ marginTop: "0.4rem" }}>
                  {a.href ? (
                    <Link href={a.href} className={styles.btn}>
                      Open
                    </Link>
                  ) : null}
                  <button type="button" className={styles.btnGhost} onClick={() => void dismiss(a.id)}>
                    Dismiss
                  </button>
                </div>
              </li>
            ))}
          </ul>
        </>
      ) : null}

      {items.length === 0 ? (
        <p className={styles.lede} role="status">
          Nothing is waiting for you.
        </p>
      ) : null}

      {[
        { label: "Urgent", rows: urgent },
        { label: "Waiting for you", rows: other },
      ]
        .filter((g) => g.rows.length > 0)
        .map((g) => (
          <section key={g.label}>
            <h2 className={styles.sectionTitle}>
              {g.label} ({g.rows.length})
            </h2>
            <ul className={styles.list}>
              {g.rows.map((i) => (
                <li key={i.kind + i.ref}>
                  {i.urgent ? <span className={styles.urgentTag}>Urgent</span> : null}
                  <strong>{i.title}</strong>
                  {money(i.amount, i.currency) ? <> · {money(i.amount, i.currency)}</> : null}
                  <br />
                  <span className={styles.muted}>
                    {[
                      i.title.startsWith(APPROVAL_KIND_LABEL[i.kind] ?? "\u0000") ? null : APPROVAL_KIND_LABEL[i.kind],
                      i.detail.replace(/_/g, " "),
                      `waiting ${waitedFor(i.waitingSince)}`,
                    ]
                      .filter(Boolean)
                      .join(" · ")}
                  </span>
                  <div style={{ marginTop: "0.35rem" }}>
                    <Link href={i.href} className={styles.btn}>
                      Open
                    </Link>
                  </div>
                </li>
              ))}
            </ul>
          </section>
        ))}
    </div>
  );
}
