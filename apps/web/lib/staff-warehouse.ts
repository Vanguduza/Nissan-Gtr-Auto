import type { Database, SupabaseClient } from "@gtr/supabase-client";
import {
  requireSession,
  zigExchangeRate,
  type StorefrontResult,
} from "@/lib/customer-storefront";

export { requireSession, zigExchangeRate };

export type CurrencyCode = Database["public"]["Enums"]["currency_code"];
export type ValuationMethod = Database["public"]["Enums"]["valuation_method"];
export type ReconciliationScope =
  Database["public"]["Enums"]["stock_reconciliation_scope"];

export type WarehouseOption = {
  id: string;
  code: string;
  name: string;
  is_quarantine: boolean;
};

export type StockItemOption = {
  id: string;
  oem_part_number: string;
  description: string | null;
  base_uom_id: string | null;
  requires_serial: boolean;
};

export type StockEntryOption = {
  id: string;
  document_number: string | null;
  status: string;
  entry_type: string;
  from_warehouse_id: string | null;
  to_warehouse_id: string | null;
  notes: string | null;
  created_at: string;
};

export type ReconciliationOption = {
  id: string;
  document_number: string | null;
  status: string;
  scope: ReconciliationScope;
  currency: CurrencyCode;
  warehouse_id: string;
  variance_value_abs: number | null;
  created_at: string;
};

export type ReconciliationLineRow = {
  id: string;
  stock_reconciliation_id: string;
  stock_item_id: string;
  system_qty: number;
  counted_qty: number;
  unit_cost: number;
  currency: CurrencyCode;
  variance_qty: number | null;
  stock_items?: { oem_part_number: string; description: string | null } | null;
};

export type ReceiptLineInput = {
  stock_item_id: string;
  uom_id: string;
  qty: number;
  unit_cost: number;
  currency: CurrencyCode;
  valuation_method?: ValuationMethod;
  serials?: string[];
};

export type TransferLineInput = {
  stock_item_id: string;
  uom_id: string;
  qty: number;
  valuation_method?: ValuationMethod;
};

function asSingle<T>(value: T | T[] | null | undefined): T | null {
  if (value == null) return null;
  return Array.isArray(value) ? (value[0] ?? null) : value;
}

export async function listWarehouses(
  client: SupabaseClient,
  opts?: { includeQuarantine?: boolean },
): Promise<StorefrontResult<WarehouseOption[]>> {
  let query = client
    .from("warehouses")
    .select("id, code, name, is_quarantine")
    .eq("is_active", true)
    .order("code");
  if (!opts?.includeQuarantine) {
    query = query.eq("is_quarantine", false);
  }
  const { data, error } = await query;
  if (error) return { ok: false, error: error.message };
  return { ok: true, data: (data as WarehouseOption[]) ?? [] };
}

export async function searchStockItems(
  client: SupabaseClient,
  query: string,
): Promise<StorefrontResult<StockItemOption[]>> {
  const q = query.trim();
  if (q.length < 2) return { ok: true, data: [] };

  const uuidLike =
    /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(q);
  if (uuidLike) {
    const { data, error } = await client
      .from("stock_items")
      .select("id, oem_part_number, description, base_uom_id, requires_serial")
      .eq("id", q)
      .limit(1);
    if (error) return { ok: false, error: error.message };
    return { ok: true, data: (data as StockItemOption[]) ?? [] };
  }

  const { data, error } = await client
    .from("stock_items")
    .select("id, oem_part_number, description, base_uom_id, requires_serial")
    .or(`oem_part_number.ilike.%${q}%,description.ilike.%${q}%`)
    .limit(20);
  if (error) return { ok: false, error: error.message };
  return { ok: true, data: (data as StockItemOption[]) ?? [] };
}

export async function postStockReceipt(
  client: SupabaseClient,
  args: {
    toWarehouseId: string;
    notes: string;
    lines: ReceiptLineInput[];
  },
): Promise<StorefrontResult<string>> {
  const { data, error } = await client.rpc("post_stock_receipt", {
    p_to_warehouse_id: args.toWarehouseId,
    p_notes: args.notes,
    p_lines: args.lines.map((l) => ({
      stock_item_id: l.stock_item_id,
      uom_id: l.uom_id,
      qty: l.qty,
      unit_cost: l.unit_cost,
      currency: l.currency,
      valuation_method: l.valuation_method ?? "FIFO",
      ...(l.serials?.length ? { serials: l.serials } : {}),
    })),
  });
  if (error) return { ok: false, error: error.message };
  if (!data) return { ok: false, error: "post_stock_receipt returned no id." };
  return { ok: true, data };
}

export async function listPendingTransfers(
  client: SupabaseClient,
): Promise<StorefrontResult<StockEntryOption[]>> {
  const { data, error } = await client
    .from("stock_entries")
    .select(
      "id, document_number, status, entry_type, from_warehouse_id, to_warehouse_id, notes, created_at",
    )
    .eq("entry_type", "transfer")
    .eq("status", "pending_approval")
    .order("created_at", { ascending: false })
    .limit(40);
  if (error) return { ok: false, error: error.message };
  return { ok: true, data: (data as StockEntryOption[]) ?? [] };
}

export async function createStockTransfer(
  client: SupabaseClient,
  args: {
    fromWarehouseId: string;
    toWarehouseId: string;
    notes: string;
    lines: TransferLineInput[];
  },
): Promise<StorefrontResult<string>> {
  const { data, error } = await client.rpc("create_stock_transfer", {
    p_from_warehouse_id: args.fromWarehouseId,
    p_to_warehouse_id: args.toWarehouseId,
    p_notes: args.notes,
    p_lines: args.lines.map((l) => ({
      stock_item_id: l.stock_item_id,
      uom_id: l.uom_id,
      qty: l.qty,
      valuation_method: l.valuation_method ?? "FIFO",
    })),
  });
  if (error) return { ok: false, error: error.message };
  if (!data) return { ok: false, error: "create_stock_transfer returned no id." };
  return { ok: true, data };
}

export async function approveStockTransfer(
  client: SupabaseClient,
  entryId: string,
): Promise<StorefrontResult<string>> {
  const { data, error } = await client.rpc("approve_stock_transfer", {
    p_entry_id: entryId,
  });
  if (error) return { ok: false, error: error.message };
  if (!data) return { ok: false, error: "approve_stock_transfer returned no id." };
  return { ok: true, data };
}

export async function rejectStockTransfer(
  client: SupabaseClient,
  entryId: string,
): Promise<StorefrontResult<string>> {
  const { data, error } = await client.rpc("reject_stock_transfer", {
    p_entry_id: entryId,
  });
  if (error) return { ok: false, error: error.message };
  if (!data) return { ok: false, error: "reject_stock_transfer returned no id." };
  return { ok: true, data };
}

export async function listReconciliations(
  client: SupabaseClient,
): Promise<StorefrontResult<ReconciliationOption[]>> {
  const { data, error } = await client
    .from("stock_reconciliations")
    .select(
      "id, document_number, status, scope, currency, warehouse_id, variance_value_abs, created_at",
    )
    .order("created_at", { ascending: false })
    .limit(40);
  if (error) return { ok: false, error: error.message };
  return { ok: true, data: (data as ReconciliationOption[]) ?? [] };
}

export async function loadReconciliationLines(
  client: SupabaseClient,
  reconciliationId: string,
): Promise<StorefrontResult<ReconciliationLineRow[]>> {
  const { data, error } = await client
    .from("stock_reconciliation_lines")
    .select(
      "id, stock_reconciliation_id, stock_item_id, system_qty, counted_qty, unit_cost, currency, variance_qty, stock_items(oem_part_number, description)",
    )
    .eq("stock_reconciliation_id", reconciliationId)
    .order("created_at");
  if (error) return { ok: false, error: error.message };
  const rows = (data ?? []).map((row) => ({
    ...row,
    stock_items: asSingle(row.stock_items),
  }));
  return { ok: true, data: rows as ReconciliationLineRow[] };
}

export async function createStockReconciliationDraft(
  client: SupabaseClient,
  args: {
    warehouseId: string;
    scope: ReconciliationScope;
    itemIds?: string[];
    notes?: string;
    currency: CurrencyCode;
    exchangeRate?: number;
  },
): Promise<StorefrontResult<string>> {
  const configuredZigRate = zigExchangeRate();
  if (args.currency === "ZIG" && args.exchangeRate == null && configuredZigRate == null) {
    return { ok: false, error: "ZiG exchange rate is not configured. Finance must publish a verified rate before ZiG transactions are enabled." };
  }
  const exchangeRate =
    args.currency === "ZIG"
      ? (args.exchangeRate ?? configuredZigRate!)
      : (args.exchangeRate ?? 1);

  const { data, error } = await client.rpc("create_stock_reconciliation_draft", {
    p_warehouse_id: args.warehouseId,
    p_scope: args.scope,
    p_item_ids: args.scope === "partial" ? args.itemIds : undefined,
    p_notes: args.notes || undefined,
    p_currency: args.currency,
    p_exchange_rate: exchangeRate,
  });
  if (error) return { ok: false, error: error.message };
  if (!data) {
    return { ok: false, error: "create_stock_reconciliation_draft returned no id." };
  }
  return { ok: true, data };
}

export async function upsertStockReconciliationLines(
  client: SupabaseClient,
  args: {
    reconciliationId: string;
    lines: { stock_item_id: string; counted_qty: number }[];
  },
): Promise<StorefrontResult<number>> {
  const { data, error } = await client.rpc("upsert_stock_reconciliation_lines", {
    p_reconciliation_id: args.reconciliationId,
    p_lines: args.lines,
  });
  if (error) return { ok: false, error: error.message };
  const n = typeof data === "number" ? data : Number(data);
  if (!Number.isFinite(n)) {
    return { ok: false, error: "upsert_stock_reconciliation_lines returned non-numeric." };
  }
  return { ok: true, data: n };
}

export async function submitStockReconciliation(
  client: SupabaseClient,
  reconciliationId: string,
): Promise<StorefrontResult<string>> {
  const { data, error } = await client.rpc("submit_stock_reconciliation", {
    p_reconciliation_id: reconciliationId,
  });
  if (error) return { ok: false, error: error.message };
  if (!data) {
    return { ok: false, error: "submit_stock_reconciliation returned no id." };
  }
  return { ok: true, data };
}

export async function approveStockReconciliation(
  client: SupabaseClient,
  reconciliationId: string,
): Promise<StorefrontResult<string>> {
  const { data, error } = await client.rpc("approve_stock_reconciliation", {
    p_reconciliation_id: reconciliationId,
  });
  if (error) return { ok: false, error: error.message };
  if (!data) {
    return { ok: false, error: "approve_stock_reconciliation returned no id." };
  }
  return { ok: true, data };
}

export async function cancelStockReconciliation(
  client: SupabaseClient,
  args: { reconciliationId: string; notes?: string },
): Promise<StorefrontResult<string>> {
  const { data, error } = await client.rpc("cancel_stock_reconciliation", {
    p_reconciliation_id: args.reconciliationId,
    p_notes: args.notes || undefined,
  });
  if (error) return { ok: false, error: error.message };
  if (!data) {
    return { ok: false, error: "cancel_stock_reconciliation returned no id." };
  }
  return { ok: true, data };
}
