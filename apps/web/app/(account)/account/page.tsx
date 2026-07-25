import Link from "next/link";
import {
  AccountNav,
  accountCardIcons,
} from "@/components/account-nav";
import {
  iconSizeMd,
  iconStroke,
  UserRound,
} from "@/components/icons";
import styles from "@/components/account.module.css";

const cards = [
  {
    href: "/account/profile",
    label: "Personal details",
    blurb: "Name & contact",
  },
  { href: "/account/addresses", label: "Addresses", blurb: "Delivery & billing" },
  { href: "/account/garage", label: "My Garage", blurb: "Vehicles & fitment" },
  { href: "/account/orders", label: "Orders", blurb: "Status & tracking" },
  { href: "/account/chat", label: "Live chat", blurb: "Support & parts" },
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
        <h1 className={styles.title}>
          <span className={styles.titleIcon} aria-hidden>
            <UserRound size={iconSizeMd} strokeWidth={iconStroke} />
          </span>
          My Account
        </h1>
        <p className={styles.lede}>
          Personal details, addresses, vehicles, orders, and loyalty — My Garage
          lives here.
        </p>
        <div className={styles.cardGrid}>
          {cards.map((c) => {
            const Icon = accountCardIcons[c.href];
            return (
              <Link key={c.href} href={c.href} className={styles.card}>
                {Icon ? (
                  <span className={styles.cardIcon} aria-hidden>
                    <Icon size={iconSizeMd} strokeWidth={iconStroke} />
                  </span>
                ) : null}
                <span className={styles.cardLabel}>{c.label}</span>
                <span className={styles.cardBlurb}>{c.blurb}</span>
              </Link>
            );
          })}
        </div>
      </div>
    </div>
  );
}
