import { StaffNav } from "@/components/staff-nav";
import { StaffStoresInsightsPanel } from "@/components/staff-stores-insights-panel";
import { BarChart3, iconSizeMd, iconStroke } from "@/components/icons";
import styles from "@/components/account.module.css";

export const metadata = { title: "Staff · Warehouse AI insights" };

export default function StaffWarehouseInsightsPage() {
  return (
    <div className={styles.shell}>
      <StaffNav current="/staff/warehouse/insights" />
      <div className={styles.panel}>
        <header className={styles.pageHeader}>
          <h1 className={styles.title}>
            <span className={styles.titleIcon} aria-hidden>
              <BarChart3 size={iconSizeMd} strokeWidth={iconStroke} />
            </span>
            Warehouse AI insights
          </h1>
          <p className={styles.pageSubtitle}>
            ABC classification, forecast suggestions, and structured restock /
            clearance directives. Suggestions never auto-post POs.
          </p>
        </header>
        <div className={styles.pageBody}>
          <StaffStoresInsightsPanel />
        </div>
      </div>
    </div>
  );
}
