import { StaffDeliveryTrackingPanel } from "@/components/staff-delivery-tracking-panel";
import { StaffNav } from "@/components/staff-nav";
import { iconSizeMd, iconStroke, MapPinned } from "@/components/icons";
import styles from "@/components/account.module.css";

export const metadata = { title: "Staff · Live delivery map" };

export default function StaffLogisticsTrackingPage() {
  return (
    <div className={styles.shell}>
      <StaffNav current="/staff/logistics/tracking" />
      <div className={styles.panel}>
        <header className={styles.pageHeader}>
          <h1 className={styles.title}>
            <span className={styles.titleIcon} aria-hidden>
              <MapPinned size={iconSizeMd} strokeWidth={iconStroke} />
            </span>
            Live delivery map
          </h1>
          <p className={styles.pageSubtitle}>
            Track active delivery jobs and driver positions in real time.
          </p>
        </header>
        <div className={styles.pageBody}>
          <StaffDeliveryTrackingPanel />
        </div>
      </div>
    </div>
  );
}
