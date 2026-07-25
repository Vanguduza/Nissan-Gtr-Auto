"use client";

import Link from "next/link";
import {
  StaffNav,
  staffNavIconForLeaf,
  staffNavIconForModule,
} from "@/components/staff-nav";
import { useStaffAuth } from "@/components/staff-auth-context";
import {
  ArrowRight,
  iconSizeMd,
  iconSizeSm,
  iconStroke,
  LayoutGrid,
} from "@/components/icons";
import { filterNavTreeForRoles } from "@/lib/staff-auth";
import styles from "@/components/account.module.css";

export default function StaffHubPage() {
  const ctx = useStaffAuth();
  const modules = filterNavTreeForRoles(ctx?.roles ?? []).filter(
    (e): e is Extract<typeof e, { kind: "module" }> => e.kind === "module",
  );

  return (
    <div className={styles.shell}>
      <StaffNav current="/staff" />
      <div className={styles.panel}>
        <header className={styles.pageHeader}>
          <h1 className={styles.title}>
            <span className={styles.titleIcon} aria-hidden>
              <LayoutGrid size={iconSizeMd} strokeWidth={iconStroke} />
            </span>
            Staff
          </h1>
          <p className={styles.pageSubtitle}>
            Modules and tools for your assigned roles.
          </p>
        </header>
        <div className={styles.pageBody}>
          <div className={styles.hubModules}>
            {modules.map((mod) => {
              const ModIcon = staffNavIconForModule(mod);
              return (
                <section
                  key={mod.id}
                  className={styles.hubModule}
                  aria-labelledby={`hub-${mod.id}`}
                >
                  <div className={styles.hubModuleHead}>
                    <span className={styles.hubModuleIcon} aria-hidden>
                      <ModIcon size={iconSizeSm} strokeWidth={iconStroke} />
                    </span>
                    <h2 id={`hub-${mod.id}`} className={styles.hubModuleTitle}>
                      {mod.label}
                    </h2>
                    <span className={styles.hubModuleIconAccent} aria-hidden />
                  </div>
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
              );
            })}
          </div>
        </div>
      </div>
    </div>
  );
}
