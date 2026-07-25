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
    label: "Live tracking",
    blurb: "Realtime map · subscribe-only",
    roles: STAFF_MODULE_ROLES.liveMap,
  },
  {
    href: "/procurement",
    label: "Procurement",
    blurb: "RFQs",
    roles: STAFF_MODULE_ROLES.procurement,
  },
];

export function StaffHubCards() {
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

  if (roles === null) {
    return <p className={styles.muted}>Loading modules…</p>;
  }

  const visible = cards.filter((card) =>
    hasAnyStaffRole(roles, card.roles),
  );

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
