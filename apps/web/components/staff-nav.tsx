"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import styles from "@/components/account.module.css";
import {
  fetchMyStaffRoles,
  hasAnyStaffRole,
  STAFF_MODULE_ROLES,
  type StaffRole,
} from "@/lib/staff-auth";
import { createWebClient } from "@/lib/supabase";

type NavItem = {
  href: string;
  label: string;
  exact?: boolean;
  /** Empty = any staff; otherwise require at least one matching role. */
  roles: readonly StaffRole[];
};

const nav: NavItem[] = [
  { href: "/staff", label: "Hub", exact: true, roles: STAFF_MODULE_ROLES.hub },
  { href: "/staff/pos", label: "POS", roles: STAFF_MODULE_ROLES.pos },
  {
    href: "/staff/warehouse",
    label: "Warehouse",
    roles: STAFF_MODULE_ROLES.warehouse,
  },
  { href: "/staff/hr", label: "HR", roles: STAFF_MODULE_ROLES.hr },
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
];

export function StaffNav({ current }: { current: string }) {
  const [roles, setRoles] = useState<StaffRole[] | null>(null);

  useEffect(() => {
    const client = createWebClient();
    if (!client) {
      setRoles([]);
      return;
    }
    let cancelled = false;
    void fetchMyStaffRoles(client).then((result) => {
      if (cancelled) return;
      setRoles(result.ok ? result.data : []);
    });
    return () => {
      cancelled = true;
    };
  }, []);

  const visible = nav.filter((item) => {
    if (item.roles.length === 0) return true;
    if (roles === null) return false;
    return hasAnyStaffRole(roles, item.roles);
  });

  return (
    <nav className={styles.nav} aria-label="Staff">
      <p className={styles.navTitle}>Staff</p>
      <ul className={styles.navList}>
        {visible.map((item) => {
          const active = item.exact
            ? current === item.href
            : current === item.href || current.startsWith(`${item.href}/`);
          return (
            <li key={item.href}>
              <Link
                href={item.href}
                className={active ? styles.navLinkActive : styles.navLink}
              >
                {item.label}
              </Link>
            </li>
          );
        })}
      </ul>
    </nav>
  );
}
