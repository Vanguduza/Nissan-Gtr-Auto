import Link from "next/link";
import { AccountNav } from "@/components/account-nav";
import { DEMO_PRODUCTS } from "@/lib/shop-demo";
import { PriceDual } from "@/components/price-dual";
import styles from "@/components/account.module.css";

export const metadata = { title: "Compare" };

export default function ComparePage() {
  const a = DEMO_PRODUCTS[0];
  const b = DEMO_PRODUCTS[1];
  return (
    <div className={styles.shell}>
      <AccountNav current="/account/compare" />
      <div className={styles.panel}>
        <h1 className={styles.title}>Compare</h1>
        <p className={styles.lede}>Side-by-side up to 3 SKUs (demo pair).</p>
        <div className={styles.cardGrid}>
          {[a, b].map((p) => (
            <div key={p.oem} className={styles.card}>
              <span className={styles.cardLabel}>{p.oem}</span>
              <span className={styles.cardBlurb}>{p.name}</span>
              <PriceDual usd={p.usd} zig={p.zig} />
              <Link href={`/parts/${encodeURIComponent(p.oem)}`} className={styles.btn}>
                Open
              </Link>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}
