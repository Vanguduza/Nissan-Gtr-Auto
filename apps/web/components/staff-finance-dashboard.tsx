"use client";

import Link from "next/link";
import {
  staffNavIconForLeaf,
  staffNavIconForModule,
} from "@/components/staff-nav";
import {
  ArrowRight,
  iconSizeMd,
  iconSizeSm,
  iconStroke,
} from "@/components/icons";
import {
  filterNavTreeForRoles,
  STAFF_NAV_TREE,
  type StaffNavModule,
  type StaffRole,
} from "@/lib/staff-auth";
import { useStaffAuth } from "@/components/staff-auth-context";
import styles from "@/components/account.module.css";

function financeModule(roles: readonly StaffRole[]): StaffNavModule | null {
  const tree = filterNavTreeForRoles([...roles]);
  const mod = tree.find(
    (e): e is StaffNavModule => e.kind === "module" && e.id === "finance",
  );
  return mod ?? null;
}

/** Tile dashboard for `/staff/finance` (no `?tab=`). */
export function StaffFinanceDashboard() {
  const ctx = useStaffAuth();
  const roles = ctx?.roles ?? [];
  const mod =
    financeModule(roles) ??
    (STAFF_NAV_TREE.find(
      (e): e is StaffNavModule => e.kind === "module" && e.id === "finance",
    ) ?? null);

  if (!mod) return null;

  const ModIcon = staffNavIconForModule(mod);

  return (
    <div className={styles.hubModules}>
      <section className={styles.hubModule} aria-labelledby="finance-hub-title">
        <div className={styles.hubModuleHead}>
          <span className={styles.hubModuleIcon} aria-hidden>
            <ModIcon size={iconSizeSm} strokeWidth={iconStroke} />
          </span>
          <h2 id="finance-hub-title" className={styles.hubModuleTitle}>
            Finance
          </h2>
          <span className={styles.hubModuleIconAccent} aria-hidden />
        </div>
        <p className={styles.muted} style={{ margin: "0 0 0.85rem" }}>
          Choose a desk — online sales master, float, payments, reports, and
          ledger tools.
        </p>
        <div className={styles.cardGrid}>
          {mod.children.map((c) => {
            const LeafIcon = staffNavIconForLeaf(c);
            return (
              <Link key={c.href} href={c.href} className={styles.card}>
                <span className={styles.cardIcon} aria-hidden>
                  <LeafIcon size={iconSizeMd} strokeWidth={iconStroke} />
                </span>
                <span className={styles.cardCopy}>
                  <span className={styles.cardLabel}>{c.label}</span>
                </span>
                <span className={styles.cardArrow} aria-hidden>
                  <ArrowRight size={iconSizeSm} strokeWidth={iconStroke} />
                </span>
              </Link>
            );
          })}
        </div>
      </section>
    </div>
  );
}
