import Link from "next/link";
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
        <p className={styles.lede}>
          Draft → count lines → submit (dual-auth when variance exceeds
          threshold) → approve or cancel posted. Explicit currency on the draft.{" "}
          <Link href="/staff/warehouse">Warehouse hub</Link>.
        </p>
        <StaffWarehouseCycleCountPanel />
      </div>
    </div>
  );
}
