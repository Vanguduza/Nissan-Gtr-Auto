package co.zw.nissangtr.customer.pay

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import co.zw.nissangtr.customer.rpc.ContipayMethod
import co.zw.nissangtr.customer.rpc.InvoiceSummary
import co.zw.nissangtr.customer.rpc.PaynowMethod
import co.zw.nissangtr.customer.rpc.RpcClient
import co.zw.nissangtr.customer.rpc.RpcNames
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PayUiState(
    val invoices: List<InvoiceSummary> = emptyList(),
    val invoiceId: String = "",
    val lastIntentId: String? = null,
    val lastProvider: String? = null,
    val busy: Boolean = false,
    val message: String? = null,
    val error: String? = null,
)

class PayIntentViewModel(
    private val rpc: RpcClient,
) : ViewModel() {
    private val _state = MutableStateFlow(PayUiState())
    val state: StateFlow<PayUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun onInvoiceIdChange(v: String) = _state.update { it.copy(invoiceId = v, error = null) }

    fun selectInvoice(id: String) = _state.update { it.copy(invoiceId = id, error = null) }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            try {
                val list = rpc.listOwnInvoices()
                val firstOpen = list.firstOrNull { it.total > it.amountPaid }
                _state.update {
                    it.copy(
                        busy = false,
                        invoices = list,
                        invoiceId = it.invoiceId.ifBlank { firstOpen?.id.orEmpty() },
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, error = e.message ?: "list failed") }
            }
        }
    }

    fun createContipay() {
        val invoiceId = _state.value.invoiceId.trim()
        if (invoiceId.isEmpty()) {
            _state.update { it.copy(error = "Invoice UUID required") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null, message = null) }
            try {
                val result = rpc.createCustomerContipayIntent(
                    salesInvoiceId = invoiceId,
                    method = ContipayMethod.ECOCASH,
                    metadataJson = """{"channel":"storefront","sales_invoice_id":"$invoiceId"}""",
                )
                _state.update {
                    it.copy(
                        busy = false,
                        lastIntentId = result.intentId,
                        lastProvider = result.provider,
                        message = "${RpcNames.CREATE_CUSTOMER_CONTIPAY_INTENT} → ${result.intentId}",
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, error = e.message ?: "contipay failed") }
            }
        }
    }

    fun createPaynow() {
        val invoiceId = _state.value.invoiceId.trim()
        if (invoiceId.isEmpty()) {
            _state.update { it.copy(error = "Invoice UUID required") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null, message = null) }
            try {
                val result = rpc.createCustomerPaynowIntent(
                    salesInvoiceId = invoiceId,
                    method = PaynowMethod.ECOCASH,
                    metadataJson = """{"channel":"storefront","sales_invoice_id":"$invoiceId"}""",
                )
                _state.update {
                    it.copy(
                        busy = false,
                        lastIntentId = result.intentId,
                        lastProvider = result.provider,
                        message = "${RpcNames.CREATE_CUSTOMER_PAYNOW_INTENT} → ${result.intentId}",
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, error = e.message ?: "paynow failed") }
            }
        }
    }

    companion object {
        fun factory(rpc: RpcClient): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    PayIntentViewModel(rpc) as T
            }
    }
}
