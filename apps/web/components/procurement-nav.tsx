import Link from "next/link";
import styles from "@/components/account.module.css";

const nav = [
  { href: "/procurement", label: "Overview", exact: true },
  { href: "/procurement/rfqs", label: "RFQs" },
  { href: "/procurement/rfqs/new", label: "New RFQ" },
];

export function ProcurementNav({ current }: { current: string }) {
  return (
    <nav className={styles.nav} aria-label="Procurement">
      <p className={styles.navTitle}>Procurement</p>
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
