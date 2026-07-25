"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import {
  iconSizeMd,
  iconStroke,
  LogIn,
  LogOut,
} from "@/components/icons";
import { createWebClient, hasSupabaseEnv } from "@/lib/supabase";
import styles from "@/components/account.module.css";

/** Sign in / Sign out controls for My Account (moved off the storefront header). */
export function AccountSessionActions() {
  const router = useRouter();
  const [signedIn, setSignedIn] = useState(false);
  const [email, setEmail] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    if (!hasSupabaseEnv()) return;
    const client = createWebClient();
    if (!client) return;

    let cancelled = false;

    async function refresh() {
      if (!client) return;
      const { data } = await client.auth.getSession();
      if (cancelled) return;
      const session = data.session;
      setSignedIn(Boolean(session));
      setEmail(session?.user.email ?? null);
    }

    void refresh();
    const { data: sub } = client.auth.onAuthStateChange(() => {
      void refresh();
    });

    return () => {
      cancelled = true;
      sub.subscription.unsubscribe();
    };
  }, []);

  async function signOut() {
    setBusy(true);
    const client = createWebClient();
    if (client) await client.auth.signOut();
    setBusy(false);
    setSignedIn(false);
    setEmail(null);
    router.replace("/login");
  }

  return (
    <div className={styles.sessionBar}>
      {signedIn ? (
        <>
          <p className={styles.sessionMeta}>
            Signed in{email ? ` as ${email}` : ""}
          </p>
          <button
            type="button"
            className={styles.sessionBtn}
            onClick={() => void signOut()}
            disabled={busy}
          >
            <LogOut size={iconSizeMd} strokeWidth={iconStroke} aria-hidden />
            {busy ? "Signing out…" : "Sign out"}
          </button>
        </>
      ) : (
        <>
          <p className={styles.sessionMeta}>
            Sign in to manage garage, orders, and chat.
          </p>
          <Link href="/login?next=/account" className={styles.sessionBtnPrimary}>
            <LogIn size={iconSizeMd} strokeWidth={iconStroke} aria-hidden />
            Sign in
          </Link>
        </>
      )}
    </div>
  );
}
