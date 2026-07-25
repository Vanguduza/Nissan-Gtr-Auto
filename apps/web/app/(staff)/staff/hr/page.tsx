import { StaffHrPanel } from "@/components/staff-hr-panel";
import { StaffNav } from "@/components/staff-nav";
import { iconSizeMd, iconStroke, Users } from "@/components/icons";
import styles from "@/components/account.module.css";

export const metadata = { title: "Staff · HR" };

export default function StaffHrPage() {
  return (
    <div className={styles.shell}>
      <StaffNav current="/staff/hr" />
      <div className={styles.panel}>
        <header className={styles.pageHeader}>
          <h1 className={styles.title}>
            <span className={styles.titleIcon} aria-hidden>
              <Users size={iconSizeMd} strokeWidth={iconStroke} />
            </span>
            HR attendance
          </h1>
          <p className={styles.pageSubtitle}>
            Clock time, period hours, and manual payroll deductions.
          </p>
        </header>
        <div className={styles.pageBody}>
          <StaffHrPanel />
        </div>
      </div>
    </div>
  );
}
