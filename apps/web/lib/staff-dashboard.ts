import type { SupabaseClient } from "@gtr/supabase-client";
import { requireSession, type StorefrontResult } from "@/lib/customer-storefront";

export { requireSession };

/** Money is always per currency: the dashboard never adds USD to ZiG. */
export type SalesRow = {
  currency: string;
  invoices: number;
  gross: number;
  returns: number;
  net: number;
  cost: number;
  margin: number;
  marginPct: number | null;
  averageSale: number | null;
  lastWeekNet: number;
};
export type Amount = { currency: string; amount: number };
export type ChannelRow = { channel: string; currency: string; invoices: number; total: number };
export type PaymentRow = { tender: string; currency: string; count: number; amount: number };
export type HourRow = { hour: number; currency: string; total: number };
export type TopPart = { oemPartNumber: string; description: string | null; currency: string; qty: number; total: number };
export type TillRow = {
  id: string;
  warehouse: string | null;
  cashier: string | null;
  status: string;
  currency: string;
  openingFloat: number;
  expectedCash: number | null;
  countedCash: number | null;
  variance: number | null;
  approved: boolean;
  openedAt: string;
  closedAt: string | null;
};
export type LowStock = { oemPartNumber: string; description: string | null; warehouse: string; onHand: number; reorderPoint: number; reorderQty: number | null };

export type DailyDashboard = {
  date: string;
  warehouseName: string | null;
  generatedAt: string;
  sales: SalesRow[];
  channels: ChannelRow[];
  payments: PaymentRow[];
  unpaidToday: (Amount & { invoices: number })[];
  hourly: HourRow[];
  topParts: TopPart[];
  tills: TillRow[];
  deliveries: { dispatched: number; completed: number; failed: number; onTheRoad: number; waitingForDriver: number };
  cashOnDelivery: (Amount & { collections: number })[];
  driverCashHeld: (Amount & { drivers: number; oldestAt: string | null })[];
  owed: (Amount & { overdue30: number; customers: number })[];
  suspendedCustomers: number;
  suspendedToday: number;
  lowStock: LowStock[];
  lowStockCount: number;
  backordersWaiting: number;
  approvalsWaiting: number;
};

function rpc(client: SupabaseClient, fn: string, args: Record<string, unknown>) {
  return (client as unknown as {
    rpc: (name: string, params: Record<string, unknown>) => PromiseLike<{ data: unknown; error: { message: string } | null }>;
  }).rpc(fn, args);
}

type Row = Record<string, unknown>;
const n = (v: unknown): number => (v == null ? 0 : Number(v));
const nOrNull = (v: unknown): number | null => (v == null ? null : Number(v));
const s = (v: unknown): string | null => (typeof v === "string" && v ? v : null);
const rows = (v: unknown): Row[] => (Array.isArray(v) ? (v as Row[]) : []);

export async function getDailyDashboard(
  client: SupabaseClient,
  date: string | null,
  warehouseId: string | null,
): Promise<StorefrontResult<DailyDashboard>> {
  const { data, error } = await rpc(client, "get_daily_dashboard", { p_date: date, p_warehouse_id: warehouseId });
  if (error) return { ok: false, error: error.message };
  const d = (data ?? {}) as Row;
  const del = (d.deliveries ?? {}) as Row;
  return {
    ok: true,
    data: {
      date: String(d.date ?? date ?? ""),
      warehouseName: s(d.warehouse_name),
      generatedAt: String(d.generated_at ?? ""),
      sales: rows(d.sales).map((r) => ({
        currency: String(r.currency),
        invoices: n(r.invoices),
        gross: n(r.gross),
        returns: n(r.returns),
        net: n(r.net),
        cost: n(r.cost),
        margin: n(r.margin),
        marginPct: nOrNull(r.margin_pct),
        averageSale: nOrNull(r.average_sale),
        lastWeekNet: n(r.last_week_net),
      })),
      channels: rows(d.channels).map((r) => ({ channel: String(r.channel), currency: String(r.currency), invoices: n(r.invoices), total: n(r.total) })),
      payments: rows(d.payments).map((r) => ({ tender: String(r.tender), currency: String(r.currency), count: n(r.count), amount: n(r.amount) })),
      unpaidToday: rows(d.unpaid_today).map((r) => ({ currency: String(r.currency), amount: n(r.amount), invoices: n(r.invoices) })),
      hourly: rows(d.hourly).map((r) => ({ hour: n(r.hour), currency: String(r.currency), total: n(r.total) })),
      topParts: rows(d.top_parts).map((r) => ({
        oemPartNumber: String(r.oem_part_number ?? ""),
        description: s(r.description),
        currency: String(r.currency),
        qty: n(r.qty),
        total: n(r.total),
      })),
      tills: rows(d.tills).map((r) => ({
        id: String(r.id),
        warehouse: s(r.warehouse),
        cashier: s(r.cashier),
        status: String(r.status ?? ""),
        currency: String(r.currency ?? "USD"),
        openingFloat: n(r.opening_float),
        expectedCash: nOrNull(r.expected_cash),
        countedCash: nOrNull(r.counted_cash),
        variance: nOrNull(r.variance),
        approved: Boolean(r.approved),
        openedAt: String(r.opened_at ?? ""),
        closedAt: s(r.closed_at),
      })),
      deliveries: {
        dispatched: n(del.dispatched),
        completed: n(del.completed),
        failed: n(del.failed),
        onTheRoad: n(del.on_the_road),
        waitingForDriver: n(del.waiting_for_driver),
      },
      cashOnDelivery: rows(d.cash_on_delivery).map((r) => ({ currency: String(r.currency), amount: n(r.collected), collections: n(r.collections) })),
      driverCashHeld: rows(d.driver_cash_held).map((r) => ({ currency: String(r.currency), amount: n(r.amount), drivers: n(r.drivers), oldestAt: s(r.oldest_at) })),
      owed: rows(d.owed).map((r) => ({ currency: String(r.currency), amount: n(r.amount), overdue30: n(r.overdue_30), customers: n(r.customers) })),
      suspendedCustomers: n(d.suspended_customers),
      suspendedToday: n(d.suspended_today),
      lowStock: rows(d.low_stock).map((r) => ({
        oemPartNumber: String(r.oem_part_number ?? ""),
        description: s(r.description),
        warehouse: String(r.warehouse ?? ""),
        onHand: n(r.on_hand),
        reorderPoint: n(r.reorder_point),
        reorderQty: nOrNull(r.reorder_qty),
      })),
      lowStockCount: n(d.low_stock_count),
      backordersWaiting: n(d.backorders_waiting),
      approvalsWaiting: n(d.approvals_waiting),
    },
  };
}

export function money(amount: number, currency: string): string {
  return `${currency} ${amount.toLocaleString("en-US", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
}

/** "+12% on last week", or null when last week had nothing to compare with. */
export function vsLastWeek(now: number, lastWeek: number): string | null {
  if (lastWeek <= 0) return null;
  const pct = Math.round(((now - lastWeek) / lastWeek) * 100);
  return `${pct >= 0 ? "+" : ""}${pct}% on last week`;
}

export const CHANNEL_LABEL: Record<string, string> = {
  counter: "Counter",
  delivery: "Delivery",
  online_pickup: "Online, collected",
};

export const TENDER_LABEL: Record<string, string> = {
  cash: "Cash",
  bank: "Bank transfer",
  card_terminal: "Card",
  ecocash: "EcoCash",
  paynow: "Paynow",
  contipay: "ContiPay",
  store_credit: "Store credit",
};

/** Today's date in Harare (the business day), as YYYY-MM-DD. */
export function harareToday(now: Date = new Date()): string {
  return new Intl.DateTimeFormat("en-CA", { timeZone: "Africa/Harare" }).format(now);
}
