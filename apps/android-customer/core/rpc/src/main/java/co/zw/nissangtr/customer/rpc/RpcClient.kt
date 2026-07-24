package co.zw.nissangtr.customer.rpc

/**
 * Thin customer RPC boundary for Compose screens.
 *
 * **Live binding (TODO):** replace [FakeRpcClient] with a Supabase Kotlin implementation:
 * ```
 * client.postgrest.rpc(RpcNames.CREATE_CUSTOMER_CART, mapOf(
 *   "p_warehouse_id" to warehouseId,
 *   "p_currency" to currency.rpcValue,
 *   "p_fulfillment_mode" to fulfillmentMode.rpcValue,
 *   "p_exchange_rate" to exchangeRate,
 * )).decodeAs<String>()
 * ```
 * List reads (open cart lines, own invoices, garage) use PostgREST / RLS —
 * not mutation RPCs — once supabase-kt is wired.
 *
 * Payment intents: create only (no real PSP crypto). Settle stays webhook.
 * QR: Bridge-First only (`bridges/android/`) — never HTML5 / WebView.
 */
interface RpcClient {
    suspend fun createCustomerCart(
        warehouseId: String,
        currency: CurrencyCode = CurrencyCode.USD,
        fulfillmentMode: FulfillmentMode = FulfillmentMode.IMMEDIATE,
        exchangeRate: Double = 1.0,
    ): String

    suspend fun addCustomerCartLine(
        cartId: String,
        stockItemId: String,
        uomId: String,
        qty: Double,
    ): String

    suspend fun checkoutCustomerCart(cartId: String): String

    /** Scaffold helper — live: SELECT open storefront cart + lines. */
    suspend fun getOpenCart(): CartSummary?

    suspend fun getCustomerOrder(invoiceId: String): CustomerOrder

    /** Scaffold list — live: SELECT sales_invoices own rows via RLS. */
    suspend fun listOwnInvoices(): List<InvoiceSummary>

    suspend fun createCustomerContipayIntent(
        salesInvoiceId: String,
        method: ContipayMethod = ContipayMethod.ECOCASH,
        metadataJson: String = "{}",
    ): PaymentIntentResult

    suspend fun createCustomerPaynowIntent(
        salesInvoiceId: String,
        method: PaynowMethod = PaynowMethod.ECOCASH,
        metadataJson: String = "{}",
    ): PaymentIntentResult

    /** Scaffold list — live: SELECT customer_garage_vehicles own rows. */
    suspend fun listGarageVehicles(): List<GarageVehicle>

    suspend fun upsertCustomerGarageVehicle(input: GarageVehicleInput): String

    suspend fun deleteCustomerGarageVehicle(id: String)
}
