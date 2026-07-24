import { StaffDeliveryTrackingPanel } from "@/components/staff-delivery-tracking-panel";
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
          Dispatcher view of bridge-ingested GPS points via Supabase Realtime.
          Staff only (admin / warehouse / dispatcher). Subscribe-only — no
          browser geolocation.
        </p>
        <StaffDeliveryTrackingPanel />
      </div>
    </div>
  );
}
