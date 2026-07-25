/**
 * Guest compare tray (localStorage). Auth users sync via customer-compare RPCs;
 * localStorage still mirrors OEMs for PDP badges.
 */

const STORAGE_KEY = "gtr.compare.oems";
/** Matches `_customer_compare_max_items()` (8). */
const MAX_COMPARE = 8;

export function readCompareOems(): string[] {
  if (typeof window === "undefined") return [];
  try {
    const raw = window.localStorage.getItem(STORAGE_KEY);
    if (!raw) return [];
    const parsed = JSON.parse(raw) as unknown;
    if (!Array.isArray(parsed)) return [];
    return parsed
      .filter((x): x is string => typeof x === "string")
      .map((s) => s.trim())
      .filter(Boolean)
      .slice(0, MAX_COMPARE);
  } catch {
    return [];
  }
}

export function writeCompareOems(oems: string[]) {
  if (typeof window === "undefined") return;
  window.localStorage.setItem(STORAGE_KEY, JSON.stringify(oems.slice(0, MAX_COMPARE)));
}

export function isOemInCompare(oem: string): boolean {
  const needle = oem.trim().toLowerCase();
  return readCompareOems().some((o) => o.toLowerCase() === needle);
}

export function addOemToCompare(oem: string): { ok: true; oems: string[] } | { ok: false; error: string } {
  const needle = oem.trim();
  if (!needle) return { ok: false, error: "OEM required." };
  const current = readCompareOems();
  if (current.some((o) => o.toLowerCase() === needle.toLowerCase())) {
    return { ok: true, oems: current };
  }
  if (current.length >= MAX_COMPARE) {
    return {
      ok: false,
      error: `Compare holds up to ${MAX_COMPARE} SKUs. Remove one first.`,
    };
  }
  const next = [...current, needle];
  writeCompareOems(next);
  return { ok: true, oems: next };
}

export function removeOemFromCompare(oem: string): string[] {
  const needle = oem.trim().toLowerCase();
  const next = readCompareOems().filter((o) => o.toLowerCase() !== needle);
  writeCompareOems(next);
  return next;
}

export function clearCompare(): void {
  if (typeof window === "undefined") return;
  window.localStorage.removeItem(STORAGE_KEY);
}

export { MAX_COMPARE };
