import { StaffBinsPanel } from "@/components/staff-bins-panel";
import { StaffNav } from "@/components/staff-nav";
import { iconSizeMd, iconStroke, Package } from "@/components/icons";
import styles from "@/components/account.module.css";

export const metadata = { title: "Staff · Bins" };

export default function StaffWarehouseBinsPage() {
  return (
    <div className={styles.shell}>
      <StaffNav current="/staff/warehouse/bins" />
      <div className={styles.panel}>
        <header className={styles.pageHeader}>
          <h1 className={styles.title}>
            <span className={styles.titleIcon} aria-hidden>
              <Package size={iconSizeMd} strokeWidth={iconStroke} />
            </span>
            Warehouse bins
          </h1>
          <p className={styles.pageSubtitle}>
            Bin codes, zones, and labels by warehouse location.
          </p>
        </header>
        <div className={styles.pageBody}>
          <StaffBinsPanel />
        </div>
      </div>
    </div>
  );
}
