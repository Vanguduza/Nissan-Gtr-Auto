package co.zw.nissangtr.customer.garage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import co.zw.nissangtr.customer.rpc.GarageVehicle
import co.zw.nissangtr.customer.rpc.GarageVehicleInput
import co.zw.nissangtr.customer.rpc.RpcClient
import co.zw.nissangtr.customer.rpc.RpcNames
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class GarageUiState(
    val vehicles: List<GarageVehicle> = emptyList(),
    val make: String = "Nissan",
    val model: String = "",
    val generation: String = "",
    val engine: String = "",
    val vin: String = "",
    val isPrimary: Boolean = false,
    val busy: Boolean = false,
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
    }

    fun onMakeChange(v: String) = _state.update { it.copy(make = v, error = null) }
    fun onModelChange(v: String) = _state.update { it.copy(model = v, error = null) }
    fun onGenerationChange(v: String) = _state.update { it.copy(generation = v) }
    fun onEngineChange(v: String) = _state.update { it.copy(engine = v) }
    fun onVinChange(v: String) = _state.update { it.copy(vin = v, error = null) }
    fun onPrimaryChange(v: Boolean) = _state.update { it.copy(isPrimary = v) }

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

    fun upsert() {
        val s = _state.value
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null, message = null) }
            try {
                val id = rpc.upsertCustomerGarageVehicle(
                    GarageVehicleInput(
                        make = s.make.ifBlank { null },
                        model = s.model.ifBlank { null },
                        generation = s.generation.ifBlank { null },
                        engine = s.engine.ifBlank { null },
                        vin = s.vin.ifBlank { null },
                        isPrimary = s.isPrimary,
                    ),
                )
                val list = rpc.listGarageVehicles()
                _state.update {
                    it.copy(
                        busy = false,
                        vehicles = list,
                        message = "${RpcNames.UPSERT_CUSTOMER_GARAGE_VEHICLE} → $id",
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, error = e.message ?: "upsert failed") }
            }
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
                        message = "${RpcNames.DELETE_CUSTOMER_GARAGE_VEHICLE} → $id",
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
