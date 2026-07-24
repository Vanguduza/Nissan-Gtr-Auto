package co.zw.nissangtr.management.rpc

import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

/**
 * In-memory stub so HR / dispatch screens compile and exercise flows without
 * the Supabase Kotlin SDK. Swap for a live client that calls [RpcNames].
 *
 * Documented live RPC → param map:
 * - [RpcNames.CLOCK_ATTENDANCE]: p_employee_id, p_event_type, p_occurred_at?, p_notes?
 * - [RpcNames.CREATE_PICK_LIST]: p_sales_invoice_id, p_lines?
 * - [RpcNames.CONFIRM_PICK_LINES]: p_pick_list_id, p_lines
 * - [RpcNames.CREATE_DELIVERY_NOTE]: p_sales_invoice_id, p_lines, p_pick_list_id?
 * - [RpcNames.SUBMIT_DELIVERY_NOTE]: p_delivery_note_id
 * - [RpcNames.CANCEL_DELIVERY_NOTE]: p_delivery_note_id
 */
class FakeRpcClient : RpcClient {
    private val dnSeq = AtomicInteger(1)
    private val plSeq = AtomicInteger(1)
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

    override suspend fun clockAttendance(
        employeeId: String,
        eventType: AttendanceEventType,
        notes: String?,
    ): String {
        require(employeeId.isNotBlank()) { "employeeId required for ${RpcNames.CLOCK_ATTENDANCE}" }
        // TODO(live): supabase.rpc(RpcNames.CLOCK_ATTENDANCE, …)
        return UUID.randomUUID().toString()
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
        // TODO(live): supabase.rpc(RpcNames.CREATE_PICK_LIST, …)
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
        // TODO(live): supabase.rpc(RpcNames.CONFIRM_PICK_LINES, …)
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
        // TODO(live): supabase.rpc(RpcNames.CREATE_DELIVERY_NOTE, …)
        return id
    }

    override suspend fun submitDeliveryNote(deliveryNoteId: String): String {
        val idx = deliveryNotes.indexOfFirst { it.id == deliveryNoteId }
        require(idx >= 0) { "delivery note not found" }
        deliveryNotes[idx] = deliveryNotes[idx].copy(status = "submitted")
        // TODO(live): supabase.rpc(RpcNames.SUBMIT_DELIVERY_NOTE, …)
        return deliveryNoteId
    }

    override suspend fun cancelDeliveryNote(deliveryNoteId: String): String {
        val idx = deliveryNotes.indexOfFirst { it.id == deliveryNoteId }
        require(idx >= 0) { "delivery note not found" }
        deliveryNotes[idx] = deliveryNotes[idx].copy(status = "cancelled")
        // TODO(live): supabase.rpc(RpcNames.CANCEL_DELIVERY_NOTE, …)
        return deliveryNoteId
    }
}
