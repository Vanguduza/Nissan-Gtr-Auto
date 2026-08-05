package co.zw.nissangtr.customer.wishlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import co.zw.nissangtr.customer.rpc.RpcClient
import co.zw.nissangtr.customer.rpc.WishlistItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Session-scoped wish-set — load once from [RpcClient.listWishlist], keep hearts
 * red across Home / Shop / PDP / Wishlist until remove.
 */
class WishlistStore(
    private val rpc: RpcClient,
) : ViewModel() {
    private val _items = MutableStateFlow<List<WishlistItem>>(emptyList())
    val items: StateFlow<List<WishlistItem>> = _items.asStateFlow()

    /** Uppercased OEM keys currently wished. */
    private val _oemKeys = MutableStateFlow<Set<String>>(emptySet())
    val oemKeys: StateFlow<Set<String>> = _oemKeys.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        refresh()
    }

    fun isLiked(oem: String?, stockItemId: String? = null): Boolean {
        val oemKey = oem?.trim()?.uppercase().orEmpty()
        if (oemKey.isNotEmpty() && oemKey in _oemKeys.value) return true
        val sid = stockItemId?.trim().orEmpty()
        if (sid.isNotEmpty()) {
            return _items.value.any { it.stockItemId == sid }
        }
        return false
    }

    fun refresh() {
        viewModelScope.launch {
            _busy.value = true
            _error.value = null
            try {
                applyList(rpc.listWishlist())
            } catch (e: Exception) {
                _error.value = e.message ?: "wishlist load failed"
            } finally {
                _busy.value = false
            }
        }
    }

    fun toggle(stockItemId: String, oem: String) {
        viewModelScope.launch {
            val oemKey = oem.trim().uppercase()
            val liked = isLiked(oem, stockItemId)
            _busy.value = true
            _error.value = null
            try {
                if (liked) {
                    val existing = _items.value.firstOrNull {
                        it.oemPartNumber.equals(oem, ignoreCase = true) ||
                            it.stockItemId == stockItemId
                    }
                    rpc.removeCustomerWishlistItem(
                        wishlistId = existing?.id,
                        stockItemId = stockItemId,
                        oem = oem,
                    )
                } else {
                    rpc.addCustomerWishlistItem(stockItemId = stockItemId, oem = oem)
                }
                applyList(rpc.listWishlist())
            } catch (e: Exception) {
                // Optimistic local flip when Fake/Live briefly fails after write — still re-sync.
                if (!liked) {
                    _oemKeys.update { it + oemKey }
                } else {
                    _oemKeys.update { it - oemKey }
                }
                _error.value = e.message ?: "wishlist update failed"
                runCatching { applyList(rpc.listWishlist()) }
            } finally {
                _busy.value = false
            }
        }
    }

    fun remove(item: WishlistItem) {
        viewModelScope.launch {
            _busy.value = true
            _error.value = null
            try {
                rpc.removeCustomerWishlistItem(
                    wishlistId = item.id,
                    stockItemId = item.stockItemId,
                    oem = item.oemPartNumber,
                )
                applyList(rpc.listWishlist())
            } catch (e: Exception) {
                _error.value = e.message ?: "remove failed"
            } finally {
                _busy.value = false
            }
        }
    }

    private fun applyList(list: List<WishlistItem>) {
        _items.value = list
        _oemKeys.value = list.map { it.oemPartNumber.trim().uppercase() }.filter { it.isNotEmpty() }.toSet()
    }

    companion object {
        fun factory(rpc: RpcClient): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    WishlistStore(rpc) as T
            }
    }
}
