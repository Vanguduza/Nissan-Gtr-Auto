import type { SupabaseClient } from "@gtr/supabase-client";
import { requireSession, type StorefrontResult } from "@/lib/customer-storefront";

export { requireSession };

/** Something waiting for a decision the signed-in person may take (`list_my_approvals`). */
export type ApprovalItem = {
  kind: string;
  ref: string;
  title: string;
  detail: string;
  amount: number | null;
  currency: string | null;
  waitingSince: string;
  /** Someone is waiting on it now (a driver at the door, a customer possibly charged). */
  urgent: boolean;
  /** Where it is decided. */
  href: string;
};

/** An item that has waited too long (sent by the 5-minute sweep). */
export type ApprovalAlert = { id: string; title: string; body: string; href: string | null; createdAt: string };

export type ApprovalsInbox = { items: ApprovalItem[]; alerts: ApprovalAlert[] };

function rpc(client: SupabaseClient, fn: string, args: Record<string, unknown>) {
  return (client as unknown as {
    rpc: (name: string, params: Record<string, unknown>) => PromiseLike<{ data: unknown; error: { message: string } | null }>;
  }).rpc(fn, args);
}

export async function listMyApprovals(client: SupabaseClient): Promise<StorefrontResult<ApprovalsInbox>> {
  const { data, error } = await rpc(client, "list_my_approvals", {});
  if (error) return { ok: false, error: error.message };
  const o = (data ?? {}) as { items?: Record<string, unknown>[]; alerts?: Record<string, unknown>[] };
  return {
    ok: true,
    data: {
      items: (o.items ?? []).map((r) => ({
        kind: String(r.kind),
        ref: String(r.ref),
        title: String(r.title ?? ""),
        detail: String(r.detail ?? ""),
        amount: r.amount == null ? null : Number(r.amount),
        currency: r.currency == null ? null : String(r.currency),
        waitingSince: String(r.waiting_since ?? ""),
        urgent: Boolean(r.urgent),
        href: String(r.href ?? "/staff"),
      })),
      alerts: (o.alerts ?? []).map((a) => ({
        id: String(a.id),
        title: String(a.title ?? ""),
        body: String(a.body ?? ""),
        href: a.href == null ? null : String(a.href),
        createdAt: String(a.created_at ?? ""),
      })),
    },
  };
}

export async function dismissApprovalAlert(client: SupabaseClient, id: string): Promise<StorefrontResult<true>> {
  const { error } = await rpc(client, "dismiss_approval_alert", { p_id: id });
  return error ? { ok: false, error: error.message } : { ok: true, data: true };
}

/** "12 min", "3 h", "2 days" since [iso]. */
export function waitedFor(iso: string, now: number = Date.now()): string {
  const t = Date.parse(iso);
  if (Number.isNaN(t)) return "";
  const m = Math.max(0, Math.floor((now - t) / 60000));
  if (m < 60) return `${m} min`;
  if (m < 48 * 60) return `${Math.floor(m / 60)} h`;
  return `${Math.floor(m / 1440)} days`;
}

/** Labels for the inbox groups. */
export const APPROVAL_KIND_LABEL: Record<string, string> = {
  delivery_balance: "Balance on account",
  card_unresolved: "Card payment to reconcile",
  driver_cash_count: "Driver cash to count",
  driver_cash_difference: "Driver cash difference",
  till_variance: "Till difference",
  return: "Return",
  split_refund: "Part-payment refund",
  warranty: "Warranty claim",
  requisition: "Requisition",
  transfer_send: "Transfer to send",
  stock_transfer: "Transfer to receive",
};
