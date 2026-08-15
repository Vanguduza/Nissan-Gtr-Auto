package com.joker.coolmall.feature.main.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.zw.nissangtr.management.gtradapter.GtrWarehouseAdapter
import co.zw.nissangtr.management.gtradapter.MasterStockRow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WarehouseMasterStockViewModel @Inject constructor(
    private val warehouse: GtrWarehouseAdapter,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _rows = MutableStateFlow<List<MasterStockRow>>(emptyList())
    val rows: StateFlow<List<MasterStockRow>> = _rows.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        search()
    }

    fun updateQuery(value: String) {
        _query.value = value
    }

    fun search() {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            warehouse.listMasterStock(limit = 200, query = _query.value.ifBlank { null })
                .fold(
                    onSuccess = { _rows.value = it },
                    onFailure = { _error.value = it.message ?: "Failed to load master stock" },
                )
            _loading.value = false
        }
    }
}
