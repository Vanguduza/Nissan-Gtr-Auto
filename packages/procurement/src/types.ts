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

const KNOWN_PROGRESS_STEPS = new Set<string>([
  "draft",
  "submitted",
  "approved",
  "funds_released",
  "partially_received",
  "received",
  "closed",
  "rejected",
  "cancelled",
]);

/**
 * Map PO status + fund-release + receive qty (+ optional DB progress_step) → tracker step.
 * Receive qty wins over a stale funds_released progress_step; `closed` only via progress_step
 * (status enum has no closed value yet).
 */
export function resolveProcurementProgress(input: {
  status: string;
  fundsReleasedAt?: string | null;
  qtyOrdered?: number;
  qtyReceived?: number;
  /** purchase_orders.progress_step when present */
  progressStep?: string | null;
}): ProcurementProgressStep {
  const st = input.status.toLowerCase();
  const stored = (input.progressStep ?? "").toLowerCase();

  if (st === "rejected" || stored === "rejected") return "rejected";
  if (st === "cancelled" || stored === "cancelled") return "cancelled";
  if (stored === "closed") return "closed";

  if (st === "draft") return "draft";
  if (st === "submitted") return "submitted";

  const ordered = input.qtyOrdered ?? 0;
  const received = input.qtyReceived ?? 0;
  if (ordered > 0 && received >= ordered) return "received";
  if (received > 0) return "partially_received";
  if (input.fundsReleasedAt || stored === "funds_released") return "funds_released";
  if (st === "approved" || stored === "approved") return "approved";
  if (KNOWN_PROGRESS_STEPS.has(stored)) {
    return stored as ProcurementProgressStep;
  }
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
