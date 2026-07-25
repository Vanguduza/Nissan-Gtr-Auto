import type { Money } from "./money";
import { assertCurrency } from "./money";

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
