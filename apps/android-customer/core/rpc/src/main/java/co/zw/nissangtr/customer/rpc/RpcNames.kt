package co.zw.nissangtr.customer.rpc

/**
 * Canonical Postgres RPC names mirroring web storefront + chat helpers.
 * Live: [SupabaseRpcClient] → `client.postgrest.rpc(RpcNames.X, params)`.
 * Fallback: [FakeRpcClient].
 */
object RpcNames {
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
}
