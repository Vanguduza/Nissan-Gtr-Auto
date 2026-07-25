"use client";

import Link from "next/link";
import { StaffNav } from "@/components/staff-nav";
import { useStaffAuth } from "@/components/staff-auth-context";
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
          <h1 className={styles.title}>Staff</h1>
          <p className={styles.pageSubtitle}>
            Modules and tools for your assigned roles.
          </p>
        </header>
        <div className={styles.pageBody}>
          <div className={styles.hubModules}>
          {modules.map((mod) => (
            <section
              key={mod.id}
              className={styles.hubModule}
              aria-labelledby={`hub-${mod.id}`}
            >
              <h2 id={`hub-${mod.id}`} className={styles.hubModuleTitle}>
                {mod.label}
              </h2>
              <div className={styles.cardGrid}>
                {mod.children.map((c) => (
                  <Link key={c.href} href={c.href} className={styles.card}>
                    <span className={styles.cardLabel}>{c.label}</span>
                  </Link>
                ))}
              </div>
            </section>
          ))}
        </div>
        </div>
      </div>
    </div>
  );
}
