/** Loyalty / points program enums and helpers (ledger-backed via CoA 2210). */
export const LOYALTY_MOVEMENTS = [
  "earn",
  "redeem",
  "expire",
  "reverse",
] as const;
export type LoyaltyMovement = (typeof LOYALTY_MOVEMENTS)[number];

/** CoA: outstanding points liability. */
export const LOYALTY_LIABILITY_ACCOUNT = "2210" as const;
/** CoA: expense when points are earned. */
export const LOYALTY_EXPENSE_ACCOUNT = "5350" as const;

export interface EarnLoyaltyPointsInput {
  customerId: string;
  points: number;
  currency?: "USD" | "ZIG";
  exchangeRateApplied?: number;
  reason?: string;
  salesInvoiceId?: string;
}

export function toEarnLoyaltyPointsArgs(input: EarnLoyaltyPointsInput) {
  return {
    p_customer_id: input.customerId,
    p_points: input.points,
    p_currency: input.currency ?? "USD",
    p_exchange_rate: input.exchangeRateApplied ?? 1,
    p_reason: input.reason ?? null,
    p_sales_invoice_id: input.salesInvoiceId ?? null,
  } as const;
}

export interface RedeemLoyaltyPointsInput {
  customerId: string;
  points: number;
  salesInvoiceId: string;
  currency?: "USD" | "ZIG";
  exchangeRateApplied?: number;
  reason?: string;
}

export function toRedeemLoyaltyPointsArgs(input: RedeemLoyaltyPointsInput) {
  return {
    p_customer_id: input.customerId,
    p_points: input.points,
    p_currency: input.currency ?? "USD",
    p_exchange_rate: input.exchangeRateApplied ?? 1,
    p_sales_invoice_id: input.salesInvoiceId,
    p_reason: input.reason ?? null,
  } as const;
}
