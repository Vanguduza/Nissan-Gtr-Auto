import { StaffLogisticsPanel } from "@/components/staff-logistics-panel";
import { StaffNav } from "@/components/staff-nav";
import styles from "@/components/account.module.css";

export const metadata = { title: "Staff · Logistics" };

export default function StaffLogisticsPage() {
  return (
    <div className={styles.shell}>
      <StaffNav current="/staff/logistics" />
      <div className={styles.panel}>
        <h1 className={styles.title}>Logistics</h1>
        <StaffLogisticsPanel />
      </div>
    </div>
  );
}
