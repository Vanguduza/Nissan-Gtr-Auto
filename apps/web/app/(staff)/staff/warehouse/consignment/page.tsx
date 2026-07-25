import Link from "next/link";
import { StaffConsignmentPanel } from "@/components/staff-consignment-panel";
import { StaffNav } from "@/components/staff-nav";
import { StaffWarehouseTabs } from "@/components/staff-warehouse-tabs";
import styles from "@/components/account.module.css";

export const metadata = { title: "Staff · Consignment" };

export default function StaffWarehouseConsignmentPage() {
  return (
    <div className={styles.shell}>
      <StaffNav current="/staff/warehouse/consignment" />
      <div className={styles.panel}>
        <h1 className={styles.title}>Consignment</h1>
        <p className={styles.lede}>
          Draft → add lines → submit or cancel via Phase 16 RPCs. Currency is
          explicit on each entry (USD | ZIG).{" "}
          <Link href="/staff/warehouse">Warehouse hub</Link>.
        </p>
        <StaffWarehouseTabs active="consignment" />
        <StaffConsignmentPanel />
      </div>
    </div>
  );
}
