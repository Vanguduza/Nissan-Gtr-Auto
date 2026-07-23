"use client";

import Image from "next/image";
import Link from "next/link";
import { SearchFourWay } from "@/components/search-four-way";
import styles from "./site-header.module.css";

const categories = [
  { href: "/catalog?cat=brakes", label: "Brakes" },
  { href: "/catalog?cat=filters", label: "Filters" },
  { href: "/catalog?cat=engine", label: "Engine" },
  { href: "/catalog?cat=suspension", label: "Suspension" },
  { href: "/catalog?cat=electrical", label: "Electrical" },
  { href: "/catalog?cat=cooling", label: "Cooling" },
  { href: "/catalog?cat=body", label: "Body" },
  { href: "/catalog?cat=transmission", label: "Drivetrain" },
];

export function SiteHeader() {
  return (
    <header className={styles.chrome}>
      <div className={styles.utility}>
        <div className={styles.utilityInner}>
          <p className={styles.utilityLeft}>
            Counter stock · Harare · Nationwide dispatch
          </p>
          <p className={styles.utilityRight}>
            Prices in <span className={styles.usd}>USD</span> /{" "}
            <span className={styles.zig}>ZiG</span>
            <span className={styles.sep} aria-hidden>
              ·
            </span>
            <Link href="/b2b">Trade account</Link>
          </p>
        </div>
      </div>

      <div className={styles.main}>
        <div className={styles.mainInner}>
          <Link href="/" className={styles.logoLink} aria-label="Nissan GTR Auto home">
            <Image
              src="/brand/logo.png"
              alt="Nissan GTR Auto"
              width={72}
              height={72}
              className={styles.logo}
              priority
            />
            <span className={styles.logoWord}>
              Nissan
              <strong>GTR Auto</strong>
            </span>
          </Link>

          <div className={styles.searchSlot}>
            <SearchFourWay variant="header" />
          </div>

          <nav className={styles.actions} aria-label="Account">
            <Link href="/garage" className={styles.action}>
              <span className={styles.actionLabel}>My Garage</span>
            </Link>
            <Link href="/cart" className={styles.actionCart}>
              <span className={styles.actionLabel}>Cart</span>
            </Link>
            <Link href="/login" className={styles.signIn}>
              Sign in
            </Link>
          </nav>
        </div>
      </div>

      <nav className={styles.categories} aria-label="Parts categories">
        <div className={styles.categoriesInner}>
          <Link href="/catalog" className={styles.catAll}>
            All categories
          </Link>
          {categories.map((c) => (
            <Link key={c.href} href={c.href} className={styles.cat}>
              {c.label}
            </Link>
          ))}
          <Link href="/search" className={styles.catSearch}>
            Advanced search
          </Link>
        </div>
      </nav>
    </header>
  );
}
