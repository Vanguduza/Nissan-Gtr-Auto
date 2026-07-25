import { StaffAnalyticsPanel } from "@/components/staff-analytics-panel";
import { StaffNav } from "@/components/staff-nav";
import { BarChart3, iconSizeMd, iconStroke } from "@/components/icons";
import styles from "@/components/account.module.css";

export const metadata = { title: "Staff · Analytics" };

export default function StaffAnalyticsPage() {
  return (
    <div className={styles.shell}>
      <StaffNav current="/staff/analytics" />
      <div className={styles.panel}>
        <header className={styles.pageHeader}>
          <h1 className={styles.title}>
            <span className={styles.titleIcon} aria-hidden>
              <BarChart3 size={iconSizeMd} strokeWidth={iconStroke} />
            </span>
            Analytics
          </h1>
          <p className={styles.pageSubtitle}>
            Ops and sales KPIs for a chosen period — subscriptions are in the
            sidebar.
          </p>
        </header>
        <div className={styles.pageBody}>
          <StaffAnalyticsPanel />
        </div>
      </div>
    </div>
  );
}
