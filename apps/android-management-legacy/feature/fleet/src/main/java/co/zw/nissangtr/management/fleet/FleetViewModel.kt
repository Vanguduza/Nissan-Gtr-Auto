package co.zw.nissangtr.management.fleet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import co.zw.nissangtr.management.rpc.FakeRpcClient
import co.zw.nissangtr.management.rpc.FleetVehicleStatus
import co.zw.nissangtr.management.rpc.FleetVehicleSummary
import co.zw.nissangtr.management.rpc.RpcClient
import co.zw.nissangtr.management.rpc.RpcNames
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FleetUiState(
    val vehicles: List<FleetVehicleSummary> = emptyList(),
    val editId: String? = null,
    val plate: String = "",
    val label: String = "",
    val status: FleetVehicleStatus = FleetVehicleStatus.ACTIVE,
    val assignedDriverUserId: String = "",
    val notes: String = "",
    val statusFilter: FleetVehicleStatus? = null,
    val busy: Boolean = false,
    val message: String? = null,
    val error: String? = null,
)

/** Company fleet — [RpcNames.LIST_FLEET_VEHICLES] / upsert / set status. No GPS. */
class FleetViewModel(
    private val rpc: RpcClient,
) : ViewModel() {
    private val _state = MutableStateFlow(FleetUiState())
    val state: StateFlow<FleetUiState> = _state.asStateFlow()

    init {
        if (rpc is FakeRpcClient) {
            _state.update {
                it.copy(assignedDriverUserId = FakeRpcClient.FAKE_DRIVER_USER_ID)
            }
        }
        refresh()
    }

    fun onPlateChange(v: String) = _state.update { it.copy(plate = v, error = null) }
    fun onLabelChange(v: String) = _state.update { it.copy(label = v) }
    fun onStatusChange(v: FleetVehicleStatus) = _state.update { it.copy(status = v) }
    fun onAssigneeChange(v: String) = _state.update { it.copy(assignedDriverUserId = v) }
    fun onNotesChange(v: String) = _state.update { it.copy(notes = v) }
    fun onFilterChange(v: FleetVehicleStatus?) {
        _state.update { it.copy(statusFilter = v) }
        refresh()
    }

    fun beginEdit(v: FleetVehicleSummary) {
        _state.update {
            it.copy(
                editId = v.id,
                plate = v.plate,
                label = v.label.orEmpty(),
                status = v.status,
                assignedDriverUserId = v.assignedDriverUserId.orEmpty(),
                notes = v.notes.orEmpty(),
                error = null,
                message = null,
            )
        }
    }

    fun clearForm() {
        _state.update {
            it.copy(
                editId = null,
                plate = "",
                label = "",
                status = FleetVehicleStatus.ACTIVE,
                assignedDriverUserId = if (rpc is FakeRpcClient) {
                    FakeRpcClient.FAKE_DRIVER_USER_ID
                } else {
                    ""
                },
                notes = "",
            )
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            try {
                val list = rpc.listFleetVehicles(_state.value.statusFilter)
                _state.update {
                    it.copy(busy = false, vehicles = list, message = "${list.size} vehicle(s)")
                }
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, error = e.message ?: "list failed") }
            }
        }
    }

    fun save() {
        val s = _state.value
        val plate = s.plate.trim()
        if (plate.isEmpty()) {
            _state.update { it.copy(error = "Plate required") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            try {
                val id = rpc.upsertFleetVehicle(
                    plate = plate,
                    label = s.label.trim().ifEmpty { null },
                    status = s.status,
                    assignedDriverUserId = s.assignedDriverUserId.trim().ifEmpty { null },
                    notes = s.notes.trim().ifEmpty { null },
                    id = s.editId,
                )
                _state.update {
                    it.copy(
                        busy = false,
                        message = "${RpcNames.UPSERT_FLEET_VEHICLE} → ${id.take(8)}…",
                    )
                }
                clearForm()
                refresh()
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, error = e.message ?: "upsert failed") }
            }
        }
    }

    fun retire(id: String) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            try {
                rpc.setFleetVehicleStatus(id, FleetVehicleStatus.RETIRED)
                _state.update {
                    it.copy(
                        busy = false,
                        message = "${RpcNames.SET_FLEET_VEHICLE_STATUS} → retired",
                    )
                }
                if (_state.value.editId == id) clearForm()
                refresh()
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, error = e.message ?: "retire failed") }
            }
        }
    }

    companion object {
        fun factory(rpc: RpcClient) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                FleetViewModel(rpc) as T
        }
    }
}
