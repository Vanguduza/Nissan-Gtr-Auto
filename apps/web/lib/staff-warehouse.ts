import type { Database, SupabaseClient } from "@gtr/supabase-client";
import {
  categoryFilterNeedles,
  effectiveCategoryFilter,
  MERCHANDISING_TAXONOMY,
} from "@gtr/shared";
import {
  requireSession,
  zigExchangeRate,
  type StorefrontResult,
} from "@/lib/customer-storefront";
import { downloadCsv } from "@/lib/staff-finance";

export { requireSession, zigExchangeRate, downloadCsv };

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

export type MasterStockRow = {
  stock_item_id: string;
  oem_part_number: string;
  description: string | null;
  qty_total: number;
  qty_wh1: number;
  qty_wh2: number;
  chassis_codes: string | null;
  category_name: string | null;
  subcategory_name: string | null;
};

export type MasterStockFilters = {
  query?: string;
  chassisCode?: string;
  categorySlug?: string;
  subcategorySlug?: string;
};

export type MasterStockChassisOption = {
  chassisCode: string;
  label: string;
};

export type MasterStockCategoryOption = {
  slug: string;
  label: string;
};

/** Browse page size; export uses MASTER_STOCK_EXPORT_LIMIT. */
export const MASTER_STOCK_PAGE_LIMIT = 500;
export const MASTER_STOCK_EXPORT_LIMIT = 5000;

export const MASTER_STOCK_CSV_HEADERS = [
  "OEM",
  "Description",
  "Model (chassis)",
  "Category",
  "Subcategory",
  "Total",
  "WH1",
  "WH2",
] as const;

export function masterStockCategoryOptions(): MasterStockCategoryOption[] {
  return MERCHANDISING_TAXONOMY.map((p) => ({
    slug: p.slug,
    label: p.label,
  }));
}

export function masterStockSubcategoryOptions(
  categorySlug: string | null | undefined,
): MasterStockCategoryOption[] {
  const slug = categorySlug?.trim();
  if (!slug) return [];
  const parent = MERCHANDISING_TAXONOMY.find((p) => p.slug === slug);
  if (!parent) return [];
  return parent.subcategories.map((s) => ({
    slug: s.slug,
    label: s.label,
  }));
}

/** Pure CSV row builder (formula-safe via downloadCsv). */
export function masterStockToCsvRows(
  rows: MasterStockRow[],
): (string | number)[][] {
  return rows.map((r) => [
    r.oem_part_number,
    r.description ?? "",
    r.chassis_codes ?? "",
    r.category_name ?? "",
    r.subcategory_name ?? "",
    Number(r.qty_total) || 0,
    Number(r.qty_wh1) || 0,
    Number(r.qty_wh2) || 0,
  ]);
}

export function sumMasterStockQty(rows: MasterStockRow[]): {
  total: number;
  wh1: number;
  wh2: number;
} {
  return rows.reduce(
    (acc, r) => ({
      total: acc.total + (Number(r.qty_total) || 0),
      wh1: acc.wh1 + (Number(r.qty_wh1) || 0),
      wh2: acc.wh2 + (Number(r.qty_wh2) || 0),
    }),
    { total: 0, wh1: 0, wh2: 0 },
  );
}

function needlesForSlug(slug: string | null | undefined): string[] | undefined {
  const s = slug?.trim();
  if (!s) return undefined;
  const needles = categoryFilterNeedles(s);
  return needles.length ? needles : undefined;
}

export function masterStockRpcArgs(
  filters: MasterStockFilters,
  limit: number,
): {
  p_limit: number;
  p_query?: string;
  p_chassis_code?: string;
  p_category_needles?: string[];
  p_subcategory_needles?: string[];
} {
  const q = filters.query?.trim() || undefined;
  const chassis = filters.chassisCode?.trim() || undefined;
  const cat = filters.categorySlug?.trim() || undefined;
  const sub = filters.subcategorySlug?.trim() || undefined;
  // When only a parent category is set, use parent needles; leaf uses subcategory needles.
  const effective = effectiveCategoryFilter(cat, sub);
  const categoryNeedles =
    cat && !sub ? needlesForSlug(cat) : undefined;
  const subcategoryNeedles = sub
    ? needlesForSlug(effective)
    : undefined;

  return {
    p_limit: limit,
    ...(q ? { p_query: q } : {}),
    ...(chassis ? { p_chassis_code: chassis } : {}),
    ...(categoryNeedles?.length
      ? { p_category_needles: categoryNeedles }
      : {}),
    ...(subcategoryNeedles?.length
      ? { p_subcategory_needles: subcategoryNeedles }
      : {}),
  };
}

export async function listMasterStockChassisOptions(
  client: SupabaseClient,
): Promise<StorefrontResult<MasterStockChassisOption[]>> {
  const { data, error } = await client
    .from("vehicle_master")
    .select("chassis_code, model_variant")
    .order("chassis_code", { ascending: true })
    .limit(800);
  if (error) return { ok: false, error: error.message };

  const byCode = new Map<string, string>();
  for (const row of data ?? []) {
    const code = String(row.chassis_code ?? "").trim();
    if (!code || byCode.has(code)) continue;
    const variant = String(row.model_variant ?? "").trim();
    byCode.set(code, variant ? `${code} — ${variant}` : code);
  }
  return {
    ok: true,
    data: [...byCode.entries()].map(([chassisCode, label]) => ({
      chassisCode,
      label,
    })),
  };
}

export async function listMasterStock(
  client: SupabaseClient,
  filters: MasterStockFilters = {},
  opts?: { limit?: number },
): Promise<StorefrontResult<MasterStockRow[]>> {
  const limit = opts?.limit ?? MASTER_STOCK_PAGE_LIMIT;
  const { data, error } = await client.rpc(
    "list_master_stock",
    masterStockRpcArgs(filters, limit),
  );
  if (error) return { ok: false, error: error.message };
  return { ok: true, data: (data as MasterStockRow[]) ?? [] };
}

export function exportMasterStockCsv(
  rows: MasterStockRow[],
  scope: "filtered" | "all",
): void {
  const stamp = new Date().toISOString().slice(0, 10);
  const filename =
    scope === "all"
      ? `master-stock-all-${stamp}.csv`
      : `master-stock-filtered-${stamp}.csv`;
  downloadCsv(
    filename,
    [...MASTER_STOCK_CSV_HEADERS],
    masterStockToCsvRows(rows),
  );
}

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
  const exchangeRate =
    args.currency === "ZIG"
      ? (args.exchangeRate ?? zigExchangeRate())
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
