import { StaffLogisticsPanel } from "@/components/staff-logistics-panel";
import { StaffLogisticsTabs } from "@/components/staff-logistics-tabs";
import { StaffNav } from "@/components/staff-nav";
import styles from "@/components/account.module.css";

export const metadata = { title: "Staff · Logistics" };

export default function StaffLogisticsPage() {
  return (
    <div className={styles.shell}>
      <StaffNav current="/staff/logistics" />
      <div className={styles.panel}>
        <h1 className={styles.title}>Logistics</h1>
        <p className={styles.lede}>
          Dispatch pick list → confirm lines → delivery note → optional delivery
          job. Online storefront orders auto-create picks and notify sales;
          driver auto-assigns after prep.
        </p>
        <StaffLogisticsTabs active="jobs" />
        <StaffLogisticsPanel />
      </div>
    </div>
  );
}
