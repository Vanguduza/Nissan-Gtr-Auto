import Link from "next/link";
import {
  ClipboardList,
  iconSizeSm,
  iconStroke,
  LayoutGrid,
  PackageSearch,
  type LucideIcon,
} from "@/components/icons";
import styles from "@/components/account.module.css";

type NavItem = {
  href: string;
  label: string;
  exact?: boolean;
  list?: boolean;
  Icon: LucideIcon;
};

const nav: NavItem[] = [
  { href: "/procurement", label: "Overview", exact: true, Icon: LayoutGrid },
  {
    href: "/procurement/suppliers",
    label: "Suppliers",
    exact: true,
    Icon: PackageSearch,
  },
  {
    href: "/procurement/approvals",
    label: "Approvals",
    exact: true,
    Icon: ClipboardList,
  },
  {
    href: "/procurement/blankets",
    label: "Blankets",
    exact: true,
    Icon: ClipboardList,
  },
  { href: "/procurement/rfqs", label: "RFQs (opt.)", list: true, Icon: ClipboardList },
];

export function ProcurementNav({ current }: { current: string }) {
  return (
    <nav className={styles.nav} aria-label="Procurement">
      <p className={styles.navTitle}>
        <PackageSearch size={iconSizeSm} strokeWidth={iconStroke} aria-hidden />
        Procurement
      </p>
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
                <item.Icon
                  size={iconSizeSm}
                  strokeWidth={iconStroke}
                  aria-hidden
                />
                {item.label}
              </Link>
            </li>
          );
        })}
        <li>
          <Link href="/staff" className={styles.navLink}>
            ← Staff hub
          </Link>
        </li>
      </ul>
    </nav>
  );
}
