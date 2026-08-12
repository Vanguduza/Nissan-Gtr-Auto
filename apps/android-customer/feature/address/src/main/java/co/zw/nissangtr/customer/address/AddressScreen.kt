package co.zw.nissangtr.customer.address

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import co.zw.nissangtr.bridges.maps.AddressPickMap
import co.zw.nissangtr.bridges.maps.MapLatLng
import co.zw.nissangtr.customer.rpc.CustomerAddress
import co.zw.nissangtr.customer.rpc.RpcClient
import co.zw.nissangtr.customer.rpc.RpcNames
import co.zw.nissangtr.ui.shop.ShopAddressPicker
import co.zw.nissangtr.ui.shop.ShopHonestEmpty
import co.zw.nissangtr.ui.shop.ShopSectionHeader
import co.zw.nissangtr.ui.shop.ShopDefaultScreen

/**
 * Shipping addresses — list / upsert / delete + MapLibre pin pick (Bridge-First maps-nav, B-MAP-1).
 * Google Maps is deprecated fallback only when MapLibre is off/fails and a key is present.
 * Wired to [RpcNames.UPSERT_CUSTOMER_ADDRESS] / [RpcNames.DELETE_CUSTOMER_ADDRESS].
 */
@Composable
fun AddressScreen(
    rpc: RpcClient,
    mapsKeyPresent: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    useMapLibre: Boolean = true,
    googleMapsKeyPresent: Boolean = false,
    viewModel: AddressViewModel = viewModel(factory = AddressViewModel.factory(rpc)),
) {
    val state by viewModel.state.collectAsState()
    val sharp = MaterialTheme.shapes.extraSmall

    ShopDefaultScreen(
        title = if (state.route == AddressScreenRoute.Edit) "Edit address" else "Addresses",
        subtitle = null,
        onBack = {
            if (state.route == AddressScreenRoute.Edit) viewModel.backToList() else onBack()
        },
        modifier = modifier,
    ) {
        when (state.route) {
            AddressScreenRoute.List -> {
                Text(
                    "Map pick stores lat/lng with the address (MapLibre SoR via Bridge-First maps-nav).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                state.error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                state.message?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall)
                }
                ShopSectionHeader(title = "Saved addresses", actionLabel = null)
                if (state.addresses.isEmpty()) {
                    ShopHonestEmpty(
                        title = "No addresses yet",
                        body = "No addresses.",
                    )
                } else {
                    state.addresses.forEach { addr ->
                        AddressListRow(
                            address = addr,
                            busy = state.busy,
                            onEdit = { viewModel.openEdit(addr) },
                            onDelete = { viewModel.delete(addr.id) },
                        )
                        HorizontalDivider()
                    }
                }
                Button(
                    onClick = viewModel::openNew,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.busy,
                    shape = sharp,
                ) { Text("Add address") }
                OutlinedButton(
                    onClick = viewModel::refresh,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.busy,
                    shape = sharp,
                ) { Text("Refresh") }
            }
            AddressScreenRoute.Edit -> {
                AddressEditForm(
                    form = state.form,
                    busy = state.busy,
                    error = state.error,
                    mapsKeyPresent = mapsKeyPresent,
                    onBack = viewModel::backToList,
                    onFormChange = viewModel::onFormChange,
                    onMapPick = viewModel::onMapPick,
                    onSave = viewModel::save,
                )
            }
        }
    }
}

@Composable
private fun AddressListRow(
    address: CustomerAddress,
    busy: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !busy, onClick = onEdit)
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(address.summaryLabel(), style = MaterialTheme.typography.titleSmall)
        Text(
            listOfNotNull(address.city, address.province, address.country)
                .joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (address.isDefault) {
            Text("Default", style = MaterialTheme.typography.labelSmall)
        }
        address.geoLatLng()?.let { (lat, lng) ->
            Text(
                "Map · %.5f, %.5f".format(lat, lng),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onEdit, enabled = !busy) { Text("Edit") }
            OutlinedButton(onClick = onDelete, enabled = !busy) { Text("Delete") }
        }
    }
}

@Composable
private fun AddressEditForm(
    form: AddressFormState,
    busy: Boolean,
    error: String?,
    mapsKeyPresent: Boolean,
    onBack: () -> Unit,
    onFormChange: ((AddressFormState) -> AddressFormState) -> Unit,
    onMapPick: (Double, Double) -> Unit,
    onSave: () -> Unit,
) {
    val sharp = MaterialTheme.shapes.extraSmall
    val selected = run {
        val lat = form.latitude.toDoubleOrNull()
        val lng = form.longitude.toDoubleOrNull()
        if (lat != null && lng != null) MapLatLng(lat, lng) else null
    }

    error?.let {
        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    }

    ShopAddressPicker(
        addressLine = form.line1,
        onAddressChange = { v -> onFormChange { it.copy(line1 = v) } },
        latitude = form.latitude,
        longitude = form.longitude,
        onLatitudeChange = { v -> onFormChange { it.copy(latitude = v) } },
        onLongitudeChange = { v -> onFormChange { it.copy(longitude = v) } },
        mapsKeyPresent = mapsKeyPresent,
        mapSlot = {
            AddressPickMap(
                selected = selected,
                onPick = { p -> onMapPick(p.latitude, p.longitude) },
                mapsKeyPresent = mapsKeyPresent,
            )
        },
    )

    OutlinedTextField(
        value = form.label,
        onValueChange = { v -> onFormChange { it.copy(label = v) } },
        label = { Text("Label (Home / Work)") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        enabled = !busy,
        shape = sharp,
    )
    OutlinedTextField(
        value = form.line2,
        onValueChange = { v -> onFormChange { it.copy(line2 = v) } },
        label = { Text("Apartment / notes") },
        modifier = Modifier.fillMaxWidth(),
        enabled = !busy,
        shape = sharp,
    )
    OutlinedTextField(
        value = form.city,
        onValueChange = { v -> onFormChange { it.copy(city = v) } },
        label = { Text("City") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        enabled = !busy,
        shape = sharp,
    )
    OutlinedTextField(
        value = form.province,
        onValueChange = { v -> onFormChange { it.copy(province = v) } },
        label = { Text("Province") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        enabled = !busy,
        shape = sharp,
    )
    OutlinedTextField(
        value = form.postalCode,
        onValueChange = { v -> onFormChange { it.copy(postalCode = v) } },
        label = { Text("Postal code") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        enabled = !busy,
        shape = sharp,
    )
    OutlinedTextField(
        value = form.country,
        onValueChange = { v -> onFormChange { it.copy(country = v) } },
        label = { Text("Country") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        enabled = !busy,
        shape = sharp,
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(
            checked = form.isDefault,
            onCheckedChange = { v -> onFormChange { it.copy(isDefault = v) } },
            enabled = !busy,
        )
        Text("Default shipping address", style = MaterialTheme.typography.bodyMedium)
    }
    Button(
        onClick = onSave,
        modifier = Modifier.fillMaxWidth(),
        enabled = !busy,
        shape = sharp,
    ) {
        Text(if (form.id == null) "Save address" else "Update address")
    }
}

/**
 * Compact picker for cart checkout (Nationwide dispatch) — select existing address.
 */
@Composable
fun AddressCheckoutPicker(
    addresses: List<CustomerAddress>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    onManageAddresses: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ShopSectionHeader(title = "Delivery address", actionLabel = "Manage", onAction = onManageAddresses)
        if (addresses.isEmpty()) {
            ShopHonestEmpty(
                title = "Add an address",
                body = "No shipping address.",
            )
            Button(onClick = onManageAddresses, modifier = Modifier.fillMaxWidth()) {
                Text("Add address")
            }
        } else {
            addresses.forEach { addr ->
                val selected = addr.id == selectedId
                OutlinedButton(
                    onClick = { onSelect(addr.id) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        (if (selected) "âœ“ " else "") + addr.summaryLabel(),
                        maxLines = 2,
                    )
                }
            }
        }
    }
}
