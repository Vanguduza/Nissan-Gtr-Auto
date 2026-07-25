import Link from "next/link";
import { KitsList } from "@/components/kits-list";
import styles from "../page.module.css";

export const metadata = { title: "Kits" };

export default function KitsPage() {
  return (
    <div className={styles.bandPad}>
      <div className={styles.sectionHead}>
        <h1 className={styles.sectionTitle}>Job / kit packs</h1>
        <p className={styles.sectionLede}>
          Brake job packs and service kits — Phase 16 BOM sell from{" "}
          <code>item_kits</code>.{" "}
          <Link href="/catalog">Browse catalog</Link>
        </p>
      </div>
      <KitsList />
    </div>
  );
}
