import {
  resolveProcurementProgress,
  type ProcurementProgressStep,
} from "@gtr/procurement";
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

/** Columns added in relationship-procurement migration; types regen is backend follow-up. */
type PoProgressColumns = {
  funds_released_at: string | null;
  progress_step: string | null;
};

export type PurchaseOrderProgress = {
  id: string;
  document_number: string | null;
  status: string;
  funds_released_at: string | null;
  progress_step: string | null;
  supplier: { code: string; name: string } | null;
  qty_ordered: number;
  qty_received: number;
  step: ProcurementProgressStep;
};

function asSingle<T>(value: T | T[] | null | undefined): T | null {
  if (value == null) return null;
  return Array.isArray(value) ? (value[0] ?? null) : value;
}

function sumLineQty(
  lines:
    | { qty_ordered: number | string; qty_received: number | string }[]
    | null
    | undefined,
): { qty_ordered: number; qty_received: number } {
  let qty_ordered = 0;
  let qty_received = 0;
  for (const l of lines ?? []) {
    qty_ordered += Number(l.qty_ordered) || 0;
    qty_received += Number(l.qty_received) || 0;
  }
  return { qty_ordered, qty_received };
}

function toProgress(row: {
  id: string;
  document_number: string | null;
  status: string;
  funds_released_at?: string | null;
  progress_step?: string | null;
  suppliers?:
    | { code: string; name: string }
    | { code: string; name: string }[]
    | null;
  purchase_order_lines?:
    | { qty_ordered: number | string; qty_received: number | string }[]
    | null;
}): PurchaseOrderProgress {
  const qtys = sumLineQty(row.purchase_order_lines);
  const funds_released_at = row.funds_released_at ?? null;
  const progress_step = row.progress_step ?? null;
  return {
    id: row.id,
    document_number: row.document_number,
    status: row.status,
    funds_released_at,
    progress_step,
    supplier: asSingle(row.suppliers),
    qty_ordered: qtys.qty_ordered,
    qty_received: qtys.qty_received,
    step: resolveProcurementProgress({
      status: row.status,
      fundsReleasedAt: funds_released_at,
      qtyOrdered: qtys.qty_ordered,
      qtyReceived: qtys.qty_received,
      progressStep: progress_step,
    }),
  };
}

const PO_PROGRESS_SELECT =
  "id, document_number, status, funds_released_at, progress_step, suppliers ( code, name ), purchase_order_lines ( qty_ordered, qty_received )";

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

/** Call tables/RPCs newer than generated database.types (regen → @backend_agent). */
function asLooseClient(client: ProcurementClient) {
  return client as unknown as {
    from: (table: string) => {
      select: (cols: string) => any;
    };
    rpc: (
      fn: string,
      args?: Record<string, unknown>,
    ) => PromiseLike<{ data: unknown; error: { message: string } | null }>;
  };
}

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
    p_notes: input.notes || undefined,
    p_expected_date: input.expectedDate || undefined,
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

/** Live tracker binding for a single PO (status → closed via resolveProcurementProgress). */
export async function loadPurchaseOrderProgress(
  client: ProcurementClient,
  purchaseOrderId: string,
): Promise<StorefrontResult<PurchaseOrderProgress>> {
  // funds_released_at / progress_step pending database.types.ts regen (@backend_agent).
  const { data, error } = await client
    .from("purchase_orders")
    .select(PO_PROGRESS_SELECT)
    .eq("id", purchaseOrderId)
    .maybeSingle();
  if (error) return { ok: false, error: error.message };
  if (!data) return { ok: false, error: "Purchase order not found." };
  return {
    ok: true,
    data: toProgress(data as Parameters<typeof toProgress>[0] & PoProgressColumns),
  };
}

/** Recent POs for hub / approvals surfaces — each row carries a live tracker step. */
export async function listRecentPurchaseOrderProgress(
  client: ProcurementClient,
  limit = 12,
): Promise<StorefrontResult<PurchaseOrderProgress[]>> {
  const { data, error } = await client
    .from("purchase_orders")
    .select(PO_PROGRESS_SELECT)
    .order("updated_at", { ascending: false })
    .limit(limit);
  if (error) return { ok: false, error: error.message };
  return {
    ok: true,
    data: (data ?? []).map((r) =>
      toProgress(r as Parameters<typeof toProgress>[0] & PoProgressColumns),
    ),
  };
}

/**
 * Catalog fast-path for GRN OEM entry (Bridge-First QR → OEM lands here as text).
 * Returns null when OEM is unknown in stock_items.
 */
export async function resolveStockItemByOem(
  client: ProcurementClient,
  oem: string,
): Promise<StorefrontResult<string | null>> {
  const { data, error } = await client.rpc("resolve_stock_item_by_oem", {
    p_oem: oem,
  });
  if (error) return { ok: false, error: error.message };
  return { ok: true, data: (data as string | null) ?? null };
}
