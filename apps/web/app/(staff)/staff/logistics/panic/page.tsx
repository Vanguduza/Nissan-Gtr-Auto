import { StaffLogisticsTabs } from "@/components/staff-logistics-tabs";
import { StaffNav } from "@/components/staff-nav";
import { StaffPanicInboxPanel } from "@/components/staff-panic-inbox-panel";
import styles from "@/components/account.module.css";

export const metadata = { title: "Staff · Panic inbox" };

export default function StaffLogisticsPanicPage() {
  return (
    <div className={styles.shell}>
      <StaffNav current="/staff/logistics/panic" />
      <div className={styles.panel}>
        <h1 className={styles.title}>Panic inbox</h1>
        <StaffLogisticsTabs active="panic" />
        <StaffPanicInboxPanel />
      </div>
    </div>
  );
}
