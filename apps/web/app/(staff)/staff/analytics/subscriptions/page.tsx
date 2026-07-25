import { StaffAnalyticsSubscriptionsPanel } from "@/components/staff-analytics-subscriptions-panel";
import { StaffAnalyticsTabs } from "@/components/staff-analytics-tabs";
import { StaffNav } from "@/components/staff-nav";
import styles from "@/components/account.module.css";

export const metadata = { title: "Staff · Report subscriptions" };

export default function StaffAnalyticsSubscriptionsPage() {
  return (
    <div className={styles.shell}>
      <StaffNav current="/staff/analytics/subscriptions" />
      <div className={styles.panel}>
        <h1 className={styles.title}>Report subscriptions</h1>
        <p className={styles.lede}>
          Schedule daily, weekly, or monthly KPI reports to email and/or
          WhatsApp. Worker delivers numeric-only when Gemini is unavailable.
        </p>
        <StaffAnalyticsTabs active="subscriptions" />
        <StaffAnalyticsSubscriptionsPanel />
      </div>
    </div>
  );
}
