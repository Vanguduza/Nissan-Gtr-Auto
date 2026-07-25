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
          Dispatcher view of bridge-ingested GPS via Supabase Realtime (admin /
          warehouse / dispatcher). Drivers use the Android management app for
          GPS — this page is subscribe-only (no browser geolocation, no{" "}
          <code>ingest_delivery_location</code>).
        </p>
        <StaffDeliveryTrackingPanel />
      </div>
    </div>
  );
}
