package co.zw.nissangtr.management.dispatch

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import co.zw.nissangtr.bridges.location.GpsBridge
import co.zw.nissangtr.bridges.location.GpsWatchHandle
import co.zw.nissangtr.bridges.location.LocationPermissionStatus
import co.zw.nissangtr.bridges.location.toDeliveryLocationIngest
import co.zw.nissangtr.management.rpc.ConfirmPickLineInput
import co.zw.nissangtr.management.rpc.DeliveryJobStatus
import co.zw.nissangtr.management.rpc.DeliveryNoteSummary
import co.zw.nissangtr.management.rpc.DnLineInput
import co.zw.nissangtr.management.rpc.PickListSummary
import co.zw.nissangtr.management.rpc.RpcClient
import co.zw.nissangtr.management.rpc.RpcNames
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class DispatchUiState(
    val deliveryNotes: List<DeliveryNoteSummary> = emptyList(),
    val pickLists: List<PickListSummary> = emptyList(),
    val salesInvoiceId: String = "",
    val invoiceLineId: String = "",
    val qty: String = "1",
    val selectedPickListId: String? = null,
    val selectedDnId: String? = null,
    /** Active delivery job for GPS trail (staff/driver). */
    val deliveryJobId: String = "",
    val tracking: Boolean = false,
    val lastIngestId: String? = null,
    val lastLatLng: String? = null,
    val ingestCount: Int = 0,
    val busy: Boolean = false,
    val message: String? = null,
    val error: String? = null,
)

/**
 * Pick/DN logistics + Bridge-First delivery GPS.
 * Compose only calls start/stop — all FusedLocation work stays in [GpsBridge].
 */
class DispatchViewModel(
    private val rpc: RpcClient,
    private val gps: GpsBridge,
) : ViewModel() {
    private val _state = MutableStateFlow(DispatchUiState())
    val state: StateFlow<DispatchUiState> = _state.asStateFlow()

    private val throttle = DeliveryLocationIngestThrottle()
    private val ingestMutex = Mutex()
    private var watchHandle: GpsWatchHandle? = null

    init {
        refresh()
    }

    fun onSalesInvoiceIdChange(v: String) =
        _state.update { it.copy(salesInvoiceId = v, error = null) }

    fun onInvoiceLineIdChange(v: String) =
        _state.update { it.copy(invoiceLineId = v, error = null) }

    fun onQtyChange(v: String) =
        _state.update { it.copy(qty = v) }

    fun onDeliveryJobIdChange(v: String) =
        _state.update { it.copy(deliveryJobId = v, error = null) }

    fun selectPickList(id: String) =
        _state.update { it.copy(selectedPickListId = id) }

    fun selectDn(id: String) =
        _state.update { it.copy(selectedDnId = id) }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            try {
                val dns = rpc.listDeliveryNotes()
                val pls = rpc.listPickLists()
                _state.update {
                    it.copy(
                        busy = false,
                        deliveryNotes = dns,
                        pickLists = pls,
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
        val lineId = _state.value.invoiceLineId.trim()
        val qty = _state.value.qty.toDoubleOrNull()
        if (pickId.isNullOrBlank()) {
            _state.update { it.copy(error = "Select a pick list") }
            return
        }
        if (lineId.isEmpty() || qty == null || qty < 0) {
            _state.update { it.copy(error = "Invoice line UUID + qty_picked required") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null, message = null) }
            try {
                val id = rpc.confirmPickLines(
                    pickId,
                    listOf(
                        ConfirmPickLineInput(
                            salesInvoiceLineId = lineId,
                            qtyPicked = qty,
                        ),
                    ),
                )
                _state.update {
                    it.copy(
                        busy = false,
                        message = "${RpcNames.CONFIRM_PICK_LINES} → $id",
                    )
                }
                refresh()
            } catch (e: Exception) {
                _state.update {
                    it.copy(busy = false, error = e.message ?: "confirm pick failed")
                }
            }
        }
    }

    fun createDeliveryNote() {
        val invoiceId = _state.value.salesInvoiceId.trim()
        val lineId = _state.value.invoiceLineId.trim()
        val qty = _state.value.qty.toDoubleOrNull()
        if (invoiceId.isEmpty() || lineId.isEmpty() || qty == null || qty <= 0) {
            _state.update {
                it.copy(error = "Invoice UUID, line UUID, and qty > 0 required for DN")
            }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null, message = null) }
            try {
                val id = rpc.createDeliveryNote(
                    salesInvoiceId = invoiceId,
                    lines = listOf(DnLineInput(lineId, qty)),
                    pickListId = _state.value.selectedPickListId,
                )
                _state.update {
                    it.copy(
                        busy = false,
                        selectedDnId = id,
                        message = "${RpcNames.CREATE_DELIVERY_NOTE} → $id",
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
                _state.update {
                    it.copy(
                        busy = false,
                        deliveryJobId = id,
                        message = "${RpcNames.CREATE_DELIVERY_JOB} → $id",
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(busy = false, error = e.message ?: "create delivery job failed")
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
                val id = rpc.updateDeliveryJobStatus(jobId, DeliveryJobStatus.DISPATCHED)
                _state.update {
                    it.copy(
                        busy = false,
                        message = "${RpcNames.UPDATE_DELIVERY_JOB_STATUS} → dispatched ($id)",
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(busy = false, error = e.message ?: "dispatch status failed")
                }
            }
        }
    }

    /**
     * Request location via bridge → watchPosition → throttle ≥5s → ingest RPC.
     * No GPS logic in Compose beyond invoking this.
     */
    fun startTracking() {
        val jobId = _state.value.deliveryJobId.trim()
        if (jobId.isEmpty()) {
            _state.update { it.copy(error = "Delivery job UUID required to track") }
            return
        }
        if (_state.value.tracking) return

        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null, message = null) }
            try {
                val status = gps.requestLocationPermission()
                if (status != LocationPermissionStatus.GRANTED &&
                    status != LocationPermissionStatus.APPROXIMATE
                ) {
                    _state.update {
                        it.copy(
                            busy = false,
                            error = "Location permission required ($status). Grant in system dialog.",
                        )
                    }
                    return@launch
                }

                throttle.reset()
                val handle = gps.watchPosition(
                    onUpdate = { coord ->
                        if (!throttle.tryAccept()) return@watchPosition
                        viewModelScope.launch {
                            ingestMutex.withLock {
                                try {
                                    val payload = toDeliveryLocationIngest(jobId, coord)
                                    val id = rpc.ingestDeliveryLocation(
                                        deliveryJobId = payload.deliveryJobId,
                                        lat = payload.lat,
                                        lng = payload.lng,
                                        recordedAt = payload.recordedAt,
                                        accuracyM = payload.accuracyM,
                                    )
                                    _state.update {
                                        it.copy(
                                            lastIngestId = id,
                                            lastLatLng = "%.5f, %.5f".format(payload.lat, payload.lng),
                                            ingestCount = it.ingestCount + 1,
                                            message = "${RpcNames.INGEST_DELIVERY_LOCATION} → $id",
                                            error = null,
                                        )
                                    }
                                } catch (e: Exception) {
                                    _state.update {
                                        it.copy(error = e.message ?: "ingest failed")
                                    }
                                }
                            }
                        }
                    },
                    onError = { msg ->
                        _state.update { it.copy(error = msg) }
                    },
                )
                watchHandle = handle
                _state.update {
                    it.copy(
                        busy = false,
                        tracking = true,
                        message = "Tracking job $jobId (bridge GPS, ≥5s throttle)",
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(busy = false, tracking = false, error = e.message ?: "start tracking failed")
                }
            }
        }
    }

    fun stopTracking() {
        viewModelScope.launch {
            val handle = watchHandle
            watchHandle = null
            try {
                handle?.stop()
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message ?: "stop tracking failed") }
            }
            _state.update {
                it.copy(
                    tracking = false,
                    busy = false,
                    message = "Tracking stopped",
                )
            }
        }
    }

    override fun onCleared() {
        val handle = watchHandle
        watchHandle = null
        if (handle != null) {
            viewModelScope.launch {
                runCatching { handle.stop() }
            }
        }
        super.onCleared()
    }

    companion object {
        fun factory(rpc: RpcClient, gps: GpsBridge): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    DispatchViewModel(rpc, gps) as T
            }
    }
}
