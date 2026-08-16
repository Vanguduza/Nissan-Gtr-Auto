package com.joker.coolmall.feature.main.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.zw.nissangtr.management.gtradapter.GtrPendingTransfer
import co.zw.nissangtr.management.gtradapter.GtrStockItemOption
import co.zw.nissangtr.management.gtradapter.GtrTransferLine
import co.zw.nissangtr.management.gtradapter.GtrWarehouseAdapter
import co.zw.nissangtr.management.gtradapter.GtrWarehouseOption
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WarehouseTransfersViewModel @Inject constructor(
    private val warehouse: GtrWarehouseAdapter,
) : ViewModel() {

    private val _warehouses = MutableStateFlow<List<GtrWarehouseOption>>(emptyList())
    val warehouses: StateFlow<List<GtrWarehouseOption>> = _warehouses.asStateFlow()

    private val _fromId = MutableStateFlow<String?>(null)
    val fromId: StateFlow<String?> = _fromId.asStateFlow()

    private val _toId = MutableStateFlow<String?>(null)
    val toId: StateFlow<String?> = _toId.asStateFlow()

    private val _oemQuery = MutableStateFlow("")
    val oemQuery: StateFlow<String> = _oemQuery.asStateFlow()

    private val _item = MutableStateFlow<GtrStockItemOption?>(null)
    val item: StateFlow<GtrStockItemOption?> = _item.asStateFlow()

    private val _qty = MutableStateFlow("1")
    val qty: StateFlow<String> = _qty.asStateFlow()

    private val _notes = MutableStateFlow("")
    val notes: StateFlow<String> = _notes.asStateFlow()

    private val _pending = MutableStateFlow<List<GtrPendingTransfer>>(emptyList())
    val pending: StateFlow<List<GtrPendingTransfer>> = _pending.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    init {
        viewModelScope.launch {
            warehouse.listWarehouses(includeQuarantine = false).onSuccess { list ->
                _warehouses.value = list
                val wh1 = list.firstOrNull { it.roleCode == "WH1" || it.code == "WH1" }
                val wh2 = list.firstOrNull { it.roleCode == "WH2" || it.code == "WH2" }
                _fromId.value = wh1?.id ?: list.firstOrNull()?.id
                _toId.value = wh2?.id ?: list.getOrNull(1)?.id
            }
            refreshPending()
        }
    }

    fun updateOemQuery(value: String) {
        _oemQuery.value = value
        _message.value = null
    }

    fun updateQty(value: String) {
        _qty.value = value
    }

    fun updateNotes(value: String) {
        _notes.value = value
    }

    fun selectFrom(id: String) {
        _fromId.value = id
    }

    fun selectTo(id: String) {
        _toId.value = id
    }

    fun searchOem() {
        val q = _oemQuery.value.trim()
        if (q.length < 2) {
            _message.value = "Enter at least 2 characters"
            return
        }
        viewModelScope.launch {
            _busy.value = true
            warehouse.searchStockItems(q).fold(
                onSuccess = { hits ->
                    val first = hits.firstOrNull()
                    _item.value = first
                    _message.value = if (first == null) "No matching OEM" else null
                },
                onFailure = { _message.value = it.message ?: "Search failed" },
            )
            _busy.value = false
        }
    }

    fun createTransfer() {
        val item = _item.value
        val from = _fromId.value
        val to = _toId.value
        val qty = _qty.value.toDoubleOrNull() ?: 0.0
        if (item == null) {
            _message.value = "Search an OEM first"
            return
        }
        val uom = item.baseUomId?.trim().orEmpty()
        if (uom.isEmpty()) {
            _message.value = "Item has no base UOM"
            return
        }
        if (from.isNullOrBlank() || to.isNullOrBlank()) {
            _message.value = "Pick from and to warehouses"
            return
        }
        if (from == to) {
            _message.value = "From and to warehouses must differ"
            return
        }
        if (qty <= 0) {
            _message.value = "Qty must be greater than 0"
            return
        }
        viewModelScope.launch {
            _busy.value = true
            warehouse.createStockTransfer(
                fromWarehouseId = from,
                toWarehouseId = to,
                notes = _notes.value,
                lines = listOf(
                    GtrTransferLine(stockItemId = item.id, uomId = uom, qty = qty),
                ),
            ).fold(
                onSuccess = { id ->
                    _message.value = "Transfer $id pending approval"
                    _qty.value = "1"
                    _notes.value = ""
                    refreshPending()
                },
                onFailure = { _message.value = it.message ?: "Transfer failed" },
            )
            _busy.value = false
        }
    }

    fun approve(id: String) = decide(id, approve = true)

    fun reject(id: String) = decide(id, approve = false)

    private fun decide(id: String, approve: Boolean) {
        viewModelScope.launch {
            _busy.value = true
            val result = if (approve) {
                warehouse.approveStockTransfer(id)
            } else {
                warehouse.rejectStockTransfer(id)
            }
            result.fold(
                onSuccess = {
                    _message.value = if (approve) "Approved" else "Rejected"
                    refreshPending()
                },
                onFailure = { _message.value = it.message ?: "Update failed" },
            )
            _busy.value = false
        }
    }

    private suspend fun refreshPending() {
        warehouse.listPendingTransfers().onSuccess { _pending.value = it }
    }
}
