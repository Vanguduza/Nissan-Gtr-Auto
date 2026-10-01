"use client";

import {
  Car,
  ChevronLeft,
  ChevronRight,
  Clock,
  Cog,
  Disc3,
  Droplet,
  Gauge,
  Pin,
  Tag,
  ShieldCheck,
  Sparkles,
  Wrench,
  Zap,
  type LucideIcon,
} from "lucide-react";
import { useRef, useState } from "react";
import { haptic } from "@/lib/pos/haptics";
import type { PopularPin } from "@/lib/pos/types";
import type { PosStore } from "@/lib/pos/use-pos";
import { PartCard } from "./PartCard";
import styles from "./pos.module.css";

/** Category tiles seed real catalogue searches — never hard-coded product results. */
const CATEGORIES: Array<{ label: string; query: string; icon: LucideIcon }> = [
  { label: "Engine & Drivetrain", query: "engine", icon: Cog },
  { label: "Brakes", query: "brake", icon: Disc3 },
  { label: "Suspension", query: "suspension", icon: Wrench },
  { label: "Body & Exterior", query: "body", icon: Car },
  { label: "Electrical", query: "electrical", icon: Zap },
  { label: "Fluids & Chemicals", query: "fluid", icon: Droplet },
  { label: "Accessories", query: "accessories", icon: Sparkles },
];

export function PosHero() {
  return (
    <section className={styles.hero} aria-label="Nissan GTR Auto">
      {/* eslint-disable-next-line @next/next/no-img-element */}
      <img className={styles.heroImage} src="/pos/pos_hero_car.webp" alt="" />
      <div className={styles.heroCopy}>
        <h1 className={styles.heroTitle}>
          Genuine Parts
          <br />
          Real Performance
        </h1>
        <p className={styles.heroSub}>Keep your GTR at its best.</p>
        <span className={styles.heroRule} aria-hidden />
        <div className={styles.heroBadges}>
          <span className={styles.heroBadge}>
            <Cog size={22} strokeWidth={1.5} aria-hidden /> Genuine
            <br />
            parts
          </span>
          <span className={styles.heroBadge}>
            <ShieldCheck size={22} strokeWidth={1.5} aria-hidden /> Trusted
            <br />
            quality
          </span>
          <span className={styles.heroBadge}>
            <Gauge size={22} strokeWidth={1.5} aria-hidden /> Performance
            <br />
            focused
          </span>
        </div>
      </div>
    </section>
  );
}

export function PosHome({ pos }: { pos: PosStore }) {
  const rowRef = useRef<HTMLDivElement>(null);
  const scroll = (dir: 1 | -1) => rowRef.current?.scrollBy({ left: dir * rowRef.current.clientWidth * 0.8, behavior: "smooth" });

  return (
    <>
      <PosHero />

      <div className={styles.categories} role="group" aria-label="Spare categories">
        {CATEGORIES.map(({ label, query, icon }) => (
          <CategoryTile
            key={label}
            label={label}
            icon={icon}
            onOpen={() => void pos.runSearch(query)}
            onPin={() => void pos.pinCategory(label, query)}
          />
        ))}
      </div>

      <section aria-labelledby="pos-popular">
        <div className={styles.sectionHead}>
          <h2 id="pos-popular" className={styles.sectionTitle}>
            Popular Items
          </h2>
          <div className={styles.sectionTools}>
            <button type="button" className={styles.iconButton} aria-label="Scroll left" onClick={() => scroll(-1)}>
              <ChevronLeft size={18} aria-hidden />
            </button>
            <button type="button" className={styles.iconButton} aria-label="Scroll right" onClick={() => scroll(1)}>
              <ChevronRight size={18} aria-hidden />
            </button>
          </div>
        </div>
        <div className={styles.popularRow} ref={rowRef}>
          {pos.popular.length === 0 ? (
            <div className={styles.emptyCard}>
              No best sellers yet. Long-press any part, category or vehicle to pin it here.
            </div>
          ) : (
            pos.popular.map((item) =>
              item.source === "bestseller" ? (
                <PartCard
                  key={item.key}
                  part={item.part}
                  pinned={pos.isPinned(item.part)}
                  disabled={pos.busy}
                  onAdd={() => void pos.addPart(item.part)}
                  onPin={() => void pos.pinPart(item.part)}
                  onRemove={() => void pos.removePopular(item)}
                />
              ) : (
                item.pin.kind !== "part" ? (
                  <PinCard key={item.key} pin={item.pin} onOpen={() => void pos.activatePin(item.pin)} onRemove={() => void pos.removePopular(item)} />
                ) : (() => {
                  const live = pos.partForPin(item.pin);
                  return (
                    <PartCard
                      key={item.key}
                      part={
                        live ?? {
                          stockItemId: null,
                          oemPartNumber: item.pin.oemPartNumber ?? item.pin.searchQuery,
                          name: item.pin.label,
                          price: null,
                          saleableQty: null,
                          imageUrl: item.pin.imageUrl,
                        }
                      }
                      pinned
                      pinnedBadge
                      disabled={pos.busy}
                      addLabel={live ? "Add" : "Find"}
                      onAdd={() => (live ? void pos.addPart(live) : void pos.runSearch(item.pin.searchQuery))}
                      onRemove={() => void pos.removePopular(item)}
                    />
                  );
                })()
              ),
            )
          )}
        </div>
      </section>

      {pos.recent.length > 0 ? (
        <section aria-labelledby="pos-recent">
          <div className={styles.sectionHead}>
            <h2 id="pos-recent" className={styles.sectionTitle}>
              Recent Searches
            </h2>
            <button type="button" className={styles.textLink} onClick={pos.clearRecent}>
              Clear All
            </button>
          </div>
          <div className={styles.chips}>
            {pos.recent.map((r) => (
              <button key={r} type="button" className={styles.chip} onClick={() => void pos.runSearch(r)}>
                <Clock size={15} aria-hidden /> {r}
              </button>
            ))}
          </div>
        </section>
      ) : null}
    </>
  );
}

export function PosSearchResults({ pos }: { pos: PosStore }) {
  const results = pos.results;
  return (
    <section aria-labelledby="pos-results">
      <div className={styles.sectionHead}>
        <h2 id="pos-results" className={styles.sectionTitle}>
          {pos.vehicle ? `Parts for ${pos.vehicle.modelName} ${pos.vehicle.chassisCode} ${pos.vehicle.engineCode}` : "Search Spares"}
        </h2>
        <span className={styles.muted}>{results ? `${results.length} result${results.length === 1 ? "" : "s"}` : ""}</span>
      </div>
      {results == null ? (
        <div className={styles.emptyCard}>Search by part name, OEM number, PNC or vehicle, or choose a vehicle above.</div>
      ) : results.length === 0 ? (
        <div className={styles.emptyCard}>No matching parts{pos.vehicle ? " for this vehicle" : ""}.</div>
      ) : (
        <div className={styles.resultsGrid}>
          {results.map((part) => (
            <PartCard
              key={part.oemPartNumber}
              part={part}
              pinned={pos.isPinned(part)}
              disabled={pos.busy}
              onAdd={() => void pos.addPart(part)}
              onPin={() => void pos.pinPart(part)}
            />
          ))}
        </div>
      )}
    </section>
  );
}

/**
 * Category tile: tap searches; long-press (or right-click) pins it to Popular Items. State lives in
 * refs so a re-render between press and release cannot lose the "long-press fired" flag.
 */
function CategoryTile({ label, icon: Icon, onOpen, onPin }: { label: string; icon: LucideIcon; onOpen: () => void; onPin: () => void }) {
  const timer = useRef<number | null>(null);
  const fired = useRef(false);
  const clear = () => {
    if (timer.current) window.clearTimeout(timer.current);
    timer.current = null;
  };
  return (
    <button
      type="button"
      className={`${styles.category} ${styles.focusable}`}
      title="Long-press or right-click to pin to Popular Items"
      onPointerDown={() => {
        fired.current = false;
        clear();
        timer.current = window.setTimeout(() => {
          fired.current = true;
          haptic("longPress");
          onPin();
        }, 600);
      }}
      onPointerUp={clear}
      onPointerLeave={clear}
      onClick={() => {
        if (fired.current) {
          fired.current = false;
          return;
        }
        onOpen();
      }}
      onContextMenu={(e) => {
        e.preventDefault();
        clear();
        onPin();
      }}
    >
      <Icon className={styles.categoryIcon} size={30} strokeWidth={1.5} aria-hidden />
      {label}
    </button>
  );
}

/** Non-part pin (vehicle, category, subcategory): opens the vehicle or the search it stands for. */
function PinCard({ pin, onOpen, onRemove }: { pin: PopularPin; onOpen: () => void; onRemove: () => void }) {
  const [menu, setMenu] = useState(false);
  const Icon = pin.kind === "model" ? Car : Tag;
  return (
    <article className={styles.partCard} onContextMenu={(e) => { e.preventDefault(); setMenu(true); }}>
      <span className={styles.pinTag}>
        <Pin size={10} aria-hidden /> {pin.kind === "model" ? "Vehicle" : pin.kind === "category" ? "Category" : "Subcategory"}
      </span>
      <button type="button" className={`${styles.iconButton} ${styles.cardMenu}`} aria-label={`More actions for ${pin.label}`} onClick={() => setMenu((m) => !m)}>
        ⋮
      </button>
      {menu ? (
        <div className={styles.menu} role="menu">
          <button type="button" role="menuitem" className={styles.menuItem} onClick={() => { setMenu(false); onRemove(); }}>
            Remove from Popular
          </button>
        </div>
      ) : null}
      <div className={styles.partImage}>
        <Icon size={34} strokeWidth={1.4} aria-hidden />
      </div>
      <div className={styles.partName}>{pin.label}</div>
      <div className={styles.partOem}>{pin.subtitle ?? ""}</div>
      <button type="button" className={styles.softButton} onClick={onOpen}>
        {pin.kind === "model" ? "Shop for this vehicle" : "Show parts"}
      </button>
    </article>
  );
}
