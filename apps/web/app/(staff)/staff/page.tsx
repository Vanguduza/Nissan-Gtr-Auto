"use client";

import Link from "next/link";
import { StaffNav } from "@/components/staff-nav";
import { useStaffAuth } from "@/components/staff-auth-context";
import { filterNavForRoles } from "@/lib/staff-auth";
import styles from "@/components/account.module.css";

const HUB_CARDS: {
  href: string;
  label: string;
  blurb: string;
}[] = [
  { href: "/staff/pos", label: "POS counter", blurb: "Cart · named customer · checkout" },
  {
    href: "/staff/warehouse",
    label: "Warehouse ops",
    blurb: "Receive · transfer · bins · pick path",
  },
  {
    href: "/staff/finance",
    label: "Finance ledger",
    blurb: "Journals · reports · payments",
  },
  {
    href: "/staff/crm/credit",
    label: "Customer credit",
    blurb: "Limit · hold · open balance",
  },
  {
    href: "/staff/crm/reviews",
    label: "Review moderation",
    blurb: "Approve · reject product reviews",
  },
  { href: "/staff/hr", label: "HR desk", blurb: "Clock + hours" },
  { href: "/staff/logistics", label: "Logistics", blurb: "Pick · DN · job" },
  {
    href: "/staff/logistics/tracking",
    label: "Live tracking",
    blurb: "Assign · ETA · map",
  },
  {
    href: "/staff/logistics/panic",
    label: "Panic inbox",
    blurb: "Driver SOS · acknowledge",
  },
  {
    href: "/staff/warranty",
    label: "Warranty claims",
    blurb: "Claims · quarantine return",
  },
  {
    href: "/staff/chat",
    label: "Customer chat",
    blurb: "Inbox · claim · reply",
  },
  {
    href: "/staff/analytics",
    label: "Analytics",
    blurb: "KPIs · AI narrative · reports",
  },
  { href: "/procurement", label: "Procurement", blurb: "RFQs · blankets" },
];

export default function StaffHubPage() {
  const ctx = useStaffAuth();
  const allowedHrefs = new Set(
    filterNavForRoles(ctx?.roles ?? []).map((i) => i.href),
  );
  const cards = HUB_CARDS.filter((c) => allowedHrefs.has(c.href));

  return (
    <div className={styles.shell}>
      <StaffNav current="/staff" />
      <div className={styles.panel}>
        <h1 className={styles.title}>Staff</h1>
        <p className={styles.lede}>
          Management fallback for POS, warehouse, finance, attendance, dispatch,
          and live delivery tracking. Roles from <code>staff_roles</code>; RPCs
          remain the source of truth. QR / GPS use the Android management
          device — not the browser.
        </p>
        <div className={styles.cardGrid}>
          {cards.map((c) => (
            <Link key={c.href} href={c.href} className={styles.card}>
              <span className={styles.cardLabel}>{c.label}</span>
              <span className={styles.cardBlurb}>{c.blurb}</span>
            </Link>
          ))}
        </div>
      </div>
    </div>
  );
}
