"use client";

import Link from "next/link";
import { useCallback, useEffect, useId, useState } from "react";
import {
  Building2,
  Car,
  iconSizeMd,
  iconSizeSm,
  iconStroke,
  LayoutGrid,
  Menu,
  MessageCircle,
  Package,
  Search,
  X,
} from "@/components/icons";
import styles from "./site-menu.module.css";

const primaryLinks = [
  { href: "/", label: "Home", Icon: LayoutGrid },
  { href: "/catalog", label: "All categories", Icon: LayoutGrid },
  { href: "/search", label: "Advanced search", Icon: Search },
  { href: "/kits", label: "Service kits", Icon: Package },
  { href: "/vehicle", label: "Select vehicle", Icon: Car },
  { href: "/account/garage", label: "My Garage", Icon: Car },
  { href: "/contact", label: "Contact us", Icon: MessageCircle },
  { href: "/b2b", label: "Trade account", Icon: Building2 },
] as const;

export function SiteMenu() {
  const [open, setOpen] = useState(false);
  const titleId = useId();
  const panelId = useId();

  const close = useCallback(() => setOpen(false), []);
  const toggle = useCallback(() => setOpen((v) => !v), []);

  useEffect(() => {
    if (!open) return;
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") close();
    };
    document.addEventListener("keydown", onKey);
    const prev = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    return () => {
      document.removeEventListener("keydown", onKey);
      document.body.style.overflow = prev;
    };
  }, [open, close]);

  return (
    <div className={styles.wrap}>
      <button
        type="button"
        className={styles.trigger}
        aria-expanded={open}
        aria-controls={panelId}
        aria-haspopup="dialog"
        onClick={toggle}
      >
        {open ? (
          <X size={iconSizeMd} strokeWidth={iconStroke} aria-hidden />
        ) : (
          <Menu size={iconSizeMd} strokeWidth={iconStroke} aria-hidden />
        )}
        <span className={styles.triggerLabel}>Menu</span>
      </button>

      {open ? (
        <div className={styles.layer} role="presentation">
          <button
            type="button"
            className={styles.backdrop}
            aria-label="Close menu"
            onClick={close}
          />
          <div
            id={panelId}
            className={styles.panel}
            role="dialog"
            aria-modal="true"
            aria-labelledby={titleId}
          >
            <div className={styles.panelHead}>
              <h2 id={titleId} className={styles.panelTitle}>
                Menu
              </h2>
              <button
                type="button"
                className={styles.close}
                onClick={close}
                aria-label="Close menu"
              >
                <X size={iconSizeMd} strokeWidth={iconStroke} aria-hidden />
              </button>
            </div>
            <nav className={styles.nav} aria-label="Site menu">
              <ul className={styles.list}>
                {primaryLinks.map(({ href, label, Icon }) => (
                  <li key={href}>
                    <Link href={href} className={styles.link} onClick={close}>
                      <Icon
                        size={iconSizeSm}
                        strokeWidth={iconStroke}
                        aria-hidden
                      />
                      {label}
                    </Link>
                  </li>
                ))}
              </ul>
            </nav>
          </div>
        </div>
      ) : null}
    </div>
  );
}
