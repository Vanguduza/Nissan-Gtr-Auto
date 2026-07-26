"use client";

import { usePathname, useRouter } from "next/navigation";
import { useEffect, useState, type ReactNode } from "react";
import { StaffAuthProvider } from "@/components/staff-auth-context";
import { StaffChrome } from "@/components/staff-chrome";
import styles from "@/components/account.module.css";
import {
  canAccessPath,
  loadStaffContext,
  prefersPosHome,
  staffLoginHref,
  type StaffContext,
} from "@/lib/staff-auth";
import { createWebClient, hasSupabaseEnv } from "@/lib/supabase";

type GateState =
  | { kind: "loading" }
  | { kind: "redirecting"; message: string }
  | { kind: "error"; message: string }
  | { kind: "ready"; ctx: StaffContext };

const STAFF_CONTEXT_TIMEOUT_MS = 20_000;

function withTimeout<T>(
  promise: Promise<T>,
  ms: number,
  label: string,
): Promise<T> {
  return new Promise((resolve, reject) => {
    const timer = setTimeout(
      () => reject(new Error(`${label} timed out after ${ms / 1000}s`)),
      ms,
    );
    promise.then(
      (v) => {
        clearTimeout(timer);
        resolve(v);
      },
      (e: unknown) => {
        clearTimeout(timer);
        reject(e);
      },
    );
  });
}

export function StaffGate({ children }: { children: ReactNode }) {
  const pathname = usePathname() || "/staff";
  const router = useRouter();
  const [state, setState] = useState<GateState>({ kind: "loading" });

  useEffect(() => {
    let cancelled = false;

    void (async () => {
      if (!hasSupabaseEnv()) {
        if (!cancelled) {
          setState({
            kind: "error",
            message:
              "Add NEXT_PUBLIC_SUPABASE_URL and ANON_KEY to .env.local to use staff surfaces.",
          });
        }
        return;
      }

      const client = createWebClient();
      if (!client) {
        if (!cancelled) {
          setState({
            kind: "error",
            message: "Supabase client could not be created.",
          });
        }
        return;
      }

      let res: Awaited<ReturnType<typeof loadStaffContext>>;
      try {
        res = await withTimeout(
          loadStaffContext(client),
          STAFF_CONTEXT_TIMEOUT_MS,
          "Staff sign-in check",
        );
      } catch (e) {
        if (cancelled) return;
        const msg =
          e instanceof Error
            ? e.message
            : "Could not verify staff access. Check Supabase is running.";
        setState({ kind: "error", message: msg });
        return;
      }
      if (cancelled) return;

      if (!res.ok) {
        setState({ kind: "error", message: res.error });
        return;
      }

      if (!res.data) {
        const loginHref = staffLoginHref(pathname);
        setState({ kind: "redirecting", message: "Redirecting to sign in…" });
        window.location.assign(loginHref);
        return;
      }

      const ctx = res.data;
      const onForbidden = pathname === "/staff/forbidden";

      if (!ctx.isStaff) {
        if (!onForbidden) {
          setState({ kind: "redirecting", message: "Redirecting…" });
          router.replace("/staff/forbidden?reason=not-staff");
          return;
        }
        setState({ kind: "ready", ctx });
        return;
      }

      if (!onForbidden && !canAccessPath(ctx, pathname)) {
        setState({ kind: "redirecting", message: "Redirecting…" });
        router.replace("/staff/forbidden?reason=role");
        return;
      }

      // Sales-only default home is POS (not full hub).
      if (
        ctx.isStaff &&
        prefersPosHome(ctx.roles) &&
        pathname === "/staff"
      ) {
        setState({ kind: "redirecting", message: "Opening POS…" });
        router.replace("/staff/pos");
        return;
      }

      setState({ kind: "ready", ctx });
    })();

    return () => {
      cancelled = true;
    };
  }, [pathname, router]);

  if (state.kind === "loading") {
    return (
      <div className={styles.shell} style={{ gridTemplateColumns: "1fr" }}>
        <div className={styles.panel} style={{ maxWidth: "40rem", margin: "1.25rem auto", width: "100%" }}>
          <div className={styles.pageBody}>
            <p className={styles.muted}>Checking staff access…</p>
          </div>
        </div>
      </div>
    );
  }

  if (state.kind === "error") {
    return (
      <div className={styles.shell} style={{ gridTemplateColumns: "1fr" }}>
        <div className={styles.panel} style={{ maxWidth: "40rem", margin: "1.25rem auto", width: "100%" }}>
          <header className={styles.pageHeader}>
            <h1 className={styles.title}>Staff</h1>
          </header>
          <div className={styles.pageBody}>
            <p className={styles.lede}>{state.message}</p>
          </div>
        </div>
      </div>
    );
  }

  return (
    <StaffAuthProvider value={state.ctx}>
      <StaffChrome>{children}</StaffChrome>
    </StaffAuthProvider>
  );
}
