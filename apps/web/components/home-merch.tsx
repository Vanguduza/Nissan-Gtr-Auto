"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { FlashSalePanel } from "@/components/flash-sale-panel";
import { HomeBannerCarousel } from "@/components/home-banner-carousel";
import {
  ArrowRight,
  ArrowUpDown,
  Car,
  CircleDot,
  Cog,
  Droplets,
  Filter,
  iconSizeLg,
  iconSizeMd,
  iconStroke,
  LayoutGrid,
  Zap,
  type LucideIcon,
} from "@/components/icons";
import { ProductRail } from "@/components/product-rail";
import {
  listHomeMerchRails,
  type CatalogListItem,
} from "@/lib/catalog-product";
import { createWebClient } from "@/lib/supabase";
import styles from "@/app/(storefront)/page.module.css";
import chipStyles from "./home-merch.module.css";

const categoryTiles: {
  href: string;
  label: string;
  blurb: string;
  Icon: LucideIcon;
}[] = [
  {
    href: "/shop?cat=brakes",
    label: "Brakes",
    blurb: "Pads, discs, hoses",
    Icon: CircleDot,
  },
  {
    href: "/shop?cat=filters",
    label: "Filters",
    blurb: "Oil, air, cabin, fuel",
    Icon: Filter,
  },
  {
    href: "/shop?cat=engine",
    label: "Engine",
    blurb: "Belts, sensors, gaskets",
    Icon: Cog,
  },
  {
    href: "/shop?cat=suspension",
    label: "Suspension",
    blurb: "Arms, bushes, shocks",
    Icon: ArrowUpDown,
  },
  {
    href: "/shop?cat=electrical",
    label: "Electrical",
    blurb: "Batteries, lighting",
    Icon: Zap,
  },
  {
    href: "/shop?cat=cooling",
    label: "Cooling",
    blurb: "Radiators, pumps",
    Icon: Droplets,
  },
];

type RailStatus =
  | { kind: "loading" }
  | { kind: "auth" }
  | { kind: "error"; message: string }
  | {
      kind: "ready";
      movers: CatalogListItem[];
      newest: CatalogListItem[];
    };

/** KMP home merchandising block under the hero — Supabase-backed rails. */
export function HomeMerch() {
  const [rails, setRails] = useState<RailStatus>({ kind: "loading" });

  useEffect(() => {
    let cancelled = false;

    async function run() {
      const client = createWebClient();
      if (!client) {
        if (!cancelled) {
          setRails({
            kind: "error",
            message: "Supabase is not configured on this environment.",
          });
        }
        return;
      }

      const { data: sessionData } = await client.auth.getSession();
      if (!sessionData.session) {
        if (!cancelled) setRails({ kind: "auth" });
        return;
      }

      const result = await listHomeMerchRails(client, 12);
      if (cancelled) return;
      if (!result.ok) {
        setRails({ kind: "error", message: result.error });
        return;
      }
      setRails({
        kind: "ready",
        movers: result.movers,
        newest: result.newest,
      });
    }

    void run();
    return () => {
      cancelled = true;
    };
  }, []);

  return (
    <>
      <section className={styles.section} aria-label="Promotions and categories">
        <div className={styles.band}>
          <HomeBannerCarousel />

          <div className={chipStyles.catsBlock}>
            <div className={chipStyles.catsHead}>
              <h2 className={styles.sectionTitle}>
                <span className={styles.sectionIcon} aria-hidden>
                  <LayoutGrid size={iconSizeLg} strokeWidth={iconStroke} />
                </span>
                Categories
              </h2>
              <Link href="/shop" className={chipStyles.seeAll}>
                See all
                <ArrowRight size={iconSizeMd} strokeWidth={iconStroke} aria-hidden />
              </Link>
            </div>
            <ul className={styles.catGrid}>
              {categoryTiles.map((c) => (
                <li key={`tile-${c.href}`}>
                  <Link href={c.href} className={styles.catTile}>
                    <span className={styles.catIcon} aria-hidden>
                      <c.Icon size={iconSizeLg} strokeWidth={iconStroke} />
                    </span>
                    <span className={styles.catCopy}>
                      <span className={styles.catLabel}>{c.label}</span>
                      <span className={styles.catBlurb}>{c.blurb}</span>
                    </span>
                    <ArrowRight
                      className={styles.catArrow}
                      size={iconSizeMd}
                      strokeWidth={iconStroke}
                      aria-hidden
                    />
                  </Link>
                </li>
              ))}
            </ul>
          </div>

          <div className={chipStyles.flashWrap}>
            <FlashSalePanel />
          </div>
        </div>
      </section>

      <section className={styles.sectionAlt} aria-label="Featured parts">
        <div className={styles.band}>
          {rails.kind === "loading" ? (
            <p className={styles.muted}>Loading stock rails…</p>
          ) : null}
          {rails.kind === "auth" ? (
            <p className={styles.lede}>
              <Car size={14} strokeWidth={iconStroke} aria-hidden />{" "}
              <Link href="/login?next=/">Sign in</Link> to see top movers and
              newest arrivals from live inventory.
            </p>
          ) : null}
          {rails.kind === "error" ? (
            <p className={styles.muted} role="alert">
              {rails.message}
            </p>
          ) : null}
          {rails.kind === "ready" ? (
            <div className={chipStyles.rails}>
              <ProductRail
                title="Top movers"
                lede="Highest on-hand qty in saleable warehouses — demand proxy until sales analytics feed the storefront."
                seeAllHref="/shop?sort=movers"
                items={rails.movers}
                emptyHint="No stock rows yet for movers — catalog may be empty."
              />
              <ProductRail
                title="Newest arrivals"
                lede="Recently added stock items — newest first."
                seeAllHref="/shop?sort=newest"
                items={rails.newest}
                emptyHint="No recent stock items — reload the catalog SoR if /shop is also empty."
              />
            </div>
          ) : null}
        </div>
      </section>
    </>
  );
}
