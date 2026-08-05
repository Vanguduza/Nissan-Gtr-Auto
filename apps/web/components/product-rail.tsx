"use client";

import Link from "next/link";
import { PriceDual } from "@/components/price-dual";
import { StockBadge } from "@/components/stock-badge";
import { ArrowRight, iconSizeSm, iconStroke } from "@/components/icons";
import type { CatalogListItem } from "@/lib/catalog-product";
import styles from "./product-rail.module.css";

export function ProductRail({
  title,
  lede,
  seeAllHref,
  items,
  emptyHint,
}: {
  title: string;
  lede?: string;
  seeAllHref: string;
  items: CatalogListItem[];
  emptyHint?: string;
}) {
  return (
    <section className={styles.rail} aria-label={title}>
      <div className={styles.head}>
        <div>
          <h2 className={styles.title}>{title}</h2>
          {lede ? <p className={styles.lede}>{lede}</p> : null}
        </div>
        <Link href={seeAllHref} className={styles.seeAll}>
          See all
          <ArrowRight size={iconSizeSm} strokeWidth={iconStroke} aria-hidden />
        </Link>
      </div>
      {items.length === 0 ? (
        <p className={styles.empty}>{emptyHint ?? "No parts to show yet."}</p>
      ) : (
        <ul className={styles.track}>
          {items.map((item) => (
            <li key={item.oem}>
              <Link
                href={`/parts/${encodeURIComponent(item.oem)}`}
                className={styles.card}
              >
                <div className={styles.thumb} aria-hidden>
                  <span>{item.oem.slice(0, 6)}</span>
                </div>
                <p className={styles.oem}>
                  <code>{item.oem}</code>
                </p>
                <p className={styles.name}>{item.name}</p>
                <StockBadge state={item.stock} />
                <div className={styles.price}>
                  {item.usd != null ? (
                    <PriceDual usd={item.usd} zig={item.zig} />
                  ) : (
                    <span className={styles.onRequest}>On request</span>
                  )}
                </div>
              </Link>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
