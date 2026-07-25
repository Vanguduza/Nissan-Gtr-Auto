package co.zw.nissangtr.customer.wishlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import co.zw.nissangtr.customer.rpc.RpcClient
import co.zw.nissangtr.customer.rpc.RpcNames
import co.zw.nissangtr.customer.rpc.WishlistItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class WishlistUiState(
    val items: List<WishlistItem> = emptyList(),
    val oem: String = "15208-65F0C",
    val busy: Boolean = false,
    val message: String? = null,
    val error: String? = null,
)

class WishlistViewModel(
    private val rpc: RpcClient,
) : ViewModel() {
    private val _state = MutableStateFlow(WishlistUiState())
    val state: StateFlow<WishlistUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun onOemChange(v: String) = _state.update { it.copy(oem = v, error = null) }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            try {
                val list = rpc.listWishlist()
                _state.update { it.copy(busy = false, items = list) }
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
                val id = rpc.addCustomerWishlistItem(oem = oem)
                val list = rpc.listWishlist()
                _state.update {
                    it.copy(
                        busy = false,
                        items = list,
                        message = "${RpcNames.ADD_CUSTOMER_WISHLIST_ITEM} → $id",
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, error = e.message ?: "add failed") }
            }
        }
    }

    fun remove(item: WishlistItem) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null, message = null) }
            try {
                rpc.removeCustomerWishlistItem(wishlistId = item.id)
                val list = rpc.listWishlist()
                _state.update {
                    it.copy(
                        busy = false,
                        items = list,
                        message = "${RpcNames.REMOVE_CUSTOMER_WISHLIST_ITEM} → ${item.id}",
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, error = e.message ?: "remove failed") }
            }
        }
    }

    fun setNotify(item: WishlistItem, notify: Boolean) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null, message = null) }
            try {
                rpc.setWishlistNotifyWhenInStock(notify = notify, wishlistId = item.id)
                val list = rpc.listWishlist()
                _state.update {
                    it.copy(
                        busy = false,
                        items = list,
                        message = "${RpcNames.SET_WISHLIST_NOTIFY_WHEN_IN_STOCK} → $notify",
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, error = e.message ?: "notify failed") }
            }
        }
    }

    fun moveToCart(item: WishlistItem) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null, message = null) }
            try {
                val lineId = rpc.wishlistMoveToCart(
                    wishlistId = item.id,
                    qty = 1.0,
                    removeFromWishlist = true,
                )
                val list = rpc.listWishlist()
                _state.update {
                    it.copy(
                        busy = false,
                        items = list,
                        message = "${RpcNames.WISHLIST_MOVE_TO_CART} → line ${lineId.take(8)}…",
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, error = e.message ?: "move failed") }
            }
        }
    }

    companion object {
        fun factory(rpc: RpcClient): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    WishlistViewModel(rpc) as T
            }
    }
}
