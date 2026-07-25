import type { StaffRole, SupabaseClient } from "@gtr/supabase-client";
import {
  requireSession,
  type StorefrontResult,
} from "@/lib/customer-storefront";

export type { StaffRole };

/**
 * Role → module matrix (web management parity ADR).
 * Empty `roles` = any authenticated staff who can open `/staff`.
 * UI hide ≠ security — RLS / SECURITY DEFINER RPCs remain authoritative.
 */
export const STAFF_MODULE_ROLES = {
  hub: [] as const satisfies readonly StaffRole[],
  pos: ["admin", "warehouse", "sales"] as const satisfies readonly StaffRole[],
  warehouse: ["admin", "warehouse"] as const satisfies readonly StaffRole[],
  finance: ["admin", "finance"] as const satisfies readonly StaffRole[],
  logistics: [
    "admin",
    "warehouse",
    "sales",
    "dispatcher",
  ] as const satisfies readonly StaffRole[],
  liveMap: [
    "admin",
    "warehouse",
    "dispatcher",
  ] as const satisfies readonly StaffRole[],
  hr: ["admin", "hr"] as const satisfies readonly StaffRole[],
  procurement: [
    "admin",
    "warehouse",
    "finance",
  ] as const satisfies readonly StaffRole[],
  warranty: [
    "admin",
    "warehouse",
    "sales",
  ] as const satisfies readonly StaffRole[],
} as const;

export function hasAnyStaffRole(
  userRoles: readonly StaffRole[],
  required: readonly StaffRole[],
): boolean {
  if (required.length === 0) return true;
  return required.some((role) => userRoles.includes(role));
}

/** Load `staff_roles` for the signed-in user (RLS: select own). */
export async function fetchMyStaffRoles(
  client: SupabaseClient,
): Promise<StorefrontResult<StaffRole[]>> {
  const session = await requireSession(client);
  if (!session.ok) return session;

  const { data, error } = await client
    .from("staff_roles")
    .select("role")
    .eq("user_id", session.data.userId);

  if (error) return { ok: false, error: error.message };
  return {
    ok: true,
    data: (data ?? []).map((row) => row.role as StaffRole),
  };
}

/** Thin wrapper around Postgres `has_staff_role` — prefer for gates; nav uses local roles. */
export async function rpcHasStaffRole(
  client: SupabaseClient,
  roles: readonly StaffRole[],
): Promise<StorefrontResult<boolean>> {
  const { data, error } = await client.rpc("has_staff_role", {
    roles: [...roles],
  });
  if (error) return { ok: false, error: error.message };
  return { ok: true, data: Boolean(data) };
}
