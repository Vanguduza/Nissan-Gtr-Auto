import type { Database, SupabaseClient } from "@gtr/supabase-client";
import type { StorefrontResult } from "@/lib/customer-storefront";
import {
  loadWarehouses,
  requireSession,
  searchStockItems,
  type CurrencyCode,
  type StockItemOption,
  type WarehouseOption,
} from "@/lib/rfq-portal";

export { requireSession, loadWarehouses, searchStockItems };
export type { CurrencyCode, StockItemOption, WarehouseOption };

export type ProcurementClient = SupabaseClient<Database>;

export type PreferredSupplierOption = {
  id: string;
  code: string;
  name: string;
  default_currency: CurrencyCode;
};

export type PoLineDraft = {
  stock_item_id: string;
  uom_id: string;
  qty: number;
  unit_price: number;
  currency?: CurrencyCode;
  oem_part_number?: string;
  description?: string | null;
};

export async function loadPreferredSuppliers(
  client: ProcurementClient,
): Promise<StorefrontResult<PreferredSupplierOption[]>> {
  const { data, error } = await client
    .from("suppliers")
    .select("id, code, name, default_currency")
    .eq("is_preferred", true)
    .eq("is_active", true)
    .order("name");
  if (error) return { ok: false, error: error.message };
  return {
    ok: true,
    data: (data ?? []).map((r) => ({
      id: r.id,
      code: r.code,
      name: r.name,
      default_currency: r.default_currency as CurrencyCode,
    })),
  };
}

/** Prefer WH1 / MAIN for receiving POs. */
export function pickReceivingWarehouse(
  warehouses: WarehouseOption[],
): WarehouseOption | null {
  return (
    warehouses.find((w) => w.code === "WH1") ??
    warehouses.find((w) => w.code === "MAIN") ??
    warehouses[0] ??
    null
  );
}

export async function createPreferredPurchaseOrder(
  client: ProcurementClient,
  input: {
    supplierId: string;
    warehouseId: string;
    currency: CurrencyCode;
    exchangeRate: number;
    lines: PoLineDraft[];
    notes?: string;
    expectedDate?: string;
    submit?: boolean;
  },
): Promise<StorefrontResult<{ purchaseOrderId: string }>> {
  const payload = input.lines.map((l) => ({
    stock_item_id: l.stock_item_id,
    uom_id: l.uom_id,
    qty: l.qty,
    unit_price: l.unit_price,
    currency: l.currency ?? input.currency,
  }));

  const { data, error } = await client.rpc("create_purchase_order", {
    p_supplier_id: input.supplierId,
    p_warehouse_id: input.warehouseId,
    p_currency: input.currency,
    p_exchange_rate: input.exchangeRate,
    p_lines: payload,
    p_notes: input.notes ?? null,
    p_expected_date: input.expectedDate ?? null,
  });
  if (error) return { ok: false, error: error.message };
  const purchaseOrderId = data as string;

  if (input.submit) {
    const sub = await client.rpc("submit_purchase_order", {
      p_purchase_order_id: purchaseOrderId,
    });
    if (sub.error) return { ok: false, error: sub.error.message };
  }

  return { ok: true, data: { purchaseOrderId } };
}

export async function listApprovedPosForGrn(
  client: ProcurementClient,
): Promise<
  StorefrontResult<
    {
      id: string;
      document_number: string | null;
      supplier_id: string;
      currency: CurrencyCode;
      suppliers: { code: string; name: string } | null;
    }[]
  >
> {
  const { data, error } = await client
    .from("purchase_orders")
    .select("id, document_number, supplier_id, currency, suppliers ( code, name )")
    .eq("status", "approved")
    .order("approved_at", { ascending: false })
    .limit(50);
  if (error) return { ok: false, error: error.message };
  return {
    ok: true,
    data: (data ?? []).map((r) => {
      const row = r as {
        id: string;
        document_number: string | null;
        supplier_id: string;
        currency: CurrencyCode;
        suppliers?:
          | { code: string; name: string }
          | { code: string; name: string }[]
          | null;
      };
      const suppliers = Array.isArray(row.suppliers)
        ? row.suppliers[0] ?? null
        : row.suppliers ?? null;
      return {
        id: row.id,
        document_number: row.document_number,
        supplier_id: row.supplier_id,
        currency: row.currency,
        suppliers,
      };
    }),
  };
}

export async function listPoLines(
  client: ProcurementClient,
  purchaseOrderId: string,
): Promise<
  StorefrontResult<
    {
      id: string;
      stock_item_id: string;
      uom_id: string;
      qty_ordered: number;
      qty_received: number;
      unit_price: number;
      oem_part_number: string;
      description: string | null;
    }[]
  >
> {
  const { data, error } = await client
    .from("purchase_order_lines")
    .select(
      "id, stock_item_id, uom_id, qty_ordered, qty_received, unit_price, stock_items ( oem_part_number, description )",
    )
    .eq("purchase_order_id", purchaseOrderId)
    .order("line_no");
  if (error) return { ok: false, error: error.message };
  return {
    ok: true,
    data: (data ?? []).map((r) => {
      const row = r as {
        id: string;
        stock_item_id: string;
        uom_id: string;
        qty_ordered: number;
        qty_received: number;
        unit_price: number;
        stock_items?:
          | { oem_part_number: string; description: string | null }
          | { oem_part_number: string; description: string | null }[]
          | null;
      };
      const si = Array.isArray(row.stock_items)
        ? row.stock_items[0]
        : row.stock_items;
      return {
        id: row.id,
        stock_item_id: row.stock_item_id,
        uom_id: row.uom_id,
        qty_ordered: Number(row.qty_ordered),
        qty_received: Number(row.qty_received),
        unit_price: Number(row.unit_price),
        oem_part_number: si?.oem_part_number ?? "",
        description: si?.description ?? null,
      };
    }),
  };
}
