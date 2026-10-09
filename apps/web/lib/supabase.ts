import { createBrowserClient } from "@supabase/ssr";
import type { Database } from "@gtr/supabase-client";
import { publicSiteUrl } from "@/lib/site-url";

export function hasSupabaseEnv(): boolean {
  return Boolean(
    process.env.NEXT_PUBLIC_SUPABASE_URL &&
      process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY,
  );
}

/**
 * Browser anon client with cookie session storage (@supabase/ssr).
 * Required so middleware can enforce /staff + /procurement before paint.
 */
export function createWebClient() {
  const url = process.env.NEXT_PUBLIC_SUPABASE_URL;
  const anon = process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY;
  if (!url || !anon) {
    return null;
  }
  return createBrowserClient<Database>(url, anon);
}

/** Absolute origin for OAuth / absolute links — https in prod, http OK locally. */
export function siteUrl() {
  return publicSiteUrl();
}

/**
 * Customer-safe error text. Network failures (browser "Failed to fetch",
 * Node "fetch failed") mean the backend could not be reached; never show the
 * raw TypeError to shoppers.
 */
export function friendlyError(message: string | null | undefined): string {
  const text = (message ?? "").trim();
  if (!text || /failed to fetch|fetch failed|networkerror|load failed|network request failed/i.test(text)) {
    return "We couldn't reach the shop right now. Check your connection and try again.";
  }
  return text;
}
