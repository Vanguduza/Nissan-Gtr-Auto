/** Currency codes used across the ERP. Never assume USD silently. */
export type CurrencyCode = "USD" | "ZIG";

/**
 * Canonical money for new APIs (H4 cutover habit).
 * Prefer/require this shape; derive major NUMERIC only for legacy bridges.
 * `amountMinor` is the smallest currency unit (cents / ZiG minor) as an integer.
 * Never use floats for payable amounts; AI must not invent this field.
 */
export interface MoneyMinor {
  amountMinor: bigint;
  currency: CurrencyCode;
  /** Ops FX row id when converted at checkout (D-57); omit if native. */
  fxRateId?: string | null;
}

/**
 * @deprecated H4 API cutover — use {@link MoneyMinor} (`amountMinor` + currency).
 * Major `amount` is a legacy NUMERIC/JS bridge only; new shared contracts must
 * not accept major-only money. Alias: {@link LegacyMoney}.
 */
export interface Money {
  /** @deprecated Prefer amountMinor; major is derived for legacy RPC/display. */
  amount: number;
  currency: CurrencyCode;
  /** Exchange rate applied at transaction time when converted; null if native. */
  exchangeRateApplied?: number | null;
}

/** @deprecated Alias of {@link Money} — major NUMERIC bridge only. */
export type LegacyMoney = Money;

/**
 * New/shared API money contract (H4): amountMinor required; major never SoR.
 * Same as {@link MoneyMinor}; named for call-site clarity on RPC/DTO boundaries.
 */
export type ApiMoney = MoneyMinor;

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

/**
 * Dual-write from preferred {@link MoneyMinor}: major is derived for legacy columns/RPCs.
 * Use on new API write paths that still dual-write NUMERIC until column drop.
 */
export function dualWriteFromMinor(
  money: MoneyMinor,
): { amount: number; amountMinor: bigint; currency: CurrencyCode } {
  assertCurrency(money.currency);
  assertAmountMinor(money.amountMinor);
  return {
    amount: fromAmountMinor(money.amountMinor, money.currency),
    amountMinor: money.amountMinor,
    currency: money.currency,
  };
}

/**
 * PostgREST / SQL JSONB dual-write fields (`amount` + `amount_minor`).
 * Triggers may also fill minor; clients still send both (H4 cutover habit).
 */
export function dualWriteMoneyRpcFields(
  amountMajor: number,
  currency: CurrencyCode,
): { amount: number; amount_minor: number; currency: CurrencyCode } {
  const d = dualWriteMoney(amountMajor, currency);
  return {
    amount: d.amount,
    amount_minor: Number(d.amountMinor),
    currency: d.currency,
  };
}

/**
 * PO / cart line dual-write fields (`unit_price` + `unit_price_minor`).
 * `create_purchase_order` reads major; trigger fills minor — clients still send both.
 */
export function dualWriteUnitPriceRpcFields(
  unitPriceMajor: number,
  currency: CurrencyCode,
): { unit_price: number; unit_price_minor: number; currency: CurrencyCode } {
  assertCurrency(currency);
  return {
    unit_price: unitPriceMajor,
    unit_price_minor: majorToMinorNumber(unitPriceMajor, currency),
    currency,
  };
}

/**
 * H4 API cutover: require amountMinor (or derive once from legacy major).
 * Prefer minor when both present; never invent amounts.
 */
export function requireApiMoney(args: {
  amountMinor?: bigint | number | string | null;
  amountMajor?: number | null;
  currency: CurrencyCode;
  fxRateId?: string | null;
}): ApiMoney {
  const base = preferAmountMinor({
    amountMinor: args.amountMinor,
    amountMajor: args.amountMajor,
    currency: args.currency,
  });
  return {
    ...base,
    ...(args.fxRateId !== undefined ? { fxRateId: args.fxRateId } : {}),
  };
}

/**
 * Cart / invoice line money DTO for shared clients (prefer *_minor).
 * Major fields are derived display bridges only.
 */
export type CartLineMoneyDto = {
  unitPrice: MoneyMinor;
  lineTotal: MoneyMinor;
  /** @deprecated Derived from unitPrice for legacy UI bridges. */
  unitPriceMajor: number;
  /** @deprecated Derived from lineTotal for legacy UI bridges. */
  lineTotalMajor: number;
};

export function cartLineMoneyDto(
  line: {
    unit_price: number;
    unit_price_minor?: bigint | number | string | null;
    line_total: number;
    line_total_minor?: bigint | number | string | null;
  },
  currency: CurrencyCode,
): CartLineMoneyDto {
  const unitPrice = preferAmountMinor({
    amountMinor: line.unit_price_minor ?? null,
    amountMajor: Number(line.unit_price),
    currency,
  });
  const lineTotal = preferAmountMinor({
    amountMinor: line.line_total_minor ?? null,
    amountMajor: Number(line.line_total),
    currency,
  });
  return {
    unitPrice,
    lineTotal,
    unitPriceMajor: fromAmountMinor(unitPrice.amountMinor, currency),
    lineTotalMajor: fromAmountMinor(lineTotal.amountMinor, currency),
  };
}

/**
 * Checkout / PSP settlement payload: prefer amountMinor; major derived for Edge/RPC
 * that still accept NUMERIC `settlement_amount`.
 */
export type SettlementMoneyInput = {
  currency: CurrencyCode;
  amountMinor: bigint;
  exchangeRate: number;
  fxRateId?: string | null;
};

/** Dual-write settlement fields for ContiPay/Paynow initiate metadata + body. */
export function settlementMoneyRpcFields(s: SettlementMoneyInput): {
  settlement_currency: CurrencyCode;
  settlement_amount: number;
  settlement_amount_minor: number;
  settlement_exchange_rate: number;
  fx_rate_id?: string | null;
} {
  assertCurrency(s.currency);
  assertAmountMinor(s.amountMinor);
  return {
    settlement_currency: s.currency,
    settlement_amount: fromAmountMinor(s.amountMinor, s.currency),
    settlement_amount_minor: Number(s.amountMinor),
    settlement_exchange_rate: s.exchangeRate,
    ...(s.fxRateId !== undefined ? { fx_rate_id: s.fxRateId } : {}),
  };
}

/**
 * Dual-read (H4 / B-MONEY-1): prefer `amountMinor` when present; else convert
 * legacy major. Never invent payable amounts — callers supply DB/API values only.
 */
export function preferAmountMinor(args: {
  amountMinor?: bigint | number | string | null;
  amountMajor?: number | null;
  currency: CurrencyCode;
}): MoneyMinor {
  assertCurrency(args.currency);
  if (
    args.amountMinor !== null &&
    args.amountMinor !== undefined &&
    args.amountMinor !== ""
  ) {
    const amountMinor =
      typeof args.amountMinor === "bigint"
        ? args.amountMinor
        : BigInt(args.amountMinor);
    return { amountMinor, currency: args.currency };
  }
  if (args.amountMajor === null || args.amountMajor === undefined) {
    throw new Error("preferAmountMinor: need amountMinor or amountMajor");
  }
  return {
    amountMinor: toAmountMinor(args.amountMajor, args.currency),
    currency: args.currency,
  };
}

/** Display major from dual-read row (minor wins when present). */
export function displayMajorFromDual(args: {
  amountMinor?: bigint | number | string | null;
  amountMajor?: number | null;
  currency: CurrencyCode;
}): number {
  return fromAmountMinor(
    preferAmountMinor(args).amountMinor,
    args.currency,
  );
}

/**
 * Dual-read row shape for cart / invoice / PO money columns.
 * Prefer `*_minor` when dual-written; fall back to major NUMERIC.
 */
export type DualMoneyRow = {
  amountMinor?: bigint | number | string | null;
  amountMajor?: number | null;
};

/**
 * Sum dual-read money rows in minor units (H4 cart/checkout totals).
 * Never invents amounts — each row must supply minor and/or major from SoR.
 */
export function sumPreferAmountMinor(
  rows: DualMoneyRow[],
  currency: CurrencyCode,
): MoneyMinor {
  assertCurrency(currency);
  let total = 0n;
  for (const row of rows) {
    total += preferAmountMinor({
      amountMinor: row.amountMinor,
      amountMajor: row.amountMajor,
      currency,
    }).amountMinor;
  }
  return { amountMinor: total, currency };
}

/** Display major for a dual-read cart/invoice line total. */
export function displayLineTotalMajor(
  line: {
    line_total: number;
    line_total_minor?: bigint | number | string | null;
  },
  currency: CurrencyCode,
): number {
  return displayMajorFromDual({
    amountMinor: line.line_total_minor ?? null,
    amountMajor: Number(line.line_total),
    currency,
  });
}

/** Display major for a dual-read unit price. */
export function displayUnitPriceMajor(
  line: {
    unit_price: number;
    unit_price_minor?: bigint | number | string | null;
  },
  currency: CurrencyCode,
): number {
  return displayMajorFromDual({
    amountMinor: line.unit_price_minor ?? null,
    amountMajor: Number(line.unit_price),
    currency,
  });
}

/** Display major for dual-read credit_limit (B-MONEY-1). */
export function displayCreditLimitMajor(
  row: {
    credit_limit: number;
    credit_limit_minor?: bigint | number | string | null;
  },
  currency: CurrencyCode,
): number {
  return displayMajorFromDual({
    amountMinor: row.credit_limit_minor ?? null,
    amountMajor: Number(row.credit_limit),
    currency,
  });
}

/** Display major for dual-read open_balance (B-MONEY-1). */
export function displayOpenBalanceMajor(
  row: {
    open_balance: number;
    open_balance_minor?: bigint | number | string | null;
  },
  currency: CurrencyCode,
): number {
  return displayMajorFromDual({
    amountMinor: row.open_balance_minor ?? null,
    amountMajor: Number(row.open_balance),
    currency,
  });
}

/** Display major for dual-read loyalty estimated liability (B-MONEY-1). */
export function displayLoyaltyLiabilityMajor(
  row: {
    estimated_liability: number;
    estimated_liability_minor?: bigint | number | string | null;
  },
  currency: CurrencyCode,
): number {
  return displayMajorFromDual({
    amountMinor: row.estimated_liability_minor ?? null,
    amountMajor: Number(row.estimated_liability),
    currency,
  });
}

/** Display major for dual-read store credit / loyalty money_value. */
export function displayMoneyValueMajor(
  row: {
    money_value?: number | null;
    amount?: number | null;
    money_value_minor?: bigint | number | string | null;
    amount_minor?: bigint | number | string | null;
  },
  currency: CurrencyCode,
): number {
  const major = row.money_value ?? row.amount;
  return displayMajorFromDual({
    amountMinor: row.money_value_minor ?? row.amount_minor ?? null,
    amountMajor: major == null ? 0 : Number(major),
    currency,
  });
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
