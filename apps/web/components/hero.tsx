import Link from "next/link";
import { VehicleSelector } from "@/components/vehicle-selector";
import styles from "./hero.module.css";

/** Compact promo band under shop chrome — AutoDoc-style vehicle entry on the right. */
export function StorefrontHero() {
  return (
    <section className={styles.promo} aria-label="Nissan GTR Auto">
      <div className={styles.inner}>
        <div className={`${styles.copy} gtr-rise`}>
          <p className={styles.kicker}>Nissan spare parts · Zimbabwe</p>
          <h1 className={styles.headline}>
            Find the right part. Order from counter stock.
          </h1>
          <p className={styles.lede}>
            Select maker → model → generation → engine, or search by OEM / VIN.
            Fitment stays scoped from My Garage.
          </p>
          <div className={styles.cta}>
            <Link href="/search" className={styles.primary}>
              Search parts
            </Link>
            <Link href="/account/garage" className={styles.secondary}>
              My Garage
            </Link>
          </div>
        </div>
        <div className={`${styles.vehicleCard} gtr-rise-delay`} aria-labelledby="vehicle-entry">
          <h2 id="vehicle-entry" className={styles.vehicleTitle}>
            Find parts for your vehicle
          </h2>
          <VehicleSelector />
        </div>
      </div>
    </section>
  );
}
