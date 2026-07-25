import Link from "next/link";
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
        <p className={styles.lede}>
          Driver SOS alerts for admin / warehouse / dispatcher. Live map:{" "}
          <Link href="/staff/logistics/tracking">tracking</Link>. No browser
          GPS — coordinates come from the delivery app bridge when present.
        </p>
        <StaffPanicInboxPanel />
      </div>
    </div>
  );
}
