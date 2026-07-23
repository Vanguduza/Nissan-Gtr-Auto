import Link from "next/link";
import styles from "./(storefront)/page.module.css";

export const metadata = { title: "Kits" };

export default function KitsPage() {
  return (
    <div className={styles.bandPad}>
      <div className={styles.sectionHead}>
        <h1 className={styles.sectionTitle}>Job / kit packs</h1>
        <p className={styles.sectionLede}>
          Brake job packs and service kits — Phase 16 BOM sell. Stub list below.
        </p>
      </div>
      <ul className={styles.simpleList}>
        <li>
          <Link href="/search?mode=part&q=brake">Front brake service pack</Link>{" "}
          — pads + discs (demo)
        </li>
        <li>
          <Link href="/search?mode=part&q=filter">Service filter kit</Link> — oil
          + air + cabin (demo)
        </li>
      </ul>
    </div>
  );
}
