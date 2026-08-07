"use client";

import Image from "next/image";
import Link from "next/link";
import { Suspense, useEffect, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import {
  ensureOwnCustomerIfNeeded,
  safeAuthNext,
} from "@/lib/auth-oauth";
import { createWebClient } from "@/lib/supabase";
import { loadStaffContext, postLoginPath } from "@/lib/staff-auth";
import styles from "../../(auth)/login/auth.module.css";

function AuthCallbackInner() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const [message, setMessage] = useState("Completing sign-in…");

  useEffect(() => {
    let cancelled = false;

    async function finish() {
      const client = createWebClient();
      if (!client) {
        if (!cancelled) {
          setMessage(
            "Add NEXT_PUBLIC_SUPABASE_URL and ANON_KEY to .env.local",
          );
        }
        return;
      }

      const next = safeAuthNext(searchParams.get("next"));
      const code = searchParams.get("code");
      const oauthError =
        searchParams.get("error_description") ||
        searchParams.get("error");

      if (oauthError) {
        if (!cancelled) {
          setMessage(oauthError);
        }
        return;
      }

      if (code) {
        const { error } = await client.auth.exchangeCodeForSession(code);
        if (error) {
          if (!cancelled) setMessage(error.message);
          return;
        }
      } else {
        const { data } = await client.auth.getSession();
        if (!data.session) {
          if (!cancelled) {
            setMessage(
              "No auth code or session. Try signing in again from /login.",
            );
          }
          return;
        }
      }

      // Best-effort customer row (trigger may have already inserted; staff deny OK).
      await ensureOwnCustomerIfNeeded(client);

      const ctx = await loadStaffContext(client);
      if (cancelled) return;

      if (!ctx.ok) {
        setMessage(ctx.error);
        return;
      }

      const dest = postLoginPath(
        Boolean(ctx.data?.isStaff),
        next,
        ctx.data?.roles ?? [],
        Boolean(ctx.data?.mustChangePassword),
      );
      setMessage("Signed in — redirecting…");
      router.replace(dest);
    }

    void finish();
    return () => {
      cancelled = true;
    };
  }, [router, searchParams]);

  return (
    <div className={styles.form}>
      <h1 className={styles.title}>Sign in</h1>
      <p className={styles.message}>{message}</p>
      <p className={styles.alt}>
        <Link href="/login">Back to sign in</Link>
      </p>
    </div>
  );
}

export default function AuthCallbackPage() {
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
        <AuthCallbackInner />
      </Suspense>
    </div>
  );
}
