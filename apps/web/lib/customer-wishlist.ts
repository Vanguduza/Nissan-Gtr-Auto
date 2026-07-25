import type { Database, SupabaseClient } from "@gtr/supabase-client";
import {
  ensureOpenCart,
  type StorefrontResult,
} from "@/lib/customer-storefront";

export type WishlistItemRow =
  Database["public"]["Tables"]["customer_wishlist_items"]["Row"] & {
    stock_items?: {
      id: string;
      oem_part_number: string;
      description: string | null;
    } | null;
  };

function asSingle<T>(value: T | T[] | null | undefined): T | null {
  if (value == null) return null;
  return Array.isArray(value) ? (value[0] ?? null) : value;
}

export async function listWishlistItems(
  client: SupabaseClient,
): Promise<StorefrontResult<WishlistItemRow[]>> {
  const { data, error } = await client
    .from("customer_wishlist_items")
    .select(
      "id, customer_id, stock_item_id, notify_when_in_stock, created_at, stock_items ( id, oem_part_number, description )",
    )
    .order("created_at", { ascending: false })
    .limit(100);
  if (error) return { ok: false, error: error.message };

  const rows: WishlistItemRow[] = (data ?? []).map((row) => {
    const r = row as WishlistItemRow & {
      stock_items?: WishlistItemRow["stock_items"] | WishlistItemRow["stock_items"][];
    };
    return { ...r, stock_items: asSingle(r.stock_items) };
  });
  return { ok: true, data: rows };
}

export async function isOemOnWishlist(
  client: SupabaseClient,
  oem: string,
): Promise<StorefrontResult<boolean>> {
  const needle = oem.trim();
  if (!needle) return { ok: true, data: false };

  const { data: item, error: itemErr } = await client
    .from("stock_items")
    .select("id")
    .ilike("oem_part_number", needle)
    .limit(1)
    .maybeSingle();
  if (itemErr) return { ok: false, error: itemErr.message };
  if (!item) return { ok: true, data: false };

  const { data, error } = await client
    .from("customer_wishlist_items")
    .select("id")
    .eq("stock_item_id", item.id)
    .limit(1)
    .maybeSingle();
  if (error) return { ok: false, error: error.message };
  return { ok: true, data: !!data };
}

export async function addWishlistItem(
  client: SupabaseClient,
  args: { stockItemId?: string; oem?: string },
): Promise<StorefrontResult<string>> {
  const { data, error } = await client.rpc("add_customer_wishlist_item", {
    p_stock_item_id: args.stockItemId ?? undefined,
    p_oem_part_number: args.oem ?? undefined,
  });
  if (error) return { ok: false, error: error.message };
  if (!data) return { ok: false, error: "add_customer_wishlist_item returned no id." };
  return { ok: true, data };
}

export async function removeWishlistItem(
  client: SupabaseClient,
  args: { stockItemId?: string; oem?: string; wishlistId?: string },
): Promise<StorefrontResult<true>> {
  const { error } = await client.rpc("remove_customer_wishlist_item", {
    p_stock_item_id: args.stockItemId ?? undefined,
    p_oem_part_number: args.oem ?? undefined,
    p_wishlist_id: args.wishlistId ?? undefined,
  });
  if (error) return { ok: false, error: error.message };
  return { ok: true, data: true };
}

export async function setWishlistNotifyWhenInStock(
  client: SupabaseClient,
  args: {
    notify: boolean;
    wishlistId?: string;
    stockItemId?: string;
    oem?: string;
  },
): Promise<StorefrontResult<string>> {
  const { data, error } = await client.rpc("set_wishlist_notify_when_in_stock", {
    p_notify: args.notify,
    p_wishlist_id: args.wishlistId ?? undefined,
    p_stock_item_id: args.stockItemId ?? undefined,
    p_oem_part_number: args.oem ?? undefined,
  });
  if (error) return { ok: false, error: error.message };
  if (!data) {
    return { ok: false, error: "set_wishlist_notify_when_in_stock returned no id." };
  }
  return { ok: true, data };
}

/** Move wishlist item into open customer cart via wishlist_move_to_cart. */
export async function moveWishlistItemToCart(
  client: SupabaseClient,
  args: {
    wishlistId?: string;
    stockItemId?: string;
    oem?: string;
    qty?: number;
    removeFromWishlist?: boolean;
  },
): Promise<StorefrontResult<{ cartId: string; lineId: string }>> {
  const cart = await ensureOpenCart(client);
  if (!cart.ok) return cart;

  const { data, error } = await client.rpc("wishlist_move_to_cart", {
    p_cart_id: cart.data.id,
    p_qty: args.qty ?? 1,
    p_remove_from_wishlist: args.removeFromWishlist ?? true,
    p_wishlist_id: args.wishlistId ?? undefined,
    p_stock_item_id: args.stockItemId ?? undefined,
    p_oem_part_number: args.oem ?? undefined,
  });
  if (error) return { ok: false, error: error.message };
  if (!data) return { ok: false, error: "wishlist_move_to_cart returned no line id." };
  return { ok: true, data: { cartId: cart.data.id, lineId: data } };
}
