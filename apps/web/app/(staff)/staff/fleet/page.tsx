import { StaffFleetPanel } from "@/components/staff-fleet-panel";
import { StaffNav } from "@/components/staff-nav";
import styles from "@/components/account.module.css";

export const metadata = { title: "Staff · Company fleet" };

export default function StaffFleetPage() {
  return (
    <div className={styles.shell}>
      <StaffNav current="/staff/fleet" />
      <div className={styles.panel}>
        <h1 className={styles.title}>Company fleet</h1>
        <StaffFleetPanel />
      </div>
    </div>
  );
}
