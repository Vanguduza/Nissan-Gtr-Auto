"use client";

import Image from "next/image";
import Link from "next/link";
import type { ComponentType } from "react";
import {
  Car,
  CircleDot,
  Cog,
  Droplets,
  Filter,
  iconSizeMd,
  iconSizeSm,
  iconStroke,
  LayoutGrid,
  Package,
  Search,
  ShoppingCart,
  ArrowUpDown,
  UserRound,
  Wrench,
  Zap,
  type LucideIcon,
} from "@/components/icons";
import styles from "./site-header.module.css";

const categories: {
  href: string;
  label: string;
  Icon: LucideIcon;
}[] = [
  { href: "/catalog?cat=brakes", label: "Brakes", Icon: CircleDot },
  { href: "/catalog?cat=filters", label: "Filters", Icon: Filter },
  { href: "/catalog?cat=engine", label: "Engine", Icon: Cog },
  { href: "/catalog?cat=suspension", label: "Suspension", Icon: ArrowUpDown },
  { href: "/catalog?cat=electrical", label: "Electrical", Icon: Zap },
  { href: "/catalog?cat=cooling", label: "Cooling", Icon: Droplets },
  { href: "/catalog?cat=body", label: "Body", Icon: Car },
  { href: "/catalog?cat=transmission", label: "Drivetrain", Icon: Wrench },
  { href: "/kits", label: "Kits", Icon: Package },
];

function ActionIcon({
  Icon,
  label,
}: {
  Icon: ComponentType<{
    size?: number;
    strokeWidth?: number;
    "aria-hidden"?: boolean;
  }>;
  label: string;
}) {
  return (
    <>
      <Icon size={iconSizeMd} strokeWidth={iconStroke} aria-hidden />
      <span className={styles.actionLabel}>{label}</span>
    </>
  );
}

export function SiteHeader() {
  return (
    <header className={styles.chrome}>
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

          <nav className={styles.actions} aria-label="Account">
            <Link href="/account" className={styles.action}>
              <ActionIcon Icon={UserRound} label="Account" />
            </Link>
            <Link href="/cart" className={styles.action}>
              <ActionIcon Icon={ShoppingCart} label="Cart" />
            </Link>
          </nav>
        </div>
      </div>

      <nav className={styles.categories} aria-label="Parts categories">
        <div className={styles.categoriesInner}>
          <Link href="/catalog" className={styles.catAll}>
            <LayoutGrid size={iconSizeSm} strokeWidth={iconStroke} aria-hidden />
            All categories
          </Link>
          {categories.map((c) => (
            <Link key={c.href} href={c.href} className={styles.cat}>
              <c.Icon size={iconSizeSm} strokeWidth={iconStroke} aria-hidden />
              {c.label}
            </Link>
          ))}
          <Link href="/search" className={styles.catSearch}>
            <Search size={iconSizeSm} strokeWidth={iconStroke} aria-hidden />
            Advanced search
          </Link>
        </div>
      </nav>
    </header>
  );
}
