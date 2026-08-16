import type { SupabaseClient } from "@gtr/supabase-client";
import {
  categoryMatchesFilter,
  effectiveCategoryFilter,
  shopCategoryFacetOptions,
  stripEpcVehicleSuffix,
  type ShopFacetOption,
} from "@gtr/shared";
import type { StockState } from "@/lib/shop-demo";
import { zigExchangeRate } from "@/lib/customer-storefront";
import {
  loadOemCatalogDiagram,
  type CatalogDiagram,
} from "@/lib/catalog-diagram";

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
  /** List price before discount (USD) when a shop discount applies. */
  listUsd?: number | null;
  discount?: {
    kind: "none" | "percent" | "amount";
    value: number;
    description: string | null;
  } | null;
  /** Staff product photos (public product-images URLs), primary first. */
  productImages?: string[];
  stock: StockState;
  coreCharge: number;
  replaces: string[];
  specs: string[];
  fitments: CatalogFitmentLine[];
  alternatives: { oem: string; name: string }[];
  /** Storage diagram when `part_fitment.diagram_path` is set. */
  diagram: CatalogDiagram | null;
};

export type CatalogListItem = {
  oem: string;
  name: string;
  stock: StockState;
  usd: number | null;
  zig: number | null;
  category: string | null;
  /** Saleable qty across active non-quarantine warehouses (for movers rail). */
  qty?: number;
  createdAt?: string | null;
  discountDescription?: string | null;
};

export type CatalogSort =
  | "oem"
  | "newest"
  | "price_asc"
  | "price_desc"
  | "movers"
  | "name";

export type CatalogListOpts = {
  category?: string | null;
  /** Leaf under a merchandising parent (`/shop?cat=suspension&sub=ball-joints`). */
  subcategory?: string | null;
  limit?: number;
  sort?: CatalogSort;
  /** Inclusive USD bounds — applied client-side after price join. */
  minUsd?: number | null;
  maxUsd?: number | null;
};

/** Strip PartSouq / Megazip vehicle noise from EPC assembly labels for display. */
export function normalizeDisplayCategory(
  name: string | null | undefined,
): string | null {
  if (!name?.trim()) return null;
  let cleaned = stripEpcVehicleSuffix(name.trim());
  for (let pass = 0; pass < 4; pass += 1) {
    const slash = cleaned.match(/^([A-Z0-9/+\-]{2,24})\s+(.+)$/i);
    if (slash && (slash[1].includes("/") || slash[1].includes("+"))) {
      cleaned = slash[2].trim();
      continue;
    }
    const body = cleaned.match(
      /^(?:COUPE|SEDAN|WAGON|HATCHBACK|HATCH|VAN|TRUCK|PICKUP|BUS)\s+(.+)$/i,
    );
    if (body) {
      cleaned = body[1].trim();
      continue;
    }
    const model = cleaned.match(
      /^(?:MICRA|QASHQAI\+?\d*|JUKE|NAVARA|X-TRAIL|PULSAR|PATROL|ALTIMA|SENTRA|MAXIMA|LEAF|370Z|350Z|GT-R|SKYLINE|DATSUN)\s+(.+)$/i,
    );
    if (model) {
      cleaned = model[1].trim();
      continue;
    }
    break;
  }
  return cleaned || name.trim();
}

/** Prefer canonical assembly group when an OEM spans multiple fitment categories. */
export function pickDisplayCategory(
  names: (string | null | undefined)[],
): string | null {
  const normalized = names
    .map((n) => normalizeDisplayCategory(n))
    .filter((n): n is string => Boolean(n));
  if (!normalized.length) return null;
  const counts = new Map<string, number>();
  for (const name of normalized) {
    counts.set(name, (counts.get(name) ?? 0) + 1);
  }
  return [...counts.entries()].sort((a, b) => {
    if (b[1] !== a[1]) return b[1] - a[1];
    return b[0].length - a[0].length;
  })[0][0];
}

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

  const [levels, fitments, xrefs, price, diagram, merch] = await Promise.all([
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
    loadOemCatalogDiagram(client, item.oem_part_number),
    loadShopMerchForItem(client, item.id),
  ]);

  if (levels.error) return { ok: false, error: levels.error.message };
  if (!fitments.ok) return fitments;
  if (xrefs.error) return { ok: false, error: xrefs.error.message };
  if (!price.ok) return price;
  if (!diagram.ok) return { ok: false, error: diagram.error };
  // Merch tables may be missing until migration applied — soft-fail.
  const merchData = merch.ok
    ? merch.data
    : { discount: null as ShopDiscount | null, imageUrls: [] as string[] };

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
  const category = pickDisplayCategory(
    fitments.data.flatMap((f) => [f.category_name, f.subcategory_name]),
  );

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

  const listUsd = price.data.unitPrice;
  const priced = applyShopDiscount(listUsd, merchData.discount);
  const rate = zigExchangeRate();
  const zig =
    priced.usd != null && price.data.currency === "USD"
      ? priced.usd * rate
      : price.data.currency === "ZIG"
        ? priced.usd
        : null;

  return {
    ok: true,
    data: {
      id: item.id,
      oem: item.oem_part_number,
      name: item.description?.trim() || item.oem_part_number,
      brand: "Nissan OE",
      category,
      usd: priced.usd,
      listUsd: priced.listUsd,
      discount: merchData.discount,
      productImages: merchData.imageUrls,
      zig,
      stock: stockStateFromQty(saleableQty, item.reorder_point),
      coreCharge: price.data.coreCharge,
      replaces,
      specs,
      fitments: fitments.data,
      alternatives: alts,
      diagram: diagram.data,
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
  const diagram = await loadOemCatalogDiagram(client, oem);
  if (!diagram.ok) return { ok: false, error: diagram.error };

  return {
    ok: true,
    data: {
      id: oem,
      oem,
      name:
        primary.subcategory_name?.trim() ||
        (primary.category_name &&
        primary.category_name.toLowerCase() !== "uncategorized"
          ? primary.category_name
          : null) ||
        oem,
      brand: "Nissan OE",
      category: pickDisplayCategory([
        primary.category_name,
        primary.subcategory_name,
      ]),
      usd: null,
      zig: null,
      stock: "counter_only",
      coreCharge: 0,
      replaces: (xrefs ?? []).map((x) => x.oe_number),
      specs,
      fitments: fitments.data,
      alternatives: alts,
      diagram: diagram.data,
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
      category_name: normalizeDisplayCategory(p?.category_name),
      subcategory_name: normalizeDisplayCategory(p?.subcategory_name),
      model_variant: vehicle?.model_variant ?? null,
      production_year: vehicle?.production_year ?? null,
    };
  });

  return { ok: true, data: lines };
}

/** Prefer customer-assigned list (B2B/Fleet/trade); else default RETAIL. */
async function resolveActivePriceList(
  client: SupabaseClient,
): Promise<
  | { ok: true; data: { id: string; currency: "USD" | "ZIG" } | null }
  | { ok: false; error: string }
> {
  // Soft-fail customers read (anon / no customer row) → default RETAIL.
  const { data: customer } = await client
    .from("customers")
    .select("price_list_id")
    .limit(1)
    .maybeSingle();

  if (customer?.price_list_id) {
    const { data: assigned, error } = await client
      .from("price_lists")
      .select("id, currency")
      .eq("id", customer.price_list_id)
      .eq("is_active", true)
      .maybeSingle();
    if (error) return { ok: false, error: error.message };
    if (assigned) return { ok: true, data: assigned };
  }

  const { data: list, error: listErr } = await client
    .from("price_lists")
    .select("id, currency")
    .eq("is_default", true)
    .eq("is_active", true)
    .limit(1)
    .maybeSingle();
  if (listErr) return { ok: false, error: listErr.message };
  return { ok: true, data: list ?? null };
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
  const listResult = await resolveActivePriceList(client);
  if (!listResult.ok) return listResult;
  const list = listResult.data;
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
 * Browse list for /shop — only saleable in-stock + priced items.
 * Sort / price filters are applied after price + qty join (KMP FilterDialog parity).
 */
export async function listCatalogProducts(
  client: SupabaseClient,
  opts: CatalogListOpts = {},
): Promise<
  | {
      ok: true;
      data: CatalogListItem[];
      /** @deprecated Prefer `categoryFacets` (slug + label). */
      categories: string[];
      categoryFacets: ShopFacetOption[];
    }
  | { ok: false; error: string }
> {
  const limit = opts.limit ?? 50;
  const parentCat = opts.category?.trim() || null;
  const subCat = opts.subcategory?.trim() || null;
  const cat = effectiveCategoryFilter(parentCat, subCat);
  const sort = opts.sort ?? "oem";
  /** Fetch a wider window when we sort/filter client-side (shop gate + price sorts). */
  const fetchLimit = Math.max(limit * 4, 120);

  // Merchandising taxonomy only — never dump raw pnc_categories.category_name
  // (Megazip "FOR <vehicle…>" assembly strings) into the FILTERS pane.
  const categoryFacets = shopCategoryFacetOptions(parentCat, subCat);
  const categories = categoryFacets.map((f) => f.label);

  let oemFilter: string[] | null = null;
  if (cat) {
    // Merchandising slugs (`brakes`, `ball-joints`) must match EPC groups /
    // PNC subcategory stems — exact equality always misses PartSouq data.
    const { data: pncs } = await client
      .from("pnc_categories")
      .select("pnc_code, category_name, subcategory_name")
      .limit(2000);

    const codes = (pncs ?? [])
      .filter((p) =>
        categoryMatchesFilter(
          cat,
          normalizeDisplayCategory(p.category_name),
          normalizeDisplayCategory(p.subcategory_name),
        ),
      )
      .map((p) => p.pnc_code);
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
        return { ok: true, data: [], categories, categoryFacets };
      }
    } else {
      return { ok: true, data: [], categories, categoryFacets };
    }
  }

  const orderCol = sort === "newest" ? "created_at" : "oem_part_number";
  let query = client
    .from("stock_items")
    .select("id, oem_part_number, description, reorder_point, created_at")
    .order(orderCol, { ascending: sort !== "newest" })
    .limit(fetchLimit);

  if (oemFilter) {
    query = query.in("oem_part_number", oemFilter);
  }

  const { data: items, error } = await query;
  if (error) return { ok: false, error: error.message };
  if (!items?.length) return { ok: true, data: [], categories, categoryFacets };

  const ids = items.map((i) => i.id);
  const oems = items.map((i) => i.oem_part_number);

  const [levels, prices, fitCats, merchRows] = await Promise.all([
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
    // New in 20260813100000 — cast until `supabase gen types` refreshed.
    (
      client as unknown as {
        from: (t: "stock_item_shop_merch") => ReturnType<SupabaseClient["from"]>;
      }
    )
      .from("stock_item_shop_merch")
      .select("stock_item_id, discount_kind, discount_value, discount_description")
      .in("stock_item_id", ids),
  ]);

  if (levels.error) return { ok: false, error: levels.error.message };
  if (!prices.ok) return prices;
  // Soft-fail merch if migration not applied yet.
  const merchOk = !("error" in merchRows && merchRows.error);

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

  const merchByItem = new Map(
    merchOk
      ? (
          ((merchRows as { data?: unknown }).data ?? []) as Array<{
            stock_item_id: string;
            discount_kind: string;
            discount_value: number;
            discount_description: string | null;
          }>
        ).map((m) => [m.stock_item_id, m])
      : [],
  );

  const catByOem = new Map<string, string>();
  const catsByOem = new Map<string, string[]>();
  for (const row of fitCats.data ?? []) {
    const pnc = row.pnc_categories as
      | { category_name: string }
      | { category_name: string }[]
      | null;
    const p = Array.isArray(pnc) ? pnc[0] : pnc;
    if (!p?.category_name) continue;
    const list = catsByOem.get(row.oem_part_number) ?? [];
    list.push(p.category_name);
    catsByOem.set(row.oem_part_number, list);
  }
  for (const [oem, names] of catsByOem) {
    const picked = pickDisplayCategory(names);
    if (picked) catByOem.set(oem, picked);
  }

  const rate = zigExchangeRate();
  let list: CatalogListItem[] = items.map((item) => {
    const price = prices.data.get(item.id);
    const listUsd =
      price?.currency === "USD"
        ? price.unitPrice
        : price?.currency === "ZIG"
          ? null
          : (price?.unitPrice ?? null);
    const merch = merchByItem.get(item.id);
    const discount =
      merch && merch.discount_kind !== "none"
        ? {
            kind: merch.discount_kind as "percent" | "amount",
            value: Number(merch.discount_value),
            description: merch.discount_description,
          }
        : null;
    const priced = applyShopDiscount(listUsd, discount);
    const zig =
      priced.usd != null
        ? priced.usd * rate
        : price?.currency === "ZIG"
          ? price.unitPrice
          : null;
    const qty = qtyByItem.get(item.id) ?? 0;

    return {
      oem: item.oem_part_number,
      name: item.description?.trim() || item.oem_part_number,
      stock: stockStateFromQty(qty, item.reorder_point),
      usd: priced.usd,
      zig,
      category: catByOem.get(item.oem_part_number) ?? null,
      qty,
      createdAt: item.created_at ?? null,
      discountDescription: discount?.description ?? null,
    };
  });

  list = applyCatalogFiltersAndSort(list, {
    sort,
    minUsd: opts.minUsd,
    maxUsd: opts.maxUsd,
    shopStockOnly: true,
  }).slice(0, limit);

  return { ok: true, data: list, categories, categoryFacets };
}

export function applyCatalogFiltersAndSort(
  items: CatalogListItem[],
  opts: {
    sort?: CatalogSort;
    minUsd?: number | null;
    maxUsd?: number | null;
    /** When true (default for /shop), keep only qty > 0 and priced > 0. */
    shopStockOnly?: boolean;
  },
): CatalogListItem[] {
  let list = [...items];
  if (opts.shopStockOnly !== false) {
    list = list.filter(
      (i) => (i.qty ?? 0) > 0 && i.usd != null && i.usd > 0,
    );
  }
  const min = opts.minUsd;
  const max = opts.maxUsd;
  if (min != null && Number.isFinite(min)) {
    list = list.filter((i) => i.usd != null && i.usd >= min);
  }
  if (max != null && Number.isFinite(max)) {
    list = list.filter((i) => i.usd != null && i.usd <= max);
  }

  const sort = opts.sort ?? "oem";
  list.sort((a, b) => {
    switch (sort) {
      case "newest":
        return (b.createdAt ?? "").localeCompare(a.createdAt ?? "");
      case "price_asc":
        return (a.usd ?? Number.POSITIVE_INFINITY) - (b.usd ?? Number.POSITIVE_INFINITY);
      case "price_desc":
        return (b.usd ?? Number.NEGATIVE_INFINITY) - (a.usd ?? Number.NEGATIVE_INFINITY);
      case "movers":
        return (b.qty ?? 0) - (a.qty ?? 0);
      case "name":
        return a.name.localeCompare(b.name);
      case "oem":
      default:
        return a.oem.localeCompare(b.oem);
    }
  });
  return list;
}

export const PRODUCT_IMAGES_BUCKET = "product-images";

export type ShopDiscount = {
  kind: "none" | "percent" | "amount";
  value: number;
  description: string | null;
};

export function applyShopDiscount(
  listUsd: number | null,
  discount: ShopDiscount | null | undefined,
): { usd: number | null; listUsd: number | null } {
  if (listUsd == null) return { usd: null, listUsd: null };
  if (!discount || discount.kind === "none" || discount.value <= 0) {
    return { usd: listUsd, listUsd: null };
  }
  let usd = listUsd;
  if (discount.kind === "percent") {
    usd = listUsd * (1 - Math.min(discount.value, 100) / 100);
  } else {
    usd = Math.max(0, listUsd - discount.value);
  }
  return { usd, listUsd };
}

export function productImagePublicUrl(
  client: SupabaseClient,
  storagePath: string,
): string {
  const path = storagePath.replace(/^product-images\//, "");
  const { data } = client.storage.from(PRODUCT_IMAGES_BUCKET).getPublicUrl(path);
  return data.publicUrl;
}

async function loadShopMerchForItem(
  client: SupabaseClient,
  stockItemId: string,
): Promise<
  | {
      ok: true;
      data: { discount: ShopDiscount | null; imageUrls: string[] };
    }
  | { ok: false; error: string }
> {
  const db = client as unknown as {
    from: (t: string) => ReturnType<SupabaseClient["from"]>;
  };
  const [merchRes, imgRes] = await Promise.all([
    db
      .from("stock_item_shop_merch")
      .select("discount_kind, discount_value, discount_description")
      .eq("stock_item_id", stockItemId)
      .maybeSingle(),
    db
      .from("stock_item_images")
      .select("storage_path, is_primary, sort_order")
      .eq("stock_item_id", stockItemId)
      .order("is_primary", { ascending: false })
      .order("sort_order", { ascending: true }),
  ]);

  if (merchRes.error) return { ok: false, error: merchRes.error.message };
  if (imgRes.error) return { ok: false, error: imgRes.error.message };

  const m = merchRes.data as {
    discount_kind: string;
    discount_value: number;
    discount_description: string | null;
  } | null;
  const discount: ShopDiscount | null =
    m && m.discount_kind !== "none"
      ? {
          kind: m.discount_kind as "percent" | "amount",
          value: Number(m.discount_value),
          description: m.discount_description,
        }
      : null;

  const imageRows = (imgRes.data ?? []) as Array<{ storage_path: string }>;
  const imageUrls = imageRows.map((row) =>
    productImagePublicUrl(client, row.storage_path),
  );

  return { ok: true, data: { discount, imageUrls } };
}

type HomeRailRpcRow = {
  rail: string;
  oem_part_number: string;
  catalog_title: string;
  qty_saleable: number;
  unit_price: number;
  currency: "USD" | "ZIG";
  created_at: string | null;
  reorder_point: number | null;
  discount_kind: string;
  discount_value: number;
  discount_description: string | null;
};

function mapHomeRailRow(row: HomeRailRpcRow): CatalogListItem {
  const qty = Number(row.qty_saleable ?? 0);
  const listUsd =
    row.currency === "USD"
      ? Number(row.unit_price)
      : row.currency === "ZIG"
        ? null
        : Number(row.unit_price);
  const discount =
    row.discount_kind && row.discount_kind !== "none"
      ? {
          kind: row.discount_kind as "percent" | "amount",
          value: Number(row.discount_value),
          description: row.discount_description,
        }
      : null;
  const priced = applyShopDiscount(listUsd, discount);
  const rate = zigExchangeRate();
  const zig =
    priced.usd != null
      ? priced.usd * rate
      : row.currency === "ZIG"
        ? Number(row.unit_price)
        : null;

  return {
    oem: row.oem_part_number,
    name: row.catalog_title?.trim() || row.oem_part_number,
    stock: stockStateFromQty(qty, row.reorder_point),
    usd: priced.usd,
    zig,
    category: null,
    qty,
    createdAt: row.created_at,
    discountDescription: discount?.description ?? null,
  };
}

/**
 * Home merchandising rails — prefers anon-safe RPC
 * (`list_storefront_home_rails`); falls back to table reads when migration
 * is not applied yet (authenticated RLS path).
 */
export async function listHomeMerchRails(
  client: SupabaseClient,
  railLimit = 12,
): Promise<
  | {
      ok: true;
      featured: CatalogListItem[];
      movers: CatalogListItem[];
      newest: CatalogListItem[];
      categories: string[];
    }
  | { ok: false; error: string }
> {
  // Cast until `supabase gen types` includes this RPC.
  const rpc = await (
    client as unknown as {
      rpc: (
        fn: string,
        args?: Record<string, unknown>,
      ) => Promise<{ data: unknown; error: { message: string } | null }>;
    }
  ).rpc("list_storefront_home_rails", {
    p_limit: railLimit,
  });

  if (!rpc.error && Array.isArray(rpc.data)) {
    const featured: CatalogListItem[] = [];
    const movers: CatalogListItem[] = [];
    const newest: CatalogListItem[] = [];
    for (const raw of rpc.data as HomeRailRpcRow[]) {
      const item = mapHomeRailRow(raw);
      if (raw.rail === "featured") featured.push(item);
      else if (raw.rail === "movers") movers.push(item);
      else if (raw.rail === "newest") newest.push(item);
    }
    return { ok: true, featured, movers, newest, categories: [] };
  }

  // Soft-fail missing RPC / schema cache — authenticated table path.
  const [movers, newest] = await Promise.all([
    listCatalogProducts(client, { sort: "movers", limit: railLimit }),
    listCatalogProducts(client, { sort: "newest", limit: railLimit }),
  ]);
  if (!movers.ok) {
    return {
      ok: false,
      error: rpc.error?.message
        ? `${rpc.error.message} (fallback: ${movers.error})`
        : movers.error,
    };
  }
  if (!newest.ok) return newest;

  // Fallback when RPC missing: movers slice only (no pins without migration).
  const featured = movers.data.slice(0, railLimit);
  return {
    ok: true,
    featured,
    movers: movers.data,
    newest: newest.data,
    categories: movers.categories,
  };
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

  const listResult = await resolveActivePriceList(client);
  if (!listResult.ok) return listResult;
  const list = listResult.data;
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
