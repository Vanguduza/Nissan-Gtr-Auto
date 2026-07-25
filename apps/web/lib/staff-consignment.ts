import type { Database, SupabaseClient } from "@gtr/supabase-client";
import {
  listWarehouses,
  requireSession,
  searchStockItems,
  type CurrencyCode,
  type StockItemOption,
  type WarehouseOption,
} from "@/lib/staff-warehouse";
import type { StorefrontResult } from "@/lib/customer-storefront";
import { loadSuppliers, type SupplierOption } from "@/lib/rfq-portal";

export {
  listWarehouses,
  loadSuppliers,
  requireSession,
  searchStockItems,
};
export type { CurrencyCode, StockItemOption, SupplierOption, WarehouseOption };

export type ConsignmentKind =
  Database["public"]["Enums"]["consignment_kind"];
export type ConsignmentPurpose =
  Database["public"]["Enums"]["consignment_entry_purpose"];

export type ConsignmentEntryRow =
  Database["public"]["Tables"]["consignment_entries"]["Row"] & {
    suppliers?: { code: string; name: string } | null;
    customers?: { display_name: string } | null;
    warehouses?: { code: string; name: string } | null;
  };

export type ConsignmentLineRow =
  Database["public"]["Tables"]["consignment_entry_lines"]["Row"] & {
    stock_items?: { oem_part_number: string; description: string | null } | null;
  };

export type CustomerOption = { id: string; display_name: string };

function asSingle<T>(value: T | T[] | null | undefined): T | null {
  if (value == null) return null;
  return Array.isArray(value) ? (value[0] ?? null) : value;
}

export async function listConsignmentEntries(
  client: SupabaseClient,
): Promise<StorefrontResult<ConsignmentEntryRow[]>> {
  const { data, error } = await client
    .from("consignment_entries")
    .select(
      "*, suppliers ( code, name ), customers ( display_name ), warehouses ( code, name )",
    )
    .order("created_at", { ascending: false })
    .limit(50);
  if (error) return { ok: false, error: error.message };

  const rows: ConsignmentEntryRow[] = (data ?? []).map((row) => {
    const r = row as ConsignmentEntryRow & {
      suppliers?: ConsignmentEntryRow["suppliers"] | ConsignmentEntryRow["suppliers"][];
      customers?: ConsignmentEntryRow["customers"] | ConsignmentEntryRow["customers"][];
      warehouses?: ConsignmentEntryRow["warehouses"] | ConsignmentEntryRow["warehouses"][];
    };
    return {
      ...r,
      suppliers: asSingle(r.suppliers),
      customers: asSingle(r.customers),
      warehouses: asSingle(r.warehouses),
    };
  });
  return { ok: true, data: rows };
}

export async function listConsignmentLines(
  client: SupabaseClient,
  entryId: string,
): Promise<StorefrontResult<ConsignmentLineRow[]>> {
  const { data, error } = await client
    .from("consignment_entry_lines")
    .select("*, stock_items ( oem_part_number, description )")
    .eq("consignment_entry_id", entryId)
    .order("line_no");
  if (error) return { ok: false, error: error.message };
  const rows: ConsignmentLineRow[] = (data ?? []).map((row) => {
    const r = row as ConsignmentLineRow & {
      stock_items?: ConsignmentLineRow["stock_items"] | ConsignmentLineRow["stock_items"][];
    };
    return { ...r, stock_items: asSingle(r.stock_items) };
  });
  return { ok: true, data: rows };
}

export async function searchCustomers(
  client: SupabaseClient,
  query: string,
): Promise<StorefrontResult<CustomerOption[]>> {
  const q = query.trim();
  if (q.length < 2) return { ok: true, data: [] };
  const { data, error } = await client
    .from("customers")
    .select("id, display_name")
    .ilike("display_name", `%${q}%`)
    .order("display_name")
    .limit(20);
  if (error) return { ok: false, error: error.message };
  return { ok: true, data: (data as CustomerOption[]) ?? [] };
}

export async function createConsignmentDraft(
  client: SupabaseClient,
  args: {
    kind: ConsignmentKind;
    purpose: ConsignmentPurpose;
    warehouseId: string;
    supplierId?: string;
    customerId?: string;
    currency: CurrencyCode;
    exchangeRate: number;
    notes?: string;
  },
): Promise<StorefrontResult<string>> {
  const { data, error } = await client.rpc("create_consignment_entry_draft", {
    p_kind: args.kind,
    p_purpose: args.purpose,
    p_warehouse_id: args.warehouseId,
    p_supplier_id: args.supplierId ?? undefined,
    p_customer_id: args.customerId ?? undefined,
    p_currency: args.currency,
    p_exchange_rate: args.exchangeRate,
    p_notes: args.notes || undefined,
  });
  if (error) return { ok: false, error: error.message };
  if (!data) return { ok: false, error: "create_consignment_entry_draft returned no id." };
  return { ok: true, data };
}

export async function addConsignmentLine(
  client: SupabaseClient,
  args: {
    entryId: string;
    stockItemId: string;
    uomId: string;
    qty: number;
    unitCost?: number;
    unitPrice?: number;
    currency?: CurrencyCode;
  },
): Promise<StorefrontResult<string>> {
  const { data, error } = await client.rpc("add_consignment_entry_line", {
    p_entry_id: args.entryId,
    p_stock_item_id: args.stockItemId,
    p_uom_id: args.uomId,
    p_qty: args.qty,
    p_unit_cost: args.unitCost ?? 0,
    p_unit_price: args.unitPrice ?? 0,
    p_currency: args.currency ?? undefined,
  });
  if (error) return { ok: false, error: error.message };
  if (!data) return { ok: false, error: "add_consignment_entry_line returned no id." };
  return { ok: true, data };
}

export async function submitConsignmentEntry(
  client: SupabaseClient,
  entryId: string,
): Promise<StorefrontResult<string>> {
  const { data, error } = await client.rpc("submit_consignment_entry", {
    p_entry_id: entryId,
  });
  if (error) return { ok: false, error: error.message };
  return { ok: true, data: data ?? entryId };
}

export async function cancelConsignmentEntry(
  client: SupabaseClient,
  entryId: string,
): Promise<StorefrontResult<string>> {
  const { data, error } = await client.rpc("cancel_consignment_entry", {
    p_entry_id: entryId,
  });
  if (error) return { ok: false, error: error.message };
  return { ok: true, data: data ?? entryId };
}
