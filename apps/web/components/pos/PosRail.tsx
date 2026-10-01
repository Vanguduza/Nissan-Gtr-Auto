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
  type LucideIcon,
} from "lucide-react";
import type { PosDestination } from "@/lib/pos/use-pos";
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
  { id: "settings", label: "Settings", icon: Settings },
];

export function PosRail({
  active,
  onSelect,
}: {
  active: PosDestination;
  onSelect: (d: PosDestination) => void;
}) {
  return (
    <nav className={styles.rail} data-gtr-scheme="dark" aria-label="POS">
      {/* eslint-disable-next-line @next/next/no-img-element */}
      <img className={styles.railLogo} src="/brand/logo.png" alt="Nissan GTR Auto" />
      {NAV.map(({ id, label, icon: Icon }) => (
        <button
          key={id}
          type="button"
          className={`${styles.navItem} ${active === id ? styles.navItemActive : ""}`}
          aria-current={active === id ? "page" : undefined}
          onClick={() => onSelect(id)}
        >
          <Icon size={20} strokeWidth={1.75} aria-hidden />
          <span>{label}</span>
        </button>
      ))}
      <div className={styles.railFoot}>
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
      </div>
    </nav>
  );
}
