import Link from "next/link";
import { StaffNav } from "@/components/staff-nav";
import { StaffWarehouseReceivePanel } from "@/components/staff-warehouse-receive-panel";
import { StaffWarehouseTabs } from "@/components/staff-warehouse-tabs";
import styles from "@/components/account.module.css";

export const metadata = { title: "Staff · Receive" };

export default function StaffWarehouseReceivePage() {
  return (
    <div className={styles.shell}>
      <StaffNav current="/staff/warehouse/receive" />
      <div className={styles.panel}>
        <h1 className={styles.title}>Receive stock</h1>
        <p className={styles.lede}>
          Post a goods receipt with explicit line currency (USD | ZIG).{" "}
          <Link href="/staff/warehouse">Warehouse hub</Link>.
        </p>
        <StaffWarehouseTabs active="receive" />
        <StaffWarehouseReceivePanel />
      </div>
    </div>
  );
}
