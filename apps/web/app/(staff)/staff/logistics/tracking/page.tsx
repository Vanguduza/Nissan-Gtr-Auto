import { StaffDeliveryTrackingPanel } from "@/components/staff-delivery-tracking-panel";
import { StaffLogisticsTabs } from "@/components/staff-logistics-tabs";
import { StaffNav } from "@/components/staff-nav";
import styles from "@/components/account.module.css";

export const metadata = { title: "Staff · Live delivery map" };

export default function StaffLogisticsTrackingPage() {
  return (
    <div className={styles.shell}>
      <StaffNav current="/staff/logistics/tracking" />
      <div className={styles.panel}>
        <h1 className={styles.title}>Live delivery map</h1>
        <StaffLogisticsTabs active="tracking" />
        <StaffDeliveryTrackingPanel />
      </div>
    </div>
  );
}
