import { StaffNav } from "@/components/staff-nav";
import { StaffWarehouseCycleCountPanel } from "@/components/staff-warehouse-cycle-count-panel";
import styles from "@/components/account.module.css";

export const metadata = { title: "Staff · Cycle count" };

export default function StaffWarehouseCycleCountPage() {
  return (
    <div className={styles.shell}>
      <StaffNav current="/staff/warehouse/cycle-count" />
      <div className={styles.panel}>
        <h1 className={styles.title}>Cycle count</h1>
        <StaffWarehouseCycleCountPanel />
      </div>
    </div>
  );
}
