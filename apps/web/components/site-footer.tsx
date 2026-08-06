import Link from "next/link";
import styles from "./site-footer.module.css";

export function SiteFooter() {
  return (
    <footer className={styles.footer}>
      <div className={styles.trust}>
        <div className={styles.trustInner}>
          <p>
            <strong>Fitment-first</strong>
            <span>VIN / model scoped parts</span>
          </p>
          <p>
            <strong>Dual currency</strong>
            <span>Explicit USD · ZiG at checkout</span>
          </p>
          <p>
            <strong>Trade supply</strong>
            <span>B2B price lists & credit hold</span>
          </p>
          <p>
            <strong>Genuine quality spares</strong>
          </p>
        </div>
      </div>
      <div className={styles.bottom}>
        <div className={styles.bottomInner}>
          <p className={styles.mark}>Nissan GTR Auto · nissangtrauto.co.zw</p>
          <nav className={styles.links} aria-label="Footer">
            <Link href="/catalog">Catalog</Link>
            <Link href="/account">My Account</Link>
            <Link href="/account/garage">My Garage</Link>
            <Link href="/kits">Kits</Link>
            <Link href="/b2b">B2B</Link>
            <Link href="/contact">Contact us</Link>
          </nav>
        </div>
      </div>
    </footer>
  );
}
