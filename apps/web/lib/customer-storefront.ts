import type { Database, SupabaseClient } from "@gtr/supabase-client";

type Currency = Database["public"]["Enums"]["currency_code"];
type FulfillmentMode = Database["public"]["Enums"]["fulfillment_mode"];
type ContipayMethod = Database["public"]["Enums"]["contipay_method"];
type PaynowMethod = Database["public"]["Enums"]["paynow_method"];

export type CartRow = Database["public"]["Tables"]["pos_carts"]["Row"];
export type CartLineRow = Database["public"]["Tables"]["pos_cart_lines"]["Row"] & {
  stock_items?: { oem_part_number: string; description: string | null } | null;
};
export type InvoiceRow = Database["public"]["Tables"]["sales_invoices"]["Row"];
export type GarageVehicleRow =
  Database["public"]["Tables"]["customer_garage_vehicles"]["Row"];

export type CustomerOrder = {
  invoice_id: string;
  document_number: string | null;
  doc_type: string;
  status: string;
  fulfillment_mode: FulfillmentMode;
  currency: Currency;
  exchange_rate_applied: number;
  subtotal: number;
  total: number;
  amount_paid: number;
  amount_open: number;
  cart_id: string | null;
  posted_at: string | null;
  pick_list_status: string | null;
  delivery_note_status: string | null;
};

export type StorefrontResult<T> =
  | { ok: true; data: T }
  | { ok: false; error: string };

const CART_KEY = "gtr.storefront.cart_id";

export function readStoredCartId(): string | null {
  if (typeof window === "undefined") return null;
  return window.localStorage.getItem(CART_KEY);
}

export function writeStoredCartId(id: string | null) {
  if (typeof window === "undefined") return;
  if (id) window.localStorage.setItem(CART_KEY, id);
  else window.localStorage.removeItem(CART_KEY);
}

export function zigExchangeRate(): number {
  const raw = process.env.NEXT_PUBLIC_ZIG_EXCHANGE_RATE;
  const n = raw ? Number(raw) : NaN;
  return Number.isFinite(n) && n > 0 ? n : 1;
}

export async function requireSession(
  client: SupabaseClient,
): Promise<StorefrontResult<{ userId: string }>> {
  const { data, error } = await client.auth.getSession();
  if (error) return { ok: false, error: error.message };
  if (!data.session) {
    return { ok: false, error: "Sign in to continue." };
  }
  return { ok: true, data: { userId: data.session.user.id } };
}

export async function resolveMainWarehouseId(
  client: SupabaseClient,
): Promise<StorefrontResult<string>> {
  const envId = process.env.NEXT_PUBLIC_DEFAULT_WAREHOUSE_ID?.trim();
  if (envId) return { ok: true, data: envId };

  const { data, error } = await client
    .from("warehouses")
    .select("id")
    .eq("is_active", true)
    .eq("is_quarantine", false)
    .eq("code", "MAIN")
    .maybeSingle();

  if (error) return { ok: false, error: error.message };
  if (data?.id) return { ok: true, data: data.id };

  const fallback = await client
    .from("warehouses")
    .select("id")
    .eq("is_active", true)
    .eq("is_quarantine", false)
    .order("code")
    .limit(1)
    .maybeSingle();

  if (fallback.error) return { ok: false, error: fallback.error.message };
  if (!fallback.data?.id) {
    return { ok: false, error: "No saleable warehouse found." };
  }
  return { ok: true, data: fallback.data.id };
}

export async function loadOpenCart(
  client: SupabaseClient,
): Promise<StorefrontResult<CartRow | null>> {
  const stored = readStoredCartId();
  if (stored) {
    const byId = await client
      .from("pos_carts")
      .select("*")
      .eq("id", stored)
      .eq("status", "open")
      .eq("channel", "storefront")
      .maybeSingle();
    if (byId.error) return { ok: false, error: byId.error.message };
    if (byId.data) return { ok: true, data: byId.data };
    writeStoredCartId(null);
  }

  const { data, error } = await client
    .from("pos_carts")
    .select("*")
    .eq("status", "open")
    .eq("channel", "storefront")
    .order("created_at", { ascending: false })
    .limit(1)
    .maybeSingle();

  if (error) return { ok: false, error: error.message };
  if (data) writeStoredCartId(data.id);
  return { ok: true, data: data ?? null };
}

export async function ensureOpenCart(
  client: SupabaseClient,
  opts: {
    currency?: Currency;
    fulfillmentMode?: FulfillmentMode;
    exchangeRate?: number;
  } = {},
): Promise<StorefrontResult<CartRow>> {
  const existing = await loadOpenCart(client);
  if (!existing.ok) return existing;
  if (existing.data) return { ok: true, data: existing.data };

  const warehouse = await resolveMainWarehouseId(client);
  if (!warehouse.ok) return warehouse;

  const currency = opts.currency ?? "USD";
  const exchangeRate =
    opts.exchangeRate ?? (currency === "ZIG" ? zigExchangeRate() : 1);

  const { data: cartId, error } = await client.rpc("create_customer_cart", {
    p_warehouse_id: warehouse.data,
    p_currency: currency,
    p_fulfillment_mode: opts.fulfillmentMode ?? "immediate",
    p_exchange_rate: exchangeRate,
  });

  if (error) return { ok: false, error: error.message };
  if (!cartId) return { ok: false, error: "Cart create returned no id." };

  writeStoredCartId(cartId);
  const loaded = await client
    .from("pos_carts")
    .select("*")
    .eq("id", cartId)
    .single();
  if (loaded.error) return { ok: false, error: loaded.error.message };
  return { ok: true, data: loaded.data };
}

export async function loadCartLines(
  client: SupabaseClient,
  cartId: string,
): Promise<StorefrontResult<CartLineRow[]>> {
  const { data, error } = await client
    .from("pos_cart_lines")
    .select(
      "*, stock_items ( oem_part_number, description )",
    )
    .eq("cart_id", cartId)
    .order("created_at", { ascending: true });

  if (error) return { ok: false, error: error.message };
  return { ok: true, data: (data ?? []) as CartLineRow[] };
}

export async function addCartLineByOem(
  client: SupabaseClient,
  oem: string,
  qty = 1,
  cartOpts?: Parameters<typeof ensureOpenCart>[1],
): Promise<StorefrontResult<{ cartId: string; lineId: string }>> {
  const cart = await ensureOpenCart(client, cartOpts);
  if (!cart.ok) return cart;

  const { data: item, error: itemErr } = await client
    .from("stock_items")
    .select("id, base_uom_id, oem_part_number")
    .eq("oem_part_number", oem.trim())
    .maybeSingle();

  if (itemErr) return { ok: false, error: itemErr.message };
  if (!item) {
    return {
      ok: false,
      error: `Part ${oem} is not in inventory yet.`,
    };
  }
  if (!item.base_uom_id) {
    return { ok: false, error: `Part ${oem} has no base UOM.` };
  }

  const { data: lineId, error } = await client.rpc("add_customer_cart_line", {
    p_cart_id: cart.data.id,
    p_stock_item_id: item.id,
    p_uom_id: item.base_uom_id,
    p_qty: qty,
  });

  if (error) return { ok: false, error: error.message };
  if (!lineId) return { ok: false, error: "Add line returned no id." };
  return { ok: true, data: { cartId: cart.data.id, lineId } };
}

export async function checkoutCustomerCart(
  client: SupabaseClient,
  cartId: string,
): Promise<StorefrontResult<string>> {
  const { data, error } = await client.rpc("checkout_customer_cart", {
    p_cart_id: cartId,
  });
  if (error) return { ok: false, error: error.message };
  if (!data) return { ok: false, error: "Checkout returned no invoice id." };
  writeStoredCartId(null);
  return { ok: true, data };
}

export async function listOwnInvoices(
  client: SupabaseClient,
): Promise<StorefrontResult<InvoiceRow[]>> {
  const { data, error } = await client
    .from("sales_invoices")
    .select("*")
    .eq("doc_type", "invoice")
    .order("created_at", { ascending: false })
    .limit(50);

  if (error) return { ok: false, error: error.message };
  return { ok: true, data: data ?? [] };
}

function parseCustomerOrder(raw: unknown): CustomerOrder | null {
  if (!raw || typeof raw !== "object") return null;
  const o = raw as Record<string, unknown>;
  if (typeof o.invoice_id !== "string") return null;
  return {
    invoice_id: o.invoice_id,
    document_number:
      typeof o.document_number === "string" ? o.document_number : null,
    doc_type: String(o.doc_type ?? ""),
    status: String(o.status ?? ""),
    fulfillment_mode: (o.fulfillment_mode as FulfillmentMode) ?? "immediate",
    currency: (o.currency as Currency) ?? "USD",
    exchange_rate_applied: Number(o.exchange_rate_applied ?? 1),
    subtotal: Number(o.subtotal ?? 0),
    total: Number(o.total ?? 0),
    amount_paid: Number(o.amount_paid ?? 0),
    amount_open: Number(o.amount_open ?? 0),
    cart_id: typeof o.cart_id === "string" ? o.cart_id : null,
    posted_at: typeof o.posted_at === "string" ? o.posted_at : null,
    pick_list_status:
      typeof o.pick_list_status === "string" ? o.pick_list_status : null,
    delivery_note_status:
      typeof o.delivery_note_status === "string"
        ? o.delivery_note_status
        : null,
  };
}

export async function getCustomerOrder(
  client: SupabaseClient,
  invoiceId: string,
): Promise<StorefrontResult<CustomerOrder>> {
  const { data, error } = await client.rpc("get_customer_order", {
    p_invoice_id: invoiceId,
  });
  if (error) return { ok: false, error: error.message };
  const parsed = parseCustomerOrder(data);
  if (!parsed) return { ok: false, error: "Unexpected order response shape." };
  return { ok: true, data: parsed };
}

export type PaymentIntentResult = {
  intentId: string;
  checkoutUrl: string | null;
};

/** Storefront return URL after ContiPay / Paynow hosted checkout (webhook still settles). */
export function checkoutReturnUrl(invoiceId?: string): string {
  const base = siteOrigin();
  const q = invoiceId
    ? `?invoice=${encodeURIComponent(invoiceId)}`
    : "";
  return `${base}/checkout/return${q}`;
}

/** Storefront cancel URL when the customer aborts PSP checkout. */
export function checkoutCancelUrl(invoiceId?: string): string {
  const base = siteOrigin();
  const q = invoiceId
    ? `?invoice=${encodeURIComponent(invoiceId)}`
    : "";
  return `${base}/checkout/cancel${q}`;
}

function siteOrigin(): string {
  if (typeof window !== "undefined" && window.location?.origin) {
    return window.location.origin;
  }
  return (
    process.env.NEXT_PUBLIC_SITE_URL?.replace(/\/$/, "") ??
    "https://nissangtrauto.co.zw"
  );
}

function parseEdgeIntent(raw: unknown): PaymentIntentResult | null {
  if (!raw || typeof raw !== "object") return null;
  const o = raw as Record<string, unknown>;
  const intentId =
    typeof o.intent_id === "string"
      ? o.intent_id
      : typeof o.intentId === "string"
        ? o.intentId
        : null;
  if (!intentId) return null;
  const checkoutUrl =
    typeof o.checkout_url === "string" && o.checkout_url.trim()
      ? o.checkout_url.trim()
      : typeof o.poll_url === "string" && o.poll_url.trim()
        ? o.poll_url.trim()
        : null;
  return { intentId, checkoutUrl };
}

/**
 * Create ContiPay intent via edge (preferred — may return checkout_url) or RPC fallback.
 * Passes return/cancel URLs in body + metadata for provider wiring (secrets stay in edge env).
 */
export async function createCustomerContipayIntent(
  client: SupabaseClient,
  invoiceId: string,
  method: ContipayMethod = "ecocash",
): Promise<StorefrontResult<PaymentIntentResult>> {
  const returnUrl = checkoutReturnUrl(invoiceId);
  const cancelUrl = checkoutCancelUrl(invoiceId);
  const metadata = {
    sales_invoice_id: invoiceId,
    channel: "storefront",
    return_url: returnUrl,
    cancel_url: cancelUrl,
  };

  const edge = await client.functions.invoke("contipay-initiate", {
    body: {
      sales_invoice_id: invoiceId,
      method,
      return_url: returnUrl,
      cancel_url: cancelUrl,
      metadata,
    },
  });

  if (!edge.error && edge.data) {
    const parsed = parseEdgeIntent(edge.data);
    if (parsed) return { ok: true, data: parsed };
    if (
      edge.data &&
      typeof edge.data === "object" &&
      "error" in edge.data &&
      typeof (edge.data as { error: unknown }).error === "string"
    ) {
      // Fall through to RPC when edge is stubbed/unavailable.
    }
  }

  const { data, error } = await client.rpc("create_customer_contipay_intent", {
    p_sales_invoice_id: invoiceId,
    p_method: method,
    p_metadata: metadata,
  });
  if (error) {
    const edgeMsg =
      edge.error?.message ??
      (edge.data &&
      typeof edge.data === "object" &&
      "error" in edge.data
        ? String((edge.data as { error: unknown }).error)
        : null);
    return {
      ok: false,
      error: edgeMsg ? `${error.message} (edge: ${edgeMsg})` : error.message,
    };
  }
  if (!data) return { ok: false, error: "ContiPay intent returned no id." };
  return { ok: true, data: { intentId: data, checkoutUrl: null } };
}

/**
 * Create Paynow intent via edge (preferred) or RPC fallback.
 * return_url / result_url passed for provider redirect when edge accepts them.
 */
export async function createCustomerPaynowIntent(
  client: SupabaseClient,
  invoiceId: string,
  method: PaynowMethod = "ecocash",
): Promise<StorefrontResult<PaymentIntentResult>> {
  const returnUrl = checkoutReturnUrl(invoiceId);
  const cancelUrl = checkoutCancelUrl(invoiceId);
  const metadata = {
    sales_invoice_id: invoiceId,
    channel: "storefront",
    return_url: returnUrl,
    cancel_url: cancelUrl,
    result_url: returnUrl,
  };

  const edge = await client.functions.invoke("paynow-initiate", {
    body: {
      sales_invoice_id: invoiceId,
      method,
      return_url: returnUrl,
      cancel_url: cancelUrl,
      result_url: returnUrl,
      metadata,
    },
  });

  if (!edge.error && edge.data) {
    const parsed = parseEdgeIntent(edge.data);
    if (parsed) return { ok: true, data: parsed };
  }

  const { data, error } = await client.rpc("create_customer_paynow_intent", {
    p_sales_invoice_id: invoiceId,
    p_method: method,
    p_metadata: metadata,
  });
  if (error) {
    const edgeMsg =
      edge.error?.message ??
      (edge.data &&
      typeof edge.data === "object" &&
      "error" in edge.data
        ? String((edge.data as { error: unknown }).error)
        : null);
    return {
      ok: false,
      error: edgeMsg ? `${error.message} (edge: ${edgeMsg})` : error.message,
    };
  }
  if (!data) return { ok: false, error: "Paynow intent returned no id." };
  return { ok: true, data: { intentId: data, checkoutUrl: null } };
}

export async function listGarageVehicles(
  client: SupabaseClient,
): Promise<StorefrontResult<GarageVehicleRow[]>> {
  const { data, error } = await client
    .from("customer_garage_vehicles")
    .select("*")
    .order("is_primary", { ascending: false })
    .order("created_at", { ascending: false });

  if (error) return { ok: false, error: error.message };
  return { ok: true, data: data ?? [] };
}

export async function upsertGarageVehicle(
  client: SupabaseClient,
  input: {
    id?: string | null;
    make?: string | null;
    model?: string | null;
    generation?: string | null;
    engine?: string | null;
    vin?: string | null;
    isPrimary?: boolean;
  },
): Promise<StorefrontResult<string>> {
  const { data, error } = await client.rpc("upsert_customer_garage_vehicle", {
    p_id: input.id ?? undefined,
    p_make: input.make ?? undefined,
    p_model: input.model ?? undefined,
    p_generation: input.generation ?? undefined,
    p_engine: input.engine ?? undefined,
    p_vin: input.vin ?? undefined,
    p_is_primary: input.isPrimary ?? false,
  });
  if (error) return { ok: false, error: error.message };
  if (!data) return { ok: false, error: "Garage upsert returned no id." };
  return { ok: true, data };
}

export async function deleteGarageVehicle(
  client: SupabaseClient,
  id: string,
): Promise<StorefrontResult<true>> {
  const { error } = await client.rpc("delete_customer_garage_vehicle", {
    p_id: id,
  });
  if (error) return { ok: false, error: error.message };
  return { ok: true, data: true };
}

export function formatMoney(amount: number, currency: Currency): string {
  return `${currency} ${amount.toFixed(2)}`;
}

export function fulfillmentLabel(mode: FulfillmentMode): string {
  return mode === "immediate" ? "Click & collect" : "Nationwide dispatch";
}

export function garageLabel(v: GarageVehicleRow): string {
  const parts = [v.make, v.model, v.generation, v.engine].filter(Boolean);
  if (parts.length) return parts.join(" · ");
  if (v.vin) return `VIN ${v.vin}`;
  return "Saved vehicle";
}
