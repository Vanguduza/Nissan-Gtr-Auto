package co.zw.nissangtr.customer.address

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
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
import co.zw.nissangtr.customer.visual.GtrPremiumColors
import co.zw.nissangtr.customer.visual.PremiumEmptyState
import co.zw.nissangtr.customer.visual.PremiumMessageBanner
import co.zw.nissangtr.customer.visual.PremiumMessageKind
import co.zw.nissangtr.customer.visual.PremiumPrimaryButton
import co.zw.nissangtr.customer.visual.PremiumScreenHeader
import co.zw.nissangtr.customer.visual.PremiumSecondaryButton
import co.zw.nissangtr.customer.visual.PremiumStatusChip
import co.zw.nissangtr.customer.visual.PremiumStatusTone
import co.zw.nissangtr.customer.visual.PremiumSurfaceCard
import co.zw.nissangtr.ui.shop.ShopAddressPicker

@Composable
fun AddressScreen(
    rpc: RpcClient,
    mapsKeyPresent: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AddressViewModel = viewModel(factory = AddressViewModel.factory(rpc)),
) {
    val state by viewModel.state.collectAsState()

    Column(modifier.fillMaxSize().background(GtrPremiumColors.Background)) {
        PremiumScreenHeader(
            title = if (state.route == AddressScreenRoute.Edit) "Address details" else "Addresses",
            subtitle = if (state.route == AddressScreenRoute.Edit) "Delivery location" else "Saved delivery addresses",
            onBack = {
                if (state.route == AddressScreenRoute.Edit) viewModel.backToList() else onBack()
            },
        )

        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (state.route) {
                AddressScreenRoute.List -> {
                    state.error?.let { PremiumMessageBanner(it, PremiumMessageKind.Error) }
                    state.message?.let { PremiumMessageBanner(it, PremiumMessageKind.Success) }

                    if (state.addresses.isEmpty()) {
                        PremiumEmptyState(
                            title = "No addresses yet",
                            body = "Add a delivery address for nationwide dispatch.",
                        )
                    } else {
                        state.addresses.forEach { address ->
                            AddressListCard(
                                address = address,
                                busy = state.busy,
                                onEdit = { viewModel.openEdit(address) },
                                onDelete = { viewModel.delete(address.id) },
                            )
                        }
                    }

                    PremiumPrimaryButton(
                        text = "Add address",
                        onClick = viewModel::openNew,
                        enabled = !state.busy,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    PremiumSecondaryButton(
                        text = "Refresh",
                        onClick = viewModel::refresh,
                        enabled = !state.busy,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                AddressScreenRoute.Edit -> AddressEditForm(
                    form = state.form,
                    busy = state.busy,
                    error = state.error,
                    mapsKeyPresent = mapsKeyPresent,
                    onFormChange = viewModel::onFormChange,
                    onMapPick = viewModel::onMapPick,
                    onSave = viewModel::save,
                )
            }
            Spacer(Modifier.padding(bottom = 16.dp))
        }
    }
}

@Composable
private fun AddressListCard(
    address: CustomerAddress,
    busy: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    PremiumSurfaceCard(onClick = onEdit) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        address.summaryLabel(),
                        color = GtrPremiumColors.TextPrimary,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        listOfNotNull(address.city, address.province, address.country).joinToString(" • "),
                        color = GtrPremiumColors.TextSecondary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (address.isDefault) PremiumStatusChip("Default", PremiumStatusTone.Premium)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PremiumSecondaryButton("Edit", onEdit, enabled = !busy, modifier = Modifier.weight(1f))
                PremiumSecondaryButton("Delete", onDelete, enabled = !busy, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun AddressEditForm(
    form: AddressFormState,
    busy: Boolean,
    error: String?,
    mapsKeyPresent: Boolean,
    onFormChange: ((AddressFormState) -> AddressFormState) -> Unit,
    onMapPick: (Double, Double) -> Unit,
    onSave: () -> Unit,
) {
    val selected = run {
        val lat = form.latitude.toDoubleOrNull()
        val lng = form.longitude.toDoubleOrNull()
        if (lat != null && lng != null) MapLatLng(lat, lng) else null
    }

    error?.let { PremiumMessageBanner(it, PremiumMessageKind.Error) }

    PremiumSurfaceCard {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "Pin delivery location",
                color = GtrPremiumColors.TextPrimary,
                style = MaterialTheme.typography.titleMedium,
            )
            ShopAddressPicker(
                addressLine = form.line1,
                onAddressChange = { value -> onFormChange { it.copy(line1 = value) } },
                latitude = form.latitude,
                longitude = form.longitude,
                onLatitudeChange = { value -> onFormChange { it.copy(latitude = value) } },
                onLongitudeChange = { value -> onFormChange { it.copy(longitude = value) } },
                mapsKeyPresent = mapsKeyPresent,
                mapSlot = {
                    AddressPickMap(
                        selected = selected,
                        onPick = { point -> onMapPick(point.latitude, point.longitude) },
                        mapsKeyPresent = mapsKeyPresent,
                    )
                },
            )
        }
    }

    PremiumSurfaceCard {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(form.label, { v -> onFormChange { it.copy(label = v) } }, Modifier.fillMaxWidth(), label = { Text("Label (Home / Work)") }, singleLine = true, enabled = !busy)
            OutlinedTextField(form.line2, { v -> onFormChange { it.copy(line2 = v) } }, Modifier.fillMaxWidth(), label = { Text("Apartment / notes") }, enabled = !busy)
            OutlinedTextField(form.city, { v -> onFormChange { it.copy(city = v) } }, Modifier.fillMaxWidth(), label = { Text("City") }, singleLine = true, enabled = !busy)
            OutlinedTextField(form.province, { v -> onFormChange { it.copy(province = v) } }, Modifier.fillMaxWidth(), label = { Text("Province") }, singleLine = true, enabled = !busy)
            OutlinedTextField(form.postalCode, { v -> onFormChange { it.copy(postalCode = v) } }, Modifier.fillMaxWidth(), label = { Text("Postal code") }, singleLine = true, enabled = !busy)
            OutlinedTextField(form.country, { v -> onFormChange { it.copy(country = v) } }, Modifier.fillMaxWidth(), label = { Text("Country") }, singleLine = true, enabled = !busy)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = form.isDefault,
                    onCheckedChange = { value -> onFormChange { it.copy(isDefault = value) } },
                    enabled = !busy,
                )
                Text("Default shipping address", color = GtrPremiumColors.TextPrimary)
            }
            PremiumPrimaryButton(
                text = if (form.id == null) "Save address" else "Update address",
                onClick = onSave,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
fun AddressCheckoutPicker(
    addresses: List<CustomerAddress>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    onManageAddresses: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Delivery address",
                color = GtrPremiumColors.TextPrimary,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            PremiumSecondaryButton("Manage", onManageAddresses)
        }
        if (addresses.isEmpty()) {
            PremiumEmptyState("Add an address", "A shipping address is required for delivery.")
            PremiumPrimaryButton("Add address", onManageAddresses, modifier = Modifier.fillMaxWidth())
        } else {
            addresses.forEach { address ->
                PremiumSurfaceCard(onClick = { onSelect(address.id) }) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            address.summaryLabel(),
                            color = GtrPremiumColors.TextPrimary,
                            modifier = Modifier.weight(1f),
                        )
                        if (address.id == selectedId) PremiumStatusChip("Selected", PremiumStatusTone.Premium)
                    }
                }
            }
        }
    }
}
