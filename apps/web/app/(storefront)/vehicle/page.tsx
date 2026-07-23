import Link from "next/link";
import { VehicleSelector } from "@/components/vehicle-selector";
import styles from "../page.module.css";

export const metadata = { title: "Select vehicle" };

export default function VehiclePage() {
  return (
    <div className={styles.bandPad}>
      <div className={styles.sectionHead}>
        <h1 className={styles.sectionTitle}>Select your vehicle</h1>
        <p className={styles.sectionLede}>
          Make / model / engine or VIN. Saved vehicles live in{" "}
          <Link href="/account/garage">My Account → My Garage</Link>.
        </p>
      </div>
      <VehicleSelector />
    </div>
  );
}
