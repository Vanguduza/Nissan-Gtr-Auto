"use client";

import { useSearchParams } from "next/navigation";
import { Suspense } from "react";
import { StaffHrOnboardingWizard } from "@/components/staff-hr-onboarding-wizard";
import { StaffHrOrganogramPanel } from "@/components/staff-hr-organogram-panel";
import { StaffHrPanel } from "@/components/staff-hr-panel";
import { StaffHrPayrollPanel } from "@/components/staff-hr-payroll-panel";
import { StaffNav } from "@/components/staff-nav";
import { iconSizeMd, iconStroke, Users } from "@/components/icons";
import styles from "@/components/account.module.css";

function StaffHrBody() {
  const searchParams = useSearchParams();
  const tab = searchParams.get("tab");
  const isOrganogram = tab === "organogram";
  const isOnboarding = tab === "onboarding";
  const isPayroll = tab === "payroll";

  const title = isOrganogram
    ? "Organogram"
    : isOnboarding
      ? "Onboarding"
      : isPayroll
        ? "Payroll & payslips"
        : "HR attendance";
  const subtitle = isOrganogram
    ? "Grades, reporting tree, and module access for staff roles."
    : isOnboarding
      ? "Five-stage resumable staff onboarding (banking/health RLS-tight)."
      : isPayroll
        ? "Schedule or on-demand fund from cash GL + branded PDF payslips (gross − manual only)."
        : "Clock time, period hours, and manual payroll deductions.";
  const navCurrent = isOrganogram
    ? "/staff/hr?tab=organogram"
    : isOnboarding
      ? "/staff/hr?tab=onboarding"
      : isPayroll
        ? "/staff/hr?tab=payroll"
        : "/staff/hr";

  return (
    <div className={styles.shell}>
      <StaffNav current={navCurrent} />
      <div className={styles.panel}>
        <header className={styles.pageHeader}>
          <h1 className={styles.title}>
            <span className={styles.titleIcon} aria-hidden>
              <Users size={iconSizeMd} strokeWidth={iconStroke} />
            </span>
            {title}
          </h1>
          <p className={styles.pageSubtitle}>{subtitle}</p>
        </header>
        <div className={styles.pageBody}>
          {isOrganogram ? (
            <StaffHrOrganogramPanel />
          ) : isOnboarding ? (
            <StaffHrOnboardingWizard />
          ) : isPayroll ? (
            <StaffHrPayrollPanel />
          ) : (
            <StaffHrPanel />
          )}
        </div>
      </div>
    </div>
  );
}

export default function StaffHrPageClient() {
  return (
    <Suspense fallback={<p className={styles.muted}>Loading HR…</p>}>
      <StaffHrBody />
    </Suspense>
  );
}
