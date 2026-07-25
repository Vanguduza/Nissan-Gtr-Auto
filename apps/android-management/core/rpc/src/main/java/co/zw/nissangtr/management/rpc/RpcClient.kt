package co.zw.nissangtr.management.rpc

/**
 * Thin staff RPC boundary for management Compose screens.
 *
 * **Live:** [SupabaseRpcClient] via [RpcClientFactory] when `SUPABASE_URL` +
 * `SUPABASE_ANON_KEY` are set (override with `rpc.forceFake=true`).
 * **Fallback:** [FakeRpcClient].
 *
 * Covers HR clock, POS cart, warehouse receive/transfer/recon, logistics pick/DN,
 * and staff live chat (claim/reply/close).
 * Reads (DN/pick/chat lists) use PostgREST + RLS — not mutation RPCs.
 *
 * GPS / QR: Bridge-First only (`bridges/android/`) — never HTML5 or WebView APIs.
 */
interface RpcClient {
    suspend fun clockAttendance(
        employeeId: String,
        eventType: AttendanceEventType,
        notes: String? = null,
    ): String

    // --- POS (typed stock_item / UOM + Bridge-First QR — no HTML5 QR) ---

    suspend fun createPosCart(
        warehouseId: String,
        currency: CurrencyCode = CurrencyCode.USD,
        fulfillmentMode: FulfillmentMode = FulfillmentMode.IMMEDIATE,
        customerId: String? = null,
    ): String

    suspend fun addCartLine(
        cartId: String,
        stockItemId: String,
        uomId: String,
        qty: Double,
    ): String

    /**
     * Bridge-decoded inventory QR payload → cart line.
     * Payload must match `gtr://part/{OEM}?batch=…&valuation=FIFO|AVG`.
     */
    suspend fun addCartLineFromQr(
        cartId: String,
        qrPayload: String,
        qty: Double = 1.0,
    ): String

    suspend fun checkoutPosCart(cartId: String): String

    /**
     * Resolve OEM (from parsed inventory QR) to stock_item + base UOM.
     * Used by warehouse receive / cycle-count after bridge scan — not inside the bridge.
     */
    suspend fun lookupStockItemByOem(oemPartNumber: String): StockItemRef

    // --- Warehouse ---

    suspend fun postStockReceipt(
        toWarehouseId: String,
        notes: String?,
        lines: List<ReceiptLineInput>,
    ): String

    suspend fun createStockTransfer(
        fromWarehouseId: String,
        toWarehouseId: String,
        notes: String?,
        lines: List<TransferLineInput>,
    ): String

    suspend fun approveStockTransfer(entryId: String): String

    suspend fun rejectStockTransfer(entryId: String): String

    suspend fun createStockReconciliationDraft(
        warehouseId: String,
        scope: ReconciliationScope,
        currency: CurrencyCode = CurrencyCode.USD,
        itemIds: List<String>? = null,
        notes: String? = null,
        exchangeRate: Double? = null,
    ): String

    /** Returns count of upserted lines. */
    suspend fun upsertStockReconciliationLines(
        reconciliationId: String,
        lines: List<ReconciliationLineInput>,
    ): Int

    suspend fun submitStockReconciliation(reconciliationId: String): String

    suspend fun approveStockReconciliation(reconciliationId: String): String

    suspend fun cancelStockReconciliation(
        reconciliationId: String,
        notes: String? = null,
    ): String

    // --- Logistics ---

    /** Live: SELECT delivery_notes via PostgREST + RLS. */
    suspend fun listDeliveryNotes(): List<DeliveryNoteSummary>

    /** Live: SELECT pick_lists via PostgREST + RLS. */
    suspend fun listPickLists(): List<PickListSummary>

    suspend fun createPickList(salesInvoiceId: String, linesJson: String? = null): String

    suspend fun confirmPickLines(pickListId: String, lines: List<ConfirmPickLineInput>): String

    suspend fun createDeliveryNote(
        salesInvoiceId: String,
        lines: List<DnLineInput>,
        pickListId: String? = null,
    ): String

    suspend fun submitDeliveryNote(deliveryNoteId: String): String

    suspend fun cancelDeliveryNote(deliveryNoteId: String): String

    /** Staff/dispatcher: create a delivery job from a submitted DN. */
    suspend fun createDeliveryJob(
        deliveryNoteId: String,
        assigneeUserId: String? = null,
        etaAt: String? = null,
        notes: String? = null,
    ): String

    /**
     * Set pickup/dropoff coords so suggest ranking + ETA work.
     * Fake: in-memory. Live: requires backend `set_delivery_job_geo` (not shipped yet) —
     * [SupabaseRpcClient] fails closed with a clear error until that RPC exists.
     */
    suspend fun setDeliveryJobCoords(
        deliveryJobId: String,
        pickupLat: Double?,
        pickupLng: Double?,
        dropoffLat: Double?,
        dropoffLng: Double?,
    )

    /** Staff/dispatcher: pending → dispatched | completed | failed. */
    suspend fun updateDeliveryJobStatus(
        deliveryJobId: String,
        status: DeliveryJobStatus,
    ): String

    /**
     * GPS trail ingest — **apps/android-delivery is the sole producer**.
     * Management dispatch must not call this from UI (view-only via
     * [getDeliveryTrackPoint]). Kept for Fake/Live parity only.
     * Never from browser / WebView geolocation.
     */
    suspend fun ingestDeliveryLocation(
        deliveryJobId: String,
        lat: Double,
        lng: Double,
        recordedAt: String? = null,
        accuracyM: Double? = null,
    ): String

    /** Nearest / capacity / shift suggestions for a pending job. */
    suspend fun suggestDeliveryAssignees(
        deliveryJobId: String,
        limit: Int = 5,
    ): List<DeliveryAssigneeSuggestion>

    /**
     * Assign driver to job. [override] = true bypasses eligibility
     * (manual override when suggest rules fail).
     */
    suspend fun assignDeliveryJob(
        deliveryJobId: String,
        assigneeUserId: String,
        override: Boolean = false,
    ): String

    /** Nearest-neighbor stop order for driver’s open jobs (persists route_sequence). */
    suspend fun optimizeDriverStops(driverUserId: String): List<OptimizedDriverStop>

    /** Staff live last-point + ETA for an active dispatched job (not a GPS producer). */
    suspend fun getDeliveryTrackPoint(deliveryJobId: String): DeliveryTrackPoint?

    /**
     * Share-token plaintext (hex). Show once to dispatcher after Mark dispatched.
     * Re-mint revokes prior active tokens for the job.
     */
    suspend fun mintDeliveryTrackToken(
        deliveryJobId: String,
        ttl: String? = null,
    ): String

    /**
     * POD OTP plaintext (6 digits). Dispatcher may read to customer;
     * hash-only in DB. Job must be dispatched.
     */
    suspend fun generateDeliveryPodOtp(
        deliveryJobId: String,
        ttl: String? = null,
    ): String

    /** Open panic rows (`acknowledged_at` IS NULL). Poll — Realtime not wired yet. */
    suspend fun listOpenPanicEvents(): List<PanicEventSummary>

    /** Mark panic handled (sets acknowledged_at / acknowledged_by). */
    suspend fun acknowledgePanicEvent(panicEventId: String): String

    // --- Live chat (staff inbox) ---

    /** GoTrue user id, or null when Fake / signed out. */
    fun currentUserId(): String?

    /** Own rows from `staff_roles` (RLS). Used for chat nav gate. */
    suspend fun listMyStaffRoles(): List<String>

    /** Live: SELECT chat_threads via PostgREST + RLS (open / mine / closed). */
    suspend fun listStaffChatThreads(filter: StaffChatFilter): List<ChatThreadSummary>

    /** Live: SELECT chat_messages for thread, oldest first. */
    suspend fun listChatMessages(threadId: String): List<ChatMessageSummary>

    suspend fun claimChatThread(threadId: String)

    suspend fun closeChatThread(threadId: String)

    suspend fun markChatThreadRead(threadId: String)

    /** Returns new message id. */
    suspend fun postChatMessage(threadId: String, body: String): String

    /** Unread across inbox, or for one thread when [threadId] set. */
    suspend fun chatUnreadCount(threadId: String? = null): Int
}
