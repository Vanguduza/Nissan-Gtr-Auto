/** Money owed per currency. Never add USD to ZiG: show each one. */
export type Owed = { currency: string; amount: number }[];

/** Reads `owing_by_currency` (falls back to the single `owing` in `owing_currency`). */
export function owedFrom(o: Record<string, unknown>): Owed {
  const list = o.owing_by_currency;
  if (Array.isArray(list)) {
    return (list as Record<string, unknown>[])
      .map((r) => ({ currency: String(r.currency ?? "USD"), amount: Number(r.amount ?? 0) }))
      .filter((r) => r.amount > 0);
  }
  const amount = Number(o.owing ?? 0);
  return amount > 0 ? [{ currency: String(o.owing_currency ?? "USD"), amount }] : [];
}

/** "USD 95.00 + ZIG 2,400.00", or "nothing". */
export function owedText(owed: Owed): string {
  if (owed.length === 0) return "nothing";
  return owed
    .map((r) => `${r.currency} ${r.amount.toLocaleString("en-US", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`)
    .join(" + ");
}
