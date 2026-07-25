import { StaffFleetPanel } from "@/components/staff-fleet-panel";
import { StaffNav } from "@/components/staff-nav";
import { Car, iconSizeMd, iconStroke } from "@/components/icons";
import styles from "@/components/account.module.css";

export const metadata = { title: "Staff · Company fleet" };

export default function StaffFleetPage() {
  return (
    <div className={styles.shell}>
      <StaffNav current="/staff/fleet" />
      <div className={styles.panel}>
        <header className={styles.pageHeader}>
          <h1 className={styles.title}>
            <span className={styles.titleIcon} aria-hidden>
              <Car size={iconSizeMd} strokeWidth={iconStroke} />
            </span>
            Company fleet
          </h1>
          <p className={styles.pageSubtitle}>
            Vehicles, assignments, and fleet maintenance records.
          </p>
        </header>
        <div className={styles.pageBody}>
          <StaffFleetPanel />
        </div>
      </div>
    </div>
  );
}
