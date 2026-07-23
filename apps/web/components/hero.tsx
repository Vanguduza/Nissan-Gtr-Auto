import Link from "next/link";
import styles from "./hero.module.css";

/** Compact promo band under shop chrome — not a full-bleed art landing. */
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
            Search by part number, VIN, model, or PNC — then check fitment from
            My Garage before you buy.
          </p>
          <div className={styles.cta}>
            <Link href="/search" className={styles.primary}>
              Search parts
            </Link>
            <Link href="/vehicle" className={styles.secondary}>
              Select vehicle
            </Link>
          </div>
        </div>
        <div className={`${styles.vehicleCard} gtr-rise-delay`} aria-labelledby="vehicle-entry">
          <h2 id="vehicle-entry" className={styles.vehicleTitle}>
            Shop by vehicle
          </h2>
          <p className={styles.vehicleLede}>
            Save a Nissan in My Garage to scope catalog and search to fitment.
          </p>
          <Link href="/account/garage" className={styles.vehicleCta}>
            Open My Garage
          </Link>
        </div>
      </div>
    </section>
  );
}
