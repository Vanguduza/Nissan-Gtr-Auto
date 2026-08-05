package co.zw.nissangtr.customer.address

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import co.zw.nissangtr.customer.rpc.AddressGeo
import co.zw.nissangtr.customer.rpc.CustomerAddress
import co.zw.nissangtr.customer.rpc.CustomerAddressInput
import co.zw.nissangtr.customer.rpc.RpcClient
import co.zw.nissangtr.customer.rpc.RpcNames
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AddressScreenRoute {
    List,
    Edit,
}

data class AddressFormState(
    val id: String? = null,
    val label: String = "",
    val line1: String = "",
    val line2: String = "",
    val city: String = "Harare",
    val province: String = "Harare",
    val postalCode: String = "",
    val country: String = "Zimbabwe",
    val isDefault: Boolean = false,
    val latitude: String = "",
    val longitude: String = "",
)

data class AddressUiState(
    val route: AddressScreenRoute = AddressScreenRoute.List,
    val addresses: List<CustomerAddress> = emptyList(),
    val form: AddressFormState = AddressFormState(),
    val busy: Boolean = false,
    val message: String? = null,
    val error: String? = null,
)

class AddressViewModel(
    private val rpc: RpcClient,
) : ViewModel() {
    private val _state = MutableStateFlow(AddressUiState())
    val state: StateFlow<AddressUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            try {
                val list = rpc.listOwnAddresses()
                _state.update { it.copy(busy = false, addresses = list) }
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, error = e.message ?: "list addresses failed") }
            }
        }
    }

    fun openNew() {
        _state.update {
            it.copy(
                route = AddressScreenRoute.Edit,
                form = AddressFormState(isDefault = it.addresses.isEmpty()),
                message = null,
                error = null,
            )
        }
    }

    fun openEdit(address: CustomerAddress) {
        val geo = address.geoLatLng()
        _state.update {
            it.copy(
                route = AddressScreenRoute.Edit,
                form = AddressFormState(
                    id = address.id,
                    label = address.label,
                    line1 = address.line1,
                    line2 = AddressGeo.strip(address.line2).orEmpty(),
                    city = address.city.orEmpty(),
                    province = address.province.orEmpty(),
                    postalCode = address.postalCode.orEmpty(),
                    country = address.country,
                    isDefault = address.isDefault,
                    latitude = geo?.first?.toString().orEmpty(),
                    longitude = geo?.second?.toString().orEmpty(),
                ),
                message = null,
                error = null,
            )
        }
    }

    fun backToList() {
        _state.update {
            it.copy(route = AddressScreenRoute.List, form = AddressFormState(), error = null)
        }
    }

    fun onFormChange(transform: (AddressFormState) -> AddressFormState) {
        _state.update { it.copy(form = transform(it.form), error = null) }
    }

    fun onMapPick(lat: Double, lng: Double) {
        _state.update {
            it.copy(
                form = it.form.copy(
                    latitude = lat.toString(),
                    longitude = lng.toString(),
                ),
                error = null,
            )
        }
    }

    fun save() {
        val f = _state.value.form
        if (f.line1.isBlank()) {
            _state.update { it.copy(error = "Street / area (line1) is required") }
            return
        }
        val lat = f.latitude.trim().toDoubleOrNull()
        val lng = f.longitude.trim().toDoubleOrNull()
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null, message = null) }
            try {
                val id = rpc.upsertCustomerAddress(
                    CustomerAddressInput(
                        id = f.id,
                        label = f.label,
                        line1 = f.line1,
                        line2 = f.line2.ifBlank { null },
                        city = f.city.ifBlank { null },
                        province = f.province.ifBlank { null },
                        postalCode = f.postalCode.ifBlank { null },
                        country = f.country.ifBlank { "Zimbabwe" },
                        isDefault = f.isDefault,
                        latitude = lat,
                        longitude = lng,
                    ),
                )
                val list = rpc.listOwnAddresses()
                _state.update {
                    it.copy(
                        busy = false,
                        addresses = list,
                        route = AddressScreenRoute.List,
                        form = AddressFormState(),
                        message = "${RpcNames.UPSERT_CUSTOMER_ADDRESS} → $id",
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, error = e.message ?: "save failed") }
            }
        }
    }

    fun delete(id: String) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null, message = null) }
            try {
                rpc.deleteCustomerAddress(id)
                val list = rpc.listOwnAddresses()
                _state.update {
                    it.copy(
                        busy = false,
                        addresses = list,
                        route = AddressScreenRoute.List,
                        message = "${RpcNames.DELETE_CUSTOMER_ADDRESS} ok",
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
                    AddressViewModel(rpc) as T
            }
    }
}
