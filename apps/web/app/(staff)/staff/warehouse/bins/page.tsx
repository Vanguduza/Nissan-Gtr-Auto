import { StaffBinsPanel } from "@/components/staff-bins-panel";
import { StaffNav } from "@/components/staff-nav";
import styles from "@/components/account.module.css";

export const metadata = { title: "Staff · Bins" };

export default function StaffWarehouseBinsPage() {
  return (
    <div className={styles.shell}>
      <StaffNav current="/staff/warehouse/bins" />
      <div className={styles.panel}>
        <h1 className={styles.title}>Warehouse bins</h1>
        <StaffBinsPanel />
      </div>
    </div>
  );
}
