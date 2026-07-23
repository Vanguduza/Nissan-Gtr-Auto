import Link from "next/link";
import styles from "./site-header.module.css";

const links = [
  { href: "/catalog", label: "Catalog" },
  { href: "/search", label: "Search" },
  { href: "/garage", label: "My Garage" },
  { href: "/b2b", label: "B2B" },
  { href: "/cart", label: "Cart" },
];

export function SiteHeader({ overlay = false }: { overlay?: boolean }) {
  return (
    <header className={overlay ? styles.headerOverlay : styles.headerSolid}>
      <Link href="/" className={styles.brand}>
        Nissan GTR Auto
      </Link>
      <nav className={styles.nav} aria-label="Primary">
        {links.map((l) => (
          <Link key={l.href} href={l.href} className={styles.link}>
            {l.label}
          </Link>
        ))}
        <Link href="/login" className={styles.login}>
          Sign in
        </Link>
      </nav>
    </header>
  );
}
