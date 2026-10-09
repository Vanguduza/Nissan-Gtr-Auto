"use client";

import {
  BookOpen,
  FileText,
  House,
  Search,
  Settings,
  ShoppingCart,
  Undo2,
  User,
  Wallet,
  type LucideIcon,
} from "lucide-react";
import type { PosDestination } from "@/lib/pos/use-pos";
import type { WindowClass } from "@/lib/pos/window-class";
import styles from "./pos.module.css";

/** Rail order is canonical: Reports is replaced by EPC Browse (delta D-001). */
const NAV: Array<{ id: PosDestination; label: string; icon: LucideIcon }> = [
  { id: "home", label: "Home", icon: House },
  { id: "search", label: "Search Spares", icon: Search },
  { id: "quickSale", label: "Quick Sale", icon: ShoppingCart },
  { id: "customer", label: "Customer", icon: User },
  { id: "orders", label: "Orders", icon: FileText },
  { id: "returns", label: "Returns", icon: Undo2 },
  { id: "epc", label: "EPC Browse", icon: BookOpen },
  { id: "till", label: "Till", icon: Wallet },
  { id: "settings", label: "Settings", icon: Settings },
];

export function PosRail({
  active,
  onSelect,
  windowClass,
}: {
  active: PosDestination;
  onSelect: (d: PosDestination) => void;
  windowClass: WindowClass;
}) {
  if (windowClass === "compact") {
    // Compact: the same destinations as a bottom navigation bar (blueprint §9).
    return (
      <nav className={styles.bottomNav} aria-label="POS">
        {NAV.map(({ id, label, icon: Icon }) => (
          <button
            key={id}
            type="button"
            className={`${styles.bottomNavItem} ${active === id ? styles.bottomNavActive : ""}`}
            aria-current={active === id ? "page" : undefined}
            aria-label={label}
            onClick={() => onSelect(id)}
          >
            <Icon size={20} strokeWidth={1.75} aria-hidden />
            <span>{label.split(" ")[0]}</span>
          </button>
        ))}
      </nav>
    );
  }
  const iconOnly = windowClass === "medium";
  return (
    <nav className={`${styles.rail} ${iconOnly ? styles.railIcons : ""}`} data-gtr-scheme="dark" aria-label="POS">
      {/* eslint-disable-next-line @next/next/no-img-element */}
      <img className={styles.railLogo} src="/brand/logo.png" alt="Nissan GTR Auto" />
      {NAV.map(({ id, label, icon: Icon }) => (
        <button
          key={id}
          type="button"
          className={`${styles.navItem} ${active === id ? styles.navItemActive : ""}`}
          aria-current={active === id ? "page" : undefined}
          aria-label={iconOnly ? label : undefined}
          title={iconOnly ? label : undefined}
          onClick={() => onSelect(id)}
        >
          <Icon size={20} strokeWidth={1.75} aria-hidden />
          {iconOnly ? null : <span>{label}</span>}
        </button>
      ))}
      {iconOnly ? null : <div className={styles.railFoot}>
        {/* eslint-disable-next-line @next/next/no-img-element */}
        <img className={styles.railCar} src="/pos/pos_nav_car_locked.webp" alt="" />
        <p className={styles.railStrap}>
          Built
          <br />
          for a
          <br />
          higher
          <br />
          standard
        </p>
      </div>}
    </nav>
  );
}
