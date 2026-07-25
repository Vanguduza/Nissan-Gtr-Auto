package co.zw.nissangtr.customer.compare

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import co.zw.nissangtr.customer.rpc.CompareItem
import co.zw.nissangtr.customer.rpc.RpcClient
import co.zw.nissangtr.customer.rpc.RpcNames
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CompareUiState(
    val items: List<CompareItem> = emptyList(),
    val oem: String = "15208-65F0C",
    val usingGuestStore: Boolean = false,
    val busy: Boolean = false,
    val message: String? = null,
    val error: String? = null,
)

class CompareViewModel(
    private val rpc: RpcClient,
    private val appContext: Context,
    private val isSignedIn: Boolean,
) : ViewModel() {
    private val _state = MutableStateFlow(CompareUiState())
    val state: StateFlow<CompareUiState> = _state.asStateFlow()

    private val preferGuest: Boolean get() = !isSignedIn

    init {
        refresh()
    }

    fun onOemChange(v: String) = _state.update { it.copy(oem = v, error = null) }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            try {
                if (preferGuest) {
                    val items = GuestCompareStore.asCompareItems(appContext)
                    _state.update {
                        it.copy(busy = false, items = items, usingGuestStore = true)
                    }
                } else {
                    val items = rpc.listCompareItems()
                    GuestCompareStore.writeOems(appContext, items.map { it.oemPartNumber })
                    _state.update {
                        it.copy(busy = false, items = items, usingGuestStore = false)
                    }
                }
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, error = e.message ?: "list failed") }
            }
        }
    }

    fun add() {
        val oem = _state.value.oem.trim()
        if (oem.isEmpty()) {
            _state.update { it.copy(error = "OEM required") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null, message = null) }
            try {
                if (preferGuest) {
                    GuestCompareStore.addOem(appContext, oem)
                    val items = GuestCompareStore.asCompareItems(appContext)
                    _state.update {
                        it.copy(
                            busy = false,
                            items = items,
                            usingGuestStore = true,
                            message = "Added to GuestCompareStore",
                        )
                    }
                } else {
                    rpc.addCustomerCompareItem(oem = oem)
                    val items = rpc.listCompareItems()
                    GuestCompareStore.writeOems(appContext, items.map { it.oemPartNumber })
                    _state.update {
                        it.copy(
                            busy = false,
                            items = items,
                            usingGuestStore = false,
                            message = RpcNames.ADD_CUSTOMER_COMPARE_ITEM,
                        )
                    }
                }
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, error = e.message ?: "add failed") }
            }
        }
    }

    fun remove(item: CompareItem) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null, message = null) }
            try {
                if (preferGuest) {
                    GuestCompareStore.removeOem(appContext, item.oemPartNumber)
                    val items = GuestCompareStore.asCompareItems(appContext)
                    _state.update {
                        it.copy(
                            busy = false,
                            items = items,
                            message = "Removed from GuestCompareStore",
                        )
                    }
                } else {
                    rpc.removeCustomerCompareItem(compareId = item.id)
                    val items = rpc.listCompareItems()
                    GuestCompareStore.writeOems(appContext, items.map { it.oemPartNumber })
                    _state.update {
                        it.copy(
                            busy = false,
                            items = items,
                            message = RpcNames.REMOVE_CUSTOMER_COMPARE_ITEM,
                        )
                    }
                }
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, error = e.message ?: "remove failed") }
            }
        }
    }

    companion object {
        fun factory(
            rpc: RpcClient,
            context: Context,
            isSignedIn: Boolean,
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    CompareViewModel(rpc, context.applicationContext, isSignedIn) as T
            }
    }
}
