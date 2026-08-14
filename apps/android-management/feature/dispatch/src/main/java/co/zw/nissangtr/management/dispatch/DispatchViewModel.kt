package co.zw.nissangtr.management.dispatch

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import co.zw.nissangtr.management.rpc.ConfirmPickLineInput
import co.zw.nissangtr.management.rpc.DeliveryAssigneeSuggestion
import co.zw.nissangtr.management.rpc.DeliveryJobDeskSummary
import co.zw.nissangtr.management.rpc.DeliveryJobStatus
import co.zw.nissangtr.management.rpc.DeliveryNoteSummary
import co.zw.nissangtr.management.rpc.DeliveryTrackPoint
import co.zw.nissangtr.management.rpc.DispatchInvoiceSummary
import co.zw.nissangtr.management.rpc.DnLineInput
import co.zw.nissangtr.management.rpc.OptimizedDriverStop
import co.zw.nissangtr.management.rpc.PanicEventSummary
import co.zw.nissangtr.management.rpc.PickListLineSummary
import co.zw.nissangtr.management.rpc.PickListSummary
import co.zw.nissangtr.management.rpc.RpcClient
import co.zw.nissangtr.management.rpc.RpcNames
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class DispatchUiState(
    val deliveryNotes: List<DeliveryNoteSummary> = emptyList(),
    val pickLists: List<PickListSummary> = emptyList(),
    /** Posted dispatch invoices — tap to fill sales invoice id. */
    val dispatchInvoices: List<DispatchInvoiceSummary> = emptyList(),
    /** Staff visibility — status + DN link / unassigned (not assign picker). */
    val deliveryJobs: List<DeliveryJobDeskSummary> = emptyList(),
    /** Lines for [selectedPickListId] — confirm / DN without UUID paste. */
    val pickLines: List<PickListLineSummary> = emptyList(),
    /** pick_list_line_id → qty draft string. */
    val pickQtyDraft: Map<String, String> = emptyMap(),
    val salesInvoiceId: String = "",
    val invoiceLineId: String = "",
    val qty: String = "1",
    val selectedPickListId: String? = null,
    val selectedDnId: String? = null,
    /** Active delivery job for assignment / staff live view. */
    val deliveryJobId: String = "",
    /** Driver UUID for route optimize + manual assign override. */
    val assigneeUserId: String = "",
    val assigneeSuggestions: List<DeliveryAssigneeSuggestion> = emptyList(),
    val optimizedStops: List<OptimizedDriverStop> = emptyList(),
    /** Staff VIEW only — last point + ETA (delivery app is sole GPS producer). */
    val liveTrack: DeliveryTrackPoint? = null,
    /** Share plaintext from update_delivery_job_status on dispatch (single mint). */
    val trackShareToken: String? = null,
    /** POD OTP plaintext from generate_delivery_pod_otp (dispatcher may read to customer). */
    val podOtp: String? = null,
    val pickupLat: String = "-17.8250",
    val pickupLng: String = "31.0330",
    val dropoffLat: String = "-17.8400",
    val dropoffLng: String = "31.0500",
    val panicEvents: List<PanicEventSummary> = emptyList(),
    val supportPhone: String = "",
    val busy: Boolean = false,
    val message: String? = null,
    val error: String? = null,
    /** Realtime not wired — panic inbox polls. */
    val pollNote: String = "Panic inbox polling ~5s (Realtime not enabled in this client)",
)

/**
 * Pick/DN logistics + dispatcher assignment / route / panic inbox.
 *
 * **GPS producer gated:** management must NOT start FGS or call
 * [RpcNames.INGEST_DELIVERY_LOCATION]. Sole producer is `apps/android-delivery`.
 * Staff may VIEW live last-point + ETA via [RpcNames.GET_DELIVERY_TRACK_POINT].
 */
class DispatchViewModel(
    private val rpc: RpcClient,
    supportPhone: String = "",
) : ViewModel() {
    private val _state = MutableStateFlow(DispatchUiState(supportPhone = supportPhone.trim()))
    val state: StateFlow<DispatchUiState> = _state.asStateFlow()

    private var panicPollJob: Job? = null

    init {
        refresh()
        startPanicPolling()
    }

    fun onSalesInvoiceIdChange(v: String) =
        _state.update { it.copy(salesInvoiceId = v, error = null) }

    fun onInvoiceLineIdChange(v: String) =
        _state.update { it.copy(invoiceLineId = v, error = null) }

    fun onQtyChange(v: String) =
        _state.update { it.copy(qty = v) }

    fun onPickQtyChange(pickListLineId: String, qty: String) =
        _state.update {
            it.copy(pickQtyDraft = it.pickQtyDraft + (pickListLineId to qty), error = null)
        }

    fun onDeliveryJobIdChange(v: String) =
        _state.update {
            it.copy(
                deliveryJobId = v,
                error = null,
                liveTrack = null,
                trackShareToken = null,
                podOtp = null,
            )
        }

    fun onAssigneeUserIdChange(v: String) =
        _state.update { it.copy(assigneeUserId = v, error = null) }

    fun onPickupLatChange(v: String) =
        _state.update { it.copy(pickupLat = v, error = null) }

    fun onPickupLngChange(v: String) =
        _state.update { it.copy(pickupLng = v, error = null) }

    fun onDropoffLatChange(v: String) =
        _state.update { it.copy(dropoffLat = v, error = null) }

    fun onDropoffLngChange(v: String) =
        _state.update { it.copy(dropoffLng = v, error = null) }

    fun selectDispatchInvoice(id: String) =
        _state.update {
            it.copy(salesInvoiceId = id, error = null, message = "Invoice selected")
        }

    fun selectPickList(id: String) {
        val pl = _state.value.pickLists.find { it.id == id }
        _state.update {
            it.copy(
                selectedPickListId = id,
                salesInvoiceId = pl?.salesInvoiceId ?: it.salesInvoiceId,
                error = null,
            )
        }
        loadPickLines(id)
    }

    fun selectPickLine(line: PickListLineSummary) {
        val draft = _state.value.pickQtyDraft[line.id]
        val qty = draft?.toDoubleOrNull()
            ?: line.qtyPicked
            ?: line.qtyRequested
        _state.update {
            it.copy(
                invoiceLineId = line.salesInvoiceLineId,
                qty = qty.toString(),
                error = null,
            )
        }
    }

    fun selectDn(id: String) =
        _state.update { it.copy(selectedDnId = id) }

    /** Link desk job → DN + job UUID fields (visibility only — no assign). */
    fun selectDeliveryJob(id: String) {
        val job = _state.value.deliveryJobs.find { it.id == id } ?: return
        _state.update {
            it.copy(
                deliveryJobId = job.id,
                selectedDnId = job.deliveryNoteId,
                error = null,
                message = "Job ${job.documentNumber ?: job.id.take(8)}… selected",
                liveTrack = null,
                trackShareToken = null,
                podOtp = null,
            )
        }
    }

    fun selectSuggestedAssignee(userId: String) =
        _state.update { it.copy(assigneeUserId = userId, error = null) }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            try {
                val dns = rpc.listDeliveryNotes()
                val pls = rpc.listPickLists()
                val jobs = runCatching { rpc.listDeliveryJobs() }.getOrDefault(emptyList())
                val invoices = runCatching { rpc.listDispatchInvoices() }
                    .getOrDefault(emptyList())
                val panics = runCatching { rpc.listOpenPanicEvents() }.getOrDefault(emptyList())
                val selectedPick = _state.value.selectedPickListId
                    ?: pls.firstOrNull()?.id
                val pickLines = if (selectedPick != null) {
                    runCatching { rpc.listPickListLines(selectedPick) }.getOrDefault(emptyList())
                } else {
                    emptyList()
                }
                _state.update {
                    it.copy(
                        busy = false,
                        deliveryNotes = dns,
                        pickLists = pls,
                        deliveryJobs = jobs,
                        dispatchInvoices = invoices,
                        panicEvents = panics,
                        selectedPickListId = selectedPick,
                        pickLines = pickLines,
                        pickQtyDraft = draftsFromLines(pickLines, it.pickQtyDraft),
                        salesInvoiceId = when {
                            it.salesInvoiceId.isNotBlank() -> it.salesInvoiceId
                            else -> invoices.firstOrNull()?.id
                                ?: pls.firstOrNull()?.salesInvoiceId
                                ?: ""
                        },
                        selectedDnId = it.selectedDnId ?: dns.firstOrNull()?.id,
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(busy = false, error = e.message ?: "refresh failed")
                }
            }
        }
    }

    fun createPickList() {
        val invoiceId = _state.value.salesInvoiceId.trim()
        if (invoiceId.isEmpty()) {
            _state.update { it.copy(error = "Sales invoice UUID required") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null, message = null) }
            try {
                val id = rpc.createPickList(invoiceId, linesJson = null)
                _state.update {
                    it.copy(
                        busy = false,
                        selectedPickListId = id,
                        message = "${RpcNames.CREATE_PICK_LIST} → $id",
                    )
                }
                refresh()
            } catch (e: Exception) {
                _state.update {
                    it.copy(busy = false, error = e.message ?: "create pick failed")
                }
            }
        }
    }

    fun confirmSelectedPick() {
        val pickId = _state.value.selectedPickListId
        if (pickId.isNullOrBlank()) {
            _state.update { it.copy(error = "Select a pick list") }
            return
        }
        val s = _state.value
        val lines = if (s.pickLines.isNotEmpty()) {
            s.pickLines.map { line ->
                val qty = s.pickQtyDraft[line.id]?.toDoubleOrNull()
                    ?: line.qtyPicked
                    ?: line.qtyRequested
                ConfirmPickLineInput(
                    pickListLineId = line.id,
                    qtyPicked = qty,
                )
            }
        } else {
            val lineId = s.invoiceLineId.trim()
            val qty = s.qty.toDoubleOrNull()
            if (lineId.isEmpty() || qty == null || qty < 0) {
                _state.update { it.copy(error = "Load pick lines or enter invoice line UUID + qty") }
                return
            }
            listOf(
                ConfirmPickLineInput(
                    salesInvoiceLineId = lineId,
                    qtyPicked = qty,
                ),
            )
        }
        if (lines.any { it.qtyPicked < 0 }) {
            _state.update { it.copy(error = "qty_picked must be ≥ 0") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null, message = null) }
            try {
                val id = rpc.confirmPickLines(pickId, lines)
                _state.update {
                    it.copy(
                        busy = false,
                        message = "${RpcNames.CONFIRM_PICK_LINES} → $id (${lines.size} line(s))",
                    )
                }
                loadPickLines(pickId)
                refresh()
            } catch (e: Exception) {
                _state.update {
                    it.copy(busy = false, error = e.message ?: "confirm pick failed")
                }
            }
        }
    }

    fun createDeliveryNote() {
        val s = _state.value
        val invoiceId = s.salesInvoiceId.trim().ifBlank {
            s.pickLists.find { it.id == s.selectedPickListId }?.salesInvoiceId.orEmpty()
        }
        val lines = if (s.pickLines.isNotEmpty()) {
            s.pickLines.mapNotNull { line ->
                val qty = s.pickQtyDraft[line.id]?.toDoubleOrNull()
                    ?: line.qtyPicked
                    ?: line.qtyRequested
                if (qty > 0) DnLineInput(line.salesInvoiceLineId, qty) else null
            }
        } else {
            val lineId = s.invoiceLineId.trim()
            val qty = s.qty.toDoubleOrNull()
            if (lineId.isEmpty() || qty == null || qty <= 0) {
                emptyList()
            } else {
                listOf(DnLineInput(lineId, qty))
            }
        }
        if (invoiceId.isEmpty() || lines.isEmpty()) {
            _state.update {
                it.copy(error = "Invoice + at least one DN line (qty > 0) required")
            }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null, message = null) }
            try {
                val id = rpc.createDeliveryNote(
                    salesInvoiceId = invoiceId,
                    lines = lines,
                    pickListId = s.selectedPickListId,
                )
                _state.update {
                    it.copy(
                        busy = false,
                        selectedDnId = id,
                        message = "${RpcNames.CREATE_DELIVERY_NOTE} → $id (${lines.size} line(s))",
                    )
                }
                refresh()
            } catch (e: Exception) {
                _state.update {
                    it.copy(busy = false, error = e.message ?: "create DN failed")
                }
            }
        }
    }

    fun submitSelectedDn() {
        val dnId = _state.value.selectedDnId
        if (dnId.isNullOrBlank()) {
            _state.update { it.copy(error = "Select a delivery note") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null, message = null) }
            try {
                val id = rpc.submitDeliveryNote(dnId)
                _state.update {
                    it.copy(
                        busy = false,
                        message = "${RpcNames.SUBMIT_DELIVERY_NOTE} → $id",
                    )
                }
                refresh()
            } catch (e: Exception) {
                _state.update {
                    it.copy(busy = false, error = e.message ?: "submit DN failed")
                }
            }
        }
    }

    fun cancelSelectedDn() {
        val dnId = _state.value.selectedDnId
        if (dnId.isNullOrBlank()) {
            _state.update { it.copy(error = "Select a delivery note") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null, message = null) }
            try {
                val id = rpc.cancelDeliveryNote(dnId)
                _state.update {
                    it.copy(
                        busy = false,
                        message = "${RpcNames.CANCEL_DELIVERY_NOTE} → $id",
                    )
                }
                refresh()
            } catch (e: Exception) {
                _state.update {
                    it.copy(busy = false, error = e.message ?: "cancel DN failed")
                }
            }
        }
    }

    /** Create a delivery job from the selected submitted DN. */
    fun createDeliveryJob() {
        val dnId = _state.value.selectedDnId
        if (dnId.isNullOrBlank()) {
            _state.update { it.copy(error = "Select a delivery note (submitted)") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null, message = null) }
            try {
                val id = rpc.createDeliveryJob(deliveryNoteId = dnId)
                // Best-effort coords via set_delivery_job_geo (Fake + Live).
                val coordMsg = runCatching { applyCoordsIfPossible(id) }
                    .fold(
                        onSuccess = { it },
                        onFailure = { e -> "coords skipped: ${e.message}" },
                    )
                _state.update {
                    it.copy(
                        busy = false,
                        deliveryJobId = id,
                        trackShareToken = null,
                        podOtp = null,
                        message = "${RpcNames.CREATE_DELIVERY_JOB} → $id" +
                            (coordMsg?.let { c -> " · $c" } ?: ""),
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(busy = false, error = e.message ?: "create delivery job failed")
                }
            }
        }
    }

    fun saveJobCoords() {
        val jobId = _state.value.deliveryJobId.trim()
        if (jobId.isEmpty()) {
            _state.update { it.copy(error = "Delivery job UUID required for coords") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null, message = null) }
            try {
                val msg = applyCoordsIfPossible(jobId)
                    ?: "Coords saved"
                _state.update {
                    it.copy(busy = false, message = msg)
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(busy = false, error = e.message ?: "save coords failed")
                }
            }
        }
    }

    fun markJobDispatched() {
        val jobId = _state.value.deliveryJobId.trim()
        if (jobId.isEmpty()) {
            _state.update { it.copy(error = "Delivery job UUID required") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null, message = null) }
            try {
                // Single mint path: status RPC returns track_token on dispatch.
                // Do NOT call mint_delivery_track_token here — that revokes SMS token.
                val result = rpc.updateDeliveryJobStatus(jobId, DeliveryJobStatus.DISPATCHED)
                _state.update {
                    it.copy(
                        busy = false,
                        trackShareToken = result.trackToken,
                        message = "${RpcNames.UPDATE_DELIVERY_JOB_STATUS} → dispatched " +
                            "(${result.deliveryJobId})" +
                            if (result.trackToken != null) {
                                " · share token ready (below)"
                            } else {
                                " · no track_token in response"
                            },
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(busy = false, error = e.message ?: "dispatch status failed")
                }
            }
        }
    }

    /** Intentional remint / rotate only — revokes prior SMS/share token. */
    fun rotateShareToken() {
        val jobId = _state.value.deliveryJobId.trim()
        if (jobId.isEmpty()) {
            _state.update { it.copy(error = "Delivery job UUID required") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null, message = null) }
            try {
                val token = rpc.mintDeliveryTrackToken(jobId)
                _state.update {
                    it.copy(
                        busy = false,
                        trackShareToken = token,
                        message = "${RpcNames.MINT_DELIVERY_TRACK_TOKEN} → rotated " +
                            "(prior SMS/share token revoked)",
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(busy = false, error = e.message ?: "rotate track token failed")
                }
            }
        }
    }

    fun generatePodOtp() {
        val jobId = _state.value.deliveryJobId.trim()
        if (jobId.isEmpty()) {
            _state.update { it.copy(error = "Delivery job UUID required") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null, message = null) }
            try {
                val otp = rpc.generateDeliveryPodOtp(jobId)
                _state.update {
                    it.copy(
                        busy = false,
                        podOtp = otp,
                        message = "${RpcNames.GENERATE_DELIVERY_POD_OTP} → $otp " +
                            "(read to customer; hash-only in DB)",
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(busy = false, error = e.message ?: "generate POD OTP failed")
                }
            }
        }
    }

    fun suggestAssignees() {
        val jobId = _state.value.deliveryJobId.trim()
        if (jobId.isEmpty()) {
            _state.update { it.copy(error = "Delivery job UUID required for suggestions") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null, message = null) }
            try {
                val list = rpc.suggestDeliveryAssignees(jobId, limit = 5)
                _state.update {
                    it.copy(
                        busy = false,
                        assigneeSuggestions = list,
                        message = "${RpcNames.SUGGEST_DELIVERY_ASSIGNEES} → ${list.size} driver(s)",
                        assigneeUserId = list.firstOrNull()?.userId ?: it.assigneeUserId,
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(busy = false, error = e.message ?: "suggest assignees failed")
                }
            }
        }
    }

    /** Assign selected / typed driver. [override] bypasses capacity/shift eligibility. */
    fun assignJob(override: Boolean) {
        val jobId = _state.value.deliveryJobId.trim()
        val assignee = _state.value.assigneeUserId.trim()
        if (jobId.isEmpty() || assignee.isEmpty()) {
            _state.update { it.copy(error = "Delivery job UUID + assignee driver UUID required") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null, message = null) }
            try {
                val id = rpc.assignDeliveryJob(jobId, assignee, override = override)
                _state.update {
                    it.copy(
                        busy = false,
                        message = "${RpcNames.ASSIGN_DELIVERY_JOB} → $id" +
                            if (override) " (manual override)" else "",
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(busy = false, error = e.message ?: "assign failed")
                }
            }
        }
    }

    fun optimizeStops() {
        val driverId = _state.value.assigneeUserId.trim()
        if (driverId.isEmpty()) {
            _state.update { it.copy(error = "Driver UUID required to optimize stops") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null, message = null) }
            try {
                val stops = rpc.optimizeDriverStops(driverId)
                _state.update {
                    it.copy(
                        busy = false,
                        optimizedStops = stops,
                        message = "${RpcNames.OPTIMIZE_DRIVER_STOPS} → ${stops.size} stop(s)",
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(busy = false, error = e.message ?: "optimize stops failed")
                }
            }
        }
    }

    /** Staff VIEW live last-point + ETA — does not start FGS or ingest. */
    fun refreshLiveTrack() {
        val jobId = _state.value.deliveryJobId.trim()
        if (jobId.isEmpty()) {
            _state.update { it.copy(error = "Delivery job UUID required to view location") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null, message = null) }
            try {
                val point = rpc.getDeliveryTrackPoint(jobId)
                _state.update {
                    it.copy(
                        busy = false,
                        liveTrack = point,
                        message = if (point == null) {
                            "No live point (job must be dispatched; GPS from delivery app)"
                        } else {
                            "${RpcNames.GET_DELIVERY_TRACK_POINT} → " +
                                "%.5f, %.5f".format(point.lat, point.lng) +
                                (point.etaAt?.let { " ETA $it" } ?: "")
                        },
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(busy = false, error = e.message ?: "live track failed")
                }
            }
        }
    }

    /**
     * Hard-gated: management must not produce GPS.
     * Sole producer: `apps/android-delivery` → FGS → [RpcNames.INGEST_DELIVERY_LOCATION].
     */
    fun startTracking() {
        _state.update {
            it.copy(
                error = DRIVER_GPS_PRODUCER_BLOCKED_MSG,
                message = null,
            )
        }
    }

    fun stopTracking() {
        // No-op — producer UI removed; delivery app owns FGS lifecycle.
    }

    fun acknowledgePanic(panicId: String) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null, message = null) }
            try {
                val id = rpc.acknowledgePanicEvent(panicId)
                val panics = rpc.listOpenPanicEvents()
                _state.update {
                    it.copy(
                        busy = false,
                        panicEvents = panics,
                        message = "Panic acknowledged → $id",
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(busy = false, error = e.message ?: "acknowledge panic failed")
                }
            }
        }
    }

    fun refreshPanicInbox() {
        viewModelScope.launch {
            try {
                val panics = rpc.listOpenPanicEvents()
                _state.update { it.copy(panicEvents = panics) }
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message ?: "panic list failed") }
            }
        }
    }

    private fun loadPickLines(pickListId: String) {
        viewModelScope.launch {
            try {
                val lines = rpc.listPickListLines(pickListId)
                _state.update {
                    it.copy(
                        pickLines = lines,
                        pickQtyDraft = draftsFromLines(lines, it.pickQtyDraft),
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        pickLines = emptyList(),
                        error = e.message ?: "pick lines failed",
                    )
                }
            }
        }
    }

    private fun startPanicPolling() {
        panicPollJob?.cancel()
        panicPollJob = viewModelScope.launch {
            while (isActive) {
                delay(5_000L)
                runCatching {
                    val panics = rpc.listOpenPanicEvents()
                    _state.update { it.copy(panicEvents = panics) }
                }
            }
        }
    }

    /**
     * Persist pickup/dropoff from UI fields when parseable.
     * Incomplete lat/lng pairs are sent as null (clears that endpoint on Live).
     * @return status string, or null when no coords entered.
     */
    private suspend fun applyCoordsIfPossible(jobId: String): String? {
        val s = _state.value
        val pLat = s.pickupLat.trim().toDoubleOrNull()
        val pLng = s.pickupLng.trim().toDoubleOrNull()
        val dLat = s.dropoffLat.trim().toDoubleOrNull()
        val dLng = s.dropoffLng.trim().toDoubleOrNull()
        if (pLat == null && pLng == null && dLat == null && dLng == null) return null
        val pickupOk = pLat != null && pLng != null
        val dropoffOk = dLat != null && dLng != null
        if (!pickupOk && (pLat != null || pLng != null)) {
            error("pickup lat and lng must both be set")
        }
        if (!dropoffOk && (dLat != null || dLng != null)) {
            error("dropoff lat and lng must both be set")
        }
        rpc.setDeliveryJobCoords(
            deliveryJobId = jobId,
            pickupLat = if (pickupOk) pLat else null,
            pickupLng = if (pickupOk) pLng else null,
            dropoffLat = if (dropoffOk) dLat else null,
            dropoffLng = if (dropoffOk) dLng else null,
        )
        return "${RpcNames.SET_DELIVERY_JOB_GEO} → coords set for suggest+ETA"
    }

    override fun onCleared() {
        panicPollJob?.cancel()
        super.onCleared()
    }

    companion object {
        /**
         * Hard gate — must stay false. Driver GPS FGS / ingest lives only in
         * `apps/android-delivery`. Management is subscribe/view for locations.
         */
        const val ALLOW_DRIVER_GPS_PRODUCER: Boolean = false

        const val DRIVER_GPS_PRODUCER_BLOCKED_MSG: String =
            "Driver GPS producer gated: use apps/android-delivery " +
                "(FGS → ingest_delivery_location). Management is view-only."

        fun draftsFromLines(
            lines: List<PickListLineSummary>,
            existing: Map<String, String>,
        ): Map<String, String> {
            val next = existing.toMutableMap()
            for (line in lines) {
                if (!next.containsKey(line.id)) {
                    val seed = line.qtyPicked?.takeIf { it > 0 } ?: line.qtyRequested
                    next[line.id] = seed.toString()
                }
            }
            return next
        }

        fun factory(
            rpc: RpcClient,
            supportPhone: String = "",
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    DispatchViewModel(rpc, supportPhone) as T
            }
    }
}
