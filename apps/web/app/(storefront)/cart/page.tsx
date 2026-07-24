import Link from "next/link";
import { PriceDual } from "@/components/price-dual";
import { DEMO_PRODUCTS } from "@/lib/shop-demo";
import styles from "../page.module.css";

export const metadata = { title: "Cart" };

export default function CartPage() {
  const line = DEMO_PRODUCTS[0];

  return (
    <div className={styles.page}>
      <h1 className={styles.title}>Cart</h1>
      <p className={styles.lede}>
        Checkout calls Phase 5 POS APIs with core-charge split and dual-currency
        totals. Digital rails (ContiPay and Paynow) land in Phase 13.
      </p>

      <div className={styles.tableWrap}>
        <table className={styles.table}>
          <thead>
            <tr>
              <th>Part</th>
              <th>Qty</th>
              <th>Price</th>
            </tr>
          </thead>
          <tbody>
            <tr>
              <td>
                <code className={styles.sku}>{line.oem}</code>
                <br />
                {line.name}
              </td>
              <td>1</td>
              <td>
                <PriceDual usd={line.usd} zig={line.zig} />
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <section className={styles.fulfill} aria-labelledby="fulfill-heading">
        <h2 id="fulfill-heading" className={styles.fulfillTitle}>
          How do you want it?
        </h2>
        <div className={styles.fulfillOptions}>
          <label className={styles.fulfillCard}>
            <input type="radio" name="fulfill" defaultChecked />
            <span>
              <strong>Click &amp; collect</strong>
              <span className={styles.muted}>
                Pick up at Harare counter when ready
              </span>
            </span>
          </label>
          <label className={styles.fulfillCard}>
            <input type="radio" name="fulfill" />
            <span>
              <strong>Nationwide dispatch</strong>
              <span className={styles.muted}>
                Courier to your address (Phase 10)
              </span>
            </span>
          </label>
        </div>
      </section>

      <Link href="/search" className={styles.button}>
        Continue shopping
      </Link>
    </div>
  );
}
