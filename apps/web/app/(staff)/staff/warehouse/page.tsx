import { StaffNav } from "@/components/staff-nav";
import { iconSizeMd, iconStroke, Warehouse } from "@/components/icons";
import styles from "@/components/account.module.css";

export const metadata = { title: "Staff · Warehouse" };

export default function StaffWarehouseHubPage() {
  return (
    <div className={styles.shell}>
      <StaffNav current="/staff/warehouse" />
      <div className={styles.panel}>
        <header className={styles.pageHeader}>
          <h1 className={styles.title}>
            <span className={styles.titleIcon} aria-hidden>
              <Warehouse size={iconSizeMd} strokeWidth={iconStroke} />
            </span>
            Warehouse
          </h1>
          <p className={styles.pageSubtitle}>
            Choose receive, bins, cycle count, or transfers in the sidebar.
          </p>
        </header>
      </div>
    </div>
  );
}
