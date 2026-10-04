import type { SupabaseClient } from "@gtr/supabase-client";
import type { StorefrontResult } from "@/lib/customer-storefront";

/** What a suspension was for (`customer_suspension_findings`). */
export type SuspensionFinding = {
  rule: "on_account_overdue" | "invoice_overdue" | "refused_cod" | string;
  label: string;
  amount: number;
  currency: string;
  since: string | null;
};

export type CustomerSuspension = {
  id: string;
  customerId: string;
  customerName: string | null;
  /** active | lifted */
  status: string;
  /** automatic | manual */
  source: string;
  reason: string;
  findings: SuspensionFinding[];
  suspendedByName: string | null;
  suspendedAt: string;
  liftedByName: string | null;
  liftedAt: string | null;
  liftReason: string | null;
  owing: number;
};

function rpc(client: SupabaseClient, fn: string, args: Record<string, unknown>) {
  return (client as unknown as {
    rpc: (name: string, params: Record<string, unknown>) => PromiseLike<{ data: unknown; error: { message: string } | null }>;
  }).rpc(fn, args);
}

const str = (v: unknown): string | null => (typeof v === "string" && v ? v : null);

function fromRow(r: Record<string, unknown>): CustomerSuspension {
  return {
    id: String(r.id),
    customerId: String(r.customer_id),
    customerName: str(r.customer_name),
    status: String(r.status ?? "active"),
    source: String(r.source ?? "automatic"),
    reason: String(r.reason ?? ""),
    findings: ((r.findings as Record<string, unknown>[] | null) ?? []).map((f) => ({
      rule: String(f.rule ?? ""),
      label: String(f.label ?? ""),
      amount: Number(f.amount ?? 0),
      currency: String(f.currency ?? "USD"),
      since: str(f.since),
    })),
    suspendedByName: str(r.suspended_by_name),
    suspendedAt: String(r.suspended_at ?? ""),
    liftedByName: str(r.lifted_by_name),
    liftedAt: str(r.lifted_at),
    liftReason: str(r.lift_reason),
    owing: Number(r.owing ?? 0),
  };
}

export async function listSuspensions(client: SupabaseClient, status: string | null): Promise<StorefrontResult<CustomerSuspension[]>> {
  const { data, error } = await rpc(client, "list_customer_suspensions", { p_status: status, p_limit: 200 });
  if (error) return { ok: false, error: error.message };
  return { ok: true, data: ((data as Record<string, unknown>[] | null) ?? []).map(fromRow) };
}

/** Admin, finance or a manager suspends a customer by hand. */
export async function suspendCustomer(client: SupabaseClient, customerId: string, reason: string): Promise<StorefrontResult<CustomerSuspension>> {
  const { data, error } = await rpc(client, "suspend_customer", { p_customer_id: customerId, p_reason: reason.trim() });
  if (error) return { ok: false, error: error.message };
  return { ok: true, data: fromRow(data as Record<string, unknown>) };
}

/** Manager consent: only a POS approver (manager) or admin may lift. */
export async function liftSuspension(client: SupabaseClient, suspensionId: string, reason: string): Promise<StorefrontResult<CustomerSuspension>> {
  const { data, error } = await rpc(client, "lift_customer_suspension", { p_suspension_id: suspensionId, p_reason: reason.trim() });
  if (error) return { ok: false, error: error.message };
  return { ok: true, data: fromRow(data as Record<string, unknown>) };
}
