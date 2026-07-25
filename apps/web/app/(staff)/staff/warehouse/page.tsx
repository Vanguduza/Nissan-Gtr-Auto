import { StaffNav } from "@/components/staff-nav";
import styles from "@/components/account.module.css";

export const metadata = { title: "Staff · Warehouse" };

export default function StaffWarehouseHubPage() {
  return (
    <div className={styles.shell}>
      <StaffNav current="/staff/warehouse" />
      <div className={styles.panel}>
        <h1 className={styles.title}>Warehouse</h1>
        <p className={styles.lede}>
          Open a warehouse tool from the sidebar.
        </p>
      </div>
    </div>
  );
}
