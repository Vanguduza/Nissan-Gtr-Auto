package co.zw.nissangtr.management.pos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import co.zw.nissangtr.management.rpc.CustomerGarageVehicle
import co.zw.nissangtr.management.rpc.CustomerOption
import co.zw.nissangtr.management.rpc.PosCustomerKind
import co.zw.nissangtr.ui.shop.ShopHonestEmpty

/** Operator customer selection + safe profile/garage management surface. */
@Composable
internal fun PosCustomerWorkspace(state: PosUiState, viewModel: PosViewModel) {
    var showCreate by remember { mutableStateOf(false) }
    var showEdit by remember { mutableStateOf(false) }
    var garageVin by remember(state.customerId) { mutableStateOf("") }
    var garagePrimary by remember(state.customerId) { mutableStateOf(state.customerGarage.isEmpty()) }

    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text("Customers", style = androidx.compose.material3.MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(
                    "Select a customer, manage non-sensitive contact details and choose a vehicle from their garage.",
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Button(onClick = { showCreate = true }, enabled = !state.busy && !state.isOffline) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text("Create customer")
            }
        }

        if (state.isOffline) {
            ShopHonestEmpty(
                title = "Named customers require a connection",
                body = "Offline sales remain walk-in only. Local EPC vehicle filtering still works independently.",
            )
        } else {
            CustomerSearchPanel(state, viewModel)
        }

        state.selectedCustomer?.let { customer ->
            SelectedCustomerCard(
                customer = customer,
                onEdit = { showEdit = true },
                onClear = viewModel::clearCustomer,
                enabled = !state.busy && !state.isOffline,
            )

            Text("Garage", style = androidx.compose.material3.MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (state.customerGarage.isEmpty()) {
                ShopHonestEmpty(
                    title = "No saved vehicles",
                    body = "Use Model → Generation → Engine above, then save the active vehicle to this customer's garage.",
                )
            } else {
                state.customerGarage.forEach { vehicle ->
                    GarageVehicleCard(
                        vehicle = vehicle,
                        active = state.saleVehicle?.let {
                            it.modelSlug == vehicle.modelSlug && it.chassisCode == vehicle.chassisCode && it.engineCode == vehicle.engine
                        } == true,
                        enabled = !state.busy,
                        onUse = { viewModel.selectCustomerGarageVehicle(vehicle) },
                    )
                }
            }

            OutlinedButton(onClick = viewModel::shopForAnotherVehicle, enabled = !state.busy) {
                Icon(Icons.Filled.DirectionsCar, contentDescription = null)
                Text("Shop for another vehicle")
            }
            Text(
                "This keeps the current sale and existing cart lines. Complete the Model → Generation → Engine cascade above to change the active parts filter.",
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (state.saleVehicle != null) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = androidx.compose.material3.MaterialTheme.shapes.medium,
                    tonalElevation = 1.dp,
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Save active vehicle to garage", fontWeight = FontWeight.SemiBold)
                        Text(state.saleVehicle.displayLabel)
                        OutlinedTextField(
                            value = garageVin,
                            onValueChange = { garageVin = it.trim().uppercase().take(32) },
                            label = { Text("VIN (optional)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = garagePrimary, onCheckedChange = { garagePrimary = it })
                            Text("Primary vehicle")
                        }
                        Button(
                            onClick = { viewModel.addCurrentVehicleToCustomerGarage(garageVin.ifBlank { null }, garagePrimary) },
                            enabled = !state.busy && !state.isOffline,
                        ) { Text("Save vehicle") }
                    }
                }
            }

            if (state.shoppingVehicles.size > 1) {
                HorizontalDivider()
                Text("Vehicles used in this sale", fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    state.shoppingVehicles.take(4).forEach { vehicle ->
                        AssistChip(onClick = {}, label = { Text(vehicle.displayLabel) })
                    }
                }
            }
        }
    }

    if (showCreate) {
        CustomerEditorDialog(
            title = "Create customer",
            initial = null,
            busy = state.busy,
            onDismiss = { showCreate = false },
            onSave = { kind, display, business, email, phone, whatsapp ->
                viewModel.createCustomer(kind, display, business, email, phone, whatsapp)
                showCreate = false
            },
        )
    }
    if (showEdit) {
        CustomerEditorDialog(
            title = "Edit customer",
            initial = state.selectedCustomer,
            busy = state.busy,
            onDismiss = { showEdit = false },
            onSave = { kind, display, business, email, phone, whatsapp ->
                viewModel.updateSelectedCustomer(kind, display, business, email, phone, whatsapp)
                showEdit = false
            },
        )
    }
    if (state.customerVehiclePickerOpen) {
        GarageVehiclePickerDialog(state = state, viewModel = viewModel)
    }
}

@Composable
private fun CustomerSearchPanel(state: PosUiState, viewModel: PosViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = state.customerQuery,
                onValueChange = viewModel::onCustomerQueryChange,
                label = { Text("Name, business, email or phone") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Button(onClick = viewModel::searchCustomers, enabled = !state.busy && state.customerQuery.trim().length >= 2) {
                Icon(Icons.Filled.Search, contentDescription = null)
                Text("Search")
            }
        }
        state.customerHits.forEach { customer ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = androidx.compose.material3.MaterialTheme.shapes.medium,
                tonalElevation = 1.dp,
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(customer.receiptDisplayName, fontWeight = FontWeight.SemiBold)
                        val detail = listOfNotNull(
                            customer.displayName.takeIf { customer.kind == PosCustomerKind.BUSINESS },
                            customer.email,
                            customer.phoneE164,
                        ).joinToString(" · ")
                        if (detail.isNotBlank()) Text(detail, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                    }
                    OutlinedButton(onClick = { viewModel.selectCustomer(customer) }, enabled = !state.busy) { Text("Select") }
                }
            }
        }
    }
}

@Composable
private fun SelectedCustomerCard(
    customer: CustomerOption,
    enabled: Boolean,
    onEdit: () -> Unit,
    onClear: () -> Unit,
) {
    Surface(modifier = Modifier.fillMaxWidth(), shape = androidx.compose.material3.MaterialTheme.shapes.large, tonalElevation = 2.dp) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (customer.kind == PosCustomerKind.BUSINESS) Icons.Filled.Business else Icons.Filled.Person,
                contentDescription = null,
            )
            Column(Modifier.weight(1f)) {
                Text(customer.receiptDisplayName, fontWeight = FontWeight.Bold)
                if (customer.kind == PosCustomerKind.BUSINESS && customer.displayName != customer.businessName) {
                    Text("Contact: ${customer.displayName}")
                }
                listOfNotNull(customer.email, customer.phoneE164, customer.whatsappE164).distinct().forEach {
                    Text(it, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                }
            }
            OutlinedButton(onClick = onEdit, enabled = enabled) { Text("Edit") }
            TextButton(onClick = onClear, enabled = enabled) { Text("Clear") }
        }
    }
}

@Composable
private fun GarageVehicleCard(
    vehicle: CustomerGarageVehicle,
    active: Boolean,
    enabled: Boolean,
    onUse: () -> Unit,
) {
    Surface(modifier = Modifier.fillMaxWidth(), shape = androidx.compose.material3.MaterialTheme.shapes.medium, tonalElevation = if (active) 3.dp else 1.dp) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.DirectionsCar, contentDescription = null)
            Column(Modifier.weight(1f)) {
                Text(vehicle.displayLabel.ifBlank { "Nissan vehicle" }, fontWeight = FontWeight.SemiBold)
                val meta = listOfNotNull(vehicle.vin?.let { "VIN $it" }, if (vehicle.isPrimary) "Primary" else null).joinToString(" · ")
                if (meta.isNotBlank()) Text(meta, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
            }
            Button(onClick = onUse, enabled = enabled && !active) { Text(if (active) "Active" else "Shop") }
        }
    }
}

@Composable
private fun GarageVehiclePickerDialog(state: PosUiState, viewModel: PosViewModel) {
    AlertDialog(
        onDismissRequest = viewModel::dismissCustomerVehiclePicker,
        title = { Text("Which vehicle are you shopping for?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("This customer has ${state.customerGarage.size} vehicles. Choose the active parts filter; you can switch again without clearing the cart.")
                state.customerGarage.forEach { vehicle ->
                    OutlinedButton(
                        onClick = { viewModel.selectCustomerGarageVehicle(vehicle) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(vehicle.displayLabel.ifBlank { "Nissan vehicle" }) }
                }
                TextButton(onClick = viewModel::shopForAnotherVehicle) { Text("Shop for another vehicle") }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = viewModel::dismissCustomerVehiclePicker) { Text("Not now") } },
    )
}

@Composable
private fun CustomerEditorDialog(
    title: String,
    initial: CustomerOption?,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSave: (PosCustomerKind, String, String?, String?, String?, String?) -> Unit,
) {
    var kind by remember(initial?.id) { mutableStateOf(initial?.kind ?: PosCustomerKind.INDIVIDUAL) }
    var displayName by remember(initial?.id) { mutableStateOf(initial?.displayName.orEmpty()) }
    var businessName by remember(initial?.id) { mutableStateOf(initial?.businessName.orEmpty()) }
    var email by remember(initial?.id) { mutableStateOf(initial?.email.orEmpty()) }
    var phone by remember(initial?.id) { mutableStateOf(initial?.phoneE164.orEmpty()) }
    var whatsapp by remember(initial?.id) { mutableStateOf(initial?.whatsappE164.orEmpty()) }
    val valid = displayName.isNotBlank() && (kind != PosCustomerKind.BUSINESS || businessName.isNotBlank())

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = kind == PosCustomerKind.INDIVIDUAL,
                        onClick = { kind = PosCustomerKind.INDIVIDUAL },
                        label = { Text("Individual") },
                        leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null) },
                    )
                    FilterChip(
                        selected = kind == PosCustomerKind.BUSINESS,
                        onClick = { kind = PosCustomerKind.BUSINESS },
                        label = { Text("Business") },
                        leadingIcon = { Icon(Icons.Filled.Business, contentDescription = null) },
                    )
                }
                if (kind == PosCustomerKind.BUSINESS) {
                    OutlinedTextField(value = businessName, onValueChange = { businessName = it }, label = { Text("Business name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    label = { Text(if (kind == PosCustomerKind.BUSINESS) "Contact / display name" else "Full name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(value = email, onValueChange = { email = it.trim() }, label = { Text("Email (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = phone, onValueChange = { phone = it.trim() }, label = { Text("Phone (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = whatsapp, onValueChange = { whatsapp = it.trim() }, label = { Text("WhatsApp (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text(
                    "POS customer profiles intentionally exclude passwords, identity documents, payment credentials and other sensitive personal data.",
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        kind, displayName.trim(), businessName.trim().ifBlank { null }, email.ifBlank { null },
                        phone.ifBlank { null }, whatsapp.ifBlank { null },
                    )
                },
                enabled = valid && !busy,
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
