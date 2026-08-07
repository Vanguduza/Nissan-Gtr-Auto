import Link from "next/link";
import styles from "../../page.module.css";

export const metadata = { title: "Payment return" };

/**
 * ContiPay / Paynow browser return landing.
 * Settlement is confirmed by provider webhook — this page only acknowledges redirect.
 * Provider HMAC / real checkout_url require CONTIPAY_* / PAYNOW_* secrets on the edge
 * (never in NEXT_PUBLIC_*). See docs/storefront-psp-return-urls.md.
 */
export default async function CheckoutReturnPage({
  searchParams,
}: {
  searchParams: Promise<{ invoice?: string }>;
}) {
  const sp = await searchParams;
  const invoiceId = sp.invoice?.trim() || null;

  return (
    <div className={styles.page}>
      <h1 className={styles.title}>Payment received</h1>
      <p className={styles.lede}>
        Thanks — if you completed checkout with ContiPay or Paynow, settlement
        is still confirming via webhook. Your order status will update once the
        payment posts.
      </p>
      <p className={styles.muted}>
        Do not refresh the payment provider page. You can safely leave this
        screen and check your order.
      </p>
      <div style={{ display: "flex", flexWrap: "wrap", gap: "0.75rem" }}>
        {invoiceId ? (
          <Link
            href={`/account/orders/${encodeURIComponent(invoiceId)}`}
            className={styles.button}
          >
            View order
          </Link>
        ) : (
          <Link href="/account/orders" className={styles.button}>
            My orders
          </Link>
        )}
        <Link
          href="/shop"
          className={styles.button}
          style={{ background: "var(--gtr-steel)" }}
        >
          Continue shopping
        </Link>
      </div>
    </div>
  );
}
