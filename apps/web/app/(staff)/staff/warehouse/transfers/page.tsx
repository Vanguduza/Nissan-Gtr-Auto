import { StaffNav } from "@/components/staff-nav";
import { StaffWarehouseTabs } from "@/components/staff-warehouse-tabs";
import { StaffWarehouseTransfersPanel } from "@/components/staff-warehouse-transfers-panel";
import styles from "@/components/account.module.css";

export const metadata = { title: "Staff · Transfers" };

export default function StaffWarehouseTransfersPage() {
  return (
    <div className={styles.shell}>
      <StaffNav current="/staff/warehouse/transfers" />
      <div className={styles.panel}>
        <h1 className={styles.title}>Stock transfers</h1>
        <StaffWarehouseTabs active="transfers" />
        <StaffWarehouseTransfersPanel />
      </div>
    </div>
  );
}
