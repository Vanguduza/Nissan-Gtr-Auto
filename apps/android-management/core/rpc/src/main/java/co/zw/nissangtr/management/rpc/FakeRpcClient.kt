package co.zw.nissangtr.management.rpc

import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

/**
 * In-memory stub so HR / POS / warehouse / dispatch / chat screens compile and exercise
 * flows without a configured Supabase project. Live: [SupabaseRpcClient] via [RpcClientFactory].
 *
 * Documented live RPC → param map:
 * - [RpcNames.CLOCK_ATTENDANCE]: p_employee_id, p_event_type, p_occurred_at?, p_notes?
 * - [RpcNames.CREATE_POS_CART]: p_warehouse_id, p_customer_id?, p_currency, p_fulfillment_mode?
 * - [RpcNames.ADD_CART_LINE]: p_cart_id, p_stock_item_id, p_uom_id, p_qty
 * - [RpcNames.ADD_CART_LINE_FROM_QR]: p_cart_id, p_qr_payload, p_qty?
 * - [RpcNames.CHECKOUT_POS_CART]: p_cart_id
 * - lookupStockItemByOem: PostgREST stock_items by oem_part_number (not an RPC)
 * - [RpcNames.POST_STOCK_RECEIPT]: p_to_warehouse_id, p_notes?, p_lines
 * - [RpcNames.CREATE_STOCK_TRANSFER]: p_from_warehouse_id, p_to_warehouse_id, p_notes?, p_lines
 * - [RpcNames.APPROVE_STOCK_TRANSFER] / [RpcNames.REJECT_STOCK_TRANSFER]: p_entry_id
 * - [RpcNames.CREATE_STOCK_RECONCILIATION_DRAFT]: p_warehouse_id, p_scope, p_item_ids?, p_notes?, p_currency, p_exchange_rate?
 * - [RpcNames.UPSERT_STOCK_RECONCILIATION_LINES]: p_reconciliation_id, p_lines
 * - [RpcNames.SUBMIT_STOCK_RECONCILIATION] / [RpcNames.APPROVE_STOCK_RECONCILIATION]: p_reconciliation_id
 * - [RpcNames.CANCEL_STOCK_RECONCILIATION]: p_reconciliation_id, p_notes?
 * - [RpcNames.CREATE_PICK_LIST]: p_sales_invoice_id, p_lines?
 * - [RpcNames.CONFIRM_PICK_LINES]: p_pick_list_id, p_lines
 * - [RpcNames.CREATE_DELIVERY_NOTE]: p_sales_invoice_id, p_lines, p_pick_list_id?
 * - [RpcNames.SUBMIT_DELIVERY_NOTE]: p_delivery_note_id
 * - [RpcNames.CANCEL_DELIVERY_NOTE]: p_delivery_note_id
 * - [RpcNames.CREATE_DELIVERY_JOB]: p_delivery_note_id, p_assignee_user_id?, p_eta_at?, p_notes?
 * - [RpcNames.UPDATE_DELIVERY_JOB_STATUS]: p_delivery_job_id, p_status
 * - [RpcNames.INGEST_DELIVERY_LOCATION]: p_delivery_job_id, p_lat, p_lng, p_recorded_at?, p_accuracy_m?
 * - [RpcNames.CLAIM_CHAT_THREAD] / [RpcNames.CLOSE_CHAT_THREAD] / [RpcNames.MARK_CHAT_THREAD_READ]: p_thread_id
 * - [RpcNames.POST_CHAT_MESSAGE]: p_thread_id, p_body
 * - [RpcNames.CHAT_UNREAD_COUNT]: p_thread_id?
 * - listStaffChatThreads / listChatMessages: PostgREST (not RPCs)
 * - listMyStaffRoles: PostgREST staff_roles
 */
class FakeRpcClient : RpcClient {
    private val dnSeq = AtomicInteger(1)
    private val plSeq = AtomicInteger(1)
    private val jobSeq = AtomicInteger(1)
    private val openCarts = mutableSetOf<String>()
    private val pendingTransfers = mutableSetOf<String>()
    private val reconDrafts = mutableSetOf<String>()
    private val deliveryJobs = mutableMapOf<String, Pair<String, String>>() // id → (dnId, status)
    /** Exposed for unit/demo checks — count of successful GPS ingests. */
    val ingestedLocationCount: AtomicInteger = AtomicInteger(0)
    private val deliveryNotes = mutableListOf(
        DeliveryNoteSummary(
            id = "00000000-0000-4000-8000-0000000000d1",
            documentNumber = "DN-SEED-001",
            salesInvoiceId = "00000000-0000-4000-8000-0000000000i1",
            status = "draft",
        ),
    )
    private val pickLists = mutableListOf(
        PickListSummary(
            id = "00000000-0000-4000-8000-0000000000p1",
            documentNumber = "PL-SEED-001",
            salesInvoiceId = "00000000-0000-4000-8000-0000000000i1",
            status = "draft",
        ),
    )

    private val fakeStaffUserId = FAKE_STAFF_USER_ID
    private val chatThreads = mutableListOf(
        ChatThreadSummary(
            id = OPEN_THREAD_ID,
            kind = "support",
            status = "open",
            subject = "Brake pads fitment",
            assignedTo = null,
            lastMessageAt = "2026-07-25T08:00:00Z",
            createdAt = "2026-07-25T07:55:00Z",
        ),
        ChatThreadSummary(
            id = MINE_THREAD_ID,
            kind = "parts",
            status = "assigned",
            subject = "OEM 12345 stock?",
            assignedTo = FAKE_STAFF_USER_ID,
            lastMessageAt = "2026-07-25T08:10:00Z",
            createdAt = "2026-07-25T08:05:00Z",
        ),
        ChatThreadSummary(
            id = CLOSED_THREAD_ID,
            kind = "support",
            status = "closed",
            subject = "Closed sample",
            assignedTo = FAKE_STAFF_USER_ID,
            lastMessageAt = "2026-07-24T12:00:00Z",
            createdAt = "2026-07-24T11:00:00Z",
        ),
    )
    private val chatMessages = mutableMapOf(
        OPEN_THREAD_ID to mutableListOf(
            ChatMessageSummary(
                id = "00000000-0000-4000-8000-0000000000m1",
                threadId = OPEN_THREAD_ID,
                senderUserId = FAKE_CUSTOMER_USER_ID,
                senderKind = "customer",
                body = "Do you have front pads for GTR R35?",
                createdAt = "2026-07-25T08:00:00Z",
            ),
        ),
        MINE_THREAD_ID to mutableListOf(
            ChatMessageSummary(
                id = "00000000-0000-4000-8000-0000000000m2",
                threadId = MINE_THREAD_ID,
                senderUserId = FAKE_CUSTOMER_USER_ID,
                senderKind = "customer",
                body = "Is OEM 12345 in stock?",
                createdAt = "2026-07-25T08:05:00Z",
            ),
            ChatMessageSummary(
                id = "00000000-0000-4000-8000-0000000000m3",
                threadId = MINE_THREAD_ID,
                senderUserId = FAKE_STAFF_USER_ID,
                senderKind = "staff",
                body = "Checking warehouse now.",
                createdAt = "2026-07-25T08:10:00Z",
            ),
        ),
        CLOSED_THREAD_ID to mutableListOf(
            ChatMessageSummary(
                id = "00000000-0000-4000-8000-0000000000m4",
                threadId = CLOSED_THREAD_ID,
                senderUserId = FAKE_CUSTOMER_USER_ID,
                senderKind = "customer",
                body = "Thanks, resolved.",
                createdAt = "2026-07-24T12:00:00Z",
            ),
        ),
    )
    private val chatUnread = mutableMapOf(
        OPEN_THREAD_ID to 1,
        MINE_THREAD_ID to 0,
        CLOSED_THREAD_ID to 0,
    )

    override suspend fun clockAttendance(
        employeeId: String,
        eventType: AttendanceEventType,
        notes: String?,
    ): String {
        require(employeeId.isNotBlank()) { "employeeId required for ${RpcNames.CLOCK_ATTENDANCE}" }
        return UUID.randomUUID().toString()
    }

    override suspend fun createPosCart(
        warehouseId: String,
        currency: CurrencyCode,
        fulfillmentMode: FulfillmentMode,
        customerId: String?,
    ): String {
        require(warehouseId.isNotBlank()) { "warehouseId required for ${RpcNames.CREATE_POS_CART}" }
        val id = UUID.randomUUID().toString()
        openCarts.add(id)
        return id
    }

    override suspend fun addCartLine(
        cartId: String,
        stockItemId: String,
        uomId: String,
        qty: Double,
    ): String {
        require(cartId.isNotBlank()) { "cartId required for ${RpcNames.ADD_CART_LINE}" }
        require(stockItemId.isNotBlank()) { "stockItemId required" }
        require(uomId.isNotBlank()) { "uomId required" }
        require(qty > 0) { "qty must be > 0" }
        // Fake allows any cart id for scaffold demos; live requires an open cart.
        openCarts.add(cartId)
        return UUID.randomUUID().toString()
    }

    override suspend fun addCartLineFromQr(
        cartId: String,
        qrPayload: String,
        qty: Double,
    ): String {
        require(cartId.isNotBlank()) { "cartId required for ${RpcNames.ADD_CART_LINE_FROM_QR}" }
        require(qty > 0) { "qty must be > 0" }
        val m = INVENTORY_QR_REGEX.matchEntire(qrPayload.trim())
            ?: throw IllegalArgumentException("invalid inventory QR payload")
        require(m.groupValues[1].isNotBlank()) { "OEM required in QR" }
        openCarts.add(cartId)
        return UUID.randomUUID().toString()
    }

    override suspend fun checkoutPosCart(cartId: String): String {
        require(cartId.isNotBlank()) { "cartId required for ${RpcNames.CHECKOUT_POS_CART}" }
        openCarts.remove(cartId)
        return UUID.randomUUID().toString()
    }

    override suspend fun lookupStockItemByOem(oemPartNumber: String): StockItemRef {
        val oem = oemPartNumber.trim()
        require(oem.isNotBlank()) { "oemPartNumber required" }
        // Deterministic fake UUIDs so scaffold demos stay stable per OEM.
        val item = UUID.nameUUIDFromBytes("item:$oem".toByteArray()).toString()
        val uom = UUID.nameUUIDFromBytes("uom:$oem".toByteArray()).toString()
        return StockItemRef(stockItemId = item, uomId = uom, oemPartNumber = oem)
    }

    override suspend fun postStockReceipt(
        toWarehouseId: String,
        notes: String?,
        lines: List<ReceiptLineInput>,
    ): String {
        require(toWarehouseId.isNotBlank())
        require(lines.isNotEmpty()) { "${RpcNames.POST_STOCK_RECEIPT} requires lines" }
        lines.forEach { line ->
            require(line.stockItemId.isNotBlank() && line.uomId.isNotBlank())
            require(line.qty > 0)
        }
        return UUID.randomUUID().toString()
    }

    override suspend fun createStockTransfer(
        fromWarehouseId: String,
        toWarehouseId: String,
        notes: String?,
        lines: List<TransferLineInput>,
    ): String {
        require(fromWarehouseId.isNotBlank() && toWarehouseId.isNotBlank())
        require(fromWarehouseId != toWarehouseId) { "from and to warehouses must differ" }
        require(lines.isNotEmpty()) { "${RpcNames.CREATE_STOCK_TRANSFER} requires lines" }
        val id = UUID.randomUUID().toString()
        pendingTransfers.add(id)
        return id
    }

    override suspend fun approveStockTransfer(entryId: String): String {
        require(entryId.isNotBlank())
        pendingTransfers.remove(entryId)
        return entryId
    }

    override suspend fun rejectStockTransfer(entryId: String): String {
        require(entryId.isNotBlank())
        pendingTransfers.remove(entryId)
        return entryId
    }

    override suspend fun createStockReconciliationDraft(
        warehouseId: String,
        scope: ReconciliationScope,
        currency: CurrencyCode,
        itemIds: List<String>?,
        notes: String?,
        exchangeRate: Double?,
    ): String {
        require(warehouseId.isNotBlank())
        if (scope == ReconciliationScope.PARTIAL) {
            require(!itemIds.isNullOrEmpty()) { "partial reconciliation requires item_ids" }
        }
        if (currency == CurrencyCode.ZIG) {
            require(exchangeRate != null && exchangeRate > 0) {
                "ZIG requires positive exchange_rate"
            }
        }
        val id = UUID.randomUUID().toString()
        reconDrafts.add(id)
        return id
    }

    override suspend fun upsertStockReconciliationLines(
        reconciliationId: String,
        lines: List<ReconciliationLineInput>,
    ): Int {
        require(reconciliationId.isNotBlank())
        require(lines.isNotEmpty()) { "${RpcNames.UPSERT_STOCK_RECONCILIATION_LINES} requires lines" }
        return lines.size
    }

    override suspend fun submitStockReconciliation(reconciliationId: String): String {
        require(reconciliationId.isNotBlank())
        reconDrafts.remove(reconciliationId)
        return reconciliationId
    }

    override suspend fun approveStockReconciliation(reconciliationId: String): String {
        require(reconciliationId.isNotBlank())
        return reconciliationId
    }

    override suspend fun cancelStockReconciliation(
        reconciliationId: String,
        notes: String?,
    ): String {
        require(reconciliationId.isNotBlank())
        reconDrafts.remove(reconciliationId)
        return reconciliationId
    }

    override suspend fun listDeliveryNotes(): List<DeliveryNoteSummary> =
        deliveryNotes.toList()

    override suspend fun listPickLists(): List<PickListSummary> =
        pickLists.toList()

    override suspend fun createPickList(salesInvoiceId: String, linesJson: String?): String {
        require(salesInvoiceId.isNotBlank())
        val id = UUID.randomUUID().toString()
        val n = plSeq.getAndIncrement()
        pickLists.add(
            0,
            PickListSummary(
                id = id,
                documentNumber = "PL-FAKE-%03d".format(n),
                salesInvoiceId = salesInvoiceId,
                status = "draft",
            ),
        )
        return id
    }

    override suspend fun confirmPickLines(
        pickListId: String,
        lines: List<ConfirmPickLineInput>,
    ): String {
        require(lines.isNotEmpty())
        val idx = pickLists.indexOfFirst { it.id == pickListId }
        if (idx >= 0) {
            val pl = pickLists[idx]
            pickLists[idx] = pl.copy(status = "done")
        }
        return pickListId
    }

    override suspend fun createDeliveryNote(
        salesInvoiceId: String,
        lines: List<DnLineInput>,
        pickListId: String?,
    ): String {
        require(salesInvoiceId.isNotBlank())
        require(lines.isNotEmpty()) { "${RpcNames.CREATE_DELIVERY_NOTE} requires lines" }
        val id = UUID.randomUUID().toString()
        val n = dnSeq.getAndIncrement()
        deliveryNotes.add(
            0,
            DeliveryNoteSummary(
                id = id,
                documentNumber = "DN-FAKE-%03d".format(n),
                salesInvoiceId = salesInvoiceId,
                status = "draft",
            ),
        )
        return id
    }

    override suspend fun submitDeliveryNote(deliveryNoteId: String): String {
        val idx = deliveryNotes.indexOfFirst { it.id == deliveryNoteId }
        require(idx >= 0) { "delivery note not found" }
        deliveryNotes[idx] = deliveryNotes[idx].copy(status = "submitted")
        return deliveryNoteId
    }

    override suspend fun cancelDeliveryNote(deliveryNoteId: String): String {
        val idx = deliveryNotes.indexOfFirst { it.id == deliveryNoteId }
        require(idx >= 0) { "delivery note not found" }
        deliveryNotes[idx] = deliveryNotes[idx].copy(status = "cancelled")
        return deliveryNoteId
    }

    override suspend fun createDeliveryJob(
        deliveryNoteId: String,
        assigneeUserId: String?,
        etaAt: String?,
        notes: String?,
    ): String {
        require(deliveryNoteId.isNotBlank())
        val dn = deliveryNotes.find { it.id == deliveryNoteId }
            ?: DeliveryNoteSummary(
                id = deliveryNoteId,
                documentNumber = "DN-EXT",
                salesInvoiceId = "",
                status = "submitted",
            )
        require(dn.status == "submitted" || dn.status == "draft") {
            // Fake allows draft for scaffold demos; live requires submitted.
            "delivery job requires DN"
        }
        val id = UUID.randomUUID().toString()
        jobSeq.getAndIncrement()
        deliveryJobs[id] = deliveryNoteId to "pending"
        return id
    }

    override suspend fun updateDeliveryJobStatus(
        deliveryJobId: String,
        status: DeliveryJobStatus,
    ): String {
        val current = deliveryJobs[deliveryJobId]
            ?: ("unknown" to "pending").also { deliveryJobs[deliveryJobId] = it }
        require(current.second !in listOf("completed", "failed")) {
            "terminal delivery job cannot change status"
        }
        deliveryJobs[deliveryJobId] = current.first to status.rpcValue
        return deliveryJobId
    }

    override suspend fun ingestDeliveryLocation(
        deliveryJobId: String,
        lat: Double,
        lng: Double,
        recordedAt: String?,
        accuracyM: Double?,
    ): String {
        require(deliveryJobId.isNotBlank())
        require(lat in -90.0..90.0) { "lat out of range" }
        require(lng in -180.0..180.0) { "lng out of range" }
        val job = deliveryJobs[deliveryJobId]
        if (job != null) {
            require(job.second !in listOf("completed", "failed")) {
                "cannot ingest locations for terminal job"
            }
        }
        ingestedLocationCount.incrementAndGet()
        return UUID.randomUUID().toString()
    }

    override fun currentUserId(): String? = fakeStaffUserId

    override suspend fun listMyStaffRoles(): List<String> =
        listOf("admin", "sales")

    override suspend fun listStaffChatThreads(filter: StaffChatFilter): List<ChatThreadSummary> {
        val uid = fakeStaffUserId
        return chatThreads.filter { t ->
            when (filter) {
                StaffChatFilter.OPEN -> t.status == "open"
                StaffChatFilter.MINE -> t.assignedTo == uid && t.status != "closed"
                StaffChatFilter.CLOSED -> t.status == "closed"
            }
        }.sortedByDescending { it.lastMessageAt ?: it.createdAt }
    }

    override suspend fun listChatMessages(threadId: String): List<ChatMessageSummary> {
        require(threadId.isNotBlank())
        return chatMessages[threadId]?.toList().orEmpty()
    }

    override suspend fun claimChatThread(threadId: String) {
        val idx = chatThreads.indexOfFirst { it.id == threadId }
        require(idx >= 0) { "thread not found" }
        val t = chatThreads[idx]
        require(t.status != "closed") { "cannot claim closed thread" }
        chatThreads[idx] = t.copy(
            status = "assigned",
            assignedTo = fakeStaffUserId,
        )
    }

    override suspend fun closeChatThread(threadId: String) {
        val idx = chatThreads.indexOfFirst { it.id == threadId }
        require(idx >= 0) { "thread not found" }
        chatThreads[idx] = chatThreads[idx].copy(status = "closed")
    }

    override suspend fun markChatThreadRead(threadId: String) {
        require(threadId.isNotBlank())
        chatUnread[threadId] = 0
    }

    override suspend fun postChatMessage(threadId: String, body: String): String {
        require(threadId.isNotBlank())
        val trimmed = body.trim()
        require(trimmed.isNotEmpty()) { "message body required" }
        val idx = chatThreads.indexOfFirst { it.id == threadId }
        require(idx >= 0) { "thread not found" }
        require(chatThreads[idx].status != "closed") { "thread closed" }
        val id = UUID.randomUUID().toString()
        val now = java.time.Instant.now().toString()
        val list = chatMessages.getOrPut(threadId) { mutableListOf() }
        list.add(
            ChatMessageSummary(
                id = id,
                threadId = threadId,
                senderUserId = fakeStaffUserId,
                senderKind = "staff",
                body = trimmed,
                createdAt = now,
            ),
        )
        chatThreads[idx] = chatThreads[idx].copy(lastMessageAt = now)
        return id
    }

    override suspend fun chatUnreadCount(threadId: String?): Int {
        return if (threadId.isNullOrBlank()) {
            chatUnread.values.sum()
        } else {
            chatUnread[threadId] ?: 0
        }
    }

    companion object {
        const val FAKE_STAFF_USER_ID = "00000000-0000-4000-8000-0000000000a1"
        const val FAKE_CUSTOMER_USER_ID = "00000000-0000-4000-8000-0000000000c1"
        private const val OPEN_THREAD_ID = "00000000-0000-4000-8000-0000000000t1"
        private const val MINE_THREAD_ID = "00000000-0000-4000-8000-0000000000t2"
        private const val CLOSED_THREAD_ID = "00000000-0000-4000-8000-0000000000t3"

        private val INVENTORY_QR_REGEX =
            Regex("""^gtr://part/([^?]+)\?batch=([^&]+)&valuation=(FIFO|AVG)$""")
    }
}
