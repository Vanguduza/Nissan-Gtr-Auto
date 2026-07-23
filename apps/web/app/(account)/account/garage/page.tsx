import Link from "next/link";
import { AccountNav } from "@/components/account-nav";
import { DEMO_VEHICLE } from "@/lib/shop-demo";
import styles from "@/components/account.module.css";

export const metadata = { title: "My Garage" };

export default function GarageAccountPage() {
  return (
    <div className={styles.shell}>
      <AccountNav current="/account/garage" />
      <div className={styles.panel}>
        <h1 className={styles.title}>My Garage</h1>
        <p className={styles.lede}>
          Saved vehicles drive fitment filters across the shop. Service
          reminders (adopt later) will notify you before due intervals.
        </p>
        <ul className={styles.list}>
          <li>
            <strong>{DEMO_VEHICLE.label}</strong>
            <p className={styles.muted}>Active for sticky “Shopping for”</p>
            <Link href="/vehicle" className={styles.btn}>
              Add / change vehicle
            </Link>
          </li>
          <li>
            <strong>Service reminders</strong>
            <p className={styles.muted}>
              Oil / filters / brakes — SMS when Phase 13 marketing channel is
              live. Stub on for this vehicle.
            </p>
          </li>
        </ul>
      </div>
    </div>
  );
}
