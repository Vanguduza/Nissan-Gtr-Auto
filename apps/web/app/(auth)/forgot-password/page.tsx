"use client";

import Image from "next/image";
import Link from "next/link";
import { FormEvent, useState } from "react";
import { useRouter } from "next/navigation";
import {
  requestPasswordReset,
  verifyPasswordReset,
  type PasswordResetChannel,
} from "@/lib/auth-password-reset";
import { createWebClient } from "@/lib/supabase";
import styles from "../login/auth.module.css";

type Step = "request" | "verify";

export default function ForgotPasswordPage() {
  const router = useRouter();
  const [step, setStep] = useState<Step>("request");
  const [channel, setChannel] = useState<PasswordResetChannel>("email");
  const [email, setEmail] = useState("");
  const [phone, setPhone] = useState("");
  const [code, setCode] = useState("");
  const [password, setPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<string | null>(null);

  async function onRequest(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setMessage(null);
    try {
      const client = createWebClient();
      if (!client) throw new Error("Supabase client is not configured.");
      const redirectTo = typeof window !== "undefined" ? `${window.location.origin}/forgot-password` : null;
      const result = await requestPasswordReset(client, {
        channel,
        email: channel === "email" ? email : null,
        phoneE164: channel === "phone" ? phone : null,
        redirectTo,
      });
      if (!result.ok) throw new Error(result.error);
      setMessage(result.message);
      setStep("verify");
    } catch (error) {
      setMessage(error instanceof Error ? error.message : "Unable to request password reset.");
    } finally {
      setBusy(false);
    }
  }

  async function onVerify(e: FormEvent) {
    e.preventDefault();
    if (password !== confirmPassword) {
      setMessage("Passwords do not match.");
      return;
    }
    setBusy(true);
    setMessage(null);
    try {
      const client = createWebClient();
      if (!client) throw new Error("Supabase client is not configured.");
      const result = await verifyPasswordReset(client, {
        channel,
        email: channel === "email" ? email : null,
        phoneE164: channel === "phone" ? phone : null,
        code,
        newPassword: password,
      });
      if (!result.ok) throw new Error(result.error);
      const { error: sessionError } = await client.auth.setSession({
        access_token: result.accessToken,
        refresh_token: result.refreshToken,
      });
      if (sessionError) throw sessionError;
      setMessage("Password updated. Your recovered Supabase Auth session is active.");
      router.replace("/account");
    } catch (error) {
      setMessage(error instanceof Error ? error.message : "Password reset failed.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className={styles.shell}>
      <Link href="/" className={styles.brand}>
        <Image src="/brand/logo.png" alt="Nissan GTR Auto" width={88} height={88} className={styles.brandLogo} priority />
      </Link>
      <div className={styles.form}>
        <h1 className={styles.title}>Reset password</h1>
        <p className={styles.alt}>
          Recovery is handled by Supabase Auth. For privacy, the request step does not reveal whether an account exists.
        </p>

        {step === "request" ? (
          <form onSubmit={(e) => void onRequest(e)}>
            <div className={styles.tabs} role="tablist" aria-label="Recovery channel">
              <button type="button" role="tab" aria-selected={channel === "email"} className={channel === "email" ? styles.tabActive : styles.tab} onClick={() => setChannel("email")}>Email</button>
              <button type="button" role="tab" aria-selected={channel === "phone"} className={channel === "phone" ? styles.tabActive : styles.tab} onClick={() => setChannel("phone")}>Phone</button>
            </div>
            {channel === "email" ? (
              <label className={styles.label}>Email<input className={styles.input} type="email" autoComplete="email" value={email} onChange={(e) => setEmail(e.target.value)} required /></label>
            ) : (
              <label className={styles.label}>Phone (E.164)<input className={styles.input} type="tel" autoComplete="tel" value={phone} onChange={(e) => setPhone(e.target.value)} placeholder="+263…" required /></label>
            )}
            <button className={styles.submit} type="submit" disabled={busy}>{busy ? "Sending…" : "Send recovery code"}</button>
          </form>
        ) : (
          <form onSubmit={(e) => void onVerify(e)}>
            <p className={styles.alt}>Enter the Supabase Auth recovery code sent to {channel === "email" ? email : phone}.</p>
            <label className={styles.label}>Recovery code<input className={styles.input} inputMode="numeric" pattern="[0-9]*" maxLength={10} autoComplete="one-time-code" value={code} onChange={(e) => setCode(e.target.value.replace(/\D/g, ""))} required /></label>
            <label className={styles.label}>New password<input className={styles.input} type="password" minLength={8} autoComplete="new-password" value={password} onChange={(e) => setPassword(e.target.value)} required /></label>
            <label className={styles.label}>Confirm password<input className={styles.input} type="password" minLength={8} autoComplete="new-password" value={confirmPassword} onChange={(e) => setConfirmPassword(e.target.value)} required /></label>
            <button className={styles.submit} type="submit" disabled={busy}>{busy ? "Updating…" : "Update password"}</button>
            <button type="button" className={styles.submit} style={{ background: "rgba(255,255,255,0.15)", marginTop: "0.5rem" }} disabled={busy} onClick={() => setStep("request")}>Request another code</button>
          </form>
        )}

        {message ? <p className={styles.message}>{message}</p> : null}
        <p className={styles.alt}><Link href="/login">Back to sign in</Link></p>
      </div>
    </div>
  );
}
