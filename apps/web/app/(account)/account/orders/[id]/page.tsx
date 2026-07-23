import Link from "next/link";
import { AccountNav } from "@/components/account-nav";
import styles from "@/components/account.module.css";

export const metadata = { title: "Order status" };

export default function OrderDetailPage() {
  return (
    <div className={styles.shell}>
      <AccountNav current="/account/orders" />
      <div className={styles.panel}>
        <h1 className={styles.title}>SINV-DEMO-0001</h1>
        <p className={styles.lede}>
          Fulfillment: <strong>Click &amp; collect</strong> — Main Store,
          Harare. Map trail appears when dispatch is live.
        </p>
        <ol className={styles.list}>
          <li>Order received</li>
          <li>Picked</li>
          <li>
            <strong>Ready for counter pickup</strong>
          </li>
          <li className={styles.muted}>Collected</li>
        </ol>
        <Link href="/account/orders" className={styles.btn}>
          Back to orders
        </Link>
      </div>
    </div>
  );
}
