import type { Database, SupabaseClient } from "@gtr/supabase-client";
import {
  displayLineTotalMajor,
  displayMajorFromDual,
  displayUnitPriceMajor,
  fromAmountMinor,
  settlementMoneyRpcFields,
  sumPreferAmountMinor,
  toAmountMinor,
  type CurrencyCode as SharedCurrency,
} from "@gtr/shared";
import { publicSiteUrl } from "@/lib/site-url";

type Currency = Database["public"]["Enums"]["currency_code"];
type FulfillmentMode = Database["public"]["Enums"]["fulfillment_mode"];
type ContipayMethod = Database["public"]["Enums"]["contipay_method"];
type PaynowMethod = Database["public"]["Enums"]["paynow_method"];

export type CartRow = Database["public"]["Tables"]["pos_carts"]["Row"];
/** Optional *_minor fields for H4 dual-read when dual-write columns land. */
export type CartLineRow = Database["public"]["Tables"]["pos_cart_lines"]["Row"] & {
  stock_items?: { oem_part_number: string; description: string | null } | null;
  unit_price_minor?: number | null;
  line_total_minor?: number | null;
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
  /** Non-terminal job id for live track via get_delivery_track_point. */
  active_delivery_job_id: string | null;
};

export type StorefrontResult<T> =
  | { ok: true; data: T }
  | { ok: false; error: string };

/**
 * Call RPCs / tables from migrations newer than generated `database.types`.
 * Remove once `packages/supabase-client` types are regenerated.
 */
async function storefrontRpc(
  client: SupabaseClient,
  fn: string,
  args?: Record<string, unknown>,
): Promise<{ data: unknown; error: { message: string } | null }> {
  return (
    client as unknown as {
      rpc: (
        name: string,
        params?: Record<string, unknown>,
      ) => PromiseLike<{ data: unknown; error: { message: string } | null }>;
    }
  ).rpc(fn, args);
}

function storefrontFrom(client: SupabaseClient, table: string) {
  return (
    client as unknown as {
      from: (t: string) => ReturnType<SupabaseClient["from"]>;
    }
  ).from(table);
}

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

/**
 * Official daily ZiG rate (ZiG per 1 USD) from `get_zig_exchange_rate`.
 * Falls back to {@link zigExchangeRate} env default when unset.
 */
export async function fetchZigExchangeRate(
  client: SupabaseClient,
  asOf?: string,
): Promise<number> {
  const { data, error } = await client.rpc("get_zig_exchange_rate", {
    p_as_of: asOf ?? null,
  });
  if (!error && data != null) {
    const n = Number(data);
    if (Number.isFinite(n) && n > 0) return n;
  }
  return zigExchangeRate();
}

/**
 * `daily_exchange_rates.id` for the row `get_zig_exchange_rate` would use
 * (latest ZIG `rate_date` ≤ as-of). Null when no row (env-fallback rate only).
 */
export async function fetchZigExchangeRateId(
  client: SupabaseClient,
  asOf?: string,
): Promise<string | null> {
  const asOfDate = asOf ?? new Date().toISOString().slice(0, 10);
  const { data, error } = await client
    .from("daily_exchange_rates")
    .select("id")
    .eq("currency", "ZIG")
    .lte("rate_date", asOfDate)
    .order("rate_date", { ascending: false })
    .limit(1)
    .maybeSingle();
  if (error || !data?.id) return null;
  return data.id;
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
  // Storefront carts are always USD; ZiG is a checkout settlement choice only.
  const cartCurrency = currency === "ZIG" ? "USD" : currency;
  const exchangeRate = opts.exchangeRate ?? 1;

  const { data: cartId, error } = await client.rpc("create_customer_cart", {
    p_warehouse_id: warehouse.data,
    p_currency: cartCurrency,
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

/** H4 / D-57: dual-read cart line unit price (minor wins when present). */
export function cartLineUnitPriceMajor(
  line: CartLineRow,
  currency: Currency = "USD",
): number {
  return displayUnitPriceMajor(line, currency as SharedCurrency);
}

/** H4 / D-57: dual-read cart line total (minor wins when present). */
export function cartLineTotalMajor(
  line: CartLineRow,
  currency: Currency = "USD",
): number {
  return displayLineTotalMajor(line, currency as SharedCurrency);
}

/**
 * Sum cart lines via minor units (H4 dual-read). Falls back to major when
 * `line_total_minor` is absent.
 */
export function sumCartLinesMajor(
  lines: CartLineRow[],
  currency: Currency = "USD",
): number {
  const sum = sumPreferAmountMinor(
    lines.map((line) => ({
      amountMinor: line.line_total_minor ?? null,
      amountMajor: Number(line.line_total),
    })),
    currency as SharedCurrency,
  );
  return fromAmountMinor(sum.amountMinor, currency as SharedCurrency);
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
  const currency = (o.currency as Currency) ?? "USD";
  // H4 dual-read: prefer *_minor when RPC dual-writes them
  const subtotal = displayMajorFromDualSafe(
    o.subtotal_minor,
    o.subtotal,
    currency,
  );
  const total = displayMajorFromDualSafe(o.total_minor, o.total, currency);
  const amount_paid = displayMajorFromDualSafe(
    o.amount_paid_minor,
    o.amount_paid,
    currency,
  );
  const amount_open = displayMajorFromDualSafe(
    o.amount_open_minor,
    o.amount_open,
    currency,
  );
  return {
    invoice_id: o.invoice_id,
    document_number:
      typeof o.document_number === "string" ? o.document_number : null,
    doc_type: String(o.doc_type ?? ""),
    status: String(o.status ?? ""),
    fulfillment_mode: (o.fulfillment_mode as FulfillmentMode) ?? "immediate",
    currency,
    exchange_rate_applied: Number(o.exchange_rate_applied ?? 1),
    subtotal,
    total,
    amount_paid,
    amount_open,
    cart_id: typeof o.cart_id === "string" ? o.cart_id : null,
    posted_at: typeof o.posted_at === "string" ? o.posted_at : null,
    pick_list_status:
      typeof o.pick_list_status === "string" ? o.pick_list_status : null,
    delivery_note_status:
      typeof o.delivery_note_status === "string"
        ? o.delivery_note_status
        : null,
    active_delivery_job_id:
      typeof o.active_delivery_job_id === "string"
        ? o.active_delivery_job_id
        : null,
  };
}

function displayMajorFromDualSafe(
  amountMinor: unknown,
  amountMajor: unknown,
  currency: Currency,
): number {
  const major =
    amountMajor === null || amountMajor === undefined
      ? 0
      : Number(amountMajor);
  const minor =
    amountMinor === null ||
    amountMinor === undefined ||
    amountMinor === ""
      ? null
      : (amountMinor as string | number);
  try {
    return displayMajorFromDual({
      amountMinor: minor,
      amountMajor: Number.isFinite(major) ? major : 0,
      currency: currency as SharedCurrency,
    });
  } catch {
    return Number.isFinite(major) ? major : 0;
  }
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

/**
 * H4 cutover: ZiG/PSP settlement prefers amountMinor; major derived for legacy Edge/RPC.
 */
export type StorefrontSettlementInput = {
  currency: Currency;
  /** Preferred: minor units (SoR for new call sites). */
  amountMinor?: bigint | number;
  /**
   * @deprecated Prefer amountMinor. Legacy major NUMERIC for Edge that still
   * read settlement_amount; derived when amountMinor is set.
   */
  amount?: number;
  exchangeRate: number;
  fxRateId?: string | null;
};

function settlementPayloadFields(settlement: StorefrontSettlementInput) {
  const currency = settlement.currency as SharedCurrency;
  const amountMinor =
    settlement.amountMinor !== undefined && settlement.amountMinor !== null
      ? typeof settlement.amountMinor === "bigint"
        ? settlement.amountMinor
        : BigInt(settlement.amountMinor)
      : toAmountMinor(Number(settlement.amount ?? 0), currency);
  return settlementMoneyRpcFields({
    currency,
    amountMinor,
    exchangeRate: settlement.exchangeRate,
    fxRateId: settlement.fxRateId ?? null,
  });
}

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
  return publicSiteUrl();
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
 * Passes return/cancel URLs + customer phone (edge requires phone / metadata.cell).
 * Optional ZiG settlement: invoice stays USD; gateway amount in ZiG at daily rate.
 */
export async function createCustomerContipayIntent(
  client: SupabaseClient,
  invoiceId: string,
  method: ContipayMethod = "ecocash",
  settlement?: StorefrontSettlementInput,
): Promise<StorefrontResult<PaymentIntentResult>> {
  const returnUrl = checkoutReturnUrl(invoiceId);
  const cancelUrl = checkoutCancelUrl(invoiceId);

  const customer = await loadOwnCustomer(client);
  const phone =
    customer.ok && customer.data
      ? (customer.data.phone_e164?.trim() ||
          customer.data.whatsapp_e164?.trim() ||
          null)
      : null;

  if (!phone) {
    return {
      ok: false,
      error:
        "Add a mobile number on your profile before paying with ContiPay (EcoCash cell required).",
    };
  }

  const settleFields = settlement ? settlementPayloadFields(settlement) : null;

  const metadata = {
    sales_invoice_id: invoiceId,
    channel: "storefront",
    return_url: returnUrl,
    cancel_url: cancelUrl,
    phone,
    cell: phone,
    ...(settleFields ?? {}),
  };

  const edge = await client.functions.invoke("contipay-initiate", {
    body: {
      sales_invoice_id: invoiceId,
      method,
      phone,
      return_url: returnUrl,
      cancel_url: cancelUrl,
      metadata,
      ...(settleFields ?? {}),
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
    ...(settleFields
      ? {
          p_settlement_currency: settleFields.settlement_currency,
          p_settlement_amount: settleFields.settlement_amount,
          p_settlement_exchange_rate: settleFields.settlement_exchange_rate,
        }
      : {}),
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
 * Browser return/cancel only — webhook result_url is edge defaultWebhookUrl.
 */
export async function createCustomerPaynowIntent(
  client: SupabaseClient,
  invoiceId: string,
  method: PaynowMethod = "ecocash",
  settlement?: StorefrontSettlementInput,
): Promise<StorefrontResult<PaymentIntentResult>> {
  const returnUrl = checkoutReturnUrl(invoiceId);
  const cancelUrl = checkoutCancelUrl(invoiceId);
  const settleFields = settlement ? settlementPayloadFields(settlement) : null;
  const metadata = {
    sales_invoice_id: invoiceId,
    channel: "storefront",
    return_url: returnUrl,
    cancel_url: cancelUrl,
    ...(settleFields ?? {}),
  };

  const edge = await client.functions.invoke("paynow-initiate", {
    body: {
      sales_invoice_id: invoiceId,
      method,
      return_url: returnUrl,
      cancel_url: cancelUrl,
      metadata,
      ...(settleFields ?? {}),
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
    ...(settleFields
      ? {
          p_settlement_currency: settleFields.settlement_currency,
          p_settlement_amount: settleFields.settlement_amount,
          p_settlement_exchange_rate: settleFields.settlement_exchange_rate,
        }
      : {}),
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

export type EcoCashPayerMode = "saved" | "other" | "profile";

/**
 * EcoCash direct C2B (not ContiPay/Paynow). Edge pushes PIN to payer_msisdn.
 * payerMode saved|profile uses profile phone; other uses explicit msisdn.
 */
export async function createCustomerEcocashIntent(
  client: SupabaseClient,
  invoiceId: string,
  opts: {
    payerMode: EcoCashPayerMode;
    payerMsisdn?: string | null;
    settlement?: StorefrontSettlementInput;
  },
): Promise<StorefrontResult<PaymentIntentResult & { message?: string }>> {
  const customer = await loadOwnCustomer(client);
  const profilePhone =
    customer.ok && customer.data
      ? (customer.data.phone_e164?.trim() ||
          customer.data.whatsapp_e164?.trim() ||
          null)
      : null;

  let payerMsisdn = opts.payerMsisdn?.trim() || null;
  let payerMode: string = opts.payerMode;
  if (opts.payerMode === "saved" || opts.payerMode === "profile") {
    if (!profilePhone) {
      return {
        ok: false,
        error:
          "No saved mobile on your profile. Enter a different EcoCash number.",
      };
    }
    payerMsisdn = profilePhone;
    payerMode = opts.payerMode === "profile" ? "profile" : "saved";
  } else if (!payerMsisdn) {
    return {
      ok: false,
      error: "Enter the EcoCash number that will approve the PIN.",
    };
  }

  const settleFields = opts.settlement
    ? settlementPayloadFields(opts.settlement)
    : null;
  const metadata = {
    sales_invoice_id: invoiceId,
    channel: "web",
    ...(settleFields ?? {}),
  };

  const edge = await client.functions.invoke("ecocash-initiate", {
    body: {
      sales_invoice_id: invoiceId,
      payer_msisdn: payerMsisdn,
      payer_mode: payerMode,
      channel: "web",
      metadata,
      ...(settleFields ?? {}),
    },
  });

  if (!edge.error && edge.data && typeof edge.data === "object") {
    const o = edge.data as Record<string, unknown>;
    if (typeof o.error === "string" && !o.intent_id) {
      // fall through
    } else {
      const intentId =
        typeof o.intent_id === "string"
          ? o.intent_id
          : typeof o.intentId === "string"
            ? o.intentId
            : null;
      if (intentId) {
        return {
          ok: true,
          data: {
            intentId,
            checkoutUrl: null,
            message:
              typeof o.message === "string"
                ? o.message
                : "EcoCash PIN request sent — approve on the EcoCash handset.",
          },
        };
      }
    }
  }

  const { data, error } = await client.rpc("create_customer_ecocash_intent", {
    p_sales_invoice_id: invoiceId,
    p_payer_msisdn: payerMsisdn,
    p_payer_mode: payerMode,
    p_channel: "web",
    p_metadata: metadata,
    ...(settleFields
      ? {
          p_settlement_currency: settleFields.settlement_currency,
          p_settlement_amount: settleFields.settlement_amount,
          p_settlement_exchange_rate: settleFields.settlement_exchange_rate,
        }
      : {}),
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
  if (!data) return { ok: false, error: "EcoCash intent returned no id." };
  return {
    ok: true,
    data: {
      intentId: data,
      checkoutUrl: null,
      message:
        "EcoCash intent created. Approve PIN on the EcoCash phone when push is live.",
    },
  };
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

export type CustomerRow = Database["public"]["Tables"]["customers"]["Row"];
export type ProfileRow = Database["public"]["Tables"]["profiles"]["Row"];
export type InvoiceLineRow =
  Database["public"]["Tables"]["sales_invoice_lines"]["Row"] & {
    stock_items?: { oem_part_number: string; description: string | null } | null;
    unit_price_minor?: number | null;
    line_total_minor?: number | null;
  };
export type LoyaltyBalance = {
  customer_id: string;
  points_balance: number;
  currency: Currency;
  liability_per_point: number;
  estimated_liability: number;
  /** B-MONEY-1 dual-read; prefer when present. */
  estimated_liability_minor?: number | null;
};
export type LoyaltyLedgerRow =
  Database["public"]["Tables"]["loyalty_ledger"]["Row"];
export type PriceListRow = Database["public"]["Tables"]["price_lists"]["Row"];
export type KitListItem = {
  kitId: string;
  stockItemId: string;
  oem: string;
  name: string;
  sellMode: Database["public"]["Enums"]["kit_sell_mode"];
  components: { oem: string; name: string; qty: number }[];
};

type StockItemBrief = {
  oem_part_number: string;
  description: string | null;
};

function asStockItemBrief(raw: unknown): StockItemBrief | null {
  if (!raw) return null;
  if (Array.isArray(raw)) {
    const first = raw[0];
    if (!first || typeof first !== "object") return null;
    const o = first as Record<string, unknown>;
    if (typeof o.oem_part_number !== "string") return null;
    return {
      oem_part_number: o.oem_part_number,
      description:
        typeof o.description === "string" || o.description === null
          ? (o.description as string | null)
          : null,
    };
  }
  if (typeof raw !== "object") return null;
  const o = raw as Record<string, unknown>;
  if (typeof o.oem_part_number !== "string") return null;
  return {
    oem_part_number: o.oem_part_number,
    description:
      typeof o.description === "string" || o.description === null
        ? (o.description as string | null)
        : null,
  };
}

export async function loadOwnCustomer(
  client: SupabaseClient,
): Promise<StorefrontResult<CustomerRow | null>> {
  const { data, error } = await client
    .from("customers")
    .select("*")
    .limit(1)
    .maybeSingle();
  if (error) return { ok: false, error: error.message };
  return { ok: true, data: data ?? null };
}

export async function loadOwnProfile(
  client: SupabaseClient,
  userId: string,
): Promise<StorefrontResult<ProfileRow | null>> {
  const { data, error } = await client
    .from("profiles")
    .select("*")
    .eq("id", userId)
    .maybeSingle();
  if (error) return { ok: false, error: error.message };
  return { ok: true, data: data ?? null };
}

export async function updateOwnFullName(
  client: SupabaseClient,
  userId: string,
  fullName: string,
): Promise<StorefrontResult<true>> {
  const { error } = await client
    .from("profiles")
    .update({ full_name: fullName.trim() || null, updated_at: new Date().toISOString() })
    .eq("id", userId);
  if (error) return { ok: false, error: error.message };
  return { ok: true, data: true };
}

/**
 * Contact / receipt prefs via `update_own_customer_profile` (own customer only).
 * `customerId` retained for call-site compatibility; RPC resolves via auth.uid().
 */
export async function updateOwnCustomerContact(
  client: SupabaseClient,
  _customerId: string,
  patch: {
    display_name?: string;
    email?: string | null;
    phone_e164?: string | null;
    whatsapp_e164?: string | null;
    sms_receipts?: boolean;
    email_receipts?: boolean;
    whatsapp_receipts?: boolean;
  },
): Promise<StorefrontResult<true>> {
  const { data, error } = await storefrontRpc(
    client,
    "update_own_customer_profile",
    {
      p_display_name: patch.display_name ?? null,
      p_email: patch.email ?? null,
      p_phone_e164: patch.phone_e164 ?? null,
      p_whatsapp_e164: patch.whatsapp_e164 ?? null,
      p_sms_receipts: patch.sms_receipts ?? null,
      p_email_receipts: patch.email_receipts ?? null,
      p_whatsapp_receipts: patch.whatsapp_receipts ?? null,
    },
  );
  if (error) return { ok: false, error: error.message };
  if (!data) {
    return { ok: false, error: "update_own_customer_profile returned no id." };
  }
  return { ok: true, data: true };
}

export type CustomerAddressRow = {
  id: string;
  customer_id: string;
  label: string;
  line1: string;
  line2: string | null;
  city: string | null;
  province: string | null;
  postal_code: string | null;
  country: string;
  is_default: boolean;
  created_at: string;
  updated_at: string;
};

export async function listOwnAddresses(
  client: SupabaseClient,
): Promise<StorefrontResult<CustomerAddressRow[]>> {
  const { data, error } = await storefrontFrom(client, "customer_addresses")
    .select("*")
    .order("is_default", { ascending: false })
    .order("created_at", { ascending: false });
  if (error) return { ok: false, error: error.message };
  return { ok: true, data: (data ?? []) as CustomerAddressRow[] };
}

export async function upsertOwnAddress(
  client: SupabaseClient,
  input: {
    id?: string | null;
    label: string;
    line1: string;
    line2?: string | null;
    city?: string | null;
    province?: string | null;
    postal_code?: string | null;
    country?: string;
    is_default?: boolean;
  },
): Promise<StorefrontResult<string>> {
  const { data, error } = await storefrontRpc(client, "upsert_customer_address", {
    p_id: input.id ?? null,
    p_label: input.label,
    p_line1: input.line1,
    p_line2: input.line2 ?? null,
    p_city: input.city ?? null,
    p_province: input.province ?? null,
    p_postal_code: input.postal_code ?? null,
    p_country: input.country ?? "Zimbabwe",
    p_is_default: input.is_default ?? false,
  });
  if (error) return { ok: false, error: error.message };
  if (typeof data !== "string" || !data) {
    return { ok: false, error: "upsert_customer_address returned no id." };
  }
  return { ok: true, data };
}

export async function deleteOwnAddress(
  client: SupabaseClient,
  addressId: string,
): Promise<StorefrontResult<true>> {
  const { error } = await storefrontRpc(client, "delete_customer_address", {
    p_id: addressId,
  });
  if (error) return { ok: false, error: error.message };
  return { ok: true, data: true };
}

export async function listInvoiceLines(
  client: SupabaseClient,
  invoiceId: string,
): Promise<StorefrontResult<InvoiceLineRow[]>> {
  const { data, error } = await client
    .from("sales_invoice_lines")
    .select("*, stock_items ( oem_part_number, description )")
    .eq("invoice_id", invoiceId)
    .order("created_at", { ascending: true });
  if (error) return { ok: false, error: error.message };
  return { ok: true, data: (data ?? []) as InvoiceLineRow[] };
}

/**
 * Quarantine-only credit note via customer-scoped RPC
 * (`post_customer_return_credit_note` / alias `request_customer_return`).
 * Unit prices are forced server-side from the source invoice.
 */
export async function requestReturnCreditNote(
  client: SupabaseClient,
  invoiceId: string,
  lines: {
    stock_item_id: string;
    uom_id: string;
    qty: number;
    unit_price?: number;
  }[],
): Promise<StorefrontResult<string>> {
  const payload = lines.map((l) => ({
    stock_item_id: l.stock_item_id,
    uom_id: l.uom_id,
    qty: l.qty,
  }));
  const { data, error } = await storefrontRpc(
    client,
    "post_customer_return_credit_note",
    {
      p_invoice_id: invoiceId,
      p_lines: payload,
    },
  );
  if (error) return { ok: false, error: error.message };
  if (typeof data !== "string" || !data) {
    return {
      ok: false,
      error: "post_customer_return_credit_note returned no id.",
    };
  }
  return { ok: true, data };
}

export async function getLoyaltyBalance(
  client: SupabaseClient,
  customerId: string,
): Promise<StorefrontResult<LoyaltyBalance>> {
  const { data, error } = await client.rpc("get_loyalty_balance", {
    p_customer_id: customerId,
  });
  if (error) return { ok: false, error: error.message };
  const row = Array.isArray(data) ? data[0] : data;
  if (!row || typeof row !== "object") {
    return { ok: false, error: "Unexpected loyalty balance shape." };
  }
  const r = row as Record<string, unknown>;
  return {
    ok: true,
    data: {
      customer_id: String(r.customer_id ?? customerId),
      points_balance: Number(r.points_balance ?? 0),
      currency: (r.currency as Currency) ?? "USD",
      liability_per_point: Number(r.liability_per_point ?? 0),
      estimated_liability: displayMajorFromDualSafe(
        r.estimated_liability_minor,
        r.estimated_liability,
        (r.currency as Currency) ?? "USD",
      ),
      estimated_liability_minor:
        r.estimated_liability_minor === null ||
        r.estimated_liability_minor === undefined
          ? null
          : Number(r.estimated_liability_minor),
    },
  };
}

export async function listLoyaltyLedger(
  client: SupabaseClient,
  customerId: string,
  limit = 20,
): Promise<StorefrontResult<LoyaltyLedgerRow[]>> {
  const { data, error } = await client
    .from("loyalty_ledger")
    .select("*")
    .eq("customer_id", customerId)
    .order("created_at", { ascending: false })
    .limit(limit);
  if (error) return { ok: false, error: error.message };
  return { ok: true, data: data ?? [] };
}

export async function loadCustomerPriceList(
  client: SupabaseClient,
): Promise<
  StorefrontResult<{
    customer: CustomerRow | null;
    priceList: PriceListRow | null;
    isTrade: boolean;
  }>
> {
  const customer = await loadOwnCustomer(client);
  if (!customer.ok) return customer;

  if (!customer.data?.price_list_id) {
    const { data: retail, error } = await client
      .from("price_lists")
      .select("*")
      .eq("code", "RETAIL")
      .eq("is_active", true)
      .maybeSingle();
    if (error) return { ok: false, error: error.message };
    return {
      ok: true,
      data: {
        customer: customer.data,
        priceList: retail ?? null,
        isTrade: false,
      },
    };
  }

  const { data: list, error } = await client
    .from("price_lists")
    .select("*")
    .eq("id", customer.data.price_list_id)
    .maybeSingle();
  if (error) return { ok: false, error: error.message };
  const code = list?.code?.toUpperCase() ?? "";
  return {
    ok: true,
    data: {
      customer: customer.data,
      priceList: list ?? null,
      isTrade: code === "B2B" || code === "FLEET",
    },
  };
}

export async function listPriceListSample(
  client: SupabaseClient,
  priceListId: string,
  limit = 24,
): Promise<
  StorefrontResult<
    {
      stock_item_id: string;
      unit_price: number;
      core_charge: number;
      oem: string;
      name: string;
    }[]
  >
> {
  const { data, error } = await client
    .from("price_list_items")
    .select(
      "stock_item_id, unit_price, core_charge, stock_items ( oem_part_number, description )",
    )
    .eq("price_list_id", priceListId)
    .limit(limit);
  if (error) return { ok: false, error: error.message };

  const rows = (data ?? []).map((row) => {
    const item = asStockItemBrief(row.stock_items);
    return {
      stock_item_id: row.stock_item_id,
      unit_price: Number(row.unit_price),
      core_charge: Number(row.core_charge ?? 0),
      oem: item?.oem_part_number ?? row.stock_item_id,
      name: item?.description?.trim() || item?.oem_part_number || "Part",
    };
  });
  return { ok: true, data: rows };
}

export async function listActiveKits(
  client: SupabaseClient,
): Promise<StorefrontResult<KitListItem[]>> {
  const { data: kits, error } = await client
    .from("item_kits")
    .select(
      "id, sell_mode, stock_item_id, stock_items ( oem_part_number, description )",
    )
    .eq("is_active", true)
    .order("created_at", { ascending: false })
    .limit(50);
  if (error) return { ok: false, error: error.message };
  if (!kits?.length) return { ok: true, data: [] };

  const kitIds = kits.map((k) => k.id);
  const { data: comps, error: compErr } = await client
    .from("item_kit_components")
    .select(
      "kit_id, qty, stock_items:component_item_id ( oem_part_number, description )",
    )
    .in("kit_id", kitIds);
  if (compErr) return { ok: false, error: compErr.message };

  const byKit = new Map<string, KitListItem["components"]>();
  for (const c of comps ?? []) {
    const item = asStockItemBrief(c.stock_items);
    const list = byKit.get(c.kit_id) ?? [];
    list.push({
      oem: item?.oem_part_number ?? "—",
      name: item?.description?.trim() || item?.oem_part_number || "Component",
      qty: Number(c.qty),
    });
    byKit.set(c.kit_id, list);
  }

  const out: KitListItem[] = kits.map((k) => {
    const item = asStockItemBrief(k.stock_items);
    return {
      kitId: k.id,
      stockItemId: k.stock_item_id,
      oem: item?.oem_part_number ?? k.stock_item_id,
      name: item?.description?.trim() || item?.oem_part_number || "Kit",
      sellMode: k.sell_mode,
      components: byKit.get(k.id) ?? [],
    };
  });
  return { ok: true, data: out };
}
