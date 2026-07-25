import type { StaffRole, SupabaseClient } from "@gtr/supabase-client";
import type { StorefrontResult } from "@/lib/customer-storefront";

export type { StaffRole };

export type StaffContext = {
  userId: string;
  isStaff: boolean;
  roles: StaffRole[];
};

/** Nav + gate matrix — mirrors plan `2026-07-25-web-management-parity-rbac`. */
export type StaffNavItem = {
  href: string;
  label: string;
  exact?: boolean;
  /** `"any"` = any authenticated staff (`is_staff`). */
  roles: StaffRole[] | "any";
};

export const STAFF_NAV_ITEMS: StaffNavItem[] = [
  { href: "/staff", label: "Hub", exact: true, roles: "any" },
  { href: "/staff/pos", label: "POS counter", roles: ["admin", "warehouse", "sales"] },
  {
    href: "/staff/warehouse",
    label: "Warehouse ops",
    roles: ["admin", "warehouse"],
  },
  { href: "/staff/finance", label: "Finance ledger", roles: ["admin", "finance"] },
  {
    href: "/staff/crm/credit",
    label: "Customer credit",
    roles: ["admin", "sales", "finance"],
  },
  {
    href: "/staff/crm/reviews",
    label: "Review moderation",
    roles: ["admin", "sales"],
  },
  {
    href: "/staff/logistics",
    label: "Logistics",
    exact: true,
    roles: ["admin", "warehouse", "sales", "dispatcher"],
  },
  {
    href: "/staff/logistics/tracking",
    label: "Live map",
    roles: ["admin", "warehouse", "dispatcher"],
  },
  {
    href: "/staff/logistics/panic",
    label: "Panic inbox",
    roles: ["admin", "warehouse", "dispatcher"],
  },
  { href: "/staff/hr", label: "HR desk", roles: ["admin", "hr"] },
  {
    href: "/staff/warranty",
    label: "Warranty claims",
    roles: ["admin", "warehouse", "sales"],
  },
  {
    href: "/staff/chat",
    label: "Customer chat",
    roles: ["admin", "sales", "warehouse"],
  },
  {
    href: "/staff/analytics",
    label: "Analytics",
    exact: true,
    roles: ["admin", "finance", "sales"],
  },
  {
    href: "/staff/analytics/subscriptions",
    label: "Report subs",
    roles: ["admin", "finance", "sales"],
  },
  {
    href: "/procurement",
    label: "Procurement",
    roles: ["admin", "warehouse", "finance"],
  },
];

/**
 * Module → required roles (same matrix as nav/gates).
 * Empty array = any staff. Prefer `STAFF_NAV_ITEMS` / `pathAccessFor` for UI.
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
  chat: ["admin", "sales", "warehouse"] as const satisfies readonly StaffRole[],
  analytics: [
    "admin",
    "finance",
    "sales",
  ] as const satisfies readonly StaffRole[],
  crmCredit: [
    "admin",
    "sales",
    "finance",
  ] as const satisfies readonly StaffRole[],
  crmReviews: ["admin", "sales"] as const satisfies readonly StaffRole[],
} as const;

export type PathAccess =
  | { kind: "any" }
  | { kind: "roles"; roles: StaffRole[] }
  | { kind: "forbidden_page" };

/**
 * Most-specific module gate for a `/staff/*` path.
 * Unknown `/staff/*` paths require any staff (hub-level).
 */
export function pathAccessFor(pathname: string): PathAccess {
  const path = pathname.split("?")[0] || pathname;
  if (path === "/staff/forbidden") return { kind: "forbidden_page" };
  if (path === "/staff") return { kind: "any" };

  if (path === "/staff/pos" || path.startsWith("/staff/pos/")) {
    return { kind: "roles", roles: ["admin", "warehouse", "sales"] };
  }
  if (path.startsWith("/staff/warehouse")) {
    return { kind: "roles", roles: ["admin", "warehouse"] };
  }
  if (path.startsWith("/staff/finance")) {
    return { kind: "roles", roles: ["admin", "finance"] };
  }
  if (
    path === "/staff/crm/credit" ||
    path.startsWith("/staff/crm/credit/")
  ) {
    return { kind: "roles", roles: ["admin", "sales", "finance"] };
  }
  if (
    path === "/staff/crm/reviews" ||
    path.startsWith("/staff/crm/reviews/")
  ) {
    return { kind: "roles", roles: ["admin", "sales"] };
  }
  if (
    path === "/staff/logistics/tracking" ||
    path.startsWith("/staff/logistics/tracking/") ||
    path === "/staff/logistics/panic" ||
    path.startsWith("/staff/logistics/panic/")
  ) {
    return { kind: "roles", roles: ["admin", "warehouse", "dispatcher"] };
  }
  if (path === "/staff/logistics" || path.startsWith("/staff/logistics/")) {
    return {
      kind: "roles",
      roles: ["admin", "warehouse", "sales", "dispatcher"],
    };
  }
  if (path.startsWith("/staff/hr")) {
    return { kind: "roles", roles: ["admin", "hr"] };
  }
  if (path.startsWith("/staff/warranty")) {
    return { kind: "roles", roles: ["admin", "warehouse", "sales"] };
  }
  if (path.startsWith("/staff/chat")) {
    return { kind: "roles", roles: ["admin", "sales", "warehouse"] };
  }
  if (path.startsWith("/staff/analytics")) {
    return { kind: "roles", roles: ["admin", "finance", "sales"] };
  }
  if (path === "/procurement" || path.startsWith("/procurement/")) {
    return { kind: "roles", roles: ["admin", "warehouse", "finance"] };
  }

  return { kind: "any" };
}

/** Alias for pathAccessFor role gates (plan / docs naming). */
export function requiredRolesForPath(
  pathname: string,
): StaffRole[] | "any" | null {
  const access = pathAccessFor(pathname);
  if (access.kind === "forbidden_page") return null;
  if (access.kind === "any") return "any";
  return access.roles;
}

export function rolesAllow(
  userRoles: StaffRole[],
  required: StaffRole[] | "any",
): boolean {
  if (required === "any") return true;
  if (userRoles.includes("admin")) return true;
  return required.some((r) => userRoles.includes(r));
}

/** Alias used by GPS discoverability / thin callers. */
export function hasAnyStaffRole(
  userRoles: readonly StaffRole[],
  required: readonly StaffRole[],
): boolean {
  if (required.length === 0) return true;
  return rolesAllow([...userRoles], [...required]);
}

export function canAccessPath(
  ctx: Pick<StaffContext, "isStaff" | "roles">,
  pathname: string,
): boolean {
  if (!ctx.isStaff) return false;
  const access = pathAccessFor(pathname);
  if (access.kind === "forbidden_page") return true;
  if (access.kind === "any") return true;
  return rolesAllow(ctx.roles, access.roles);
}

export function filterNavForRoles(roles: StaffRole[]): StaffNavItem[] {
  return STAFF_NAV_ITEMS.filter((item) => rolesAllow(roles, item.roles));
}

/**
 * Sales-only → POS workspace as home; admin/warehouse keep hub.
 * Mirrors Android `ManagementHomeRoles.prefersPosHome`.
 */
export function prefersPosHome(roles: readonly StaffRole[]): boolean {
  if (roles.some((r) => r === "admin" || r === "warehouse")) return false;
  return roles.includes("sales");
}

/** Default staff landing after sign-in (no `next` override). */
export function staffHomePath(roles: readonly StaffRole[]): string {
  return prefersPosHome(roles) ? "/staff/pos" : "/staff";
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

  const roles = (rolesRes.data ?? []).map((row) => row.role as StaffRole);

  return {
    ok: true,
    data: {
      userId,
      isStaff: Boolean(profileRes.data?.is_staff),
      roles,
    },
  };
}

/** Load `staff_roles` for the signed-in user (RLS: select own). */
export async function fetchMyStaffRoles(
  client: SupabaseClient,
): Promise<StorefrontResult<StaffRole[]>> {
  const ctx = await loadStaffContext(client);
  if (!ctx.ok) return ctx;
  if (!ctx.data) return { ok: false, error: "Not signed in" };
  return { ok: true, data: ctx.data.roles };
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

/**
 * After password sign-in: staff land on management, not the storefront.
 * Sales-only default = `/staff/pos`; admin/warehouse = hub.
 * Honor `next` only for staff surfaces (`/staff`, `/procurement`).
 */
export function postLoginPath(
  isStaff: boolean,
  next: string | null | undefined,
  roles: readonly StaffRole[] = [],
): string {
  const path = next && next.startsWith("/") && !next.startsWith("//") ? next : null;
  if (isStaff) {
    if (
      path &&
      (path === "/staff" ||
        path.startsWith("/staff/") ||
        path === "/procurement" ||
        path.startsWith("/procurement/"))
    ) {
      // Bare hub → POS for sales-only (same as default home).
      if (path === "/staff" && prefersPosHome(roles)) return "/staff/pos";
      return path;
    }
    return staffHomePath(roles);
  }
  return path ?? "/account";
}

