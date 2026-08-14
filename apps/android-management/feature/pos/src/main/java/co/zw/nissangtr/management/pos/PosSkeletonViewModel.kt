package co.zw.nissangtr.management.pos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import co.zw.nissangtr.management.rpc.CatalogPartHit
import co.zw.nissangtr.management.rpc.CatalogSearchMode
import co.zw.nissangtr.management.rpc.CurrencyCode
import co.zw.nissangtr.management.rpc.PosCartLineSummary
import co.zw.nissangtr.management.rpc.RpcClient
import co.zw.nissangtr.management.rpc.WarehouseRef
import co.zw.nissangtr.management.rpc.isPosSaleableWarehouse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PosSkeletonState(
    val warehouses: List<WarehouseRef> = emptyList(),
    val selectedWarehouseId: String? = null,
    val currency: CurrencyCode = CurrencyCode.USD,
    val cartId: String? = null,
    val query: String = "",
    val hits: List<CatalogPartHit> = emptyList(),
    val lines: List<PosCartLineSummary> = emptyList(),
    val busy: Boolean = false,
    val status: String? = null,
    val error: String? = null,
)

/**
 * Thin adapter: CoolMall-like till chrome ← GTR POS RPCs (Fake/Live).
 * Core-charge / multi-currency / Lock Task remain product rules — Phase 2 depth.
 */
class PosSkeletonViewModel(
    private val rpc: RpcClient,
) : ViewModel() {
    private val _state = MutableStateFlow(PosSkeletonState())
    val state: StateFlow<PosSkeletonState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val wh = runCatching { rpc.listWarehouses() }
                .getOrDefault(emptyList())
                .filter(::isPosSaleableWarehouse)
            _state.update {
                it.copy(
                    warehouses = wh,
                    selectedWarehouseId = wh.firstOrNull()?.id,
                    status = if (wh.isEmpty()) "No WH2 storefloor warehouse" else null,
                )
            }
        }
    }

    fun onQueryChange(v: String) = _state.update { it.copy(query = v, error = null) }

    fun selectWarehouse(id: String) = _state.update { it.copy(selectedWarehouseId = id) }

    fun toggleCurrency() = _state.update {
        it.copy(
            currency = if (it.currency == CurrencyCode.USD) CurrencyCode.ZIG else CurrencyCode.USD,
        )
    }

    fun openCart() {
        val whId = _state.value.selectedWarehouseId ?: return
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            try {
                val id = rpc.createPosCart(whId, _state.value.currency)
                val lines = rpc.listPosCartLines(id)
                _state.update {
                    it.copy(cartId = id, lines = lines, busy = false, status = "Cart $id")
                }
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, error = e.message ?: "Open cart failed") }
            }
        }
    }

    fun search() {
        val q = _state.value.query
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            try {
                val result = rpc.searchCatalog(CatalogSearchMode.PART, q)
                _state.update { it.copy(hits = result.parts, busy = false) }
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, error = e.message ?: "Search failed") }
            }
        }
    }

    fun addHit(hit: CatalogPartHit) {
        val cartId = _state.value.cartId
        if (cartId == null) {
            _state.update { it.copy(error = "Open a cart first") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            try {
                val ref = rpc.lookupStockItemByOem(hit.oemPartNumber)
                rpc.addCartLine(cartId, ref.stockItemId, ref.uomId, 1.0)
                val lines = rpc.listPosCartLines(cartId)
                _state.update { it.copy(lines = lines, busy = false, status = "Added ${hit.oemPartNumber}") }
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, error = e.message ?: "Add failed") }
            }
        }
    }

    fun checkout() {
        val cartId = _state.value.cartId ?: return
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            try {
                val result = rpc.checkoutPosCart(cartId)
                _state.update {
                    it.copy(
                        busy = false,
                        cartId = null,
                        lines = emptyList(),
                        status = "Checked out ${result.invoiceId}",
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, error = e.message ?: "Checkout failed") }
            }
        }
    }

    companion object {
        fun factory(rpc: RpcClient): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    PosSkeletonViewModel(rpc) as T
            }
    }
}
