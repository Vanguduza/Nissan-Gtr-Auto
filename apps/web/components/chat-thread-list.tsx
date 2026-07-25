"use client";

import type { ChatThread } from "@/lib/chat";
import { formatChatTime, threadPreview } from "@/lib/chat";
import styles from "@/components/chat.module.css";

function statusClass(status: ChatThread["status"]): string {
  if (status === "open") return styles.statusOpen;
  if (status === "assigned") return styles.statusAssigned;
  return styles.statusClosed;
}

export function ChatThreadList({
  threads,
  selectedId,
  onSelect,
  emptyLabel = "No threads yet.",
}: {
  threads: ChatThread[];
  selectedId: string | null;
  onSelect: (id: string) => void;
  emptyLabel?: string;
}) {
  if (!threads.length) {
    return <p className={styles.emptyList}>{emptyLabel}</p>;
  }

  return (
    <ul className={styles.threadList}>
      {threads.map((t) => {
        const active = t.id === selectedId;
        return (
          <li key={t.id}>
            <button
              type="button"
              className={active ? styles.threadItemActive : styles.threadItem}
              onClick={() => onSelect(t.id)}
            >
              <span className={styles.threadTitle}>{threadPreview(t)}</span>
              <span className={styles.threadMeta}>
                <span
                  className={`${styles.statusPill} ${statusClass(t.status)}`}
                >
                  {t.status}
                </span>
                <span>{t.kind}</span>
                <span>{formatChatTime(t.last_message_at ?? t.created_at)}</span>
              </span>
            </button>
          </li>
        );
      })}
    </ul>
  );
}
