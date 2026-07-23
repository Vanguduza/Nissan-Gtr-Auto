/** Currency codes used across the ERP. Never assume USD silently. */
export type CurrencyCode = "USD" | "ZIG";

export interface Money {
  amount: number;
  currency: CurrencyCode;
  /** Exchange rate applied at transaction time when converted; null if native. */
  exchangeRateApplied?: number | null;
}

export function assertCurrency(code: string): asserts code is CurrencyCode {
  if (code !== "USD" && code !== "ZIG") {
    throw new Error(`Unsupported currency: ${code}`);
  }
}
