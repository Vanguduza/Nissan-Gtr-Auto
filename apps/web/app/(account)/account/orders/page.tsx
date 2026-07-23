import Link from "next/link";
import { AccountNav } from "@/components/account-nav";
import styles from "@/components/account.module.css";

export const metadata = { title: "Orders" };

export default function OrdersPage() {
  return (
    <div className={styles.shell}>
      <AccountNav current="/account/orders" />
      <div className={styles.panel}>
        <h1 className={styles.title}>Orders & tracking</h1>
        <p className={styles.lede}>
          Click &amp; collect (Harare counter) or nationwide dispatch. Live
          GPS tracking binds in Phase 10.
        </p>
        <ul className={styles.list}>
          <li>
            <strong>SINV-DEMO-0001</strong> · Click &amp; collect · Ready for
            pickup
            <br />
            <Link href="/account/orders/demo" className={styles.btn}>
              View status
            </Link>
          </li>
          <li className={styles.muted}>No further orders yet.</li>
        </ul>
      </div>
    </div>
  );
}
