import { StaffConsignmentPanel } from "@/components/staff-consignment-panel";
import { StaffNav } from "@/components/staff-nav";
import styles from "@/components/account.module.css";

export const metadata = { title: "Staff · Consignment" };

export default function StaffWarehouseConsignmentPage() {
  return (
    <div className={styles.shell}>
      <StaffNav current="/staff/warehouse/consignment" />
      <div className={styles.panel}>
        <h1 className={styles.title}>Consignment</h1>
        <StaffConsignmentPanel />
      </div>
    </div>
  );
}
