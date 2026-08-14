package co.zw.nissangtr.management.warehouse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import co.zw.nissangtr.management.rpc.ConsignmentEntrySummary
import co.zw.nissangtr.management.rpc.ConsignmentKind
import co.zw.nissangtr.management.rpc.ConsignmentPurpose
import co.zw.nissangtr.management.rpc.CurrencyCode
import co.zw.nissangtr.management.rpc.CustomerOption
import co.zw.nissangtr.management.rpc.FakeRpcClient
import co.zw.nissangtr.management.rpc.RpcClient
import co.zw.nissangtr.management.rpc.RpcNames
import co.zw.nissangtr.management.rpc.SupplierRef
import co.zw.nissangtr.management.rpc.WarehouseRef
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ConsignmentUiState(
    val warehouses: List<WarehouseRef> = emptyList(),
    val suppliers: List<SupplierRef> = emptyList(),
    val entries: List<ConsignmentEntrySummary> = emptyList(),
    val warehouseId: String = "",
    val kind: ConsignmentKind = ConsignmentKind.SUPPLIER_OWNED,
    val purpose: ConsignmentPurpose = ConsignmentPurpose.RECEIVE,
    val supplierId: String = "",
    val customerId: String = "",
    val customerQuery: String = "",
    val customerHits: List<CustomerOption> = emptyList(),
    val currency: CurrencyCode = CurrencyCode.USD,
    val exchangeRate: String = "1",
    val notes: String = "",
    val entryId: String = "",
    val lineStockItemId: String = "",
    val lineUomId: String = "",
    val lineQty: String = "1",
    val lineUnitCost: String = "0",
    val lineUnitPrice: String = "0",
    val busy: Boolean = false,
    val message: String? = null,
    val error: String? = null,
)

/** Phase 16 consignment draft / line / submit / cancel. */
class ConsignmentViewModel(
    private val rpc: RpcClient,
) : ViewModel() {
    private val _state = MutableStateFlow(ConsignmentUiState())
    val state: StateFlow<ConsignmentUiState> = _state.asStateFlow()

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
                )
            }
            refresh()
        }
    }

    fun onWarehouseIdChange(v: String) = _state.update { it.copy(warehouseId = v, error = null) }
    fun onKindChange(v: ConsignmentKind) = _state.update { it.copy(kind = v) }
    fun onPurposeChange(v: ConsignmentPurpose) = _state.update { it.copy(purpose = v) }
    fun onSupplierIdChange(v: String) = _state.update { it.copy(supplierId = v) }
    fun onCustomerIdChange(v: String) = _state.update { it.copy(customerId = v) }
    fun onCustomerQueryChange(v: String) = _state.update { it.copy(customerQuery = v) }
    fun onCurrencyChange(v: CurrencyCode) = _state.update { it.copy(currency = v) }
    fun onExchangeRateChange(v: String) = _state.update { it.copy(exchangeRate = v) }
    fun onNotesChange(v: String) = _state.update { it.copy(notes = v) }
    fun onEntryIdChange(v: String) = _state.update { it.copy(entryId = v) }
    fun onLineStockItemIdChange(v: String) = _state.update { it.copy(lineStockItemId = v) }
    fun onLineUomIdChange(v: String) = _state.update { it.copy(lineUomId = v) }
    fun onLineQtyChange(v: String) = _state.update { it.copy(lineQty = v) }
    fun onLineUnitCostChange(v: String) = _state.update { it.copy(lineUnitCost = v) }
    fun onLineUnitPriceChange(v: String) = _state.update { it.copy(lineUnitPrice = v) }

    fun selectCustomer(c: CustomerOption) =
        _state.update {
            it.copy(customerId = c.id, customerQuery = c.displayName, customerHits = emptyList())
        }

    fun searchCustomers() {
        val q = _state.value.customerQuery
        viewModelScope.launch {
            try {
                val hits = rpc.searchCustomers(q)
                _state.update { it.copy(customerHits = hits, error = null) }
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message ?: "customer search failed") }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            try {
                val entries = rpc.listConsignmentEntries()
                _state.update {
                    it.copy(busy = false, entries = entries, message = "${entries.size} entr(y/ies)")
                }
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, error = e.message ?: "list failed") }
            }
        }
    }

    fun createDraft() {
        val s = _state.value
        val rate = s.exchangeRate.toDoubleOrNull() ?: 1.0
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            try {
                val id = rpc.createConsignmentEntryDraft(
                    kind = s.kind,
                    purpose = s.purpose,
                    warehouseId = s.warehouseId.trim(),
                    supplierId = s.supplierId.trim().ifBlank { null },
                    customerId = s.customerId.trim().ifBlank { null },
                    currency = s.currency,
                    exchangeRate = rate,
                    notes = s.notes.trim().ifBlank { null },
                )
                _state.update {
                    it.copy(
                        busy = false,
                        entryId = id,
                        message = "${RpcNames.CREATE_CONSIGNMENT_ENTRY_DRAFT} → $id",
                    )
                }
                refresh()
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, error = e.message ?: "create draft failed") }
            }
        }
    }

    fun addLine() {
        val s = _state.value
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            try {
                val id = rpc.addConsignmentEntryLine(
                    entryId = s.entryId.trim(),
                    stockItemId = s.lineStockItemId.trim(),
                    uomId = s.lineUomId.trim(),
                    qty = s.lineQty.toDoubleOrNull() ?: error("qty required"),
                    unitCost = s.lineUnitCost.toDoubleOrNull() ?: 0.0,
                    unitPrice = s.lineUnitPrice.toDoubleOrNull() ?: 0.0,
                    currency = s.currency,
                )
                _state.update {
                    it.copy(busy = false, message = "${RpcNames.ADD_CONSIGNMENT_ENTRY_LINE} → $id")
                }
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, error = e.message ?: "add line failed") }
            }
        }
    }

    fun submit(entryId: String = _state.value.entryId) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            try {
                rpc.submitConsignmentEntry(entryId.trim())
                _state.update {
                    it.copy(busy = false, message = "${RpcNames.SUBMIT_CONSIGNMENT_ENTRY} → $entryId")
                }
                refresh()
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, error = e.message ?: "submit failed") }
            }
        }
    }

    fun cancel(entryId: String = _state.value.entryId) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            try {
                rpc.cancelConsignmentEntry(entryId.trim())
                _state.update {
                    it.copy(busy = false, message = "${RpcNames.CANCEL_CONSIGNMENT_ENTRY} → $entryId")
                }
                refresh()
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, error = e.message ?: "cancel failed") }
            }
        }
    }

    companion object {
        fun factory(rpc: RpcClient) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                ConsignmentViewModel(rpc) as T
        }
    }
}
