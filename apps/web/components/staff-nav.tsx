"use client";

import Link from "next/link";
import styles from "@/components/account.module.css";
import { useStaffAuth } from "@/components/staff-auth-context";
import { filterNavForRoles, STAFF_NAV_ITEMS } from "@/lib/staff-auth";

export function StaffNav({ current }: { current: string }) {
  const ctx = useStaffAuth();
  const items = ctx
    ? filterNavForRoles(ctx.roles)
    : STAFF_NAV_ITEMS.filter((i) => i.roles === "any");

  return (
    <nav className={styles.nav} aria-label="Staff">
      <p className={styles.navTitle}>Staff</p>
      <ul className={styles.navList}>
        {items.map((item) => {
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
