import type {
  Database,
  ProcurementDocStatus,
  RfqRow,
  SupabaseClient,
  SupplierQuotationRow,
} from "@gtr/supabase-client";
import { requireSession, type StorefrontResult } from "@/lib/customer-storefront";

export type { RfqRow, SupplierQuotationRow, ProcurementDocStatus };

export type CurrencyCode = Database["public"]["Enums"]["currency_code"];

export type RfqLineRow = Database["public"]["Tables"]["rfq_lines"]["Row"] & {
  stock_items?: { oem_part_number: string; description: string | null } | null;
};

export type QuotationWithSupplier = SupplierQuotationRow & {
  suppliers?: { code: string; name: string } | null;
};

export type QuotationLineRow =
  Database["public"]["Tables"]["supplier_quotation_lines"]["Row"];

export type WarehouseOption = { id: string; code: string; name: string };
export type SupplierOption = { id: string; code: string; name: string };
export type StockItemOption = {
  id: string;
  oem_part_number: string;
  description: string | null;
  base_uom_id: string | null;
};

export type RfqCreateLine = {
  stock_item_id: string;
  uom_id: string;
  qty: number;
};

export type QuoteLineInput = {
  rfq_line_id: string;
  stock_item_id: string;
  uom_id: string;
  qty: number;
  unit_price: number;
};

export type RfqDetail = {
  rfq: RfqRow;
  lines: RfqLineRow[];
  invites: { supplier_id: string; suppliers: { code: string; name: string } | null }[];
  quotations: QuotationWithSupplier[];
};

export { requireSession };

export function statusLabel(status: ProcurementDocStatus): string {
  return status;
}

export async function listStaffRfqs(
  client: SupabaseClient,
): Promise<StorefrontResult<RfqRow[]>> {
  const { data, error } = await client
    .from("rfqs")
    .select("*")
    .order("created_at", { ascending: false })
    .limit(100);
  if (error) return { ok: false, error: error.message };
  return { ok: true, data: data ?? [] };
}

export async function listSupplierRfqs(
  client: SupabaseClient,
): Promise<StorefrontResult<RfqRow[]>> {
  const { data, error } = await client
    .from("rfqs")
    .select("*")
    .order("created_at", { ascending: false })
    .limit(100);
  if (error) return { ok: false, error: error.message };
  return { ok: true, data: data ?? [] };
}

export async function loadWarehouses(
  client: SupabaseClient,
): Promise<StorefrontResult<WarehouseOption[]>> {
  const { data, error } = await client
    .from("warehouses")
    .select("id, code, name")
    .eq("is_active", true)
    .eq("is_quarantine", false)
    .order("code");
  if (error) return { ok: false, error: error.message };
  return { ok: true, data: data ?? [] };
}

export async function loadSuppliers(
  client: SupabaseClient,
): Promise<StorefrontResult<SupplierOption[]>> {
  const { data, error } = await client
    .from("suppliers")
    .select("id, code, name")
    .eq("is_active", true)
    .order("code");
  if (error) return { ok: false, error: error.message };
  return { ok: true, data: data ?? [] };
}

export async function searchStockItems(
  client: SupabaseClient,
  query: string,
): Promise<StorefrontResult<StockItemOption[]>> {
  const q = query.trim();
  if (q.length < 2) return { ok: true, data: [] };
  const { data, error } = await client
    .from("stock_items")
    .select("id, oem_part_number, description, base_uom_id")
    .or(`oem_part_number.ilike.%${q}%,description.ilike.%${q}%`)
    .limit(20);
  if (error) return { ok: false, error: error.message };
  return { ok: true, data: data ?? [] };
}

export async function loadRfqDetail(
  client: SupabaseClient,
  rfqId: string,
): Promise<StorefrontResult<RfqDetail>> {
  const rfqRes = await client.from("rfqs").select("*").eq("id", rfqId).maybeSingle();
  if (rfqRes.error) return { ok: false, error: rfqRes.error.message };
  if (!rfqRes.data) return { ok: false, error: "RFQ not found or not visible." };

  const [linesRes, invitesRes, quotesRes] = await Promise.all([
    client
      .from("rfq_lines")
      .select("*, stock_items(oem_part_number, description)")
      .eq("rfq_id", rfqId)
      .order("line_no"),
    client
      .from("rfq_suppliers")
      .select("supplier_id, suppliers(code, name)")
      .eq("rfq_id", rfqId),
    client
      .from("supplier_quotations")
      .select("*, suppliers(code, name)")
      .eq("rfq_id", rfqId)
      .order("created_at"),
  ]);

  if (linesRes.error) return { ok: false, error: linesRes.error.message };
  if (invitesRes.error) return { ok: false, error: invitesRes.error.message };
  if (quotesRes.error) return { ok: false, error: quotesRes.error.message };

  return {
    ok: true,
    data: {
      rfq: rfqRes.data,
      lines: (linesRes.data ?? []) as RfqLineRow[],
      invites: (invitesRes.data ?? []) as RfqDetail["invites"],
      quotations: (quotesRes.data ?? []) as QuotationWithSupplier[],
    },
  };
}

export async function loadOwnQuotation(
  client: SupabaseClient,
  rfqId: string,
): Promise<
  StorefrontResult<{
    quotation: SupplierQuotationRow | null;
    lines: QuotationLineRow[];
  }>
> {
  const { data: quotation, error } = await client
    .from("supplier_quotations")
    .select("*")
    .eq("rfq_id", rfqId)
    .maybeSingle();
  if (error) return { ok: false, error: error.message };
  if (!quotation) return { ok: true, data: { quotation: null, lines: [] } };

  const linesRes = await client
    .from("supplier_quotation_lines")
    .select("*")
    .eq("supplier_quotation_id", quotation.id)
    .order("line_no");
  if (linesRes.error) return { ok: false, error: linesRes.error.message };
  return {
    ok: true,
    data: { quotation, lines: linesRes.data ?? [] },
  };
}

export async function createRfq(
  client: SupabaseClient,
  args: {
    warehouseId: string;
    neededBy: string;
    supplierIds: string[];
    lines: RfqCreateLine[];
    notes?: string;
  },
): Promise<StorefrontResult<string>> {
  const { data, error } = await client.rpc("create_rfq", {
    p_warehouse_id: args.warehouseId,
    p_needed_by: args.neededBy,
    p_lines: args.lines,
    p_supplier_ids: args.supplierIds,
    p_notes: args.notes || undefined,
  });
  if (error) return { ok: false, error: error.message };
  if (!data) return { ok: false, error: "create_rfq returned no id." };
  return { ok: true, data };
}

export async function submitRfq(
  client: SupabaseClient,
  rfqId: string,
): Promise<StorefrontResult<string>> {
  const { data, error } = await client.rpc("submit_rfq", { p_rfq_id: rfqId });
  if (error) return { ok: false, error: error.message };
  return { ok: true, data: data ?? rfqId };
}

export async function upsertSupplierQuotation(
  client: SupabaseClient,
  args: {
    rfqId: string;
    currency: CurrencyCode;
    exchangeRate: number;
    lines: QuoteLineInput[];
    validUntil?: string;
    notes?: string;
  },
): Promise<StorefrontResult<string>> {
  const { data, error } = await client.rpc("upsert_supplier_quotation", {
    p_rfq_id: args.rfqId,
    p_currency: args.currency,
    p_exchange_rate: args.exchangeRate,
    p_lines: args.lines,
    p_valid_until: args.validUntil || undefined,
    p_notes: args.notes || undefined,
  });
  if (error) return { ok: false, error: error.message };
  if (!data) return { ok: false, error: "upsert_supplier_quotation returned no id." };
  return { ok: true, data };
}

export async function submitSupplierQuotation(
  client: SupabaseClient,
  quotationId: string,
): Promise<StorefrontResult<string>> {
  const { data, error } = await client.rpc("submit_supplier_quotation", {
    p_supplier_quotation_id: quotationId,
  });
  if (error) return { ok: false, error: error.message };
  return { ok: true, data: data ?? quotationId };
}

export async function awardQuotationToPo(
  client: SupabaseClient,
  quotationId: string,
  notes?: string,
): Promise<StorefrontResult<string>> {
  const { data, error } = await client.rpc("award_quotation_to_po", {
    p_supplier_quotation_id: quotationId,
    p_notes: notes || undefined,
  });
  if (error) return { ok: false, error: error.message };
  if (!data) return { ok: false, error: "award_quotation_to_po returned no PO id." };
  return { ok: true, data };
}
