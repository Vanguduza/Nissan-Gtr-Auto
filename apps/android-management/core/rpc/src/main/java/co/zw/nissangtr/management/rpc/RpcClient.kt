package co.zw.nissangtr.management.rpc

/**
 * Thin staff RPC boundary for management Compose screens.
 *
 * **Live:** [SupabaseRpcClient] via [RpcClientFactory] when `SUPABASE_URL` +
 * `SUPABASE_ANON_KEY` are set (override with `rpc.forceFake=true`).
 * **Fallback:** [FakeRpcClient].
 *
 * Covers HR clock, POS cart, warehouse receive/transfer/recon, and logistics pick/DN.
 * Reads (DN/pick list lists) use PostgREST `from("delivery_notes")` / PowerSync
 * bucket `by_staff_dispatch` — not mutation RPCs.
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

    /** Staff/dispatcher: pending → dispatched | completed | failed. */
    suspend fun updateDeliveryJobStatus(
        deliveryJobId: String,
        status: DeliveryJobStatus,
    ): String

    /**
     * Bridge-only GPS trail point. Map GpsCoordinate via bridge helper
     * `toDeliveryLocationIngest`, with client-side ≥~5s throttle.
     * Never from browser / WebView geolocation.
     */
    suspend fun ingestDeliveryLocation(
        deliveryJobId: String,
        lat: Double,
        lng: Double,
        recordedAt: String? = null,
        accuracyM: Double? = null,
    ): String
}
