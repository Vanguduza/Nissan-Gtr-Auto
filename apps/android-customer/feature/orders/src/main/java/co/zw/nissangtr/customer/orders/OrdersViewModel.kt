package co.zw.nissangtr.customer.orders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import co.zw.nissangtr.customer.rpc.CustomerOrder
import co.zw.nissangtr.customer.rpc.InvoiceSummary
import co.zw.nissangtr.customer.rpc.RpcClient
import co.zw.nissangtr.customer.rpc.RpcNames
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class OrdersUiState(
    val invoices: List<InvoiceSummary> = emptyList(),
    val selected: CustomerOrder? = null,
    val busy: Boolean = false,
    val message: String? = null,
    val error: String? = null,
)

class OrdersViewModel(
    private val rpc: RpcClient,
) : ViewModel() {
    private val _state = MutableStateFlow(OrdersUiState())
    val state: StateFlow<OrdersUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
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

    fun loadOrder(invoiceId: String) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null, message = null) }
            try {
                val order = rpc.getCustomerOrder(invoiceId)
                _state.update {
                    it.copy(
                        busy = false,
                        selected = order,
                        message = "${RpcNames.GET_CUSTOMER_ORDER} → ${order.documentNumber ?: order.invoiceId}",
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, error = e.message ?: "get order failed") }
            }
        }
    }

    companion object {
        fun factory(rpc: RpcClient): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    OrdersViewModel(rpc) as T
            }
    }
}
