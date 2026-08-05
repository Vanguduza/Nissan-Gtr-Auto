import Link from "next/link";
import { AccountNav } from "@/components/account-nav";
import { Ticket, iconSizeMd, iconStroke } from "@/components/icons";
import styles from "@/components/account.module.css";

export const metadata = { title: "Coupons" };

/**
 * KMP My Coupons shell — no hardcoded fake coupons.
 * TODO(backend): list customer promo codes when storefront coupon RPC exists.
 */
export default function CouponsPage() {
  return (
    <div className={styles.shell}>
      <AccountNav current="/account/coupons" />
      <div className={styles.panel}>
        <h1 className={styles.title}>
          <span className={styles.titleIcon} aria-hidden>
            <Ticket size={iconSizeMd} strokeWidth={iconStroke} />
          </span>
          Coupons
        </h1>
        <p className={styles.lede}>
          Promo codes and loyalty vouchers will list here when the customer
          coupon feed is wired. Until then this page stays empty — we do not
          show sample discounts that cannot be applied at checkout.
        </p>
        <p className={styles.muted}>
          No coupons available. Loyalty points live under{" "}
          <Link href="/account/loyalty">Loyalty</Link>.
        </p>
      </div>
    </div>
  );
}
