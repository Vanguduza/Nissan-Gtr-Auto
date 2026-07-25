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

    /** Fake active job → last point (single row only; no trail). Nudged on each poll. */
    private var fakeTrackPoint: DeliveryTrackPoint? = seedTrackPoint()
    private val trackTick = AtomicInteger(0)

    private fun seedTrackPoint() = DeliveryTrackPoint(
        deliveryJobId = SEED_ACTIVE_JOB_ID,
        lat = -17.8292,
        lng = 31.0522,
        recordedAt = "2026-07-25T09:10:00Z",
        etaAt = "2026-07-25T09:11:00Z",
        /** ~6 polls at 8s → terminal demo without waiting half an hour. */
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
        val secs = (point.etaSeconds ?: 2_100).coerceAtLeast(0) - 8
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
        fakeTrackPoint = fakeTrackPoint?.copy(status = "completed", etaSeconds = 0, etaAt = null)
            ?: DeliveryTrackPoint(
                deliveryJobId = SEED_ACTIVE_JOB_ID,
                lat = -17.8292,
                lng = 31.0522,
                recordedAt = "2026-07-25T09:10:00Z",
                etaAt = null,
                etaSeconds = 0,
                status = "completed",
            )
    }

    /** Demo helper: reset seed active point for another Fake track session. */
    fun resetFakeTrackPoint() {
        trackTick.set(0)
        fakeTrackPoint = DeliveryTrackPoint(
            deliveryJobId = SEED_ACTIVE_JOB_ID,
            lat = -17.8292,
            lng = 31.0522,
            recordedAt = "2026-07-25T09:10:00Z",
            etaAt = "2026-07-25T09:45:00Z",
            etaSeconds = 2_100,
            status = "dispatched",
        )
    }
}
