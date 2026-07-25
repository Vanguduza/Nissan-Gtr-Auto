package co.zw.nissangtr.management.procurement

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import co.zw.nissangtr.management.rpc.BlanketLineInput
import co.zw.nissangtr.management.rpc.BlanketReleaseLineInput
import co.zw.nissangtr.management.rpc.BlanketSummary
import co.zw.nissangtr.management.rpc.CurrencyCode
import co.zw.nissangtr.management.rpc.FakeRpcClient
import co.zw.nissangtr.management.rpc.RpcClient
import co.zw.nissangtr.management.rpc.RpcNames
import co.zw.nissangtr.management.rpc.SupplierRef
import co.zw.nissangtr.management.rpc.WarehouseRef
import co.zw.nissangtr.management.rpc.blanketAlerts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BlanketsUiState(
    val warehouses: List<WarehouseRef> = emptyList(),
    val suppliers: List<SupplierRef> = emptyList(),
    val blankets: List<BlanketSummary> = emptyList(),
    val supplierId: String = "",
    val warehouseId: String = "",
    val currency: CurrencyCode = CurrencyCode.USD,
    val exchangeRate: String = "1",
    val blanketMaxValue: String = "1000",
    val expectedDate: String = "",
    val notes: String = "",
    val lineStockItemId: String = "",
    val lineUomId: String = "",
    val lineQty: String = "10",
    val lineUnitPrice: String = "25",
    val releaseQtyByLineId: Map<String, String> = emptyMap(),
    val busy: Boolean = false,
    val message: String? = null,
    val error: String? = null,
)

/** Thin Phase 8b blanket bind with expiry/remaining alerts. */
class BlanketsViewModel(
    private val rpc: RpcClient,
) : ViewModel() {
    private val _state = MutableStateFlow(BlanketsUiState())
    val state: StateFlow<BlanketsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val warehouses = runCatching { rpc.listWarehouses() }.getOrDefault(emptyList())
            val suppliers = runCatching { rpc.listSuppliers() }.getOrDefault(emptyList())
            _state.update {
                it.copy(
                    warehouses = warehouses,
                    suppliers = suppliers,
                    warehouseId = warehouses.firstOrNull()?.id
                        ?: if (rpc is FakeRpcClient) FakeRpcClient.FAKE_WAREHOUSE_ID else "",
                    supplierId = suppliers.firstOrNull()?.id
                        ?: if (rpc is FakeRpcClient) FakeRpcClient.FAKE_SUPPLIER_ID else "",
                    lineStockItemId = if (rpc is FakeRpcClient) FakeRpcClient.FAKE_STOCK_ITEM_ID else "",
                    lineUomId = if (rpc is FakeRpcClient) {
                        "00000000-0000-4000-8000-0000000000u1"
                    } else {
                        ""
                    },
                )
            }
            refresh()
        }
    }

    fun onSupplierIdChange(v: String) = _state.update { it.copy(supplierId = v) }
    fun onWarehouseIdChange(v: String) = _state.update { it.copy(warehouseId = v) }
    fun onCurrencyChange(v: CurrencyCode) = _state.update { it.copy(currency = v) }
    fun onExchangeRateChange(v: String) = _state.update { it.copy(exchangeRate = v) }
    fun onBlanketMaxValueChange(v: String) = _state.update { it.copy(blanketMaxValue = v) }
    fun onExpectedDateChange(v: String) = _state.update { it.copy(expectedDate = v) }
    fun onNotesChange(v: String) = _state.update { it.copy(notes = v) }
    fun onLineStockItemIdChange(v: String) = _state.update { it.copy(lineStockItemId = v) }
    fun onLineUomIdChange(v: String) = _state.update { it.copy(lineUomId = v) }
    fun onLineQtyChange(v: String) = _state.update { it.copy(lineQty = v) }
    fun onLineUnitPriceChange(v: String) = _state.update { it.copy(lineUnitPrice = v) }
    fun onReleaseQtyChange(lineId: String, qty: String) =
        _state.update {
            it.copy(releaseQtyByLineId = it.releaseQtyByLineId + (lineId to qty))
        }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            try {
                val blankets = rpc.listBlanketPurchaseOrders()
                _state.update {
                    it.copy(
                        busy = false,
                        blankets = blankets,
                        message = "${blankets.size} blanket(s)",
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, error = e.message ?: "list blankets failed") }
            }
        }
    }

    fun createBlanket() {
        val s = _state.value
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            try {
                val id = rpc.createBlanketPurchaseOrder(
                    supplierId = s.supplierId.trim(),
                    warehouseId = s.warehouseId.trim(),
                    currency = s.currency,
                    exchangeRate = s.exchangeRate.toDoubleOrNull() ?: 1.0,
                    blanketMaxValue = s.blanketMaxValue.toDoubleOrNull()
                        ?: error("blanket max value required"),
                    lines = listOf(
                        BlanketLineInput(
                            stockItemId = s.lineStockItemId.trim(),
                            uomId = s.lineUomId.trim(),
                            qty = s.lineQty.toDoubleOrNull() ?: error("line qty required"),
                            unitPrice = s.lineUnitPrice.toDoubleOrNull()
                                ?: error("unit price required"),
                            currency = s.currency,
                        ),
                    ),
                    notes = s.notes.trim().ifBlank { null },
                    expectedDate = s.expectedDate.trim().ifBlank { null },
                )
                _state.update {
                    it.copy(busy = false, message = "${RpcNames.CREATE_BLANKET_PURCHASE_ORDER} → $id")
                }
                refresh()
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, error = e.message ?: "create blanket failed") }
            }
        }
    }

    fun submitBlanket(poId: String) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            try {
                rpc.submitPurchaseOrder(poId)
                _state.update {
                    it.copy(busy = false, message = "${RpcNames.SUBMIT_PURCHASE_ORDER} → $poId")
                }
                refresh()
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, error = e.message ?: "submit failed") }
            }
        }
    }

    fun releaseCallOff(blanket: BlanketSummary) {
        val qtyMap = _state.value.releaseQtyByLineId
        val lines = blanket.lines.mapNotNull { line ->
            val q = qtyMap[line.id]?.toDoubleOrNull() ?: return@mapNotNull null
            if (q <= 0) return@mapNotNull null
            BlanketReleaseLineInput(blanketLineId = line.id, qty = q)
        }
        if (lines.isEmpty()) {
            _state.update { it.copy(error = "Enter release qty on at least one line") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            try {
                val id = rpc.createBlanketRelease(blanket.id, lines)
                _state.update {
                    it.copy(busy = false, message = "${RpcNames.CREATE_BLANKET_RELEASE} → $id")
                }
                refresh()
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, error = e.message ?: "release failed") }
            }
        }
    }

    companion object {
        fun factory(rpc: RpcClient) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                BlanketsViewModel(rpc) as T
        }
    }
}

fun BlanketSummary.alertMessages(): List<String> =
    blanketAlerts(this).map { a -> "${a.severity.name}: ${a.message}" }
