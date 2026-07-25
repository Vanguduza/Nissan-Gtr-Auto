"use client";

import Link from "next/link";
import { useCallback, useEffect, useRef, useState } from "react";
import { ChatComposer } from "@/components/chat-composer";
import { ChatMessageBubbles } from "@/components/chat-message-bubbles";
import { ChatThreadList } from "@/components/chat-thread-list";
import styles from "@/components/chat.module.css";
import accountStyles from "@/components/account.module.css";
import {
  chatMessagesInsertChannel,
  fetchChatUnreadCount,
  listCustomerThreads,
  listThreadMessages,
  markChatThreadRead,
  postChatMessage,
  startChatThread,
  threadPreview,
  type ChatMessage,
  type ChatThread,
  type ChatThreadKind,
} from "@/lib/chat";
import { createWebClient, hasSupabaseEnv } from "@/lib/supabase";

type Status =
  | { kind: "loading" }
  | { kind: "auth" }
  | { kind: "error"; message: string }
  | { kind: "ready"; userId: string };

function readQueryDefaults(): {
  kind: ChatThreadKind;
  subject: string;
} {
  if (typeof window === "undefined") {
    return { kind: "support", subject: "" };
  }
  const sp = new URLSearchParams(window.location.search);
  const kind = sp.get("kind") === "parts" ? "parts" : "support";
  const subject = sp.get("subject")?.trim() ?? "";
  return { kind, subject };
}

export function CustomerChatPanel() {
  const [status, setStatus] = useState<Status>({ kind: "loading" });
  const [threads, setThreads] = useState<ChatThread[]>([]);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [busy, setBusy] = useState(false);
  const [sendBusy, setSendBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [unread, setUnread] = useState(0);
  const [kind, setKind] = useState<ChatThreadKind>("support");
  const [subject, setSubject] = useState("");
  const [firstBody, setFirstBody] = useState("");
  const bubblesEnd = useRef<HTMLDivElement | null>(null);

  const refreshThreads = useCallback(async (userId: string) => {
    const client = createWebClient();
    if (!client) return;
    const res = await listCustomerThreads(client);
    if (!res.ok) {
      setError(res.error);
      return;
    }
    setThreads(res.data);
    const ur = await fetchChatUnreadCount(client);
    if (ur.ok) setUnread(ur.data);
    void userId;
  }, []);

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
    const defaults = readQueryDefaults();
    setKind(defaults.kind);
    setSubject(defaults.subject);
  }, []);

  useEffect(() => {
    let cancelled = false;

    void (async () => {
      if (!hasSupabaseEnv()) {
        if (!cancelled) {
          setStatus({
            kind: "error",
            message:
              "Add NEXT_PUBLIC_SUPABASE_URL and ANON_KEY to .env.local for chat.",
          });
        }
        return;
      }
      const client = createWebClient();
      if (!client) {
        if (!cancelled) {
          setStatus({ kind: "error", message: "Supabase client unavailable." });
        }
        return;
      }
      const { data } = await client.auth.getSession();
      if (cancelled) return;
      if (!data.session) {
        setStatus({ kind: "auth" });
        return;
      }
      const userId = data.session.user.id;
      setStatus({ kind: "ready", userId });
      await refreshThreads(userId);
    })();

    return () => {
      cancelled = true;
    };
  }, [refreshThreads]);

  useEffect(() => {
    if (status.kind !== "ready" || !selectedId) return;
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
      void refreshThreads(status.userId);
    });
    channel.subscribe();

    return () => {
      void client.removeChannel(channel);
    };
  }, [selectedId, status, refreshThreads]);

  useEffect(() => {
    bubblesEnd.current?.scrollIntoView({ behavior: "smooth" });
  }, [messages]);

  async function onStart() {
    if (status.kind !== "ready") return;
    const client = createWebClient();
    if (!client) return;
    setBusy(true);
    setError(null);
    const res = await startChatThread(client, {
      kind,
      subject: subject.trim() || null,
      body: firstBody.trim() || null,
    });
    setBusy(false);
    if (!res.ok) {
      setError(res.error);
      return;
    }
    setFirstBody("");
    await refreshThreads(status.userId);
    await openThread(res.data);
  }

  async function onSend(body: string) {
    if (!selectedId || status.kind !== "ready") return;
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
    await refreshThreads(status.userId);
  }

  if (status.kind === "loading") {
    return <p className={accountStyles.muted}>Loading chat…</p>;
  }

  if (status.kind === "auth") {
    return (
      <div>
        <p className={accountStyles.lede}>
          Sign in to start a live support or parts chat with the counter.
        </p>
        <Link
          href={`/login?next=${encodeURIComponent("/account/chat")}`}
          className={accountStyles.btn}
        >
          Sign in
        </Link>
      </div>
    );
  }

  if (status.kind === "error") {
    return <p className={styles.error}>{status.message}</p>;
  }

  const selected = threads.find((t) => t.id === selectedId) ?? null;
  const closed = selected?.status === "closed";

  return (
    <div>
      {unread > 0 ? (
        <p className={accountStyles.muted}>
          Unread messages: <strong>{unread}</strong>
        </p>
      ) : null}
      <div className={styles.layout}>
        <aside className={styles.sidebar}>
          <div className={styles.startForm}>
            <div className={styles.startRow}>
              <select
                className={styles.startSelect}
                value={kind}
                onChange={(e) =>
                  setKind(e.target.value === "parts" ? "parts" : "support")
                }
                aria-label="Thread kind"
              >
                <option value="support">Support</option>
                <option value="parts">Parts</option>
              </select>
              <input
                className={styles.startInput}
                value={subject}
                onChange={(e) => setSubject(e.target.value)}
                placeholder="Subject (optional)"
                maxLength={200}
              />
            </div>
            <input
              className={styles.startInput}
              value={firstBody}
              onChange={(e) => setFirstBody(e.target.value)}
              placeholder="First message (optional)"
              maxLength={4000}
            />
            <button
              type="button"
              className={styles.startBtn}
              disabled={busy}
              onClick={() => void onStart()}
            >
              {busy ? "Starting…" : "New thread"}
            </button>
          </div>
          <ChatThreadList
            threads={threads}
            selectedId={selectedId}
            onSelect={(id) => void openThread(id)}
            emptyLabel="No threads yet — start one above."
          />
        </aside>
        <section className={styles.main}>
          {!selected ? (
            <div className={styles.mainEmpty}>
              <p className={styles.muted}>
                Select a thread or start a new support / parts chat.
              </p>
            </div>
          ) : (
            <>
              <div className={styles.threadBar}>
                <h2 className={styles.threadBarTitle}>
                  {threadPreview(selected)}
                </h2>
                <span
                  className={`${styles.statusPill} ${
                    selected.status === "open"
                      ? styles.statusOpen
                      : selected.status === "assigned"
                        ? styles.statusAssigned
                        : styles.statusClosed
                  }`}
                >
                  {selected.status}
                </span>
              </div>
              <ChatMessageBubbles
                messages={messages}
                currentUserId={status.userId}
              />
              <div ref={bubblesEnd} />
              <ChatComposer
                disabled={closed}
                busy={sendBusy}
                onSend={onSend}
                placeholder={
                  closed ? "Thread closed" : "Message the counter…"
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
