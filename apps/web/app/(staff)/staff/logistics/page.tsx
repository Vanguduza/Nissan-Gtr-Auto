import Link from "next/link";
import { StaffLogisticsPanel } from "@/components/staff-logistics-panel";
import { StaffNav } from "@/components/staff-nav";
import styles from "@/components/account.module.css";

export const metadata = { title: "Staff · Logistics" };

export default function StaffLogisticsPage() {
  return (
    <div className={styles.shell}>
      <StaffNav current="/staff/logistics" />
      <div className={styles.panel}>
        <h1 className={styles.title}>Logistics</h1>
        <p className={styles.lede}>
          Dispatch pick list → confirm lines → delivery note → optional
          delivery job. Warehouse/dispatcher/sales/admin. Assign, ETA, dispatch
          notify, and live GPS (subscribe-only — no browser GPS):{" "}
          <Link href="/staff/logistics/tracking">Live tracking</Link>
          {" · "}
          <Link href="/staff/logistics/panic">Panic inbox</Link>.
        </p>
        <StaffLogisticsPanel />
      </div>
    </div>
  );
}
