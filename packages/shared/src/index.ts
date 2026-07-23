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

/**
 * Split a cart line into physical part price + optional core-charge deposit.
 * Keeps revenue/COGS accurate per .cursorrules Core Charge Handling.
 */
export function splitCoreCharge(params: {
  partPrice: Money;
  coreCharge?: Money | null;
}): { part: Money; core: Money | null } {
  assertCurrency(params.partPrice.currency);
  if (!params.coreCharge) {
    return { part: params.partPrice, core: null };
  }
  assertCurrency(params.coreCharge.currency);
  if (params.coreCharge.currency !== params.partPrice.currency) {
    throw new Error("Core charge currency must match part price currency");
  }
  return { part: params.partPrice, core: params.coreCharge };
}

export * from "./ledger/journal.js";
