import Link from "next/link";
import styles from "@/components/account.module.css";

type NavItem = {
  href: string;
  label: string;
  exact?: boolean;
  list?: boolean;
};

const nav: NavItem[] = [
  { href: "/procurement", label: "Overview", exact: true },
  { href: "/procurement/rfqs", label: "RFQs", list: true },
  { href: "/procurement/rfqs/new", label: "New RFQ", exact: true },
];

export function ProcurementNav({ current }: { current: string }) {
  return (
    <nav className={styles.nav} aria-label="Procurement">
      <p className={styles.navTitle}>Procurement</p>
      <ul className={styles.navList}>
        {nav.map((item) => {
          const active = item.exact
            ? current === item.href
            : item.list
              ? current === item.href ||
                (current.startsWith(`${item.href}/`) &&
                  !current.startsWith("/procurement/rfqs/new"))
              : current === item.href || current.startsWith(`${item.href}/`);
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
