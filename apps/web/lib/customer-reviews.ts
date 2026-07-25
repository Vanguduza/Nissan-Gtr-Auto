import type { Database, SupabaseClient } from "@gtr/supabase-client";
import type { StorefrontResult } from "@/lib/customer-storefront";

export type ProductReviewStatus =
  Database["public"]["Enums"]["product_review_status"];

export type ProductReviewRow =
  Database["public"]["Tables"]["customer_product_reviews"]["Row"] & {
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

export async function listOwnReviews(
  client: SupabaseClient,
): Promise<StorefrontResult<ProductReviewRow[]>> {
  const { data, error } = await client
    .from("customer_product_reviews")
    .select(
      "id, customer_id, stock_item_id, rating, body, status, created_at, updated_at, stock_items ( id, oem_part_number, description )",
    )
    .order("created_at", { ascending: false })
    .limit(100);
  if (error) return { ok: false, error: error.message };

  const rows: ProductReviewRow[] = (data ?? []).map((row) => {
    const r = row as ProductReviewRow & {
      stock_items?: ProductReviewRow["stock_items"] | ProductReviewRow["stock_items"][];
    };
    return { ...r, stock_items: asSingle(r.stock_items) };
  });
  return { ok: true, data: rows };
}

export async function listApprovedReviewsForOem(
  client: SupabaseClient,
  oem: string,
): Promise<StorefrontResult<ProductReviewRow[]>> {
  const needle = oem.trim();
  if (!needle) return { ok: true, data: [] };

  const { data: item, error: itemErr } = await client
    .from("stock_items")
    .select("id")
    .ilike("oem_part_number", needle)
    .limit(1)
    .maybeSingle();
  if (itemErr) return { ok: false, error: itemErr.message };
  if (!item) return { ok: true, data: [] };

  const { data, error } = await client
    .from("customer_product_reviews")
    .select(
      "id, customer_id, stock_item_id, rating, body, status, created_at, updated_at",
    )
    .eq("stock_item_id", item.id)
    .eq("status", "approved")
    .order("created_at", { ascending: false })
    .limit(40);
  if (error) return { ok: false, error: error.message };
  return { ok: true, data: (data as ProductReviewRow[]) ?? [] };
}

export async function submitProductReview(
  client: SupabaseClient,
  args: {
    rating: number;
    body?: string;
    stockItemId?: string;
    oem?: string;
  },
): Promise<StorefrontResult<string>> {
  const { data, error } = await client.rpc("submit_customer_product_review", {
    p_rating: args.rating,
    p_body: args.body ?? "",
    p_stock_item_id: args.stockItemId ?? undefined,
    p_oem_part_number: args.oem ?? undefined,
  });
  if (error) return { ok: false, error: error.message };
  if (!data) return { ok: false, error: "submit_customer_product_review returned no id." };
  return { ok: true, data };
}

export function reviewStatusLabel(status: ProductReviewStatus): string {
  return status;
}
