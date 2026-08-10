import type { Database, SupabaseClient } from "@gtr/supabase-client";
import {
  loadOwnCustomer,
  type StorefrontResult,
} from "@/lib/customer-storefront";

export type ProductReviewStatus =
  Database["public"]["Enums"]["product_review_status"];

export type ProductReviewRow =
  Database["public"]["Tables"]["customer_product_reviews"]["Row"] & {
    stock_items?: {
      id: string;
      oem_part_number: string;
      description: string | null;
    } | null;
    customers?: { display_name: string } | null;
  };

export type ProductReviewPhotoRow =
  Database["public"]["Tables"]["customer_product_review_photos"]["Row"];

export type ProductReviewStats = {
  stock_item_id: string;
  avg_rating: number;
  review_count: number;
};

export const REVIEW_PHOTOS_BUCKET = "review-photos";

function asSingle<T>(value: T | T[] | null | undefined): T | null {
  if (value == null) return null;
  return Array.isArray(value) ? (value[0] ?? null) : value;
}

export async function listOwnReviews(
  client: SupabaseClient,
): Promise<StorefrontResult<ProductReviewRow[]>> {
  const customer = await loadOwnCustomer(client);
  if (!customer.ok) return customer;
  if (!customer.data) return { ok: true, data: [] };

  const { data, error } = await client
    .from("customer_product_reviews")
    .select(
      "id, customer_id, stock_item_id, rating, body, status, created_at, updated_at, stock_items ( id, oem_part_number, description )",
    )
    .eq("customer_id", customer.data.id)
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

export async function getProductReviewStats(
  client: SupabaseClient,
  args: { stockItemId?: string; oem?: string },
): Promise<StorefrontResult<ProductReviewStats | null>> {
  const { data, error } = await client.rpc("get_product_review_stats", {
    p_stock_item_id: args.stockItemId ?? undefined,
    p_oem_part_number: args.oem ?? undefined,
  });
  if (error) return { ok: false, error: error.message };
  const row = (data as ProductReviewStats[] | null)?.[0] ?? null;
  return { ok: true, data: row };
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

/** Upload image to review-photos bucket then register via RPC. Path: {review_id}/{file}. */
export async function uploadReviewPhoto(
  client: SupabaseClient,
  args: { reviewId: string; file: File; sortOrder?: number },
): Promise<StorefrontResult<string>> {
  const reviewId = args.reviewId.trim();
  if (!reviewId) return { ok: false, error: "review_id required." };

  const ext =
    args.file.name.split(".").pop()?.toLowerCase().replace(/[^a-z0-9]/g, "") ||
    "jpg";
  const safeExt = ["jpg", "jpeg", "png", "webp"].includes(ext) ? ext : "jpg";
  const objectPath = `${reviewId}/${crypto.randomUUID()}.${safeExt}`;

  const { error: upErr } = await client.storage
    .from(REVIEW_PHOTOS_BUCKET)
    .upload(objectPath, args.file, {
      cacheControl: "3600",
      upsert: false,
      contentType: args.file.type || `image/${safeExt}`,
    });
  if (upErr) return { ok: false, error: upErr.message };

  const { data, error } = await client.rpc("add_customer_product_review_photo", {
    p_review_id: reviewId,
    p_storage_path: objectPath,
    p_sort_order: args.sortOrder ?? 0,
  });
  if (error) return { ok: false, error: error.message };
  if (!data) {
    return { ok: false, error: "add_customer_product_review_photo returned no id." };
  }
  return { ok: true, data };
}

export async function listApprovedReviewPhotoUrlsForOem(
  client: SupabaseClient,
  oem: string,
): Promise<StorefrontResult<string[]>> {
  const reviews = await listApprovedReviewsForOem(client, oem);
  if (!reviews.ok) return reviews;
  if (!reviews.data.length) return { ok: true, data: [] };

  const urls: string[] = [];
  for (const review of reviews.data) {
    const photos = await listReviewPhotos(client, review.id);
    if (!photos.ok) continue;
    for (const ph of photos.data) {
      const url = await signedReviewPhotoUrl(client, ph.storage_path);
      if (url) urls.push(url);
    }
  }
  return { ok: true, data: urls.slice(0, 6) };
}

export async function listReviewPhotos(
  client: SupabaseClient,
  reviewId: string,
): Promise<StorefrontResult<ProductReviewPhotoRow[]>> {
  const { data, error } = await client
    .from("customer_product_review_photos")
    .select("id, review_id, storage_path, sort_order, created_at")
    .eq("review_id", reviewId)
    .order("sort_order")
    .order("created_at");
  if (error) return { ok: false, error: error.message };
  return { ok: true, data: (data as ProductReviewPhotoRow[]) ?? [] };
}

export async function signedReviewPhotoUrl(
  client: SupabaseClient,
  storagePath: string,
): Promise<string | null> {
  const path = storagePath.trim().replace(/^review-photos\//, "");
  if (!path) return null;
  const { data, error } = await client.storage
    .from(REVIEW_PHOTOS_BUCKET)
    .createSignedUrl(path, 3600);
  if (error || !data?.signedUrl) return null;
  return data.signedUrl;
}

export async function listPendingReviewsForStaff(
  client: SupabaseClient,
): Promise<StorefrontResult<ProductReviewRow[]>> {
  const { data, error } = await client
    .from("customer_product_reviews")
    .select(
      "id, customer_id, stock_item_id, rating, body, status, created_at, updated_at, stock_items ( id, oem_part_number, description ), customers ( display_name )",
    )
    .eq("status", "pending")
    .order("created_at", { ascending: true })
    .limit(100);
  if (error) return { ok: false, error: error.message };

  const rows: ProductReviewRow[] = (data ?? []).map((row) => {
    const r = row as ProductReviewRow & {
      stock_items?: ProductReviewRow["stock_items"] | ProductReviewRow["stock_items"][];
      customers?: ProductReviewRow["customers"] | ProductReviewRow["customers"][];
    };
    return {
      ...r,
      stock_items: asSingle(r.stock_items),
      customers: asSingle(r.customers),
    };
  });
  return { ok: true, data: rows };
}

export async function moderateProductReview(
  client: SupabaseClient,
  args: { reviewId: string; status: "approved" | "rejected" },
): Promise<StorefrontResult<string>> {
  const { data, error } = await client.rpc("moderate_customer_product_review", {
    p_review_id: args.reviewId,
    p_status: args.status,
  });
  if (error) return { ok: false, error: error.message };
  if (!data) {
    return { ok: false, error: "moderate_customer_product_review returned no id." };
  }
  return { ok: true, data };
}

export function reviewStatusLabel(status: ProductReviewStatus): string {
  return status;
}
