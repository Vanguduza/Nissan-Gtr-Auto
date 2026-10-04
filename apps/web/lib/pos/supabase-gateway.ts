import { createEphemeralClient, type SupabaseClient } from "@gtr/supabase-client";
import type { PosGateway } from "@/lib/pos/gateway";
import { roundMoney } from "@/lib/pos/money";
import type {
  CartLine,
  CustomerInput,
  EpcDiagram,
  FulfillmentMode,
  GarageVehicle,
  ManagerCredentials,
  PosCustomer,
  QuotationStatus,
  Tender,
  PopularPin,
  PopularPinKind,
  PosCart,
  PosCurrency,
  PosPart,
  PosResult,
  SelectedVehicle,
  TillSession,
  VehicleModel,
  VehicleVariant,
  Governed,
  PaymentStatus,
  SplitSession,
  SplitRecoveryItem,
  CardTerminal,
  TerminalRecoveryItem,
  InvoiceDetail,
  WarrantyClaim,
  WarrantyStatus,
  FulfillmentRequest,
  BusinessProfile,
  LetterSourceKind,
  MySignature,
  PaymentLetterSummary,
} from "@/lib/pos/types";
import { replacementJson, warrantyApproveArgs } from "@/lib/pos/returns";
import {
  CatalogGatewayError,
  catalogGatewayGet,
  type CustomerStockResponse,
  type DiagramImageResponse,
  type StaffPartsResponse,
} from "@/lib/catalog-live-gateway";
import { searchPosCatalog } from "@/lib/staff-pos";

import { VEHICLE_MASTER_PAGE } from "@/lib/vehicle-catalog";

const NISSAN_MAKER_SLUG = "nissan";
const PRODUCT_IMAGE_BUCKET = "product-images";

// Generated database types predate the September POS RPCs; call those through a narrow untyped shim.
type RpcResult = { data: unknown; error: { message: string } | null };
function rpc(client: SupabaseClient, fn: string, args?: Record<string, unknown>): Promise<RpcResult> {
  const call = client.rpc as unknown as (f: string, a?: Record<string, unknown>) => Promise<RpcResult>;
  return call.call(client, fn, args);
}

/**
 * Database refusals are mostly operator-readable (RAISE EXCEPTION texts); the few that are not
 * (stock-ledger internals, privilege errors) are reworded here so ids never reach the cashier.
 */
export function operatorMessage(message: string | null | undefined, fallback: string): string {
  const m = (message ?? "").trim();
  if (!m) return fallback;
  if (/insufficient .*(qty|stock)|short \d/i.test(m)) {
    return "Not enough stock to complete this sale. Reduce or remove the line that is out of stock.";
  }
  if (/permission denied|42501/i.test(m)) return "Your account is not allowed to do this.";
  if (/offline_price_conflict/i.test(m)) return "The price changed since this sale was rung up. Check the line prices.";
  return m;
}

function fail<T>(error: { message: string } | null | undefined, fallback: string): PosResult<T> {
  return { ok: false, error: operatorMessage(error?.message, fallback) };
}

type StaffDiagramRow = { diagram_id: string; title: string | null; name_en: string | null; image_ready: boolean };
type StaffSectionRow = { section_id: string; section_slug: string; display_name: string; diagram_count: number };
/** A `list_customer_vehicle_master` row; id is the catalogue vehicle_id. */
type VehicleMasterDbRow = {
  id: string;
  model_family: string;
  chassis_code: string;
  engine_code: string | null;
  year_start: number | null;
  year_end: number | null;
  sales_region: string | null;
};

/** Same family key as the catalogue gateway (`staff-families`). */
function familySlug(model: string): string {
  return model.trim().toLowerCase().replace(/[^a-z0-9]+/g, "-").replace(/^-+|-+$/g, "");
}
function minYear(a: number | null, b: number | null): number | null {
  return a == null ? b : b == null ? a : Math.min(a, b);
}
function maxYear(a: number | null, b: number | null): number | null {
  return a == null ? b : b == null ? a : Math.max(a, b);
}
function yearRange(start: number | null, end: number | null): string | null {
  if (start == null && end == null) return null;
  return `${start ?? ""}–${end ?? ""}`;
}

/** Cashier wording for the live catalogue's fail-closed states. */
export function liveCatalogMessage(e: unknown, fallback: string): string {
  if (e instanceof CatalogGatewayError) {
    if (e.catalogStatus === "CATALOG_REPUBLISH_REQUIRED") return "This part of the catalogue is still being published. Try again later.";
    if (/R2 is not configured/i.test(e.message)) return "The full catalogue is not connected yet (catalogue storage keys are missing).";
    if (e.httpStatus === 403) return "Your account cannot open the parts catalogue.";
    if (e.httpStatus === 503 && /no current catalog release/i.test(e.message)) return "No catalogue release is published yet.";
  }
  return fallback;
}

function asCurrency(value: unknown): PosCurrency {
  return value === "ZIG" ? "ZIG" : "USD";
}

function vehicleFromRow(row: Record<string, unknown>, prefix: string): SelectedVehicle | null {
  const chassis = row[`${prefix}chassis_code`];
  if (typeof chassis !== "string" || !chassis) return null;
  return {
    modelSlug: String(row[`${prefix}model_slug`] ?? ""),
    modelName: String(row[`${prefix}model_name`] ?? ""),
    generation: String(row[`${prefix}generation`] ?? chassis),
    chassisCode: chassis,
    engineCode: String(row[`${prefix}engine_code`] ?? ""),
  };
}

function customerFromRow(r: Record<string, unknown>): PosCustomer {
  return {
    id: String(r.id),
    kind: r.customer_kind === "business" ? "business" : "individual",
    displayName: String(r.display_name ?? ""),
    businessName: (r.business_name as string | null) ?? null,
    email: (r.email as string | null) ?? null,
    phoneE164: (r.phone_e164 as string | null) ?? null,
    whatsappE164: (r.whatsapp_e164 as string | null) ?? null,
  };
}

function tillFromRow(r: Record<string, unknown>): TillSession {
  const opt = (v: unknown) => (v == null ? null : num(v));
  const status = r.status === "variance_pending" || r.status === "closed" ? r.status : "open";
  return {
    id: String(r.id),
    deviceId: String(r.device_id ?? ""),
    warehouseId: String(r.warehouse_id ?? ""),
    currency: asCurrency(r.currency),
    operatorUserId: String(r.operator_user_id ?? ""),
    openingFloat: num(r.opening_float),
    status,
    expectedCash: opt(r.expected_cash),
    countedCash: opt(r.counted_cash),
    variance: opt(r.variance),
    varianceReasonCode: (r.variance_reason_code as string | null) ?? null,
    openedAt: String(r.opened_at ?? ""),
    closedAt: (r.closed_at as string | null) ?? null,
  };
}

function paymentStatusFromRow(r: Record<string, unknown>): PaymentStatus {
  const str = (v: unknown) => (v == null ? null : String(v));
  return {
    orderId: String(r.order_id),
    cartId: str(r.cart_id),
    state: String(r.state ?? ""),
    total: num(r.total),
    currency: asCurrency(r.currency),
    reservationExpiresAt: str(r.reservation_expires_at),
    activeProvider: str(r.active_provider),
    activeIntentId: str(r.active_intent_id),
    providerStatus: str(r.provider_status),
    providerFailure: str(r.provider_failure),
    settledProvider: str(r.settled_provider),
    settledProviderRef: str(r.settled_provider_ref),
    salesInvoiceId: str(r.sales_invoice_id),
    paymentException: str(r.payment_exception),
    exceptions: ((r.exceptions as Record<string, unknown>[] | null) ?? []).map((e) => ({
      id: String(e.id),
      provider: str(e.provider),
      code: String(e.code ?? ""),
      detail: str(e.detail),
      resolvedAt: str(e.resolved_at),
      resolution: str(e.resolution),
      createdAt: String(e.created_at ?? ""),
    })),
  };
}

/** `pos_split_payment_payload` → the counter's view of a part-paid sale (amounts are the server's). */
function splitFromRow(r: Record<string, unknown>): SplitSession {
  const str = (v: unknown) => (v == null ? null : String(v));
  const opt = (v: unknown) => (v == null ? null : num(v));
  return {
    sessionId: String(r.session_id),
    orderId: String(r.order_id),
    status: String(r.status ?? "open") as SplitSession["status"],
    total: num(r.total),
    currency: asCurrency(r.currency),
    captured: num(r.captured_amount),
    held: num(r.held_amount),
    pending: num(r.pending_amount),
    locked: num(r.locked_amount),
    balanceDue: num(r.balance_due),
    availableToAllocate: num(r.available_to_allocate),
    finalInvoiceId: str(r.final_invoice_id),
    finalizationError: str(r.finalization_error),
    reducedBasketAcceptedAt: str(r.reduced_basket_accepted_at),
    legs: ((r.legs as Record<string, unknown>[] | null) ?? []).map((l) => ({
      id: String(l.id),
      sequenceNo: num(l.sequence_no),
      tender: String(l.tender ?? ""),
      amount: num(l.amount),
      status: String(l.status ?? "planned") as SplitSession["legs"][number]["status"],
      reference: str(l.external_reference),
      providerRef: str(l.provider_ref),
      statusDetail: str(l.status_detail),
      appliedAmount: opt(l.applied_target_amount),
      refundRequired: opt(l.refund_required_amount),
    })),
    refunds: ((r.refunds as Record<string, unknown>[] | null) ?? []).map((f) => ({
      id: String(f.id),
      legId: String(f.leg_id),
      status: String(f.status ?? "review"),
      grossAmount: num(f.gross_amount),
      feePolicy: String(f.fee_policy ?? "manual_review"),
      netCustomerRefund: opt(f.net_customer_refund),
      providerRef: str(f.provider_ref),
      failureReason: str(f.failure_reason),
      notes: str(f.notes),
    })),
  };
}

function num(value: unknown): number {
  const n = typeof value === "number" ? value : Number(value);
  return Number.isFinite(n) ? n : 0;
}

type PartMeta = {
  stockItemId: string;
  uomId: string | null;
  name: string;
  price: { amount: number; currency: PosCurrency } | null;
  saleableQty: number;
  imageUrl: string | null;
};

function warrantyFromRow(r: Record<string, unknown>): WarrantyClaim {
  return {
    id: String(r.id),
    documentNumber: (r.document_number as string | null) ?? null,
    status: String(r.status) as WarrantyStatus,
    resolution: (r.resolution as string | null) ?? null,
    invoiceId: (r.sales_invoice_id as string | null) ?? null,
    invoiceNumber: (r.invoice_number as string | null) ?? null,
    stockItemId: (r.stock_item_id as string | null) ?? null,
    partNumber: (r.oem_part_number as string | null) ?? null,
    serialNumber: (r.serial_number as string | null) ?? null,
    notes: (r.notes as string | null) ?? null,
    rejectReason: (r.reject_reason as string | null) ?? null,
    creditNoteId: (r.credit_note_id as string | null) ?? null,
    createdAt: String(r.created_at),
    decidedAt: (r.decided_at as string | null) ?? null,
    closedAt: (r.closed_at as string | null) ?? null,
  };
}

const str = (v: unknown): string | null => (v == null || v === "" ? null : String(v));

function letterSummaryFromRow(r: Record<string, unknown>): PaymentLetterSummary {
  return {
    id: String(r.id),
    documentNumber: str(r.document_number),
    sourceKind: String(r.source_kind) as LetterSourceKind,
    provider: str(r.provider),
    observedStatus: str(r.observed_status),
    amount: num(r.amount),
    currency: asCurrency(r.currency),
    customerName: str(r.customer_name),
    invoiceNumber: str(r.invoice_document_number),
    managerName: str(r.manager_name),
    managerTitle: str(r.manager_title),
    issuedAt: String(r.issued_at),
  };
}

function profileFromRow(b: Record<string, unknown>): BusinessProfile {
  return {
    legalName: String(b.legal_name ?? ""),
    tradingName: String(b.trading_name ?? ""),
    domain: String(b.domain ?? ""),
    city: str(b.city),
    country: str(b.country),
    addressLine1: str(b.address_line1),
    addressLine2: str(b.address_line2),
    phone: str(b.phone_e164),
    email: str(b.email),
    registrationNumber: str(b.registration_number),
  };
}

async function sha256Hex(data: ArrayBuffer): Promise<string> {
  const digest = await crypto.subtle.digest("SHA-256", data);
  return Array.from(new Uint8Array(digest), (b) => b.toString(16).padStart(2, "0")).join("");
}

export function createSupabasePosGateway(client: SupabaseClient): PosGateway {
  const projectUrl = process.env.NEXT_PUBLIC_SUPABASE_URL?.replace(/\/$/, "") ?? "";

  function imageUrl(path: string | null | undefined): string | null {
    const p = path?.trim();
    if (!p) return null;
    if (/^https?:\/\//i.test(p)) return p;
    return projectUrl ? `${projectUrl}/storage/v1/object/public/${PRODUCT_IMAGE_BUCKET}/${p.replace(/^\//, "")}` : null;
  }

  // The published vehicle master (one row per vehicle the full catalogue serves). Its id is the
  // `catalog-live-r2` vehicle_id; models, generations and engines in the cascade all come from it.
  let vehicleMaster: Promise<PosResult<VehicleMasterDbRow[]>> | null = null;

  function loadVehicleMaster(): Promise<PosResult<VehicleMasterDbRow[]>> {
    vehicleMaster ??= (async (): Promise<PosResult<VehicleMasterDbRow[]>> => {
      const rows: VehicleMasterDbRow[] = [];
      for (let offset = 0; ; offset += VEHICLE_MASTER_PAGE) {
        const { data, error } = await rpc(client, "list_customer_vehicle_master", {
          p_maker: NISSAN_MAKER_SLUG,
          p_limit: VEHICLE_MASTER_PAGE,
          p_offset: offset,
        });
        if (error) {
          vehicleMaster = null;
          return fail(error, "Could not load the vehicle list.");
        }
        const page = (data ?? []) as VehicleMasterDbRow[];
        rows.push(...page);
        if (page.length < VEHICLE_MASTER_PAGE) break;
      }
      return { ok: true, data: rows };
    })();
    return vehicleMaster;
  }

  /**
   * The published vehicle-master id for this model + chassis + engine, or null if absent. The
   * catalogue lists several build variants per chassis + engine with near-identical fitment, so the
   * first (by id, for a stable choice) keys the R2 shard rather than refusing the search.
   */
  async function vehicleMasterId(v: SelectedVehicle): Promise<string | null> {
    const res = await loadVehicleMaster();
    const rows = res.ok ? res.data : [];
    const ids = rows
      .filter(
        (r) =>
          familySlug(r.model_family) === v.modelSlug &&
          r.chassis_code.trim().toUpperCase() === v.chassisCode.trim().toUpperCase() &&
          (r.engine_code ?? "").trim().toUpperCase() === v.engineCode.trim().toUpperCase(),
      )
      .map((r) => r.id)
      .sort();
    return ids[0] ?? null;
  }

  /** Vehicle-filtered search over the full catalogue (R2 shard + shop stock); null = not available. */
  async function liveVehicleSearch(v: SelectedVehicle, q: string): Promise<PosPart[] | null> {
    const id = await vehicleMasterId(v);
    if (!id) return null;
    try {
      const res = await catalogGatewayGet<CustomerStockResponse>(client, q ? "customer-search" : "customer-stock", {
        maker: NISSAN_MAKER_SLUG,
        vehicle_id: id,
        q: q || undefined,
        limit: 100,
      });
      return res.results.map((r) => ({
        stockItemId: r.stock_item_id,
        oemPartNumber: r.internal_catalog_ref ?? r.name,
        name: r.name,
        price: r.price ? { amount: r.price.amount, currency: asCurrency(r.price.currency) } : null,
        saleableQty: r.stock.qty,
        imageUrl: null,
        categoryName: r.subcategory ?? r.category,
      }));
    } catch {
      return null;
    }
  }

  /** Price, saleable stock and image per OEM — same sources as `list_pos_popular_spares`. */
  async function hydrate(oems: string[]): Promise<Map<string, PartMeta>> {
    const out = new Map<string, PartMeta>();
    const unique = [...new Set(oems.map((o) => o.trim()).filter(Boolean))].slice(0, 60);
    if (unique.length === 0) return out;
    const { data: items } = await client
      .from("stock_items")
      .select("id, oem_part_number, description, base_uom_id")
      .in("oem_part_number", unique);
    const rows = (items ?? []) as Array<{ id: string; oem_part_number: string; description: string | null; base_uom_id: string | null }>;
    const ids = rows.map((r) => r.id);
    if (ids.length === 0) return out;

    const [prices, levels, images] = await Promise.all([
      client
        .from("price_list_items")
        .select("stock_item_id, unit_price, price_lists!inner(currency, is_default, is_active)")
        .in("stock_item_id", ids)
        .eq("price_lists.is_default", true)
        .eq("price_lists.is_active", true),
      client
        .from("stock_levels")
        .select("stock_item_id, quantity, warehouses!inner(is_active, is_quarantine)")
        .in("stock_item_id", ids)
        .eq("warehouses.is_active", true)
        .eq("warehouses.is_quarantine", false),
      client
        .from("stock_item_images")
        .select("stock_item_id, storage_path, is_primary, sort_order")
        .in("stock_item_id", ids)
        .order("is_primary", { ascending: false })
        .order("sort_order"),
    ]);

    const priceBy = new Map<string, { amount: number; currency: PosCurrency }>();
    for (const p of (prices.data ?? []) as Array<{ stock_item_id: string; unit_price: number; price_lists: { currency: string } | { currency: string }[] }>) {
      if (priceBy.has(p.stock_item_id)) continue;
      const list = Array.isArray(p.price_lists) ? p.price_lists[0] : p.price_lists;
      priceBy.set(p.stock_item_id, { amount: num(p.unit_price), currency: asCurrency(list?.currency) });
    }
    const qtyBy = new Map<string, number>();
    for (const l of (levels.data ?? []) as Array<{ stock_item_id: string; quantity: number }>) {
      qtyBy.set(l.stock_item_id, (qtyBy.get(l.stock_item_id) ?? 0) + num(l.quantity));
    }
    const imageBy = new Map<string, string>();
    for (const i of (images.data ?? []) as Array<{ stock_item_id: string; storage_path: string }>) {
      if (!imageBy.has(i.stock_item_id)) imageBy.set(i.stock_item_id, i.storage_path);
    }
    for (const r of rows) {
      out.set(r.oem_part_number.trim().toUpperCase(), {
        stockItemId: r.id,
        uomId: r.base_uom_id,
        name: r.description ?? r.oem_part_number,
        price: priceBy.get(r.id) ?? null,
        saleableQty: qtyBy.get(r.id) ?? 0,
        imageUrl: imageUrl(imageBy.get(r.id)),
      });
    }
    return out;
  }

  async function partsFromHits(
    hits: Array<{ oem_part_number: string; description?: string | null; category_name?: string | null }>,
  ): Promise<PosPart[]> {
    const meta = await hydrate(hits.map((h) => h.oem_part_number));
    const seen = new Set<string>();
    const parts: PosPart[] = [];
    for (const h of hits) {
      const key = h.oem_part_number.trim().toUpperCase();
      if (!key || seen.has(key)) continue;
      seen.add(key);
      const m = meta.get(key);
      parts.push({
        stockItemId: m?.stockItemId ?? null,
        oemPartNumber: h.oem_part_number,
        name: m?.name ?? h.description ?? h.oem_part_number,
        price: m?.price ?? null,
        saleableQty: m ? m.saleableQty : null,
        imageUrl: m?.imageUrl ?? null,
        categoryName: h.category_name ?? null,
      });
    }
    return parts;
  }

  /**
   * Manager approval (owner decision D4 / tablet `withManagerApproval`): the manager signs in on an
   * isolated, non-persisted client; the approval RPC runs as the manager; the session is discarded.
   * The cashier's own session is never replaced.
   */
  async function asManager<T>(
    manager: ManagerCredentials,
    action: (managerClient: SupabaseClient) => Promise<PosResult<T>>,
  ): Promise<PosResult<T>> {
    const url = process.env.NEXT_PUBLIC_SUPABASE_URL;
    const anon = process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY;
    if (!url || !anon) return { ok: false, error: "Supabase is not configured." };
    if (!manager.identifier.trim() || !manager.password) return { ok: false, error: "Manager ID and password are required." };
    const managerClient = createEphemeralClient(url, anon);
    const { data: email, error: resolveError } = await rpc(managerClient, "resolve_staff_login_email", {
      p_identifier: manager.identifier.trim(),
    });
    if (resolveError || typeof email !== "string" || !email.trim()) return { ok: false, error: "Manager sign-in failed." };
    const { error: signInError } = await managerClient.auth.signInWithPassword({ email: email.trim(), password: manager.password });
    if (signInError) return { ok: false, error: "Manager sign-in failed." };
    try {
      return await action(managerClient);
    } finally {
      // Local scope: end this approval's session only, never the manager's sessions on other devices.
      await managerClient.auth.signOut({ scope: "local" });
    }
  }

  /** A manager's isolated session, or the signed-in user's own when they are the manager. */
  function withManagerOrSelf<T>(manager: ManagerCredentials | null, action: (c: SupabaseClient) => Promise<PosResult<T>>): Promise<PosResult<T>> {
    return manager ? asManager(manager, action) : action(client);
  }

  /** Governed action: the manager's isolated session when policy needs one, else the cashier's. */
  function governed<T>(g: Governed, action: (c: SupabaseClient) => Promise<PosResult<T>>): Promise<PosResult<T>> {
    return g.manager ? asManager(g.manager, action) : action(client);
  }

  async function readCart(cartId: string): Promise<PosResult<PosCart>> {
    const { data: cart, error } = await client
      .from("pos_carts")
      .select("*")
      .eq("id", cartId)
      .maybeSingle();
    if (error || !cart) return fail(error, "Cart not found.");
    const c = cart as Record<string, unknown>;
    const { data: lineRows, error: lineErr } = await client
      .from("pos_cart_lines")
      .select("id, stock_item_id, qty, unit_price, line_total, is_core_charge, stock_items ( oem_part_number, description )")
      .eq("cart_id", cartId)
      .order("created_at");
    if (lineErr) return fail(lineErr, "Could not load cart lines.");
    const rows = (lineRows ?? []) as Array<{
      id: string;
      stock_item_id: string;
      qty: number;
      unit_price: number;
      line_total: number;
      stock_items: { oem_part_number: string; description: string | null } | { oem_part_number: string; description: string | null }[] | null;
    }>;
    const metas = await hydrate(
      rows.map((r) => (Array.isArray(r.stock_items) ? r.stock_items[0] : r.stock_items)?.oem_part_number ?? ""),
    );
    const lines: CartLine[] = rows.map((r) => {
      const si = Array.isArray(r.stock_items) ? r.stock_items[0] : r.stock_items;
      const oem = si?.oem_part_number ?? "";
      return {
        id: r.id,
        stockItemId: r.stock_item_id,
        oemPartNumber: oem,
        name: si?.description ?? oem,
        qty: num(r.qty),
        unitPrice: num(r.unit_price),
        lineTotal: num(r.line_total),
        imageUrl: metas.get(oem.trim().toUpperCase())?.imageUrl ?? null,
      };
    });
    const vehicle = vehicleFromRow(c, "vehicle_");
    const contexts = Array.isArray(c.vehicle_contexts) ? (c.vehicle_contexts as Record<string, unknown>[]) : [];
    const vehicles = contexts.map((v) => vehicleFromRow(v, "")).filter((v): v is SelectedVehicle => v !== null);
    let customerName: string | null = null;
    const customerId = typeof c.customer_id === "string" ? c.customer_id : null;
    if (customerId) {
      const { data: cust } = await client.from("customers").select("display_name").eq("id", customerId).maybeSingle();
      customerName = (cust as { display_name: string | null } | null)?.display_name ?? null;
    }
    return {
      ok: true,
      data: {
        id: cartId,
        documentNumber: (c.document_number as string | null) ?? null,
        status: String(c.status ?? "open"),
        currency: asCurrency(c.currency),
        warehouseId: (c.warehouse_id as string | null) ?? null,
        fulfillmentMode: c.fulfillment_mode === "dispatch" ? "dispatch" : "immediate",
        lines,
        customerId,
        customerName,
        vehicle,
        vehicles: vehicles.length ? vehicles : vehicle ? [vehicle] : [],
        tillSessionId: typeof c.till_session_id === "string" ? c.till_session_id : null,
      },
    };
  }

  const gateway: PosGateway = {
    isPreview: false,

    async listModels() {
      const res = await loadVehicleMaster();
      if (!res.ok) return res;
      const models = new Map<string, VehicleModel>();
      for (const r of res.data) {
        const slug = familySlug(r.model_family);
        const m = models.get(slug);
        models.set(slug, {
          slug,
          name: r.model_family,
          yearStart: minYear(m?.yearStart ?? null, r.year_start),
          yearEnd: maxYear(m?.yearEnd ?? null, r.year_end),
        });
      }
      return { ok: true, data: [...models.values()].sort((a, b) => a.name.localeCompare(b.name)) };
    },

    async listVariants(modelSlug) {
      const res = await loadVehicleMaster();
      if (!res.ok) return res;
      return {
        ok: true,
        data: res.data
          .filter((r) => familySlug(r.model_family) === modelSlug)
          .map((r) => ({
            slug: r.id,
            chassisCode: r.chassis_code,
            engineCode: r.engine_code,
            yearLabel: [yearRange(r.year_start, r.year_end), r.sales_region].filter(Boolean).join(" · ") || null,
          })),
      };
    },

    async searchParts(query, vehicle) {
      const q = query.trim();
      if (vehicle) {
        // Full catalogue first: the R2 fitment shard for this exact vehicle, joined to shop stock.
        const live = await liveVehicleSearch(vehicle, q);
        if (live) return { ok: true, data: live };
        // R2 not serving yet (or vehicle not in the published master): Supabase fitment rows.
        const { data, error } = await rpc(client, "search_pos_vehicle_spares", {
          p_model_slug: vehicle.modelSlug,
          p_chassis_code: vehicle.chassisCode,
          p_engine_code: vehicle.engineCode,
          p_query: q,
          p_limit: 50,
        });
        if (error) return fail(error, "Vehicle search failed.");
        const results = ((data as { results?: unknown[] } | null)?.results ?? []) as Array<{
          oem_part_number: string;
          description: string | null;
          category_name: string | null;
        }>;
        return { ok: true, data: await partsFromHits(results) };
      }
      if (q.length < 2) return { ok: true, data: [] };
      // The shop's own stock (part number and description words) plus catalogue fitment hits;
      // a stocked part with no fitment row is only found by the first.
      const [stock, catalog] = await Promise.all([
        rpc(client, "search_pos_stock_items", { p_query: q, p_limit: 50 }),
        searchPosCatalog(client, "part", q),
      ]);
      if (stock.error && !catalog.ok) return catalog;
      const stockHits = ((stock.data as { results?: unknown[] } | null)?.results ?? []) as Array<{
        oem_part_number: string;
        description: string | null;
        category_name?: string | null;
      }>;
      const seen = new Set(stockHits.map((h) => h.oem_part_number.trim().toUpperCase()));
      const merged = [
        ...stockHits.map((h) => ({ ...h, category_name: h.category_name ?? null })),
        ...(catalog.ok ? catalog.data : []).filter((h) => !seen.has(h.oem_part_number.trim().toUpperCase())),
      ];
      return { ok: true, data: await partsFromHits(merged) };
    },

    async listBestSellers() {
      const { data, error } = await rpc(client, "list_pos_popular_spares", { p_days: 90, p_limit: 24 });
      if (error) return fail(error, "Could not load best sellers.");
      const rows = (data ?? []) as Array<{
        stock_item_id: string;
        oem_part_number: string;
        description: string | null;
        saleable_qty: number | null;
        unit_price: number | null;
        currency: string | null;
        image_storage_path: string | null;
      }>;
      return {
        ok: true,
        data: rows.map((r) => ({
          stockItemId: r.stock_item_id,
          oemPartNumber: r.oem_part_number,
          name: r.description ?? r.oem_part_number,
          price: r.unit_price == null ? null : { amount: num(r.unit_price), currency: asCurrency(r.currency) },
          saleableQty: r.saleable_qty == null ? null : num(r.saleable_qty),
          imageUrl: imageUrl(r.image_storage_path),
        })),
      };
    },

    async listPins() {
      const { data, error } = await rpc(client, "list_pos_popular_pins");
      if (error) return fail(error, "Could not load pins.");
      const rows = (data ?? []) as Array<{
        item_type: string;
        item_key: string;
        label: string;
        subtitle: string | null;
        search_query: string;
        oem_part_number: string | null;
        image_url: string | null;
      }>;
      return {
        ok: true,
        data: rows.map((r) => ({
          kind: r.item_type as PopularPinKind,
          key: r.item_key,
          label: r.label,
          subtitle: r.subtitle,
          searchQuery: r.search_query,
          oemPartNumber: r.oem_part_number,
          imageUrl: r.image_url,
        })),
      };
    },

    async pin(pin: PopularPin) {
      const { error } = await rpc(client, "upsert_pos_popular_pin", {
        p_item_type: pin.kind,
        p_item_key: pin.key,
        p_label: pin.label,
        p_subtitle: pin.subtitle,
        p_search_query: pin.searchQuery,
        p_oem_part_number: pin.oemPartNumber,
        p_image_url: pin.imageUrl,
      });
      return error ? fail(error, "Pin failed.") : { ok: true, data: true };
    },

    async unpin(pin: PopularPin) {
      const { error } = await rpc(client, "delete_pos_popular_pin", { p_item_type: pin.kind, p_item_key: pin.key });
      return error ? fail(error, "Unpin failed.") : { ok: true, data: true };
    },

    async listHiddenBestSellers() {
      const { data, error } = await rpc(client, "list_pos_hidden_bestsellers");
      if (error) return fail(error, "Could not load removed best sellers.");
      return { ok: true, data: ((data ?? []) as Array<{ stock_item_id: string }>).map((r) => r.stock_item_id) };
    },

    async hideBestSeller(stockItemId) {
      const { error } = await rpc(client, "hide_pos_bestseller", { p_stock_item_id: stockItemId });
      return error ? fail(error, "Remove failed.") : { ok: true, data: true };
    },

    async unhideBestSeller(stockItemId) {
      const { error } = await rpc(client, "unhide_pos_bestseller", { p_stock_item_id: stockItemId });
      return error ? fail(error, "Restore failed.") : { ok: true, data: true };
    },

    async listWarehouses() {
      const { data, error } = await client
        .from("warehouses")
        .select("id, code, name")
        .eq("is_active", true)
        .eq("is_quarantine", false)
        .order("code");
      if (error) return fail(error, "Could not load warehouses.");
      return { ok: true, data: (data ?? []) as Array<{ id: string; code: string; name: string }> };
    },

    async openCart(setup) {
      let warehouseId = setup.warehouseId;
      if (!warehouseId) {
        const { data: wh, error: whErr } = await client
          .from("warehouses")
          .select("id")
          .eq("is_active", true)
          .eq("is_quarantine", false)
          .order("code")
          .limit(1)
          .maybeSingle();
        if (whErr || !wh) return fail(whErr, "No saleable warehouse is configured.");
        warehouseId = (wh as { id: string }).id;
      }
      const { data, error } = await rpc(client, "create_pos_cart", {
        p_warehouse_id: warehouseId,
        p_currency: setup.currency,
        p_fulfillment_mode: setup.fulfillmentMode satisfies FulfillmentMode,
      });
      if (error || typeof data !== "string") return fail(error, "Could not open a sale.");
      return readCart(data);
    },

    loadCart: readCart,

    async addPart(cartId, part, qty) {
      const meta = (await hydrate([part.oemPartNumber])).get(part.oemPartNumber.trim().toUpperCase());
      if (!meta) return { ok: false, error: `${part.oemPartNumber} is not a stocked item.` };
      if (!meta.uomId) return { ok: false, error: `${part.oemPartNumber} has no base unit of measure.` };
      const { error } = await rpc(client, "add_cart_line", {
        p_cart_id: cartId,
        p_stock_item_id: meta.stockItemId,
        p_uom_id: meta.uomId,
        p_qty: qty,
      });
      if (error) return fail(error, "Could not add the part.");
      return readCart(cartId);
    },

    async setLineQty(cartId, lineId, qty) {
      if (!(qty > 0)) return gateway.removeLine(cartId, lineId);
      const current = await readCart(cartId);
      if (!current.ok) return current;
      const line = current.data.lines.find((l) => l.id === lineId);
      if (!line) return { ok: false, error: "Line not found." };
      const { error } = await client
        .from("pos_cart_lines")
        .update({ qty, line_total: roundMoney(line.unitPrice * qty) })
        .eq("id", lineId);
      if (error) return fail(error, "Could not change quantity.");
      return readCart(cartId);
    },

    async removeLine(cartId, lineId) {
      const { error } = await client.from("pos_cart_lines").delete().eq("id", lineId);
      if (error) return fail(error, "Could not remove the line.");
      return readCart(cartId);
    },

    async setCartVehicle(cartId, vehicle: SelectedVehicle | null) {
      const { error } = await rpc(client, "set_pos_cart_vehicle", {
        p_cart_id: cartId,
        p_model_slug: vehicle?.modelSlug ?? null,
        p_model_name: vehicle?.modelName ?? null,
        p_generation: vehicle?.generation ?? null,
        p_chassis_code: vehicle?.chassisCode ?? null,
        p_engine_code: vehicle?.engineCode ?? null,
      });
      if (error) return fail(error, "Could not set the vehicle.");
      return readCart(cartId);
    },

    async voidCart(cartId, g) {
      return governed(g, async (c) => {
        const { error } = await rpc(c, "void_pos_cart_governed", { p_cart_id: cartId, p_reason_code: g.reasonCode, p_notes: g.notes });
        return error ? fail(error, "Could not void the sale.") : { ok: true, data: true };
      });
    },

    async applyDiscount(cartId, percent, g) {
      const res = await governed(g, async (c) => {
        const { error } = await rpc(c, "apply_pos_cart_discount_governed", {
          p_cart_id: cartId,
          p_discount_percent: percent,
          p_reason_code: g.reasonCode,
          p_notes: g.notes,
        });
        return error ? fail<true>(error, "Discount refused.") : { ok: true, data: true as const };
      });
      return res.ok ? readCart(cartId) : res;
    },

    async overrideLinePrice(cartId, lineId, unitPrice, g) {
      const res = await governed(g, async (c) => {
        const { error } = await rpc(c, "apply_pos_line_price_override_governed", {
          p_line_id: lineId,
          p_unit_price: unitPrice,
          p_reason_code: g.reasonCode,
          p_notes: g.notes,
        });
        return error ? fail<true>(error, "Price override refused.") : { ok: true, data: true as const };
      });
      return res.ok ? readCart(cartId) : res;
    },

    async requiresManager(action, value) {
      const { data, error } = await rpc(client, "pos_action_requires_manager", { p_action: action, p_value: value });
      // Fail closed: if the policy cannot be read, ask for a manager.
      if (error) return { ok: true, data: true };
      return { ok: true, data: data !== false };
    },

    async listApprovalPolicies() {
      const { data, error } = await rpc(client, "list_pos_approval_policies", {});
      if (error) return fail(error, "Could not load approval policies.");
      return {
        ok: true,
        data: ((data as Record<string, unknown>[] | null) ?? []).map((r) => ({
          action: String(r.action),
          thresholdValue: num(r.threshold_value),
          alwaysRequireManager: r.always_require_manager === true,
          reasonRequired: r.reason_required !== false,
          updatedAt: (r.updated_at as string | null) ?? null,
        })),
      };
    },

    async setApprovalPolicy(p) {
      const { error } = await rpc(client, "set_pos_approval_policy", {
        p_action: p.action,
        p_threshold_value: p.thresholdValue,
        p_always_require_manager: p.alwaysRequireManager,
        p_reason_required: p.reasonRequired,
      });
      return error ? fail(error, "Could not save the policy.") : { ok: true, data: true };
    },

    async searchCustomers(query) {
      const { data, error } = await rpc(client, "list_pos_customers", { p_query: query.trim() || null, p_limit: 30 });
      if (error) return fail(error, "Customer search failed.");
      return { ok: true, data: ((data ?? []) as Record<string, unknown>[]).map(customerFromRow) };
    },

    async createCustomer(input: CustomerInput) {
      const { data, error } = await rpc(client, "create_pos_customer", {
        p_customer_kind: input.kind,
        p_display_name: input.displayName,
        p_business_name: input.businessName,
        p_email: input.email,
        p_phone_e164: input.phoneE164,
        p_whatsapp_e164: input.whatsappE164,
      });
      if (error || typeof data !== "string") return fail(error, "Could not create the customer.");
      return { ok: true, data: { id: data, ...input } };
    },

    async updateCustomer(id, input) {
      const { error } = await rpc(client, "update_pos_customer", {
        p_customer_id: id,
        p_customer_kind: input.kind,
        p_display_name: input.displayName,
        p_business_name: input.businessName,
        p_email: input.email,
        p_phone_e164: input.phoneE164,
        p_whatsapp_e164: input.whatsappE164,
      });
      if (error) return fail(error, "Could not update the customer.");
      return { ok: true, data: { id, ...input } };
    },

    async setCartCustomer(cartId, customerId) {
      const { error } = await rpc(client, "set_pos_cart_customer", { p_cart_id: cartId, p_customer_id: customerId });
      if (error) return fail(error, "Could not attach the customer.");
      return readCart(cartId);
    },

    async listGarage(customerId) {
      const { data, error } = await rpc(client, "list_pos_customer_garage", { p_customer_id: customerId });
      if (error) return fail(error, "Could not load the garage.");
      return {
        ok: true,
        data: ((data ?? []) as Record<string, unknown>[]).map(
          (r): GarageVehicle => ({
            id: String(r.id),
            modelSlug: (r.model_slug as string | null) ?? null,
            model: (r.model as string | null) ?? null,
            generation: (r.generation as string | null) ?? null,
            chassisCode: (r.chassis_code as string | null) ?? null,
            engine: (r.engine as string | null) ?? null,
            vin: (r.vin as string | null) ?? null,
            isPrimary: Boolean(r.is_primary),
          }),
        ),
      };
    },

    async saveGarageVehicle(customerId, vehicle, isPrimary) {
      const { error } = await rpc(client, "upsert_pos_customer_garage_vehicle", {
        p_customer_id: customerId,
        p_model_slug: vehicle.modelSlug,
        p_model: vehicle.modelName,
        p_generation: vehicle.generation,
        p_chassis_code: vehicle.chassisCode,
        p_engine: vehicle.engineCode,
        p_is_primary: isPrimary,
      });
      return error ? fail(error, "Could not save the vehicle.") : { ok: true, data: true };
    },

    async createScanSession(cartId) {
      const { data, error } = await rpc(client, "create_pos_scan_session", { p_cart_id: cartId });
      const row = (Array.isArray(data) ? data[0] : data) as { session_id: string; pairing_code: string; expires_at: string } | null;
      if (error || !row) return fail(error, "Could not create a pairing code.");
      return { ok: true, data: { sessionId: row.session_id, pairingCode: row.pairing_code, expiresAt: row.expires_at } };
    },

    async revokeScanSession(sessionId) {
      const { error } = await rpc(client, "revoke_pos_scan_session", { p_session_id: sessionId });
      return error ? fail(error, "Could not end the pairing.") : { ok: true, data: true };
    },

    watchCompanion(cartId, sessionId, onCart, onStatus) {
      const channel = client
        .channel(`pos-companion-${sessionId}`)
        .on("postgres_changes", { event: "*", schema: "public", table: "pos_cart_lines", filter: `cart_id=eq.${cartId}` }, () => onCart())
        .on("postgres_changes", { event: "UPDATE", schema: "public", table: "pos_scan_sessions", filter: `id=eq.${sessionId}` }, (payload) => {
          const status = (payload.new as { status?: string } | null)?.status;
          if (status) onStatus(status);
        })
        .subscribe();
      return () => {
        void client.removeChannel(channel);
      };
    },

    async prepareCheckout(cartId, requestId, contacts) {
      const { data, error } = await rpc(client, "prepare_pos_commerce_checkout_v2", {
        p_cart_id: cartId,
        p_checkout_request_id: requestId,
        p_reservation_ttl: "20 minutes",
        p_receipt_email: contacts.email,
        p_receipt_whatsapp_e164: contacts.whatsappE164,
        p_receipt_phone_e164: contacts.phoneE164,
      });
      if (error || typeof data !== "string") return fail(error, "Could not reserve the sale for payment.");
      return { ok: true, data };
    },

    async paymentStatus(orderId) {
      const { data, error } = await rpc(client, "get_pos_payment_status", { p_order_id: orderId });
      if (error || !data) return fail(error, "Could not read the payment status.");
      return { ok: true, data: paymentStatusFromRow(data as Record<string, unknown>) };
    },

    async settleTenders(orderId, paymentRequestId, tenders) {
      const { data, error } = await rpc(client, "settle_pos_commerce_tenders", {
        p_order_id: orderId,
        p_payment_request_id: paymentRequestId,
        p_tenders: tenders.map((t) => ({ tender: t.tender, amount: roundMoney(t.amount) })),
      });
      if (error || !data) return fail(error, "Payment was not recorded.");
      const r = data as Record<string, unknown>;
      return { ok: true, data: { invoiceId: String(r.invoice_id), state: String(r.state ?? "paid") } };
    },

    async providerAvailability() {
      // A provider without keys answers 503 before it reads the body, so an empty probe is harmless.
      const probe = async (fn: string): Promise<string | null> => {
        const { error } = await client.functions.invoke(fn, { body: {} });
        const status = (error as { context?: Response } | null)?.context?.status;
        if (status === 503) return "Not set up for this shop yet.";
        if (status === 401) return "Sign in again to use this provider.";
        if (error && status == null) return "Provider unreachable.";
        return null;
      };
      const [ecocash, paynow, contipay] = await Promise.all([probe("ecocash-initiate"), probe("paynow-initiate"), probe("contipay-initiate")]);
      return { ok: true, data: { ecocash, paynow, contipay } };
    },

    async startProvider(orderId, provider, params) {
      const body: Record<string, unknown> = { pos_commerce_order_id: orderId, channel: "pos", metadata: { source: "web_pos" } };
      if (provider === "ecocash") body.payer_msisdn = params.msisdn;
      else {
        body.method = params.method;
        body.return_url = params.returnUrl;
        body.cancel_url = params.returnUrl;
        if (params.msisdn) {
          body.phone = params.msisdn;
          body.authphone = params.msisdn;
        }
      }
      const { data, error } = await client.functions.invoke(`${provider}-initiate`, { body });
      let payload = data as Record<string, unknown> | null;
      if (error) {
        const ctx = (error as { context?: Response }).context;
        payload = ctx ? ((await ctx.json().catch(() => null)) as Record<string, unknown> | null) : null;
        // The intent exists even when the provider call failed: the server tracks it; recovery can see it.
        if (!payload?.intent_id) return { ok: false, error: operatorMessage(String(payload?.error ?? error.message), "The payment request was not sent.") };
      }
      if (!payload || typeof payload.intent_id !== "string") return { ok: false, error: "The payment request was not sent." };
      return {
        ok: true,
        data: {
          intentId: payload.intent_id,
          checkoutUrl: typeof payload.checkout_url === "string" ? payload.checkout_url : null,
          message: typeof payload.message === "string" ? payload.message : typeof payload.error === "string" ? payload.error : null,
        },
      };
    },

    async cancelCheckout(orderId, reason) {
      const { error } = await rpc(client, "cancel_pos_commerce_checkout", { p_order_id: orderId, p_reason: reason });
      return error ? fail(error, "Could not cancel the payment.") : { ok: true, data: true };
    },

    async checkoutOnAccount(cartId, contacts) {
      const { data, error } = await rpc(client, "checkout_pos_cart_on_account", {
        p_cart_id: cartId,
        p_receipt_email: contacts.email,
        p_receipt_whatsapp_e164: contacts.whatsappE164,
        p_receipt_phone_e164: contacts.phoneE164,
      });
      if (error || typeof data !== "string") return fail(error, "On-account sale refused.");
      return { ok: true, data };
    },

    async listPaymentRecovery() {
      const { data, error } = await rpc(client, "list_pos_payment_recovery", { p_limit: 100 });
      if (error) return fail(error, "Could not load payments to resolve.");
      return {
        ok: true,
        data: ((data as Record<string, unknown>[] | null) ?? []).map((r) => ({
          orderId: String(r.order_id),
          state: String(r.state),
          total: num(r.total),
          currency: asCurrency(r.currency),
          activeProvider: (r.active_provider as string | null) ?? null,
          settledProvider: (r.settled_provider as string | null) ?? null,
          salesInvoiceId: (r.sales_invoice_id as string | null) ?? null,
          paymentException: (r.payment_exception as string | null) ?? null,
          reservationExpiresAt: (r.reservation_expires_at as string | null) ?? null,
          updatedAt: String(r.updated_at ?? ""),
          openExceptions: num(r.open_exception_count),
        })),
      };
    },

    async repairPaidOrder(orderId, notes, manager) {
      return withManagerOrSelf(manager, async (m) => {
        const { data, error } = await rpc(m, "repair_pos_paid_order", { p_order_id: orderId, p_notes: notes });
        if (error || typeof data !== "string") return fail(error, "The paid order could not be repaired.");
        return { ok: true, data };
      });
    },

    async findSplit(orderId) {
      const { data, error } = await rpc(client, "find_pos_split_payment", { p_order_id: orderId });
      if (error) return fail(error, "Could not read the part payments.");
      return { ok: true, data: data ? splitFromRow(data as Record<string, unknown>) : null };
    },

    async startSplit(orderId) {
      const { data, error } = await rpc(client, "start_pos_split_payment", { p_order_id: orderId });
      if (error || !data) return fail(error, "Could not start part payments.");
      return { ok: true, data: splitFromRow(data as Record<string, unknown>) };
    },

    async getSplit(sessionId) {
      const { data, error } = await rpc(client, "get_pos_split_payment", { p_session_id: sessionId });
      if (error || !data) return fail(error, "Could not read the part payments.");
      return { ok: true, data: splitFromRow(data as Record<string, unknown>) };
    },

    async addSplitLeg(sessionId, tender, amount, requestId, reference) {
      const { data, error } = await rpc(client, "add_pos_split_payment_leg", {
        p_session_id: sessionId,
        p_tender: tender,
        p_amount: roundMoney(amount),
        p_request_id: requestId,
        p_external_reference: reference,
      });
      if (error || !data) return fail(error, "The part payment was not recorded.");
      return { ok: true, data: splitFromRow((data as Record<string, unknown>).session as Record<string, unknown>) };
    },

    async acceptReducedBasket(sessionId, items, notes) {
      const { data, error } = await rpc(client, "accept_pos_split_affordable_items", {
        p_session_id: sessionId,
        p_items: items.map((i) => ({ cart_line_id: i.cartLineId, qty: i.qty })),
        p_customer_confirmed: true,
        p_notes: notes,
      });
      if (error || !data) return fail(error, "The reduced basket was not accepted.");
      return { ok: true, data: splitFromRow(data as Record<string, unknown>) };
    },

    async cancelSplit(sessionId, reason, feePolicy) {
      const { data, error } = await rpc(client, "request_pos_split_cancellation", { p_session_id: sessionId, p_reason: reason, p_fee_policy: feePolicy });
      if (error || !data) return fail(error, "The part-paid sale was not cancelled.");
      return { ok: true, data: splitFromRow(data as Record<string, unknown>) };
    },

    async retrySplitFinalization(sessionId) {
      const { data, error } = await rpc(client, "retry_pos_split_finalization", { p_session_id: sessionId });
      if (error || !data) return fail(error, "The sale could not be posted.");
      return { ok: true, data: splitFromRow(data as Record<string, unknown>) };
    },

    async listSplitRecovery() {
      const { data, error } = await rpc(client, "list_pos_split_payment_recovery", { p_limit: 100 });
      if (error) return fail(error, "Could not load part payments to resolve.");
      return {
        ok: true,
        data: ((data as Record<string, unknown>[] | null) ?? []).map((r) => ({
          sessionId: String(r.session_id),
          orderId: String(r.order_id),
          status: String(r.status) as SplitRecoveryItem["status"],
          documentNumber: (r.document_number as string | null) ?? null,
          customerName: (r.customer_name as string | null) ?? null,
          total: num(r.total),
          currency: asCurrency(r.currency),
          updatedAt: String(r.updated_at ?? ""),
          session: splitFromRow(r.payload as Record<string, unknown>),
        })),
      };
    },

    async splitRefundStep(refundId, step, manager) {
      return withManagerOrSelf(manager, async (m) => {
        const { data, error } =
          step.kind === "approve"
            ? await rpc(m, "approve_pos_split_refund", {
                p_refund_id: refundId,
                p_fee_policy: step.feePolicy,
                p_estimated_provider_fee: 0,
                p_estimated_transfer_fee: 0,
                p_customer_fee: roundMoney(step.customerFee),
                p_expected_days: 3,
                p_notes: step.notes,
              })
            : step.kind === "complete"
              ? await rpc(m, "complete_pos_split_refund", {
                  p_refund_id: refundId,
                  p_provider_ref: step.providerRef,
                  p_actual_provider_fee: 0,
                  p_actual_transfer_fee: 0,
                  p_actual_customer_fee: null,
                  p_notes: step.notes,
                })
              : await rpc(m, "fail_pos_split_refund", { p_refund_id: refundId, p_reason: step.reason });
        if (error || !data) return fail(error, "The refund step was not recorded.");
        return { ok: true, data: splitFromRow(data as Record<string, unknown>) };
      });
    },

    async listCardTerminals() {
      const { data, error } = await rpc(client, "list_pos_card_terminals", { p_warehouse_id: null, p_device_id: null });
      if (error) return fail(error, "Could not load the card machines.");
      return {
        ok: true,
        data: ((data as Record<string, unknown>[] | null) ?? []).map(
          (r): CardTerminal => ({
            id: String(r.id),
            code: String(r.code ?? ""),
            label: String(r.label ?? ""),
            acquirerName: (r.acquirer_name as string | null) ?? null,
            externalTerminalId: (r.external_terminal_id as string | null) ?? null,
            config: (r.adapter_config as CardTerminal["config"] | null) ?? {},
            warehouseId: (r.warehouse_id as string | null) ?? null,
            deviceId: (r.device_id as string | null) ?? null,
            isActive: r.is_active !== false,
          }),
        ),
      };
    },

    async saveCardTerminal(t) {
      // Only the keys the server allows, and no blanks: it refuses anything else.
      const config = Object.fromEntries(Object.entries(t.config).filter(([, v]) => typeof v === "string" && v.trim() !== "").map(([k, v]) => [k, v!.trim()]));
      const { data, error } = await rpc(client, "upsert_pos_card_terminal", {
        p_id: t.id,
        p_code: t.code.trim(),
        p_label: t.label.trim(),
        p_acquirer_name: t.acquirerName?.trim() || null,
        p_external_terminal_id: t.externalTerminalId?.trim() || null,
        p_adapter_key: "android_intent_v1",
        p_adapter_config: config,
        p_warehouse_id: t.warehouseId,
        p_device_id: t.deviceId?.trim() || null,
        p_is_active: t.isActive,
      });
      if (error || typeof data !== "string") return fail(error, "The card machine was not saved.");
      return { ok: true, data };
    },

    async listTerminalRecovery() {
      const { data, error } = await rpc(client, "list_pos_card_terminal_recovery", { p_limit: 100 });
      if (error) return fail(error, "Could not load card-machine payments to resolve.");
      return {
        ok: true,
        data: ((data as Record<string, unknown>[] | null) ?? []).map(
          (r): TerminalRecoveryItem => ({
            attemptId: String(r.id),
            operation: String(r.operation ?? ""),
            status: String(r.status ?? ""),
            terminalLabel: (r.terminal_label as string | null) ?? null,
            orderId: (r.commerce_order_id as string | null) ?? null,
            amount: num(r.amount),
            currency: asCurrency(r.currency),
            transactionId: (r.terminal_transaction_id as string | null) ?? null,
            cardLast4: (r.card_last4 as string | null) ?? null,
            message: ((r.finalization_error ?? r.response_message) as string | null) ?? null,
            updatedAt: String(r.updated_at ?? ""),
          }),
        ),
      };
    },

    async finishTerminalPayment(attemptId) {
      const { data, error } = await rpc(client, "finalize_pos_card_terminal_purchase", { p_attempt_id: attemptId });
      if (error || !data) return fail(error, "The card payment could not be applied.");
      const r = data as Record<string, unknown>;
      return { ok: true, data: { status: String(r.status ?? ""), error: ((r.error ?? r.finalization_error) as string | null) ?? null } };
    },

    async listPickupOrders(query) {
      const { data, error } = await rpc(client, "list_pos_pickup_orders", { p_query: query.trim() || null, p_limit: 100 });
      if (error) return fail(error, "Could not load pickups.");
      return {
        ok: true,
        data: ((data as Record<string, unknown>[] | null) ?? []).map((r) => ({
          orderId: String(r.order_id),
          documentNumber: (r.document_number as string | null) ?? null,
          customerName: (r.customer_name as string | null) ?? null,
          state: String(r.state),
          total: num(r.total),
          currency: asCurrency(r.currency),
          salesInvoiceId: (r.sales_invoice_id as string | null) ?? null,
          settledProvider: (r.settled_provider as string | null) ?? null,
          updatedAt: String(r.updated_at ?? ""),
        })),
      };
    },

    async collectOrder(orderId, notes) {
      const { error } = await rpc(client, "collect_pos_commerce_order", { p_order_id: orderId, p_notes: notes });
      return error ? fail(error, "Could not mark the order collected.") : { ok: true, data: true };
    },

    async loadReceipt(invoiceId) {
      const { data: inv, error } = await client
        .from("sales_invoices")
        .select("*")
        .eq("id", invoiceId)
        .maybeSingle();
      if (error || !inv) return fail(error, "Invoice not found.");
      const i = inv as Record<string, unknown>;
      const { data: lineRows } = await client
        .from("sales_invoice_lines")
        .select("qty, unit_price, line_total, stock_items ( oem_part_number, description )")
        .eq("invoice_id", invoiceId)
        .order("created_at");
      const lines = ((lineRows ?? []) as Array<{
        qty: number;
        unit_price: number;
        line_total: number;
        stock_items: { oem_part_number: string; description: string | null } | { oem_part_number: string; description: string | null }[] | null;
      }>).map((r) => {
        const si = Array.isArray(r.stock_items) ? r.stock_items[0] : r.stock_items;
        return {
          name: si?.description ?? si?.oem_part_number ?? "",
          oemPartNumber: si?.oem_part_number ?? "",
          qty: num(r.qty),
          unitPrice: num(r.unit_price),
          lineTotal: num(r.line_total),
        };
      });
      const v = vehicleFromRow(i, "vehicle_");
      return {
        ok: true,
        data: {
          invoiceId,
          documentNumber: (i.document_number as string | null) ?? null,
          postedAt: (i.posted_at as string | null) ?? (i.created_at as string | null) ?? null,
          currency: asCurrency(i.currency),
          customerName: (i.customer_display_name as string | null) ?? null,
          lines,
          subtotal: num(i.subtotal),
          total: num(i.total),
          amountPaid: num(i.amount_paid),
          tenders: [],
          vehicleLabel: v ? `${v.modelName} ${v.chassisCode} ${v.engineCode}`.trim() : null,
          operator: "",
        },
      };
    },

    async parkCart(cartId) {
      const { error } = await rpc(client, "park_pos_cart", { p_cart_id: cartId });
      return error ? fail(error, "Could not park the sale.") : { ok: true, data: true };
    },

    async listParked() {
      const { data, error } = await client
        .from("pos_carts")
        .select("id, document_number, updated_at, currency, pos_cart_lines ( line_total )")
        .eq("status", "parked")
        .eq("channel", "pos")
        .order("updated_at", { ascending: false })
        .limit(50);
      if (error) return fail(error, "Could not load parked sales.");
      return {
        ok: true,
        data: ((data ?? []) as Array<{ id: string; document_number: string | null; updated_at: string; currency: string; pos_cart_lines: Array<{ line_total: number }> | null }>).map((r) => ({
          id: r.id,
          documentNumber: r.document_number,
          updatedAt: r.updated_at,
          currency: asCurrency(r.currency),
          lineCount: r.pos_cart_lines?.length ?? 0,
          total: roundMoney((r.pos_cart_lines ?? []).reduce((sum, l) => sum + num(l.line_total), 0)),
        })),
      };
    },

    async resumeCart(cartId) {
      const { error } = await rpc(client, "resume_pos_cart", { p_cart_id: cartId });
      if (error) return fail(error, "Could not resume the sale.");
      return readCart(cartId);
    },

    async listQuotations() {
      const { data, error } = await rpc(client, "list_pos_quotations", { p_status: null, p_limit: 50 });
      if (error) return fail(error, "Could not load quotations.");
      return {
        ok: true,
        data: ((data ?? []) as Record<string, unknown>[]).map((r) => ({
          id: String(r.id),
          documentNumber: (r.document_number as string | null) ?? null,
          status: String(r.status) as QuotationStatus,
          validUntil: (r.valid_until as string | null) ?? null,
          sentChannel: (r.sent_channel as string | null) ?? null,
          createdAt: String(r.created_at),
          lineCount: num(r.line_count),
          total: num(r.total),
          currency: asCurrency(r.currency),
        })),
      };
    },

    async createQuotation(cartId, validUntil, notes) {
      const { data, error } = await rpc(client, "create_pos_quotation_from_cart", {
        p_cart_id: cartId,
        p_valid_until: validUntil,
        p_notes: notes,
      });
      if (error || typeof data !== "string") return fail(error, "Could not create the quotation.");
      return { ok: true, data };
    },

    async sendQuotation(quotationId, channel, contact) {
      const { error } = await rpc(client, "send_pos_quotation", { p_quotation_id: quotationId, p_channel: channel, p_contact: contact });
      return error ? fail(error, "Could not send the quotation.") : { ok: true, data: true };
    },

    async convertQuotation(quotationId) {
      const { data, error } = await rpc(client, "convert_pos_quotation_to_cart", { p_quotation_id: quotationId });
      if (error || typeof data !== "string") return fail(error, "Could not convert the quotation.");
      return readCart(data);
    },

    async listRecentInvoices(query) {
      const { data, error } = await rpc(client, "list_pos_recent_invoices", { p_query: query.trim() || null, p_limit: 50 });
      if (error) return fail(error, "Could not load recent sales.");
      return {
        ok: true,
        data: ((data ?? []) as Record<string, unknown>[]).map((r) => ({
          id: String(r.id),
          documentNumber: (r.document_number as string | null) ?? null,
          customerName: (r.customer_name as string | null) ?? null,
          total: num(r.total),
          currency: asCurrency(r.currency),
          postedAt: (r.posted_at as string | null) ?? null,
          vehicleLabel: r.vehicle_chassis_code
            ? [r.vehicle_model_name, r.vehicle_chassis_code, r.vehicle_engine_code].filter(Boolean).join(" ")
            : null,
        })),
      };
    },

    async refundInvoice(invoiceId, g) {
      return governed(g, async (c) => {
        const { data, error } = await rpc(c, "post_pos_refund_governed", { p_invoice_id: invoiceId, p_reason_code: g.reasonCode, p_notes: g.notes });
        if (error || typeof data !== "string") return fail(error, "Refund refused.");
        return { ok: true, data };
      });
    },

    async getInvoiceDetail(invoiceId) {
      const { data, error } = await rpc(client, "get_pos_invoice_detail", { p_invoice_id: invoiceId });
      if (error || !data) return fail(error, "Could not load the sale.");
      const d = data as Record<string, unknown>;
      const detail: InvoiceDetail = {
        id: String(d.id),
        documentNumber: (d.document_number as string | null) ?? null,
        customerId: (d.customer_id as string | null) ?? null,
        currency: asCurrency(d.currency),
        total: num(d.total),
        amountPaid: num(d.amount_paid),
        postedAt: (d.posted_at as string | null) ?? null,
        tillSessionId: (d.till_session_id as string | null) ?? null,
        lines: ((d.lines ?? []) as Record<string, unknown>[]).map((l) => ({
          id: String(l.id),
          stockItemId: String(l.stock_item_id),
          partNumber: String(l.oem_part_number ?? ""),
          description: (l.description as string | null) ?? null,
          uomId: String(l.uom_id),
          qty: num(l.qty),
          unitPrice: num(l.unit_price),
          lineTotal: num(l.line_total),
          isCore: Boolean(l.is_core_charge),
          returnableQty: num(l.returnable_qty),
        })),
      };
      return { ok: true, data: detail };
    },

    async createReturnCase(d) {
      const { data, error } = await rpc(client, "create_pos_return_case", {
        p_invoice_id: d.invoiceId,
        p_resolution: d.resolution,
        p_reason_code: d.reasonCode,
        p_lines: d.lines.map((l) => ({ invoice_line_id: l.invoiceLineId, qty: l.qty, condition: l.condition })),
        p_notes: d.notes,
        p_replacement_lines: d.replacementLines ? replacementJson(d.replacementLines) : null,
        p_till_session_id: d.tillSessionId,
      });
      if (error || typeof data !== "string") return fail(error, "Could not start the return.");
      return { ok: true, data };
    },

    async postReturnCase(caseId, manager) {
      return governed({ reasonCode: "", notes: null, manager }, async (c) => {
        const { error } = await rpc(c, "post_pos_return_case", { p_return_case_id: caseId });
        if (error) return fail(error, "The return was not posted.");
        return { ok: true, data: true as const };
      });
    },

    async postCoreReturn(r, manager) {
      return governed({ reasonCode: r.reasonCode, notes: r.notes, manager }, async (c) => {
        const { error } = await rpc(c, "post_pos_core_return", {
          p_invoice_id: r.invoiceId,
          p_source_core_line_id: r.coreLineId,
          p_qty: r.qty,
          p_resolution: r.resolution,
          p_reason_code: r.reasonCode,
          p_till_session_id: r.tillSessionId,
          p_notes: r.notes,
        });
        if (error) return fail(error, "The core return was not posted.");
        return { ok: true, data: true as const };
      });
    },

    async openWarrantyClaim(invoiceId, invoiceLineId, serialId, notes) {
      const { data, error } = await rpc(client, "open_pos_warranty_claim", {
        p_sales_invoice_id: invoiceId,
        p_invoice_line_id: invoiceLineId,
        p_stock_serial_id: serialId,
        p_notes: notes,
      });
      if (error || typeof data !== "string") return fail(error, "Could not open the warranty claim.");
      return { ok: true, data };
    },

    async findWarrantySerial(serial) {
      const { data, error } = await rpc(client, "find_pos_warranty_serial", { p_serial_number: serial.trim() });
      if (error) return fail(error, "Could not look up the serial number.");
      return {
        ok: true,
        data: ((data ?? []) as Record<string, unknown>[]).map((r) => ({
          id: String(r.id),
          serialNumber: String(r.serial_number),
          stockItemId: String(r.stock_item_id),
          partNumber: (r.oem_part_number as string | null) ?? null,
          status: String(r.status ?? ""),
        })),
      };
    },

    async listWarrantyClaims(query, status) {
      const { data, error } = await rpc(client, "list_pos_warranty_claims", { p_query: query.trim() || null, p_status: status, p_limit: 100 });
      if (error) return fail(error, "Could not load warranty claims.");
      return { ok: true, data: ((data ?? []) as Record<string, unknown>[]).map(warrantyFromRow) };
    },

    async decideWarrantyClaim(claim, decision, manager) {
      return governed({ reasonCode: "", notes: null, manager }, async (c) => {
        const { error } =
          decision.kind === "reject"
            ? await rpc(c, "reject_pos_warranty_claim", { p_claim_id: claim.id, p_reason: decision.reason })
            : await rpc(c, "approve_pos_warranty_claim", warrantyApproveArgs(claim, decision, "p_"));
        if (error) return fail(error, "The warranty decision was not saved.");
        return { ok: true, data: true as const };
      });
    },

    async closeWarrantyClaim(claimId) {
      const { error } = await rpc(client, "close_warranty_claim", { p_claim_id: claimId });
      if (error) return fail(error, "Could not close the claim.");
      return { ok: true, data: true };
    },

    async listLetters(sourceKind, sourceId, query) {
      const { data, error } = await rpc(client, "list_payment_resolution_letters", {
        p_source_kind: sourceKind,
        p_source_id: sourceId,
        p_query: query.trim() || null,
        p_limit: 50,
      });
      if (error) return fail(error, "Could not load payment letters.");
      return { ok: true, data: ((data ?? []) as Record<string, unknown>[]).map(letterSummaryFromRow) };
    },

    async issueLetter(sourceKind, sourceId, notes) {
      const { data, error } = await rpc(client, "create_payment_resolution_letter", { p_source_kind: sourceKind, p_source_id: sourceId, p_issue_notes: notes });
      if (error || typeof data !== "string") {
        if (error && /signature required/i.test(error.message)) return { ok: false, error: "Add your signature in Settings → My signature first." };
        return fail(error, "The letter was not issued.");
      }
      return { ok: true, data };
    },

    async getLetter(letterId) {
      const { data, error } = await rpc(client, "get_payment_resolution_letter_render_data", { p_letter_id: letterId });
      if (error || !data) return fail(error, "Could not load the letter.");
      const d = data as { letter: Record<string, unknown>; business: Record<string, unknown> | null };
      const l = d.letter;
      let signatureUrl: string | null = null;
      if (l.signature_storage_path) {
        const signed = await client.storage.from(String(l.signature_storage_bucket ?? "staff-signatures")).createSignedUrl(String(l.signature_storage_path), 600);
        signatureUrl = signed.data?.signedUrl ?? null;
      }
      return {
        ok: true,
        data: {
          ...letterSummaryFromRow(l),
          externalReference: str(l.external_reference),
          providerReference: str(l.provider_reference),
          terminalTransactionId: str(l.terminal_transaction_id),
          rrn: str(l.rrn),
          authorizationCode: str(l.authorization_code),
          cardLast4: str(l.card_last4),
          cardScheme: str(l.card_scheme),
          failureDetail: str(l.failure_detail),
          managerEmployeeCode: str(l.manager_employee_code),
          issueNotes: str(l.issue_notes),
          signatureSha256: str(l.signature_sha256),
          signatureUrl,
          business: d.business ? profileFromRow(d.business) : null,
        },
      };
    },

    async getMySignature() {
      const { data, error } = await rpc(client, "get_my_manager_signature");
      if (error || !data) return fail(error, "Could not load your signature.");
      const d = data as Record<string, unknown>;
      let imageUrl: string | null = null;
      if (d.signature_path) {
        const signed = await client.storage.from(String(d.signature_bucket ?? "staff-signatures")).createSignedUrl(String(d.signature_path), 600);
        imageUrl = signed.data?.signedUrl ?? null;
      }
      const sig: MySignature = {
        fullName: String(d.full_name ?? ""),
        employeeCode: str(d.employee_code),
        hasSignature: Boolean(d.has_signature),
        capturedAt: str(d.signature_captured_at),
        imageUrl,
      };
      return { ok: true, data: sig };
    },

    async saveMySignature(image) {
      if (image.type !== "image/png" && image.type !== "image/jpeg") return { ok: false, error: "Use a PNG or JPEG image." };
      if (image.size > 2 * 1024 * 1024) return { ok: false, error: "The signature image must be under 2 MB." };
      const { data: auth } = await client.auth.getUser();
      const uid = auth.user?.id;
      if (!uid) return { ok: false, error: "Sign in again to save your signature." };
      const bytes = await image.arrayBuffer();
      const hash = await sha256Hex(bytes);
      // Own folder only (storage policy); a new file per signature keeps old letters' signatures intact.
      const path = `${uid}/signature-${hash.slice(0, 16)}.${image.type === "image/png" ? "png" : "jpg"}`;
      const up = await client.storage.from("staff-signatures").upload(path, image, { contentType: image.type, upsert: true });
      if (up.error) return fail(up.error, "The signature was not uploaded.");
      const { error } = await rpc(client, "register_my_manager_signature", { p_storage_path: path, p_mime_type: image.type, p_sha256: hash });
      if (error) return fail(error, "The signature was not saved.");
      return this.getMySignature();
    },

    async getBusinessProfile() {
      const { data, error } = await rpc(client, "get_business_document_profile");
      if (error || !data) return fail(error, "Could not load the business details.");
      return { ok: true, data: profileFromRow(data as Record<string, unknown>) };
    },

    async setBusinessProfile(p) {
      const { data, error } = await rpc(client, "set_business_document_profile", {
        p_legal_name: p.legalName,
        p_trading_name: p.tradingName,
        p_domain: p.domain,
        p_city: p.city,
        p_country: p.country,
        p_address_line1: p.addressLine1,
        p_address_line2: p.addressLine2,
        p_phone_e164: p.phone,
        p_email: p.email,
        p_registration_number: p.registrationNumber,
      });
      if (error || !data) return fail(error, "The business details were not saved.");
      return { ok: true, data: profileFromRow(data as Record<string, unknown>) };
    },

    async createFulfillment(f) {
      // Requests are in the part's stock unit.
      const { data: item, error: itemError } = await client.from("stock_items").select("base_uom_id").eq("id", f.stockItemId).maybeSingle();
      const uom = (item as { base_uom_id: string | null } | null)?.base_uom_id;
      if (itemError || !uom) return fail(itemError, "This part has no stock unit set up.");
      const { data, error } = await rpc(client, "create_pos_fulfillment_request", {
        p_kind: f.kind,
        p_stock_item_id: f.stockItemId,
        p_uom_id: uom,
        p_qty: f.qty,
        p_source_warehouse_id: f.sourceWarehouseId,
        p_destination_warehouse_id: f.destinationWarehouseId,
        p_customer_id: f.customerId,
        p_cart_id: f.cartId,
        p_invoice_id: null,
        p_notes: f.notes,
        p_hold_minutes: f.holdMinutes,
      });
      if (error || typeof data !== "string") return fail(error, "Could not hold the part.");
      return { ok: true, data };
    },

    async listFulfillment(query, status) {
      const { data, error } = await rpc(client, "list_pos_fulfillment_requests", { p_query: query.trim() || null, p_status: status, p_limit: 100 });
      if (error) return fail(error, "Could not load collections and transfers.");
      return {
        ok: true,
        data: ((data ?? []) as Record<string, unknown>[]).map((r) => ({
          id: String(r.id),
          documentNumber: (r.document_number as string | null) ?? null,
          kind: String(r.kind) as FulfillmentRequest["kind"],
          status: String(r.status) as FulfillmentRequest["status"],
          stockItemId: String(r.stock_item_id),
          partNumber: String(r.oem_part_number ?? ""),
          description: (r.description as string | null) ?? null,
          qty: num(r.qty),
          sourceWarehouseId: (r.source_warehouse_id as string | null) ?? null,
          sourceName: (r.source_warehouse_name as string | null) ?? null,
          destinationWarehouseId: (r.destination_warehouse_id as string | null) ?? null,
          destinationName: (r.destination_warehouse_name as string | null) ?? null,
          customerId: (r.customer_id as string | null) ?? null,
          cartId: (r.cart_id as string | null) ?? null,
          invoiceId: (r.invoice_id as string | null) ?? null,
          expiresAt: (r.expires_at as string | null) ?? null,
          readyAt: (r.ready_at as string | null) ?? null,
          collectedAt: (r.collected_at as string | null) ?? null,
          createdAt: String(r.created_at),
        })),
      };
    },

    async fulfillmentStep(requestId, step, notes) {
      const fn = { approve: "approve_pos_fulfillment_request", ready: "mark_pos_fulfillment_ready", collect: "collect_pos_fulfillment_request", cancel: "cancel_pos_fulfillment_request" }[step];
      const { error } = await rpc(client, fn, { p_request_id: requestId, p_notes: notes });
      if (error) return fail(error, "That step was refused.");
      return { ok: true, data: true };
    },

    async listStockAvailability(stockItemId) {
      const { data, error } = await rpc(client, "list_pos_stock_availability", { p_stock_item_id: stockItemId });
      if (error) return fail(error, "Could not load stock by branch.");
      return {
        ok: true,
        data: ((data ?? []) as Record<string, unknown>[]).map((r) => ({
          warehouseId: String(r.warehouse_id),
          code: String(r.warehouse_code ?? ""),
          name: String(r.warehouse_name ?? ""),
          onHand: num(r.on_hand),
          reserved: num(r.reserved),
          available: num(r.available),
          incoming: num(r.transfer_incoming),
        })),
      };
    },

    async listEpcVariants(modelSlug) {
      return gateway.listVariants(modelSlug);
    },

    // Sections, diagram lists, parts and images come from the full catalogue through
    // `catalog-live-r2` (vehicle routing in Supabase, shards and images in R2), never fixture rows.
    // The variant slug is the vehicle-master id; the section slug is the catalogue section id.
    async listEpcSections(_modelSlug, variantSlug) {
      try {
        const res = await catalogGatewayGet<{ sections?: StaffSectionRow[] }>(client, "staff-sections", {
          maker: NISSAN_MAKER_SLUG,
          variant_id: variantSlug,
        });
        return {
          ok: true,
          data: (res.sections ?? []).map((r) => ({ slug: r.section_id, name: r.display_name, thumbnailUrl: null })),
        };
      } catch (e) {
        return { ok: false, error: liveCatalogMessage(e, "Could not load sections.") };
      }
    },

    async listEpcDiagrams(_modelSlug, variantSlug, sectionSlug) {
      try {
        const rows: StaffDiagramRow[] = [];
        for (let offset = 0; ; ) {
          const page = await catalogGatewayGet<{ diagrams?: StaffDiagramRow[] }>(client, "staff-diagrams", {
            maker: NISSAN_MAKER_SLUG,
            variant_id: variantSlug,
            section_id: sectionSlug,
            limit: 200,
            offset,
          });
          const got = page.diagrams ?? [];
          rows.push(...got);
          if (got.length < 200) break;
          offset += got.length;
        }
        return {
          ok: true,
          data: rows.map((r) => ({ id: r.diagram_id, slug: r.diagram_id, title: r.title ?? r.name_en ?? "Diagram", imageUrl: null })),
        };
      } catch (e) {
        return { ok: false, error: liveCatalogMessage(e, "Could not load diagrams.") };
      }
    },

    async getEpcDiagram(_modelSlug, _variantSlug, _sectionSlug, ref) {
      if (!ref.id) return { ok: false, error: "This diagram is not in the live catalogue." };
      const [image, parts] = await Promise.allSettled([
        catalogGatewayGet<DiagramImageResponse>(client, "diagram-image", { maker: NISSAN_MAKER_SLUG, diagram_id: ref.id }),
        catalogGatewayGet<StaffPartsResponse>(client, "staff-diagram-parts", { maker: NISSAN_MAKER_SLUG, diagram_id: ref.id }),
      ]);
      if (image.status === "rejected" && parts.status === "rejected") {
        return { ok: false, error: liveCatalogMessage(parts.reason, "Could not load the diagram.") };
      }
      const seen = new Set<string>();
      const rawParts = parts.status === "fulfilled" ? parts.value.parts : [];
      // Callout boxes (fractions of the image) travel on the part rows; one part may have several.
      const hotspots = rawParts.flatMap((p) => {
        const oem = (p.display_oem_number ?? p.normalized_oem_number ?? "").trim();
        if (!oem || p.bbox_x == null || p.bbox_y == null || !p.bbox_width || !p.bbox_height) return [];
        return [{ oem, pnc: p.pnc_code ?? null, x: p.bbox_x, y: p.bbox_y, w: p.bbox_width, h: p.bbox_height }];
      });
      const partRows = rawParts.flatMap((p) => {
        const oem = (p.display_oem_number ?? p.normalized_oem_number ?? "").trim();
        const key = oem.toUpperCase().replace(/[^A-Z0-9]/g, "");
        if (!oem || seen.has(key)) return [];
        seen.add(key);
        return [{
          oemPartNumber: oem,
          pnc: p.pnc_code ?? null,
          ref: p.callout_ref ?? null,
          name: p.name ?? p.description ?? p.subcategory_name ?? oem,
          categoryName: p.category_name ?? null,
          subcategoryName: p.subcategory_name ?? null,
        }];
      });
      const diagram: EpcDiagram = {
        title: ref.title,
        imageUrl: image.status === "fulfilled" ? image.value.signed_url : null,
        width: null,
        height: null,
        hotspots,
        parts: partRows,
        notice:
          image.status === "rejected"
            ? liveCatalogMessage(image.reason, "Diagram image unavailable.")
            : parts.status === "rejected"
              ? liveCatalogMessage(parts.reason, "Parts list unavailable.")
              : null,
      };
      return { ok: true, data: diagram };
    },

    async getMyTill(deviceId) {
      const { data, error } = await rpc(client, "get_my_open_pos_till_session", { p_device_id: deviceId });
      if (error) return fail(error, "Could not check the till.");
      return { ok: true, data: data ? tillFromRow(data as Record<string, unknown>) : null };
    },

    async openTill(warehouseId, deviceId, openingFloat, currency) {
      const { data, error } = await rpc(client, "open_pos_till_session", {
        p_warehouse_id: warehouseId,
        p_device_id: deviceId,
        p_opening_float: roundMoney(openingFloat),
        p_currency: currency,
      });
      if (error || typeof data !== "string") return fail(error, "Could not open the till.");
      const mine = await gateway.getMyTill(deviceId);
      if (!mine.ok) return mine;
      return mine.data ? { ok: true, data: mine.data } : { ok: false, error: "The till opened but could not be read back." };
    },

    async attachCartToTill(cartId, sessionId) {
      const { error } = await rpc(client, "attach_pos_cart_till_session", { p_cart_id: cartId, p_session_id: sessionId });
      return error ? fail(error, "Could not ring this sale through the till.") : { ok: true, data: true };
    },

    async listReasons(action) {
      const { data, error } = await rpc(client, "list_pos_approval_reasons", { p_action: action });
      if (error) return fail(error, "Could not load reasons.");
      return {
        ok: true,
        data: ((data ?? []) as Array<{ code: string; label: string; requires_notes: boolean }>).map((r) => ({
          code: r.code,
          label: r.label,
          requiresNotes: Boolean(r.requires_notes),
        })),
      };
    },

    async recordCashMovement(sessionId, kind, amount, reasonCode, notes, manager) {
      const run = async (c: SupabaseClient): Promise<PosResult<true>> => {
        const { error } = await rpc(c, "record_pos_till_cash_movement", {
          p_session_id: sessionId,
          p_kind: kind,
          p_amount: roundMoney(amount),
          p_reason_code: reasonCode,
          p_notes: notes,
        });
        return error ? fail(error, "Cash movement refused.") : { ok: true, data: true };
      };
      return manager ? asManager(manager, run) : run(client);
    },

    async closeTill(sessionId, counts, varianceReasonCode, notes) {
      const { data, error } = await rpc(client, "submit_pos_till_denominated_close", {
        p_session_id: sessionId,
        p_denominations: counts.filter((c) => c.quantity > 0).map((c) => ({ denomination: c.denomination, quantity: c.quantity })),
        p_variance_reason_code: varianceReasonCode,
        p_notes: notes,
      });
      if (error || !data) return fail(error, "Could not close the till.");
      const r = data as Record<string, unknown>;
      return {
        ok: true,
        data: {
          sessionId: String(r.session_id ?? sessionId),
          expectedCash: num(r.expected_cash),
          countedCash: num(r.counted_cash),
          variance: num(r.variance),
          status: r.status === "variance_pending" ? "variance_pending" : "closed",
        },
      };
    },

    async approveTillVariance(sessionId, reasonCode, notes, manager) {
      return withManagerOrSelf(manager, async (m) => {
        const { error } = await rpc(m, "approve_pos_till_variance", { p_session_id: sessionId, p_reason_code: reasonCode, p_notes: notes });
        return error ? fail(error, "Variance approval refused.") : { ok: true, data: true };
      });
    },

    async listHandoverOperators() {
      const { data, error } = await rpc(client, "list_pos_handover_operators");
      if (error) return fail(error, "Could not load operators.");
      return {
        ok: true,
        data: ((data ?? []) as Array<{ user_id: string; employee_code: string | null; full_name: string | null; roles: string[] | null }>).map((r) => ({
          userId: r.user_id,
          employeeCode: r.employee_code ?? "",
          fullName: r.full_name ?? r.employee_code ?? "Staff",
          roles: r.roles ?? [],
        })),
      };
    },

    async handoverTill(sessionId, newOperatorUserId, notes, manager) {
      return withManagerOrSelf(manager, async (m) => {
        const { error } = await rpc(m, "handover_pos_till_session", {
          p_session_id: sessionId,
          p_new_operator_user_id: newOperatorUserId,
          p_notes: notes,
        });
        return error ? fail(error, "Handover refused.") : { ok: true, data: true };
      });
    },

    async approverStatus() {
      const { data, error } = await rpc(client, "get_my_pos_approver_status", {});
      if (error || !data) return { ok: true, data: { isApprover: false, source: null } };
      const r = data as Record<string, unknown>;
      return { ok: true, data: { isApprover: r.is_approver === true, source: (r.source as string | null) ?? null } };
    },

    async badgeApprove(payload, action, args, deviceId) {
      const { data, error } = await rpc(client, "pos_badge_approve", { p_badge: payload.trim(), p_action: action, p_args: args, p_device_id: deviceId });
      if (error || !data) return fail(error, "Badge approval failed.");
      const r = data as Record<string, unknown>;
      if (r.ok !== true) return { ok: false, error: operatorMessage(String(r.error ?? ""), "Badge approval refused.") };
      return { ok: true, data: { managerName: (r.manager_name as string | null) ?? null } };
    },

    async listManagerCandidates() {
      const { data, error } = await rpc(client, "list_approver_candidates", {});
      if (error) return fail(error, "Could not load staff.");
      const str = (v: unknown) => (v == null ? null : String(v));
      return {
        ok: true,
        data: ((data as Record<string, unknown>[] | null) ?? []).map((r) => ({
          holderType: r.holder_type === "user" ? ("user" as const) : ("employee" as const),
          employeeId: str(r.employee_id),
          userId: str(r.user_id),
          fullName: String(r.full_name ?? "Staff"),
          employeeCode: str(r.employee_code),
          grade: str(r.grade),
          roleTitle: str(r.role_title),
          department: str(r.department),
          hasLogin: r.has_login === true,
          isApprover: r.is_approver === true,
          source: str(r.source),
          assigned: r.assigned === true,
          activeBadges: num(r.active_badges),
        })),
      };
    },

    async setManagerAssignment(employeeId, assigned, notes) {
      const { error } = await rpc(client, "set_approver_assignment", { p_employee_id: employeeId, p_assigned: assigned, p_notes: notes });
      return error ? fail(error, "Could not change the approver assignment.") : { ok: true, data: true };
    },

    async issueBadge(holder, label, validDays) {
      const { data, error } = await rpc(client, "issue_approval_badge", {
        p_employee_id: "employeeId" in holder ? holder.employeeId : null,
        p_user_id: "userId" in holder ? holder.userId : null,
        p_label: label,
        p_valid_days: validDays,
      });
      if (error || !data) return fail(error, "Could not issue the badge.");
      const r = data as Record<string, unknown>;
      return {
        ok: true,
        data: {
          badgeId: String(r.badge_id),
          payload: String(r.payload),
          expiresAt: String(r.expires_at),
          fullName: String(r.full_name ?? ""),
          employeeCode: (r.employee_code as string | null) ?? null,
          title: (r.title as string | null) ?? null,
        },
      };
    },

    async revokeBadge(badgeId, reason) {
      const { error } = await rpc(client, "revoke_approval_badge", { p_badge_id: badgeId, p_reason: reason });
      return error ? fail(error, "Could not revoke the badge.") : { ok: true, data: true };
    },

    async listBadges() {
      const { data, error } = await rpc(client, "list_approval_badges", {});
      if (error) return fail(error, "Could not load badges.");
      return {
        ok: true,
        data: ((data as Record<string, unknown>[] | null) ?? []).map((r) => ({
          badgeId: String(r.badge_id),
          employeeId: (r.employee_id as string | null) ?? null,
          userId: (r.user_id as string | null) ?? null,
          fullName: String(r.full_name ?? ""),
          label: (r.label as string | null) ?? null,
          issuedAt: String(r.issued_at),
          expiresAt: String(r.expires_at),
          revokedAt: (r.revoked_at as string | null) ?? null,
          revokeReason: (r.revoke_reason as string | null) ?? null,
          lastUsedAt: (r.last_used_at as string | null) ?? null,
          useCount: num(r.use_count),
          status: r.status === "revoked" || r.status === "expired" ? r.status : "active",
        })),
      };
    },

    async approvalTrail(limit) {
      const { data, error } = await rpc(client, "list_approval_trail", { p_limit: limit });
      if (error) return fail(error, "Could not load the approval trail.");
      return {
        ok: true,
        data: ((data as Record<string, unknown>[] | null) ?? []).map((r) => ({
          at: String(r.at),
          method: String(r.method),
          outcome: String(r.outcome),
          action: String(r.action),
          managerName: (r.manager_name as string | null) ?? null,
          requestedByName: (r.requested_by_name as string | null) ?? null,
          reasonCode: (r.reason_code as string | null) ?? null,
          detail: (r.detail as string | null) ?? null,
          deviceId: (r.device_id as string | null) ?? null,
        })),
      };
    },

    async listTillSessions(status) {
      const { data, error } = await rpc(client, "list_pos_till_sessions", { p_status: status, p_limit: 30 });
      if (error) return fail(error, "Could not load till sessions.");
      return { ok: true, data: ((data ?? []) as Record<string, unknown>[]).map(tillFromRow) };
    },

    async operatorLabel() {
      const { data } = await client.auth.getUser();
      const meta = (data.user?.user_metadata ?? {}) as Record<string, unknown>;
      const name = typeof meta.full_name === "string" ? meta.full_name : null;
      return name ?? data.user?.email ?? "Operator";
    },

    async reauthenticate(password) {
      const { data } = await client.auth.getUser();
      const email = data.user?.email;
      if (!email) return { ok: false, error: "No signed-in staff email to confirm." };
      const { error } = await client.auth.signInWithPassword({ email, password });
      return error ? { ok: false, error: "Password not accepted." } : { ok: true, data: true };
    },
  };
  return gateway;
}

export type { VehicleModel, VehicleVariant };
