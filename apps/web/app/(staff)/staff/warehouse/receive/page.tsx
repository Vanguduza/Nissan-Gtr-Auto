import { StaffNav } from "@/components/staff-nav";
import { StaffWarehouseReceivePanel } from "@/components/staff-warehouse-receive-panel";
import { iconSizeMd, iconStroke, PackageCheck } from "@/components/icons";
import styles from "@/components/account.module.css";

export const metadata = { title: "Staff · Receive" };

export default function StaffWarehouseReceivePage() {
  return (
    <div className={styles.shell}>
      <StaffNav current="/staff/warehouse/receive" />
      <div className={styles.panel}>
        <header className={styles.pageHeader}>
          <h1 className={styles.title}>
            <span className={styles.titleIcon} aria-hidden>
              <PackageCheck size={iconSizeMd} strokeWidth={iconStroke} />
            </span>
            Receive stock
          </h1>
          <p className={styles.pageSubtitle}>
            Post inbound receipts against purchase or transfer documents.
          </p>
        </header>
        <div className={styles.pageBody}>
          <StaffWarehouseReceivePanel />
        </div>
      </div>
    </div>
  );
}
