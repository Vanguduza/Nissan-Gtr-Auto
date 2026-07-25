import { StaffLogisticsTabs } from "@/components/staff-logistics-tabs";
import { StaffNav } from "@/components/staff-nav";
import { StaffOnlinePrepPanel } from "@/components/staff-online-prep-panel";
import styles from "@/components/account.module.css";

export const metadata = { title: "Staff · Sales prep" };

export default function StaffLogisticsPrepPage() {
  return (
    <div className={styles.shell}>
      <StaffNav current="/staff/logistics/prep" />
      <div className={styles.panel}>
        <h1 className={styles.title}>Sales prep</h1>
        <p className={styles.lede}>
          Online dispatch orders waiting for pick/pack. Confirm pick under Jobs;
          a driver is assigned automatically when prep completes.
        </p>
        <StaffLogisticsTabs active="prep" />
        <StaffOnlinePrepPanel />
      </div>
    </div>
  );
}
