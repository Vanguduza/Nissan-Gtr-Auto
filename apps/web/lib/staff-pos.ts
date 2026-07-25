import type { Database, SupabaseClient } from "@gtr/supabase-client";
import {
  requireSession,
  type StorefrontResult,
} from "@/lib/customer-storefront";

export { requireSession };

export type CurrencyCode = Database["public"]["Enums"]["currency_code"];
export type FulfillmentMode = Database["public"]["Enums"]["fulfillment_mode"];

export type WarehouseOption = {
  id: string;
  code: string;
  name: string;
};

export type StockItemOption = {
  id: string;
  oem_part_number: string;
  description: string | null;
  base_uom_id: string | null;
};

export type PosCartRow = Database["public"]["Tables"]["pos_carts"]["Row"];

export type PosCartLineRow =
  Database["public"]["Tables"]["pos_cart_lines"]["Row"] & {
    stock_items?: { oem_part_number: string; description: string | null } | null;
  };

function asSingle<T>(value: T | T[] | null | undefined): T | null {
  if (value == null) return null;
  return Array.isArray(value) ? (value[0] ?? null) : value;
}

export async function listSaleableWarehouses(
  client: SupabaseClient,
): Promise<StorefrontResult<WarehouseOption[]>> {
  const { data, error } = await client
    .from("warehouses")
    .select("id, code, name")
    .eq("is_active", true)
    .eq("is_quarantine", false)
    .order("code");
  if (error) return { ok: false, error: error.message };
  return { ok: true, data: (data as WarehouseOption[]) ?? [] };
}

/** OEM / description search (typed input only — no browser QR). */
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
      .select("id, oem_part_number, description, base_uom_id")
      .eq("id", q)
      .limit(1);
    if (error) return { ok: false, error: error.message };
    return { ok: true, data: (data as StockItemOption[]) ?? [] };
  }

  const { data, error } = await client
    .from("stock_items")
    .select("id, oem_part_number, description, base_uom_id")
    .or(`oem_part_number.ilike.%${q}%,description.ilike.%${q}%`)
    .limit(20);
  if (error) return { ok: false, error: error.message };
  return { ok: true, data: (data as StockItemOption[]) ?? [] };
}

export async function createPosCart(
  client: SupabaseClient,
  args: {
    warehouseId: string;
    currency: CurrencyCode;
    fulfillmentMode?: FulfillmentMode;
    customerId?: string | null;
  },
): Promise<StorefrontResult<string>> {
  const { data, error } = await client.rpc("create_pos_cart", {
    p_warehouse_id: args.warehouseId,
    p_currency: args.currency,
    p_fulfillment_mode: args.fulfillmentMode ?? "immediate",
    p_customer_id: args.customerId || undefined,
  });
  if (error) return { ok: false, error: error.message };
  if (!data) return { ok: false, error: "create_pos_cart returned no id." };
  return { ok: true, data };
}

export async function loadPosCart(
  client: SupabaseClient,
  cartId: string,
): Promise<StorefrontResult<PosCartRow | null>> {
  const { data, error } = await client
    .from("pos_carts")
    .select("*")
    .eq("id", cartId)
    .maybeSingle();
  if (error) return { ok: false, error: error.message };
  return { ok: true, data: data as PosCartRow | null };
}

export async function loadPosCartLines(
  client: SupabaseClient,
  cartId: string,
): Promise<StorefrontResult<PosCartLineRow[]>> {
  const { data, error } = await client
    .from("pos_cart_lines")
    .select(
      "*, stock_items ( oem_part_number, description )",
    )
    .eq("cart_id", cartId)
    .order("created_at");
  if (error) return { ok: false, error: error.message };
  const rows = (data ?? []).map((row) => ({
    ...row,
    stock_items: asSingle(row.stock_items),
  }));
  return { ok: true, data: rows as PosCartLineRow[] };
}

export async function addCartLine(
  client: SupabaseClient,
  args: {
    cartId: string;
    stockItemId: string;
    uomId: string;
    qty: number;
  },
): Promise<StorefrontResult<string>> {
  const { data, error } = await client.rpc("add_cart_line", {
    p_cart_id: args.cartId,
    p_stock_item_id: args.stockItemId,
    p_uom_id: args.uomId,
    p_qty: args.qty,
  });
  if (error) return { ok: false, error: error.message };
  if (!data) return { ok: false, error: "add_cart_line returned no id." };
  return { ok: true, data };
}

export async function checkoutPosCart(
  client: SupabaseClient,
  cartId: string,
): Promise<StorefrontResult<string>> {
  const { data, error } = await client.rpc("checkout_pos_cart", {
    p_cart_id: cartId,
  });
  if (error) return { ok: false, error: error.message };
  if (!data) return { ok: false, error: "checkout_pos_cart returned no id." };
  return { ok: true, data };
}
