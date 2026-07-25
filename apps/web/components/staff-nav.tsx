"use client";

import Link from "next/link";
import styles from "@/components/account.module.css";
import { useStaffAuth } from "@/components/staff-auth-context";
import {
  BarChart3,
  ClipboardList,
  Gauge,
  iconSizeSm,
  iconStroke,
  Landmark,
  LayoutGrid,
  Mail,
  Map,
  MessageCircle,
  Package,
  ShieldCheck,
  Store,
  Truck,
  Users,
  Warehouse,
  type LucideIcon,
} from "@/components/icons";
import { filterNavForRoles, STAFF_NAV_ITEMS } from "@/lib/staff-auth";

const STAFF_NAV_ICONS: Record<string, LucideIcon> = {
  "/staff": LayoutGrid,
  "/staff/pos": Store,
  "/staff/warehouse": Warehouse,
  "/staff/finance": Landmark,
  "/staff/logistics": Truck,
  "/staff/logistics/tracking": Map,
  "/staff/hr": Users,
  "/staff/warranty": ShieldCheck,
  "/staff/chat": MessageCircle,
  "/staff/analytics": BarChart3,
  "/staff/analytics/subscriptions": Mail,
  "/procurement": ClipboardList,
};

export function StaffNav({ current }: { current: string }) {
  const ctx = useStaffAuth();
  const items = ctx
    ? filterNavForRoles(ctx.roles)
    : STAFF_NAV_ITEMS.filter((i) => i.roles === "any");

  return (
    <nav className={styles.nav} aria-label="Staff">
      <p className={styles.navTitle}>
        <Gauge size={iconSizeSm} strokeWidth={iconStroke} aria-hidden />
        Staff
      </p>
      <ul className={styles.navList}>
        {items.map((item) => {
          const active = item.exact
            ? current === item.href
            : current === item.href || current.startsWith(`${item.href}/`);
          const Icon = STAFF_NAV_ICONS[item.href] ?? Package;
          return (
            <li key={item.href}>
              <Link
                href={item.href}
                className={active ? styles.navLinkActive : styles.navLink}
              >
                <Icon size={iconSizeSm} strokeWidth={iconStroke} aria-hidden />
                {item.label}
              </Link>
            </li>
          );
        })}
      </ul>
    </nav>
  );
}
