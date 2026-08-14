package co.zw.nissangtr.management.crm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import co.zw.nissangtr.management.rpc.ChassisOption
import co.zw.nissangtr.management.rpc.RpcClient
import co.zw.nissangtr.management.rpc.StaffKitRow
import co.zw.nissangtr.management.rpc.StockItemOption
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class KitsUiState(
    val kits: List<StaffKitRow> = emptyList(),
    val chassisOptions: List<ChassisOption> = emptyList(),
    val title: String = "",
    val oem: String = "",
    val chassisCode: String = "",
    val componentSlots: List<String> = listOf("", ""),
    val searchQuery: String = "",
    val searchHits: List<StockItemOption> = emptyList(),
    /** Which component slot (0-based) receives the next search pick. */
    val activeSlot: Int = 0,
    val busy: Boolean = false,
    val message: String? = null,
    val error: String? = null,
)

class KitsViewModel(
    private val rpc: RpcClient,
) : ViewModel() {
    private val _state = MutableStateFlow(KitsUiState())
    val state: StateFlow<KitsUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun onTitleChange(v: String) = _state.update { it.copy(title = v, error = null) }
    fun onOemChange(v: String) = _state.update { it.copy(oem = v, error = null) }
    fun onChassisChange(v: String) = _state.update { it.copy(chassisCode = v, error = null) }
    fun onSearchQueryChange(v: String) = _state.update { it.copy(searchQuery = v, error = null) }
    fun onActiveSlot(i: Int) = _state.update { it.copy(activeSlot = i.coerceAtLeast(0)) }

    fun addComponentSlot() {
        _state.update { it.copy(componentSlots = it.componentSlots + "") }
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            try {
                val kits = rpc.listStaffKits()
                val chassis = runCatching { rpc.listChassisOptions() }.getOrDefault(emptyList())
                _state.update {
                    it.copy(busy = false, kits = kits, chassisOptions = chassis)
                }
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, error = e.message ?: "list kits failed") }
            }
        }
    }

    fun searchComponents() {
        val q = _state.value.searchQuery.trim()
        if (q.isEmpty()) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            try {
                val hits = rpc.searchStockItems(q, 20)
                _state.update { it.copy(busy = false, searchHits = hits) }
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, error = e.message ?: "search failed") }
            }
        }
    }

    fun pickComponent(item: StockItemOption) {
        _state.update { s ->
            val slots = s.componentSlots.toMutableList()
            val idx = s.activeSlot.coerceIn(0, (slots.size - 1).coerceAtLeast(0))
            if (slots.isEmpty()) {
                slots.add(item.id)
            } else {
                slots[idx] = item.id
            }
            s.copy(
                componentSlots = slots,
                searchHits = emptyList(),
                searchQuery = "",
                message = "Slot ${idx + 1} ← ${item.oemPartNumber}",
            )
        }
    }

    fun create() {
        val s = _state.value
        val title = s.title.trim()
        val oem = s.oem.trim()
        val ids = s.componentSlots.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (title.isEmpty() || oem.isEmpty()) {
            _state.update { it.copy(error = "Title and kit OEM required") }
            return
        }
        if (ids.size < 2) {
            _state.update { it.copy(error = "Pick at least 2 unique catalog parts") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null, message = null) }
            try {
                val id = rpc.createKitWithComponents(
                    oem = oem,
                    title = title,
                    componentItemIds = ids,
                    chassisCode = s.chassisCode.trim().ifEmpty { null },
                )
                _state.update {
                    it.copy(
                        busy = false,
                        message = "Created kit $id",
                        title = "",
                        oem = "",
                        chassisCode = "",
                        componentSlots = listOf("", ""),
                    )
                }
                refresh()
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, error = e.message ?: "create failed") }
            }
        }
    }

    fun toggleActive(kit: StaffKitRow) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            try {
                rpc.updateItemKit(kitId = kit.kitId, isActive = !kit.isActive)
                _state.update { it.copy(busy = false, message = "Updated ${kit.oem}") }
                refresh()
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, error = e.message ?: "update failed") }
            }
        }
    }

    companion object {
        fun factory(rpc: RpcClient): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    KitsViewModel(rpc) as T
            }
    }
}
