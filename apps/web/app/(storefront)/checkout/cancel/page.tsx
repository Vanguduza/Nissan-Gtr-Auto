import Link from "next/link";
import styles from "../../page.module.css";

export const metadata = { title: "Payment cancelled" };

/**
 * ContiPay / Paynow cancel / abort landing.
 * Invoice remains open until paid; webhook will not settle a cancelled attempt.
 */
export default async function CheckoutCancelPage({
  searchParams,
}: {
  searchParams: Promise<{ invoice?: string }>;
}) {
  const sp = await searchParams;
  const invoiceId = sp.invoice?.trim() || null;

  return (
    <div className={styles.page}>
      <h1 className={styles.title}>Payment cancelled</h1>
      <p className={styles.lede}>
        Checkout was cancelled before the provider confirmed payment. Your
        invoice is still open — you can try ContiPay or Paynow again from the
        order page.
      </p>
      <div style={{ display: "flex", flexWrap: "wrap", gap: "0.75rem" }}>
        {invoiceId ? (
          <Link
            href={`/account/orders/${encodeURIComponent(invoiceId)}`}
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
