package co.zw.nissangtr.management.rpc

/**
 * Canonical Postgres RPC names for management surfaces.
 * Live: [SupabaseRpcClient] → `client.postgrest.rpc(RpcNames.X, params)`.
 * Fallback: [FakeRpcClient].
 */
object RpcNames {
    // Phase 9 HR (gross payroll only — no PAYE/NSSA UI)
    const val CLOCK_ATTENDANCE = "clock_attendance"

    // Phase 5 POS (typed OEM/UUID lines — Bridge-First QR via add_cart_line_from_qr)
    const val CREATE_POS_CART = "create_pos_cart"
    const val ADD_CART_LINE = "add_cart_line"
    const val ADD_CART_LINE_FROM_QR = "add_cart_line_from_qr"
    const val CHECKOUT_POS_CART = "checkout_pos_cart"
    // Optional companion pairing (cart usable with zero sessions)
    const val CREATE_POS_SCAN_SESSION = "create_pos_scan_session"
    const val CLAIM_POS_SCAN_SESSION = "claim_pos_scan_session"
    const val REVOKE_POS_SCAN_SESSION = "revoke_pos_scan_session"
    // Catalog search (standalone add-line path — no session required)
    const val SEARCH_CATALOG = "search_catalog"

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
    /**
     * Returns jsonb `{ delivery_job_id, track_token? }`.
     * `track_token` only on transition to dispatched (single mint for SMS + share).
     * Do NOT call [MINT_DELIVERY_TRACK_TOKEN] immediately after — that revokes the SMS token.
     */
    const val UPDATE_DELIVERY_JOB_STATUS = "update_delivery_job_status"
    /** Dispatcher: set pickup/dropoff for suggest ranking + Haversine ETA. */
    const val SET_DELIVERY_JOB_GEO = "set_delivery_job_geo"
    /**
     * Location ingest (~5s). **Producer is apps/android-delivery only** —
     * management must not start FGS / call this from dispatch UI.
     * Kept for Fake/Live contract parity; staff VIEW uses [GET_DELIVERY_TRACK_POINT].
     */
    const val INGEST_DELIVERY_LOCATION = "ingest_delivery_location"

    // Dedicated delivery app — dispatcher assignment / route / staff live view / panic
    const val SUGGEST_DELIVERY_ASSIGNEES = "suggest_delivery_assignees"
    const val ASSIGN_DELIVERY_JOB = "assign_delivery_job"
    const val OPTIMIZE_DRIVER_STOPS = "optimize_driver_stops"
    /** Staff/customer last-point + ETA (active dispatched job). Not a GPS producer. */
    const val GET_DELIVERY_TRACK_POINT = "get_delivery_track_point"
    /**
     * Intentional remint / rotate only. Revokes prior active tokens.
     * After Mark dispatched, use `track_token` from [UPDATE_DELIVERY_JOB_STATUS].
     */
    const val MINT_DELIVERY_TRACK_TOKEN = "mint_delivery_track_token"
    /** Dispatcher/driver: 6-digit plaintext once (hash stored). Show/read to customer. */
    const val GENERATE_DELIVERY_POD_OTP = "generate_delivery_pod_otp"
    /** Driver-only raise; management lists/acks `panic_events` via PostgREST. */
    const val RAISE_DELIVERY_PANIC = "raise_delivery_panic"

    // Live chat (staff inbox — same RPCs as web /staff/chat)
    const val CLAIM_CHAT_THREAD = "claim_chat_thread"
    const val CLOSE_CHAT_THREAD = "close_chat_thread"
    const val MARK_CHAT_THREAD_READ = "mark_chat_thread_read"
    const val POST_CHAT_MESSAGE = "post_chat_message"
    const val CHAT_UNREAD_COUNT = "chat_unread_count"
}
