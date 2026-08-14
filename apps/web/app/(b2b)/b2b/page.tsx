import Link from "next/link";
import { B2bPricePanel } from "@/components/b2b-price-panel";
import styles from "../../(storefront)/page.module.css";

export const metadata = { title: "B2B" };

export default function B2bPage() {
  return (
    <div className={styles.page}>
      <h1 className={styles.title}>B2B supply</h1>
      <p className={styles.lede}>
        Trade accounts resolve the assigned <strong>B2B</strong> / Fleet price
        list and credit terms from Phase 5. Sign in with a linked customer
        profile to see net pricing, credit limit / hold / open balance, and
        catalog PDP trade prices.
      </p>
      <B2bPricePanel />
      <p className={styles.lede} style={{ marginTop: "1.25rem" }}>
        Procurement: <Link href="/procurement">preferred-supplier hub</Link>
        {" · "}
        <Link href="/procurement/rfqs">optional RFQs (spot-buy)</Link>
        {" · "}
        <Link href="/supplier/rfqs">supplier quotations</Link>
      </p>
      <p className={styles.lede} style={{ marginTop: "0.75rem" }}>
        Staff ops: <Link href="/staff">hub</Link>
        {" · "}
        <Link href="/staff/hr">HR attendance</Link>
        {" · "}
        <Link href="/staff/logistics">logistics DN</Link>
      </p>
    </div>
  );
}
