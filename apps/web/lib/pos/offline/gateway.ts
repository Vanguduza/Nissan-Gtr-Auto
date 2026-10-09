import type { SupabaseClient } from "@gtr/supabase-client";
import { catalogGatewayGet } from "@/lib/catalog-live-gateway";
import type { PosGateway } from "@/lib/pos/gateway";
import type { PosResult } from "@/lib/pos/types";
import type { OfflineCatalog, OfflineSources, StockSnapshot } from "./catalog";
import type { BundleManifest, StockItem } from "./format";

const NISSAN_MAKER_SLUG = "nissan";

function isOffline(): boolean {
  return typeof navigator !== "undefined" && navigator.onLine === false;
}

/**
 * The POS catalogue calls with the downloaded catalogue behind them: with no connection they are
 * answered on the device; online they go to the server and fall back to the device copy if the
 * server cannot be reached. Everything else (carts, payments, customers) stays online-only.
 */
export function withOfflineCatalog(online: PosGateway, offline: OfflineCatalog): PosGateway {
  async function pick<T>(live: () => Promise<PosResult<T>>, local: () => Promise<PosResult<T>>): Promise<PosResult<T>> {
    if (offline.ready && isOffline()) return local();
    let res: PosResult<T>;
    try {
      res = await live();
    } catch (e) {
      res = { ok: false, error: e instanceof Error ? e.message : "Request failed." };
    }
    if (!res.ok && offline.ready) {
      const fallback = await local();
      if (fallback.ok) return fallback;
    }
    return res;
  }
  return {
    ...online,
    listModels: () => pick(() => online.listModels(), () => offline.listModels()),
    listVariants: (modelSlug) => pick(() => online.listVariants(modelSlug), () => offline.listVariants(modelSlug)),
    listEpcVariants: (modelSlug) => pick(() => online.listEpcVariants(modelSlug), () => offline.listVariants(modelSlug)),
    searchParts: (query, vehicle) => pick(() => online.searchParts(query, vehicle), () => offline.searchParts(query, vehicle)),
    listEpcSections: (modelSlug, variantSlug) =>
      pick(() => online.listEpcSections(modelSlug, variantSlug), () => offline.listSections(variantSlug)),
    listEpcDiagrams: (modelSlug, variantSlug, sectionSlug) =>
      pick(() => online.listEpcDiagrams(modelSlug, variantSlug, sectionSlug), () => offline.listDiagrams(variantSlug, sectionSlug)),
    getEpcDiagram: (modelSlug, variantSlug, sectionSlug, diagram) =>
      pick(() => online.getEpcDiagram(modelSlug, variantSlug, sectionSlug, diagram), () => offline.getDiagram(diagram)),
  };
}

/** The signed bundle manifest (`catalog-live-r2` `offline-bundle`) and the till's stock snapshot. */
export function supabaseOfflineSources(client: SupabaseClient): OfflineSources {
  const call = client.rpc as unknown as (f: string, a?: Record<string, unknown>) => Promise<{ data: unknown; error: { message: string } | null }>;
  return {
    fetchManifest: () => catalogGatewayGet<BundleManifest>(client, "offline-bundle", { maker: NISSAN_MAKER_SLUG }),
    async fetchStock(): Promise<StockSnapshot> {
      const { data: warehouses, error: whError } = await client
        .from("warehouses")
        .select("id, code")
        .eq("is_active", true)
        .eq("is_quarantine", false)
        .order("code");
      if (whError) throw new Error("Could not load the shop's warehouses.");
      const rows = (warehouses ?? []) as Array<{ id: string; code: string }>;
      // Same selling warehouse as the counter tablet: MAIN, else the first saleable one.
      const warehouse = rows.find((w) => w.code.toUpperCase() === "MAIN") ?? rows[0];
      if (!warehouse) throw new Error("No active warehouse is set up for this shop.");
      const { data, error } = await call.call(client, "pull_pos_offline_snapshot", { p_warehouse_id: warehouse.id });
      if (error) throw new Error(error.message || "Could not pull stock and prices.");
      const snap = (data ?? {}) as { warehouse_id?: string; pulled_at?: string; items?: StockItem[] };
      return {
        warehouseId: snap.warehouse_id ?? warehouse.id,
        pulledAt: snap.pulled_at ?? new Date().toISOString(),
        items: (snap.items ?? []).map((i) => ({ ...i, unit_price: Number(i.unit_price), core_charge: Number(i.core_charge), saleable_qty: Number(i.saleable_qty) })),
      };
    },
  };
}
