/**
 * Relationship-based procurement domain (founder 2026-08-12).
 * Pattern vocabulary inspired by InvenTree PO/GRN (MIT) — Postgres remains SoR.
 * RFQ-win is NOT required to authorize a supplier on a PO.
 */

export type ProcurementProgressStep =
  | "draft"
  | "submitted"
  | "approved"
  | "funds_released"
  | "partially_received"
  | "received"
  | "closed"
  | "rejected"
  | "cancelled";

/** Ordered happy-path steps for UI progress trackers (excludes terminal rejects). */
export const PROCUREMENT_TRACKER_STEPS: readonly ProcurementProgressStep[] = [
  "draft",
  "submitted",
  "approved",
  "funds_released",
  "partially_received",
  "received",
  "closed",
] as const;

export const PROCUREMENT_STEP_LABELS: Record<ProcurementProgressStep, string> = {
  draft: "Draft",
  submitted: "Submitted",
  approved: "Approved",
  funds_released: "Funds released",
  partially_received: "Partially received",
  received: "Received",
  closed: "Closed",
  rejected: "Rejected",
  cancelled: "Cancelled",
};

export type WarehouseRoleCode = "WH1" | "WH2";

export const WAREHOUSE_ROLE: Record<
  WarehouseRoleCode,
  { label: string; purpose: string }
> = {
  WH1: {
    label: "Warehouse 1 — Receiving",
    purpose: "All goods received from suppliers land here first",
  },
  WH2: {
    label: "Warehouse 2 — Storefloor",
    purpose: "Sales floor / POS pick location",
  },
};

export type PreferredSupplierInput = {
  code: string;
  name: string;
  email?: string | null;
  phoneE164?: string | null;
  defaultCurrency?: "USD" | "ZIG";
  notes?: string | null;
  productCategories?: string[];
};

export type GrnFastLine = {
  /** Maps directly to stock_items.oem_part_number */
  oemPartNumber: string;
  qtyReceived: number;
  unitCostQuoted?: number;
};

export type MasterStockRow = {
  stockItemId: string;
  oemPartNumber: string;
  description: string | null;
  qtyTotal: number;
  qtyWh1: number;
  qtyWh2: number;
};

/**
 * Map legacy PO/status + fund-release flag → tracker step.
 */
export function resolveProcurementProgress(input: {
  status: string;
  fundsReleasedAt?: string | null;
  qtyOrdered?: number;
  qtyReceived?: number;
}): ProcurementProgressStep {
  const st = input.status.toLowerCase();
  if (st === "rejected") return "rejected";
  if (st === "cancelled") return "cancelled";
  if (st === "draft") return "draft";
  if (st === "submitted") return "submitted";

  const ordered = input.qtyOrdered ?? 0;
  const received = input.qtyReceived ?? 0;
  if (ordered > 0 && received >= ordered) return "received";
  if (received > 0) return "partially_received";
  if (input.fundsReleasedAt) return "funds_released";
  if (st === "approved") return "approved";
  return "draft";
}

export function trackerIndex(step: ProcurementProgressStep): number {
  const i = PROCUREMENT_TRACKER_STEPS.indexOf(step as (typeof PROCUREMENT_TRACKER_STEPS)[number]);
  return i < 0 ? -1 : i;
}

/** Steps completed for a cool progress bar (0..n). */
export function completedTrackerCount(step: ProcurementProgressStep): number {
  const i = trackerIndex(step);
  if (i < 0) return 0;
  return i + 1;
}
