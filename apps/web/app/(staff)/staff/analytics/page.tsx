import { StaffAnalyticsPanel } from "@/components/staff-analytics-panel";
import { StaffAnalyticsTabs } from "@/components/staff-analytics-tabs";
import { StaffNav } from "@/components/staff-nav";
import styles from "@/components/account.module.css";

export const metadata = { title: "Staff · Analytics" };

export default function StaffAnalyticsPage() {
  return (
    <div className={styles.shell}>
      <StaffNav current="/staff/analytics" />
      <div className={styles.panel}>
        <h1 className={styles.title}>Analytics</h1>
        <p className={styles.lede}>
          Ops / sales KPIs with optional AI narrative. Admin, finance, and sales
          only. Aggregates — no customer PII or ledger dumps to the model.
        </p>
        <StaffAnalyticsTabs active="kpis" />
        <StaffAnalyticsPanel />
      </div>
    </div>
  );
}
