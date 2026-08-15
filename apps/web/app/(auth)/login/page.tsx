"use client";

import Image from "next/image";
import Link from "next/link";
import { FormEvent, Suspense, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { signInWithEmailOrPhone } from "@/lib/auth-otp";
import {
  startCustomerOAuth,
  type CustomerOAuthProvider,
} from "@/lib/auth-oauth";
import {
  COUNTRY_DIAL_CODES,
  DEFAULT_COUNTRY_DIAL,
  countryDialOptionValue,
  nationalDigitsOnly,
  parseCountryDialOption,
  toE164,
} from "@/lib/country-dial-codes";
import { createWebClient } from "@/lib/supabase";
import {
  loadStaffContext,
  postLoginPath,
  signInWithStaffIdentifier,
} from "@/lib/staff-auth";
import { clearStaffIdleLockStorage } from "@/lib/staff-idle-lock-state";
import styles from "./auth.module.css";

const DEFAULT_COUNTRY_OPTION =
  COUNTRY_DIAL_CODES.find((c) => c.dial === DEFAULT_COUNTRY_DIAL && c.iso === "ZW") ??
  COUNTRY_DIAL_CODES.find((c) => c.dial === DEFAULT_COUNTRY_DIAL) ?? {
    iso: "ZW",
    name: "Zimbabwe",
    dial: DEFAULT_COUNTRY_DIAL,
  };

function safeNext(raw: string | null): string | null {
  if (!raw || !raw.startsWith("/") || raw.startsWith("//")) return null;
  return raw;
}

type LoginMethod = "email" | "phone" | "employee";

function LoginForm() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const next = safeNext(searchParams.get("next"));
  const staffNext =
    Boolean(next) &&
    (next === "/staff" ||
      next?.startsWith("/staff/") ||
      next === "/procurement" ||
      next?.startsWith("/procurement/"));

  const [method, setMethod] = useState<LoginMethod>(
    staffNext ? "employee" : "email",
  );
  const [email, setEmail] = useState("");
  const [employeeCode, setEmployeeCode] = useState("");
  const [countryOption, setCountryOption] = useState(
    countryDialOptionValue(DEFAULT_COUNTRY_OPTION),
  );
  const [phoneNational, setPhoneNational] = useState("");
  const [password, setPassword] = useState("");
  const [message, setMessage] = useState<string | null>(() => {
    const notice = searchParams.get("notice");
    if (notice === "password-reset") {
      return "Password updated. Sign in with your new password.";
    }
    if (notice === "staff-only") {
      return "That area is for staff accounts only.";
    }
    return null;
  });
  const [busy, setBusy] = useState(false);

  const selectedDial =
    parseCountryDialOption(countryOption)?.dial ?? DEFAULT_COUNTRY_DIAL;
  const phoneE164 =
    method === "phone" ? toE164(selectedDial, phoneNational) : null;

  const canSubmit =
    method === "email"
      ? Boolean(email.trim())
      : method === "employee"
        ? Boolean(employeeCode.trim())
        : Boolean(nationalDigitsOnly(phoneNational));

  async function finishStaffRedirect(
    client: NonNullable<ReturnType<typeof createWebClient>>,
  ) {
    const ctx = await loadStaffContext(client);
    if (!ctx.ok) {
      setMessage(ctx.error);
      return;
    }
    // Fresh password sign-in resets idle lock so reload after login is not locked.
    clearStaffIdleLockStorage();
    const dest = postLoginPath(
      Boolean(ctx.data?.isStaff),
      next,
      ctx.data?.roles ?? [],
      Boolean(ctx.data?.mustChangePassword),
    );
    setMessage(
      ctx.data?.mustChangePassword
        ? "Signed in — password change required…"
        : ctx.data?.isStaff
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

    if (method === "employee") {
      // Staff emp# (or email/phone via same RPC) — Android parity.
      const loggedIn = await signInWithStaffIdentifier(
        client,
        employeeCode,
        password,
      );
      if (!loggedIn.ok) {
        setBusy(false);
        setMessage(loggedIn.error);
        return;
      }
    } else {
      // Customer / staff email|phone — password auth only (no OTP on login).
      const loggedIn = await signInWithEmailOrPhone(client, {
        email: method === "email" ? email || null : null,
        phoneE164: method === "phone" ? phoneE164 : null,
        password,
      });
      if (!loggedIn.ok) {
        setBusy(false);
        setMessage(loggedIn.error);
        return;
      }
    }
    await finishStaffRedirect(client);
    setBusy(false);
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
    const started = await startCustomerOAuth(client, provider, next);
    if (!started.ok) {
      setBusy(false);
      setMessage(started.error);
      return;
    }
    // Browser redirects to provider; keep busy until navigation.
  }

  const showCustomerOAuth = method !== "employee";

  return (
    <div className={styles.form}>
      <h1 className={styles.title}>Sign in</h1>
      {next ? (
        <p className={styles.alt}>
          Continue to <code>{next}</code> after sign-in.
        </p>
      ) : null}
      <p className={styles.alt}>
        Email or phone for customers. Staff may use employee #, email, or phone.
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
        <button
          type="button"
          role="tab"
          id="login-tab-employee"
          aria-selected={method === "employee"}
          aria-controls="login-panel-employee"
          className={method === "employee" ? styles.tabActive : styles.tab}
          onClick={() => {
            setMethod("employee");
            setMessage(null);
          }}
        >
          Employee #
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
        ) : method === "phone" ? (
          <div
            role="tabpanel"
            id="login-panel-phone"
            aria-labelledby="login-tab-phone"
          >
            <label className={styles.label}>
              Phone number
              <div className={styles.phoneRow}>
                <select
                  className={styles.countrySelect}
                  aria-label="Country code"
                  value={countryOption}
                  onChange={(e) => setCountryOption(e.target.value)}
                >
                  {COUNTRY_DIAL_CODES.map((c) => (
                    <option key={countryDialOptionValue(c)} value={countryDialOptionValue(c)}>
                      {c.name} ({c.dial})
                    </option>
                  ))}
                </select>
                <input
                  className={styles.input}
                  type="tel"
                  inputMode="numeric"
                  pattern="[0-9]*"
                  autoComplete="tel-national"
                  value={phoneNational}
                  onChange={(e) =>
                    setPhoneNational(e.target.value.replace(/\D/g, ""))
                  }
                  placeholder="771234567"
                  required
                />
              </div>
            </label>
          </div>
        ) : (
          <div
            role="tabpanel"
            id="login-panel-employee"
            aria-labelledby="login-tab-employee"
          >
            <label className={styles.label}>
              Emp # / email / phone
              <input
                className={styles.input}
                type="text"
                autoComplete="username"
                value={employeeCode}
                onChange={(e) => setEmployeeCode(e.target.value)}
                placeholder="GTR…"
                required
              />
            </label>
            <p className={styles.alt}>
              Staff only — resolves via secure lookup, then password.
            </p>
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

      {showCustomerOAuth ? (
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
          <p className={styles.oauthHint}>
            First Google sign-in creates your storefront account.
          </p>
        </div>
      ) : null}

      {message ? <p className={styles.message}>{message}</p> : null}
      {method !== "employee" ? (
        <p className={styles.alt}>
          No account? <Link href="/signup">Create one</Link>
          {" · "}
          <Link href="/forgot-password">Forgot password?</Link>
        </p>
      ) : (
        <p className={styles.alt}>
          <Link href="/forgot-password">Forgot password?</Link>
        </p>
      )}
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
