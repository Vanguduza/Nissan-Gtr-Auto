package co.zw.nissangtr.customer.returns

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import co.zw.nissangtr.customer.rpc.InvoiceLineSummary
import co.zw.nissangtr.customer.rpc.InvoiceSummary
import co.zw.nissangtr.customer.rpc.ReturnCreditNoteLine
import co.zw.nissangtr.customer.rpc.RpcClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ReturnsUiState(
    val invoices: List<InvoiceSummary> = emptyList(),
    val selectedInvoiceId: String? = null,
    val lines: List<InvoiceLineSummary> = emptyList(),
    val selectedLineIds: Set<String> = emptySet(),
    val busy: Boolean = false,
    val message: String? = null,
    val error: String? = null,
)

class ReturnsViewModel(
    private val rpc: RpcClient,
) : ViewModel() {
    private val _state = MutableStateFlow(ReturnsUiState())
    val state: StateFlow<ReturnsUiState> = _state.asStateFlow()

    init {
        refreshInvoices()
    }

    fun refreshInvoices() {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            try {
                val list = rpc.listOwnInvoices()
                _state.update { it.copy(busy = false, invoices = list) }
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, error = e.message ?: "list failed") }
            }
        }
    }

    fun selectInvoice(invoiceId: String) {
        viewModelScope.launch {
            _state.update {
                it.copy(
                    busy = true,
                    selectedInvoiceId = invoiceId,
                    selectedLineIds = emptySet(),
                    message = null,
                    error = null,
                )
            }
            try {
                val lines = rpc.listInvoiceLines(invoiceId)
                _state.update { it.copy(busy = false, lines = lines) }
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, error = e.message ?: "lines failed") }
            }
        }
    }

    fun toggleLine(lineId: String) {
        _state.update { s ->
            val next = s.selectedLineIds.toMutableSet()
            if (next.contains(lineId)) next.remove(lineId) else next.add(lineId)
            s.copy(selectedLineIds = next, message = null)
        }
    }

    fun submitReturn() {
        val invoiceId = _state.value.selectedInvoiceId ?: return
        val selected = _state.value.lines.filter { _state.value.selectedLineIds.contains(it.id) }
        if (selected.isEmpty()) {
            _state.update { it.copy(error = "Select at least one line to return") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null, message = null) }
            try {
                val payload = selected.map {
                    ReturnCreditNoteLine(
                        stockItemId = it.stockItemId,
                        uomId = it.uomId,
                        qty = it.qty,
                    )
                }
                val cnId = rpc.postCustomerReturnCreditNote(invoiceId, payload)
                _state.update {
                    it.copy(
                        busy = false,
                        message = "Return credit note posted ($cnId). Faulty SKU routes to quarantine — not direct exchange.",
                        selectedLineIds = emptySet(),
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, error = e.message ?: "return failed") }
            }
        }
    }

    companion object {
        fun factory(rpc: RpcClient): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    ReturnsViewModel(rpc) as T
            }
    }
}
