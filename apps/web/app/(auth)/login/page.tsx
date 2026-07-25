"use client";

import Image from "next/image";
import Link from "next/link";
import { FormEvent, Suspense, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { signInWithEmailOrPhone } from "@/lib/auth-otp";
import { createWebClient } from "@/lib/supabase";
import { loadStaffContext, postLoginPath } from "@/lib/staff-auth";
import styles from "./auth.module.css";

function safeNext(raw: string | null): string | null {
  if (!raw || !raw.startsWith("/") || raw.startsWith("//")) return null;
  return raw;
}

type LoginMethod = "email" | "phone";

function LoginForm() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const next = safeNext(searchParams.get("next"));

  const [method, setMethod] = useState<LoginMethod>("email");
  const [email, setEmail] = useState("");
  const [phone, setPhone] = useState("");
  const [password, setPassword] = useState("");
  const [message, setMessage] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const canSubmit =
    method === "email" ? Boolean(email.trim()) : Boolean(phone.trim());

  async function finishStaffRedirect(
    client: NonNullable<ReturnType<typeof createWebClient>>,
  ) {
    const ctx = await loadStaffContext(client);
    if (!ctx.ok) {
      setMessage(ctx.error);
      return;
    }
    const dest = postLoginPath(
      Boolean(ctx.data?.isStaff),
      next,
      ctx.data?.roles ?? [],
    );
    setMessage(
      ctx.data?.isStaff
        ? "Signed in — opening staff…"
        : "Signed in — redirecting…",
    );
    router.replace(dest);
  }

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setMessage(null);
    const client = createWebClient();
    if (!client) {
      setMessage("Add NEXT_PUBLIC_SUPABASE_URL and ANON_KEY to .env.local");
      setBusy(false);
      return;
    }
    // Tab chooses a single identifier — password auth only (no OTP on login).
    const loggedIn = await signInWithEmailOrPhone(client, {
      email: method === "email" ? email || null : null,
      phoneE164: method === "phone" ? phone || null : null,
      password,
    });
    if (!loggedIn.ok) {
      setBusy(false);
      setMessage(loggedIn.error);
      return;
    }
    await finishStaffRedirect(client);
    setBusy(false);
  }

  return (
    <div className={styles.form}>
      <h1 className={styles.title}>Sign in</h1>
      {next ? (
        <p className={styles.alt}>
          Continue to <code>{next}</code> after sign-in.
        </p>
      ) : null}
      <p className={styles.alt}>
        Sign in with the email or phone saved at registration.
      </p>

      <div className={styles.tabs} role="tablist" aria-label="Sign in with">
        <button
          type="button"
          role="tab"
          id="login-tab-email"
          aria-selected={method === "email"}
          aria-controls="login-panel-email"
          className={method === "email" ? styles.tabActive : styles.tab}
          onClick={() => {
            setMethod("email");
            setMessage(null);
          }}
        >
          Email
        </button>
        <button
          type="button"
          role="tab"
          id="login-tab-phone"
          aria-selected={method === "phone"}
          aria-controls="login-panel-phone"
          className={method === "phone" ? styles.tabActive : styles.tab}
          onClick={() => {
            setMethod("phone");
            setMessage(null);
          }}
        >
          Phone number
        </button>
      </div>

      <form onSubmit={(e) => void onSubmit(e)}>
        {method === "email" ? (
          <div
            role="tabpanel"
            id="login-panel-email"
            aria-labelledby="login-tab-email"
          >
            <label className={styles.label}>
              Email
              <input
                className={styles.input}
                type="email"
                autoComplete="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                required
              />
            </label>
          </div>
        ) : (
          <div
            role="tabpanel"
            id="login-panel-phone"
            aria-labelledby="login-tab-phone"
          >
            <label className={styles.label}>
              Phone number (E.164)
              <input
                className={styles.input}
                type="tel"
                autoComplete="tel"
                value={phone}
                onChange={(e) => setPhone(e.target.value)}
                placeholder="+263…"
                required
              />
            </label>
          </div>
        )}
        <label className={styles.label}>
          Password
          <input
            className={styles.input}
            type="password"
            autoComplete="current-password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            required
          />
        </label>
        <button
          className={styles.submit}
          type="submit"
          disabled={busy || !canSubmit}
        >
          {busy ? "Signing in…" : "Sign in"}
        </button>
      </form>

      {message ? <p className={styles.message}>{message}</p> : null}
      <p className={styles.alt}>
        No account? <Link href="/signup">Create one</Link>
      </p>
    </div>
  );
}

export default function LoginPage() {
  return (
    <div className={styles.shell}>
      <Link href="/" className={styles.brand}>
        <Image
          src="/brand/logo.png"
          alt="Nissan GTR Auto"
          width={88}
          height={88}
          className={styles.brandLogo}
          priority
        />
      </Link>
      <Suspense fallback={<p className={styles.message}>Loading…</p>}>
        <LoginForm />
      </Suspense>
    </div>
  );
}
