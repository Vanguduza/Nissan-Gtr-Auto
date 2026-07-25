import { StaffAnalyticsSubscriptionsPanel } from "@/components/staff-analytics-subscriptions-panel";
import { StaffNav } from "@/components/staff-nav";
import styles from "@/components/account.module.css";

export const metadata = { title: "Staff · Report subscriptions" };

export default function StaffAnalyticsSubscriptionsPage() {
  return (
    <div className={styles.shell}>
      <StaffNav current="/staff/analytics/subscriptions" />
      <div className={styles.panel}>
        <h1 className={styles.title}>Report subscriptions</h1>
        <StaffAnalyticsSubscriptionsPanel />
      </div>
    </div>
  );
}
