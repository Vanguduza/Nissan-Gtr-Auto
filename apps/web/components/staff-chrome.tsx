"use client";

import Image from "next/image";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useState, type ReactNode } from "react";
import { useStaffAuth } from "@/components/staff-auth-context";
import { iconSizeMd, iconStroke, LogOut } from "@/components/icons";
import { clearStaffIdleLockStorage } from "@/lib/staff-idle-lock-state";
import { createWebClient } from "@/lib/supabase";
import styles from "./staff-chrome.module.css";

/** Management shell — Overview owns its benchmark header; other staff routes retain this chrome. */
export function StaffChrome({ children }: { children: ReactNode }) {
  const ctx = useStaffAuth();
  const pathname = usePathname() || "/staff";
  const router = useRouter();
  const [busy, setBusy] = useState(false);
  const overview = pathname === "/staff";

  async function signOut() {
    setBusy(true);
    clearStaffIdleLockStorage();
    const client = createWebClient();
    if (client) await client.auth.signOut();
    setBusy(false);
    router.replace("/login");
  }

  return (
    <div className={styles.shell}>
      {!overview ? (
        <header className={styles.bar}>
          <div className={styles.barInner}>
            <Link href="/staff" className={styles.brand} aria-label="Staff overview">
              <Image
                src="/brand/logo.png"
                alt=""
                width={40}
                height={40}
                className={styles.logo}
                priority
              />
              <span className={styles.brandText}>
                Nissan GTR Auto
                <strong>Staff</strong>
              </span>
            </Link>
            <div className={styles.actions}>
              {ctx?.roles?.length ? (
                <p className={styles.roles} title={ctx.roles.join(", ")}>
                  {ctx.roles.join(" · ")}
                </p>
              ) : null}
              <button
                type="button"
                className={styles.signOut}
                onClick={() => void signOut()}
                disabled={busy}
              >
                <LogOut size={iconSizeMd} strokeWidth={iconStroke} aria-hidden />
                {busy ? "Signing out…" : "Sign out"}
              </button>
            </div>
          </div>
        </header>
      ) : null}
      <main className={styles.main}>{children}</main>
    </div>
  );
}
