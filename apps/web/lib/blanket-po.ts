import type { Database, SupabaseClient } from "@gtr/supabase-client";
import type { StorefrontResult } from "@/lib/customer-storefront";
import {
  loadSuppliers,
  loadWarehouses,
  requireSession,
  searchStockItems,
  type CurrencyCode,
  type StockItemOption,
  type SupplierOption,
  type WarehouseOption,
} from "@/lib/rfq-portal";

export { requireSession, loadSuppliers, loadWarehouses, searchStockItems };
export type { CurrencyCode, StockItemOption, SupplierOption, WarehouseOption };

export type PurchaseOrderRow =
  Database["public"]["Tables"]["purchase_orders"]["Row"];

export type BlanketLineRow =
  Database["public"]["Tables"]["purchase_order_lines"]["Row"] & {
    stock_items?: { oem_part_number: string; description: string | null } | null;
  };

export type BlanketSummary = {
  po: PurchaseOrderRow;
  supplier: { code: string; name: string } | null;
  warehouse: { code: string; name: string } | null;
  lines: BlanketLineRow[];
  remainingValue: number;
  remainingQty: number;
};

export type BlanketAlert = {
  kind: "expiry" | "remaining_value" | "remaining_qty";
  severity: "warn" | "critical";
  message: string;
};

const EXPIRY_WARN_DAYS = 14;
const REMAINING_VALUE_PCT = 0.15;
const REMAINING_QTY_FLOOR = 5;

/** Expiry / remaining alerts for staff + supplier blanket UIs. */
export function blanketAlerts(summary: BlanketSummary): BlanketAlert[] {
  const alerts: BlanketAlert[] = [];
  const maxValue = Number(summary.po.blanket_max_value ?? 0);
  const remainingValue = summary.remainingValue;
  const remainingQty = summary.remainingQty;

  if (summary.po.expected_date) {
    const due = new Date(summary.po.expected_date);
    if (!Number.isNaN(due.getTime())) {
      const days =
        (due.getTime() - Date.now()) / (1000 * 60 * 60 * 24);
      if (days < 0) {
        alerts.push({
          kind: "expiry",
          severity: "critical",
          message: `Expected date passed (${summary.po.expected_date.slice(0, 10)})`,
        });
      } else if (days <= EXPIRY_WARN_DAYS) {
        alerts.push({
          kind: "expiry",
          severity: "warn",
          message: `Expires in ${Math.ceil(days)} day(s) (${summary.po.expected_date.slice(0, 10)})`,
        });
      }
    }
  }

  if (maxValue > 0 && remainingValue / maxValue <= REMAINING_VALUE_PCT) {
    alerts.push({
      kind: "remaining_value",
      severity: remainingValue <= 0 ? "critical" : "warn",
      message: `Remaining value ${remainingValue.toFixed(2)} ${summary.po.currency} (${Math.round((remainingValue / maxValue) * 100)}% of max)`,
    });
  }

  if (remainingQty > 0 && remainingQty <= REMAINING_QTY_FLOOR) {
    alerts.push({
      kind: "remaining_qty",
      severity: "warn",
      message: `Low remaining qty · ${remainingQty} left across lines`,
    });
  } else if (remainingQty <= 0 && summary.lines.length > 0) {
    alerts.push({
      kind: "remaining_qty",
      severity: "critical",
      message: "No remaining qty on blanket lines",
    });
  }

  return alerts;
}

function asSingle<T>(value: T | T[] | null | undefined): T | null {
  if (value == null) return null;
  return Array.isArray(value) ? (value[0] ?? null) : value;
}

export async function listBlanketPurchaseOrders(
  client: SupabaseClient,
): Promise<StorefrontResult<BlanketSummary[]>> {
  const { data, error } = await client
    .from("purchase_orders")
    .select(
      "*, suppliers ( code, name ), warehouses ( code, name )",
    )
    .eq("is_blanket", true)
    .order("created_at", { ascending: false })
    .limit(50);
  if (error) return { ok: false, error: error.message };

  const summaries: BlanketSummary[] = [];
  for (const row of data ?? []) {
    const r = row as PurchaseOrderRow & {
      suppliers?: { code: string; name: string } | { code: string; name: string }[] | null;
      warehouses?: { code: string; name: string } | { code: string; name: string }[] | null;
    };
    const linesRes = await client
      .from("purchase_order_lines")
      .select("*, stock_items ( oem_part_number, description )")
      .eq("purchase_order_id", r.id)
      .order("line_no");
    if (linesRes.error) return { ok: false, error: linesRes.error.message };

    const lines: BlanketLineRow[] = (linesRes.data ?? []).map((line) => {
      const l = line as BlanketLineRow & {
        stock_items?: BlanketLineRow["stock_items"] | BlanketLineRow["stock_items"][];
      };
      return { ...l, stock_items: asSingle(l.stock_items) };
    });

    const maxValue = Number(r.blanket_max_value ?? 0);
    const releasedValue = Number(r.blanket_value_released ?? 0);
    const remainingQty = lines.reduce(
      (sum, line) =>
        sum + Math.max(0, Number(line.qty_ordered) - Number(line.qty_released ?? 0)),
      0,
    );

    summaries.push({
      po: r,
      supplier: asSingle(r.suppliers),
      warehouse: asSingle(r.warehouses),
      lines,
      remainingValue: Math.max(0, maxValue - releasedValue),
      remainingQty,
    });
  }

  return { ok: true, data: summaries };
}

export async function createBlanketPurchaseOrder(
  client: SupabaseClient,
  args: {
    supplierId: string;
    warehouseId: string;
    currency: CurrencyCode;
    exchangeRate: number;
    blanketMaxValue: number;
    lines: {
      stock_item_id: string;
      uom_id: string;
      qty: number;
      unit_price: number;
      currency?: CurrencyCode;
    }[];
    notes?: string;
    expectedDate?: string;
  },
): Promise<StorefrontResult<string>> {
  const { data, error } = await client.rpc("create_blanket_purchase_order", {
    p_supplier_id: args.supplierId,
    p_warehouse_id: args.warehouseId,
    p_currency: args.currency,
    p_exchange_rate: args.exchangeRate,
    p_blanket_max_value: args.blanketMaxValue,
    p_lines: args.lines,
    p_notes: args.notes || undefined,
    p_expected_date: args.expectedDate || undefined,
  });
  if (error) return { ok: false, error: error.message };
  if (!data) return { ok: false, error: "create_blanket_purchase_order returned no id." };
  return { ok: true, data };
}

export async function submitPurchaseOrder(
  client: SupabaseClient,
  purchaseOrderId: string,
): Promise<StorefrontResult<string>> {
  const { data, error } = await client.rpc("submit_purchase_order", {
    p_purchase_order_id: purchaseOrderId,
  });
  if (error) return { ok: false, error: error.message };
  return { ok: true, data: data ?? purchaseOrderId };
}

export async function createBlanketRelease(
  client: SupabaseClient,
  args: {
    blanketPurchaseOrderId: string;
    lines: { blanket_line_id: string; qty: number }[];
    notes?: string;
  },
): Promise<StorefrontResult<string>> {
  const { data, error } = await client.rpc("create_blanket_release", {
    p_blanket_purchase_order_id: args.blanketPurchaseOrderId,
    p_lines: args.lines,
    p_notes: args.notes || undefined,
  });
  if (error) return { ok: false, error: error.message };
  if (!data) return { ok: false, error: "create_blanket_release returned no id." };
  return { ok: true, data };
}
