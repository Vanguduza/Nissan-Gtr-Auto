package co.zw.nissangtr.management.credit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import co.zw.nissangtr.management.rpc.CustomerCreditSnapshot
import co.zw.nissangtr.management.rpc.CustomerOption
import co.zw.nissangtr.management.rpc.FakeRpcClient
import co.zw.nissangtr.management.rpc.RpcClient
import co.zw.nissangtr.management.rpc.RpcNames
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CreditUiState(
    val query: String = "",
    val hits: List<CustomerOption> = emptyList(),
    val customerId: String = "",
    val customerName: String = "",
    val snapshot: CustomerCreditSnapshot? = null,
    val creditLimitInput: String = "",
    val creditHold: Boolean = false,
    val busy: Boolean = false,
    val message: String? = null,
    val error: String? = null,
)

/** Staff B2B credit surface — [RpcNames.SET_CUSTOMER_CREDIT] + named search. */
class CreditViewModel(
    private val rpc: RpcClient,
) : ViewModel() {
    private val _state = MutableStateFlow(CreditUiState())
    val state: StateFlow<CreditUiState> = _state.asStateFlow()

    init {
        if (rpc is FakeRpcClient) {
            _state.update {
                it.copy(
                    customerId = FakeRpcClient.FAKE_CUSTOMER_ID,
                    customerName = "Acme Motors (B2B)",
                    query = "Acme",
                )
            }
            load()
        }
    }

    fun onQueryChange(v: String) = _state.update { it.copy(query = v, error = null) }
    fun onCustomerIdChange(v: String) = _state.update { it.copy(customerId = v) }
    fun onCreditLimitInputChange(v: String) = _state.update { it.copy(creditLimitInput = v) }
    fun onCreditHoldChange(v: Boolean) = _state.update { it.copy(creditHold = v) }

    fun search() {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            try {
                val hits = rpc.searchCustomers(_state.value.query)
                _state.update { it.copy(busy = false, hits = hits) }
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, error = e.message ?: "search failed") }
            }
        }
    }

    fun selectCustomer(c: CustomerOption) {
        _state.update {
            it.copy(
                customerId = c.id,
                customerName = c.displayName,
                query = c.displayName,
                hits = emptyList(),
            )
        }
        load()
    }

    fun load() {
        val id = _state.value.customerId.trim()
        if (id.isEmpty()) {
            _state.update { it.copy(error = "Customer required") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            try {
                val snap = rpc.loadCustomerCredit(id)
                _state.update {
                    it.copy(
                        busy = false,
                        snapshot = snap,
                        creditLimitInput = snap?.displayCreditLimit()?.toString().orEmpty(),
                        creditHold = snap?.creditHold ?: false,
                        message = snap?.let {
                            "Open balance ${it.displayOpenBalance()} ${it.currency.rpcValue}"
                        },
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, error = e.message ?: "load failed") }
            }
        }
    }

    fun saveLimitAndHold() {
        val id = _state.value.customerId.trim()
        val limit = _state.value.creditLimitInput.trim().toDoubleOrNull()
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            try {
                val snap = rpc.setCustomerCredit(
                    customerId = id,
                    creditLimit = limit,
                    creditHold = _state.value.creditHold,
                )
                _state.update {
                    it.copy(
                        busy = false,
                        snapshot = snap,
                        creditLimitInput = snap.displayCreditLimit().toString(),
                        creditHold = snap.creditHold,
                        message = "${RpcNames.SET_CUSTOMER_CREDIT} → " +
                            "limit ${snap.displayCreditLimit()} ${snap.currency.rpcValue} · " +
                            "hold=${snap.creditHold} · open ${snap.displayOpenBalance()}",
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, error = e.message ?: "set credit failed") }
            }
        }
    }

    fun toggleHoldOnly() {
        val id = _state.value.customerId.trim()
        val next = !_state.value.creditHold
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            try {
                val snap = rpc.setCustomerCredit(customerId = id, creditHold = next)
                _state.update {
                    it.copy(
                        busy = false,
                        snapshot = snap,
                        creditHold = snap.creditHold,
                        message = "Hold → ${snap.creditHold} (${snap.currency.rpcValue})",
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, error = e.message ?: "hold failed") }
            }
        }
    }

    companion object {
        fun factory(rpc: RpcClient) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                CreditViewModel(rpc) as T
        }
    }
}
