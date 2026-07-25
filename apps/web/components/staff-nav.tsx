import Link from "next/link";
import styles from "@/components/account.module.css";

type NavItem = {
  href: string;
  label: string;
  exact?: boolean;
};

const nav: NavItem[] = [
  { href: "/staff", label: "Hub", exact: true },
  { href: "/staff/pos", label: "POS" },
  { href: "/staff/warehouse", label: "Warehouse" },
  { href: "/staff/hr", label: "HR" },
  { href: "/staff/logistics", label: "Logistics", exact: true },
  { href: "/staff/logistics/tracking", label: "Live map" },
];

export function StaffNav({ current }: { current: string }) {
  return (
    <nav className={styles.nav} aria-label="Staff">
      <p className={styles.navTitle}>Staff</p>
      <ul className={styles.navList}>
        {nav.map((item) => {
          const active = item.exact
            ? current === item.href
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
