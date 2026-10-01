import type { SupabaseClient } from "@gtr/supabase-client";
import type { PosGateway } from "@/lib/pos/gateway";
import { roundMoney } from "@/lib/pos/money";
import type {
  CartLine,
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
    const vehicle =
      typeof c.vehicle_chassis_code === "string" && c.vehicle_chassis_code
        ? {
            modelSlug: String(c.vehicle_model_slug ?? ""),
            modelName: String(c.vehicle_model_name ?? ""),
            generation: String(c.vehicle_generation ?? c.vehicle_chassis_code),
            chassisCode: String(c.vehicle_chassis_code),
            engineCode: String(c.vehicle_engine_code ?? ""),
          }
        : null;
    return {
      ok: true,
      data: {
        id: cartId,
        currency: asCurrency(c.currency),
        lines,
        customerName: typeof c.customer_display_name === "string" ? c.customer_display_name : null,
        vehicle,
      },
    };
  }

  return {
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

    async openCart(currency) {
      const { data: wh, error: whErr } = await client
        .from("warehouses")
        .select("id, code")
        .eq("is_active", true)
        .eq("is_quarantine", false)
        .order("code")
        .limit(1)
        .maybeSingle();
      if (whErr || !wh) return fail(whErr, "No saleable warehouse is configured.");
      const { data, error } = await rpc(client, "create_pos_cart", {
        p_warehouse_id: (wh as { id: string }).id,
        p_currency: currency,
        p_fulfillment_mode: "immediate",
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
      if (!(qty > 0)) return this.removeLine(cartId, lineId);
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

    async voidCart(cartId) {
      const { error } = await rpc(client, "void_pos_cart", { p_cart_id: cartId });
      return error ? fail(error, "Could not clear the sale.") : { ok: true, data: true };
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
}

export type { VehicleModel, VehicleVariant };
