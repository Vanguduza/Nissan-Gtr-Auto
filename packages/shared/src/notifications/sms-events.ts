/** Canonical manager-SMS / domain event codes (must match sms_event_catalog). */
export const SMS_EVENT_CODES = [
  "order_received",
  "order_completed",
  "order_cancelled",
  "order_on_hold",
  "large_order",
  "return_initiated",
  "return_completed",
  "payment_received",
  "payment_failed",
  "payment_partial",
  "refund_issued",
  "ar_overdue",
  "stock_received",
  "low_stock",
  "stockout",
  "transfer_pending_approval",
  "transfer_completed",
  "transfer_rejected",
  "quarantine_received",
  "serial_moved",
  "stock_reconciliation_posted",
  "stock_reconciliation_pending_approval",
  "stock_reconciliation_cancelled",
  "po_created",
  "po_approved",
  "po_received",
  "po_overdue",
  "supplier_mismatch",
  "delivery_dispatched",
  "delivery_completed",
  "delivery_failed",
  "delivery_delayed",
  "journal_post_rejected",
  "day_close_completed",
  "cash_drawer_variance",
  "staff_no_show",
  "payroll_run_ready",
] as const;

export type SmsEventCode = (typeof SMS_EVENT_CODES)[number];

export function isSmsEventCode(value: string): value is SmsEventCode {
  return (SMS_EVENT_CODES as readonly string[]).includes(value);
}

export interface EmitDomainEventInput {
  eventCode: SmsEventCode;
  /** Stable id for idempotency, e.g. `order:uuid` or `payment:uuid`. */
  dedupeKey: string;
  payload?: Record<string, unknown>;
  messageBody?: string;
}

/**
 * Call after domain writes. Uses RPC `emit_domain_event`.
 * Enqueues SMS for opted-in managers; safe before gateway exists.
 */
export function emitDomainEventArgs(input: EmitDomainEventInput) {
  if (!isSmsEventCode(input.eventCode)) {
    throw new Error(`Invalid event code: ${input.eventCode}`);
  }
  if (!input.dedupeKey.trim()) {
    throw new Error("dedupeKey is required");
  }
  return {
    p_event_code: input.eventCode,
    p_dedupe_key: input.dedupeKey,
    p_payload: input.payload ?? {},
    p_message_body: input.messageBody ?? null,
  } as const;
}
