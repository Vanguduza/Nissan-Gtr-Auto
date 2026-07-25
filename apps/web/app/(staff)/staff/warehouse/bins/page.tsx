import Link from "next/link";
import { StaffNav } from "@/components/staff-nav";
import { StaffBinsPanel } from "@/components/staff-bins-panel";
import styles from "@/components/account.module.css";

export const metadata = { title: "Staff · Bins" };

export default function StaffWarehouseBinsPage() {
  return (
    <div className={styles.shell}>
      <StaffNav current="/staff/warehouse/bins" />
      <div className={styles.panel}>
        <h1 className={styles.title}>Warehouse bins</h1>
        <p className={styles.lede}>
          Create bins, deactivate, and set preferred stock-level bins.{" "}
          <Link href="/staff/warehouse">Warehouse hub</Link>.
        </p>
        <StaffBinsPanel />
      </div>
    </div>
  );
}
