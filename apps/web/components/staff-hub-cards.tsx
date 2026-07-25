"use client";

import Link from "next/link";
import styles from "@/components/account.module.css";
import { useStaffAuth } from "@/components/staff-auth-context";
import {
  filterNavForRoles,
  STAFF_MODULE_ROLES,
  type StaffRole,
} from "@/lib/staff-auth";

type HubCard = {
  href: string;
  label: string;
  blurb: string;
  roles: readonly StaffRole[];
};

const cards: HubCard[] = [
  {
    href: "/staff/pos",
    label: "POS",
    blurb: "Cart · lines · checkout",
    roles: STAFF_MODULE_ROLES.pos,
  },
  {
    href: "/staff/warehouse",
    label: "Warehouse",
    blurb: "Receive · transfer · count",
    roles: STAFF_MODULE_ROLES.warehouse,
  },
  {
    href: "/staff/finance",
    label: "Finance",
    blurb: "Journals · reports · payments",
    roles: STAFF_MODULE_ROLES.finance,
  },
  {
    href: "/staff/hr",
    label: "HR",
    blurb: "Clock + hours",
    roles: STAFF_MODULE_ROLES.hr,
  },
  {
    href: "/staff/logistics",
    label: "Logistics",
    blurb: "Pick · DN · job",
    roles: STAFF_MODULE_ROLES.logistics,
  },
  {
    href: "/staff/logistics/tracking",
    label: "Live map",
    blurb: "Realtime GPS subscribe",
    roles: STAFF_MODULE_ROLES.liveMap,
  },
  {
    href: "/staff/warranty",
    label: "Warranty",
    blurb: "Claims · quarantine return",
    roles: STAFF_MODULE_ROLES.warranty,
  },
  {
    href: "/procurement",
    label: "Procurement",
    blurb: "RFQs",
    roles: STAFF_MODULE_ROLES.procurement,
  },
];

export function StaffHubCards() {
  const ctx = useStaffAuth();
  const roles = ctx?.roles ?? [];
  const allowed = new Set(filterNavForRoles(roles).map((i) => i.href));
  const visible = cards.filter((card) => allowed.has(card.href));

  if (!ctx) {
    return <p className={styles.muted}>Loading modules…</p>;
  }

  if (visible.length === 0) {
    return (
      <p className={styles.muted}>
        No staff modules for your roles. Ask an admin to assign roles in{" "}
        <code>staff_roles</code>.
      </p>
    );
  }

  return (
    <div className={styles.cardGrid}>
      {visible.map((card) => (
        <Link key={card.href} href={card.href} className={styles.card}>
          <span className={styles.cardLabel}>{card.label}</span>
          <span className={styles.cardBlurb}>{card.blurb}</span>
        </Link>
      ))}
    </div>
  );
}
