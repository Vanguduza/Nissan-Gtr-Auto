import { StaffNav } from "@/components/staff-nav";
import { StaffDashboardPanel } from "@/components/staff-dashboard-panel";
import { LayoutDashboard, iconSizeMd, iconStroke } from "@/components/icons";
import styles from "@/components/account.module.css";

export const metadata = { title: "Staff · Today" };

export default function StaffDashboardPage() {
  return (
    <div className={styles.shell}>
      <StaffNav current="/staff/dashboard" />
      <div className={styles.panel}>
        <header className={styles.pageHeader}>
          <h1 className={styles.title}>
            <span className={styles.titleIcon} aria-hidden>
              <LayoutDashboard size={iconSizeMd} strokeWidth={iconStroke} />
            </span>
            Today
          </h1>
          <p className={styles.pageSubtitle}>
            The day at a glance: sales and margin, money taken, what is owed, tills, deliveries, driver cash and stock to reorder.
          </p>
        </header>
        <div className={styles.pageBody}>
          <StaffDashboardPanel />
        </div>
      </div>
    </div>
  );
}
