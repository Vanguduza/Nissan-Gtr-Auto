import type { Database, SupabaseClient } from "@gtr/supabase-client";
import {
  loadSuppliers,
  loadWarehouses,
  requireSession,
  searchStockItems,
  type CurrencyCode,
  type StockItemOption,
  type SupplierOption,
  type WarehouseOption,
  type StorefrontResult,
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
