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

/** Leaf under a module (route or `?tab=` deep link). */
export type StaffNavLeaf = StaffNavItem & {
  /** When set, leaf is active only if `?tab=` matches (or default when absent). */
  tab?: string;
  /** Prefix paths that should not activate this leaf (e.g. RFQ list vs new). */
  excludePathPrefix?: string;
};

export type StaffNavModule = {
  id: string;
  label: string;
  /** Default entry when opening the module from hub / group header. */
  href: string;
  /** `"any"` = any authenticated staff (`is_staff`). */
  roles: StaffRole[] | "any";
  /** Default `?tab=` when path matches and URL has no tab (finance/POS). */
  defaultTab?: string;
  children: StaffNavLeaf[];
};

export type StaffNavEntry =
  | ({ kind: "link" } & StaffNavItem)
  | ({ kind: "module" } & StaffNavModule);

/**
 * Hierarchical staff IA — sidebar modules expand to subfeatures.
 * Flattened `STAFF_NAV_ITEMS` stays for gates / hub href filters.
 */
export const STAFF_NAV_TREE: StaffNavEntry[] = [
  { kind: "link", href: "/staff", label: "Hub", exact: true, roles: "any" },
  {
    kind: "module",
    id: "pos",
    label: "POS",
    href: "/staff/pos",
    defaultTab: "cart",
    roles: ["admin", "warehouse", "sales"],
    children: [
      {
        href: "/staff/pos?tab=cart",
        label: "Cart",
        tab: "cart",
        exact: true,
        roles: ["admin", "warehouse", "sales"],
      },
      {
        href: "/staff/pos?tab=prep",
        label: "Online prep",
        tab: "prep",
        exact: true,
        roles: ["admin", "warehouse", "sales"],
      },
    ],
  },
  {
    kind: "module",
    id: "warehouse",
    label: "Warehouse",
    href: "/staff/warehouse",
    roles: ["admin", "warehouse"],
    children: [
      {
        href: "/staff/warehouse",
        label: "Overview",
        exact: true,
        roles: ["admin", "warehouse"],
      },
      {
        href: "/staff/warehouse/receive",
        label: "Receive",
        roles: ["admin", "warehouse"],
      },
      {
        href: "/staff/warehouse/transfers",
        label: "Transfers",
        roles: ["admin", "warehouse"],
      },
      {
        href: "/staff/warehouse/cycle-count",
        label: "Cycle count",
        roles: ["admin", "warehouse"],
      },
      {
        href: "/staff/warehouse/bins",
        label: "Bins",
        roles: ["admin", "warehouse"],
      },
      {
        href: "/staff/warehouse/consignment",
        label: "Consignment",
        roles: ["admin", "warehouse"],
      },
    ],
  },
  {
    kind: "module",
    id: "finance",
    label: "Finance",
    href: "/staff/finance",
    defaultTab: "journals",
    roles: ["admin", "finance"],
    children: [
      {
        href: "/staff/finance?tab=petty-cash",
        label: "Petty cash",
        tab: "petty-cash",
        exact: true,
        roles: ["admin", "finance"],
      },
      {
        href: "/staff/finance?tab=cash-sales",
        label: "Cash sales",
        tab: "cash-sales",
        exact: true,
        roles: ["admin", "finance"],
      },
      {
        href: "/staff/finance?tab=online-sales",
        label: "Online sales",
        tab: "online-sales",
        exact: true,
        roles: ["admin", "finance"],
      },
      {
        href: "/staff/finance?tab=exchange-rate",
        label: "ZiG rate",
        tab: "exchange-rate",
        exact: true,
        roles: ["admin", "finance"],
      },
      {
        href: "/staff/finance?tab=journals",
        label: "Journals",
        tab: "journals",
        exact: true,
        roles: ["admin", "finance"],
      },
      {
        href: "/staff/finance?tab=requisitions",
        label: "Requisitions",
        tab: "requisitions",
        exact: true,
        roles: ["admin", "finance"],
      },
      {
        href: "/staff/finance?tab=payments",
        label: "Payments",
        tab: "payments",
        exact: true,
        roles: ["admin", "finance"],
      },
      {
        href: "/staff/finance?tab=reports",
        label: "Reports",
        tab: "reports",
        exact: true,
        roles: ["admin", "finance"],
      },
      {
        href: "/staff/finance?tab=bank-recon",
        label: "Bank recon",
        tab: "bank-recon",
        exact: true,
        roles: ["admin", "finance"],
      },
      {
        href: "/staff/finance?tab=periods",
        label: "Periods",
        tab: "periods",
        exact: true,
        roles: ["admin", "finance"],
      },
    ],
  },
  {
    kind: "module",
    id: "crm",
    label: "CRM",
    href: "/staff/crm/credit",
    roles: ["admin", "sales", "finance"],
    children: [
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
    ],
  },
  {
    kind: "module",
    id: "logistics",
    label: "Logistics",
    href: "/staff/logistics",
    roles: ["admin", "warehouse", "sales", "dispatcher"],
    children: [
      {
        href: "/staff/logistics",
        label: "Jobs / pick",
        exact: true,
        roles: ["admin", "warehouse", "sales", "dispatcher"],
      },
      {
        href: "/staff/logistics/prep",
        label: "Sales prep",
        roles: ["admin", "warehouse", "sales", "dispatcher"],
      },
      {
        href: "/staff/logistics/tracking",
        label: "Live tracking",
        roles: ["admin", "warehouse", "dispatcher"],
      },
      {
        href: "/staff/logistics/panic",
        label: "Panic inbox",
        roles: ["admin", "warehouse", "dispatcher"],
      },
    ],
  },
  {
    kind: "module",
    id: "fleet",
    label: "Fleet",
    href: "/staff/fleet",
    roles: ["admin", "warehouse", "dispatcher"],
    children: [
      {
        href: "/staff/fleet",
        label: "Company fleet",
        roles: ["admin", "warehouse", "dispatcher"],
      },
    ],
  },
  {
    kind: "module",
    id: "hr",
    label: "HR",
    href: "/staff/hr",
    roles: ["admin", "hr"],
    children: [
      { href: "/staff/hr", label: "HR desk", roles: ["admin", "hr"] },
    ],
  },
  {
    kind: "module",
    id: "warranty",
    label: "Warranty",
    href: "/staff/warranty",
    roles: ["admin", "warehouse", "sales"],
    children: [
      {
        href: "/staff/warranty",
        label: "Warranty claims",
        roles: ["admin", "warehouse", "sales"],
      },
    ],
  },
  {
    kind: "module",
    id: "chat",
    label: "Chat",
    href: "/staff/chat",
    roles: ["admin", "sales", "warehouse"],
    children: [
      {
        href: "/staff/chat",
        label: "Customer chat",
        roles: ["admin", "sales", "warehouse"],
      },
    ],
  },
  {
    kind: "module",
    id: "analytics",
    label: "Analytics",
    href: "/staff/analytics",
    roles: ["admin", "finance", "sales"],
    children: [
      {
        href: "/staff/analytics",
        label: "KPIs",
        exact: true,
        roles: ["admin", "finance", "sales"],
      },
      {
        href: "/staff/analytics/subscriptions",
        label: "Report subscriptions",
        roles: ["admin", "finance", "sales"],
      },
    ],
  },
  {
    kind: "module",
    id: "procurement",
    label: "Procurement",
    href: "/procurement",
    roles: ["admin", "warehouse", "finance"],
    children: [
      {
        href: "/procurement",
        label: "Overview",
        exact: true,
        roles: ["admin", "warehouse", "finance"],
      },
      {
        href: "/procurement/rfqs",
        label: "RFQs",
        excludePathPrefix: "/procurement/rfqs/new",
        roles: ["admin", "warehouse", "finance"],
      },
      {
        href: "/procurement/rfqs/new",
        label: "New RFQ",
        exact: true,
        roles: ["admin", "warehouse", "finance"],
      },
      {
        href: "/procurement/blankets",
        label: "Blankets",
        exact: true,
        roles: ["admin", "warehouse", "finance"],
      },
      {
        href: "/procurement/approvals",
        label: "Approvals",
        exact: true,
        roles: ["admin", "finance"],
      },
    ],
  },
];

/** Flat leaf list (compat) — pathnames only, deduped. */
export const STAFF_NAV_ITEMS: StaffNavItem[] = (() => {
  const seen = new Set<string>();
  const items: StaffNavItem[] = [];
  for (const entry of STAFF_NAV_TREE) {
    if (entry.kind === "link") {
      const { kind: _k, ...item } = entry;
      items.push(item);
      continue;
    }
    for (const child of entry.children) {
      const q = child.href.indexOf("?");
      const pathname = q < 0 ? child.href : child.href.slice(0, q);
      if (seen.has(pathname)) continue;
      seen.add(pathname);
      items.push({
        href: pathname,
        label: child.label,
        exact: child.exact,
        roles: child.roles,
      });
    }
  }
  return items;
})();

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
  fleet: [
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
  if (path === "/staff/fleet" || path.startsWith("/staff/fleet/")) {
    return { kind: "roles", roles: ["admin", "warehouse", "dispatcher"] };
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

/** Role-filtered hierarchical nav for the staff sidebar / hub. */
export function filterNavTreeForRoles(roles: StaffRole[]): StaffNavEntry[] {
  const out: StaffNavEntry[] = [];
  for (const entry of STAFF_NAV_TREE) {
    if (entry.kind === "link") {
      if (rolesAllow(roles, entry.roles)) out.push(entry);
      continue;
    }
    const children = entry.children.filter((c) => rolesAllow(roles, c.roles));
    if (children.length === 0) continue;
    out.push({ ...entry, children });
  }
  return out;
}

/** Pathname (+ optional tab) for a nav href that may include `?tab=`. */
export function navHrefParts(href: string): { pathname: string; tab: string | null } {
  const q = href.indexOf("?");
  if (q < 0) return { pathname: href, tab: null };
  const pathname = href.slice(0, q);
  const tab = new URLSearchParams(href.slice(q + 1)).get("tab");
  return { pathname, tab };
}

export function isStaffNavLeafActive(
  leaf: StaffNavLeaf,
  pathname: string,
  searchTab: string | null,
  moduleDefaultTab?: string,
): boolean {
  const { pathname: leafPath, tab: leafTab } = navHrefParts(leaf.href);
  const tabKey = leaf.tab ?? leafTab;

  if (
    leaf.excludePathPrefix &&
    (pathname === leaf.excludePathPrefix ||
      pathname.startsWith(`${leaf.excludePathPrefix}/`))
  ) {
    return false;
  }

  if (tabKey) {
    if (pathname !== leafPath) return false;
    const effective = searchTab ?? moduleDefaultTab ?? null;
    return effective === tabKey;
  }

  if (leaf.exact) return pathname === leafPath;

  return pathname === leafPath || pathname.startsWith(`${leafPath}/`);
}

export function isStaffNavModuleActive(
  mod: StaffNavModule,
  pathname: string,
  searchTab: string | null,
): boolean {
  return mod.children.some((c) =>
    isStaffNavLeafActive(c, pathname, searchTab, mod.defaultTab),
  );
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

