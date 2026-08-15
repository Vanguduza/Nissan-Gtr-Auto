package co.zw.nissangtr.customer.loyalty

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import co.zw.nissangtr.customer.rpc.LoyaltyBalance
import co.zw.nissangtr.customer.rpc.LoyaltyLedgerEntry
import co.zw.nissangtr.customer.rpc.RpcClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LoyaltyUiState(
    val balance: LoyaltyBalance? = null,
    val ledger: List<LoyaltyLedgerEntry> = emptyList(),
    val busy: Boolean = false,
    val error: String? = null,
)

class LoyaltyViewModel(
    private val rpc: RpcClient,
) : ViewModel() {
    private val _state = MutableStateFlow(LoyaltyUiState())
    val state: StateFlow<LoyaltyUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            try {
                val customer = rpc.loadOwnCustomer()
                    ?: error("Sign in and link a customer profile to view loyalty.")
                val balance = rpc.getLoyaltyBalance(customer.id)
                val ledger = rpc.listLoyaltyLedger(customer.id)
                _state.update {
                    it.copy(busy = false, balance = balance, ledger = ledger)
                }
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, error = e.message ?: "load failed") }
            }
        }
    }

    companion object {
        fun factory(rpc: RpcClient): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    LoyaltyViewModel(rpc) as T
            }
    }
}
