import type { SupabaseClient } from "@gtr/supabase-client";
import type { StorefrontResult } from "@/lib/customer-storefront";
import { searchStockItems, type StockItemOption } from "@/lib/staff-warehouse";

export type { StockItemOption };

export type StaffKitComponent = {
  componentItemId: string;
  oem: string;
  name: string;
  qty: number;
  uomId: string;
};

export type StaffKitRow = {
  kitId: string;
  stockItemId: string;
  oem: string;
  title: string;
  sellMode: "explode" | "stocked";
  isActive: boolean;
  chassisCodes: string[];
  components: StaffKitComponent[];
};

export type ChassisOption = {
  chassisCode: string;
  label: string;
};

type RpcClient = {
  rpc: (
    fn: string,
    args?: Record<string, unknown>,
  ) => Promise<{ data: unknown; error: { message: string } | null }>;
};

function asRpc(client: SupabaseClient): RpcClient {
  return client as unknown as RpcClient;
}

function asStockBrief(
  raw: unknown,
): { oem_part_number: string; description: string | null } | null {
  if (!raw) return null;
  const row = Array.isArray(raw) ? raw[0] : raw;
  if (!row || typeof row !== "object") return null;
  const o = row as Record<string, unknown>;
  return {
    oem_part_number: String(o.oem_part_number ?? ""),
    description: (o.description as string | null) ?? null,
  };
}

export { searchStockItems };

export async function listChassisOptions(
  client: SupabaseClient,
): Promise<StorefrontResult<ChassisOption[]>> {
  const { data, error } = await client
    .from("vehicle_master")
    .select("chassis_code, model_variant")
    .order("chassis_code", { ascending: true })
    .limit(800);
  if (error) return { ok: false, error: error.message };

  const byCode = new Map<string, string>();
  for (const row of data ?? []) {
    const code = String(row.chassis_code ?? "").trim();
    if (!code || byCode.has(code)) continue;
    const variant = String(row.model_variant ?? "").trim();
    byCode.set(code, variant ? `${code} — ${variant}` : code);
  }
  return {
    ok: true,
    data: [...byCode.entries()].map(([chassisCode, label]) => ({
      chassisCode,
      label,
    })),
  };
}

export async function listStaffKits(
  client: SupabaseClient,
): Promise<StorefrontResult<StaffKitRow[]>> {
  const { data: kits, error } = await client
    .from("item_kits")
    .select(
      "id, sell_mode, is_active, stock_item_id, stock_items ( oem_part_number, description )",
    )
    .order("created_at", { ascending: false })
    .limit(100);
  if (error) return { ok: false, error: error.message };
  if (!kits?.length) return { ok: true, data: [] };

  const kitIds = kits.map((k) => k.id);
  const oems = kits
    .map((k) => asStockBrief(k.stock_items)?.oem_part_number)
    .filter((o): o is string => Boolean(o));

  const [{ data: comps, error: compErr }, { data: fitment, error: fitErr }] =
    await Promise.all([
      client
        .from("item_kit_components")
        .select(
          "kit_id, qty, uom_id, component_item_id, stock_items:component_item_id ( oem_part_number, description )",
        )
        .in("kit_id", kitIds),
      oems.length
        ? client
            .from("part_fitment")
            .select("oem_part_number, chassis_code")
            .in("oem_part_number", oems)
        : Promise.resolve({ data: [], error: null }),
    ]);

  if (compErr) return { ok: false, error: compErr.message };
  if (fitErr) return { ok: false, error: fitErr.message };

  const chassisByOem = new Map<string, string[]>();
  for (const f of fitment ?? []) {
    const oem = String(f.oem_part_number ?? "");
    const chassis = String(f.chassis_code ?? "").trim();
    if (!oem || !chassis) continue;
    const list = chassisByOem.get(oem) ?? [];
    if (!list.includes(chassis)) list.push(chassis);
    chassisByOem.set(oem, list);
  }

  const byKit = new Map<string, StaffKitComponent[]>();
  for (const c of comps ?? []) {
    const item = asStockBrief(c.stock_items);
    const list = byKit.get(c.kit_id) ?? [];
    list.push({
      componentItemId: String(c.component_item_id),
      oem: item?.oem_part_number ?? "—",
      name: item?.description?.trim() || item?.oem_part_number || "Component",
      qty: Number(c.qty),
      uomId: String(c.uom_id),
    });
    byKit.set(c.kit_id, list);
  }

  const out: StaffKitRow[] = kits.map((k) => {
    const item = asStockBrief(k.stock_items);
    const oem = item?.oem_part_number ?? k.stock_item_id;
    return {
      kitId: k.id,
      stockItemId: k.stock_item_id,
      oem,
      title: item?.description?.trim() || item?.oem_part_number || "Kit",
      sellMode: k.sell_mode as "explode" | "stocked",
      isActive: Boolean(k.is_active),
      chassisCodes: chassisByOem.get(oem) ?? [],
      components: byKit.get(k.id) ?? [],
    };
  });
  return { ok: true, data: out };
}

export async function createStaffKit(
  client: SupabaseClient,
  input: {
    oem: string;
    title: string;
    componentItemIds: string[];
    chassisCode?: string | null;
    qtys?: number[];
  },
): Promise<StorefrontResult<string>> {
  const components = input.componentItemIds.map((id, i) => ({
    stock_item_id: id,
    qty: input.qtys?.[i] ?? 1,
  }));
  const { data, error } = await asRpc(client).rpc("create_kit_with_components", {
    p_oem: input.oem.trim(),
    p_title: input.title.trim(),
    p_components: components,
    p_chassis_code: input.chassisCode?.trim() || null,
    p_sell_mode: "explode",
  });
  if (error) return { ok: false, error: error.message };
  if (!data) return { ok: false, error: "create_kit_with_components returned no id." };
  return { ok: true, data: String(data) };
}

export async function updateStaffKit(
  client: SupabaseClient,
  input: {
    kitId: string;
    title?: string | null;
    isActive?: boolean | null;
    sellMode?: "explode" | "stocked" | null;
  },
): Promise<StorefrontResult<string>> {
  const { data, error } = await asRpc(client).rpc("update_item_kit", {
    p_kit_id: input.kitId,
    p_sell_mode: input.sellMode ?? null,
    p_is_active: input.isActive ?? null,
    p_title: input.title?.trim() || null,
  });
  if (error) return { ok: false, error: error.message };
  return { ok: true, data: String(data ?? input.kitId) };
}

export async function addStaffKitComponent(
  client: SupabaseClient,
  input: {
    kitId: string;
    componentItemId: string;
    qty?: number;
    uomId: string;
  },
): Promise<StorefrontResult<string>> {
  const { data, error } = await asRpc(client).rpc("add_kit_component", {
    p_kit_id: input.kitId,
    p_component_item_id: input.componentItemId,
    p_qty: input.qty ?? 1,
    p_uom_id: input.uomId,
  });
  if (error) return { ok: false, error: error.message };
  return { ok: true, data: String(data) };
}

export async function removeStaffKitComponent(
  client: SupabaseClient,
  input: { kitId: string; componentItemId: string },
): Promise<StorefrontResult<true>> {
  const { error } = await asRpc(client).rpc("remove_kit_component", {
    p_kit_id: input.kitId,
    p_component_item_id: input.componentItemId,
  });
  if (error) return { ok: false, error: error.message };
  return { ok: true, data: true };
}
