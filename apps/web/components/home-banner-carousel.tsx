"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import { ArrowRight, iconSizeSm, iconStroke } from "@/components/icons";
import styles from "./home-banner-carousel.module.css";

export type HomeBanner = {
  id: string;
  kicker: string;
  title: string;
  lede: string;
  href: string;
  cta: string;
  tone: "steel" | "red" | "mist";
};

const DEFAULT_BANNERS: HomeBanner[] = [
  {
    id: "garage",
    kicker: "Fitment first",
    title: "Parts matched to your garage vehicle",
    lede: "Set maker → model → engine once — search and catalog stay scoped.",
    href: "/account/garage",
    cta: "Open garage",
    tone: "steel",
  },
  {
    id: "search",
    kicker: "Four-way lookup",
    title: "OEM, VIN, model, or PNC",
    lede: "Counter-grade catalog search — same index as the mobile apps.",
    href: "/search",
    cta: "Search parts",
    tone: "red",
  },
  {
    id: "kits",
    kicker: "Service kits",
    title: "Bundled filters, belts & wear items",
    lede: "Job-ready kits from inventory — pick up or dispatch nationwide.",
    href: "/kits",
    cta: "Browse kits",
    tone: "mist",
  },
];

/** KMP-style horizontal pager — GTR promo copy only (no fake deal SKUs). */
export function HomeBannerCarousel({
  banners = DEFAULT_BANNERS,
  intervalMs = 6500,
}: {
  banners?: HomeBanner[];
  intervalMs?: number;
}) {
  const [index, setIndex] = useState(0);
  const count = banners.length;

  const go = useCallback(
    (next: number) => {
      if (count === 0) return;
      setIndex(((next % count) + count) % count);
    },
    [count],
  );

  useEffect(() => {
    if (count < 2) return;
    const reduced =
      typeof window !== "undefined" &&
      window.matchMedia("(prefers-reduced-motion: reduce)").matches;
    if (reduced) return;
    const id = window.setInterval(() => go(index + 1), intervalMs);
    return () => window.clearInterval(id);
  }, [count, go, index, intervalMs]);

  if (count === 0) return null;
  const banner = banners[index]!;

  return (
    <section className={styles.wrap} aria-roledescription="carousel" aria-label="Promotions">
      <div className={`${styles.slide} ${styles[banner.tone]}`} aria-live="polite">
        <p className={styles.kicker}>{banner.kicker}</p>
        <h2 className={styles.title}>{banner.title}</h2>
        <p className={styles.lede}>{banner.lede}</p>
        <Link href={banner.href} className={styles.cta}>
          {banner.cta}
          <ArrowRight size={iconSizeSm} strokeWidth={iconStroke} aria-hidden />
        </Link>
      </div>
      {count > 1 ? (
        <div className={styles.dots} role="tablist" aria-label="Banner slides">
          {banners.map((b, i) => (
            <button
              key={b.id}
              type="button"
              role="tab"
              aria-selected={i === index}
              aria-label={`Show banner ${i + 1}: ${b.title}`}
              className={i === index ? styles.dotActive : styles.dot}
              onClick={() => setIndex(i)}
            />
          ))}
        </div>
      ) : null}
    </section>
  );
}
