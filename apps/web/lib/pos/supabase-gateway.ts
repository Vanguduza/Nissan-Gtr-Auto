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
  VehicleModel,
  VehicleVariant,
} from "@/lib/pos/types";
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
      await managerClient.auth.signOut();
    }
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

    async voidCart(cartId, manager, notes) {
      return asManager(manager, async (m) => {
        const { error } = await rpc(m, "void_pos_cart", { p_cart_id: cartId, p_notes: notes });
        return error ? fail(error, "Could not void the sale.") : { ok: true, data: true };
      });
    },

    async applyDiscount(cartId, percent, manager, notes) {
      const res = await asManager(manager, async (m) => {
        const { error } = await rpc(m, "apply_pos_cart_discount", {
          p_cart_id: cartId,
          p_discount_percent: percent,
          p_notes: notes,
        });
        return error ? fail<true>(error, "Discount refused.") : { ok: true, data: true as const };
      });
      return res.ok ? readCart(cartId) : res;
    },

    async overrideLinePrice(cartId, lineId, unitPrice, manager, notes) {
      const res = await asManager(manager, async (m) => {
        const { error } = await rpc(m, "apply_pos_line_price_override", {
          p_line_id: lineId,
          p_unit_price: unitPrice,
          p_notes: notes,
        });
        return error ? fail<true>(error, "Price override refused.") : { ok: true, data: true as const };
      });
      return res.ok ? readCart(cartId) : res;
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

    async checkout(cartId, tenders, contacts) {
      const { data, error } = await rpc(client, "checkout_pos_cart_with_tenders", {
        p_cart_id: cartId,
        p_tenders: tenders.map((t) => ({ tender: t.tender satisfies Tender, amount: roundMoney(t.amount) })),
        p_receipt_email: contacts.email,
        p_receipt_whatsapp_e164: contacts.whatsappE164,
        p_receipt_phone_e164: contacts.phoneE164,
      });
      if (error || typeof data !== "string") return fail(error, "Checkout failed.");
      return { ok: true, data };
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

    async requestEcocash(invoiceId, msisdn, amount, currency, customerId) {
      const { data, error } = await rpc(client, "create_ecocash_intent", {
        p_external_ref: `POS-${invoiceId}-${Date.now()}`,
        p_payer_msisdn: msisdn,
        p_amount: roundMoney(amount),
        p_currency: currency,
        p_payer_mode: "pos_entered",
        p_channel: "web",
        p_customer_id: customerId,
        p_sales_invoice_id: invoiceId,
      });
      if (error || typeof data !== "string") return fail(error, "EcoCash request failed.");
      return { ok: true, data };
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

    async refundInvoice(invoiceId, manager, notes) {
      return asManager(manager, async (m) => {
        const { data, error } = await rpc(m, "post_pos_refund", { p_invoice_id: invoiceId, p_notes: notes });
        if (error || typeof data !== "string") return fail(error, "Refund refused.");
        return { ok: true, data };
      });
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
      const partRows = (parts.status === "fulfilled" ? parts.value.parts : []).flatMap((p) => {
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
        // R2 part shards carry no callout boxes; rows are matched to the artwork by PNC.
        hotspots: [],
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
