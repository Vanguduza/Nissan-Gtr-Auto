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
}
