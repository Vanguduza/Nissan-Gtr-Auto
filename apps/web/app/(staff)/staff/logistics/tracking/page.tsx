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
        <p className={styles.lede}>
          Assign drivers, mark dispatched (out-for-delivery SMS via outbox), and
          watch bridge-ingested GPS via Realtime (admin / warehouse /
          dispatcher). Drivers use the delivery Android app — this page is
          subscribe-only (no browser geolocation).
        </p>
        <StaffLogisticsTabs active="tracking" />
        <StaffDeliveryTrackingPanel />
      </div>
    </div>
  );
}
