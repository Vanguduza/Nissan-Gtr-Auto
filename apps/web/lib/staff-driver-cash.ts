import type { SupabaseClient } from "@gtr/supabase-client";
import { requireSession, type StorefrontResult } from "@/lib/customer-storefront";

export { requireSession };

/** Cash a driver collected on delivery and has not handed in yet, per currency. */
export type DriverCashHolding = {
  driverUserId: string;
  driverName: string | null;
  currency: string;
  amount: number;
  count: number;
  oldestAt: string | null;
};

export type DriverCashHandin = {
  id: string;
  documentNumber: string | null;
  driverUserId: string;
  driverName: string | null;
  currency: string;
  expectedAmount: number;
  declaredAmount: number;
  receivedAmount: number | null;
  variance: number | null;
  collectionCount: number;
  /** submitted | received | variance_pending | approved */
  status: string;
  reasonCode: string | null;
  driverNotes: string | null;
  receiverNotes: string | null;
  approverNotes: string | null;
  submittedAt: string;
  receivedByName: string | null;
  receivedAt: string | null;
  approvedByName: string | null;
  approvedAt: string | null;
};

export type DriverCashBoard = { holding: DriverCashHolding[]; handins: DriverCashHandin[] };

export type VarianceReason = { code: string; label: string };

function rpc(client: SupabaseClient, fn: string, args: Record<string, unknown>) {
  return (client as unknown as {
    rpc: (name: string, params: Record<string, unknown>) => PromiseLike<{ data: unknown; error: { message: string } | null }>;
  }).rpc(fn, args);
}

const num = (v: unknown): number => (v == null ? 0 : Number(v));
const numOrNull = (v: unknown): number | null => (v == null ? null : Number(v));
const str = (v: unknown): string | null => (typeof v === "string" && v ? v : null);

function handinFrom(r: Record<string, unknown>): DriverCashHandin {
  return {
    id: String(r.id),
    documentNumber: str(r.document_number),
    driverUserId: String(r.driver_user_id ?? ""),
    driverName: str(r.driver_name),
    currency: String(r.currency ?? "USD"),
    expectedAmount: num(r.expected_amount),
    declaredAmount: num(r.declared_amount),
    receivedAmount: numOrNull(r.received_amount),
    variance: numOrNull(r.variance),
    collectionCount: num(r.collection_count),
    status: String(r.status ?? "submitted"),
    reasonCode: str(r.reason_code),
    driverNotes: str(r.driver_notes),
    receiverNotes: str(r.receiver_notes),
    approverNotes: str(r.approver_notes),
    submittedAt: String(r.submitted_at ?? ""),
    receivedByName: str(r.received_by_name),
    receivedAt: str(r.received_at),
    approvedByName: str(r.approved_by_name),
    approvedAt: str(r.approved_at),
  };
}

export async function listDriverCash(client: SupabaseClient, status: string | null): Promise<StorefrontResult<DriverCashBoard>> {
  const { data, error } = await rpc(client, "list_driver_cash", { p_status: status, p_limit: 100 });
  if (error) return { ok: false, error: error.message };
  const o = (data ?? {}) as { holding?: Record<string, unknown>[]; handins?: Record<string, unknown>[] };
  return {
    ok: true,
    data: {
      holding: (o.holding ?? []).map((h) => ({
        driverUserId: String(h.driver_user_id),
        driverName: str(h.driver_name),
        currency: String(h.currency ?? "USD"),
        amount: num(h.amount),
        count: num(h.count),
        oldestAt: str(h.oldest_at),
      })),
      handins: (o.handins ?? []).map(handinFrom),
    },
  };
}

export async function listVarianceReasons(client: SupabaseClient): Promise<StorefrontResult<VarianceReason[]>> {
  const { data, error } = await rpc(client, "list_pos_approval_reasons", { p_action: "driver_cash_variance" });
  if (error) return { ok: false, error: error.message };
  return { ok: true, data: ((data ?? []) as { code: string; label: string }[]).map((r) => ({ code: r.code, label: r.label })) };
}

/** Count the cash on receipt; a difference needs a reason and goes to a manager. */
export async function receiveDriverCash(
  client: SupabaseClient,
  handinId: string,
  counted: number,
  reasonCode: string | null,
  notes: string | null,
): Promise<StorefrontResult<DriverCashHandin>> {
  const { data, error } = await rpc(client, "receive_driver_cash_handin", {
    p_handin_id: handinId,
    p_counted_amount: counted,
    p_reason_code: reasonCode || null,
    p_notes: notes?.trim() || null,
  });
  if (error) return { ok: false, error: error.message };
  return { ok: true, data: handinFrom(data as Record<string, unknown>) };
}

/** A manager (not whoever counted) signs off a difference. */
export async function approveDriverCashVariance(
  client: SupabaseClient,
  handinId: string,
  reasonCode: string,
  notes: string | null,
): Promise<StorefrontResult<DriverCashHandin>> {
  const { data, error } = await rpc(client, "approve_driver_cash_variance", {
    p_handin_id: handinId,
    p_reason_code: reasonCode,
    p_notes: notes?.trim() || null,
  });
  if (error) return { ok: false, error: error.message };
  return { ok: true, data: handinFrom(data as Record<string, unknown>) };
}

/** Hours since the oldest collection still held (for "held 26 h"). */
export function hoursHeld(oldestAt: string | null, now: number = Date.now()): number | null {
  if (!oldestAt) return null;
  const t = Date.parse(oldestAt);
  return Number.isNaN(t) ? null : Math.max(0, Math.floor((now - t) / 3_600_000));
}
