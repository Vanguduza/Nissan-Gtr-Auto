import Link from "next/link";
import { DEMO_VEHICLE } from "@/lib/shop-demo";
import styles from "./sticky-garage-bar.module.css";

export function StickyGarageBar() {
  return (
    <div className={styles.bar} role="status">
      <div className={styles.inner}>
        <span className={styles.label}>Shopping for</span>
        <strong className={styles.vehicle}>{DEMO_VEHICLE.label}</strong>
        <Link href="/account/garage" className={styles.change}>
          Change in My Garage
        </Link>
        <Link href="/vehicle" className={styles.select}>
          Select vehicle
        </Link>
      </div>
    </div>
  );
}
