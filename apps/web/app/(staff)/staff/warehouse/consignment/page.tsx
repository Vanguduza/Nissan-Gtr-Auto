import { StaffConsignmentPanel } from "@/components/staff-consignment-panel";
import { StaffNav } from "@/components/staff-nav";
import { iconSizeMd, iconStroke, PackageSearch } from "@/components/icons";
import styles from "@/components/account.module.css";

export const metadata = { title: "Staff · Consignment" };

export default function StaffWarehouseConsignmentPage() {
  return (
    <div className={styles.shell}>
      <StaffNav current="/staff/warehouse/consignment" />
      <div className={styles.panel}>
        <header className={styles.pageHeader}>
          <h1 className={styles.title}>
            <span className={styles.titleIcon} aria-hidden>
              <PackageSearch size={iconSizeMd} strokeWidth={iconStroke} />
            </span>
            Consignment
          </h1>
          <p className={styles.pageSubtitle}>
            Consignment stock receipts, sales, and supplier settlements.
          </p>
        </header>
        <div className={styles.pageBody}>
          <StaffConsignmentPanel />
        </div>
      </div>
    </div>
  );
}
