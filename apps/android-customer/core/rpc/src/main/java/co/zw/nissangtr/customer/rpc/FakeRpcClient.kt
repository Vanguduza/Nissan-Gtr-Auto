package co.zw.nissangtr.customer.rpc

import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

/**
 * In-memory stub so cart / orders / garage / pay / chat screens compile and exercise
 * flows without a configured Supabase project. Live: [SupabaseRpcClient] via [RpcClientFactory].
 *
 * Documented live RPC → param map (mirrors web + migration):
 * - [RpcNames.CREATE_CUSTOMER_CART]: p_warehouse_id, p_currency?, p_fulfillment_mode?, p_exchange_rate?
 * - [RpcNames.ADD_CUSTOMER_CART_LINE]: p_cart_id, p_stock_item_id, p_uom_id, p_qty
 * - [RpcNames.CHECKOUT_CUSTOMER_CART]: p_cart_id
 * - [RpcNames.GET_CUSTOMER_ORDER]: p_invoice_id
 * - [RpcNames.CREATE_CUSTOMER_CONTIPAY_INTENT]: p_sales_invoice_id, p_method, p_metadata?
 * - [RpcNames.CREATE_CUSTOMER_PAYNOW_INTENT]: p_sales_invoice_id, p_method, p_metadata?
 * - [RpcNames.UPSERT_CUSTOMER_GARAGE_VEHICLE]: p_id?, p_make?, p_model?, p_generation?, p_engine?, p_vin?, p_is_primary?
 * - [RpcNames.DELETE_CUSTOMER_GARAGE_VEHICLE]: p_id
 * - [RpcNames.START_CHAT_THREAD]: p_kind?, p_subject?, p_body?
 * - [RpcNames.POST_CHAT_MESSAGE]: p_thread_id, p_body
 * - [RpcNames.MARK_CHAT_THREAD_READ]: p_thread_id
 * - [RpcNames.CHAT_UNREAD_COUNT]: p_thread_id?
 * - [RpcNames.GET_DELIVERY_TRACK_POINT]: p_delivery_job_id?, p_token?
 * - [RpcNames.ADD_CUSTOMER_WISHLIST_ITEM]: p_stock_item_id?, p_oem_part_number?
 * - [RpcNames.REMOVE_CUSTOMER_WISHLIST_ITEM]: p_wishlist_id?, p_stock_item_id?, p_oem_part_number?
 * - [RpcNames.SET_WISHLIST_NOTIFY_WHEN_IN_STOCK]: p_notify, p_wishlist_id?, …
 * - [RpcNames.WISHLIST_MOVE_TO_CART]: p_cart_id, p_qty?, p_remove_from_wishlist?, …
 * - [RpcNames.LIST_CUSTOMER_COMPARE_ITEMS] / ADD / REMOVE
 * - [RpcNames.SUBMIT_CUSTOMER_PRODUCT_REVIEW] / GET_PRODUCT_REVIEW_STATS / ADD_…_PHOTO
 *
 * No PSP secrets or crypto here — intent UUID only.
 */
class FakeRpcClient : RpcClient {
    private val invSeq = AtomicInteger(1)
    private var openCart: CartSummary? = null
    private val invoices = mutableListOf(
        InvoiceSummary(
            id = "00000000-0000-4000-8000-0000000000i1",
            documentNumber = "INV-SEED-001",
            status = "posted",
            currency = CurrencyCode.USD,
            total = 42.0,
            amountPaid = 0.0,
        ),
        InvoiceSummary(
            id = SEED_DISPATCH_INVOICE_ID,
            documentNumber = "INV-SEED-DISPATCH",
            status = "posted",
            currency = CurrencyCode.USD,
            total = 88.0,
            amountPaid = 88.0,
        ),
    )
    private val orders = mutableMapOf<String, CustomerOrder>()
    private val garage = mutableListOf(
        GarageVehicle(
            id = "00000000-0000-4000-8000-0000000000g1",
            make = "Nissan",
            model = "GT-R",
            generation = "R35",
            engine = "VR38DETT",
            vin = null,
            isPrimary = true,
        ),
    )
    private val chatThreads = mutableListOf<ChatThread>()
    private val chatMessages = mutableListOf<ChatMessage>()
    private val chatLastRead = mutableMapOf<String, String>()
    private val fakeUserId = "00000000-0000-4000-8000-0000000000cu"
    private val wishlist = mutableListOf(
        WishlistItem(
            id = "00000000-0000-4000-8000-0000000000b1",
            stockItemId = SEED_OIL_FILTER_ID,
            oemPartNumber = "15208-65F0C",
            description = "Oil filter (demo)",
            notifyWhenInStock = false,
            createdAt = "2026-07-25T10:00:00Z",
        ),
        WishlistItem(
            id = "00000000-0000-4000-8000-0000000000b2",
            stockItemId = SEED_AIR_FILTER_ID,
            oemPartNumber = "16546-EB70A",
            description = "Air cleaner element (demo)",
            notifyWhenInStock = true,
            createdAt = "2026-07-25T09:00:00Z",
        ),
    )
    private val compare = mutableListOf(
        CompareItem(
            id = "00000000-0000-4000-8000-0000000000c1",
            stockItemId = SEED_OIL_FILTER_ID,
            oemPartNumber = "15208-65F0C",
            description = "Oil filter (demo)",
            createdAt = "2026-07-25T10:00:00Z",
        ),
    )
    private val reviews = mutableListOf(
        ProductReview(
            id = "00000000-0000-4000-8000-0000000000r1",
            stockItemId = SEED_OIL_FILTER_ID,
            oemPartNumber = "15208-65F0C",
            description = "Oil filter (demo)",
            rating = 5,
            body = "Fits my Navara — approved demo review.",
            status = ProductReviewStatus.APPROVED,
            createdAt = "2026-07-24T10:00:00Z",
        ),
        ProductReview(
            id = "00000000-0000-4000-8000-0000000000r2",
            stockItemId = SEED_AIR_FILTER_ID,
            oemPartNumber = "16546-EB70A",
            description = "Air cleaner element (demo)",
            rating = 4,
            body = "Pending demo review.",
            status = ProductReviewStatus.PENDING,
            createdAt = "2026-07-25T11:00:00Z",
        ),
    )
    private val reviewPhotoCounts = mutableMapOf<String, Int>()

    /** Fake active job → last point (single row only; no trail). Nudged on each poll. */
    private var fakeTrackPoint: DeliveryTrackPoint? = seedTrackPoint()
    private val trackTick = AtomicInteger(0)

    private fun seedTrackPoint() = DeliveryTrackPoint(
        deliveryJobId = SEED_ACTIVE_JOB_ID,
        lat = -17.8292,
        lng = 31.0522,
        recordedAt = "2026-07-25T09:10:00Z",
        etaAt = "2026-07-25T09:11:00Z",
        // ~6 polls at 8s → terminal demo without waiting half an hour.
        etaSeconds = 48,
        status = "dispatched",
    )

    init {
        val seed = invoices.first()
        orders[seed.id] = CustomerOrder(
            invoiceId = seed.id,
            documentNumber = seed.documentNumber,
            docType = "invoice",
            status = seed.status,
            fulfillmentMode = FulfillmentMode.IMMEDIATE,
            currency = seed.currency,
            exchangeRateApplied = 1.0,
            subtotal = 40.0,
            total = seed.total,
            amountPaid = seed.amountPaid,
            amountOpen = seed.total - seed.amountPaid,
            cartId = null,
            postedAt = "2026-07-24T00:00:00Z",
            pickListStatus = null,
            deliveryNoteStatus = null,
        )
        val dispatch = invoices[1]
        orders[dispatch.id] = CustomerOrder(
            invoiceId = dispatch.id,
            documentNumber = dispatch.documentNumber,
            docType = "invoice",
            status = dispatch.status,
            fulfillmentMode = FulfillmentMode.DISPATCH,
            currency = dispatch.currency,
            exchangeRateApplied = 1.0,
            subtotal = 80.0,
            total = dispatch.total,
            amountPaid = dispatch.amountPaid,
            amountOpen = 0.0,
            cartId = null,
            postedAt = "2026-07-25T08:00:00Z",
            pickListStatus = "completed",
            deliveryNoteStatus = "submitted",
            activeDeliveryJobId = SEED_ACTIVE_JOB_ID,
        )
    }

    companion object {
        const val SEED_DISPATCH_INVOICE_ID = "00000000-0000-4000-8000-0000000000i2"
        const val SEED_ACTIVE_JOB_ID = "00000000-0000-4000-8000-0000000000dj"
        /** Demo share token (SMS `/track/{token}`). Not a secret. */
        const val SEED_TRACK_TOKEN = "fake_customer_track_token_demo_00000001"
        const val SEED_OIL_FILTER_ID = "00000000-0000-4000-8000-0000000000a1"
        const val SEED_AIR_FILTER_ID = "00000000-0000-4000-8000-0000000000a2"
        const val SEED_WAREHOUSE_ID = "00000000-0000-4000-8000-0000000000w1"
        const val SEED_UOM_ID = "00000000-0000-4000-8000-0000000000u1"

        private fun seedCatalogProducts(): List<CatalogProduct> = listOf(
            CatalogProduct(
                stockItemId = SEED_OIL_FILTER_ID,
                baseUomId = SEED_UOM_ID,
                oem = "15208-65F0C",
                name = "Oil filter (demo)",
                brand = "Nissan",
                category = "Filters",
                usd = 12.50,
                stock = StockState.IN_STOCK,
                coreCharge = 0.0,
            ),
            CatalogProduct(
                stockItemId = SEED_AIR_FILTER_ID,
                baseUomId = SEED_UOM_ID,
                oem = "16546-EB70A",
                name = "Air cleaner element (demo)",
                brand = "Nissan",
                category = "Filters",
                usd = 28.00,
                stock = StockState.LOW,
                coreCharge = 0.0,
            ),
        )
    }

    private val catalogProducts = seedCatalogProducts().associateBy { it.oem.uppercase() }

    override suspend fun searchCatalog(mode: SearchMode, query: String): SearchCatalogResponse {
        val q = query.trim()
        require(q.isNotEmpty()) { "search query required" }
        val needle = q.uppercase()
        val hits = catalogProducts.values
            .filter { p ->
                when (mode) {
                    SearchMode.PART -> p.oem.uppercase().contains(needle) ||
                        p.name.uppercase().contains(needle)
                    SearchMode.VIN -> needle.startsWith("JN") || p.oem.contains("15208")
                    SearchMode.MODEL -> p.name.uppercase().contains(needle) ||
                        needle.contains("NAVARA", ignoreCase = true)
                    SearchMode.PNC -> p.category?.uppercase()?.contains(needle) == true ||
                        needle.contains("FILTER")
                }
            }
            .map {
                CatalogPartHit(
                    oemPartNumber = it.oem,
                    categoryName = it.category,
                )
            }
        return SearchCatalogResponse(mode = mode, query = q, parts = hits)
    }

    override suspend fun listCatalogBrowse(category: String?, limit: Int): CatalogBrowseResult {
        val cap = limit.coerceIn(1, 100)
        val cat = category?.trim()?.lowercase()
        val items = catalogProducts.values
            .filter { cat == null || it.category?.lowercase()?.contains(cat) == true }
            .take(cap)
            .map {
                CatalogListItem(
                    stockItemId = it.stockItemId,
                    oem = it.oem,
                    name = it.name,
                    stock = it.stock,
                    usd = it.usd,
                    category = it.category,
                )
            }
        return CatalogBrowseResult(
            items = items,
            categories = listOf("Filters", "Brakes", "Engine"),
        )
    }

    override suspend fun loadCatalogProduct(oem: String): CatalogProduct {
        val key = oem.trim().uppercase()
        return catalogProducts[key]
            ?: catalogProducts.values.firstOrNull { it.oem.equals(oem, ignoreCase = true) }
            ?: error("Part not found: $oem")
    }

    override suspend fun addCustomerCartLineByOem(oem: String, qty: Double): Pair<String, String> {
        require(qty > 0) { "qty must be > 0" }
        val product = loadCatalogProduct(oem)
        val cartId = openCart?.id ?: createCustomerCart(
            warehouseId = SEED_WAREHOUSE_ID,
            currency = CurrencyCode.USD,
            fulfillmentMode = FulfillmentMode.IMMEDIATE,
            exchangeRate = 1.0,
        )
        val lineId = addCustomerCartLine(
            cartId = cartId,
            stockItemId = product.stockItemId,
            uomId = product.baseUomId,
            qty = qty,
        )
        return cartId to lineId
    }

    override suspend fun createCustomerCart(
        warehouseId: String,
        currency: CurrencyCode,
        fulfillmentMode: FulfillmentMode,
        exchangeRate: Double,
    ): String {
        require(warehouseId.isNotBlank()) { "warehouseId required for ${RpcNames.CREATE_CUSTOMER_CART}" }
        require(exchangeRate > 0) { "exchangeRate must be > 0" }
        val id = UUID.randomUUID().toString()
        openCart = CartSummary(
            id = id,
            currency = currency,
            fulfillmentMode = fulfillmentMode,
            status = "open",
        )
        // TODO(live): supabase.rpc(RpcNames.CREATE_CUSTOMER_CART, …)
        return id
    }

    override suspend fun addCustomerCartLine(
        cartId: String,
        stockItemId: String,
        uomId: String,
        qty: Double,
    ): String {
        require(qty > 0) { "qty must be > 0" }
        val cart = openCart
        require(cart != null && cart.id == cartId && cart.status == "open") {
            "open cart $cartId required for ${RpcNames.ADD_CUSTOMER_CART_LINE}"
        }
        val lineId = UUID.randomUUID().toString()
        openCart = cart.copy(
            lines = cart.lines + CartLineSummary(
                id = lineId,
                stockItemId = stockItemId,
                uomId = uomId,
                qty = qty,
                oemPartNumber = "FAKE-OEM",
            ),
        )
        // TODO(live): supabase.rpc(RpcNames.ADD_CUSTOMER_CART_LINE, …)
        return lineId
    }

    override suspend fun checkoutCustomerCart(cartId: String): String {
        val cart = openCart
        require(cart != null && cart.id == cartId && cart.status == "open") {
            "open cart $cartId required for ${RpcNames.CHECKOUT_CUSTOMER_CART}"
        }
        require(cart.lines.isNotEmpty()) { "cart has no lines" }
        val invId = UUID.randomUUID().toString()
        val n = invSeq.getAndIncrement()
        val total = cart.lines.sumOf { it.qty } * 10.0
        val summary = InvoiceSummary(
            id = invId,
            documentNumber = "INV-FAKE-%03d".format(n),
            status = "posted",
            currency = cart.currency,
            total = total,
            amountPaid = 0.0,
        )
        invoices.add(0, summary)
        orders[invId] = CustomerOrder(
            invoiceId = invId,
            documentNumber = summary.documentNumber,
            docType = "invoice",
            status = "posted",
            fulfillmentMode = cart.fulfillmentMode,
            currency = cart.currency,
            exchangeRateApplied = if (cart.currency == CurrencyCode.USD) 1.0 else 1.0,
            subtotal = total,
            total = total,
            amountPaid = 0.0,
            amountOpen = total,
            cartId = cartId,
            postedAt = "2026-07-24T12:00:00Z",
            pickListStatus = null,
            deliveryNoteStatus = null,
        )
        openCart = cart.copy(status = "checked_out")
        // TODO(live): supabase.rpc(RpcNames.CHECKOUT_CUSTOMER_CART, …)
        return invId
    }

    override suspend fun getOpenCart(): CartSummary? =
        openCart?.takeIf { it.status == "open" }

    override suspend fun getCustomerOrder(invoiceId: String): CustomerOrder {
        require(invoiceId.isNotBlank())
        // TODO(live): supabase.rpc(RpcNames.GET_CUSTOMER_ORDER, …)
        return orders[invoiceId]
            ?: error("order not found for ${RpcNames.GET_CUSTOMER_ORDER}")
    }

    override suspend fun listOwnInvoices(): List<InvoiceSummary> =
        invoices.toList()

    override suspend fun createCustomerContipayIntent(
        salesInvoiceId: String,
        method: ContipayMethod,
        metadataJson: String,
    ): PaymentIntentResult {
        val inv = invoices.find { it.id == salesInvoiceId }
            ?: error("invoice required for ${RpcNames.CREATE_CUSTOMER_CONTIPAY_INTENT}")
        require(inv.status == "posted" && inv.total > inv.amountPaid) {
            "only unpaid posted invoices"
        }
        val id = UUID.randomUUID().toString()
        // TODO(live): supabase.rpc(RpcNames.CREATE_CUSTOMER_CONTIPAY_INTENT, …) — no PSP crypto
        return PaymentIntentResult(intentId = id, provider = "contipay")
    }

    override suspend fun createCustomerPaynowIntent(
        salesInvoiceId: String,
        method: PaynowMethod,
        metadataJson: String,
    ): PaymentIntentResult {
        val inv = invoices.find { it.id == salesInvoiceId }
            ?: error("invoice required for ${RpcNames.CREATE_CUSTOMER_PAYNOW_INTENT}")
        require(inv.status == "posted" && inv.total > inv.amountPaid) {
            "only unpaid posted invoices"
        }
        val id = UUID.randomUUID().toString()
        // TODO(live): supabase.rpc(RpcNames.CREATE_CUSTOMER_PAYNOW_INTENT, …) — no PSP crypto
        return PaymentIntentResult(intentId = id, provider = "paynow")
    }

    override suspend fun listGarageVehicles(): List<GarageVehicle> =
        garage.sortedWith(
            compareByDescending<GarageVehicle> { it.isPrimary }
                .thenByDescending { it.id },
        )

    override suspend fun upsertCustomerGarageVehicle(input: GarageVehicleInput): String {
        val hasFitment = listOf(input.make, input.model, input.generation, input.engine, input.vin)
            .any { !it.isNullOrBlank() }
        require(hasFitment) { "vin or fitment required for ${RpcNames.UPSERT_CUSTOMER_GARAGE_VEHICLE}" }
        val id = input.id?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()
        if (input.isPrimary) {
            for (i in garage.indices) {
                garage[i] = garage[i].copy(isPrimary = false)
            }
        }
        val idx = garage.indexOfFirst { it.id == id }
        val row = GarageVehicle(
            id = id,
            make = input.make,
            model = input.model,
            generation = input.generation,
            engine = input.engine,
            vin = input.vin,
            isPrimary = input.isPrimary,
        )
        if (idx >= 0) garage[idx] = row else garage.add(0, row)
        // TODO(live): supabase.rpc(RpcNames.UPSERT_CUSTOMER_GARAGE_VEHICLE, …)
        return id
    }

    override suspend fun deleteCustomerGarageVehicle(id: String) {
        val removed = garage.removeAll { it.id == id }
        require(removed) { "garage vehicle not found for ${RpcNames.DELETE_CUSTOMER_GARAGE_VEHICLE}" }
        // TODO(live): supabase.rpc(RpcNames.DELETE_CUSTOMER_GARAGE_VEHICLE, …)
    }

    override suspend fun listChatThreads(): List<ChatThread> =
        chatThreads.sortedByDescending { it.lastMessageAt ?: it.createdAt }

    override suspend fun listChatMessages(threadId: String): List<ChatMessage> {
        require(threadId.isNotBlank())
        require(chatThreads.any { it.id == threadId }) {
            "thread not found for listChatMessages"
        }
        return chatMessages.filter { it.threadId == threadId }.sortedBy { it.createdAt }
    }

    override suspend fun startChatThread(input: StartChatThreadInput): String {
        val id = UUID.randomUUID().toString()
        val now = "2026-07-25T12:00:00Z"
        val thread = ChatThread(
            id = id,
            customerUserId = fakeUserId,
            kind = input.kind,
            status = ChatThreadStatus.OPEN,
            subject = input.subject?.trim()?.takeIf { it.isNotEmpty() },
            lastMessageAt = if (!input.body.isNullOrBlank()) now else null,
            createdAt = now,
        )
        chatThreads.add(0, thread)
        val body = input.body?.trim()
        if (!body.isNullOrEmpty()) {
            chatMessages.add(
                ChatMessage(
                    id = UUID.randomUUID().toString(),
                    threadId = id,
                    senderUserId = fakeUserId,
                    senderKind = ChatSenderKind.CUSTOMER,
                    body = body,
                    createdAt = now,
                ),
            )
        }
        // TODO(live): supabase.rpc(RpcNames.START_CHAT_THREAD, …)
        return id
    }

    override suspend fun postChatMessage(threadId: String, body: String): String {
        val trimmed = body.trim()
        require(trimmed.isNotEmpty()) { "body required for ${RpcNames.POST_CHAT_MESSAGE}" }
        val idx = chatThreads.indexOfFirst { it.id == threadId }
        require(idx >= 0) { "thread not found for ${RpcNames.POST_CHAT_MESSAGE}" }
        val thread = chatThreads[idx]
        require(thread.status != ChatThreadStatus.CLOSED) { "thread closed" }
        val now = "2026-07-25T12:01:00Z"
        val msgId = UUID.randomUUID().toString()
        chatMessages.add(
            ChatMessage(
                id = msgId,
                threadId = threadId,
                senderUserId = fakeUserId,
                senderKind = ChatSenderKind.CUSTOMER,
                body = trimmed,
                createdAt = now,
            ),
        )
        chatThreads[idx] = thread.copy(lastMessageAt = now)
        // TODO(live): supabase.rpc(RpcNames.POST_CHAT_MESSAGE, …)
        return msgId
    }

    override suspend fun markChatThreadRead(threadId: String) {
        require(chatThreads.any { it.id == threadId }) {
            "thread not found for ${RpcNames.MARK_CHAT_THREAD_READ}"
        }
        chatLastRead[threadId] = "2026-07-25T12:02:00Z"
        // TODO(live): supabase.rpc(RpcNames.MARK_CHAT_THREAD_READ, …)
    }

    override suspend fun chatUnreadCount(threadId: String?): Int {
        val threads = if (threadId != null) {
            chatThreads.filter { it.id == threadId }
        } else {
            chatThreads
        }
        var n = 0
        for (t in threads) {
            val since = chatLastRead[t.id]
            n += chatMessages.count { m ->
                m.threadId == t.id &&
                    m.senderKind != ChatSenderKind.CUSTOMER &&
                    (since == null || m.createdAt > since)
            }
        }
        return n
    }

    override suspend fun getDeliveryTrackPoint(
        deliveryJobId: String?,
        token: String?,
    ): DeliveryTrackPoint? {
        val jobId = deliveryJobId?.trim()?.takeIf { it.isNotEmpty() }
        val tok = token?.trim()?.takeIf { it.isNotEmpty() }
        require(jobId != null || tok != null) {
            "delivery_job_id or token required for ${RpcNames.GET_DELIVERY_TRACK_POINT}"
        }
        // OR match (mirrors live RPC: owner job id and/or share token).
        val tokenOk = tok != null && tok == SEED_TRACK_TOKEN
        val jobOk = jobId != null && jobId == SEED_ACTIVE_JOB_ID
        if (!tokenOk && !jobOk) return null
        val point = fakeTrackPoint ?: return null
        if (point.status != "dispatched") return null
        // Nudge last point each read so Fake demos look "live" (still one row, no trail).
        val n = trackTick.incrementAndGet()
        val secs = (point.etaSeconds ?: 48).coerceAtLeast(0) - 8
        val next = point.copy(
            lat = -17.8292 + n * 0.00012,
            lng = 31.0522 + n * 0.00009,
            recordedAt = "2026-07-25T09:%02d:00Z".format((10 + n).coerceAtMost(59)),
            etaSeconds = secs.coerceAtLeast(0),
            etaAt = if (secs <= 0) null else point.etaAt,
        )
        // After ~ETA expiry, mark terminal so poll stops (no stalking).
        if (secs <= 0) {
            fakeTrackPoint = next.copy(status = "completed", etaSeconds = 0, etaAt = null)
            return null
        }
        fakeTrackPoint = next
        // Single last point only — never a list / trail.
        return next
    }

    /** Demo helper: force terminal so UI stops polling and clears coords. */
    fun simulateDeliveryComplete() {
        fakeTrackPoint = seedTrackPoint().copy(status = "completed", etaSeconds = 0, etaAt = null)
    }

    /** Demo helper: reset seed active point for another Fake track session. */
    fun resetFakeTrackPoint() {
        trackTick.set(0)
        fakeTrackPoint = seedTrackPoint()
    }

    // --- Wishlist ---

    override suspend fun listWishlist(): List<WishlistItem> =
        wishlist.sortedByDescending { it.createdAt ?: "" }

    override suspend fun addCustomerWishlistItem(stockItemId: String?, oem: String?): String {
        val resolvedOem = oem?.trim()?.takeIf { it.isNotEmpty() }
        require(stockItemId != null || resolvedOem != null) {
            "stock_item_id or oem_part_number required for ${RpcNames.ADD_CUSTOMER_WISHLIST_ITEM}"
        }
        stockItemId?.let { sid ->
            wishlist.firstOrNull { it.stockItemId == sid }?.let { return it.id }
        }
        resolvedOem?.let { needle ->
            wishlist.firstOrNull { it.oemPartNumber.equals(needle, ignoreCase = true) }
                ?.let { return it.id }
        }
        val id = UUID.randomUUID().toString()
        val itemId = stockItemId ?: UUID.randomUUID().toString()
        wishlist.add(
            0,
            WishlistItem(
                id = id,
                stockItemId = itemId,
                oemPartNumber = resolvedOem ?: "OEM-${itemId.take(8)}",
                description = "Demo wishlist part",
                notifyWhenInStock = false,
                createdAt = "2026-07-25T12:00:00Z",
            ),
        )
        return id
    }

    override suspend fun removeCustomerWishlistItem(
        wishlistId: String?,
        stockItemId: String?,
        oem: String?,
    ) {
        val before = wishlist.size
        when {
            !wishlistId.isNullOrBlank() -> wishlist.removeAll { it.id == wishlistId }
            !stockItemId.isNullOrBlank() -> wishlist.removeAll { it.stockItemId == stockItemId }
            !oem.isNullOrBlank() -> {
                val needle = oem.trim()
                wishlist.removeAll { it.oemPartNumber.equals(needle, ignoreCase = true) }
            }
            else -> error("wishlist id, stock item, or OEM required")
        }
        require(wishlist.size < before) {
            "wishlist item not found for ${RpcNames.REMOVE_CUSTOMER_WISHLIST_ITEM}"
        }
    }

    override suspend fun setWishlistNotifyWhenInStock(
        notify: Boolean,
        wishlistId: String?,
        stockItemId: String?,
        oem: String?,
    ): String {
        val idx = when {
            !wishlistId.isNullOrBlank() -> wishlist.indexOfFirst { it.id == wishlistId }
            !stockItemId.isNullOrBlank() -> wishlist.indexOfFirst { it.stockItemId == stockItemId }
            !oem.isNullOrBlank() -> {
                val needle = oem.trim()
                wishlist.indexOfFirst { it.oemPartNumber.equals(needle, ignoreCase = true) }
            }
            else -> -1
        }
        require(idx >= 0) { "wishlist item not found for ${RpcNames.SET_WISHLIST_NOTIFY_WHEN_IN_STOCK}" }
        wishlist[idx] = wishlist[idx].copy(notifyWhenInStock = notify)
        return wishlist[idx].id
    }

    override suspend fun wishlistMoveToCart(
        wishlistId: String?,
        stockItemId: String?,
        oem: String?,
        qty: Double,
        removeFromWishlist: Boolean,
    ): String {
        require(qty > 0) { "qty must be > 0" }
        val item = when {
            !wishlistId.isNullOrBlank() -> wishlist.firstOrNull { it.id == wishlistId }
            !stockItemId.isNullOrBlank() -> wishlist.firstOrNull { it.stockItemId == stockItemId }
            !oem.isNullOrBlank() -> {
                val needle = oem.trim()
                wishlist.firstOrNull { it.oemPartNumber.equals(needle, ignoreCase = true) }
            }
            else -> null
        } ?: error("wishlist item not found for ${RpcNames.WISHLIST_MOVE_TO_CART}")

        var cart = openCart?.takeIf { it.status == "open" }
        if (cart == null) {
            createCustomerCart(SEED_WAREHOUSE_ID)
            cart = openCart!!
        }
        val lineId = addCustomerCartLine(
            cartId = cart.id,
            stockItemId = item.stockItemId,
            uomId = "00000000-0000-4000-8000-0000000000u1",
            qty = qty,
        )
        if (removeFromWishlist) {
            wishlist.removeAll { it.id == item.id }
        }
        return lineId
    }

    // --- Compare ---

    override suspend fun listCompareItems(): List<CompareItem> =
        compare.sortedByDescending { it.createdAt ?: "" }

    override suspend fun addCustomerCompareItem(stockItemId: String?, oem: String?): String {
        val resolvedOem = oem?.trim()?.takeIf { it.isNotEmpty() }
        require(stockItemId != null || resolvedOem != null) {
            "stock_item_id or oem_part_number required for ${RpcNames.ADD_CUSTOMER_COMPARE_ITEM}"
        }
        stockItemId?.let { sid ->
            compare.firstOrNull { it.stockItemId == sid }?.let { return it.id }
        }
        resolvedOem?.let { needle ->
            compare.firstOrNull { it.oemPartNumber.equals(needle, ignoreCase = true) }
                ?.let { return it.id }
        }
        require(compare.size < RpcNames.MAX_COMPARE_ITEMS) {
            "compare list is full (max ${RpcNames.MAX_COMPARE_ITEMS} items)"
        }
        val id = UUID.randomUUID().toString()
        val itemId = stockItemId ?: UUID.randomUUID().toString()
        compare.add(
            0,
            CompareItem(
                id = id,
                stockItemId = itemId,
                oemPartNumber = resolvedOem ?: "OEM-${itemId.take(8)}",
                description = "Demo compare part",
                createdAt = "2026-07-25T12:00:00Z",
            ),
        )
        return id
    }

    override suspend fun removeCustomerCompareItem(
        compareId: String?,
        stockItemId: String?,
        oem: String?,
    ) {
        val before = compare.size
        when {
            !compareId.isNullOrBlank() -> compare.removeAll { it.id == compareId }
            !stockItemId.isNullOrBlank() -> compare.removeAll { it.stockItemId == stockItemId }
            !oem.isNullOrBlank() -> {
                val needle = oem.trim()
                compare.removeAll { it.oemPartNumber.equals(needle, ignoreCase = true) }
            }
            else -> error("compare id, stock item, or OEM required")
        }
        require(compare.size < before) {
            "compare item not found for ${RpcNames.REMOVE_CUSTOMER_COMPARE_ITEM}"
        }
    }

    // --- Reviews ---

    override suspend fun listOwnReviews(): List<ProductReview> =
        reviews.sortedByDescending { it.createdAt ?: "" }

    override suspend fun listApprovedReviews(oem: String): List<ProductReview> {
        val needle = oem.trim()
        if (needle.isEmpty()) return emptyList()
        return reviews.filter {
            it.status == ProductReviewStatus.APPROVED &&
                it.oemPartNumber.equals(needle, ignoreCase = true)
        }
    }

    override suspend fun getProductReviewStats(
        stockItemId: String?,
        oem: String?,
    ): ProductReviewStats? {
        val approved = when {
            !stockItemId.isNullOrBlank() ->
                reviews.filter {
                    it.stockItemId == stockItemId && it.status == ProductReviewStatus.APPROVED
                }
            !oem.isNullOrBlank() -> {
                val needle = oem.trim()
                reviews.filter {
                    it.status == ProductReviewStatus.APPROVED &&
                        it.oemPartNumber.equals(needle, ignoreCase = true)
                }
            }
            else -> error("stock_item_id or oem_part_number required")
        }
        val anchor = approved.firstOrNull()
            ?: reviews.firstOrNull { r ->
                when {
                    !stockItemId.isNullOrBlank() -> r.stockItemId == stockItemId
                    !oem.isNullOrBlank() ->
                        r.oemPartNumber.equals(oem.trim(), ignoreCase = true)
                    else -> false
                }
            }
        val sid = anchor?.stockItemId ?: stockItemId ?: UUID.randomUUID().toString()
        val count = approved.size
        val avg = if (count == 0) 0.0 else approved.sumOf { it.rating }.toDouble() / count
        return ProductReviewStats(stockItemId = sid, avgRating = avg, reviewCount = count)
    }

    override suspend fun submitCustomerProductReview(
        rating: Int,
        body: String,
        stockItemId: String?,
        oem: String?,
    ): String {
        require(rating in 1..5) { "rating must be 1..5" }
        val resolvedOem = oem?.trim()?.takeIf { it.isNotEmpty() }
        require(stockItemId != null || resolvedOem != null) {
            "stock_item_id or oem_part_number required for ${RpcNames.SUBMIT_CUSTOMER_PRODUCT_REVIEW}"
        }
        val existingIdx = reviews.indexOfFirst { r ->
            when {
                !stockItemId.isNullOrBlank() -> r.stockItemId == stockItemId
                resolvedOem != null -> r.oemPartNumber.equals(resolvedOem, ignoreCase = true)
                else -> false
            }
        }
        if (existingIdx >= 0) {
            val existing = reviews[existingIdx]
            require(existing.status != ProductReviewStatus.APPROVED) {
                "cannot replace an approved review; contact support"
            }
            reviews[existingIdx] = existing.copy(
                rating = rating,
                body = body,
                status = ProductReviewStatus.PENDING,
                createdAt = "2026-07-25T12:30:00Z",
            )
            return existing.id
        }
        val id = UUID.randomUUID().toString()
        val itemId = stockItemId ?: UUID.randomUUID().toString()
        reviews.add(
            0,
            ProductReview(
                id = id,
                stockItemId = itemId,
                oemPartNumber = resolvedOem ?: "OEM-${itemId.take(8)}",
                description = "Demo review part",
                rating = rating,
                body = body,
                status = ProductReviewStatus.PENDING,
                createdAt = "2026-07-25T12:30:00Z",
            ),
        )
        return id
    }

    override suspend fun uploadReviewPhoto(
        reviewId: String,
        localFilePath: String,
        mimeType: String,
        sortOrder: Int,
    ): String {
        val review = reviews.firstOrNull { it.id == reviewId }
            ?: error("pending review not found for customer")
        require(review.status == ProductReviewStatus.PENDING) {
            "pending review not found for customer"
        }
        val count = reviewPhotoCounts[reviewId] ?: 0
        require(count < 5) { "max 5 photos per review" }
        // Fake accepts missing file (gallery/camera demos may pass a stub path).
        reviewPhotoCounts[reviewId] = count + 1
        return UUID.randomUUID().toString()
    }
}
