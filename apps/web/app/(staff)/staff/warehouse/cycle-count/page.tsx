import { StaffNav } from "@/components/staff-nav";
import { StaffWarehouseCycleCountPanel } from "@/components/staff-warehouse-cycle-count-panel";
import { ClipboardList, iconSizeMd, iconStroke } from "@/components/icons";
import styles from "@/components/account.module.css";

export const metadata = { title: "Staff · Cycle count" };

export default function StaffWarehouseCycleCountPage() {
  return (
    <div className={styles.shell}>
      <StaffNav current="/staff/warehouse/cycle-count" />
      <div className={styles.panel}>
        <header className={styles.pageHeader}>
          <h1 className={styles.title}>
            <span className={styles.titleIcon} aria-hidden>
              <ClipboardList size={iconSizeMd} strokeWidth={iconStroke} />
            </span>
            Cycle count
          </h1>
          <p className={styles.pageSubtitle}>
            Count sessions, variances, and inventory adjustments.
          </p>
        </header>
        <div className={styles.pageBody}>
          <StaffWarehouseCycleCountPanel />
        </div>
      </div>
    </div>
  );
}
