import Link from "next/link";
import styles from "../../page.module.css";

export const metadata = { title: "Payment cancelled" };

/**
 * ContiPay / Paynow cancel / abort landing.
 * The reserved commerce order remains unpaid until it expires or a later attempt settles.
 */
export default async function CheckoutCancelPage({
  searchParams,
}: {
  searchParams: Promise<{ order?: string; invoice?: string }>;
}) {
  const sp = await searchParams;
  const orderRef = sp.order?.trim() || sp.invoice?.trim() || null;

  return (
    <div className={styles.page}>
      <h1 className={styles.title}>Payment cancelled</h1>
      <p className={styles.lede}>
        Checkout was cancelled before the provider confirmed payment. The
        order remains unpaid while its stock reservation is valid — you can
        retry payment from the order page.
      </p>
      <div style={{ display: "flex", flexWrap: "wrap", gap: "0.75rem" }}>
        {orderRef ? (
          <Link
            href={`/account/orders/${encodeURIComponent(orderRef)}`}
            className={styles.button}
          >
            Return to order
          </Link>
        ) : (
          <Link href="/account/orders" className={styles.button}>
            My orders
          </Link>
        )}
        <Link href="/cart" className={styles.button} style={{ background: "var(--gtr-steel)" }}>
          Cart
        </Link>
      </div>
    </div>
  );
}
