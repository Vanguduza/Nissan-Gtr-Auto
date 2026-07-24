package co.zw.nissangtr.customer.rpc

/**
 * Canonical Postgres RPC names mirroring web `apps/web/lib/customer-storefront.ts`.
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
}
