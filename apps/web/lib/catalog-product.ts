import type { SupabaseClient } from "@gtr/supabase-client";
import type { StockState } from "@/lib/shop-demo";
import { zigExchangeRate } from "@/lib/customer-storefront";

export type CatalogFitmentLine = {
  chassis_code: string | null;
  engine_code: string | null;
  pnc_code: string | null;
  category_name: string | null;
  subcategory_name: string | null;
  model_variant: string | null;
  production_year: number | null;
};

export type CatalogProduct = {
  id: string;
  oem: string;
  name: string;
  brand: string;
  category: string | null;
  usd: number | null;
  zig: number | null;
  stock: StockState;
  coreCharge: number;
  replaces: string[];
  specs: string[];
  fitments: CatalogFitmentLine[];
  alternatives: { oem: string; name: string }[];
};

export type CatalogListItem = {
  oem: string;
  name: string;
  stock: StockState;
  usd: number | null;
  zig: number | null;
  category: string | null;
};

function decodeOem(raw: string): string {
  try {
    return decodeURIComponent(raw).trim();
  } catch {
    return raw.trim();
  }
}

function stockStateFromQty(
  qty: number,
  reorderPoint: number | null,
): StockState {
  if (qty <= 0) return "backorder";
  if (reorderPoint != null && qty <= reorderPoint) return "low";
  return "in_stock";
}

function fitmentLabel(line: CatalogFitmentLine): string {
  const bits = [
    line.model_variant,
    line.chassis_code,
    line.engine_code,
    line.production_year != null ? String(line.production_year) : null,
  ].filter(Boolean);
  return bits.join(" · ");
}

export { fitmentLabel };

/**
 * Load a single PDP row from stock_items + fitment / OE / price tables.
 * Requires an authenticated Supabase session (RLS).
 */
export async function loadCatalogProduct(
  client: SupabaseClient,
  oemParam: string,
): Promise<
  | { ok: true; data: CatalogProduct }
  | { ok: false; error: string; missing?: boolean }
> {
  const oem = decodeOem(oemParam);
  if (!oem) {
    return { ok: false, error: "Missing OEM.", missing: true };
  }

  const { data: item, error: itemErr } = await client
    .from("stock_items")
    .select("id, oem_part_number, description, reorder_point")
    .ilike("oem_part_number", oem)
    .maybeSingle();

  if (itemErr) return { ok: false, error: itemErr.message };
  if (!item) {
    // Fitment-only OEM (catalog hit without inventory row) — still show PDP shell.
    const fitOnly = await loadFitmentOnlyProduct(client, oem);
    if (fitOnly.ok) return fitOnly;
    return { ok: false, error: "Part not found.", missing: true };
  }

  const [levels, fitments, xrefs, price] = await Promise.all([
    client
      .from("stock_levels")
      .select("quantity, warehouse_id, warehouses!inner(is_quarantine, is_active)")
      .eq("stock_item_id", item.id),
    loadFitmentLines(client, item.oem_part_number),
    client
      .from("oe_cross_refs")
      .select("oe_number, brand")
      .eq("oem_part_number", item.oem_part_number),
    loadDefaultPrice(client, item.id),
  ]);

  if (levels.error) return { ok: false, error: levels.error.message };
  if (!fitments.ok) return fitments;
  if (xrefs.error) return { ok: false, error: xrefs.error.message };
  if (!price.ok) return price;

  const saleableQty = (levels.data ?? []).reduce((sum, row) => {
    const wh = row.warehouses as
      | { is_quarantine: boolean; is_active: boolean }
      | { is_quarantine: boolean; is_active: boolean }[]
      | null;
    const w = Array.isArray(wh) ? wh[0] : wh;
    if (!w || w.is_quarantine || !w.is_active) return sum;
    return sum + Number(row.quantity ?? 0);
  }, 0);

  const primaryFit = fitments.data[0];
  const category =
    primaryFit?.category_name ??
    primaryFit?.subcategory_name ??
    null;

  const { data: fitRows } = await client
    .from("part_fitment")
    .select("superseded_by")
    .eq("oem_part_number", item.oem_part_number);

  const superseded = (fitRows ?? [])
    .map((r) => r.superseded_by)
    .filter((v): v is string => Boolean(v));

  const replaces = [
    ...new Set([
      ...(xrefs.data ?? []).map((x) => x.oe_number),
      ...superseded,
    ]),
  ].filter((n) => n.toLowerCase() !== item.oem_part_number.toLowerCase());

  const specs: string[] = [];
  if (primaryFit?.pnc_code) specs.push(`PNC ${primaryFit.pnc_code}`);
  if (primaryFit?.chassis_code) specs.push(`Chassis ${primaryFit.chassis_code}`);
  if (primaryFit?.engine_code) specs.push(`Engine ${primaryFit.engine_code}`);

  const alts = await loadAlternatives(
    client,
    item.oem_part_number,
    primaryFit?.pnc_code ?? null,
  );

  const usd = price.data.unitPrice;
  const rate = zigExchangeRate();
  const zig =
    usd != null && price.data.currency === "USD"
      ? usd * rate
      : price.data.currency === "ZIG"
        ? price.data.unitPrice
        : null;

  return {
    ok: true,
    data: {
      id: item.id,
      oem: item.oem_part_number,
      name: item.description?.trim() || item.oem_part_number,
      brand: "Nissan OE",
      category,
      usd,
      zig,
      stock: stockStateFromQty(saleableQty, item.reorder_point),
      coreCharge: price.data.coreCharge,
      replaces,
      specs,
      fitments: fitments.data,
      alternatives: alts,
    },
  };
}

async function loadFitmentOnlyProduct(
  client: SupabaseClient,
  oem: string,
): Promise<
  | { ok: true; data: CatalogProduct }
  | { ok: false; error: string; missing?: boolean }
> {
  const fitments = await loadFitmentLines(client, oem);
  if (!fitments.ok) return fitments;
  if (fitments.data.length === 0) {
    return { ok: false, error: "Part not found.", missing: true };
  }

  const { data: xrefs } = await client
    .from("oe_cross_refs")
    .select("oe_number")
    .ilike("oem_part_number", oem);

  const primary = fitments.data[0];
  const specs: string[] = [];
  if (primary.pnc_code) specs.push(`PNC ${primary.pnc_code}`);
  if (primary.chassis_code) specs.push(`Chassis ${primary.chassis_code}`);
  if (primary.engine_code) specs.push(`Engine ${primary.engine_code}`);

  const alts = await loadAlternatives(client, oem, primary.pnc_code);

  return {
    ok: true,
    data: {
      id: oem,
      oem,
      name: oem,
      brand: "Nissan OE",
      category: primary.category_name,
      usd: null,
      zig: null,
      stock: "counter_only",
      coreCharge: 0,
      replaces: (xrefs ?? []).map((x) => x.oe_number),
      specs,
      fitments: fitments.data,
      alternatives: alts,
    },
  };
}

async function loadFitmentLines(
  client: SupabaseClient,
  oem: string,
): Promise<
  | { ok: true; data: CatalogFitmentLine[] }
  | { ok: false; error: string }
> {
  const { data, error } = await client
    .from("part_fitment")
    .select(
      "chassis_code, engine_code, pnc_code, pnc_categories ( category_name, subcategory_name )",
    )
    .ilike("oem_part_number", oem)
    .limit(40);

  if (error) return { ok: false, error: error.message };

  const chassisCodes = [
    ...new Set(
      (data ?? [])
        .map((r) => r.chassis_code)
        .filter((c): c is string => Boolean(c)),
    ),
  ];

  const vehiclesByChassis = new Map<
    string,
    { model_variant: string; production_year: number | null; engine_code: string }
  >();

  if (chassisCodes.length) {
    const { data: vehicles } = await client
      .from("vehicle_master")
      .select("chassis_code, model_variant, production_year, engine_code")
      .in("chassis_code", chassisCodes)
      .limit(80);
    for (const v of vehicles ?? []) {
      const key = `${v.chassis_code}|${v.engine_code}`;
      if (!vehiclesByChassis.has(key)) {
        vehiclesByChassis.set(key, {
          model_variant: v.model_variant,
          production_year: v.production_year,
          engine_code: v.engine_code,
        });
      }
    }
  }

  const lines: CatalogFitmentLine[] = (data ?? []).map((row) => {
    const pnc = row.pnc_categories as
      | { category_name: string; subcategory_name: string | null }
      | { category_name: string; subcategory_name: string | null }[]
      | null;
    const p = Array.isArray(pnc) ? pnc[0] : pnc;
    const key = `${row.chassis_code ?? ""}|${row.engine_code ?? ""}`;
    const vehicle =
      vehiclesByChassis.get(key) ??
      (row.chassis_code
        ? [...vehiclesByChassis.entries()].find(([k]) =>
            k.startsWith(`${row.chassis_code}|`),
          )?.[1]
        : undefined);

    return {
      chassis_code: row.chassis_code,
      engine_code: row.engine_code,
      pnc_code: row.pnc_code,
      category_name: p?.category_name ?? null,
      subcategory_name: p?.subcategory_name ?? null,
      model_variant: vehicle?.model_variant ?? null,
      production_year: vehicle?.production_year ?? null,
    };
  });

  return { ok: true, data: lines };
}

async function loadDefaultPrice(
  client: SupabaseClient,
  stockItemId: string,
): Promise<
  | {
      ok: true;
      data: {
        unitPrice: number | null;
        coreCharge: number;
        currency: "USD" | "ZIG" | null;
      };
    }
  | { ok: false; error: string }
> {
  const { data: list, error: listErr } = await client
    .from("price_lists")
    .select("id, currency")
    .eq("is_default", true)
    .eq("is_active", true)
    .limit(1)
    .maybeSingle();

  if (listErr) return { ok: false, error: listErr.message };
  if (!list) {
    return {
      ok: true,
      data: { unitPrice: null, coreCharge: 0, currency: null },
    };
  }

  const { data: row, error } = await client
    .from("price_list_items")
    .select("unit_price, core_charge")
    .eq("price_list_id", list.id)
    .eq("stock_item_id", stockItemId)
    .maybeSingle();

  if (error) return { ok: false, error: error.message };

  return {
    ok: true,
    data: {
      unitPrice: row ? Number(row.unit_price) : null,
      coreCharge: row ? Number(row.core_charge ?? 0) : 0,
      currency: list.currency,
    },
  };
}

async function loadAlternatives(
  client: SupabaseClient,
  oem: string,
  pnc: string | null,
): Promise<{ oem: string; name: string }[]> {
  if (!pnc) return [];

  const { data } = await client
    .from("part_fitment")
    .select("oem_part_number")
    .eq("pnc_code", pnc)
    .neq("oem_part_number", oem)
    .limit(12);

  const oems = [
    ...new Set((data ?? []).map((r) => r.oem_part_number)),
  ].slice(0, 3);

  if (!oems.length) return [];

  const { data: items } = await client
    .from("stock_items")
    .select("oem_part_number, description")
    .in("oem_part_number", oems);

  const byOem = new Map(
    (items ?? []).map((i) => [i.oem_part_number, i.description]),
  );

  return oems.map((o) => ({
    oem: o,
    name: byOem.get(o)?.trim() || o,
  }));
}

/**
 * Browse list for /catalog — stock_items with optional category facet via PNC name.
 */
export async function listCatalogProducts(
  client: SupabaseClient,
  opts: { category?: string | null; limit?: number } = {},
): Promise<
  | { ok: true; data: CatalogListItem[]; categories: string[] }
  | { ok: false; error: string }
> {
  const limit = opts.limit ?? 50;
  const cat = opts.category?.trim().toLowerCase() || null;

  const { data: categoriesRows } = await client
    .from("pnc_categories")
    .select("category_name")
    .order("category_name")
    .limit(100);

  const categories = [
    ...new Set(
      (categoriesRows ?? [])
        .map((r) => r.category_name.trim())
        .filter(Boolean),
    ),
  ].slice(0, 24);

  let oemFilter: string[] | null = null;
  if (cat) {
    const { data: pncs } = await client
      .from("pnc_categories")
      .select("pnc_code")
      .ilike("category_name", cat);

    const codes = (pncs ?? []).map((p) => p.pnc_code);
    if (codes.length) {
      const { data: fits } = await client
        .from("part_fitment")
        .select("oem_part_number")
        .in("pnc_code", codes)
        .limit(200);
      oemFilter = [
        ...new Set((fits ?? []).map((f) => f.oem_part_number)),
      ];
      if (!oemFilter.length) {
        return { ok: true, data: [], categories };
      }
    } else {
      return { ok: true, data: [], categories };
    }
  }

  let query = client
    .from("stock_items")
    .select("id, oem_part_number, description, reorder_point")
    .order("oem_part_number")
    .limit(limit);

  if (oemFilter) {
    query = query.in("oem_part_number", oemFilter);
  }

  const { data: items, error } = await query;
  if (error) return { ok: false, error: error.message };
  if (!items?.length) return { ok: true, data: [], categories };

  const ids = items.map((i) => i.id);
  const oems = items.map((i) => i.oem_part_number);

  const [levels, prices, fitCats] = await Promise.all([
    client
      .from("stock_levels")
      .select(
        "stock_item_id, quantity, warehouses!inner(is_quarantine, is_active)",
      )
      .in("stock_item_id", ids),
    loadPricesForItems(client, ids),
    client
      .from("part_fitment")
      .select("oem_part_number, pnc_categories ( category_name )")
      .in("oem_part_number", oems)
      .limit(200),
  ]);

  if (levels.error) return { ok: false, error: levels.error.message };
  if (!prices.ok) return prices;

  const qtyByItem = new Map<string, number>();
  for (const row of levels.data ?? []) {
    const wh = row.warehouses as
      | { is_quarantine: boolean; is_active: boolean }
      | { is_quarantine: boolean; is_active: boolean }[]
      | null;
    const w = Array.isArray(wh) ? wh[0] : wh;
    if (!w || w.is_quarantine || !w.is_active) continue;
    qtyByItem.set(
      row.stock_item_id,
      (qtyByItem.get(row.stock_item_id) ?? 0) + Number(row.quantity ?? 0),
    );
  }

  const catByOem = new Map<string, string>();
  for (const row of fitCats.data ?? []) {
    if (catByOem.has(row.oem_part_number)) continue;
    const pnc = row.pnc_categories as
      | { category_name: string }
      | { category_name: string }[]
      | null;
    const p = Array.isArray(pnc) ? pnc[0] : pnc;
    if (p?.category_name) catByOem.set(row.oem_part_number, p.category_name);
  }

  const rate = zigExchangeRate();
  const list: CatalogListItem[] = items.map((item) => {
    const price = prices.data.get(item.id);
    const usd =
      price?.currency === "USD"
        ? price.unitPrice
        : price?.currency === "ZIG"
          ? null
          : (price?.unitPrice ?? null);
    const zig =
      price?.currency === "ZIG"
        ? price.unitPrice
        : usd != null
          ? usd * rate
          : null;

    return {
      oem: item.oem_part_number,
      name: item.description?.trim() || item.oem_part_number,
      stock: stockStateFromQty(
        qtyByItem.get(item.id) ?? 0,
        item.reorder_point,
      ),
      usd,
      zig,
      category: catByOem.get(item.oem_part_number) ?? null,
    };
  });

  return { ok: true, data: list, categories };
}

async function loadPricesForItems(
  client: SupabaseClient,
  stockItemIds: string[],
): Promise<
  | {
      ok: true;
      data: Map<
        string,
        { unitPrice: number; coreCharge: number; currency: "USD" | "ZIG" }
      >;
    }
  | { ok: false; error: string }
> {
  const map = new Map<
    string,
    { unitPrice: number; coreCharge: number; currency: "USD" | "ZIG" }
  >();
  if (!stockItemIds.length) return { ok: true, data: map };

  const { data: list, error: listErr } = await client
    .from("price_lists")
    .select("id, currency")
    .eq("is_default", true)
    .eq("is_active", true)
    .limit(1)
    .maybeSingle();

  if (listErr) return { ok: false, error: listErr.message };
  if (!list) return { ok: true, data: map };

  const { data: rows, error } = await client
    .from("price_list_items")
    .select("stock_item_id, unit_price, core_charge")
    .eq("price_list_id", list.id)
    .in("stock_item_id", stockItemIds);

  if (error) return { ok: false, error: error.message };

  for (const row of rows ?? []) {
    map.set(row.stock_item_id, {
      unitPrice: Number(row.unit_price),
      coreCharge: Number(row.core_charge ?? 0),
      currency: list.currency,
    });
  }

  return { ok: true, data: map };
}
