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
import { searchPosCatalog } from "@/lib/staff-pos";

const NISSAN_MAKER_SLUG = "nissan";
const PRODUCT_IMAGE_BUCKET = "product-images";
const DIAGRAM_BUCKET = "catalog-diagrams";

// Generated database types predate the September POS RPCs; call those through a narrow untyped shim.
type RpcResult = { data: unknown; error: { message: string } | null };
function rpc(client: SupabaseClient, fn: string, args?: Record<string, unknown>): Promise<RpcResult> {
  const call = client.rpc as unknown as (f: string, a?: Record<string, unknown>) => Promise<RpcResult>;
  return call.call(client, fn, args);
}

function fail<T>(error: { message: string } | null | undefined, fallback: string): PosResult<T> {
  return { ok: false, error: error?.message ?? fallback };
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

  function diagramUrl(path: string | null | undefined): string | null {
    const p = path?.trim();
    if (!p) return null;
    if (/^https?:\/\//i.test(p)) return p;
    return projectUrl ? `${projectUrl}/storage/v1/object/public/${DIAGRAM_BUCKET}/${p.replace(/^\//, "")}` : null;
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
      const { data, error } = await rpc(client, "list_catalog_models", { p_maker_slug: NISSAN_MAKER_SLUG });
      if (error) return fail(error, "Could not load models.");
      const rows = (data ?? []) as Array<{ slug: string; display_name: string; year_start: number | null; year_end: number | null }>;
      return {
        ok: true,
        data: rows.map((r) => ({ slug: r.slug, name: r.display_name, yearStart: r.year_start, yearEnd: r.year_end })),
      };
    },

    async listVariants(modelSlug) {
      const { data, error } = await rpc(client, "list_catalog_variants", {
        p_maker_slug: NISSAN_MAKER_SLUG,
        p_model_slug: modelSlug,
      });
      if (error) return fail(error, "Could not load generations.");
      const rows = (data ?? []) as Array<{ slug: string; chassis_code: string; engine_code: string | null; year_label: string | null }>;
      return {
        ok: true,
        data: rows.map((r) => ({ slug: r.slug, chassisCode: r.chassis_code, engineCode: r.engine_code, yearLabel: r.year_label })),
      };
    },

    async searchParts(query, vehicle) {
      const q = query.trim();
      if (vehicle) {
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
      const res = await searchPosCatalog(client, "part", q);
      if (!res.ok) return res;
      return { ok: true, data: await partsFromHits(res.data) };
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

    async listEpcSections(modelSlug, variantSlug) {
      const { data, error } = await rpc(client, "list_catalog_sections", {
        p_maker_slug: NISSAN_MAKER_SLUG,
        p_model_slug: modelSlug,
        p_variant_slug: variantSlug,
      });
      if (error) return fail(error, "Could not load sections.");
      return {
        ok: true,
        data: ((data ?? []) as Array<{ slug: string; name: string; thumbnail_url: string | null }>).map((r) => ({
          slug: r.slug,
          name: r.name,
          thumbnailUrl: r.thumbnail_url,
        })),
      };
    },

    async listEpcDiagrams(modelSlug, variantSlug, sectionSlug) {
      const { data, error } = await rpc(client, "list_catalog_diagrams", {
        p_maker_slug: NISSAN_MAKER_SLUG,
        p_model_slug: modelSlug,
        p_variant_slug: variantSlug,
        p_section_slug: sectionSlug,
      });
      if (error) return fail(error, "Could not load diagrams.");
      return {
        ok: true,
        data: ((data ?? []) as Array<{ slug: string; title: string; storage_path: string | null; image_url: string | null }>).map((r) => ({
          slug: r.slug,
          title: r.title,
          imageUrl: diagramUrl(r.image_url ?? r.storage_path),
        })),
      };
    },

    async getEpcDiagram(modelSlug, variantSlug, sectionSlug, diagramSlug) {
      const { data, error } = await rpc(client, "get_catalog_diagram_by_slug", {
        p_maker_slug: NISSAN_MAKER_SLUG,
        p_model_slug: modelSlug,
        p_variant_slug: variantSlug,
        p_section_slug: sectionSlug,
        p_diagram_slug: diagramSlug,
      });
      if (error) return fail(error, "Could not load the diagram.");
      const d = (data ?? {}) as {
        diagram: { title: string; storage_path: string | null; image_url: string | null; width: number | null; height: number | null } | null;
        hotspots: Array<{ oem: string; pnc_code: string | null; bbox_x: number; bbox_y: number; bbox_width: number; bbox_height: number }>;
        parts: Array<{ oem_part_number: string; pnc_code: string | null; category_name: string | null; subcategory_name: string | null; stock_description: string | null }>;
      };
      if (!d.diagram) return { ok: false, error: "Diagram not found." };
      const diagram: EpcDiagram = {
        title: d.diagram.title,
        imageUrl: diagramUrl(d.diagram.image_url ?? d.diagram.storage_path),
        width: d.diagram.width,
        height: d.diagram.height,
        hotspots: (d.hotspots ?? []).map((h) => ({ oem: h.oem, pnc: h.pnc_code, x: num(h.bbox_x), y: num(h.bbox_y), w: num(h.bbox_width), h: num(h.bbox_height) })),
        parts: (d.parts ?? []).map((p) => ({
          oemPartNumber: p.oem_part_number,
          pnc: p.pnc_code,
          name: p.stock_description ?? p.subcategory_name ?? p.oem_part_number,
          categoryName: p.category_name,
          subcategoryName: p.subcategory_name,
        })),
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
