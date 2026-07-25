import type { StaffRole, SupabaseClient } from "@gtr/supabase-client";
import type { StorefrontResult } from "@/lib/customer-storefront";

export type { StaffRole };

export type StaffContext = {
  userId: string;
  isStaff: boolean;
  roles: StaffRole[];
};

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

export type StaffNavItem = {
  href: string;
  label: string;
  exact?: boolean;
  roles: readonly StaffRole[];
};

export const STAFF_NAV_ITEMS: StaffNavItem[] = [
  { href: "/staff", label: "Hub", exact: true, roles: STAFF_MODULE_ROLES.hub },
  { href: "/staff/pos", label: "POS", roles: STAFF_MODULE_ROLES.pos },
  {
    href: "/staff/warehouse",
    label: "Warehouse",
    roles: STAFF_MODULE_ROLES.warehouse,
  },
  {
    href: "/staff/finance",
    label: "Finance",
    roles: STAFF_MODULE_ROLES.finance,
  },
  {
    href: "/staff/logistics",
    label: "Logistics",
    exact: true,
    roles: STAFF_MODULE_ROLES.logistics,
  },
  {
    href: "/staff/logistics/tracking",
    label: "Live map",
    roles: STAFF_MODULE_ROLES.liveMap,
  },
  { href: "/staff/hr", label: "HR", roles: STAFF_MODULE_ROLES.hr },
  {
    href: "/staff/warranty",
    label: "Warranty",
    roles: STAFF_MODULE_ROLES.warranty,
  },
  {
    href: "/procurement",
    label: "Procurement",
    roles: STAFF_MODULE_ROLES.procurement,
  },
];

export function hasAnyStaffRole(
  userRoles: readonly StaffRole[],
  required: readonly StaffRole[],
): boolean {
  if (required.length === 0) return true;
  return required.some((role) => userRoles.includes(role));
}

export function filterNavForRoles(roles: readonly StaffRole[]): StaffNavItem[] {
  return STAFF_NAV_ITEMS.filter((item) => hasAnyStaffRole(roles, item.roles));
}

export type PathAccess =
  | { kind: "any" }
  | { kind: "roles"; roles: readonly StaffRole[] }
  | { kind: "forbidden_page" };

/** Most-specific module gate for a `/staff/*` path. */
export function pathAccessFor(pathname: string): PathAccess {
  const path = (pathname.split("?")[0] || pathname).replace(/\/$/, "") || "/";
  if (path === "/staff/forbidden") return { kind: "forbidden_page" };
  if (path === "/staff") return { kind: "any" };

  if (path === "/staff/pos" || path.startsWith("/staff/pos/")) {
    return { kind: "roles", roles: STAFF_MODULE_ROLES.pos };
  }
  if (path.startsWith("/staff/warehouse")) {
    return { kind: "roles", roles: STAFF_MODULE_ROLES.warehouse };
  }
  if (path.startsWith("/staff/finance")) {
    return { kind: "roles", roles: STAFF_MODULE_ROLES.finance };
  }
  if (
    path === "/staff/logistics/tracking" ||
    path.startsWith("/staff/logistics/tracking/")
  ) {
    return { kind: "roles", roles: STAFF_MODULE_ROLES.liveMap };
  }
  if (path === "/staff/logistics" || path.startsWith("/staff/logistics/")) {
    return { kind: "roles", roles: STAFF_MODULE_ROLES.logistics };
  }
  if (path.startsWith("/staff/hr")) {
    return { kind: "roles", roles: STAFF_MODULE_ROLES.hr };
  }
  if (path.startsWith("/staff/warranty")) {
    return { kind: "roles", roles: STAFF_MODULE_ROLES.warranty };
  }

  return { kind: "any" };
}

export function canAccessPath(
  ctx: Pick<StaffContext, "isStaff" | "roles">,
  pathname: string,
): boolean {
  if (!ctx.isStaff) return false;
  const access = pathAccessFor(pathname);
  if (access.kind === "forbidden_page" || access.kind === "any") return true;
  return hasAnyStaffRole(ctx.roles, access.roles);
}

/** Load `staff_roles` for the signed-in user (RLS: select own). */
export async function fetchMyStaffRoles(
  client: SupabaseClient,
): Promise<StorefrontResult<StaffRole[]>> {
  const { data: sessionData, error: sessionError } =
    await client.auth.getSession();
  if (sessionError) return { ok: false, error: sessionError.message };
  if (!sessionData.session) {
    return { ok: false, error: "Sign in to continue." };
  }

  const { data, error } = await client
    .from("staff_roles")
    .select("role")
    .eq("user_id", sessionData.session.user.id);

  if (error) return { ok: false, error: error.message };
  return {
    ok: true,
    data: (data ?? []).map((row) => row.role as StaffRole),
  };
}

export async function loadStaffContext(
  client: SupabaseClient,
): Promise<StorefrontResult<StaffContext | null>> {
  const { data, error } = await client.auth.getSession();
  if (error) return { ok: false, error: error.message };
  if (!data.session) return { ok: true, data: null };

  const userId = data.session.user.id;

  const [profileRes, rolesRes] = await Promise.all([
    client.from("profiles").select("is_staff").eq("id", userId).maybeSingle(),
    client.from("staff_roles").select("role").eq("user_id", userId),
  ]);

  if (profileRes.error) return { ok: false, error: profileRes.error.message };
  if (rolesRes.error) return { ok: false, error: rolesRes.error.message };

  return {
    ok: true,
    data: {
      userId,
      isStaff: Boolean(profileRes.data?.is_staff),
      roles: (rolesRes.data ?? []).map((row) => row.role as StaffRole),
    },
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

export function staffLoginHref(returnPath: string): string {
  const next = returnPath.startsWith("/") ? returnPath : "/staff";
  return `/login?next=${encodeURIComponent(next)}`;
}
