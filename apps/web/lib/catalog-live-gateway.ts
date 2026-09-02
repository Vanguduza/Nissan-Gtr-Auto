import type { SupabaseClient } from "@gtr/supabase-client";

type QueryValue = string | number | boolean | null | undefined;

/**
 * Runtime catalog gateway.
 *
 * The client never downloads the complete EPC bundle. The gateway combines:
 * - Supabase control/commerce data, and
 * - only the requested Cloudflare R2 serving shard or signed diagram image.
 */
function gatewayBase(): string {
  const base = process.env.NEXT_PUBLIC_SUPABASE_URL?.replace(/\/$/, "");
  if (!base) throw new Error("NEXT_PUBLIC_SUPABASE_URL is not configured");
  return `${base}/functions/v1/catalog-live-r2`;
}

export async function catalogGatewayGet<T>(
  client: SupabaseClient,
  route: string,
  params: Record<string, QueryValue> = {},
): Promise<T> {
  const { data } = await client.auth.getSession();
  const token = data.session?.access_token;
  if (!token) throw new Error("Sign in required");

  const url = new URL(`${gatewayBase()}/${route.replace(/^\/+/, "")}`);
  for (const [key, value] of Object.entries(params)) {
    if (value == null || value === "") continue;
    url.searchParams.set(key, String(value));
  }

  const resp = await fetch(url, {
    method: "GET",
    headers: {
      Authorization: `Bearer ${token}`,
      apikey: process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY ?? "",
      Accept: "application/json",
    },
    cache: "no-store",
  });
  const payload = await resp.json().catch(() => ({}));
  if (!resp.ok) {
    throw new Error(
      typeof payload?.error === "string"
        ? payload.error
        : `Catalog gateway ${resp.status}`,
    );
  }
  return payload as T;
}

export type LiveCatalogPart = {
  normalized_oem_number?: string | null;
  display_oem_number?: string | null;
  name?: string | null;
  description?: string | null;
  category_name?: string | null;
  subcategory_name?: string | null;
  pnc_code?: string | null;
  diagram_id?: string | null;
  section_id?: string | null;
  chassis_code?: string | null;
  engine_code?: string | null;
  applicability?: unknown;
};

export type LiveCustomerCatalogItem = {
  stock_item_id: string;
  name: string;
  category: string | null;
  subcategory: string | null;
  stock: { state: "in_stock" | "low" | "backorder"; qty: number };
  price: { amount: number; currency: string } | null;
  fitment_status: "VERIFIED_FIT" | "FITMENT_UNRESOLVED";
  vehicle_id: string | null;
  /** Internal routing identity only. Never render in customer UI. */
  internal_catalog_ref: string | null;
};

export type CustomerStockResponse = {
  source: "r2_epc+supabase_commerce";
  release: string;
  vehicle_id: string;
  results: LiveCustomerCatalogItem[];
  full_catalog_download_required: false;
};

export type StaffPartsResponse = {
  source: "r2_live";
  release: string;
  row_count: number;
  parts: LiveCatalogPart[];
  full_catalog_download_required: false;
};

export type DiagramImageResponse = {
  signed_url: string;
  expires_in: number;
  diagram_id: string;
  object_key: string;
  sha256: string | null;
  full_catalog_download_required: false;
};
