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
import {
  evaluateStaffIdleLock,
  readStaffIdleLockStorage,
  writeStaffIdleLockStorage,
} from "@/lib/staff-idle-lock-state";
import { createWebClient } from "@/lib/supabase";
import styles from "./staff-idle-lock.module.css";

const ACTIVITY_EVENTS = [
  "pointerdown",
  "keydown",
  "scroll",
  "touchstart",
] as const;

const POLL_MS = 5_000;
/** Throttle sessionStorage writes on activity (still update in-memory immediately). */
const PERSIST_ACTIVITY_MS = 5_000;

type Phase = "boot" | "active" | "locked";

/**
 * Staff-shell idle lock (web parity with Android IdleSessionHost).
 * After {@link STAFF_IDLE_LOCK_MS} of inactivity: remount children (clear
 * sensitive in-memory UI) and show in-app password reauth — never leave staff shell.
 *
 * Idle / locked state is persisted in sessionStorage so a full page reload cannot
 * silently reopen staff UI while GoTrue still auto-refreshes the access token.
 */
export function StaffIdleLock({
  enabled,
  children,
}: {
  enabled: boolean;
  children: ReactNode;
}) {
  const [phase, setPhase] = useState<Phase>(enabled ? "boot" : "active");
  const [contentGeneration, setContentGeneration] = useState(0);
  const lastActiveAt = useRef(Date.now());
  const lastPersistAt = useRef(0);

  const persist = useCallback((locked: boolean, at: number) => {
    writeStaffIdleLockStorage({ lastActiveAt: at, locked });
  }, []);

  const bump = useCallback(() => {
    if (phase !== "active") return;
    const now = Date.now();
    lastActiveAt.current = now;
    if (now - lastPersistAt.current >= PERSIST_ACTIVITY_MS) {
      lastPersistAt.current = now;
      persist(false, now);
    }
  }, [phase, persist]);

  const lock = useCallback(() => {
    setPhase("locked");
    setContentGeneration((g) => g + 1);
    persist(true, lastActiveAt.current);
  }, [persist]);

  // Boot: restore persisted idle/lock across reload (same tab).
  useEffect(() => {
    if (!enabled) {
      setPhase("active");
      return;
    }
    const now = Date.now();
    const next = evaluateStaffIdleLock(
      readStaffIdleLockStorage(),
      now,
      STAFF_IDLE_LOCK_MS,
    );
    lastActiveAt.current = next.lastActiveAt;
    lastPersistAt.current = now;
    persist(next.locked, next.lastActiveAt);
    if (next.locked) {
      setContentGeneration((g) => g + 1);
      setPhase("locked");
    } else {
      setPhase("active");
    }
  }, [enabled, persist]);

  useEffect(() => {
    if (!enabled || phase !== "active") return;

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
  }, [enabled, phase, bump, lock]);

  if (!enabled) {
    return <>{children}</>;
  }

  // Avoid flashing staff UI before sessionStorage idle check completes.
  if (phase === "boot") {
    return <div className={styles.host} aria-busy="true" />;
  }

  return (
    <div className={styles.host}>
      <div key={contentGeneration}>{children}</div>
      {phase === "locked" ? (
        <IdleLockOverlay
          onUnlocked={() => {
            const now = Date.now();
            lastActiveAt.current = now;
            lastPersistAt.current = now;
            persist(false, now);
            setPhase("active");
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
