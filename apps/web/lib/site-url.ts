/**
 * Public origin helpers for absolute links, OAuth redirects, and HTTPS policy.
 * Local Next (`localhost` / `127.0.0.1`) may stay on http://; everything else
 * prefers https:// (defense-in-depth if NEXT_PUBLIC_SITE_URL is mis-set).
 */

/** Hosts allowed to serve over plain HTTP (local `next dev`). */
export function isLocalDevHost(hostname: string): boolean {
  const h = hostname.replace(/^\[|\]$/g, "").toLowerCase();
  return h === "localhost" || h === "127.0.0.1" || h === "::1";
}

/**
 * 1 year HSTS. `includeSubDomains` is safe: apex + www are both HTTPS
 * (`docs/decisions/2026-07-23-company-domain.md`). No `preload` — that needs
 * an explicit HSTS preload list submission.
 */
export const HSTS_HEADER_VALUE = "max-age=31536000; includeSubDomains";

/**
 * Canonical public origin from env (or prod default).
 * Forces `https:` for non-local hosts when the value is `http://…`.
 */
export function publicSiteUrl(
  raw: string | undefined = process.env.NEXT_PUBLIC_SITE_URL,
): string {
  const fallback = "https://nissangtrauto.co.zw";
  const trimmed = (raw ?? fallback).trim().replace(/\/$/, "");
  try {
    const withScheme = trimmed.includes("://") ? trimmed : `https://${trimmed}`;
    const u = new URL(withScheme);
    if (!isLocalDevHost(u.hostname) && u.protocol === "http:") {
      u.protocol = "https:";
    }
    return u.origin;
  } catch {
    return fallback;
  }
}
