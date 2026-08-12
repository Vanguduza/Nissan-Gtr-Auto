/** Currency codes used across the ERP. Never assume USD silently. */
export type CurrencyCode = "USD" | "ZIG";

/**
 * Legacy display/RPC money (NUMERIC / JS number). Prefer {@link MoneyMinor} for
 * new code paths (DIAL Dial-a-Spare lock: amountMinor + currency).
 */
export interface Money {
  amount: number;
  currency: CurrencyCode;
  /** Exchange rate applied at transaction time when converted; null if native. */
  exchangeRateApplied?: number | null;
}

/**
 * Canonical money for new APIs and dual-write migrations.
 * `amountMinor` is the smallest currency unit (cents / ZiG minor) as an integer.
 * Never use floats for payable amounts; AI must not invent this field.
 */
export interface MoneyMinor {
  amountMinor: bigint;
  currency: CurrencyCode;
  /** Ops FX row id when converted at checkout (D-57); omit if native. */
  fxRateId?: string | null;
}

/** Minor units per major unit for supported currencies (both use 2 decimals today). */
export const MINOR_PER_MAJOR: Record<CurrencyCode, number> = {
  USD: 100,
  ZIG: 100,
};

export function assertCurrency(code: string): asserts code is CurrencyCode {
  if (code !== "USD" && code !== "ZIG") {
    throw new Error(`Unsupported currency: ${code}`);
  }
}

export function assertAmountMinor(amountMinor: bigint): void {
  if (typeof amountMinor !== "bigint") {
    throw new Error("amountMinor must be bigint");
  }
}

/**
 * Convert a decimal major-unit amount to minor units.
 * Rejects non-finite values; rounds half-away-from-zero to nearest minor.
 */
export function toAmountMinor(
  amountMajor: number,
  currency: CurrencyCode,
): bigint {
  assertCurrency(currency);
  if (!Number.isFinite(amountMajor)) {
    throw new Error("amountMajor must be finite");
  }
  const scale = MINOR_PER_MAJOR[currency];
  const scaled = amountMajor * scale;
  const rounded =
    scaled >= 0 ? Math.floor(scaled + 0.5) : Math.ceil(scaled - 0.5);
  return BigInt(rounded);
}

/** Convert minor units back to a JS number major (display / legacy RPC bridge). */
export function fromAmountMinor(
  amountMinor: bigint,
  currency: CurrencyCode,
): number {
  assertCurrency(currency);
  assertAmountMinor(amountMinor);
  const scale = MINOR_PER_MAJOR[currency];
  return Number(amountMinor) / scale;
}

export function moneyToMinor(m: Money): MoneyMinor {
  assertCurrency(m.currency);
  return {
    amountMinor: toAmountMinor(m.amount, m.currency),
    currency: m.currency,
  };
}

export function minorToMoney(m: MoneyMinor): Money {
  assertCurrency(m.currency);
  return {
    amount: fromAmountMinor(m.amountMinor, m.currency),
    currency: m.currency,
  };
}

/** Convert major NUMERIC amount to minor bigint for dual-write / PostgREST. */
export function majorToMinorNumber(
  amountMajor: number,
  currency: CurrencyCode = "USD",
): number {
  return Number(toAmountMinor(amountMajor, currency));
}

/** Dual-write payload: keep legacy amount + amount_minor together. */
export function dualWriteMoney(
  amountMajor: number,
  currency: CurrencyCode,
): { amount: number; amountMinor: bigint; currency: CurrencyCode } {
  assertCurrency(currency);
  return {
    amount: amountMajor,
    amountMinor: toAmountMinor(amountMajor, currency),
    currency,
  };
}

/** Serialize for JSON / PostgREST (bigint → string). */
export function moneyMinorToJson(m: MoneyMinor): {
  amountMinor: string;
  currency: CurrencyCode;
  fxRateId?: string | null;
} {
  return {
    amountMinor: m.amountMinor.toString(),
    currency: m.currency,
    ...(m.fxRateId !== undefined ? { fxRateId: m.fxRateId } : {}),
  };
}

export function moneyMinorFromJson(raw: {
  amountMinor: string | number | bigint;
  currency: string;
  fxRateId?: string | null;
}): MoneyMinor {
  assertCurrency(raw.currency);
  const amountMinor =
    typeof raw.amountMinor === "bigint"
      ? raw.amountMinor
      : BigInt(raw.amountMinor);
  return {
    amountMinor,
    currency: raw.currency,
    fxRateId: raw.fxRateId ?? null,
  };
}
