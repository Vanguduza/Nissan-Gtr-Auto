import type { Database, SupabaseClient } from "@gtr/supabase-client";
import {
  listWarehouses,
  requireSession,
  searchStockItems,
  type StockItemOption,
  type StorefrontResult,
  type WarehouseOption,
} from "@/lib/staff-warehouse";

export {
  listWarehouses,
  requireSession,
  searchStockItems,
};
export type { StockItemOption, WarehouseOption };

export type WarehouseBinRow =
  Database["public"]["Tables"]["warehouse_bins"]["Row"];

export async function listWarehouseBins(
  client: SupabaseClient,
  warehouseId: string,
): Promise<StorefrontResult<WarehouseBinRow[]>> {
  const { data, error } = await client
    .from("warehouse_bins")
    .select("*")
    .eq("warehouse_id", warehouseId)
    .order("pick_path_seq")
    .order("code")
    .limit(200);
  if (error) return { ok: false, error: error.message };
  return { ok: true, data: (data as WarehouseBinRow[]) ?? [] };
}

export async function createWarehouseBin(
  client: SupabaseClient,
  args: {
    warehouseId: string;
    code: string;
    name: string;
    pickPathSeq?: number;
    aisle?: string;
    rack?: string;
    shelf?: string;
  },
): Promise<StorefrontResult<string>> {
  const { data, error } = await client.rpc("create_warehouse_bin", {
    p_warehouse_id: args.warehouseId,
    p_code: args.code,
    p_name: args.name,
    p_pick_path_seq: args.pickPathSeq ?? 100,
    p_aisle: args.aisle || undefined,
    p_rack: args.rack || undefined,
    p_shelf: args.shelf || undefined,
  });
  if (error) return { ok: false, error: error.message };
  if (!data) return { ok: false, error: "create_warehouse_bin returned no id." };
  return { ok: true, data };
}

export async function updateWarehouseBin(
  client: SupabaseClient,
  args: {
    binId: string;
    name?: string;
    pickPathSeq?: number;
    aisle?: string;
    rack?: string;
    shelf?: string;
    isActive?: boolean;
  },
): Promise<StorefrontResult<string>> {
  const { data, error } = await client.rpc("update_warehouse_bin", {
    p_bin_id: args.binId,
    p_name: args.name ?? undefined,
    p_pick_path_seq: args.pickPathSeq ?? undefined,
    p_aisle: args.aisle ?? undefined,
    p_rack: args.rack ?? undefined,
    p_shelf: args.shelf ?? undefined,
    p_is_active: args.isActive ?? undefined,
  });
  if (error) return { ok: false, error: error.message };
  return { ok: true, data: data ?? args.binId };
}

export async function deactivateWarehouseBin(
  client: SupabaseClient,
  binId: string,
): Promise<StorefrontResult<string>> {
  const { data, error } = await client.rpc("deactivate_warehouse_bin", {
    p_bin_id: binId,
  });
  if (error) return { ok: false, error: error.message };
  return { ok: true, data: data ?? binId };
}

export async function setStockLevelBin(
  client: SupabaseClient,
  args: {
    stockItemId: string;
    warehouseId: string;
    binId: string | null;
  },
): Promise<StorefrontResult<string>> {
  const { data, error } = await client.rpc("set_stock_level_bin", {
    p_stock_item_id: args.stockItemId,
    p_warehouse_id: args.warehouseId,
    p_bin_id: args.binId ?? undefined,
  });
  if (error) return { ok: false, error: error.message };
  if (!data) return { ok: false, error: "set_stock_level_bin returned no id." };
  return { ok: true, data };
}
