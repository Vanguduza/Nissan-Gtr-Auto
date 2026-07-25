package co.zw.nissangtr.management.rpc

/**
 * Canonical Postgres RPC names for management surfaces.
 * Live: [SupabaseRpcClient] → `client.postgrest.rpc(RpcNames.X, params)`.
 * Fallback: [FakeRpcClient].
 */
object RpcNames {
    // Phase 9 HR (gross payroll only — no PAYE/NSSA UI)
    const val CLOCK_ATTENDANCE = "clock_attendance"

    // Phase 5 POS (typed OEM/UUID lines — no browser QR; Bridge-First for QR later)
    const val CREATE_POS_CART = "create_pos_cart"
    const val ADD_CART_LINE = "add_cart_line"
    const val CHECKOUT_POS_CART = "checkout_pos_cart"

    // Phase 4 inventory / warehouse
    const val POST_STOCK_RECEIPT = "post_stock_receipt"
    const val CREATE_STOCK_TRANSFER = "create_stock_transfer"
    const val APPROVE_STOCK_TRANSFER = "approve_stock_transfer"
    const val REJECT_STOCK_TRANSFER = "reject_stock_transfer"

    // Phase 4b cycle count / reconciliation
    const val CREATE_STOCK_RECONCILIATION_DRAFT = "create_stock_reconciliation_draft"
    const val UPSERT_STOCK_RECONCILIATION_LINES = "upsert_stock_reconciliation_lines"
    const val SUBMIT_STOCK_RECONCILIATION = "submit_stock_reconciliation"
    const val APPROVE_STOCK_RECONCILIATION = "approve_stock_reconciliation"
    const val CANCEL_STOCK_RECONCILIATION = "cancel_stock_reconciliation"

    // Phase 10 logistics / pick-pack / DN
    const val CREATE_PICK_LIST = "create_pick_list"
    const val CONFIRM_PICK_LINES = "confirm_pick_lines"
    const val CREATE_DELIVERY_NOTE = "create_delivery_note"
    const val SUBMIT_DELIVERY_NOTE = "submit_delivery_note"
    const val CANCEL_DELIVERY_NOTE = "cancel_delivery_note"
    const val CREATE_DELIVERY_JOB = "create_delivery_job"
    const val UPDATE_DELIVERY_JOB_STATUS = "update_delivery_job_status"
    /** Bridge-only ingest (~5s). Do not call from Compose with browser geolocation. */
    const val INGEST_DELIVERY_LOCATION = "ingest_delivery_location"
}
