import Link from "next/link";
import styles from "./account.module.css";

const nav = [
  { href: "/account", label: "Overview", exact: true },
  { href: "/account/garage", label: "My Garage" },
  { href: "/account/orders", label: "Orders & tracking" },
  { href: "/account/wishlist", label: "Wishlist" },
  { href: "/account/returns", label: "Returns" },
  { href: "/account/compare", label: "Compare" },
  { href: "/account/loyalty", label: "Loyalty" },
  { href: "/account/reviews", label: "My reviews" },
  { href: "/account/apps", label: "Mobile apps" },
];

export function AccountNav({ current }: { current: string }) {
  return (
    <nav className={styles.nav} aria-label="My Account">
      <p className={styles.navTitle}>My Account</p>
      <ul className={styles.navList}>
        {nav.map((item) => {
          const active = item.exact
            ? current === item.href
            : current === item.href || current.startsWith(item.href + "/");
          return (
            <li key={item.href}>
              <Link
                href={item.href}
                className={active ? styles.navLinkActive : styles.navLink}
              >
                {item.label}
              </Link>
            </li>
          );
        })}
      </ul>
    </nav>
  );
}
