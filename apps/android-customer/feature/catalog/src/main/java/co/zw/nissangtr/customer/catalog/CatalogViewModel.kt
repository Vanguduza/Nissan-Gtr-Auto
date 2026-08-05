package co.zw.nissangtr.customer.catalog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import co.zw.nissangtr.customer.catalog.data.CatalogRepositoryImpl
import co.zw.nissangtr.customer.catalog.domain.DealTile
import co.zw.nissangtr.customer.catalog.domain.usecase.CatalogUseCases
import co.zw.nissangtr.customer.rpc.CatalogListItem
import co.zw.nissangtr.customer.rpc.CatalogProduct
import co.zw.nissangtr.customer.rpc.GarageVehicle
import co.zw.nissangtr.customer.rpc.ProductReviewStats
import co.zw.nissangtr.customer.rpc.RpcClient
import co.zw.nissangtr.ui.shop.ShopFilterState
import co.zw.nissangtr.ui.shop.ShopSortOption
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class CatalogScreenRoute {
    Home,
    Categories,
    CategoryBrowse,
    Newest,
    Product,
}

data class CatalogUiState(
    val route: CatalogScreenRoute = CatalogScreenRoute.Home,
    val browseItems: List<CatalogListItem> = emptyList(),
    /** Category filter applied on Home rails (chip / categories grid). */
    val activeCategory: String? = null,
    /** Title shown on category PLP. */
    val categoryBrowseTitle: String? = null,
    val filterState: ShopFilterState = ShopFilterState(),
    val sortOption: ShopSortOption = ShopSortOption.Relevance,
    val product: CatalogProduct? = null,
    val addQty: String = "1",
    val primaryVehicle: GarageVehicle? = null,
    /** Always empty until a real backend RPC ships — see [DealTile] TODO. Never fabricated. */
    val deals: List<DealTile> = emptyList(),
    val reviewStats: ProductReviewStats? = null,
    val busy: Boolean = false,
    val message: String? = null,
    val error: String? = null,
)

/**
 * Storefront catalog/home ViewModel — depends only on [CatalogUseCases], never on
 * [RpcClient] directly (see `factory`).
 */
class CatalogViewModel(
    private val useCases: CatalogUseCases,
) : ViewModel() {
    private val _state = MutableStateFlow(CatalogUiState())
    val state: StateFlow<CatalogUiState> = _state.asStateFlow()

    /** Filtered + sorted browse rows for PLP screens. */
    val displayBrowseItems: List<CatalogListItem>
        get() {
            val s = _state.value
            return applyCatalogFilterSort(s.browseItems, s.filterState, s.sortOption)
        }

    /** Route before opening PDP — restore on back. */
    private var routeBeforeProduct: CatalogScreenRoute = CatalogScreenRoute.Home

    init {
        refreshBrowse()
        loadHomeMerchandising()
    }

    fun onAddQtyChange(v: String) = _state.update { it.copy(addQty = v, error = null) }

    private fun loadHomeMerchandising() {
        viewModelScope.launch {
            try {
                val vehicle = useCases.getPrimaryVehicle()
                _state.update { it.copy(primaryVehicle = vehicle) }
            } catch (_: Exception) {
                // Guest / signed-out — non-fatal.
            }
            try {
                val deals = useCases.getActiveDeals()
                _state.update { it.copy(deals = deals) }
            } catch (_: Exception) {
                // Stub use case never throws today.
            }
        }
    }

    fun refreshBrowse(category: String? = _state.value.activeCategory) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            try {
                val browse = useCases.browse(category = category, limit = 50)
                _state.update {
                    it.copy(
                        busy = false,
                        browseItems = browse.items,
                        activeCategory = category,
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, error = e.message ?: "browse failed") }
            }
        }
    }

    fun applyCategoryFilter(category: String?) {
        openCategoryBrowse(category ?: return, fromHome = true)
    }

    fun openCategoryBrowse(categoryLabel: String, fromHome: Boolean = false) {
        val label = categoryLabel.trim()
        if (label.isEmpty()) return
        _state.update {
            it.copy(
                route = if (fromHome) CatalogScreenRoute.CategoryBrowse else CatalogScreenRoute.CategoryBrowse,
                activeCategory = label,
                categoryBrowseTitle = label,
                filterState = it.filterState.copy(category = label),
                error = null,
                message = null,
            )
        }
        refreshBrowse(label)
    }

    fun openCategories() {
        _state.update {
            it.copy(route = CatalogScreenRoute.Categories, error = null, message = null)
        }
    }

    fun openNewest() {
        _state.update {
            it.copy(
                route = CatalogScreenRoute.Newest,
                categoryBrowseTitle = "Newest products",
                error = null,
                message = null,
            )
        }
        if (_state.value.browseItems.isEmpty()) refreshBrowse(category = null)
    }

    fun applyBrowseFilter(filter: ShopFilterState) {
        _state.update { it.copy(filterState = filter, error = null) }
        val cat = filter.category?.trim()?.takeIf { it.isNotEmpty() }
        if (cat != null && cat != _state.value.activeCategory) {
            refreshBrowse(cat)
        }
    }

    fun applyBrowseSort(sort: ShopSortOption) {
        _state.update { it.copy(sortOption = sort, error = null) }
    }

    fun openProduct(oem: String) {
        viewModelScope.launch {
            routeBeforeProduct = _state.value.route.takeUnless { it == CatalogScreenRoute.Product }
                ?: CatalogScreenRoute.Home
            _state.update { it.copy(busy = true, error = null, message = null) }
            try {
                val product = useCases.loadProduct(oem)
                val stats = runCatching {
                    useCases.reviewStats(product.stockItemId, product.oem)
                }.getOrNull()
                _state.update {
                    it.copy(
                        busy = false,
                        product = product,
                        reviewStats = stats,
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
                message = null,
                error = null,
            )
        }
    }

    fun navigateBackFromProduct() {
        _state.update {
            it.copy(
                route = routeBeforeProduct,
                product = null,
                error = null,
                message = null,
            )
        }
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
                val (cartId, lineId) = useCases.addToCart(product.oem, qty)
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
                    CatalogViewModel(CatalogUseCases.from(CatalogRepositoryImpl(rpc))) as T
            }
    }
}
