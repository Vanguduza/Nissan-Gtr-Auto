"use client";

import type { ChatMessage } from "@/lib/chat";
import { formatChatTime } from "@/lib/chat";
import styles from "@/components/chat.module.css";

export function ChatMessageBubbles({
  messages,
  currentUserId,
}: {
  messages: ChatMessage[];
  currentUserId: string;
}) {
  if (!messages.length) {
    return <p className={styles.muted}>No messages yet. Say hello.</p>;
  }

  return (
    <div className={styles.bubbles} role="log" aria-live="polite">
      {messages.map((m) => {
        const mine = m.sender_user_id === currentUserId;
        const system = m.sender_kind === "system";
        const rowClass = system
          ? styles.bubbleRowSystem
          : mine
            ? `${styles.bubbleRow} ${styles.bubbleRowMine}`
            : `${styles.bubbleRow} ${styles.bubbleRowOther}`;
        const bubbleClass = system
          ? `${styles.bubble} ${styles.bubbleSystem}`
          : mine
            ? `${styles.bubble} ${styles.bubbleMine}`
            : `${styles.bubble} ${styles.bubbleOther}`;
        const who = system
          ? "System"
          : m.sender_kind === "staff"
            ? "Staff"
            : mine
              ? "You"
              : "Customer";
        return (
          <div key={m.id} className={rowClass}>
            <div className={bubbleClass}>{m.body}</div>
            <span className={styles.bubbleMeta}>
              {who}
              {m.created_at ? ` · ${formatChatTime(m.created_at)}` : null}
            </span>
          </div>
        );
      })}
    </div>
  );
}
