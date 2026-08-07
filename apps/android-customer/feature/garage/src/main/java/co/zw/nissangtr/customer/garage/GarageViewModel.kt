package co.zw.nissangtr.customer.garage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import co.zw.nissangtr.customer.rpc.GarageVehicle
import co.zw.nissangtr.customer.rpc.GarageVehicleInput
import co.zw.nissangtr.customer.rpc.RpcClient
import co.zw.nissangtr.customer.rpc.SelectedFitmentVehicle
import co.zw.nissangtr.customer.rpc.VehicleCascade
import co.zw.nissangtr.customer.rpc.VehicleMasterRow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class GarageUiState(
    val vehicles: List<GarageVehicle> = emptyList(),
    /** Live `vehicle_master` rows — same source as homepage Select Vehicle. */
    val vehicleRows: List<VehicleMasterRow> = emptyList(),
    val isPrimary: Boolean = false,
    val busy: Boolean = false,
    val catalogBusy: Boolean = false,
    /** Bumped after successful save so [VehicleSelectorSection] local state resets. */
    val formEpoch: Int = 0,
    val message: String? = null,
    val error: String? = null,
)

class GarageViewModel(
    private val rpc: RpcClient,
) : ViewModel() {
    private val _state = MutableStateFlow(GarageUiState())
    val state: StateFlow<GarageUiState> = _state.asStateFlow()

    init {
        refresh()
        loadVehicleCatalog()
    }

    fun onPrimaryChange(v: Boolean) = _state.update { it.copy(isPrimary = v) }

    fun clearFormError() = _state.update { it.copy(error = null, message = null) }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            try {
                val list = rpc.listGarageVehicles()
                _state.update { it.copy(busy = false, vehicles = list) }
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, error = e.message ?: "list failed") }
            }
        }
    }

    fun loadVehicleCatalog() {
        viewModelScope.launch {
            _state.update { it.copy(catalogBusy = true, error = null) }
            try {
                val rows = rpc.listVehicleMaster()
                _state.update { it.copy(catalogBusy = false, vehicleRows = rows) }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        catalogBusy = false,
                        error = e.message ?: "Could not load vehicle catalog",
                    )
                }
            }
        }
    }

    /** Persist cascade selection — options must resolve against live [vehicleRows]. */
    fun saveFromCascade(
        maker: String,
        model: String,
        generation: String,
        engine: String?,
    ) {
        viewModelScope.launch {
            val selected = VehicleCascade.fromCascade(
                maker = maker,
                model = model,
                generation = generation,
                engine = engine,
                rows = _state.value.vehicleRows,
            )
            if (selected == null) {
                _state.update { it.copy(error = "Selection not found.") }
                return@launch
            }
            upsertSelected(selected)
        }
    }

    /** Persist VIN only when identifiable in live catalog. */
    fun saveFromVin(vin: String) {
        viewModelScope.launch {
            val selected = VehicleCascade.resolveVin(_state.value.vehicleRows, vin)
            if (selected == null) {
                _state.update { it.copy(error = "VIN not found.") }
                return@launch
            }
            upsertSelected(selected)
        }
    }

    private suspend fun upsertSelected(selected: SelectedFitmentVehicle) {
        _state.update { it.copy(busy = true, error = null, message = null) }
        try {
            rpc.upsertCustomerGarageVehicle(
                GarageVehicleInput(
                    make = selected.make,
                    model = selected.model,
                    generation = selected.generation,
                    engine = selected.engine,
                    vin = selected.vin,
                    isPrimary = _state.value.isPrimary,
                ),
            )
            val list = rpc.listGarageVehicles()
            _state.update {
                it.copy(
                    busy = false,
                    vehicles = list,
                    message = "Vehicle saved",
                    isPrimary = false,
                    formEpoch = it.formEpoch + 1,
                )
            }
        } catch (e: Exception) {
            _state.update { it.copy(busy = false, error = e.message ?: "upsert failed") }
        }
    }

    fun delete(id: String) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null, message = null) }
            try {
                rpc.deleteCustomerGarageVehicle(id)
                val list = rpc.listGarageVehicles()
                _state.update {
                    it.copy(
                        busy = false,
                        vehicles = list,
                        message = "Vehicle removed",
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, error = e.message ?: "delete failed") }
            }
        }
    }

    companion object {
        fun factory(rpc: RpcClient): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    GarageViewModel(rpc) as T
            }
    }
}
