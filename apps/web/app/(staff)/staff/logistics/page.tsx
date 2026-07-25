import { StaffLogisticsPanel } from "@/components/staff-logistics-panel";
import { StaffNav } from "@/components/staff-nav";
import { iconSizeMd, iconStroke, Truck } from "@/components/icons";
import styles from "@/components/account.module.css";

export const metadata = { title: "Staff · Logistics" };

export default function StaffLogisticsPage() {
  return (
    <div className={styles.shell}>
      <StaffNav current="/staff/logistics" />
      <div className={styles.panel}>
        <header className={styles.pageHeader}>
          <h1 className={styles.title}>
            <span className={styles.titleIcon} aria-hidden>
              <Truck size={iconSizeMd} strokeWidth={iconStroke} />
            </span>
            Logistics
          </h1>
          <p className={styles.pageSubtitle}>
            Pick, dispatch, and delivery notes for outbound orders.
          </p>
        </header>
        <div className={styles.pageBody}>
          <StaffLogisticsPanel />
        </div>
      </div>
    </div>
  );
}
