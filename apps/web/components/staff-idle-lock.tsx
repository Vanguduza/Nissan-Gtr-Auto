"use client";

import {
  FormEvent,
  useCallback,
  useEffect,
  useRef,
  useState,
  type ReactNode,
} from "react";
import { STAFF_IDLE_LOCK_MS } from "@/lib/staff-auth";
import { createWebClient } from "@/lib/supabase";
import styles from "./staff-idle-lock.module.css";

const ACTIVITY_EVENTS = [
  "pointerdown",
  "keydown",
  "scroll",
  "touchstart",
] as const;

const POLL_MS = 5_000;

/**
 * Staff-shell idle lock (web parity with Android IdleSessionHost).
 * After {@link STAFF_IDLE_LOCK_MS} of inactivity: remount children (clear
 * sensitive in-memory UI) and show in-app password reauth — never leave staff shell.
 */
export function StaffIdleLock({
  enabled,
  children,
}: {
  enabled: boolean;
  children: ReactNode;
}) {
  const [locked, setLocked] = useState(false);
  const [contentGeneration, setContentGeneration] = useState(0);
  const lastActiveAt = useRef(Date.now());

  const bump = useCallback(() => {
    if (!locked) lastActiveAt.current = Date.now();
  }, [locked]);

  const lock = useCallback(() => {
    setLocked(true);
    setContentGeneration((g) => g + 1);
  }, []);

  useEffect(() => {
    if (!enabled || locked) return;

    const onActivity = () => bump();
    for (const ev of ACTIVITY_EVENTS) {
      window.addEventListener(ev, onActivity, { passive: true, capture: true });
    }

    const timer = window.setInterval(() => {
      if (Date.now() - lastActiveAt.current >= STAFF_IDLE_LOCK_MS) {
        lock();
      }
    }, POLL_MS);

    return () => {
      for (const ev of ACTIVITY_EVENTS) {
        window.removeEventListener(ev, onActivity, true);
      }
      window.clearInterval(timer);
    };
  }, [enabled, locked, bump, lock]);

  if (!enabled) {
    return <>{children}</>;
  }

  return (
    <div className={styles.host}>
      <div key={contentGeneration}>{children}</div>
      {locked ? (
        <IdleLockOverlay
          onUnlocked={() => {
            setLocked(false);
            lastActiveAt.current = Date.now();
          }}
        />
      ) : null}
    </div>
  );
}

function IdleLockOverlay({ onUnlocked }: { onUnlocked: () => void }) {
  const [email, setEmail] = useState<string | null>(null);
  const [password, setPassword] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    void (async () => {
      const client = createWebClient();
      if (!client) return;
      const { data } = await client.auth.getUser();
      if (!cancelled) {
        setEmail(data.user?.email ?? null);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, []);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    if (!password.trim()) return;
    setBusy(true);
    setError(null);
    const client = createWebClient();
    if (!client) {
      setBusy(false);
      setError("Supabase is not configured.");
      return;
    }
    if (!email) {
      setBusy(false);
      setError("No signed-in account");
      return;
    }
    const { error: authError } = await client.auth.signInWithPassword({
      email,
      password,
    });
    setBusy(false);
    if (authError) {
      setError("Reauthentication failed");
      return;
    }
    setPassword("");
    onUnlocked();
  }

  return (
    <div
      className={styles.overlay}
      role="dialog"
      aria-modal="true"
      aria-labelledby="staff-idle-lock-title"
    >
      <form className={styles.card} onSubmit={(e) => void onSubmit(e)}>
        <h2 id="staff-idle-lock-title" className={styles.title}>
          Session locked
        </h2>
        <p className={styles.subtitle}>
          Inactivity timeout — reauthenticate to continue. You stay in staff.
        </p>
        {email ? <p className={styles.email}>{email}</p> : null}
        <label className={styles.label}>
          Password
          <input
            className={styles.input}
            type="password"
            autoComplete="current-password"
            value={password}
            onChange={(e) => {
              setPassword(e.target.value);
              setError(null);
            }}
            disabled={busy}
            autoFocus
            required
          />
        </label>
        <button
          className={styles.submit}
          type="submit"
          disabled={busy || !password.trim()}
        >
          {busy ? "Unlocking…" : "Unlock"}
        </button>
        {error ? (
          <p className={styles.error} role="alert">
            {error}
          </p>
        ) : null}
      </form>
    </div>
  );
}
