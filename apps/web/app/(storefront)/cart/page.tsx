import Link from "next/link";
import styles from "../page.module.css";

export const metadata = { title: "Cart" };

export default function CartPage() {
  return (
    <div className={styles.page}>
      <h1 className={styles.title}>Cart</h1>
      <p className={styles.lede}>
        Checkout calls Phase 5 POS APIs (
        <code>create_pos_cart</code> / <code>checkout_pos_cart</code>) with
        core-charge split and dual-currency totals. ContiPay lands in Phase 13.
      </p>
      <p className={styles.muted}>
        Cart is empty in this scaffold. Sign in and add parts from search when
        the Supabase session is wired.
      </p>
      <Link href="/search" className={styles.button}>
        Continue shopping
      </Link>
    </div>
  );
}
