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
import co.zw.nissangtr.customer.rpc.SelectedFitmentVehicle
import co.zw.nissangtr.customer.rpc.UserFacingErrors
import co.zw.nissangtr.customer.rpc.VehicleCascade
import co.zw.nissangtr.customer.rpc.VehicleMasterRow
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
    /** Session fitment from Select vehicle (preferred over garage for the bar). */
    val selectedFitment: SelectedFitmentVehicle? = null,
    val vehicleRows: List<VehicleMasterRow> = emptyList(),
    val vehicleBusy: Boolean = false,
    val vehicleError: String? = null,
    /** Always empty until a real backend RPC ships — see [DealTile] TODO. Never fabricated. */
    val deals: List<DealTile> = emptyList(),
    val reviewStats: ProductReviewStats? = null,
    val busy: Boolean = false,
    val message: String? = null,
    val error: String? = null,
) {
    /** Compact fitment bar label — no maker/Nissan tag, no My Garage. */
    fun fitmentBarLabel(): String {
        selectedFitment?.compactLabel()?.takeIf { it.isNotEmpty() }?.let { return it }
        val g = primaryVehicle ?: return "No vehicle selected"
        return listOfNotNull(g.model, g.generation, g.engine)
            .joinToString(" · ")
            .ifBlank { g.vin?.let { "VIN $it" } ?: "No vehicle selected" }
    }
}

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
                _state.update { it.copy(vehicleBusy = true, vehicleError = null) }
                val rows = useCases.listVehicleMaster()
                _state.update {
                    it.copy(
                        vehicleRows = rows,
                        vehicleBusy = false,
                        vehicleError = if (rows.isEmpty()) {
                            "No vehicles in the live catalog yet."
                        } else {
                            null
                        },
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        vehicleBusy = false,
                        vehicleError = e.message ?: "Could not load vehicle catalog",
                    )
                }
            }
            try {
                val deals = useCases.getActiveDeals()
                _state.update { it.copy(deals = deals) }
            } catch (_: Exception) {
                // Stub use case never throws today.
            }
        }
    }

    fun confirmCascadeVehicle(
        maker: String,
        model: String,
        generation: String,
        engine: String?,
    ) {
        viewModelScope.launch {
            val selected = VehicleCascade.fromCascade(
                maker = maker,
                model = model,
                generation = generation,
                engine = engine,
                rows = _state.value.vehicleRows,
            )
            if (selected == null) {
                _state.update {
                    it.copy(vehicleError = "Selection not found.")
                }
                return@launch
            }
            applySelectedVehicle(selected)
        }
    }

    fun confirmVinVehicle(vin: String) {
        viewModelScope.launch {
            val selected = VehicleCascade.resolveVin(_state.value.vehicleRows, vin)
            if (selected == null) {
                _state.update {
                    it.copy(
                        vehicleError = "VIN not found.",
                    )
                }
                return@launch
            }
            applySelectedVehicle(selected)
        }
    }

    fun clearSelectedVehicle() {
        _state.update {
            it.copy(selectedFitment = null, vehicleError = null, message = null)
        }
    }

    private suspend fun applySelectedVehicle(selected: SelectedFitmentVehicle) {
        _state.update {
            it.copy(
                selectedFitment = selected,
                vehicleBusy = true,
                vehicleError = null,
                busy = true,
                error = null,
            )
        }
        try {
            val browse = useCases.listCatalogForVehicle(
                chassisCode = selected.generation,
                engineCode = selected.engine,
                limit = 50,
            )
            _state.update {
                it.copy(
                    busy = false,
                    vehicleBusy = false,
                    browseItems = browse.items,
                    route = CatalogScreenRoute.CategoryBrowse,
                    categoryBrowseTitle = selected.compactLabel(),
                    activeCategory = selected.generation,
                    message = if (browse.items.isEmpty()) {
                        "Vehicle set — no stocked parts for this chassis/engine yet."
                    } else {
                        "Scoped to ${selected.compactLabel()}"
                    },
                )
            }
        } catch (e: Exception) {
            _state.update {
                it.copy(
                    busy = false,
                    vehicleBusy = false,
                    vehicleError = e.message ?: "Could not load parts for vehicle",
                )
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
        val label = category?.trim()?.takeIf { it.isNotEmpty() } ?: return
        openCategoryBrowse(label, fromHome = true)
    }

    fun openCategoryBrowse(categoryLabel: String, fromHome: Boolean = false) {
        val label = categoryLabel.trim()
        if (label.isEmpty()) return
        _state.update {
            it.copy(
                route = CatalogScreenRoute.CategoryBrowse,
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
                _state.update {
                    it.copy(
                        busy = false,
                        error = UserFacingErrors.from(e, "Could not add to cart"),
                    )
                }
            }
        }
    }


    fun quickAddToCart(item: CatalogListItem, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null, message = null) }
            try {
                useCases.addToCart(item.oem, 1.0)
                _state.update { it.copy(busy = false, message = "Added to cart") }
                onDone()
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        busy = false,
                        error = UserFacingErrors.from(e, "Could not add to cart"),
                    )
                }
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
