"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import { PriceDual } from "@/components/price-dual";
import { listHomeCarousel, partHref, type HomeCarouselItem } from "@/lib/catalog-product";
import { createWebClient } from "@/lib/supabase";
import styles from "./home-showcase-carousel.module.css";

const INTERVAL_MS = 6000;

type Status =
  | { kind: "loading" }
  | { kind: "empty"; message: string }
  | { kind: "ready"; items: HomeCarouselItem[] };

type Slot = "active" | "prev" | "next" | "hidden";

function slotFor(index: number, active: number, count: number): Slot {
  if (index === active) return "active";
  if (count > 1 && index === (active - 1 + count) % count) return "prev";
  if (count > 2 && index === (active + 1) % count) return "next";
  return "hidden";
}

/**
 * Home hero: radio-driven card carousel of items picked in CRM → Product pages.
 * Centre card is the selected item; side cards step to it; the player bar shows
 * the name, caption and price with an auto-advance progress line.
 */
export function HomeShowcaseCarousel() {
  const [status, setStatus] = useState<Status>({ kind: "loading" });
  const [active, setActive] = useState(0);
  const [paused, setPaused] = useState(false);
  const [cycle, setCycle] = useState(0);

  useEffect(() => {
    let cancelled = false;
    async function run() {
      const client = createWebClient();
      if (!client) {
        setStatus({ kind: "empty", message: "The shop is being set up. Please check back soon." });
        return;
      }
      const result = await listHomeCarousel(client, 6);
      if (cancelled) return;
      if (!result.ok || result.data.length === 0) {
        setStatus({
          kind: "empty",
          message: result.ok
            ? "New stock is on the way. Browse the shop in the meantime."
            : "We couldn't load featured parts right now. Please try again shortly.",
        });
        return;
      }
      setStatus({ kind: "ready", items: result.data });
    }
    void run();
    return () => {
      cancelled = true;
    };
  }, []);

  const count = status.kind === "ready" ? status.items.length : 0;

  const select = useCallback((index: number) => {
    setActive(index);
    setCycle((c) => c + 1);
  }, []);

  useEffect(() => {
    if (count < 2 || paused) return;
    if (window.matchMedia("(prefers-reduced-motion: reduce)").matches) return;
    const timer = window.setTimeout(() => {
      setActive((a) => (a + 1) % count);
      setCycle((c) => c + 1);
    }, INTERVAL_MS);
    return () => window.clearTimeout(timer);
  }, [count, paused, active, cycle]);

  if (status.kind !== "ready") {
    return (
      <section className={styles.hero} aria-label="Featured parts">
        <div className={styles.container}>
          <div className={styles.placeholder}>
            <p className={styles.kicker}>Nissan GTR Auto</p>
            <h1 className={styles.headline}>Genuine Nissan parts, in stock</h1>
            <p className={styles.note} role="status">
              {status.kind === "loading" ? "Loading featured parts…" : status.message}
            </p>
            <div className={styles.ctaRow}>
              <Link href="/shop" className={styles.cta}>
                Shop parts
              </Link>
              <Link href="/vehicle" className={styles.ctaGhost}>
                Select vehicle
              </Link>
            </div>
          </div>
        </div>
      </section>
    );
  }

  const items = status.items;
  const current = items[active] ?? items[0];

  return (
    <section
      className={styles.hero}
      aria-roledescription="carousel"
      aria-label="Featured parts"
      onMouseEnter={() => setPaused(true)}
      onMouseLeave={() => setPaused(false)}
    >
      <div className={styles.container}>
        {items.map((item, i) => (
          <input
            key={`radio-${item.id}`}
            className={styles.radio}
            type="radio"
            name="slider"
            id={`item-${i + 1}`}
            checked={i === active}
            onChange={() => select(i)}
            aria-label={`Show ${item.name}`}
          />
        ))}

        <div className={styles.cards}>
          {items.map((item, i) => {
            const slot = slotFor(i, active, items.length);
            const media = item.imageUrl ? (
              // eslint-disable-next-line @next/next/no-img-element
              <img src={item.imageUrl} alt={item.name} loading={i === 0 ? "eager" : "lazy"} />
            ) : (
              <span className={styles.fallback} aria-hidden>
                <span className={styles.fallbackMark}>GTR</span>
                <span className={styles.fallbackName}>{item.name}</span>
              </span>
            );
            return slot === "active" ? (
              <Link
                key={item.id}
                href={partHref(item)}
                className={`${styles.card} ${styles.active}`}
                id={`song-${i + 1}`}
                aria-label={`${item.name}: view part`}
              >
                {media}
              </Link>
            ) : (
              <label
                key={item.id}
                className={`${styles.card} ${styles[slot]}`}
                htmlFor={`item-${i + 1}`}
                id={`song-${i + 1}`}
                aria-hidden={slot === "hidden"}
              >
                {media}
              </label>
            );
          })}
        </div>

        <div className={styles.player}>
          <div className={styles.upperPart}>
            <button
              type="button"
              className={styles.playIcon}
              onClick={() => setPaused((p) => !p)}
              aria-label={paused ? "Play carousel" : "Pause carousel"}
            >
              {paused ? (
                <svg width="20" height="20" viewBox="0 0 24 24" aria-hidden>
                  <path d="M8 5v14l11-7z" fill="currentColor" />
                </svg>
              ) : (
                <svg width="20" height="20" viewBox="0 0 24 24" aria-hidden>
                  <path d="M7 5h4v14H7zM13 5h4v14h-4z" fill="currentColor" />
                </svg>
              )}
            </button>
            <div className={styles.infoArea} aria-live="polite">
              <Link href={partHref(current)} className={styles.songInfo}>
                <span className={styles.title}>{current.name}</span>
                <span className={styles.subLine}>
                  <span className={styles.subtitle}>
                    {current.caption ?? current.discountDescription ?? "Genuine Nissan part"}
                  </span>
                  <span className={styles.time}>
                    {current.usd != null ? (
                      <PriceDual usd={current.usd} zig={current.zig} />
                    ) : (
                      "Price on request"
                    )}
                  </span>
                </span>
              </Link>
            </div>
          </div>
          <div className={styles.progressBar} aria-hidden>
            <span
              key={`${active}-${cycle}`}
              className={`${styles.progress} ${paused ? styles.progressPaused : ""}`}
              style={{ animationDuration: `${INTERVAL_MS}ms` }}
            />
          </div>
          <div className={styles.dots}>
            {items.map((item, i) => (
              <label
                key={`dot-${item.id}`}
                htmlFor={`item-${i + 1}`}
                className={i === active ? styles.dotActive : styles.dot}
              >
                <span className={styles.srOnly}>{item.name}</span>
              </label>
            ))}
          </div>
        </div>
      </div>
    </section>
  );
}
