package com.joker.coolmall.feature.main.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.zw.nissangtr.management.gtradapter.GtrReceiptLine
import co.zw.nissangtr.management.gtradapter.GtrStockItemOption
import co.zw.nissangtr.management.gtradapter.GtrWarehouseAdapter
import co.zw.nissangtr.management.gtradapter.GtrWarehouseOption
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WarehouseReceiveViewModel @Inject constructor(
    private val warehouse: GtrWarehouseAdapter,
) : ViewModel() {

    private val _warehouses = MutableStateFlow<List<GtrWarehouseOption>>(emptyList())
    val warehouses: StateFlow<List<GtrWarehouseOption>> = _warehouses.asStateFlow()

    private val _selectedWarehouseId = MutableStateFlow<String?>(null)
    val selectedWarehouseId: StateFlow<String?> = _selectedWarehouseId.asStateFlow()

    private val _oemQuery = MutableStateFlow("")
    val oemQuery: StateFlow<String> = _oemQuery.asStateFlow()

    private val _item = MutableStateFlow<GtrStockItemOption?>(null)
    val item: StateFlow<GtrStockItemOption?> = _item.asStateFlow()

    private val _qty = MutableStateFlow("1")
    val qty: StateFlow<String> = _qty.asStateFlow()

    private val _unitCost = MutableStateFlow("0")
    val unitCost: StateFlow<String> = _unitCost.asStateFlow()

    private val _currency = MutableStateFlow("USD")
    val currency: StateFlow<String> = _currency.asStateFlow()

    private val _notes = MutableStateFlow("")
    val notes: StateFlow<String> = _notes.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    init {
        viewModelScope.launch {
            warehouse.listWarehouses(includeQuarantine = false).onSuccess { list ->
                _warehouses.value = list
                val preferred = list.firstOrNull { it.roleCode == "WH1" || it.code == "WH1" }
                    ?: list.firstOrNull()
                _selectedWarehouseId.value = preferred?.id
            }.onFailure { _message.value = it.message }
        }
    }

    fun updateOemQuery(value: String) {
        _oemQuery.value = value
        _message.value = null
    }

    fun updateQty(value: String) {
        _qty.value = value
    }

    fun updateUnitCost(value: String) {
        _unitCost.value = value
    }

    fun updateCurrency(value: String) {
        _currency.value = if (value == "ZIG") "ZIG" else "USD"
    }

    fun updateNotes(value: String) {
        _notes.value = value
    }

    fun selectWarehouse(id: String) {
        _selectedWarehouseId.value = id
    }

    fun searchOem() {
        val q = _oemQuery.value.trim()
        if (q.length < 2) {
            _message.value = "Enter at least 2 characters"
            return
        }
        viewModelScope.launch {
            _busy.value = true
            _message.value = null
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

    fun postReceipt() {
        val item = _item.value
        val whId = _selectedWarehouseId.value
        val qty = _qty.value.toDoubleOrNull() ?: 0.0
        val cost = _unitCost.value.toDoubleOrNull() ?: 0.0
        if (item == null) {
            _message.value = "Search an OEM first"
            return
        }
        if (item.requiresSerial) {
            _message.value = "Serial-tracked receive is not on this desk yet"
            return
        }
        val uom = item.baseUomId?.trim().orEmpty()
        if (uom.isEmpty()) {
            _message.value = "Item has no base UOM"
            return
        }
        if (whId.isNullOrBlank()) {
            _message.value = "Pick a warehouse"
            return
        }
        if (qty <= 0) {
            _message.value = "Qty must be greater than 0"
            return
        }
        viewModelScope.launch {
            _busy.value = true
            warehouse.postStockReceipt(
                toWarehouseId = whId,
                notes = _notes.value,
                lines = listOf(
                    GtrReceiptLine(
                        stockItemId = item.id,
                        uomId = uom,
                        qty = qty,
                        unitCost = cost.coerceAtLeast(0.0),
                        currency = _currency.value,
                    ),
                ),
            ).fold(
                onSuccess = { id ->
                    _message.value = "Received $id"
                    _qty.value = "1"
                    _notes.value = ""
                },
                onFailure = { _message.value = it.message ?: "Receive failed" },
            )
            _busy.value = false
        }
    }
}
