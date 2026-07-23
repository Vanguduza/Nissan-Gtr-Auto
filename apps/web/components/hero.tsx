import Link from "next/link";
import styles from "./hero.module.css";

export function StorefrontHero() {
  return (
    <section className={styles.hero} aria-label="Nissan GTR Auto">
      <div className={styles.plane} aria-hidden />
      <div className={styles.grain} aria-hidden />
      <div className={styles.content}>
        <p className={`${styles.brand} gtr-rise`}>Nissan GTR Auto</p>
        <div className={`${styles.rule} gtr-rise-delay`} aria-hidden />
        <h1 className={`${styles.headline} gtr-rise-delay`}>
          Parts that fit. Delivered across Zimbabwe.
        </h1>
        <p className={`${styles.lede} gtr-rise-delay-2`}>
          Search by VIN, model, PNC, or part number — then order from counter
          stock with dual-currency clarity.
        </p>
        <div className={`${styles.cta} gtr-rise-delay-2`}>
          <Link href="/search" className={styles.primary}>
            Find parts
          </Link>
          <Link href="/garage" className={styles.secondary}>
            Open My Garage
          </Link>
        </div>
      </div>
    </section>
  );
}
