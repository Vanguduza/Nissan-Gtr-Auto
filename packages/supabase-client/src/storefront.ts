import type { Database } from "./database.types";

/** RPC name for storefront order detail. */
export const GET_CUSTOMER_ORDER_RPC = "get_customer_order" as const;

export function getCustomerOrderArgs(invoiceId: string) {
  return { p_invoice_id: invoiceId } as const;
}

/**
 * Parsed shape of `get_customer_order` JSONB.
 * Use `active_delivery_job_id` with `get_delivery_track_point({ p_delivery_job_id })`
 * when the authenticated customer owns the invoice (no share token needed).
 */
export type CustomerOrderPayload = {
  invoice_id: string;
  document_number: string | null;
  doc_type: string;
  status: string;
  fulfillment_mode: Database["public"]["Enums"]["fulfillment_mode"] | null;
  currency: Database["public"]["Enums"]["currency_code"];
  exchange_rate_applied: number;
  subtotal: number;
  total: number;
  amount_paid: number;
  amount_open: number;
  cart_id: string | null;
  posted_at: string | null;
  pick_list_status: Database["public"]["Enums"]["pick_list_status"] | null;
  delivery_note_status: Database["public"]["Enums"]["delivery_note_status"] | null;
  /** Non-terminal job (pending|dispatched); prefers dispatched. Null when none. */
  active_delivery_job_id: string | null;
};
