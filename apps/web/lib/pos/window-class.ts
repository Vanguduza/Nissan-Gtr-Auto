"use client";

import { useEffect, useState } from "react";

/**
 * Window classes from the blueprint (§3.5), derived from the viewport — never from a device name.
 * Expanded ≥ 1040 px · Medium 600–1039 px · Compact < 600 px (or short landscape).
 */
export type WindowClass = "expanded" | "medium" | "compact";

/** The cart stays a side pane while rail + 320 px cart + a usable canvas fit (§3.3, §3.5). */
export type CartMode = "pane" | "sheet";

const MEDIUM = "(min-width: 600px) and (min-height: 480px)";
const EXPANDED = "(min-width: 1040px) and (min-height: 480px)";
const CART_PANE = "(min-width: 900px) and (min-height: 480px)";

function read(): { windowClass: WindowClass; cartMode: CartMode } {
  if (typeof window === "undefined" || !window.matchMedia) return { windowClass: "expanded", cartMode: "pane" };
  const windowClass: WindowClass = window.matchMedia(EXPANDED).matches
    ? "expanded"
    : window.matchMedia(MEDIUM).matches
      ? "medium"
      : "compact";
  return { windowClass, cartMode: window.matchMedia(CART_PANE).matches ? "pane" : "sheet" };
}

export function useWindowClass(): { windowClass: WindowClass; cartMode: CartMode } {
  // Expanded on the server and first paint; corrected before the user can interact.
  const [value, setValue] = useState<{ windowClass: WindowClass; cartMode: CartMode }>({
    windowClass: "expanded",
    cartMode: "pane",
  });
  useEffect(() => {
    const update = () => setValue(read());
    update();
    const queries = [EXPANDED, MEDIUM, CART_PANE].map((q) => window.matchMedia(q));
    queries.forEach((q) => q.addEventListener("change", update));
    return () => queries.forEach((q) => q.removeEventListener("change", update));
  }, []);
  return value;
}
