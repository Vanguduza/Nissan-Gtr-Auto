"use client";

import { StaffNav } from "@/components/staff-nav";
import { StaffAccountPanel } from "@/components/staff-account-panel";
import { iconSizeMd, iconStroke, UserRound } from "@/components/icons";
import styles from "@/components/account.module.css";

export default function StaffAccountPage() {
  return (
    <div className={styles.shell}>
      <StaffNav current="/staff/account" />
      <div className={styles.panel}>
        <header className={styles.pageHeader}>
          <h1 className={styles.title}>
            <span className={styles.titleIcon} aria-hidden>
              <UserRound size={iconSizeMd} strokeWidth={iconStroke} />
            </span>
            My Account
          </h1>
          <p className={styles.pageSubtitle}>
            Your identity, payslip history, and security settings.
          </p>
        </header>
        <StaffAccountPanel />
      </div>
    </div>
  );
}
