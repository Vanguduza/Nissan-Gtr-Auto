import type { Database, SupabaseClient } from "@gtr/supabase-client";
import {
  requireSession,
  type StorefrontResult,
} from "@/lib/customer-storefront";
import type { TransferLineInput } from "@/lib/staff-warehouse";

export { requireSession };

export type WarrantyClaimStatus =
  Database["public"]["Enums"]["warranty_claim_status"];
export type WarrantyClaimResolution =
  Database["public"]["Enums"]["warranty_claim_resolution"];

export type WarrantyClaimOption = {
  id: string;
  document_number: string;
  status: WarrantyClaimStatus;
  resolution: WarrantyClaimResolution | null;
  sales_invoice_id: string | null;
  stock_serial_id: string | null;
  stock_batch_id: string | null;
  notes: string | null;
  reject_reason: string | null;
  created_at: string;
};

export async function listWarrantyClaims(
  client: SupabaseClient,
): Promise<StorefrontResult<WarrantyClaimOption[]>> {
  const { data, error } = await client
    .from("warranty_claims")
    .select(
      "id, document_number, status, resolution, sales_invoice_id, stock_serial_id, stock_batch_id, notes, reject_reason, created_at",
    )
    .order("created_at", { ascending: false })
    .limit(40);
  if (error) return { ok: false, error: error.message };
  return { ok: true, data: (data as WarrantyClaimOption[]) ?? [] };
}

export async function openWarrantyClaim(
  client: SupabaseClient,
  args: {
    salesInvoiceId?: string;
    stockSerialId?: string;
    stockBatchId?: string;
    notes?: string;
  },
): Promise<StorefrontResult<string>> {
  const { data, error } = await client.rpc("open_warranty_claim", {
    p_sales_invoice_id: args.salesInvoiceId || undefined,
    p_stock_serial_id: args.stockSerialId || undefined,
    p_stock_batch_id: args.stockBatchId || undefined,
    p_notes: args.notes || undefined,
  });
  if (error) return { ok: false, error: error.message };
  if (!data) return { ok: false, error: "open_warranty_claim returned no id." };
  return { ok: true, data };
}

export async function approveWarrantyClaim(
  client: SupabaseClient,
  args: {
    claimId: string;
    resolution: WarrantyClaimResolution;
    lines?: unknown;
    replacementLines?: unknown;
  },
): Promise<StorefrontResult<string>> {
  const { data, error } = await client.rpc("approve_warranty_claim", {
    p_claim_id: args.claimId,
    p_resolution: args.resolution,
    p_lines: args.lines,
    p_replacement_lines: args.replacementLines,
  });
  if (error) return { ok: false, error: error.message };
  if (!data) {
    return { ok: false, error: "approve_warranty_claim returned no id." };
  }
  return { ok: true, data };
}

export async function rejectWarrantyClaim(
  client: SupabaseClient,
  args: { claimId: string; reason?: string },
): Promise<StorefrontResult<string>> {
  const { data, error } = await client.rpc("reject_warranty_claim", {
    p_claim_id: args.claimId,
    p_reason: args.reason || undefined,
  });
  if (error) return { ok: false, error: error.message };
  if (!data) return { ok: false, error: "reject_warranty_claim returned no id." };
  return { ok: true, data };
}

export async function closeWarrantyClaim(
  client: SupabaseClient,
  claimId: string,
): Promise<StorefrontResult<string>> {
  const { data, error } = await client.rpc("close_warranty_claim", {
    p_claim_id: claimId,
  });
  if (error) return { ok: false, error: error.message };
  if (!data) return { ok: false, error: "close_warranty_claim returned no id." };
  return { ok: true, data };
}

/** Quarantine return path — never saleable warehouse as destination (RPC-enforced). */
export async function postReturnToQuarantine(
  client: SupabaseClient,
  args: {
    fromWarehouseId: string;
    notes: string;
    lines: TransferLineInput[];
  },
): Promise<StorefrontResult<string>> {
  const { data, error } = await client.rpc("post_return_to_quarantine", {
    p_from_warehouse_id: args.fromWarehouseId,
    p_notes: args.notes,
    p_lines: args.lines.map((l) => ({
      stock_item_id: l.stock_item_id,
      uom_id: l.uom_id,
      qty: l.qty,
      valuation_method: l.valuation_method ?? "FIFO",
    })),
  });
  if (error) return { ok: false, error: error.message };
  if (!data) {
    return { ok: false, error: "post_return_to_quarantine returned no id." };
  }
  return { ok: true, data };
}

export async function postReturnCreditNote(
  client: SupabaseClient,
  args: {
    invoiceId: string;
    lines: {
      stock_item_id: string;
      uom_id: string;
      qty: number;
      unit_price?: number;
    }[];
  },
): Promise<StorefrontResult<string>> {
  const { data, error } = await client.rpc("post_return_credit_note", {
    p_invoice_id: args.invoiceId,
    p_lines: args.lines,
  });
  if (error) return { ok: false, error: error.message };
  if (!data) {
    return { ok: false, error: "post_return_credit_note returned no id." };
  }
  return { ok: true, data };
}
