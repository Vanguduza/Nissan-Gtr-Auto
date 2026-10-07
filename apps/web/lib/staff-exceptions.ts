import type { SupabaseClient } from "@gtr/supabase-client";
import { requireSession, type StorefrontResult } from "@/lib/customer-storefront";

export { requireSession };

export type ExceptionItem = {
  at: string;
  kind: string;
  severity: "high" | "medium" | "low";
  personId: string | null;
  person: string | null;
  approvedBy: string | null;
  warehouse: string | null;
  currency: string | null;
  amount: number | null;
  detail: string;
  href: string;
};
export type ExceptionKindTotal = { kind: string; count: number; currency: string | null; amount: number };
export type ExceptionPerson = { personId: string; person: string | null; count: number; high: number; kinds: Record<string, number> };
export type ExceptionReport = {
  from: string;
  to: string;
  warehouseName: string | null;
  items: ExceptionItem[];
  byKind: ExceptionKindTotal[];
  byPerson: ExceptionPerson[];
};

function rpc(client: SupabaseClient, fn: string, args: Record<string, unknown>) {
  return (client as unknown as {
    rpc: (name: string, params: Record<string, unknown>) => PromiseLike<{ data: unknown; error: { message: string } | null }>;
  }).rpc(fn, args);
}

type Row = Record<string, unknown>;
const rows = (v: unknown): Row[] => (Array.isArray(v) ? (v as Row[]) : []);
const s = (v: unknown): string | null => (typeof v === "string" && v ? v : null);

export async function getExceptionReport(
  client: SupabaseClient,
  from: string,
  to: string,
  warehouseId: string | null,
): Promise<StorefrontResult<ExceptionReport>> {
  const { data, error } = await rpc(client, "get_exception_report", { p_from: from, p_to: to, p_warehouse_id: warehouseId });
  if (error) return { ok: false, error: error.message };
  const d = (data ?? {}) as Row;
  return {
    ok: true,
    data: {
      from: String(d.from ?? from),
      to: String(d.to ?? to),
      warehouseName: s(d.warehouse_name),
      items: rows(d.items).map((r) => ({
        at: String(r.at ?? ""),
        kind: String(r.kind),
        severity: (String(r.severity) as ExceptionItem["severity"]) ?? "low",
        personId: s(r.person_id),
        person: s(r.person),
        approvedBy: s(r.approved_by),
        warehouse: s(r.warehouse),
        currency: s(r.currency),
        amount: r.amount == null ? null : Number(r.amount),
        detail: String(r.detail ?? ""),
        href: String(r.href ?? "/staff"),
      })),
      byKind: rows(d.by_kind).map((r) => ({ kind: String(r.kind), count: Number(r.count ?? 0), currency: s(r.currency), amount: Number(r.amount ?? 0) })),
      byPerson: rows(d.by_person).map((r) => ({
        personId: String(r.person_id),
        person: s(r.person),
        count: Number(r.count ?? 0),
        high: Number(r.high ?? 0),
        kinds: (r.kinds ?? {}) as Record<string, number>,
      })),
    },
  };
}

export const EXCEPTION_LABEL: Record<string, string> = {
  cart_voided: "Sale voided",
  cart_line_removed: "Part removed after ringing up",
  discount_applied: "Discount",
  price_override: "Price changed",
  paid_order_repaired: "Paid order repaired",
  return: "Return",
  till_short: "Till short",
  till_over: "Till over",
  driver_cash_short: "Driver cash short",
  driver_cash_over: "Driver cash over",
  card_declined: "Card declined",
  card_cancelled: "Card cancelled",
  card_unknown: "Card: no answer",
  card_failed: "Card failed",
  below_cost: "Sold below cost",
  after_hours: "Sale after hours",
  delivery_failed: "Delivery failed",
  balance_on_account: "Balance left on account",
  stock_count_difference: "Stock count difference",
};
