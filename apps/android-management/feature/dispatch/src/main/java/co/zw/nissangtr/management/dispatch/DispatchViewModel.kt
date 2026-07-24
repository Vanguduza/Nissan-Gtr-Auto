package co.zw.nissangtr.management.dispatch

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import co.zw.nissangtr.management.rpc.ConfirmPickLineInput
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

data class DispatchUiState(
    val deliveryNotes: List<DeliveryNoteSummary> = emptyList(),
    val pickLists: List<PickListSummary> = emptyList(),
    val salesInvoiceId: String = "",
    val invoiceLineId: String = "",
    val qty: String = "1",
    val selectedPickListId: String? = null,
    val selectedDnId: String? = null,
    val busy: Boolean = false,
    val message: String? = null,
    val error: String? = null,
)

class DispatchViewModel(
    private val rpc: RpcClient,
) : ViewModel() {
    private val _state = MutableStateFlow(DispatchUiState())
    val state: StateFlow<DispatchUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun onSalesInvoiceIdChange(v: String) =
        _state.update { it.copy(salesInvoiceId = v, error = null) }

    fun onInvoiceLineIdChange(v: String) =
        _state.update { it.copy(invoiceLineId = v, error = null) }

    fun onQtyChange(v: String) =
        _state.update { it.copy(qty = v) }

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

    companion object {
        fun factory(rpc: RpcClient): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    DispatchViewModel(rpc) as T
            }
    }
}
