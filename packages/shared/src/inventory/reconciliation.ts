/** Stock reconciliation scope (Phase 4b). */
export type StockReconciliationScope = "full" | "partial";

/** Reuses stock_entry_status lifecycle for reconciliation docs. */
export type StockReconciliationStatus =
  | "draft"
  | "pending_approval"
  | "posted"
  | "cancelled"
  | "rejected";

/** app_settings key for dual-auth variance threshold (JSON: { USD, ZIG }). */
export const RECON_VARIANCE_THRESHOLD_SETTING_KEY =
  "inventory.reconciliation_variance_dual_auth_threshold";
