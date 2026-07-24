package co.zw.nissangtr.management.rpc

/**
 * Thin staff RPC boundary for management Compose screens.
 *
 * **Live:** [SupabaseRpcClient] via [RpcClientFactory] when `SUPABASE_URL` +
 * `SUPABASE_ANON_KEY` are set (override with `rpc.forceFake=true`).
 * **Fallback:** [FakeRpcClient].
 *
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
     * Bridge-only GPS trail point. Call after [co.zw.nissangtr.bridges.location.toDeliveryLocationIngest]
     * with client-side ≥~5s throttle. Never from browser geolocation.
     */
    suspend fun ingestDeliveryLocation(
        deliveryJobId: String,
        lat: Double,
        lng: Double,
        recordedAt: String? = null,
        accuracyM: Double? = null,
    ): String
}
