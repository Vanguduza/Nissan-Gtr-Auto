import type { SupabaseClient } from "@gtr/supabase-client";
import {
  PRODUCT_IMAGES_BUCKET,
  productImagePublicUrl,
} from "@/lib/catalog-product";

export type StaffProductPageRow = {
  stock_item_id: string;
  oem_part_number: string;
  catalog_title: string;
  unit_price: number | null;
  currency: string;
  qty_saleable: number;
  discount_kind: "none" | "percent" | "amount";
  discount_value: number;
  discount_description: string | null;
  primary_image_path: string | null;
  image_count: number;
  /** Manual home-rail pins (backup control; algorithm still fills remaining slots). */
  pin_featured: boolean;
  pin_movers: boolean;
  pin_newest: boolean;
  pin_sort: number;
};

export type StaffProductImage = {
  id: string;
  storage_path: string;
  is_primary: boolean;
  sort_order: number;
  public_url: string;
};

type RpcClient = {
  rpc: (
    fn: string,
    args?: Record<string, unknown>,
  ) => Promise<{ data: unknown; error: { message: string } | null }>;
  from: (t: string) => {
    select: (cols: string) => {
      eq: (
        col: string,
        val: string,
      ) => {
        order: (
          col: string,
          opts: { ascending: boolean },
        ) => {
          order: (
            col: string,
            opts: { ascending: boolean },
          ) => Promise<{ data: unknown; error: { message: string } | null }>;
        };
      };
    };
  };
  storage: SupabaseClient["storage"];
};

function asRpc(client: SupabaseClient): RpcClient {
  return client as unknown as RpcClient;
}

export async function listStaffProductPages(
  client: SupabaseClient,
  query?: string,
): Promise<
  { ok: true; data: StaffProductPageRow[] } | { ok: false; error: string }
> {
  const { data, error } = await asRpc(client).rpc("list_staff_product_pages", {
    p_query: query?.trim() || null,
    p_limit: 100,
  });
  if (error) return { ok: false, error: error.message };
  const rows = (
    (data ?? []) as Array<Record<string, unknown>>
  ).map((r) => ({
    stock_item_id: String(r.stock_item_id),
    oem_part_number: String(r.oem_part_number),
    catalog_title: String(r.catalog_title),
    unit_price: r.unit_price != null ? Number(r.unit_price) : null,
    currency: String(r.currency ?? "USD"),
    qty_saleable: Number(r.qty_saleable ?? 0),
    discount_kind:
      (r.discount_kind as StaffProductPageRow["discount_kind"]) ?? "none",
    discount_value: Number(r.discount_value ?? 0),
    discount_description: (r.discount_description as string | null) ?? null,
    primary_image_path: (r.primary_image_path as string | null) ?? null,
    image_count: Number(r.image_count ?? 0),
    pin_featured: Boolean(r.pin_featured),
    pin_movers: Boolean(r.pin_movers),
    pin_newest: Boolean(r.pin_newest),
    pin_sort: Number(r.pin_sort ?? 0),
  }));
  return { ok: true, data: rows };
}

export async function saveStaffProductPage(
  client: SupabaseClient,
  input: {
    stockItemId: string;
    unitPrice: number;
    discountKind: "none" | "percent" | "amount";
    discountValue: number;
    discountDescription: string | null;
    pinFeatured: boolean;
    pinMovers: boolean;
    pinNewest: boolean;
    pinSort: number;
  },
): Promise<{ ok: true } | { ok: false; error: string }> {
  const { error } = await asRpc(client).rpc("upsert_staff_product_page", {
    p_stock_item_id: input.stockItemId,
    p_unit_price: input.unitPrice,
    p_discount_kind: input.discountKind,
    p_discount_value: input.discountValue,
    p_discount_description: input.discountDescription,
    p_pin_featured: input.pinFeatured,
    p_pin_movers: input.pinMovers,
    p_pin_newest: input.pinNewest,
    p_pin_sort: Math.min(999, Math.max(0, Math.floor(input.pinSort) || 0)),
  });
  if (error) return { ok: false, error: error.message };
  return { ok: true };
}

export async function listStaffProductImages(
  client: SupabaseClient,
  stockItemId: string,
): Promise<
  { ok: true; data: StaffProductImage[] } | { ok: false; error: string }
> {
  const { data, error } = await asRpc(client)
    .from("stock_item_images")
    .select("id, storage_path, is_primary, sort_order")
    .eq("stock_item_id", stockItemId)
    .order("is_primary", { ascending: false })
    .order("sort_order", { ascending: true });

  if (error) return { ok: false, error: error.message };
  const rows = (
    (data ?? []) as Array<{
      id: string;
      storage_path: string;
      is_primary: boolean;
      sort_order: number;
    }>
  ).map((row) => ({
    id: row.id,
    storage_path: row.storage_path,
    is_primary: row.is_primary,
    sort_order: row.sort_order,
    public_url: productImagePublicUrl(client, row.storage_path),
  }));
  return { ok: true, data: rows };
}

export async function uploadStaffProductImage(
  client: SupabaseClient,
  input: {
    stockItemId: string;
    file: File;
    asPrimary?: boolean;
  },
): Promise<{ ok: true; imageId: string } | { ok: false; error: string }> {
  const ext = (input.file.name.split(".").pop() || "jpg").toLowerCase();
  const safeExt = ["jpg", "jpeg", "png", "webp"].includes(ext) ? ext : "jpg";
  const path = `${input.stockItemId}/${crypto.randomUUID()}.${safeExt}`;

  const { error: upErr } = await client.storage
    .from(PRODUCT_IMAGES_BUCKET)
    .upload(path, input.file, {
      contentType: input.file.type || `image/${safeExt}`,
      upsert: false,
    });
  if (upErr) return { ok: false, error: upErr.message };

  const { data, error } = await asRpc(client).rpc("register_stock_item_image", {
    p_stock_item_id: input.stockItemId,
    p_storage_path: path,
    p_is_primary: Boolean(input.asPrimary),
    p_sort_order: 0,
  });
  if (error) {
    await client.storage.from(PRODUCT_IMAGES_BUCKET).remove([path]);
    return { ok: false, error: error.message };
  }
  return { ok: true, imageId: String(data) };
}

export async function setStaffProductPrimaryImage(
  client: SupabaseClient,
  imageId: string,
): Promise<{ ok: true } | { ok: false; error: string }> {
  const { error } = await asRpc(client).rpc("set_stock_item_primary_image", {
    p_image_id: imageId,
  });
  if (error) return { ok: false, error: error.message };
  return { ok: true };
}

export async function deleteStaffProductImage(
  client: SupabaseClient,
  image: StaffProductImage,
): Promise<{ ok: true } | { ok: false; error: string }> {
  const { error } = await asRpc(client).rpc("delete_stock_item_image", {
    p_image_id: image.id,
  });
  if (error) return { ok: false, error: error.message };
  await client.storage
    .from(PRODUCT_IMAGES_BUCKET)
    .remove([image.storage_path.replace(/^product-images\//, "")]);
  return { ok: true };
}
