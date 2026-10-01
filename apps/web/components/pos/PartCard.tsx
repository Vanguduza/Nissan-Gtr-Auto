"use client";

import { EllipsisVertical, Package, Pin, PinOff, Search, ShoppingCart, Trash2 } from "lucide-react";
import { useEffect, useRef, useState } from "react";
import { formatMoney } from "@/lib/pos/money";
import type { PosPart } from "@/lib/pos/types";
import styles from "./pos.module.css";

export function PartThumb({ src, className }: { src: string | null; className: string }) {
  return (
    <div className={className}>
      {src ? (
        // eslint-disable-next-line @next/next/no-img-element
        <img src={src} alt="" />
      ) : (
        <Package size={28} strokeWidth={1.5} aria-hidden />
      )}
    </div>
  );
}

/**
 * Part card used in Popular Items and search results. Long-press (or the ⋮ menu) opens
 * Pin / Remove — no permanent pin control cluttering the card (blueprint §7.2).
 */
export function PartCard({
  part,
  pinned,
  pinnedBadge,
  disabled,
  addLabel = "Add",
  onAdd,
  onPin,
  onRemove,
}: {
  part: PosPart;
  pinned: boolean;
  pinnedBadge?: boolean;
  disabled?: boolean;
  /** "Find" for a pin whose live price is not loaded yet — it opens a search instead of adding. */
  addLabel?: "Add" | "Find";
  onAdd: () => void;
  onPin?: () => void;
  onRemove?: () => void;
}) {
  const [menu, setMenu] = useState(false);
  const pressTimer = useRef<number | null>(null);
  const hasMenu = Boolean(onPin || onRemove);

  useEffect(() => {
    if (!menu) return;
    const close = () => setMenu(false);
    window.addEventListener("click", close);
    return () => window.removeEventListener("click", close);
  }, [menu]);

  const startPress = () => {
    if (!hasMenu) return;
    pressTimer.current = window.setTimeout(() => setMenu(true), 550);
  };
  const endPress = () => {
    if (pressTimer.current) window.clearTimeout(pressTimer.current);
    pressTimer.current = null;
  };

  const actionable = addLabel === "Find" || Boolean(part.price);
  return (
    <article
      className={styles.partCard}
      onPointerDown={startPress}
      onPointerUp={endPress}
      onPointerLeave={endPress}
      onContextMenu={(e) => {
        if (!hasMenu) return;
        e.preventDefault();
        setMenu(true);
      }}
    >
      {pinnedBadge ? (
        <span className={styles.pinTag}>
          <Pin size={10} aria-hidden /> Pinned
        </span>
      ) : null}
      {hasMenu ? (
        <button
          type="button"
          className={`${styles.iconButton} ${styles.cardMenu}`}
          aria-label={`More actions for ${part.name}`}
          onClick={(e) => {
            e.stopPropagation();
            setMenu((m) => !m);
          }}
        >
          <EllipsisVertical size={18} aria-hidden />
        </button>
      ) : null}
      {menu ? (
        <div className={styles.menu} role="menu" onClick={(e) => e.stopPropagation()}>
          {onPin ? (
            <button type="button" role="menuitem" className={styles.menuItem} onClick={() => { setMenu(false); onPin(); }}>
              {pinned ? <PinOff size={16} aria-hidden /> : <Pin size={16} aria-hidden />}
              {pinned ? "Unpin from Popular" : "Pin to Popular"}
            </button>
          ) : null}
          {onRemove ? (
            <button type="button" role="menuitem" className={styles.menuItem} onClick={() => { setMenu(false); onRemove(); }}>
              <Trash2 size={16} aria-hidden /> Remove from Popular
            </button>
          ) : null}
        </div>
      ) : null}
      <PartThumb src={part.imageUrl} className={styles.partImage} />
      <div className={styles.partName}>{part.name}</div>
      <div className={styles.partOem}>{part.oemPartNumber}</div>
      <div className={styles.partMeta}>
        <span className={styles.partPrice}>
          {part.price ? formatMoney(part.price.amount, part.price.currency) : addLabel === "Find" ? "" : "Needs price"}
        </span>
        {part.saleableQty != null ? (
          <span className={part.saleableQty > 0 ? styles.stockIn : styles.stockOut}>
            {part.saleableQty > 0 ? `${part.saleableQty} in stock` : "Out of stock"}
          </span>
        ) : null}
      </div>
      <button type="button" className={styles.softButton} disabled={disabled || !actionable} onClick={onAdd}>
        {addLabel === "Find" ? <Search size={16} aria-hidden /> : <ShoppingCart size={16} aria-hidden />} {addLabel}
      </button>
    </article>
  );
}
