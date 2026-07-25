"use client";

import { FormEvent, useState } from "react";
import styles from "@/components/chat.module.css";

export function ChatComposer({
  disabled,
  busy,
  onSend,
  placeholder = "Type a message…",
}: {
  disabled?: boolean;
  busy?: boolean;
  onSend: (body: string) => Promise<void> | void;
  placeholder?: string;
}) {
  const [text, setText] = useState("");

  async function submit(e: FormEvent) {
    e.preventDefault();
    const body = text.trim();
    if (!body || disabled || busy) return;
    setText("");
    await onSend(body);
  }

  return (
    <form className={styles.composer} onSubmit={(e) => void submit(e)}>
      <textarea
        className={styles.composerInput}
        value={text}
        onChange={(e) => setText(e.target.value)}
        placeholder={placeholder}
        disabled={disabled || busy}
        rows={2}
        maxLength={4000}
        aria-label="Message"
      />
      <button
        type="submit"
        className={styles.sendBtn}
        disabled={disabled || busy || !text.trim()}
      >
        {busy ? "…" : "Send"}
      </button>
    </form>
  );
}
