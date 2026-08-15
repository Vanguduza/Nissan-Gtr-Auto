import { MasterStockPanel } from "@/components/master-stock-panel";
import { StaffNav } from "@/components/staff-nav";
import { iconSizeMd, iconStroke, Warehouse } from "@/components/icons";
import styles from "@/components/account.module.css";

export const metadata = { title: "Staff · Master stock" };

export default function MasterStockPage() {
  return (
    <div className={`${styles.shell} ${styles.shellWide}`}>
      <StaffNav current="/staff/warehouse/master-stock" />
      <div className={styles.panel}>
        <header className={styles.pageHeader}>
          <h1 className={styles.title}>
            <span className={styles.titleIcon} aria-hidden>
              <Warehouse size={iconSizeMd} strokeWidth={iconStroke} />
            </span>
            Master stock
          </h1>
          <p className={styles.pageSubtitle}>
            Stock report across warehouses — filter by model, category, and OEM;
            export CSV for the filtered set or whole stock.
          </p>
        </header>
        <div className={styles.pageBody}>
          <MasterStockPanel />
        </div>
      </div>
    </div>
  );
}
