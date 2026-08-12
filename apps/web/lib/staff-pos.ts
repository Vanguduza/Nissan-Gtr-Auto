import type { Database, SupabaseClient } from "@gtr/supabase-client";
import { receiptContactsForCheckout } from "@gtr/shared";
import {
  requireSession,
  type StorefrontResult,
} from "@/lib/customer-storefront";
import {
  searchCatalog,
  type PartHit,
  type SearchMode,
} from "@/lib/catalog-search";

export { requireSession };
export type { SearchMode, PartHit };

export type CurrencyCode = Database["public"]["Enums"]["currency_code"];
export type FulfillmentMode = Database["public"]["Enums"]["fulfillment_mode"];

export type WarehouseOption = {
  id: string;
  code: string;
  name: string;
  role_code: string | null;
};

/** POS picks only from WH2 storefloor — WH1 is receiving-only. */
export function isPosSaleableWarehouse(w: {
  role_code?: string | null;
  code?: string;
  is_quarantine?: boolean;
  is_active?: boolean;
}): boolean {
  if (w.is_active === false) return false;
  if (w.is_quarantine) return false;
  return w.role_code === "WH2" || w.code === "WH2";
}

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

export type PosScanSession = {
  sessionId: string;
  pairingCode: string;
  expiresAt: string;
};

export type CheckoutPosResult = {
  invoiceId: string;
  customerId: string | null;
  receiptEmail: string | null;
  receiptWhatsappE164: string | null;
  hadCustomerBeforeCheckout: boolean;
  bindMessage: string;
};

function asSingle<T>(value: T | T[] | null | undefined): T | null {
  if (value == null) return null;
  return Array.isArray(value) ? (value[0] ?? null) : value;
}

export function checkoutBindMessage(args: {
  hadCustomerBeforeCheckout: boolean;
  customerId: string | null;
  receiptEmail: string | null;
  receiptWhatsappE164: string | null;
}): string {
  if (args.hadCustomerBeforeCheckout && args.customerId) {
    return "Customer was already on cart";
  }
  if (args.customerId) {
    return "Bound to registered / trade account";
  }
  if (args.receiptEmail || args.receiptWhatsappE164) {
    return "Walk-in — no unique account match (link manually if needed)";
  }
  return "Walk-in — no receipt contacts";
}

export async function listSaleableWarehouses(
  client: SupabaseClient,
): Promise<StorefrontResult<WarehouseOption[]>> {
  // Prefer role_code WH2 (storefloor). Keep quarantine + inactive out of POS.
  // Client filter also accepts code=WH2 for legacy rows missing role_code.
  const { data, error } = await client
    .from("warehouses")
    .select("id, code, name, role_code, is_quarantine, is_active")
    .eq("is_active", true)
    .eq("is_quarantine", false)
    .or("role_code.eq.WH2,code.eq.WH2")
    .order("code");
  if (error) return { ok: false, error: error.message };
  const rows = ((data as Array<WarehouseOption & {
    is_quarantine?: boolean;
    is_active?: boolean;
  }>) ?? []).filter(isPosSaleableWarehouse);
  return {
    ok: true,
    data: rows.map(({ id, code, name, role_code }) => ({
      id,
      code,
      name,
      role_code: role_code ?? null,
    })),
  };
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

/**
 * Catalog browse via dual-read search (standalone — no scan session).
 * Hits are identity only; saleable qty comes from stock SoR (not Meili).
 */
export async function searchPosCatalog(
  client: SupabaseClient,
  mode: SearchMode,
  query: string,
): Promise<StorefrontResult<PartHit[]>> {
  const q = query.trim();
  if (q.length < 2) return { ok: true, data: [] };
  const res = await searchCatalog(client, mode, q);
  if (!res.ok) return res;

  const parts: PartHit[] = [];
  for (const hit of res.data.results) {
    if (hit.type === "part") {
      parts.push(hit);
    } else if (hit.type === "vehicle" || hit.type === "pnc") {
      for (const f of hit.fitments ?? []) {
        if (f.type === "part") parts.push(f);
      }
    }
  }
  return { ok: true, data: parts };
}

export async function lookupStockItemByOem(
  client: SupabaseClient,
  oemPartNumber: string,
): Promise<StorefrontResult<StockItemOption>> {
  const oem = oemPartNumber.trim();
  if (!oem) return { ok: false, error: "OEM required." };
  const { data, error } = await client
    .from("stock_items")
    .select("id, oem_part_number, description, base_uom_id")
    .eq("oem_part_number", oem)
    .limit(1)
    .maybeSingle();
  if (error) return { ok: false, error: error.message };
  if (!data) return { ok: false, error: `Unknown part ${oem} — not in stock_items.` };
  return { ok: true, data: data as StockItemOption };
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

/** Park an open cart for later resume (Batch 1 park/hold). */
export async function parkPosCart(
  client: SupabaseClient,
  cartId: string,
): Promise<StorefrontResult<string>> {
  const { data, error } = await client.rpc("park_pos_cart", {
    p_cart_id: cartId,
  });
  if (error) return { ok: false, error: error.message };
  if (!data) return { ok: false, error: "park_pos_cart returned no id." };
  return { ok: true, data };
}

/** Resume a parked cart back to open. */
export async function resumePosCart(
  client: SupabaseClient,
  cartId: string,
): Promise<StorefrontResult<string>> {
  const { data, error } = await client.rpc("resume_pos_cart", {
    p_cart_id: cartId,
  });
  if (error) return { ok: false, error: error.message };
  if (!data) return { ok: false, error: "resume_pos_cart returned no id." };
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

/** Add catalog OEM hit → stock_items resolve → add_cart_line (no session). */
export async function addCatalogPartToCart(
  client: SupabaseClient,
  args: { cartId: string; oemPartNumber: string; qty: number },
): Promise<StorefrontResult<string>> {
  const item = await lookupStockItemByOem(client, args.oemPartNumber);
  if (!item.ok) return item;
  if (!item.data.base_uom_id) {
    return { ok: false, error: "Selected item has no base UOM." };
  }
  return addCartLine(client, {
    cartId: args.cartId,
    stockItemId: item.data.id,
    uomId: item.data.base_uom_id,
    qty: args.qty,
  });
}

export async function checkoutPosCart(
  client: SupabaseClient,
  args: {
    cartId: string;
    receiptEmail?: string | null;
    receiptWhatsappE164?: string | null;
    receiptPhoneE164?: string | null;
    /** Batch 1 §1.3 — when set, uses checkout_pos_cart_with_tenders (multi-tender settle). */
    tenders?: Array<{
      tender: string;
      amount: number;
      currency?: string;
      exchange_rate?: number;
    }> | null;
  },
): Promise<StorefrontResult<CheckoutPosResult>> {
  const cartRes = await loadPosCart(client, args.cartId);
  if (!cartRes.ok) return cartRes;
  const hadCustomerBeforeCheckout = Boolean(cartRes.data?.customer_id);

  const contacts = receiptContactsForCheckout({
    email: args.receiptEmail,
    whatsappE164: args.receiptWhatsappE164,
    phoneE164: args.receiptPhoneE164,
  });

  const tenders = (args.tenders ?? []).filter((t) => t.amount > 0);
  const useSplit = tenders.length > 0;

  const { data, error } = useSplit
    ? await client.rpc("checkout_pos_cart_with_tenders", {
        p_cart_id: args.cartId,
        p_tenders: tenders.map((t) => ({
          tender: t.tender,
          amount: t.amount,
          currency: t.currency,
          exchange_rate: t.exchange_rate,
        })),
        p_receipt_email: contacts.p_receipt_email ?? undefined,
        p_receipt_whatsapp_e164: contacts.p_receipt_whatsapp_e164 ?? undefined,
        p_receipt_phone_e164: contacts.p_receipt_phone_e164 ?? undefined,
      })
    : await client.rpc("checkout_pos_cart", {
        p_cart_id: args.cartId,
        p_receipt_email: contacts.p_receipt_email ?? undefined,
        p_receipt_whatsapp_e164: contacts.p_receipt_whatsapp_e164 ?? undefined,
        p_receipt_phone_e164: contacts.p_receipt_phone_e164 ?? undefined,
      });
  if (error) return { ok: false, error: error.message };
  if (!data) {
    return {
      ok: false,
      error: useSplit
        ? "checkout_pos_cart_with_tenders returned no id."
        : "checkout_pos_cart returned no id.",
    };
  }

  const { data: inv, error: invErr } = await client
    .from("sales_invoices")
    .select("id, customer_id, customer_email, customer_whatsapp_e164")
    .eq("id", data)
    .maybeSingle();
  if (invErr) return { ok: false, error: invErr.message };

  const customerId = inv?.customer_id ?? null;
  const receiptEmail =
    inv?.customer_email ?? contacts.p_receipt_email ?? null;
  const receiptWhatsappE164 =
    inv?.customer_whatsapp_e164 ?? contacts.p_receipt_whatsapp_e164 ?? null;

  return {
    ok: true,
    data: {
      invoiceId: data,
      customerId,
      receiptEmail,
      receiptWhatsappE164,
      hadCustomerBeforeCheckout,
      bindMessage: checkoutBindMessage({
        hadCustomerBeforeCheckout,
        customerId,
        receiptEmail,
        receiptWhatsappE164,
      }),
    },
  };
}

/** Optional companion: display pairing code only (phone claims + scans via bridge). */
export async function createPosScanSession(
  client: SupabaseClient,
  cartId: string,
): Promise<StorefrontResult<PosScanSession>> {
  const { data, error } = await client.rpc("create_pos_scan_session", {
    p_cart_id: cartId,
  });
  if (error) return { ok: false, error: error.message };
  const row = Array.isArray(data) ? data[0] : data;
  if (!row || typeof row !== "object") {
    return { ok: false, error: "create_pos_scan_session returned no row." };
  }
  const r = row as {
    session_id: string;
    pairing_code: string;
    expires_at: string;
  };
  return {
    ok: true,
    data: {
      sessionId: r.session_id,
      pairingCode: r.pairing_code,
      expiresAt: r.expires_at,
    },
  };
}

export async function revokePosScanSession(
  client: SupabaseClient,
  sessionId: string,
): Promise<StorefrontResult<string>> {
  const { data, error } = await client.rpc("revoke_pos_scan_session", {
    p_session_id: sessionId,
  });
  if (error) return { ok: false, error: error.message };
  if (!data) return { ok: false, error: "revoke_pos_scan_session returned no id." };
  return { ok: true, data };
}
