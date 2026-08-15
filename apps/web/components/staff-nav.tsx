"use client";

import Link from "next/link";
import { usePathname, useSearchParams } from "next/navigation";
import { Suspense, useEffect, useMemo, useState } from "react";
import styles from "@/components/account.module.css";
import {
  Banknote,
  BarChart3,
  Bell,
  ChevronDown,
  ClipboardList,
  iconSizeSm,
  iconStroke,
  LayoutGrid,
  MapPinned,
  PackageCheck,
  Siren,
  Car,
  MessageCircle,
  Package,
  PackageSearch,
  ShieldCheck,
  Star,
  Truck,
  Users,
  Warehouse,
  type LucideIcon,
} from "@/components/icons";
import { useStaffAuth } from "@/components/staff-auth-context";
import {
  filterNavTreeForModuleAccess,
  isStaffNavLeafActive,
  isStaffNavModuleActive,
  navHrefParts,
  STAFF_NAV_TREE,
  type StaffNavEntry,
  type StaffNavLeaf,
  type StaffNavModule,
} from "@/lib/staff-auth";

const staffNavIcons: Record<string, LucideIcon> = {
  "/staff": LayoutGrid,
  warehouse: Warehouse,
  finance: Banknote,
  crm: Users,
  logistics: Truck,
  fleet: Car,
  hr: Users,
  warranty: ShieldCheck,
  chat: MessageCircle,
  analytics: BarChart3,
  procurement: PackageSearch,
  "/staff/warehouse": Warehouse,
  "/staff/warehouse/insights": BarChart3,
  "/staff/finance": Banknote,
  "/staff/crm/credit": Users,
  "/staff/crm/reviews": Star,
  "/staff/crm/product-pages": PackageSearch,
  "/staff/crm/kits": Package,
  "/staff/logistics": Truck,
  "/staff/logistics/prep": PackageCheck,
  "/staff/logistics/tracking": MapPinned,
  "/staff/logistics/panic": Siren,
  "/staff/fleet": Car,
  "/staff/hr": Users,
  "/staff/warranty": ShieldCheck,
  "/staff/chat": MessageCircle,
  "/staff/analytics": BarChart3,
  "/staff/analytics/subscriptions": Bell,
  "/procurement": PackageSearch,
  "/procurement/rfqs": PackageSearch,
  "/procurement/rfqs/new": PackageSearch,
  "/procurement/blankets": PackageSearch,
  "/procurement/approvals": PackageSearch,
};

function iconForLeaf(leaf: StaffNavLeaf): LucideIcon | undefined {
  const { pathname } = navHrefParts(leaf.href);
  return staffNavIcons[leaf.href] ?? staffNavIcons[pathname];
}

function iconForModule(mod: StaffNavModule): LucideIcon | undefined {
  return staffNavIcons[mod.id] ?? staffNavIcons[navHrefParts(mod.href).pathname];
}

/** Hub cards + external consumers — same icon map as sidebar. */
export function staffNavIconForLeaf(leaf: StaffNavLeaf): LucideIcon {
  return iconForLeaf(leaf) ?? ClipboardList;
}

export function staffNavIconForModule(mod: StaffNavModule): LucideIcon {
  return iconForModule(mod) ?? LayoutGrid;
}

function StaffNavInner({ current }: { current: string }) {
  const ctx = useStaffAuth();
  const pathnameHook = usePathname();
  const searchParams = useSearchParams();
  const pathname =
    pathnameHook || navHrefParts(current).pathname || "/staff";
  const searchTab = searchParams?.get("tab") ?? null;

  const entries: StaffNavEntry[] = useMemo(() => {
    if (ctx) return filterNavTreeForModuleAccess(ctx.roles, ctx.moduleAccess);
    return STAFF_NAV_TREE.filter(
      (e) => e.kind === "link" && e.roles === "any",
    );
  }, [ctx]);

  const initiallyOpen = useMemo(() => {
    const open = new Set<string>();
    for (const entry of entries) {
      if (
        entry.kind === "module" &&
        isStaffNavModuleActive(entry, pathname, searchTab)
      ) {
        open.add(entry.id);
      }
    }
    return open;
  }, [entries, pathname, searchTab]);

  const [openIds, setOpenIds] = useState<Set<string>>(initiallyOpen);

  useEffect(() => {
    setOpenIds((prev) => {
      const next = new Set(prev);
      for (const id of initiallyOpen) next.add(id);
      return next;
    });
  }, [initiallyOpen]);

  function toggleModule(id: string) {
    setOpenIds((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  }

  return (
    <nav className={styles.nav} aria-label="Staff">
      <p className={styles.navTitle}>Staff</p>
      <ul className={styles.navList}>
        {entries.map((entry) => {
          if (entry.kind === "link") {
            const active = isStaffNavLeafActive(entry, pathname, searchTab);
            const Icon = staffNavIcons[entry.href];
            return (
              <li key={entry.href}>
                <Link
                  href={entry.href}
                  className={active ? styles.navLinkActive : styles.navLink}
                >
                  {Icon ? (
                    <Icon
                      size={iconSizeSm}
                      strokeWidth={iconStroke}
                      aria-hidden
                    />
                  ) : null}
                  {entry.label}
                </Link>
              </li>
            );
          }

          const modActive = isStaffNavModuleActive(
            entry,
            pathname,
            searchTab,
          );
          const expanded = openIds.has(entry.id);
          const ModIcon = iconForModule(entry);

          return (
            <li key={entry.id} className={styles.navGroup}>
              <div className={styles.navGroupRow}>
                <Link
                  href={entry.href}
                  className={
                    modActive ? styles.navGroupLinkActive : styles.navGroupLink
                  }
                  aria-current={modActive ? "page" : undefined}
                >
                  {ModIcon ? (
                    <ModIcon
                      size={iconSizeSm}
                      strokeWidth={iconStroke}
                      aria-hidden
                    />
                  ) : null}
                  {entry.label}
                </Link>
                <button
                  type="button"
                  className={styles.navGroupToggle}
                  aria-expanded={expanded}
                  aria-controls={`staff-nav-${entry.id}`}
                  aria-label={`${expanded ? "Collapse" : "Expand"} ${entry.label}`}
                  onClick={() => toggleModule(entry.id)}
                >
                  <ChevronDown
                    size={iconSizeSm}
                    strokeWidth={iconStroke}
                    className={
                      expanded ? styles.navChevronOpen : styles.navChevron
                    }
                    aria-hidden
                  />
                </button>
              </div>
              {expanded ? (
                <ul
                  id={`staff-nav-${entry.id}`}
                  className={styles.navSubList}
                >
                  {entry.children.map((leaf) => {
                    const active = isStaffNavLeafActive(
                      leaf,
                      pathname,
                      searchTab,
                      entry.defaultTab,
                    );
                    const Icon = iconForLeaf(leaf);
                    return (
                      <li key={leaf.href}>
                        <Link
                          href={leaf.href}
                          className={
                            active
                              ? styles.navSubLinkActive
                              : styles.navSubLink
                          }
                        >
                          {Icon ? (
                            <Icon
                              size={iconSizeSm}
                              strokeWidth={iconStroke}
                              aria-hidden
                            />
                          ) : null}
                          {leaf.label}
                        </Link>
                      </li>
                    );
                  })}
                </ul>
              ) : null}
            </li>
          );
        })}
      </ul>
    </nav>
  );
}

/** Staff sidebar with expandable module → subfeature menus (RBAC-filtered). */
export function StaffNav({ current }: { current: string }) {
  return (
    <Suspense fallback={<StaffNavStatic current={current} />}>
      <StaffNavInner current={current} />
    </Suspense>
  );
}

/** Pre-hydration / Suspense fallback without useSearchParams. */
function StaffNavStatic({ current }: { current: string }) {
  const ctx = useStaffAuth();
  const pathname = navHrefParts(current).pathname;
  const entries = ctx
    ? filterNavTreeForModuleAccess(ctx.roles, ctx.moduleAccess)
    : STAFF_NAV_TREE.filter((e) => e.kind === "link" && e.roles === "any");

  return (
    <nav className={styles.nav} aria-label="Staff">
      <p className={styles.navTitle}>Staff</p>
      <ul className={styles.navList}>
        {entries.map((entry) => {
          if (entry.kind === "link") {
            const active = isStaffNavLeafActive(entry, pathname, null);
            return (
              <li key={entry.href}>
                <Link
                  href={entry.href}
                  className={active ? styles.navLinkActive : styles.navLink}
                >
                  {entry.label}
                </Link>
              </li>
            );
          }
          const modActive = isStaffNavModuleActive(entry, pathname, null);
          return (
            <li key={entry.id} className={styles.navGroup}>
              <div className={styles.navGroupRow}>
                <Link
                  href={entry.href}
                  className={
                    modActive ? styles.navGroupLinkActive : styles.navGroupLink
                  }
                >
                  {entry.label}
                </Link>
              </div>
              {modActive ? (
                <ul className={styles.navSubList}>
                  {entry.children.map((leaf) => {
                    const active = isStaffNavLeafActive(
                      leaf,
                      pathname,
                      null,
                      entry.defaultTab,
                    );
                    return (
                      <li key={leaf.href}>
                        <Link
                          href={leaf.href}
                          className={
                            active
                              ? styles.navSubLinkActive
                              : styles.navSubLink
                          }
                        >
                          {leaf.label}
                        </Link>
                      </li>
                    );
                  })}
                </ul>
              ) : null}
            </li>
          );
        })}
      </ul>
    </nav>
  );
}
