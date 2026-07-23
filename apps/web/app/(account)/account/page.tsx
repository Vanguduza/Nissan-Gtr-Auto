import Link from "next/link";
import { AccountNav } from "@/components/account-nav";
import styles from "./account.module.css";

const cards = [
  { href: "/account/garage", label: "My Garage", blurb: "Vehicles & fitment" },
  { href: "/account/orders", label: "Orders", blurb: "Status & tracking" },
  { href: "/account/wishlist", label: "Wishlist", blurb: "Saved parts" },
  { href: "/account/returns", label: "Returns", blurb: "Quarantine path" },
  { href: "/account/compare", label: "Compare", blurb: "Side-by-side SKUs" },
  { href: "/account/loyalty", label: "Loyalty", blurb: "Points & rewards" },
  { href: "/account/reviews", label: "Reviews", blurb: "Your ratings" },
  { href: "/account/apps", label: "Apps", blurb: "iOS & Android" },
];

export const metadata = { title: "My Account" };

export default function AccountPage() {
  return (
    <div className={styles.shell}>
      <AccountNav current="/account" />
      <div className={styles.panel}>
        <h1 className={styles.title}>My Account</h1>
        <p className={styles.lede}>
          Manage vehicles, orders, wishlist, returns, and loyalty — My Garage
          lives here.
        </p>
        <div className={styles.cardGrid}>
          {cards.map((c) => (
            <Link key={c.href} href={c.href} className={styles.card}>
              <span className={styles.cardLabel}>{c.label}</span>
              <span className={styles.cardBlurb}>{c.blurb}</span>
            </Link>
          ))}
        </div>
      </div>
    </div>
  );
}
