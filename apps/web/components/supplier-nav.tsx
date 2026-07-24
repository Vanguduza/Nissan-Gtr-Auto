import Link from "next/link";
import styles from "@/components/account.module.css";

const nav = [
  { href: "/supplier", label: "Overview", exact: true },
  { href: "/supplier/rfqs", label: "Invited RFQs" },
];

export function SupplierNav({ current }: { current: string }) {
  return (
    <nav className={styles.nav} aria-label="Supplier portal">
      <p className={styles.navTitle}>Supplier</p>
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
