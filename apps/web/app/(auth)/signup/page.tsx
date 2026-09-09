"use client";

import Image from "next/image";
import Link from "next/link";
import { FormEvent, useMemo, useState } from "react";
import { useRouter } from "next/navigation";
import {
  completeAuthSignup,
  requestAuthOtp,
  verifyAuthOtp,
  type AuthOtpChannel,
} from "@/lib/auth-otp";
import {
  startCustomerOAuth,
  type CustomerOAuthProvider,
} from "@/lib/auth-oauth";
import { createWebClient } from "@/lib/supabase";
import { loadStaffContext, postLoginPath } from "@/lib/staff-auth";
import styles from "../login/auth.module.css";

type Step = "identifiers" | "verify" | "account";

export default function SignupPage() {
  const router = useRouter();
  const [step, setStep] = useState<Step>("identifiers");
  const [email, setEmail] = useState("");
  const [phone, setPhone] = useState("");
  const [password, setPassword] = useState("");
  const [fullName, setFullName] = useState("");
  const [otpCode, setOtpCode] = useState("");
  const [channel, setChannel] = useState<AuthOtpChannel>("email");
  const [emailVerified, setEmailVerified] = useState(false);
  const [phoneVerified, setPhoneVerified] = useState(false);
  const [phoneRequired, setPhoneRequired] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const trimmedEmail = email.trim();
  const trimmedPhone = phone.trim();
  const canComplete = emailVerified && (!phoneRequired || phoneVerified);
  const verifyLabel = useMemo(
    () => (channel === "email" ? trimmedEmail : trimmedPhone),
    [channel, trimmedEmail, trimmedPhone],
  );

  async function requestChannel(target: AuthOtpChannel) {
    const client = createWebClient();
    if (!client) throw new Error("Supabase client is not configured.");
    const res = await requestAuthOtp(client, {
      email: trimmedEmail,
      phoneE164: trimmedPhone || null,
      channel: target,
      fullName,
    });
    if (!res.ok) throw new Error(res.error);
    setPhoneRequired(res.verificationRequired.phone);
    setChannel(target);
    setOtpCode("");
    setStep("verify");
    setMessage(
      target === "email"
        ? "Supabase Auth sent a verification code to your email."
        : "Supabase Auth sent a verification code to your phone.",
    );
  }

  async function onRequestOtp(e: FormEvent) {
    e.preventDefault();
    if (!trimmedEmail) {
      setMessage("Email is required for a storefront account.");
      return;
    }
    setBusy(true);
    setMessage(null);
    try {
      await requestChannel("email");
    } catch (error) {
      setMessage(error instanceof Error ? error.message : "Unable to start signup.");
    } finally {
      setBusy(false);
    }
  }

  async function onVerifyOtp(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setMessage(null);
    try {
      const client = createWebClient();
      if (!client) throw new Error("Supabase client is not configured.");
      const res = await verifyAuthOtp(client, {
        email: trimmedEmail,
        phoneE164: trimmedPhone || null,
        channel,
        code: otpCode,
      });
      if (!res.ok) throw new Error(res.error);

      if (channel === "email") setEmailVerified(true);
      if (channel === "phone") setPhoneVerified(true);

      const nowEmailVerified = channel === "email" ? true : res.verified.email;
      const nowPhoneVerified = channel === "phone" ? true : res.verified.phone === true;
      const needsPhone = res.verified.phone !== null || phoneRequired;

      setEmailVerified(nowEmailVerified);
      setPhoneVerified(nowPhoneVerified);
      setPhoneRequired(needsPhone);
      setOtpCode("");

      if (nowEmailVerified && needsPhone && !nowPhoneVerified) {
        if (!trimmedPhone) {
          throw new Error("A phone number was attached to this signup but is missing locally. Restart signup.");
        }
        await requestChannel("phone");
        return;
      }

      if (res.signupReady || (nowEmailVerified && (!needsPhone || nowPhoneVerified))) {
        setStep("account");
        setMessage("Contact verification complete. Set your password to finish creating the account.");
        return;
      }

      setMessage("Verification is incomplete. Request the remaining verification code.");
    } catch (error) {
      setMessage(error instanceof Error ? error.message : "Verification failed.");
    } finally {
      setBusy(false);
    }
  }

  async function onResend() {
    setBusy(true);
    setMessage(null);
    try {
      await requestChannel(channel);
    } catch (error) {
      setMessage(error instanceof Error ? error.message : "Unable to resend code.");
    } finally {
      setBusy(false);
    }
  }

  async function onCreateAccount(e: FormEvent) {
    e.preventDefault();
    if (!canComplete) {
      setMessage("Complete the required Supabase Auth verification first.");
      return;
    }
    setBusy(true);
    setMessage(null);
    const client = createWebClient();
    if (!client) {
      setMessage("Supabase client is not configured.");
      setBusy(false);
      return;
    }

    const created = await completeAuthSignup(client, {
      email: trimmedEmail,
      password,
      fullName,
      phoneE164: trimmedPhone || null,
    });
    if (!created.ok) {
      setBusy(false);
      setMessage(created.error);
      return;
    }

    const { error: sessionErr } = await client.auth.setSession({
      access_token: created.accessToken,
      refresh_token: created.refreshToken,
    });
    if (sessionErr) {
      setBusy(false);
      setMessage(sessionErr.message);
      return;
    }

    setMessage("Account created — redirecting…");
    const ctx = await loadStaffContext(client);
    setBusy(false);
    if (ctx.ok) {
      router.replace(
        postLoginPath(
          Boolean(ctx.data?.isStaff),
          null,
          ctx.data?.roles ?? [],
          Boolean(ctx.data?.mustChangePassword),
        ),
      );
    } else {
      router.replace("/account");
    }
  }

  async function onOAuth(provider: CustomerOAuthProvider) {
    setBusy(true);
    setMessage(null);
    const client = createWebClient();
    if (!client) {
      setMessage("Supabase client is not configured.");
      setBusy(false);
      return;
    }
    const started = await startCustomerOAuth(client, provider, "/account");
    if (!started.ok) {
      setBusy(false);
      setMessage(started.error);
    }
  }

  return (
    <div className={styles.shell}>
      <Link href="/" className={styles.brand}>
        <Image src="/brand/logo.png" alt="Nissan GTR Auto" width={88} height={88} className={styles.brandLogo} priority />
      </Link>
      <div className={styles.form}>
        <h1 className={styles.title}>Create account</h1>
        <p className={styles.alt}>
          Supabase Auth verifies your email first and, when supplied, your phone separately. Your password is set only after all required verification succeeds.
        </p>

        {step === "identifiers" ? (
          <>
            <form onSubmit={(e) => void onRequestOtp(e)}>
              <label className={styles.label}>Full name<input className={styles.input} value={fullName} onChange={(e) => setFullName(e.target.value)} autoComplete="name" /></label>
              <label className={styles.label}>Email<input className={styles.input} type="email" autoComplete="email" value={email} onChange={(e) => setEmail(e.target.value)} required /></label>
              <label className={styles.label}>Phone (optional, E.164)<input className={styles.input} type="tel" autoComplete="tel" value={phone} onChange={(e) => setPhone(e.target.value)} placeholder="+263…" /></label>
              <button className={styles.submit} type="submit" disabled={busy}>{busy ? "Sending…" : "Verify email"}</button>
            </form>
            <div className={styles.oauthBlock}>
              <p className={styles.oauthDivider} role="presentation"><span>or continue with</span></p>
              <button type="button" className={styles.oauthGoogle} disabled={busy} onClick={() => void onOAuth("google")}>Google</button>
              <button type="button" className={styles.oauthApple} disabled={busy} onClick={() => void onOAuth("apple")}>Apple</button>
              <p className={styles.oauthHint}>Google and Apple remain native Supabase Auth providers.</p>
            </div>
          </>
        ) : null}

        {step === "verify" ? (
          <form onSubmit={(e) => void onVerifyOtp(e)}>
            <p className={styles.alt}>Enter the code sent to <strong>{verifyLabel}</strong>.</p>
            <label className={styles.label}>Verification code<input className={styles.input} inputMode="numeric" pattern="[0-9]*" maxLength={10} value={otpCode} onChange={(e) => setOtpCode(e.target.value.replace(/\D/g, ""))} autoComplete="one-time-code" required /></label>
            <button className={styles.submit} type="submit" disabled={busy}>{busy ? "Verifying…" : `Verify ${channel}`}</button>
            <button type="button" className={styles.submit} style={{ background: "rgba(255,255,255,0.15)", marginTop: "0.5rem" }} disabled={busy} onClick={() => void onResend()}>Resend code</button>
            <button type="button" className={styles.submit} style={{ background: "transparent", marginTop: "0.5rem" }} disabled={busy} onClick={() => { setStep("identifiers"); setMessage(null); }}>Restart signup</button>
          </form>
        ) : null}

        {step === "account" ? (
          <form onSubmit={(e) => void onCreateAccount(e)}>
            <p className={styles.alt}>Email verified{phoneRequired ? " · Phone verified" : ""}.</p>
            <label className={styles.label}>Password<input className={styles.input} type="password" autoComplete="new-password" value={password} onChange={(e) => setPassword(e.target.value)} required minLength={8} /></label>
            <button className={styles.submit} type="submit" disabled={busy}>{busy ? "Creating…" : "Create account"}</button>
          </form>
        ) : null}

        {message ? <p className={styles.message}>{message}</p> : null}
        <p className={styles.alt}>Already registered? <Link href="/login">Sign in</Link></p>
      </div>
    </div>
  );
}
