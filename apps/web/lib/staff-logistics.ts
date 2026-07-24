import type { SupabaseClient } from "@gtr/supabase-client";
import {
  requireSession,
  type StorefrontResult,
} from "@/lib/customer-storefront";

export { requireSession };

export type DispatchInvoiceOption = {
  id: string;
  document_number: string | null;
  status: string;
  fulfillment_mode: string | null;
  created_at: string;
};

export type PickListOption = {
  id: string;
  document_number: string | null;
  sales_invoice_id: string;
  status: string;
  created_at: string;
};

export type PickListLineRow = {
  id: string;
  pick_list_id: string;
  sales_invoice_line_id: string;
  stock_item_id: string;
  qty_requested: number;
  qty_picked: number | null;
  stock_items?: { oem_part_number: string; description: string | null } | null;
};

export type DeliveryNoteOption = {
  id: string;
  document_number: string | null;
  sales_invoice_id: string;
  pick_list_id: string | null;
  status: string;
  created_at: string;
};

function asSingle<T>(value: T | T[] | null | undefined): T | null {
  if (value == null) return null;
  return Array.isArray(value) ? (value[0] ?? null) : value;
}

export async function listDispatchInvoices(
  client: SupabaseClient,
): Promise<StorefrontResult<DispatchInvoiceOption[]>> {
  const { data, error } = await client
    .from("sales_invoices")
    .select("id, document_number, status, fulfillment_mode, created_at")
    .eq("doc_type", "invoice")
    .eq("status", "posted")
    .eq("fulfillment_mode", "dispatch")
    .order("created_at", { ascending: false })
    .limit(40);
  if (error) return { ok: false, error: error.message };
  return { ok: true, data: (data as DispatchInvoiceOption[]) ?? [] };
}

export async function listPickLists(
  client: SupabaseClient,
): Promise<StorefrontResult<PickListOption[]>> {
  const { data, error } = await client
    .from("pick_lists")
    .select("id, document_number, sales_invoice_id, status, created_at")
    .order("created_at", { ascending: false })
    .limit(40);
  if (error) return { ok: false, error: error.message };
  return { ok: true, data: (data as PickListOption[]) ?? [] };
}

export async function listDeliveryNotes(
  client: SupabaseClient,
): Promise<StorefrontResult<DeliveryNoteOption[]>> {
  const { data, error } = await client
    .from("delivery_notes")
    .select(
      "id, document_number, sales_invoice_id, pick_list_id, status, created_at",
    )
    .order("created_at", { ascending: false })
    .limit(40);
  if (error) return { ok: false, error: error.message };
  return { ok: true, data: (data as DeliveryNoteOption[]) ?? [] };
}

export async function loadPickListLines(
  client: SupabaseClient,
  pickListId: string,
): Promise<StorefrontResult<PickListLineRow[]>> {
  const { data, error } = await client
    .from("pick_list_lines")
    .select(
      "id, pick_list_id, sales_invoice_line_id, stock_item_id, qty_requested, qty_picked, stock_items(oem_part_number, description)",
    )
    .eq("pick_list_id", pickListId);
  if (error) return { ok: false, error: error.message };
  const rows = (data ?? []).map((row) => ({
    ...row,
    stock_items: asSingle(row.stock_items),
  }));
  return { ok: true, data: rows as PickListLineRow[] };
}

export async function createPickList(
  client: SupabaseClient,
  salesInvoiceId: string,
): Promise<StorefrontResult<string>> {
  const { data, error } = await client.rpc("create_pick_list", {
    p_sales_invoice_id: salesInvoiceId,
    p_lines: null,
  });
  if (error) return { ok: false, error: error.message };
  if (!data) return { ok: false, error: "create_pick_list returned no id." };
  return { ok: true, data };
}

export async function confirmPickLines(
  client: SupabaseClient,
  pickListId: string,
  lines: { pick_list_line_id: string; qty_picked: number }[],
): Promise<StorefrontResult<string>> {
  const { data, error } = await client.rpc("confirm_pick_lines", {
    p_pick_list_id: pickListId,
    p_lines: lines.map((l) => ({
      pick_list_line_id: l.pick_list_line_id,
      qty_picked: l.qty_picked,
    })),
  });
  if (error) return { ok: false, error: error.message };
  if (!data) return { ok: false, error: "confirm_pick_lines returned no id." };
  return { ok: true, data };
}

export async function createDeliveryNote(
  client: SupabaseClient,
  args: {
    salesInvoiceId: string;
    pickListId?: string | null;
    lines: { sales_invoice_line_id: string; qty: number }[];
  },
): Promise<StorefrontResult<string>> {
  const { data, error } = await client.rpc("create_delivery_note", {
    p_sales_invoice_id: args.salesInvoiceId,
    p_lines: args.lines.map((l) => ({
      sales_invoice_line_id: l.sales_invoice_line_id,
      qty: l.qty,
    })),
    p_pick_list_id: args.pickListId || undefined,
  });
  if (error) return { ok: false, error: error.message };
  if (!data) return { ok: false, error: "create_delivery_note returned no id." };
  return { ok: true, data };
}

export async function submitDeliveryNote(
  client: SupabaseClient,
  deliveryNoteId: string,
): Promise<StorefrontResult<string>> {
  const { data, error } = await client.rpc("submit_delivery_note", {
    p_delivery_note_id: deliveryNoteId,
  });
  if (error) return { ok: false, error: error.message };
  if (!data) return { ok: false, error: "submit_delivery_note returned no id." };
  return { ok: true, data };
}

export async function createDeliveryJob(
  client: SupabaseClient,
  args: {
    deliveryNoteId: string;
    notes?: string;
    etaAt?: string;
  },
): Promise<StorefrontResult<string>> {
  const { data, error } = await client.rpc("create_delivery_job", {
    p_delivery_note_id: args.deliveryNoteId,
    p_notes: args.notes || undefined,
    p_eta_at: args.etaAt || undefined,
  });
  if (error) return { ok: false, error: error.message };
  if (!data) return { ok: false, error: "create_delivery_job returned no id." };
  return { ok: true, data };
}
