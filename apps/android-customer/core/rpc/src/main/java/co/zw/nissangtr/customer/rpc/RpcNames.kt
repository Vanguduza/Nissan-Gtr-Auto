package co.zw.nissangtr.customer.rpc

/**
 * Canonical Postgres RPC names mirroring web storefront + chat helpers.
 * Live: [SupabaseRpcClient] → `client.postgrest.rpc(RpcNames.X, params)`.
 * Fallback: [FakeRpcClient].
 */
object RpcNames {
    /** Four-way catalog lookup — mirrors apps/web/lib/catalog-search.ts */
    const val SEARCH_CATALOG = "search_catalog"

    const val CREATE_CUSTOMER_CART = "create_customer_cart"
    const val ADD_CUSTOMER_CART_LINE = "add_customer_cart_line"
    const val CHECKOUT_CUSTOMER_CART = "checkout_customer_cart"
    const val GET_CUSTOMER_ORDER = "get_customer_order"

    const val CREATE_CUSTOMER_CONTIPAY_INTENT = "create_customer_contipay_intent"
    const val CREATE_CUSTOMER_PAYNOW_INTENT = "create_customer_paynow_intent"

    const val UPSERT_CUSTOMER_GARAGE_VEHICLE = "upsert_customer_garage_vehicle"
    const val DELETE_CUSTOMER_GARAGE_VEHICLE = "delete_customer_garage_vehicle"

    // Live chat — mirrors packages/supabase-client/src/chat.ts CHAT_RPC
    const val START_CHAT_THREAD = "start_chat_thread"
    const val POST_CHAT_MESSAGE = "post_chat_message"
    const val MARK_CHAT_THREAD_READ = "mark_chat_thread_read"
    const val CHAT_UNREAD_COUNT = "chat_unread_count"

    /**
     * Privacy-safe last point + ETA for an active (`dispatched`) job.
     * Args: `p_delivery_job_id` and/or `p_token` (share link). Never returns a trail.
     * Mirrors packages/supabase-client DELIVERY_RPC.getTrackPoint.
     */
    const val GET_DELIVERY_TRACK_POINT = "get_delivery_track_point"

    // Wishlist — mirrors apps/web/lib/customer-wishlist.ts + AuthZ migrations
    const val ADD_CUSTOMER_WISHLIST_ITEM = "add_customer_wishlist_item"
    const val REMOVE_CUSTOMER_WISHLIST_ITEM = "remove_customer_wishlist_item"
    const val SET_WISHLIST_NOTIFY_WHEN_IN_STOCK = "set_wishlist_notify_when_in_stock"
    const val WISHLIST_MOVE_TO_CART = "wishlist_move_to_cart"

    // Compare — mirrors customer-compare.ts
    const val LIST_CUSTOMER_COMPARE_ITEMS = "list_customer_compare_items"
    const val ADD_CUSTOMER_COMPARE_ITEM = "add_customer_compare_item"
    const val REMOVE_CUSTOMER_COMPARE_ITEM = "remove_customer_compare_item"

    // Reviews — mirrors customer-reviews.ts
    const val SUBMIT_CUSTOMER_PRODUCT_REVIEW = "submit_customer_product_review"
    const val GET_PRODUCT_REVIEW_STATS = "get_product_review_stats"
    const val ADD_CUSTOMER_PRODUCT_REVIEW_PHOTO = "add_customer_product_review_photo"

    /** Storage bucket for review photo object keys (`{review_id}/{uuid}.jpg`). */
    const val REVIEW_PHOTOS_BUCKET = "review-photos"

    /** Soft cap matching `_customer_compare_max_items()`. */
    const val MAX_COMPARE_ITEMS = 8
}
