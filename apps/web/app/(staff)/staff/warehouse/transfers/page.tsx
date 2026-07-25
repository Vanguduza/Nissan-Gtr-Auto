import { StaffNav } from "@/components/staff-nav";
import { StaffWarehouseTransfersPanel } from "@/components/staff-warehouse-transfers-panel";
import { iconSizeMd, iconStroke, ListOrdered } from "@/components/icons";
import styles from "@/components/account.module.css";

export const metadata = { title: "Staff · Transfers" };

export default function StaffWarehouseTransfersPage() {
  return (
    <div className={styles.shell}>
      <StaffNav current="/staff/warehouse/transfers" />
      <div className={styles.panel}>
        <header className={styles.pageHeader}>
          <h1 className={styles.title}>
            <span className={styles.titleIcon} aria-hidden>
              <ListOrdered size={iconSizeMd} strokeWidth={iconStroke} />
            </span>
            Stock transfers
          </h1>
          <p className={styles.pageSubtitle}>
            Create and post inter-warehouse stock transfer documents.
          </p>
        </header>
        <div className={styles.pageBody}>
          <StaffWarehouseTransfersPanel />
        </div>
      </div>
    </div>
  );
}
