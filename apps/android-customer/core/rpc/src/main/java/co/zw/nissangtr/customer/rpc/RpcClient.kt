package co.zw.nissangtr.customer.rpc

/**
 * Thin customer RPC boundary for Compose screens.
 *
 * **Live:** [SupabaseRpcClient] via [RpcClientFactory] when `SUPABASE_URL` +
 * `SUPABASE_ANON_KEY` are set (override with `rpc.forceFake=true`).
 * **Fallback:** [FakeRpcClient].
 *
 * List reads (open cart lines, own invoices, garage) use PostgREST / RLS —
 * not mutation RPCs.
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

    /** Live: SELECT open storefront cart + lines via PostgREST + RLS. */
    suspend fun getOpenCart(): CartSummary?

    suspend fun getCustomerOrder(invoiceId: String): CustomerOrder

    /** Live: SELECT sales_invoices own rows via RLS. */
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

    /** Live: SELECT customer_garage_vehicles own rows. */
    suspend fun listGarageVehicles(): List<GarageVehicle>

    suspend fun upsertCustomerGarageVehicle(input: GarageVehicleInput): String

    suspend fun deleteCustomerGarageVehicle(id: String)

    /** Live: SELECT chat_threads own rows via RLS (ordered by last_message_at). */
    suspend fun listChatThreads(): List<ChatThread>

    /** Live: SELECT chat_messages for thread via RLS. */
    suspend fun listChatMessages(threadId: String): List<ChatMessage>

    suspend fun startChatThread(input: StartChatThreadInput = StartChatThreadInput()): String

    suspend fun postChatMessage(threadId: String, body: String): String

    suspend fun markChatThreadRead(threadId: String)

    /** Unread across all threads when [threadId] is null. */
    suspend fun chatUnreadCount(threadId: String? = null): Int
}
