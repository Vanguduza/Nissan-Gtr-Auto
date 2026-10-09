import type { SupabaseClient } from "@gtr/supabase-client";
import { requireSession, type StorefrontResult } from "@/lib/customer-storefront";

export { requireSession };

/** One part at one branch that needs topping up (`get_restock_suggestions`); quantities in base units. */
export type RestockSuggestion = {
  stockItemId: string;
  uomId: string;
  oemPartNumber: string;
  description: string | null;
  warehouseId: string;
  warehouse: string;
  onHand: number;
  available: number;
  onOrder: number;
  /** In draft purchase orders: not on its way yet. */
  inDraft: number;
  transferIn: number;
  backordered: number;
  sold: number;
  /** Asked for at the counter and not had (record_lost_demand), counted in the sales speed. */
  lost: number;
  daily: number;
  daysOfCover: number | null;
  reorderPoint: number;
  /** branch: this branch's own · set: the part's own · from_sales: worked out from sales speed */
  reorderPointSource: "branch" | "set" | "from_sales";
  reorderQty: number | null;
  qtyNeeded: number;
  transferQty: number;
  transferFrom: { warehouseId: string; warehouse: string; spare: number } | null;
  buyQty: number;
  supplierId: string | null;
  supplier: string | null;
  leadDays: number;
  /** received: median of orders actually received · quoted: the supplier's quote · default: the page setting */
  leadSource: "received" | "quoted" | "default";
  leadSamples: number | null;
  unitCost: number | null;
  costCurrency: string | null;
  /** out: none left or customers waiting · before_delivery: runs out before a delivery would arrive · low */
  urgency: "out" | "before_delivery" | "low";
};
export type SlowStock = {
  stockItemId: string;
  uomId: string;
  oemPartNumber: string;
  description: string | null;
  warehouseId: string;
  warehouse: string;
  onHand: number;
  available: number;
  lastSoldAt: string | null;
  value: number;
  currency: string | null;
  /** Another branch sells it: move [moveQty] there. */
  moveTo: { warehouseId: string; warehouse: string; sold: number } | null;
  moveQty: number | null;
  /** Nobody sells it: suggested markdown (10/20/30% by age). */
  markdownPct: number | null;
};
export type RestockPlan = {
  settings: { salesDays: number; leadDays: number; safetyDays: number; coverDays: number };
  suggestions: RestockSuggestion[];
  slowStock: SlowStock[];
};
export type RestockSettings = { salesDays: number; leadDays: number; safetyDays: number; coverDays: number };

function rpc(client: SupabaseClient, fn: string, args: Record<string, unknown>) {
  return (client as unknown as {
    rpc: (name: string, params: Record<string, unknown>) => PromiseLike<{ data: unknown; error: { message: string } | null }>;
  }).rpc(fn, args);
}

type Row = Record<string, unknown>;
const rows = (v: unknown): Row[] => (Array.isArray(v) ? (v as Row[]) : []);
const n = (v: unknown) => (v == null ? 0 : Number(v));
const nOrNull = (v: unknown) => (v == null ? null : Number(v));
const s = (v: unknown) => (typeof v === "string" && v ? v : null);

export async function getRestockPlan(
  client: SupabaseClient,
  warehouseId: string | null,
  st: RestockSettings,
): Promise<StorefrontResult<RestockPlan>> {
  const { data, error } = await rpc(client, "get_restock_suggestions", {
    p_warehouse_id: warehouseId,
    p_sales_days: st.salesDays,
    p_lead_days: st.leadDays,
    p_safety_days: st.safetyDays,
    p_cover_days: st.coverDays,
  });
  if (error) return { ok: false, error: error.message };
  const d = (data ?? {}) as Row;
  const set = (d.settings ?? {}) as Row;
  return {
    ok: true,
    data: {
      settings: { salesDays: n(set.sales_days), leadDays: n(set.lead_days), safetyDays: n(set.safety_days), coverDays: n(set.cover_days) },
      suggestions: rows(d.suggestions).map((r) => {
        const from = r.transfer_from as Row | null;
        return {
          stockItemId: String(r.stock_item_id),
          uomId: String(r.uom_id),
          oemPartNumber: String(r.oem_part_number ?? ""),
          description: s(r.description),
          warehouseId: String(r.warehouse_id),
          warehouse: String(r.warehouse ?? ""),
          onHand: n(r.on_hand),
          available: n(r.available),
          onOrder: n(r.on_order),
          inDraft: n(r.in_draft),
          transferIn: n(r.transfer_in),
          backordered: n(r.backordered),
          sold: n(r.sold),
          lost: n(r.lost),
          daily: n(r.daily),
          daysOfCover: nOrNull(r.days_of_cover),
          reorderPoint: n(r.reorder_point),
          reorderPointSource: r.reorder_point_source === "branch" ? "branch" : r.reorder_point_source === "set" ? "set" : "from_sales",
          reorderQty: nOrNull(r.reorder_qty),
          qtyNeeded: n(r.qty_needed),
          transferQty: n(r.transfer_qty),
          transferFrom: from ? { warehouseId: String(from.warehouse_id), warehouse: String(from.warehouse ?? ""), spare: n(from.spare) } : null,
          buyQty: n(r.buy_qty),
          supplierId: s(r.supplier_id),
          supplier: s(r.supplier),
          leadDays: n(r.lead_days),
          leadSource: r.lead_source === "received" ? "received" : r.lead_source === "quoted" ? "quoted" : "default",
          leadSamples: nOrNull(r.lead_samples),
          unitCost: nOrNull(r.unit_cost),
          costCurrency: s(r.cost_currency),
          urgency: (r.urgency as RestockSuggestion["urgency"]) ?? "low",
        };
      }),
      slowStock: rows(d.slow_stock).map((r) => {
        const to = r.move_to as Row | null;
        return {
          stockItemId: String(r.stock_item_id),
          uomId: String(r.uom_id ?? ""),
          oemPartNumber: String(r.oem_part_number ?? ""),
          description: s(r.description),
          warehouseId: String(r.warehouse_id ?? ""),
          warehouse: String(r.warehouse ?? ""),
          onHand: n(r.on_hand),
          available: n(r.available),
          lastSoldAt: s(r.last_sold_at),
          value: n(r.value),
          currency: s(r.currency),
          moveTo: to ? { warehouseId: String(to.warehouse_id), warehouse: String(to.warehouse ?? ""), sold: n(to.sold) } : null,
          moveQty: nOrNull(r.move_qty),
          markdownPct: nOrNull(r.markdown_pct),
        };
      }),
    },
  };
}

/** Draft purchase order for one supplier and branch (`create_purchase_order`; procurement approves it). */
export async function createPurchaseOrderDraft(
  client: SupabaseClient,
  args: { supplierId: string; warehouseId: string; currency: string; lines: { stockItemId: string; uomId: string; qty: number; unitPrice: number }[]; notes: string; expectedDate: string | null },
): Promise<StorefrontResult<string>> {
  const { data, error } = await rpc(client, "create_purchase_order", {
    p_supplier_id: args.supplierId,
    p_warehouse_id: args.warehouseId,
    p_currency: args.currency,
    p_exchange_rate: 1,
    p_lines: args.lines.map((l) => ({ stock_item_id: l.stockItemId, uom_id: l.uomId, qty: l.qty, unit_price: l.unitPrice })),
    p_notes: args.notes,
    p_expected_date: args.expectedDate,
    p_material_request_id: null,
  });
  if (error) return { ok: false, error: error.message };
  return { ok: true, data: String(data) };
}

export const URGENCY_LABEL: Record<RestockSuggestion["urgency"], string> = {
  out: "Out",
  before_delivery: "Runs out first",
  low: "Low",
};

/** This branch's own reorder point (null clears it: back to the part's own or the sales-based one). */
export async function setBranchReorderPoint(
  client: SupabaseClient,
  stockItemId: string,
  warehouseId: string,
  reorderPoint: number | null,
  reorderQty: number | null,
): Promise<StorefrontResult<true>> {
  const { error } = await rpc(client, "set_branch_reorder_point", {
    p_stock_item_id: stockItemId,
    p_warehouse_id: warehouseId,
    p_reorder_point: reorderPoint,
    p_reorder_qty: reorderQty,
  });
  if (error) return { ok: false, error: error.message };
  return { ok: true, data: true };
}

/** Lower every price of a part by [percent] (manager / finance / admin; logged with the reason). */
export async function markdownStockItem(
  client: SupabaseClient,
  stockItemId: string,
  percent: number,
  reason: string,
): Promise<StorefrontResult<{ priceList: string; old: number; new: number }[]>> {
  const { data, error } = await rpc(client, "markdown_stock_item", { p_stock_item_id: stockItemId, p_percent: percent, p_reason: reason });
  if (error) return { ok: false, error: error.message };
  return { ok: true, data: rows(data).map((r) => ({ priceList: String(r.price_list ?? ""), old: n(r.old), new: n(r.new) })) };
}

export const LEAD_SOURCE_LABEL: Record<RestockSuggestion["leadSource"], string> = {
  received: "seen on received orders",
  quoted: "supplier's quote",
  default: "page setting",
};
