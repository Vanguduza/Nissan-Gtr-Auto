import { createClient, type SupabaseClient } from "@supabase/supabase-js";
import type { Database } from "./database.types.js";

/**
 * Browser/mobile anon client. Never ship the service_role key here.
 */
export function createBrowserClient(
  url: string,
  anonKey: string,
): SupabaseClient<Database> {
  if (!url || !anonKey) {
    throw new Error("Supabase URL and anon key are required");
  }
  return createClient<Database>(url, anonKey, {
    auth: {
      persistSession: true,
      autoRefreshToken: true,
    },
  });
}

export type { Database, SupabaseClient };
