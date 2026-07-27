package co.zw.nissangtr.customer.catalog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import co.zw.nissangtr.customer.rpc.CatalogListItem
import co.zw.nissangtr.customer.rpc.CatalogPartHit
import co.zw.nissangtr.customer.rpc.CatalogProduct
import co.zw.nissangtr.customer.rpc.RpcClient
import co.zw.nissangtr.customer.rpc.RpcNames
import co.zw.nissangtr.customer.rpc.SearchMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class CatalogScreenRoute {
    Home,
    SearchResults,
    Product,
}

data class CatalogUiState(
    val route: CatalogScreenRoute = CatalogScreenRoute.Home,
    val query: String = "",
    val searchMode: SearchMode = SearchMode.PART,
    val browseItems: List<CatalogListItem> = emptyList(),
    val searchHits: List<CatalogPartHit> = emptyList(),
    val product: CatalogProduct? = null,
    val addQty: String = "1",
    val busy: Boolean = false,
    val message: String? = null,
    val error: String? = null,
)

class CatalogViewModel(
    private val rpc: RpcClient,
) : ViewModel() {
    private val _state = MutableStateFlow(CatalogUiState())
    val state: StateFlow<CatalogUiState> = _state.asStateFlow()

    init {
        refreshBrowse()
    }

    fun onQueryChange(v: String) = _state.update { it.copy(query = v, error = null) }
    fun onSearchModeChange(mode: SearchMode) = _state.update { it.copy(searchMode = mode) }
    fun onAddQtyChange(v: String) = _state.update { it.copy(addQty = v, error = null) }

    fun refreshBrowse() {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            try {
                val browse = rpc.listCatalogBrowse(category = null, limit = 50)
                _state.update { it.copy(busy = false, browseItems = browse.items) }
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, error = e.message ?: "browse failed") }
            }
        }
    }

    fun runSearch() {
        val q = _state.value.query.trim()
        if (q.isEmpty()) {
            _state.update { it.copy(error = "Enter a search query") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null, message = null) }
            try {
                val mode = _state.value.searchMode
                val res = rpc.searchCatalog(mode, q)
                _state.update {
                    it.copy(
                        busy = false,
                        searchHits = res.parts,
                        route = CatalogScreenRoute.SearchResults,
                        message = "${RpcNames.SEARCH_CATALOG} → ${res.parts.size} hit(s)",
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, error = e.message ?: "search failed") }
            }
        }
    }

    fun openProduct(oem: String) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null, message = null) }
            try {
                val product = rpc.loadCatalogProduct(oem)
                _state.update {
                    it.copy(
                        busy = false,
                        product = product,
                        route = CatalogScreenRoute.Product,
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, error = e.message ?: "load PDP failed") }
            }
        }
    }

    fun navigateHome() {
        _state.update {
            it.copy(
                route = CatalogScreenRoute.Home,
                product = null,
                searchHits = emptyList(),
                message = null,
                error = null,
            )
        }
    }

    fun navigateBackFromProduct() {
        val prev = if (_state.value.searchHits.isNotEmpty()) {
            CatalogScreenRoute.SearchResults
        } else {
            CatalogScreenRoute.Home
        }
        _state.update { it.copy(route = prev, product = null, error = null, message = null) }
    }

    fun addToCart(onDone: () -> Unit = {}) {
        val product = _state.value.product ?: return
        val qty = _state.value.addQty.toDoubleOrNull()
        if (qty == null || qty <= 0) {
            _state.update { it.copy(error = "Qty must be > 0") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null, message = null) }
            try {
                val (cartId, lineId) = rpc.addCustomerCartLineByOem(product.oem, qty)
                _state.update {
                    it.copy(
                        busy = false,
                        message = "Added to cart $cartId · line $lineId",
                    )
                }
                onDone()
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, error = e.message ?: "add to cart failed") }
            }
        }
    }

    companion object {
        fun factory(rpc: RpcClient): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    CatalogViewModel(rpc) as T
            }
    }
}
