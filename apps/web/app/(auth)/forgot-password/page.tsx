"use client";

import Image from "next/image";
import Link from "next/link";
import { FormEvent, useState } from "react";
import { useRouter } from "next/navigation";
import {
  requestPasswordReset,
  verifyPasswordReset,
} from "@/lib/auth-password-reset";
import { createWebClient } from "@/lib/supabase";
import styles from "../login/auth.module.css";

type Step = "request" | "verify";

export default function ForgotPasswordPage() {
  const router = useRouter();
  const [step, setStep] = useState<Step>("request");
  const [email, setEmail] = useState("");
  const [phone, setPhone] = useState("");
  const [code, setCode] = useState("");
  const [password, setPassword] = useState("");
  const [confirm, setConfirm] = useState("");
  const [message, setMessage] = useState<string | null>(null);
  const [stubHint, setStubHint] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function onRequest(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setMessage(null);
    setStubHint(null);
    const client = createWebClient();
    if (!client) {
      setMessage("Add NEXT_PUBLIC_SUPABASE_URL and ANON_KEY to .env.local");
      setBusy(false);
      return;
    }
    const res = await requestPasswordReset(client, {
      email: email.trim() || null,
      phoneE164: phone.trim() || null,
    });
    setBusy(false);
    if (!res.ok) {
      setMessage(
        res.failClosed
          ? "Password reset is unavailable (email/SMS not configured)."
          : res.error,
      );
      return;
    }
    if (res.stubCode) setStubHint(res.stubCode);
    setMessage(
      res.stub
        ? "Local stub OTP ready — enter the code below."
        : "If an account exists, a reset code was sent.",
    );
    setStep("verify");
  }

  async function onVerify(e: FormEvent) {
    e.preventDefault();
    if (password !== confirm) {
      setMessage("Passwords do not match.");
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
    const res = await verifyPasswordReset(client, {
      email: email.trim() || null,
      phoneE164: phone.trim() || null,
      code,
      newPassword: password,
    });
    setBusy(false);
    if (!res.ok) {
      setMessage(res.error);
      return;
    }
    setMessage("Password updated — sign in with your new password.");
    router.replace("/login?notice=password-reset");
  }

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
      <div className={styles.form}>
        <h1 className={styles.title}>Reset password</h1>
        <p className={styles.alt}>
          Customers, staff, and delivery drivers use the same reset flow.
        </p>

        {step === "request" ? (
          <form onSubmit={(e) => void onRequest(e)}>
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
              {busy ? "Sending…" : "Send reset code"}
            </button>
          </form>
        ) : null}

        {step === "verify" ? (
          <form onSubmit={(e) => void onVerify(e)}>
            <label className={styles.label}>
              6-digit code
              <input
                className={styles.input}
                inputMode="numeric"
                pattern="[0-9]{6}"
                maxLength={6}
                value={code}
                onChange={(e) => setCode(e.target.value)}
                autoComplete="one-time-code"
                required
              />
            </label>
            {stubHint ? (
              <p className={styles.alt}>
                Server stub code (local only): <code>{stubHint}</code>
              </p>
            ) : null}
            <label className={styles.label}>
              New password
              <input
                className={styles.input}
                type="password"
                autoComplete="new-password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                minLength={8}
                required
              />
            </label>
            <label className={styles.label}>
              Confirm password
              <input
                className={styles.input}
                type="password"
                autoComplete="new-password"
                value={confirm}
                onChange={(e) => setConfirm(e.target.value)}
                minLength={8}
                required
              />
            </label>
            <button className={styles.submit} type="submit" disabled={busy}>
              {busy ? "Updating…" : "Set new password"}
            </button>
            <button
              type="button"
              className={styles.submit}
              style={{ background: "rgba(255,255,255,0.15)", marginTop: "0.5rem" }}
              disabled={busy}
              onClick={() => {
                setStep("request");
                setMessage(null);
              }}
            >
              Back
            </button>
          </form>
        ) : null}

        {message ? <p className={styles.message}>{message}</p> : null}
        <p className={styles.alt}>
          <Link href="/login">Back to sign in</Link>
        </p>
      </div>
    </div>
  );
}
