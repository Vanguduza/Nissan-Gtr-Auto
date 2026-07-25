"use client";

import Image from "next/image";
import Link from "next/link";
import { useEffect, useState, type ComponentType } from "react";
import { ChatNavLink } from "@/components/chat-nav-link";
import {
  Building2,
  Car,
  CircleDot,
  Cog,
  Droplets,
  Filter,
  iconSizeMd,
  iconSizeSm,
  iconStroke,
  LayoutGrid,
  LogIn,
  MessageCircle,
  Package,
  Search,
  ShoppingCart,
  ArrowUpDown,
  UserRound,
  Wrench,
  Zap,
  type LucideIcon,
} from "@/components/icons";
import { SearchFourWay } from "@/components/search-four-way";
import { createWebClient, hasSupabaseEnv } from "@/lib/supabase";
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
  Icon: ComponentType<{ size?: number; strokeWidth?: number; "aria-hidden"?: boolean }>;
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
  const [showStaff, setShowStaff] = useState(false);

  useEffect(() => {
    if (!hasSupabaseEnv()) return;
    const client = createWebClient();
    if (!client) return;

    let cancelled = false;
    void (async () => {
      const { data } = await client.auth.getSession();
      const userId = data.session?.user.id;
      if (!userId) {
        if (!cancelled) setShowStaff(false);
        return;
      }
      const profile = await client
        .from("profiles")
        .select("is_staff")
        .eq("id", userId)
        .maybeSingle();
      if (!cancelled) {
        setShowStaff(Boolean(profile.data?.is_staff));
      }
    })();

    const { data: sub } = client.auth.onAuthStateChange(() => {
      void (async () => {
        const { data } = await client.auth.getSession();
        const userId = data.session?.user.id;
        if (!userId) {
          setShowStaff(false);
          return;
        }
        const profile = await client
          .from("profiles")
          .select("is_staff")
          .eq("id", userId)
          .maybeSingle();
        setShowStaff(Boolean(profile.data?.is_staff));
      })();
    });

    return () => {
      cancelled = true;
      sub.subscription.unsubscribe();
    };
  }, []);

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
            <Link href="/b2b" className={styles.utilityLink}>
              <Building2 size={iconSizeSm} strokeWidth={iconStroke} aria-hidden />
              Trade account
            </Link>
            {showStaff ? (
              <>
                <span className={styles.sep} aria-hidden>
                  ·
                </span>
                <Link href="/staff" className={styles.staffLink}>
                  Staff
                </Link>
              </>
            ) : null}
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
            <ChatNavLink
              className={styles.action}
              label="Chat"
              icon={
                <MessageCircle
                  size={iconSizeMd}
                  strokeWidth={iconStroke}
                  aria-hidden
                />
              }
            />
            <Link href="/account" className={styles.action}>
              <ActionIcon Icon={UserRound} label="Account" />
            </Link>
            <Link href="/cart" className={styles.actionCart}>
              <ActionIcon Icon={ShoppingCart} label="Cart" />
            </Link>
            <Link href="/login" className={styles.signIn}>
              <LogIn size={iconSizeMd} strokeWidth={iconStroke} aria-hidden />
              <span className={styles.actionLabel}>Sign in</span>
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
