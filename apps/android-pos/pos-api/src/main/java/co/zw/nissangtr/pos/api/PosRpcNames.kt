package co.zw.nissangtr.pos.api

/**
 * Slim POS RPC name constants — Live client wires these; Fake exercises routes for tests.
 * Not the management RpcClient god object.
 */
object PosRpcNames {
    const val SEARCH_CATALOG = "search_catalog"
    const val LIST_POS_TILL_ITEMS = "list_pos_till_items"
    const val LIST_CATALOG_MAKERS = "list_catalog_makers"
    const val LIST_CATALOG_MODELS = "list_catalog_models"
    const val LIST_CATALOG_VARIANTS = "list_catalog_variants"
    const val LIST_CATALOG_SECTIONS = "list_catalog_sections"
    const val GET_CATALOG_DIAGRAM = "get_catalog_diagram"
    const val CREATE_POS_CART = "create_pos_cart"
    const val ADD_CART_LINE = "add_cart_line"
    const val ADD_CART_LINE_FROM_QR = "add_cart_line_from_qr"
    const val PARK_POS_CART = "park_pos_cart"
    const val RESUME_POS_CART = "resume_pos_cart"
    const val VOID_POS_CART = "void_pos_cart"
    const val APPLY_POS_CART_DISCOUNT = "apply_pos_cart_discount"
    const val IS_POS_APPROVER = "is_pos_approver"
    const val CHECKOUT_POS_CART = "checkout_pos_cart"
    const val CHECKOUT_POS_CART_WITH_TENDERS = "checkout_pos_cart_with_tenders"
    const val SETTLE_INVOICE_TENDERS = "settle_invoice_tenders"
    const val CREATE_ECOCASH_INTENT = "create_ecocash_intent"
    const val CREATE_PAYNOW_INTENT = "create_paynow_intent"
    const val CREATE_CONTIPAY_INTENT = "create_contipay_intent"
    const val CREATE_POS_QUOTATION_FROM_CART = "create_pos_quotation_from_cart"
    const val SEND_POS_QUOTATION = "send_pos_quotation"
    const val CONVERT_POS_QUOTATION_TO_CART = "convert_pos_quotation_to_cart"
    const val PULL_POS_OFFLINE_SNAPSHOT = "pull_pos_offline_snapshot"
    const val REPLAY_OFFLINE_POS_SALE = "replay_offline_pos_sale"
    const val LIST_POS_QUOTATIONS = "list_pos_quotations"
    const val RESOLVE_STAFF_LOGIN_EMAIL = "resolve_staff_login_email"
    const val OPEN_ACCOUNT_PERIOD = "open_account_period"
    const val CLOSE_ACCOUNT_PERIOD = "close_account_period"
    const val POST_POS_REFUND = "post_pos_refund"
}
