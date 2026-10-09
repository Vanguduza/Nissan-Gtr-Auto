import type { Money, MoneyMinor } from "./money";
import { assertCurrency, moneyToMinor, minorToMoney } from "./money";

/**
 * Split a cart line into physical part price + optional core-charge deposit.
 * Keeps revenue/COGS accurate per .cursorrules Core Charge Handling.
 *
 * @deprecated Prefer {@link splitCoreChargeMinor} (H4 — amountMinor SoR).
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

/** H4: core-charge split on MoneyMinor (major derived only if a legacy bridge needs it). */
export function splitCoreChargeMinor(params: {
  partPrice: MoneyMinor;
  coreCharge?: MoneyMinor | null;
}): { part: MoneyMinor; core: MoneyMinor | null } {
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

/** Bridge: legacy Money split → MoneyMinor. */
export function splitCoreChargeAsMinor(params: {
  partPrice: Money;
  coreCharge?: Money | null;
}): { part: MoneyMinor; core: MoneyMinor | null } {
  const split = splitCoreCharge(params);
  return {
    part: moneyToMinor(split.part),
    core: split.core ? moneyToMinor(split.core) : null,
  };
}

/** Bridge: MoneyMinor split → legacy Money (display / major RPC only). */
export function splitCoreChargeAsLegacy(params: {
  partPrice: MoneyMinor;
  coreCharge?: MoneyMinor | null;
}): { part: Money; core: Money | null } {
  const split = splitCoreChargeMinor(params);
  return {
    part: minorToMoney(split.part),
    core: split.core ? minorToMoney(split.core) : null,
  };
}
