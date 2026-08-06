import { createBrowserClient } from "@gtr/supabase-client";
import { publicSiteUrl } from "@/lib/site-url";

export function hasSupabaseEnv(): boolean {
  return Boolean(
    process.env.NEXT_PUBLIC_SUPABASE_URL &&
      process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY,
  );
}

export function createWebClient() {
  const url = process.env.NEXT_PUBLIC_SUPABASE_URL;
  const anon = process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY;
  if (!url || !anon) {
    return null;
  }
  return createBrowserClient(url, anon);
}

/** Absolute origin for OAuth / absolute links — https in prod, http OK locally. */
export function siteUrl() {
  return publicSiteUrl();
}
