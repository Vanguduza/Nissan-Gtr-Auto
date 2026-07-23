import { createBrowserClient } from "@gtr/supabase-client";

export function createWebClient() {
  const url = process.env.NEXT_PUBLIC_SUPABASE_URL;
  const anon = process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY;
  if (!url || !anon) {
    return null;
  }
  return createBrowserClient(url, anon);
}

export function siteUrl() {
  return process.env.NEXT_PUBLIC_SITE_URL ?? "https://nissangtrauto.co.zw";
}
