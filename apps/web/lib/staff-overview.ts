import type { SupabaseClient } from "@gtr/supabase-client";
import type { StorefrontResult } from "@/lib/customer-storefront";

export type OverviewPermissions = {
  commercial: boolean;
  finance: boolean;
  customers: boolean;
  inventory: boolean;
  logistics: boolean;
};

export type OverviewCurrencyBucket = {
  currency: string;
  invoice_count?: number;
  revenue?: number;
  subtotal?: number;
  average_order_value?: number;
};

export type OverviewSalesSummary = {
  order_count?: number;
  by_currency?: OverviewCurrencyBucket[];
  average_order_value?: OverviewCurrencyBucket[];
};

export type OverviewTrendPoint = {
  date: string;
  revenue_usd: number;
  orders: number;
};

export type OverviewPaymentMix = {
  tender: string;
  payments: number;
  amount_usd: number;
};

export type OverviewTopProduct = {
  stock_item_id: string;
  oem: string;
  description: string | null;
  qty: number;
  revenue_usd: number;
  image_path: string | null;
};

export type OverviewCustomerSegment = {
  segment: string;
  customer_count: number;
};

export type OverviewRecentOrder = {
  id: string;
  document_number: string | null;
  customer: string;
  total: number;
  currency: string;
  amount_paid: number;
  fulfillment_mode: string;
  delivery_payment_method: string | null;
  state: string;
  posted_at: string;
};

export type OverviewInventory = {
  total_skus?: number;
  out_of_stock?: number;
  low_stock?: number;
  in_stock?: number;
  quarantine?: number;
  on_hand_qty?: number;
  quarantine_qty?: number;
};

export type OverviewDelivery = {
  open_jobs?: number;
  pending?: number;
  dispatched?: number;
  completed?: number;
  failed?: number;
};

export type StaffOverviewDashboard = {
  generated_at: string;
  timezone: string;
  period: {
    from: string;
    to: string;
    previous_from: string;
    previous_to: string;
  };
  staff: { name: string | null; roles: string[] };
  permissions: OverviewPermissions;
  summary: {
    sales: OverviewSalesSummary | null;
    previous_sales: OverviewSalesSummary | null;
    customers: number | null;
    previous_customers: number | null;
    new_customers: number | null;
    net_margin_pct: number | null;
    previous_net_margin_pct: number | null;
  };
  sales_trend: OverviewTrendPoint[];
  payment_mix: OverviewPaymentMix[];
  top_products: OverviewTopProduct[];
  customer_segments: OverviewCustomerSegment[];
  recent_orders: OverviewRecentOrder[];
  inventory: OverviewInventory;
  delivery: OverviewDelivery;
};

export type StaffOverviewSearchHit = {
  kind: "part" | "customer" | "order" | "delivery";
  id: string;
  label: string;
  subtitle: string;
  destination: string;
  filter: string;
};

async function overviewRpc(
  client: SupabaseClient,
  fn: string,
  args?: Record<string, unknown>,
): Promise<{ data: unknown; error: { message: string } | null }> {
  return (
    client as unknown as {
      rpc: (
        name: string,
        params?: Record<string, unknown>,
      ) => PromiseLike<{ data: unknown; error: { message: string } | null }>;
    }
  ).rpc(fn, args);
}

function asDashboard(raw: unknown): StaffOverviewDashboard | null {
  if (!raw || typeof raw !== "object" || Array.isArray(raw)) return null;
  const d = raw as StaffOverviewDashboard;
  if (!d.period || !d.permissions || !d.summary || !Array.isArray(d.sales_trend)) {
    return null;
  }
  return d;
}

export async function fetchStaffOverview(
  client: SupabaseClient,
  args: { from: string; to: string },
): Promise<StorefrontResult<StaffOverviewDashboard>> {
  const { data, error } = await overviewRpc(client, "get_staff_overview_dashboard", {
    p_from: args.from,
    p_to: args.to,
  });
  if (error) return { ok: false, error: error.message };
  const parsed = asDashboard(data);
  return parsed
    ? { ok: true, data: parsed }
    : { ok: false, error: "Overview returned an invalid payload." };
}

export async function searchStaffOverview(
  client: SupabaseClient,
  query: string,
): Promise<StorefrontResult<StaffOverviewSearchHit[]>> {
  const q = query.trim();
  if (q.length < 2) return { ok: true, data: [] };
  const { data, error } = await overviewRpc(client, "search_staff_overview", {
    p_query: q,
    p_limit: 12,
  });
  if (error) return { ok: false, error: error.message };
  if (!Array.isArray(data)) return { ok: true, data: [] };
  return { ok: true, data: data as StaffOverviewSearchHit[] };
}

export function overviewDateBounds(from: string, to: string): StorefrontResult<{ from: string; to: string }> {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(from) || !/^\d{4}-\d{2}-\d{2}$/.test(to)) {
    return { ok: false, error: "Choose a valid date range." };
  }
  const start = new Date(`${from}T00:00:00+02:00`);
  const end = new Date(`${to}T00:00:00+02:00`);
  end.setDate(end.getDate() + 1);
  if (Number.isNaN(start.getTime()) || Number.isNaN(end.getTime()) || end <= start) {
    return { ok: false, error: "End date must be on or after start date." };
  }
  return { ok: true, data: { from: start.toISOString(), to: end.toISOString() } };
}

export function overviewDefaultDates(now = new Date()): { from: string; to: string } {
  const to = new Date(now);
  const from = new Date(now);
  from.setDate(from.getDate() - 29);
  const date = (d: Date) => d.toLocaleDateString("en-CA", { timeZone: "Africa/Harare" });
  return { from: date(from), to: date(to) };
}

export function percentChange(current: number | null | undefined, previous: number | null | undefined): number | null {
  if (current == null || previous == null || !Number.isFinite(current) || !Number.isFinite(previous)) return null;
  if (previous === 0) return current === 0 ? 0 : null;
  return ((current - previous) / Math.abs(previous)) * 100;
}

export function moneyUsd(value: number | null | undefined): string {
  if (value == null || !Number.isFinite(value)) return "—";
  return new Intl.NumberFormat("en-US", {
    style: "currency",
    currency: "USD",
    maximumFractionDigits: 2,
  }).format(value);
}
