"use client";

import Link from "next/link";
import { StaffNav } from "@/components/staff-nav";
import { useStaffAuth } from "@/components/staff-auth-context";
import { filterNavForRoles } from "@/lib/staff-auth";
import styles from "@/components/account.module.css";

/** Module → sub-feature cards (mirrors Android hub hierarchy where possible). */
const HUB_MODULES: {
  id: string;
  label: string;
  features: { href: string; label: string; blurb: string }[];
}[] = [
  {
    id: "pos",
    label: "POS",
    features: [
      {
        href: "/staff/pos",
        label: "POS counter",
        blurb: "Cart · named customer · checkout",
      },
    ],
  },
  {
    id: "warehouse",
    label: "Warehouse",
    features: [
      {
        href: "/staff/warehouse",
        label: "Warehouse ops",
        blurb: "Receive · transfer · bins · pick path",
      },
    ],
  },
  {
    id: "finance",
    label: "Finance",
    features: [
      {
        href: "/staff/finance",
        label: "Finance ledger",
        blurb: "Journals · reports · payments",
      },
    ],
  },
  {
    id: "crm",
    label: "CRM",
    features: [
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
    ],
  },
  {
    id: "hr",
    label: "HR",
    features: [{ href: "/staff/hr", label: "HR desk", blurb: "Clock + hours" }],
  },
  {
    id: "logistics",
    label: "Logistics",
    features: [
      {
        href: "/staff/logistics",
        label: "Pick / DN / Dispatch",
        blurb: "Pick · DN · job",
      },
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
    ],
  },
  {
    id: "fleet",
    label: "Company fleet",
    features: [
      {
        href: "/staff/fleet",
        label: "Company fleet",
        blurb: "Plates · status · driver assign",
      },
    ],
  },
  {
    id: "warranty",
    label: "Warranty",
    features: [
      {
        href: "/staff/warranty",
        label: "Warranty claims",
        blurb: "Claims · quarantine return",
      },
    ],
  },
  {
    id: "chat",
    label: "Chat",
    features: [
      {
        href: "/staff/chat",
        label: "Customer chat",
        blurb: "Inbox · claim · reply",
      },
    ],
  },
  {
    id: "analytics",
    label: "Analytics",
    features: [
      {
        href: "/staff/analytics",
        label: "Analytics",
        blurb: "KPIs · AI narrative · reports",
      },
      {
        href: "/staff/analytics/subscriptions",
        label: "Report subscriptions",
        blurb: "Scheduled report delivery",
      },
    ],
  },
  {
    id: "procurement",
    label: "Procurement",
    features: [
      { href: "/procurement", label: "Procurement", blurb: "RFQs · blankets" },
    ],
  },
];

export default function StaffHubPage() {
  const ctx = useStaffAuth();
  const allowedHrefs = new Set(
    filterNavForRoles(ctx?.roles ?? []).map((i) => i.href),
  );
  const modules = HUB_MODULES.map((mod) => ({
    ...mod,
    features: mod.features.filter((f) => allowedHrefs.has(f.href)),
  })).filter((mod) => mod.features.length > 0);

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
        <div className={styles.hubModules}>
          {modules.map((mod) => (
            <section key={mod.id} className={styles.hubModule} aria-labelledby={`hub-${mod.id}`}>
              <h2 id={`hub-${mod.id}`} className={styles.hubModuleTitle}>
                {mod.label}
              </h2>
              <div className={styles.cardGrid}>
                {mod.features.map((c) => (
                  <Link key={c.href} href={c.href} className={styles.card}>
                    <span className={styles.cardLabel}>{c.label}</span>
                    <span className={styles.cardBlurb}>{c.blurb}</span>
                  </Link>
                ))}
              </div>
            </section>
          ))}
        </div>
      </div>
    </div>
  );
}
