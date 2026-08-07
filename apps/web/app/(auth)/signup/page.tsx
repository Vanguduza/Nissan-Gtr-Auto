"use client";

import Image from "next/image";
import Link from "next/link";
import { FormEvent, useState } from "react";
import { useRouter } from "next/navigation";
import {
  completeAuthSignup,
  requestAuthOtp,
  verifyAuthOtp,
} from "@/lib/auth-otp";
import {
  startCustomerOAuth,
  type CustomerOAuthProvider,
} from "@/lib/auth-oauth";
import { createWebClient } from "@/lib/supabase";
import { loadStaffContext, postLoginPath } from "@/lib/staff-auth";
import styles from "../login/auth.module.css";

type Step = "identifiers" | "otp" | "account";

export default function SignupPage() {
  const router = useRouter();
  const [step, setStep] = useState<Step>("identifiers");
  const [email, setEmail] = useState("");
  const [phone, setPhone] = useState("");
  const [password, setPassword] = useState("");
  const [fullName, setFullName] = useState("");
  const [otpCode, setOtpCode] = useState("");
  const [localStubHint, setLocalStubHint] = useState<string | null>(null);
  const [otpProofToken, setOtpProofToken] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function onRequestOtp(e: FormEvent) {
    e.preventDefault();
    if (!email.trim() && !phone.trim()) {
      setMessage("Enter email and/or phone.");
      return;
    }
    setBusy(true);
    setMessage(null);
    setLocalStubHint(null);
    setOtpProofToken(null);
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
    setStep("otp");
    if (res.stub && res.stubCode) {
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
      setMessage(res.error);
      return;
    }
    setOtpProofToken(res.proofToken);
    if (!email.trim()) {
      setMessage(
        "Phone OTP verified. Add an email + password to create a storefront account (session requires Auth email).",
      );
      setStep("account");
      return;
    }
    setStep("account");
    setMessage("OTP verified — create your account with a password.");
  }

  async function onCreateAccount(e: FormEvent) {
    e.preventDefault();
    if (!otpProofToken) {
      setMessage("Verify OTP before creating an account.");
      return;
    }
    if (!email.trim()) {
      setMessage("Email is required to create a Supabase Auth account.");
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
    const created = await completeAuthSignup(client, {
      email,
      password,
      proofToken: otpProofToken,
      fullName,
      phoneE164: phone || null,
    });
    if (!created.ok) {
      setBusy(false);
      setMessage(created.error);
      setOtpProofToken(null);
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

    setBusy(false);
    setMessage("Account created — redirecting…");
    const ctx = await loadStaffContext(client);
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
      setMessage("Add NEXT_PUBLIC_SUPABASE_URL and ANON_KEY to .env.local");
      setBusy(false);
      return;
    }
    const started = await startCustomerOAuth(client, provider, "/account");
    if (!started.ok) {
      setBusy(false);
      setMessage(started.error);
      return;
    }
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
        <h1 className={styles.title}>Create account</h1>
        <p className={styles.alt}>
          Confirm email and/or phone with a one-time code, then set your
          password. Later sign-ins use that email/phone + password (no OTP).
          OTP send fails closed when gateways are unset unless the local stub
          flag is on.
        </p>

        {step === "identifiers" ? (
          <>
            <form onSubmit={(e) => void onRequestOtp(e)}>
              <label className={styles.label}>
                Full name
                <input
                  className={styles.input}
                  value={fullName}
                  onChange={(e) => setFullName(e.target.value)}
                  autoComplete="name"
                />
              </label>
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
            <div className={styles.oauthBlock}>
              <p className={styles.oauthDivider} role="presentation">
                <span>or continue with</span>
              </p>
              <button
                type="button"
                className={styles.oauthGoogle}
                disabled={busy}
                onClick={() => void onOAuth("google")}
              >
                Google
              </button>
              <button
                type="button"
                className={styles.oauthApple}
                disabled={busy}
                onClick={() => void onOAuth("apple")}
              >
                Apple
              </button>
              <p className={styles.oauthHint}>
                Google/Apple skip OTP — first login creates your account when
                providers are enabled in Supabase.
              </p>
            </div>
          </>
        ) : null}

        {step === "otp" ? (
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
            <button
              type="button"
              className={styles.submit}
              style={{ background: "rgba(255,255,255,0.15)", marginTop: "0.5rem" }}
              disabled={busy}
              onClick={() => {
                setStep("identifiers");
                setMessage(null);
              }}
            >
              Back
            </button>
          </form>
        ) : null}

        {step === "account" ? (
          <form onSubmit={(e) => void onCreateAccount(e)}>
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
                autoComplete="new-password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                required
                minLength={8}
              />
            </label>
            <button className={styles.submit} type="submit" disabled={busy}>
              {busy ? "Creating…" : "Create account"}
            </button>
          </form>
        ) : null}

        {message ? <p className={styles.message}>{message}</p> : null}
        <p className={styles.alt}>
          Already registered? <Link href="/login">Sign in</Link>
        </p>
      </div>
    </div>
  );
}
