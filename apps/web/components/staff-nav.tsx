"use client";

import Link from "next/link";
import styles from "@/components/account.module.css";
import {
  Banknote,
  BarChart3,
  Bell,
  iconSizeSm,
  iconStroke,
  LayoutGrid,
  MapPinned,
  PackageCheck,
  Siren,
  Car,
  MessageCircle,
  Monitor,
  PackageSearch,
  ShieldCheck,
  Star,
  Truck,
  Users,
  Warehouse,
  type LucideIcon,
} from "@/components/icons";
import { useStaffAuth } from "@/components/staff-auth-context";
import { filterNavForRoles, STAFF_NAV_ITEMS } from "@/lib/staff-auth";

const staffNavIcons: Record<string, LucideIcon> = {
  "/staff": LayoutGrid,
  "/staff/pos": Monitor,
  "/staff/warehouse": Warehouse,
  "/staff/finance": Banknote,
  "/staff/crm/credit": Users,
  "/staff/crm/reviews": Star,
  "/staff/logistics": Truck,
  "/staff/logistics/prep": PackageCheck,
  "/staff/logistics/tracking": MapPinned,
  "/staff/logistics/panic": Siren,
  "/staff/hr": Users,
  "/staff/warranty": ShieldCheck,
  "/staff/chat": MessageCircle,
  "/staff/analytics": BarChart3,
  "/staff/analytics/subscriptions": Bell,
  "/procurement": PackageSearch,
};

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
          const Icon = staffNavIcons[item.href];
          return (
            <li key={item.href}>
              <Link
                href={item.href}
                className={active ? styles.navLinkActive : styles.navLink}
              >
                {Icon ? (
                  <Icon
                    size={iconSizeSm}
                    strokeWidth={iconStroke}
                    aria-hidden
                  />
                ) : null}
                {item.label}
              </Link>
            </li>
          );
        })}
      </ul>
    </nav>
  );
}
