import type { SupabaseClient } from "@gtr/supabase-client";
import {
  addOemToCompare,
  clearCompare,
  MAX_COMPARE,
  readCompareOems,
  removeOemFromCompare,
  writeCompareOems,
} from "@/lib/compare-selection";
import type { StorefrontResult } from "@/lib/customer-storefront";

export type CompareItemRow = {
  id: string;
  stock_item_id: string;
  oem_part_number: string;
  description: string;
  created_at: string;
};

export async function listCompareItems(
  client: SupabaseClient,
): Promise<StorefrontResult<CompareItemRow[]>> {
  const { data, error } = await client.rpc("list_customer_compare_items");
  if (error) return { ok: false, error: error.message };
  return { ok: true, data: (data as CompareItemRow[]) ?? [] };
}

export async function addCompareItem(
  client: SupabaseClient,
  args: { stockItemId?: string; oem?: string },
): Promise<StorefrontResult<string>> {
  const { data, error } = await client.rpc("add_customer_compare_item", {
    p_stock_item_id: args.stockItemId ?? undefined,
    p_oem_part_number: args.oem ?? undefined,
  });
  if (error) return { ok: false, error: error.message };
  if (!data) return { ok: false, error: "add_customer_compare_item returned no id." };
  return { ok: true, data };
}

export async function removeCompareItem(
  client: SupabaseClient,
  args: { compareId?: string; stockItemId?: string; oem?: string },
): Promise<StorefrontResult<true>> {
  const { error } = await client.rpc("remove_customer_compare_item", {
    p_compare_id: args.compareId ?? undefined,
    p_stock_item_id: args.stockItemId ?? undefined,
    p_oem_part_number: args.oem ?? undefined,
  });
  if (error) return { ok: false, error: error.message };
  return { ok: true, data: true };
}

/**
 * Guest: localStorage only.
 * Auth: server RPCs; also mirrors OEM list into localStorage for PDP badges.
 */
export async function addOemToCompareTray(
  client: SupabaseClient | null,
  oem: string,
  authenticated: boolean,
): Promise<{ ok: true; oems: string[] } | { ok: false; error: string }> {
  const needle = oem.trim();
  if (!needle) return { ok: false, error: "OEM required." };

  if (!authenticated || !client) {
    return addOemToCompare(needle);
  }

  const existing = await listCompareItems(client);
  if (!existing.ok) return { ok: false, error: existing.error };
  if (
    !existing.data.some(
      (r) => r.oem_part_number.toLowerCase() === needle.toLowerCase(),
    ) &&
    existing.data.length >= MAX_COMPARE
  ) {
    return {
      ok: false,
      error: `Compare holds up to ${MAX_COMPARE} SKUs. Remove one first.`,
    };
  }

  const added = await addCompareItem(client, { oem: needle });
  if (!added.ok) return { ok: false, error: added.error };

  const local = addOemToCompare(needle);
  if (!local.ok) {
    // Server succeeded; still sync local from server list
    const refreshed = await listCompareItems(client);
    if (refreshed.ok) {
      writeCompareOems(refreshed.data.map((r) => r.oem_part_number));
      return { ok: true, oems: refreshed.data.map((r) => r.oem_part_number) };
    }
  }
  return local.ok ? local : { ok: true, oems: readCompareOems() };
}

export async function removeOemFromCompareTray(
  client: SupabaseClient | null,
  oem: string,
  authenticated: boolean,
): Promise<string[]> {
  const needle = oem.trim();
  if (authenticated && client && needle) {
    await removeCompareItem(client, { oem: needle });
  }
  return removeOemFromCompare(needle);
}

/** Push guest localStorage OEMs to server after login; then prefer server list. */
export async function syncLocalCompareToServer(
  client: SupabaseClient,
): Promise<StorefrontResult<string[]>> {
  const local = readCompareOems();
  for (const oem of local) {
    const res = await addCompareItem(client, { oem });
    if (!res.ok && !/full|already|conflict/i.test(res.error)) {
      // Ignore "already present"; surface real failures
      if (!/not found|full/i.test(res.error)) {
        // continue syncing remaining OEMs
      }
    }
  }
  const listed = await listCompareItems(client);
  if (!listed.ok) return listed;
  const oems = listed.data.map((r) => r.oem_part_number).slice(0, MAX_COMPARE);
  writeCompareOems(oems);
  return { ok: true, data: oems };
}

export async function clearCompareTray(
  client: SupabaseClient | null,
  authenticated: boolean,
): Promise<void> {
  if (authenticated && client) {
    const listed = await listCompareItems(client);
    if (listed.ok) {
      for (const row of listed.data) {
        await removeCompareItem(client, { compareId: row.id });
      }
    }
  }
  clearCompare();
}
