"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import styles from "@/components/chat.module.css";
import { fetchChatUnreadCount } from "@/lib/chat";
import { createWebClient, hasSupabaseEnv } from "@/lib/supabase";

/** Header / nav unread pill via `chat_unread_count`. */
export function ChatNavLink({
  href = "/account/chat",
  className,
  label = "Chat",
  icon,
}: {
  href?: string;
  className?: string;
  label?: string;
  icon?: React.ReactNode;
}) {
  const [unread, setUnread] = useState(0);
  const [signedIn, setSignedIn] = useState(false);

  useEffect(() => {
    if (!hasSupabaseEnv()) return;
    const client = createWebClient();
    if (!client) return;

    let cancelled = false;

    async function refresh() {
      if (!client) return;
      const { data } = await client.auth.getSession();
      if (!data.session) {
        if (!cancelled) {
          setSignedIn(false);
          setUnread(0);
        }
        return;
      }
      if (!cancelled) setSignedIn(true);
      const ur = await fetchChatUnreadCount(client);
      if (!cancelled && ur.ok) setUnread(ur.data);
    }

    void refresh();
    const { data: sub } = client.auth.onAuthStateChange(() => {
      void refresh();
    });

    const interval = window.setInterval(() => void refresh(), 45_000);

    return () => {
      cancelled = true;
      sub.subscription.unsubscribe();
      window.clearInterval(interval);
    };
  }, []);

  const target = signedIn
    ? href
    : `/login?next=${encodeURIComponent(href)}`;

  return (
    <Link href={target} className={className ?? styles.headerChat}>
      {icon}
      <span>{label}</span>
      {unread > 0 ? (
        <span className={styles.badge} aria-label={`${unread} unread`}>
          {unread > 99 ? "99+" : unread}
        </span>
      ) : null}
    </Link>
  );
}
