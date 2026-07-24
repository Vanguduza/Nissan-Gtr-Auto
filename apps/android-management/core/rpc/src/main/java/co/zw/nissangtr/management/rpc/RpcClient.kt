package co.zw.nissangtr.management.rpc

/**
 * Thin staff RPC boundary for management Compose screens.
 *
 * **Live binding (TODO):** replace [FakeRpcClient] with a Supabase Kotlin implementation:
 * ```
 * // supabase-kt (when added to :app / :core:rpc):
 * client.postgrest.rpc(RpcNames.CLOCK_ATTENDANCE, mapOf(
 *   "p_employee_id" to employeeId,
 *   "p_event_type" to eventType.rpcValue,
 *   "p_occurred_at" to null,
 *   "p_notes" to notes,
 * )).decodeAs<String>()
 * ```
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

    /** Scaffold list — live: SELECT delivery_notes WHERE status IN ('draft','submitted'). */
    suspend fun listDeliveryNotes(): List<DeliveryNoteSummary>

    /** Scaffold list — live: SELECT pick_lists open drafts. */
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
