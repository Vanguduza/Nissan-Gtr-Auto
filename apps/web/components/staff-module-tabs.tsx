"use client";

import Link from "next/link";
import styles from "@/components/staff-module-tabs.module.css";

export type StaffModuleTab = {
  id: string;
  label: string;
  /** When set, tab navigates instead of (or in addition to) onChange. */
  href?: string;
};

type Props = {
  tabs: StaffModuleTab[];
  active: string;
  onChange?: (id: string) => void;
  ariaLabel?: string;
};

export function StaffModuleTabs({
  tabs,
  active,
  onChange,
  ariaLabel = "Module sections",
}: Props) {
  return (
    <div className={styles.bar} role="tablist" aria-label={ariaLabel}>
      {tabs.map((tab) => {
        const isActive = tab.id === active;
        const className = isActive ? styles.tabActive : styles.tab;
        if (tab.href) {
          return (
            <Link
              key={tab.id}
              href={tab.href}
              role="tab"
              aria-selected={isActive}
              className={className}
            >
              {tab.label}
            </Link>
          );
        }
        return (
          <button
            key={tab.id}
            type="button"
            role="tab"
            aria-selected={isActive}
            className={className}
            onClick={() => onChange?.(tab.id)}
          >
            {tab.label}
          </button>
        );
      })}
    </div>
  );
}
