"use client";

import { ChatNavLink } from "@/components/chat-nav-link";
import { iconSizeLg, iconStroke, MessageCircle } from "@/components/icons";
import styles from "./chat-fab.module.css";

/** Floating live-chat entry — storefront chrome only. */
export function ChatFab() {
  return (
    <ChatNavLink
      href="/account/chat"
      className={styles.fab}
      label="Chat"
      icon={
        <MessageCircle size={iconSizeLg} strokeWidth={iconStroke} aria-hidden />
      }
    />
  );
}
