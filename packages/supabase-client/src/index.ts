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

/** Admin RPC args — call via `supabase.rpc('assign_staff_role', …)`. */
export function assignStaffRoleArgs(userId: string, role: Database["public"]["Enums"]["staff_role"]) {
  return { p_user_id: userId, p_role: role } as const;
}

export function revokeStaffRoleArgs(userId: string, role: Database["public"]["Enums"]["staff_role"]) {
  return { p_user_id: userId, p_role: role } as const;
}

export type { Database, SupabaseClient };
export type StaffRole = Database["public"]["Enums"]["staff_role"];
export type ProcurementDocStatus = Database["public"]["Enums"]["procurement_doc_status"];
