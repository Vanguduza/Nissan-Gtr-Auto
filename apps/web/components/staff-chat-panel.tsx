"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { ChatComposer } from "@/components/chat-composer";
import { ChatMessageBubbles } from "@/components/chat-message-bubbles";
import { ChatThreadList } from "@/components/chat-thread-list";
import { useStaffAuth } from "@/components/staff-auth-context";
import styles from "@/components/chat.module.css";
import accountStyles from "@/components/account.module.css";
import {
  chatMessagesInsertChannel,
  chatThreadsChangeChannel,
  claimChatThread,
  closeChatThread,
  fetchChatUnreadCount,
  listStaffThreads,
  listThreadMessages,
  markChatThreadRead,
  postChatMessage,
  threadPreview,
  type ChatMessage,
  type ChatThread,
  type StaffChatFilter,
} from "@/lib/chat";
import { createWebClient } from "@/lib/supabase";

const FILTERS: { id: StaffChatFilter; label: string }[] = [
  { id: "open", label: "Open" },
  { id: "mine", label: "Mine" },
  { id: "closed", label: "Closed" },
];

export function StaffChatPanel() {
  const ctx = useStaffAuth();
  const userId = ctx?.userId ?? "";
  const [filter, setFilter] = useState<StaffChatFilter>("open");
  const [threads, setThreads] = useState<ChatThread[]>([]);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [busy, setBusy] = useState(false);
  const [sendBusy, setSendBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [unread, setUnread] = useState(0);
  const [loading, setLoading] = useState(true);
  const bubblesEnd = useRef<HTMLDivElement | null>(null);

  const refreshThreads = useCallback(
    async (nextFilter: StaffChatFilter = filter) => {
      const client = createWebClient();
      if (!client || !userId) return;
      const res = await listStaffThreads(client, nextFilter, userId);
      if (!res.ok) {
        setError(res.error);
        return;
      }
      setThreads(res.data);
      const ur = await fetchChatUnreadCount(client);
      if (ur.ok) setUnread(ur.data);
    },
    [filter, userId],
  );

  const openThread = useCallback(async (threadId: string) => {
    const client = createWebClient();
    if (!client) return;
    setSelectedId(threadId);
    setError(null);
    const res = await listThreadMessages(client, threadId);
    if (!res.ok) {
      setError(res.error);
      return;
    }
    setMessages(res.data);
    await markChatThreadRead(client, threadId);
    const ur = await fetchChatUnreadCount(client);
    if (ur.ok) setUnread(ur.data);
  }, []);

  useEffect(() => {
    if (!userId) return;
    let cancelled = false;
    void (async () => {
      setLoading(true);
      await refreshThreads(filter);
      if (!cancelled) setLoading(false);
    })();
    return () => {
      cancelled = true;
    };
  }, [userId, filter, refreshThreads]);

  useEffect(() => {
    if (!userId) return;
    const client = createWebClient();
    if (!client) return;

    const channel = chatThreadsChangeChannel(client, () => {
      void refreshThreads();
    });
    channel.subscribe();

    return () => {
      void client.removeChannel(channel);
    };
  }, [userId, refreshThreads]);

  useEffect(() => {
    if (!selectedId || !userId) return;
    const client = createWebClient();
    if (!client) return;

    const channel = chatMessagesInsertChannel(client, selectedId, (msg) => {
      setMessages((prev) =>
        prev.some((m) => m.id === msg.id) ? prev : [...prev, msg],
      );
      void markChatThreadRead(client, selectedId).then(() =>
        fetchChatUnreadCount(client).then((ur) => {
          if (ur.ok) setUnread(ur.data);
        }),
      );
      void refreshThreads();
    });
    channel.subscribe();

    return () => {
      void client.removeChannel(channel);
    };
  }, [selectedId, userId, refreshThreads]);

  useEffect(() => {
    bubblesEnd.current?.scrollIntoView({ behavior: "smooth" });
  }, [messages]);

  async function onClaim() {
    if (!selectedId) return;
    const client = createWebClient();
    if (!client) return;
    setBusy(true);
    setError(null);
    const res = await claimChatThread(client, selectedId);
    setBusy(false);
    if (!res.ok) {
      setError(res.error);
      return;
    }
    setFilter("mine");
    await refreshThreads("mine");
    await openThread(selectedId);
  }

  async function onClose() {
    if (!selectedId) return;
    const client = createWebClient();
    if (!client) return;
    setBusy(true);
    setError(null);
    const res = await closeChatThread(client, selectedId);
    setBusy(false);
    if (!res.ok) {
      setError(res.error);
      return;
    }
    await refreshThreads();
    await openThread(selectedId);
  }

  async function onSend(body: string) {
    if (!selectedId) return;
    const client = createWebClient();
    if (!client) return;
    setSendBusy(true);
    setError(null);
    const res = await postChatMessage(client, selectedId, body);
    setSendBusy(false);
    if (!res.ok) {
      setError(res.error);
      return;
    }
    const list = await listThreadMessages(client, selectedId);
    if (list.ok) setMessages(list.data);
    await refreshThreads();
  }

  if (!ctx) {
    return <p className={accountStyles.muted}>Checking staff access…</p>;
  }

  const selected = threads.find((t) => t.id === selectedId) ?? null;
  const closed = selected?.status === "closed";
  const canClaim =
    selected &&
    (selected.status === "open" ||
      (selected.status === "assigned" && selected.assigned_to !== userId));

  return (
    <div>
      {unread > 0 ? (
        <p className={accountStyles.muted}>
          Unread: <strong>{unread}</strong>
        </p>
      ) : null}
      <div className={styles.layout}>
        <aside className={styles.sidebar}>
          <div className={styles.sidebarHead}>
            <div className={styles.filters} role="tablist" aria-label="Filter">
              {FILTERS.map((f) => (
                <button
                  key={f.id}
                  type="button"
                  role="tab"
                  aria-selected={filter === f.id}
                  className={
                    filter === f.id ? styles.filterBtnActive : styles.filterBtn
                  }
                  onClick={() => {
                    setSelectedId(null);
                    setMessages([]);
                    setFilter(f.id);
                  }}
                >
                  {f.label}
                </button>
              ))}
            </div>
          </div>
          {loading ? (
            <p className={styles.emptyList}>Loading…</p>
          ) : (
            <ChatThreadList
              threads={threads}
              selectedId={selectedId}
              onSelect={(id) => void openThread(id)}
              emptyLabel={`No ${filter} threads.`}
            />
          )}
        </aside>
        <section className={styles.main}>
          {!selected ? (
            <div className={styles.mainEmpty}>
              <p className={styles.muted}>
                Select an open thread to claim and reply.
              </p>
            </div>
          ) : (
            <>
              <div className={styles.threadBar}>
                <h2 className={styles.threadBarTitle}>
                  {threadPreview(selected)}
                </h2>
                <div className={styles.threadActions}>
                  {canClaim ? (
                    <button
                      type="button"
                      className={styles.actionBtn}
                      disabled={busy}
                      onClick={() => void onClaim()}
                    >
                      Claim
                    </button>
                  ) : null}
                  {!closed ? (
                    <button
                      type="button"
                      className={styles.actionBtnDanger}
                      disabled={busy}
                      onClick={() => void onClose()}
                    >
                      Close
                    </button>
                  ) : null}
                </div>
              </div>
              <ChatMessageBubbles
                messages={messages}
                currentUserId={userId}
              />
              <div ref={bubblesEnd} />
              <ChatComposer
                disabled={closed}
                busy={sendBusy}
                onSend={onSend}
                placeholder={
                  closed ? "Thread closed" : "Reply to customer…"
                }
              />
            </>
          )}
        </section>
      </div>
      {error ? <p className={styles.error}>{error}</p> : null}
    </div>
  );
}
