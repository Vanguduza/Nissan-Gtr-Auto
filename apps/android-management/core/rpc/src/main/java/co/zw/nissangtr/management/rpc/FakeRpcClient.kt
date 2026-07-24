package co.zw.nissangtr.management.rpc

import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

/**
 * In-memory stub so HR / dispatch screens compile and exercise flows without
 * a configured Supabase project. Live: [SupabaseRpcClient] via [RpcClientFactory].
 *
 * Documented live RPC → param map:
 * - [RpcNames.CLOCK_ATTENDANCE]: p_employee_id, p_event_type, p_occurred_at?, p_notes?
 * - [RpcNames.CREATE_PICK_LIST]: p_sales_invoice_id, p_lines?
 * - [RpcNames.CONFIRM_PICK_LINES]: p_pick_list_id, p_lines
 * - [RpcNames.CREATE_DELIVERY_NOTE]: p_sales_invoice_id, p_lines, p_pick_list_id?
 * - [RpcNames.SUBMIT_DELIVERY_NOTE]: p_delivery_note_id
 * - [RpcNames.CANCEL_DELIVERY_NOTE]: p_delivery_note_id
 * - [RpcNames.CREATE_DELIVERY_JOB]: p_delivery_note_id, p_assignee_user_id?, p_eta_at?, p_notes?
 * - [RpcNames.UPDATE_DELIVERY_JOB_STATUS]: p_delivery_job_id, p_status
 * - [RpcNames.INGEST_DELIVERY_LOCATION]: p_delivery_job_id, p_lat, p_lng, p_recorded_at?, p_accuracy_m?
 */
class FakeRpcClient : RpcClient {
    private val dnSeq = AtomicInteger(1)
    private val plSeq = AtomicInteger(1)
    private val jobSeq = AtomicInteger(1)
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
}
