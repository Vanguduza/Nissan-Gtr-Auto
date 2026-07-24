/** Payment tender and ContiPay / Paynow status enums (no secrets). */
export const PAYMENT_TENDERS = [
  "cash",
  "bank",
  "contipay",
  "paynow",
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

export interface PaymentAllocationInput {
  salesInvoiceId: string;
  amount: number;
  exchangeRateApplied?: number;
}

export function toAllocatePaymentArgs(
  paymentEntryId: string,
  allocations: PaymentAllocationInput[],
) {
  return {
    p_payment_entry_id: paymentEntryId,
    p_allocations: allocations.map((a) => ({
      sales_invoice_id: a.salesInvoiceId,
      amount: a.amount,
      ...(a.exchangeRateApplied != null
        ? { exchange_rate_applied: a.exchangeRateApplied }
        : {}),
    })),
  } as const;
}
