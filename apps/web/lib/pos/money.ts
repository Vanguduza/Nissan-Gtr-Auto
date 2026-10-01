import type { PosCurrency } from "@/lib/pos/types";

/** Explicit currency on every amount (AGENTS.md multi-currency law). Never a bare number. */
export function formatMoney(amount: number, currency: PosCurrency): string {
  const symbol = currency === "USD" ? "US$" : "ZiG";
  const value = new Intl.NumberFormat("en-ZW", {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(amount);
  return `${symbol} ${value}`;
}

export function roundMoney(amount: number): number {
  return Math.round(amount * 100) / 100;
}
