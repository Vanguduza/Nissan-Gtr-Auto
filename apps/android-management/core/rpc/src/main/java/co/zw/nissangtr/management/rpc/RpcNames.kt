package co.zw.nissangtr.management.rpc

/**
 * Canonical Postgres RPC names for management surfaces.
 * Live: [SupabaseRpcClient] → `client.postgrest.rpc(RpcNames.X, params)`.
 * Fallback: [FakeRpcClient].
 */
object RpcNames {
    // Phase 9 HR (gross payroll only — no PAYE/NSSA UI)
    const val CLOCK_ATTENDANCE = "clock_attendance"
    /** Period hours for gross payroll (HR/admin or self). */
    const val ATTENDANCE_HOURS_IN_PERIOD = "attendance_hours_in_period"
    /** Manual/custom deduction only — never PAYE/NSSA/statutory. */
    const val ADD_PAYROLL_DEDUCTION = "add_payroll_deduction"
    /** Batch 1 HR onboarding — resumable draft save. */
    const val SAVE_HR_ONBOARDING_STAGE = "save_hr_onboarding_stage"
    /** Batch 1 HR onboarding — create employee + emp#. */
    const val COMPLETE_HR_ONBOARDING = "complete_hr_onboarding"
    /** Edge: Admin create/link Auth user + credential outbox (no password to client). */
    const val HR_ONBOARDING_CREATE_AUTH_FN = "hr-onboarding-create-auth"

    // Phase 5 POS (typed OEM/UUID lines — Bridge-First QR via add_cart_line_from_qr)
    const val CREATE_POS_CART = "create_pos_cart"
    const val ADD_CART_LINE = "add_cart_line"
    const val ADD_CART_LINE_FROM_QR = "add_cart_line_from_qr"
    const val CHECKOUT_POS_CART = "checkout_pos_cart"
    /** Batch 1 §1.3 — multi-tender settle after checkout. */
    const val CHECKOUT_POS_CART_WITH_TENDERS = "checkout_pos_cart_with_tenders"
    /** EcoCash direct C2B (staff) — not ContiPay/Paynow. */
    const val CREATE_ECOCASH_INTENT = "create_ecocash_intent"
    // Optional companion pairing (cart usable with zero sessions)
    const val CREATE_POS_SCAN_SESSION = "create_pos_scan_session"
    const val CLAIM_POS_SCAN_SESSION = "claim_pos_scan_session"
    const val REVOKE_POS_SCAN_SESSION = "revoke_pos_scan_session"
    // Catalog search (standalone add-line path — no session required)
    const val SEARCH_CATALOG = "search_catalog"
    /** Megazip hierarchy browse — online-only; offline POS cache stays flat. */
    const val LIST_CATALOG_MAKERS = "list_catalog_makers"
    const val LIST_CATALOG_MODELS = "list_catalog_models"
    const val LIST_CATALOG_VARIANTS = "list_catalog_variants"
    const val LIST_CATALOG_SECTIONS = "list_catalog_sections"
    const val GET_CATALOG_DIAGRAM = "get_catalog_diagram"
    /** Organogram module_access for signed-in employee (Batch 1 §1.6). */
    const val MY_MODULE_ACCESS = "my_module_access"
    /** Optional hr_roles.default_landing (pos|hub). */
    const val MY_DEFAULT_LANDING = "my_default_landing"

    /** Park / resume open cart (Batch 1). */
    const val PARK_POS_CART = "park_pos_cart"
    const val RESUME_POS_CART = "resume_pos_cart"

    /** Admin|shop-manager POS actions (tablet Phase 5). */
    const val IS_POS_APPROVER = "is_pos_approver"
    const val APPLY_POS_CART_DISCOUNT = "apply_pos_cart_discount"
    const val APPLY_POS_LINE_PRICE_OVERRIDE = "apply_pos_line_price_override"
    const val VOID_POS_CART = "void_pos_cart"
    /** Counter refund — always posts through finance pipeline. */
    const val POST_POS_REFUND = "post_pos_refund"
    const val POST_FINANCE_REFUND = "post_finance_refund"

    /** Offline POS: pull retail catalog + warehouse stock snapshot. */
    const val PULL_POS_OFFLINE_SNAPSHOT = "pull_pos_offline_snapshot"
    /** Offline POS: idempotent replay of queued cash sale. */
    const val REPLAY_OFFLINE_POS_SALE = "replay_offline_pos_sale"

    /** Customer POS quotations (create / send / convert). */
    const val CREATE_POS_QUOTATION_FROM_CART = "create_pos_quotation_from_cart"
    const val SEND_POS_QUOTATION = "send_pos_quotation"
    const val CONVERT_POS_QUOTATION_TO_CART = "convert_pos_quotation_to_cart"
    const val LIST_POS_QUOTATIONS = "list_pos_quotations"

    /** Pre-auth staff identifier → GoTrue email (emp#|email|phone). */
    const val RESOLVE_STAFF_LOGIN_EMAIL = "resolve_staff_login_email"
    /** Password attempt lockout helpers (anon-safe, hashed identifier). */
    const val STAFF_LOGIN_IS_LOCKED = "staff_login_is_locked"
    const val RECORD_STAFF_LOGIN_ATTEMPT = "record_staff_login_attempt"

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

    // Phase 8b procurement / blankets + preferred manual PO
    const val CREATE_PURCHASE_ORDER = "create_purchase_order"
    const val CREATE_BLANKET_PURCHASE_ORDER = "create_blanket_purchase_order"
    const val CREATE_BLANKET_RELEASE = "create_blanket_release"
    const val SUBMIT_PURCHASE_ORDER = "submit_purchase_order"

    // Phase 16 warehouse bins / pick-path
    const val CREATE_WAREHOUSE_BIN = "create_warehouse_bin"
    const val UPDATE_WAREHOUSE_BIN = "update_warehouse_bin"
    const val DEACTIVATE_WAREHOUSE_BIN = "deactivate_warehouse_bin"
    const val SET_STOCK_LEVEL_BIN = "set_stock_level_bin"
    const val GET_PICK_PATH_HINTS = "get_pick_path_hints"

    // Phase 16 consignment
    const val CREATE_CONSIGNMENT_ENTRY_DRAFT = "create_consignment_entry_draft"
    const val ADD_CONSIGNMENT_ENTRY_LINE = "add_consignment_entry_line"
    const val SUBMIT_CONSIGNMENT_ENTRY = "submit_consignment_entry"
    const val CANCEL_CONSIGNMENT_ENTRY = "cancel_consignment_entry"

    // B2B credit (staff DEFINER — admin|sales|finance)
    const val SET_CUSTOMER_CREDIT = "set_customer_credit"

    // CRM product pages / kits (admin|sales|warehouse)
    const val LIST_STAFF_PRODUCT_PAGES = "list_staff_product_pages"
    const val UPSERT_STAFF_PRODUCT_PAGE = "upsert_staff_product_page"
    const val REGISTER_STOCK_ITEM_IMAGE = "register_stock_item_image"
    const val SET_STOCK_ITEM_PRIMARY_IMAGE = "set_stock_item_primary_image"
    const val CREATE_KIT_WITH_COMPONENTS = "create_kit_with_components"
    const val UPDATE_ITEM_KIT = "update_item_kit"
    const val PRODUCT_IMAGES_BUCKET = "product-images"

    // Company fleet (ops vehicles — not B2B FLEET price list / garage)
    const val LIST_FLEET_VEHICLES = "list_fleet_vehicles"
    const val UPSERT_FLEET_VEHICLE = "upsert_fleet_vehicle"
    const val SET_FLEET_VEHICLE_STATUS = "set_fleet_vehicle_status"
}
