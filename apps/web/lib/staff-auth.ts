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
  { href: "/staff/pos", label: "POS", roles: ["admin", "warehouse", "sales"] },
  {
    href: "/staff/warehouse",
    label: "Warehouse",
    roles: ["admin", "warehouse"],
  },
  { href: "/staff/finance", label: "Finance", roles: ["admin", "finance"] },
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
  { href: "/staff/hr", label: "HR", roles: ["admin", "hr"] },
  {
    href: "/staff/warranty",
    label: "Warranty",
    roles: ["admin", "warehouse", "sales"],
  },
  {
    href: "/procurement",
    label: "Procurement",
    roles: ["admin", "warehouse", "finance"],
  },
];

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
    path === "/staff/logistics/tracking" ||
    path.startsWith("/staff/logistics/tracking/")
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

  return { kind: "any" };
}

export function rolesAllow(
  userRoles: StaffRole[],
  required: StaffRole[] | "any",
): boolean {
  if (required === "any") return true;
  if (userRoles.includes("admin")) return true;
  return required.some((r) => userRoles.includes(r));
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

  const roles = (rolesRes.data ?? []).map(
    (row) => row.role as StaffRole,
  );

  return {
    ok: true,
    data: {
      userId,
      isStaff: Boolean(profileRes.data?.is_staff),
      roles,
    },
  };
}

export function staffLoginHref(returnPath: string): string {
  const next = returnPath.startsWith("/") ? returnPath : "/staff";
  return `/login?next=${encodeURIComponent(next)}`;
}
