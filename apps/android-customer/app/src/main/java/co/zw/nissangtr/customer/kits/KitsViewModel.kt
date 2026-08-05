package co.zw.nissangtr.customer.kits

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import co.zw.nissangtr.customer.rpc.KitListItem
import co.zw.nissangtr.customer.rpc.RpcClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class KitsUiState(
    val kits: List<KitListItem> = emptyList(),
    val busy: Boolean = false,
    val error: String? = null,
)

class KitsViewModel(
    private val rpc: RpcClient,
) : ViewModel() {
    private val _state = MutableStateFlow(KitsUiState())
    val state: StateFlow<KitsUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            try {
                val kits = rpc.listActiveKits(limit = 50)
                _state.update { it.copy(busy = false, kits = kits) }
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
                    KitsViewModel(rpc) as T
            }
    }
}
