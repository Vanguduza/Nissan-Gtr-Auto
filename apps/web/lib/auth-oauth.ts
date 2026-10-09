import type { SupabaseClient } from "@gtr/supabase-client";
import { siteUrl } from "@/lib/supabase";

export type CustomerOAuthProvider = "google";

export type OAuthStartResult =
  | { ok: true }
  | { ok: false; error: string };

/** Same-origin relative paths only (open-redirect guard). */
export function safeAuthNext(raw: string | null | undefined): string | null {
  if (!raw || !raw.startsWith("/") || raw.startsWith("//")) return null;
  return raw;
}

/**
 * Absolute redirect for Supabase OAuth — must match Auth redirect allow-list
 * (`…/auth/callback` in docs/CUSTOMER_OAUTH_SETUP.md + config.toml).
 */
export function customerOAuthRedirectTo(next?: string | null): string {
  const origin =
    typeof window !== "undefined" ? window.location.origin : siteUrl();
  const url = new URL("/auth/callback", origin.replace(/\/$/, ""));
  const safe = safeAuthNext(next ?? null);
  if (safe) url.searchParams.set("next", safe);
  return url.toString();
}

/** Map GoTrue provider-disabled / misconfig errors to a storefront-friendly line. */
export function friendlyOAuthError(
  provider: CustomerOAuthProvider,
  raw: string,
): string {
  const lower = raw.toLowerCase();
  if (
    lower.includes("provider is not enabled") ||
    lower.includes("unsupported provider") ||
    (lower.includes("provider") && lower.includes("not enabled"))
  ) {
    return "Google sign-in is not enabled yet. Ask an admin to turn on the provider in Supabase Auth (see docs/CUSTOMER_OAUTH_SETUP.md).";
  }
  void provider;
  return raw;
}

/**
 * Customer storefront OAuth (Google only). Staff Employee # tab must not call this.
 * Uses PKCE; session completes on `/auth/callback` via exchangeCodeForSession.
 */
export async function startCustomerOAuth(
  client: SupabaseClient,
  provider: CustomerOAuthProvider,
  next?: string | null,
): Promise<OAuthStartResult> {
  const { error } = await client.auth.signInWithOAuth({
    provider,
    options: {
      redirectTo: customerOAuthRedirectTo(next),
      skipBrowserRedirect: false,
    },
  });
  if (error) {
    return { ok: false, error: friendlyOAuthError(provider, error.message) };
  }
  return { ok: true };
}

/**
 * Defense-in-depth after OAuth session: mint retail `customers` if AuthZ is null.
 * Staff / employees are denied by the RPC — ignore those errors and continue.
 */
export async function ensureOwnCustomerIfNeeded(
  client: SupabaseClient,
): Promise<string | null> {
  const { data, error } = await client.rpc("ensure_own_customer");
  if (error) return null;
  return typeof data === "string" && data.trim() ? data : null;
}
