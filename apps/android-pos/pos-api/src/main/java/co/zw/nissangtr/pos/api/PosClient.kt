package co.zw.nissangtr.pos.api

/**
 * Slim till API — Fake for tests/chrome; Live wires [PosRpcNames] when credentials exist.
 */
interface PosClient {
    fun fakeTillState(): TillFakeState

    suspend fun searchCatalog(
        mode: CatalogSearchMode,
        query: String,
    ): CatalogSearchResponse

    suspend fun listTillItems(request: ListTillItemsRequest): List<TillItem>

    suspend fun listCatalogMakers(): List<CatalogMaker>

    suspend fun listCatalogModels(makerId: String): List<CatalogModel>

    suspend fun listCatalogVariants(modelId: String): List<CatalogVariant>

    suspend fun listCatalogSections(variantId: String): List<CatalogSection>

    suspend fun createCart(warehouseId: String, customerId: String? = null): String

    suspend fun addCartLine(
        cartId: String,
        item: TillItem,
        qty: Int,
        quoteOnly: Boolean,
    ): TicketSnapshot

    suspend fun parkCart(cartId: String): ParkedCartRef

    suspend fun resumeCart(cartId: String): TicketSnapshot

    suspend fun voidCart(cartId: String, managerPin: String? = null): TicketSnapshot

    suspend fun applyCartDiscount(
        cartId: String,
        percent: Int,
        managerPin: String? = null,
    ): TicketSnapshot

    /** create_pos_quotation_from_cart → park → new cart. */
    suspend fun createQuotationAndPark(cartId: String): QuoteParkResult

    suspend fun listCustomers(query: String = ""): List<CustomerRef>

    suspend fun bindCustomer(cartId: String, customerId: String?): TicketSnapshot

    /**
     * Live rail intent per tender slice. Fake can settle or fail.
     * Failure must not proceed to checkout.
     */
    suspend fun createLiveRailIntent(
        mode: TenderMode,
        amountCents: Long,
        currency: String,
        externalRef: String,
        payerMsisdn: String? = null,
    ): LiveRailIntent

    suspend fun settleLiveRailIntent(intentId: String): LiveRailIntent

    /**
     * `checkout_pos_cart_with_tenders` — [tenders] must be **applied** amounts only
     * (cash change never posted). Returns on_hold without treating as paid.
     */
    suspend fun checkoutWithTenders(
        cartId: String,
        tenders: List<TenderRpcLine>,
        receipt: CheckoutReceiptContacts = CheckoutReceiptContacts(),
    ): CheckoutResult

    fun listParkedCarts(): List<ParkedCartRef>

    fun listQuotations(): List<QuotationRef> = emptyList()

    suspend fun convertQuotationToCart(quotationId: String): TicketSnapshot {
        error("convertQuotationToCart not implemented")
    }

    /** Offline snapshot — same TillItem shape as list_pos_till_items. */
    suspend fun pullOfflineSnapshot(warehouseId: String): OfflineSnapshotDto

    /**
     * Idempotent offline sale replay. Throws [OfflineReplayConflict] on price/stock conflict.
     */
    suspend fun replayOfflineSale(
        clientSaleId: String,
        payloadJson: String,
    ): OfflineReplayResult

    /** Distinct chassis codes for shortcut chips (vehicle_master / catalog). */
    suspend fun listChassisShortcuts(): List<ChassisShortcut> = emptyList()

    /** Open cash-sales till float period — account **1120** only. */
    suspend fun openTillFloat(request: OpenTillFloatRequest): TillFloatPeriod {
        error("openTillFloat not implemented")
    }

    /** Close till float with physical count. */
    suspend fun closeTillFloat(request: CloseTillFloatRequest): TillFloatPeriod {
        error("closeTillFloat not implemented")
    }

    /**
     * `post_pos_refund` — server routes stock to quarantine WH.
     * Never direct exchange / WH2 restock from the client.
     */
    suspend fun postPosRefund(invoiceId: String, reason: String?): String {
        error("postPosRefund not implemented")
    }
}

data class OfflineSnapshotDto(
    val warehouseId: String,
    val pulledAt: String,
    val currency: String = "USD",
    val items: List<TillItem> = emptyList(),
)

data class OfflineReplayResult(
    val invoiceId: String,
    val duplicate: Boolean = false,
)

class OfflineReplayConflict(
    val code: String,
    message: String,
) : Exception(message)

data class QuoteParkResult(
    val quotation: QuotationRef,
    val ticket: TicketSnapshot,
    val newCartId: String,
)
