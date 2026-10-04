import type { SupabaseClient } from "@gtr/supabase-client";
import { requireSession, type StorefrontResult } from "@/lib/customer-storefront";

export { requireSession };

/** A driver's request to leave the unpaid part of a cash/card-on-delivery invoice on account. */
export type DeliveryBalanceApproval = {
  id: string;
  deliveryJobId: string;
  jobNumber: string | null;
  invoiceNumber: string | null;
  customerName: string | null;
  invoiceTotal: number;
  amountPaid: number;
  amount: number;
  currency: string;
  reason: string;
  /** pending | auto_approved | approved | refused | cancelled */
  status: string;
  /** credit_limit (approved by the trade account) | back_office */
  basis: string;
  creditLimit: number | null;
  exposure: number | null;
  requestedByName: string | null;
  decidedByName: string | null;
  decidedAt: string | null;
  decisionNote: string | null;
  createdAt: string;
};

function rpc(client: SupabaseClient, fn: string, args: Record<string, unknown>) {
  return (client as unknown as {
    rpc: (name: string, params: Record<string, unknown>) => PromiseLike<{ data: unknown; error: { message: string } | null }>;
  }).rpc(fn, args);
}

const num = (v: unknown): number => (v == null ? 0 : Number(v));
const numOrNull = (v: unknown): number | null => (v == null ? null : Number(v));
const str = (v: unknown): string | null => (typeof v === "string" && v ? v : null);

function fromRow(r: Record<string, unknown>): DeliveryBalanceApproval {
  return {
    id: String(r.id),
    deliveryJobId: String(r.delivery_job_id),
    jobNumber: str(r.job_number),
    invoiceNumber: str(r.invoice_number),
    customerName: str(r.customer_name),
    invoiceTotal: num(r.invoice_total),
    amountPaid: num(r.amount_paid),
    amount: num(r.amount),
    currency: String(r.currency ?? "USD"),
    reason: String(r.reason ?? ""),
    status: String(r.status ?? "pending"),
    basis: String(r.basis ?? "back_office"),
    creditLimit: numOrNull(r.credit_limit),
    exposure: numOrNull(r.exposure),
    requestedByName: str(r.requested_by_name),
    decidedByName: str(r.decided_by_name),
    decidedAt: str(r.decided_at),
    decisionNote: str(r.decision_note),
    createdAt: String(r.created_at ?? ""),
  };
}

export async function listDeliveryBalanceApprovals(
  client: SupabaseClient,
  status: string | null,
): Promise<StorefrontResult<DeliveryBalanceApproval[]>> {
  const { data, error } = await rpc(client, "list_delivery_balance_approvals", { p_status: status, p_limit: 100 });
  if (error) return { ok: false, error: error.message };
  return { ok: true, data: ((data as Record<string, unknown>[] | null) ?? []).map(fromRow) };
}

export async function decideDeliveryBalance(
  client: SupabaseClient,
  approvalId: string,
  approve: boolean,
  note: string | null,
): Promise<StorefrontResult<DeliveryBalanceApproval>> {
  const { data, error } = await rpc(client, "decide_delivery_balance_on_account", {
    p_approval_id: approvalId,
    p_approve: approve,
    p_note: note?.trim() || null,
  });
  if (error) return { ok: false, error: error.message };
  return { ok: true, data: fromRow(data as Record<string, unknown>) };
}

/** New and changed requests, so the queue updates while a driver waits at the door. */
export function deliveryBalanceChannel(client: SupabaseClient, onChange: () => void) {
  return client
    .channel("delivery-balance-approvals")
    .on("postgres_changes", { event: "*", schema: "public", table: "delivery_balance_approvals" }, () => onChange())
    .subscribe();
}
