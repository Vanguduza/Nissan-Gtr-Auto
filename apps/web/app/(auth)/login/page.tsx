"use client";

import Image from "next/image";
import Link from "next/link";
import { FormEvent, Suspense, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { requestAuthOtp, verifyAuthOtp } from "@/lib/auth-otp";
import { createWebClient } from "@/lib/supabase";
import { loadStaffContext, postLoginPath } from "@/lib/staff-auth";
import styles from "./auth.module.css";

function safeNext(raw: string | null): string | null {
  if (!raw || !raw.startsWith("/") || raw.startsWith("//")) return null;
  return raw;
}

type Mode = "password" | "otp";

function LoginForm() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const next = safeNext(searchParams.get("next"));

  const [mode, setMode] = useState<Mode>("password");
  const [email, setEmail] = useState("");
  const [phone, setPhone] = useState("");
  const [password, setPassword] = useState("");
  const [otpCode, setOtpCode] = useState("");
  const [otpVerified, setOtpVerified] = useState(false);
  const [localStubHint, setLocalStubHint] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function finishStaffRedirect(client: NonNullable<ReturnType<typeof createWebClient>>) {
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

  async function onPasswordSubmit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setMessage(null);
    const client = createWebClient();
    if (!client) {
      setMessage("Add NEXT_PUBLIC_SUPABASE_URL and ANON_KEY to .env.local");
      setBusy(false);
      return;
    }
    const { error } = await client.auth.signInWithPassword({ email, password });
    if (error) {
      setBusy(false);
      setMessage(error.message);
      return;
    }
    await finishStaffRedirect(client);
    setBusy(false);
  }

  async function onRequestOtp(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setMessage(null);
    setLocalStubHint(null);
    setOtpVerified(false);
    const client = createWebClient();
    if (!client) {
      setMessage("Add NEXT_PUBLIC_SUPABASE_URL and ANON_KEY to .env.local");
      setBusy(false);
      return;
    }
    const res = await requestAuthOtp(client, {
      email: email || null,
      phoneE164: phone || null,
    });
    setBusy(false);
    if (!res.ok) {
      setMessage(
        res.failClosed
          ? `OTP unavailable: ${res.error}. Gateway keys missing and local stub is off — fail-closed.`
          : res.error,
      );
      return;
    }
    if (res.stub && res.stubCode) {
      // Server-returned stub only — never hardcode as a client default.
      setLocalStubHint(res.stubCode);
      setMessage("Local stub OTP enabled on server — enter the code shown below.");
    } else {
      setMessage("Code sent — check email and/or SMS.");
    }
  }

  async function onVerifyOtp(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setMessage(null);
    const client = createWebClient();
    if (!client) {
      setMessage("Add NEXT_PUBLIC_SUPABASE_URL and ANON_KEY to .env.local");
      setBusy(false);
      return;
    }
    const res = await verifyAuthOtp(client, {
      email: email || null,
      phoneE164: phone || null,
      code: otpCode,
    });
    setBusy(false);
    if (!res.ok) {
      setMessage(
        res.failClosed
          ? `OTP verify refused: ${res.error}`
          : res.error,
      );
      return;
    }
    setOtpVerified(true);
    if (email.trim()) {
      setMessage(
        "OTP verified. Enter your password to open a session (Edge OTP does not mint JWTs).",
      );
    } else {
      setMessage(
        "OTP verified for phone. Sign in with email + password if you have an account; phone is linked after a signed-in verify.",
      );
    }
  }

  async function onOtpPasswordContinue(e: FormEvent) {
    e.preventDefault();
    if (!otpVerified) {
      setMessage("Verify OTP first.");
      return;
    }
    setBusy(true);
    setMessage(null);
    const client = createWebClient();
    if (!client) {
      setMessage("Add NEXT_PUBLIC_SUPABASE_URL and ANON_KEY to .env.local");
      setBusy(false);
      return;
    }
    const { error } = await client.auth.signInWithPassword({
      email,
      password,
    });
    if (error) {
      setBusy(false);
      setMessage(error.message);
      return;
    }
    // Re-verify phone with Bearer so Edge can persist profiles.phone_e164.
    if (phone.trim()) {
      const again = await requestAuthOtp(client, { phoneE164: phone });
      if (again.ok) {
        const code =
          again.stubCode ||
          (localStubHint ?? "") ||
          otpCode;
        if (code) {
          await verifyAuthOtp(client, {
            phoneE164: phone,
            code,
          });
        }
      }
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

      <div style={{ display: "flex", gap: "0.5rem", flexWrap: "wrap" }}>
        <button
          type="button"
          className={styles.submit}
          style={{
            flex: 1,
            opacity: mode === "password" ? 1 : 0.55,
            marginTop: 0,
          }}
          onClick={() => {
            setMode("password");
            setMessage(null);
          }}
        >
          Password
        </button>
        <button
          type="button"
          className={styles.submit}
          style={{
            flex: 1,
            opacity: mode === "otp" ? 1 : 0.55,
            marginTop: 0,
            background: mode === "otp" ? "var(--gtr-red)" : "rgba(255,255,255,0.15)",
          }}
          onClick={() => {
            setMode("otp");
            setMessage(null);
          }}
        >
          Email / phone OTP
        </button>
      </div>

      {mode === "password" ? (
        <form onSubmit={(e) => void onPasswordSubmit(e)}>
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
          <button className={styles.submit} type="submit" disabled={busy}>
            {busy ? "Signing in…" : "Sign in"}
          </button>
        </form>
      ) : (
        <>
          <form onSubmit={(e) => void onRequestOtp(e)}>
            <p className={styles.alt}>
              Email and/or phone. Server fail-closes without gateway keys unless
              local stub flag is set.
            </p>
            <label className={styles.label}>
              Email
              <input
                className={styles.input}
                type="email"
                autoComplete="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
              />
            </label>
            <label className={styles.label}>
              Phone (E.164)
              <input
                className={styles.input}
                type="tel"
                autoComplete="tel"
                value={phone}
                onChange={(e) => setPhone(e.target.value)}
                placeholder="+263…"
              />
            </label>
            <button className={styles.submit} type="submit" disabled={busy}>
              {busy ? "Requesting…" : "Request OTP"}
            </button>
          </form>

          <form onSubmit={(e) => void onVerifyOtp(e)}>
            <label className={styles.label}>
              6-digit code
              <input
                className={styles.input}
                inputMode="numeric"
                pattern="[0-9]{6}"
                maxLength={6}
                value={otpCode}
                onChange={(e) => setOtpCode(e.target.value)}
                autoComplete="one-time-code"
                required
              />
            </label>
            {localStubHint ? (
              <p className={styles.alt}>
                Server stub code (local only): <code>{localStubHint}</code>
              </p>
            ) : null}
            <button className={styles.submit} type="submit" disabled={busy}>
              {busy ? "Verifying…" : "Verify OTP"}
            </button>
          </form>

          {otpVerified ? (
            <form onSubmit={(e) => void onOtpPasswordContinue(e)}>
              <label className={styles.label}>
                Password (open session)
                <input
                  className={styles.input}
                  type="password"
                  autoComplete="current-password"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  required
                />
              </label>
              <button className={styles.submit} type="submit" disabled={busy || !email.trim()}>
                {busy ? "Signing in…" : "Complete sign-in"}
              </button>
            </form>
          ) : null}
        </>
      )}

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
