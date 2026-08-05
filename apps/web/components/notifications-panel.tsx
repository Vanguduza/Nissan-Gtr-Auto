"use client";

import { useState } from "react";
import styles from "@/components/account.module.css";

/**
 * KMP Notifications shell — mark-all-read is a no-op until inbox RPC exists.
 * TODO(backend): wire customer notification table / RPC.
 */
export function NotificationsPanel() {
  const [marked, setMarked] = useState(false);

  return (
    <div>
      <button
        type="button"
        className={styles.btnGhost}
        onClick={() => setMarked(true)}
      >
        Mark all read
      </button>
      {marked ? (
        <p className={styles.muted} role="status">
          Nothing to mark — inbox is empty until notifications are wired.
        </p>
      ) : (
        <p className={styles.muted}>No notifications yet.</p>
      )}
    </div>
  );
}
