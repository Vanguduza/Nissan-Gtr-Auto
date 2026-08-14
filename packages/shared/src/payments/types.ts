import {
  fromAmountMinor,
  toAmountMinor,
  type CurrencyCode,
} from "../money";

/** Payment tender and ContiPay / Paynow / EcoCash direct status enums (no secrets). */
export const PAYMENT_TENDERS = [
  "cash",
  "bank",
  "contipay",
  "paynow",
  "ecocash",
  "store_credit",
] as const;
export type PaymentTender = (typeof PAYMENT_TENDERS)[number];

export const PAYMENT_ENTRY_STATUSES = ["draft", "posted", "cancelled"] as const;
export type PaymentEntryStatus = (typeof PAYMENT_ENTRY_STATUSES)[number];

export const CONTIPAY_METHODS = ["ecocash", "visa", "zimswitch"] as const;
export type ContiPayMethod = (typeof CONTIPAY_METHODS)[number];

export const CONTIPAY_INTENT_STATUSES = [
  "pending",
  "authorized",
  "settled",
  "failed",
  "cancelled",
] as const;
export type ContiPayIntentStatus = (typeof CONTIPAY_INTENT_STATUSES)[number];

/** Paynow method channels (ZW mobile money + card as provider supports). */
export const PAYNOW_METHODS = ["ecocash", "onemoney", "innbucks", "visa"] as const;
export type PaynowMethod = (typeof PAYNOW_METHODS)[number];

export const PAYNOW_INTENT_STATUSES = [
  "pending",
  "authorized",
  "settled",
  "failed",
  "cancelled",
] as const;
export type PaynowIntentStatus = (typeof PAYNOW_INTENT_STATUSES)[number];

/** EcoCash direct C2B payer choice (not ContiPay/Paynow aggregator method). */
export const ECOCASH_PAYER_MODES = [
  "whatsapp",
  "saved",
  "other",
  "pos_entered",
  "profile",
] as const;
export type EcoCashPayerMode = (typeof ECOCASH_PAYER_MODES)[number];

export const ECOCASH_INTENT_STATUSES = [
  "pending",
  "authorized",
  "settled",
  "failed",
  "cancelled",
] as const;
export type EcoCashIntentStatus = (typeof ECOCASH_INTENT_STATUSES)[number];

/**
 * Allocate payment line (H4 cutover): prefer `amountMinor`; major is derived for
 * legacy `allocate_payment` JSONB which still reads NUMERIC `amount`.
 */
export interface PaymentAllocationInput {
  salesInvoiceId: string;
  /**
   * Preferred minor units (H4). When set, major `amount` is derived for the RPC.
   */
  amountMinor?: bigint | number;
  /**
   * @deprecated Legacy major NUMERIC. Prefer amountMinor; kept for callers that
   * have not yet cut over. Required when amountMinor is omitted.
   */
  amount?: number;
  /** Currency for minor↔major conversion (defaults USD). */
  currency?: CurrencyCode;
  exchangeRateApplied?: number;
}

function allocationMajorAmount(a: PaymentAllocationInput): number {
  const currency = a.currency ?? "USD";
  if (a.amountMinor !== undefined && a.amountMinor !== null) {
    const minor =
      typeof a.amountMinor === "bigint"
        ? a.amountMinor
        : BigInt(a.amountMinor);
    return fromAmountMinor(minor, currency);
  }
  if (a.amount === undefined || a.amount === null) {
    throw new Error(
      "PaymentAllocationInput: need amountMinor or legacy amount",
    );
  }
  return a.amount;
}

function allocationMinorNumber(a: PaymentAllocationInput): number {
  const currency = a.currency ?? "USD";
  if (a.amountMinor !== undefined && a.amountMinor !== null) {
    return typeof a.amountMinor === "bigint"
      ? Number(a.amountMinor)
      : Number(a.amountMinor);
  }
  return Number(toAmountMinor(allocationMajorAmount(a), currency));
}

export function toAllocatePaymentArgs(
  paymentEntryId: string,
  allocations: PaymentAllocationInput[],
) {
  return {
    p_payment_entry_id: paymentEntryId,
    p_allocations: allocations.map((a) => {
      const amount = allocationMajorAmount(a);
      const amount_minor = allocationMinorNumber(a);
      return {
        sales_invoice_id: a.salesInvoiceId,
        amount,
        amount_minor,
        ...(a.exchangeRateApplied != null
          ? { exchange_rate_applied: a.exchangeRateApplied }
          : {}),
      };
    }),
  } as const;
}
