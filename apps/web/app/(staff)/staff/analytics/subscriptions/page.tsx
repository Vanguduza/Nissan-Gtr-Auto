import { StaffAnalyticsSubscriptionsPanel } from "@/components/staff-analytics-subscriptions-panel";
import { StaffNav } from "@/components/staff-nav";
import { Bell, iconSizeMd, iconStroke } from "@/components/icons";
import styles from "@/components/account.module.css";

export const metadata = { title: "Staff · Report subscriptions" };

export default function StaffAnalyticsSubscriptionsPage() {
  return (
    <div className={styles.shell}>
      <StaffNav current="/staff/analytics/subscriptions" />
      <div className={styles.panel}>
        <header className={styles.pageHeader}>
          <h1 className={styles.title}>
            <span className={styles.titleIcon} aria-hidden>
              <Bell size={iconSizeMd} strokeWidth={iconStroke} />
            </span>
            Report subscriptions
          </h1>
          <p className={styles.pageSubtitle}>
            Schedule recurring analytics report deliveries by email.
          </p>
        </header>
        <div className={styles.pageBody}>
          <StaffAnalyticsSubscriptionsPanel />
        </div>
      </div>
    </div>
  );
}
