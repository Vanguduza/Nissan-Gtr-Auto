import type { Database, SupabaseClient } from "@gtr/supabase-client";
import type { StorefrontResult } from "@/lib/customer-storefront";
import { requireSession } from "@/lib/rfq-portal";

export { requireSession };

export type ProcurementClient = SupabaseClient<Database>;

export type PurchaseOrderRow =
  Database["public"]["Tables"]["purchase_orders"]["Row"];
export type MaterialRequestRow =
  Database["public"]["Tables"]["material_requests"]["Row"];

export type PendingPurchaseOrder = {
  po: PurchaseOrderRow;
  supplier: { code: string; name: string } | null;
  warehouse: { code: string; name: string } | null;
};

export type PendingMaterialRequest = {
  mr: MaterialRequestRow;
  warehouse: { code: string; name: string } | null;
  lineCount: number;
};

export async function listSubmittedPurchaseOrders(
  client: ProcurementClient,
): Promise<StorefrontResult<PendingPurchaseOrder[]>> {
  const { data, error } = await client
    .from("purchase_orders")
    .select(
      "*, suppliers(code, name), warehouses(code, name)",
    )
    .eq("status", "submitted")
    .order("submitted_at", { ascending: true });
  if (error) return { ok: false, error: error.message };

  const rows = (data ?? []).map((r) => {
    const row = r as PurchaseOrderRow & {
      suppliers?: { code: string; name: string } | null;
      warehouses?: { code: string; name: string } | null;
    };
    return {
      po: row,
      supplier: row.suppliers ?? null,
      warehouse: row.warehouses ?? null,
    };
  });
  return { ok: true, data: rows };
}

export async function listSubmittedMaterialRequests(
  client: ProcurementClient,
): Promise<StorefrontResult<PendingMaterialRequest[]>> {
  const { data, error } = await client
    .from("material_requests")
    .select("*, warehouses(code, name), material_request_lines(id)")
    .eq("status", "submitted")
    .order("submitted_at", { ascending: true });
  if (error) return { ok: false, error: error.message };

  const rows = (data ?? []).map((r) => {
    const row = r as MaterialRequestRow & {
      warehouses?: { code: string; name: string } | null;
      material_request_lines?: { id: string }[] | null;
    };
    return {
      mr: row,
      warehouse: row.warehouses ?? null,
      lineCount: row.material_request_lines?.length ?? 0,
    };
  });
  return { ok: true, data: rows };
}

export async function approvePurchaseOrder(
  client: ProcurementClient,
  purchaseOrderId: string,
): Promise<StorefrontResult<string>> {
  const { data, error } = await client.rpc("approve_purchase_order", {
    p_purchase_order_id: purchaseOrderId,
  });
  if (error) return { ok: false, error: error.message };
  if (!data) {
    return { ok: false, error: "approve_purchase_order returned no id." };
  }
  return { ok: true, data };
}

export async function rejectPurchaseOrder(
  client: ProcurementClient,
  args: { purchaseOrderId: string; reason?: string },
): Promise<StorefrontResult<string>> {
  const { data, error } = await client.rpc("reject_purchase_order", {
    p_purchase_order_id: args.purchaseOrderId,
    p_reason: args.reason?.trim() || undefined,
  });
  if (error) return { ok: false, error: error.message };
  if (!data) {
    return { ok: false, error: "reject_purchase_order returned no id." };
  }
  return { ok: true, data };
}

export async function approveMaterialRequest(
  client: ProcurementClient,
  materialRequestId: string,
): Promise<StorefrontResult<string>> {
  const { data, error } = await client.rpc("approve_material_request", {
    p_material_request_id: materialRequestId,
  });
  if (error) return { ok: false, error: error.message };
  if (!data) {
    return { ok: false, error: "approve_material_request returned no id." };
  }
  return { ok: true, data };
}

export async function rejectMaterialRequest(
  client: ProcurementClient,
  args: { materialRequestId: string; reason?: string },
): Promise<StorefrontResult<string>> {
  const { data, error } = await client.rpc("reject_material_request", {
    p_material_request_id: args.materialRequestId,
    p_reason: args.reason?.trim() || undefined,
  });
  if (error) return { ok: false, error: error.message };
  if (!data) {
    return { ok: false, error: "reject_material_request returned no id." };
  }
  return { ok: true, data };
}
