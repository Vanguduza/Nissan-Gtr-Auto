package com.joker.coolmall.feature.main.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.zw.nissangtr.management.gtradapter.GtrOpsCatalog
import co.zw.nissangtr.management.gtradapter.GtrOpsRow
import co.zw.nissangtr.management.gtradapter.GtrOpsSpec
import co.zw.nissangtr.management.gtradapter.GtrStaffOpsAdapter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class StaffOpsViewModel @Inject constructor(
    private val ops: GtrStaffOpsAdapter,
) : ViewModel() {

    private var href: String = ""

    private val _spec = MutableStateFlow<GtrOpsSpec?>(null)
    val spec: StateFlow<GtrOpsSpec?> = _spec.asStateFlow()

    private val _rows = MutableStateFlow<List<GtrOpsRow>>(emptyList())
    val rows: StateFlow<List<GtrOpsRow>> = _rows.asStateFlow()

    private val _selectedId = MutableStateFlow<String?>(null)
    val selectedId: StateFlow<String?> = _selectedId.asStateFlow()

    private val _fields = MutableStateFlow<Map<String, String>>(emptyMap())
    val fields: StateFlow<Map<String, String>> = _fields.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    fun bind(href: String) {
        if (this.href == href && _spec.value != null) return
        this.href = href
        val spec = GtrOpsCatalog.spec(href)
        _spec.value = spec
        _selectedId.value = null
        _fields.value = spec?.fields?.associate { it.key to "" }.orEmpty()
        refresh()
    }

    fun updateField(key: String, value: String) {
        _fields.value = _fields.value + (key to value)
    }

    fun select(id: String) {
        _selectedId.value = id
    }

    fun refresh() {
        val h = href
        if (h.isEmpty()) return
        viewModelScope.launch {
            _busy.value = true
            ops.listLeaf(h).fold(
                onSuccess = { _rows.value = it },
                onFailure = { _message.value = it.message },
            )
            _busy.value = false
        }
    }

    fun run(actionId: String) {
        val h = href
        viewModelScope.launch {
            _busy.value = true
            ops.runAction(h, actionId, _selectedId.value, _fields.value).fold(
                onSuccess = {
                    _message.value = it
                    refresh()
                },
                onFailure = { _message.value = it.message ?: "Action failed" },
            )
            _busy.value = false
        }
    }
}
