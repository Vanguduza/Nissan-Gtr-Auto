package co.zw.nissangtr.management.warehouse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import co.zw.nissangtr.management.rpc.CurrencyCode
import co.zw.nissangtr.management.rpc.ReceiptLineInput
import co.zw.nissangtr.management.rpc.RpcClient
import co.zw.nissangtr.management.rpc.StockItemRef
import co.zw.nissangtr.management.rpc.WarehouseRef
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * InvenTree-inspired IA steps (Compose, not Flutter):
 * Scan/OEM → resolve item → pick location → receive action.
 */
enum class ReceiveStep {
    Identify,
    Location,
    Action,
}

data class WarehouseReceiveState(
    val step: ReceiveStep = ReceiveStep.Identify,
    val oemInput: String = "",
    val item: StockItemRef? = null,
    val warehouses: List<WarehouseRef> = emptyList(),
    val selectedWarehouseId: String? = null,
    val qty: String = "1",
    val unitCost: String = "0",
    val currency: CurrencyCode = CurrencyCode.USD,
    val busy: Boolean = false,
    val lastReceiptId: String? = null,
    val error: String? = null,
)

class WarehouseReceiveViewModel(
    private val rpc: RpcClient,
) : ViewModel() {
    private val _state = MutableStateFlow(WarehouseReceiveState())
    val state: StateFlow<WarehouseReceiveState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val wh = runCatching { rpc.listWarehouses() }.getOrDefault(emptyList())
            // Prefer WH1 receiving; quarantine remains visible for awareness.
            val preferred = wh.firstOrNull { it.roleCode == "WH1" || it.code == "WH1" }
                ?: wh.firstOrNull { !it.isQuarantine }
            _state.update {
                it.copy(
                    warehouses = wh,
                    selectedWarehouseId = preferred?.id,
                )
            }
        }
    }

    fun onOemChange(v: String) = _state.update { it.copy(oemInput = v, error = null) }

    fun onQtyChange(v: String) = _state.update { it.copy(qty = v) }

    fun onUnitCostChange(v: String) = _state.update { it.copy(unitCost = v) }

    fun selectWarehouse(id: String) = _state.update { it.copy(selectedWarehouseId = id) }

    fun resolveOem() {
        val oem = _state.value.oemInput.trim()
        if (oem.isEmpty()) {
            _state.update { it.copy(error = "Enter OEM or paste Bridge QR payload OEM") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            try {
                val ref = rpc.lookupStockItemByOem(oem)
                _state.update {
                    it.copy(
                        item = ref,
                        step = ReceiveStep.Location,
                        busy = false,
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, error = e.message ?: "Lookup failed") }
            }
        }
    }

    fun goToAction() {
        if (_state.value.selectedWarehouseId.isNullOrBlank()) {
            _state.update { it.copy(error = "Pick a warehouse / location") }
            return
        }
        _state.update { it.copy(step = ReceiveStep.Action, error = null) }
    }

    fun resetToIdentify() = _state.update {
        it.copy(step = ReceiveStep.Identify, item = null, error = null)
    }

    fun postReceipt() {
        val s = _state.value
        val whId = s.selectedWarehouseId
        val item = s.item
        val qty = s.qty.toDoubleOrNull()
        val cost = s.unitCost.toDoubleOrNull()
        if (whId == null || item == null || qty == null || qty <= 0.0 || cost == null) {
            _state.update { it.copy(error = "Qty and unit cost required") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            try {
                val id = rpc.postStockReceipt(
                    toWarehouseId = whId,
                    notes = "Phase 1 receive skeleton",
                    lines = listOf(
                        ReceiptLineInput(
                            stockItemId = item.stockItemId,
                            uomId = item.uomId,
                            qty = qty,
                            unitCost = cost,
                            currency = s.currency,
                        ),
                    ),
                )
                _state.update {
                    it.copy(
                        busy = false,
                        lastReceiptId = id,
                        step = ReceiveStep.Identify,
                        item = null,
                        oemInput = "",
                        qty = "1",
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, error = e.message ?: "Receive failed") }
            }
        }
    }

    companion object {
        fun factory(rpc: RpcClient): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    WarehouseReceiveViewModel(rpc) as T
            }
    }
}
