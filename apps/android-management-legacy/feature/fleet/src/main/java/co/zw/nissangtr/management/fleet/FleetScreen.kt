package co.zw.nissangtr.management.fleet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import co.zw.nissangtr.management.rpc.FleetVehicleStatus
import co.zw.nissangtr.management.rpc.RpcClient
import co.zw.nissangtr.ui.shop.ShopListCard
import co.zw.nissangtr.ui.shop.ShopPrimaryButton
import co.zw.nissangtr.ui.shop.ShopSecondaryButton
import co.zw.nissangtr.ui.shop.ShopStaffPanel
import co.zw.nissangtr.ui.shop.ShopStaffScreen

/**
 * Company fleet CRUD scaffold. Metadata only — no
 * [co.zw.nissangtr.management.rpc.RpcNames.INGEST_DELIVERY_LOCATION] / GPS producer.
 */
@Composable
fun FleetScreen(
    rpc: RpcClient,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FleetViewModel = viewModel(
        factory = FleetViewModel.factory(rpc),
    ),
) {
    val state by viewModel.state.collectAsState()

    ShopStaffScreen(
        title = "Fleet",
        subtitle = "Company vehicles",
        modifier = modifier,
        onBack = onBack,
    ) {
        ShopStaffPanel(title = "Vehicles") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = state.statusFilter == null,
                    onClick = { viewModel.onFilterChange(null) },
                    label = { Text("All") },
                    enabled = !state.busy,
                )
                FleetVehicleStatus.entries.forEach { st ->
                    FilterChip(
                        selected = state.statusFilter == st,
                        onClick = { viewModel.onFilterChange(st) },
                        label = { Text(st.rpcValue) },
                        enabled = !state.busy,
                    )
                }
            }
            ShopSecondaryButton(
                label = "Refresh",
                onClick = viewModel::refresh,
                enabled = !state.busy,
            )

            state.vehicles.forEach { v ->
                ShopListCard(
                    title = "${v.plate}${v.label?.let { " — $it" } ?: ""}",
                    subtitle = "${v.status.rpcValue} · " +
                        (v.assignedDriverUserId?.take(8)?.plus("…") ?: "unassigned"),
                    onClick = { viewModel.beginEdit(v) },
                    trailing = {
                        if (v.status != FleetVehicleStatus.RETIRED) {
                            TextButton(
                                onClick = { viewModel.retire(v.id) },
                                enabled = !state.busy,
                            ) { Text("Retire") }
                        }
                    },
                )
            }
        }

        ShopStaffPanel(
            title = if (state.editId == null) "Add vehicle" else "Edit vehicle",
        ) {
            OutlinedTextField(
                value = state.plate,
                onValueChange = viewModel::onPlateChange,
                label = { Text("Plate") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !state.busy,
            )
            OutlinedTextField(
                value = state.label,
                onValueChange = viewModel::onLabelChange,
                label = { Text("Label / nickname") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !state.busy,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FleetVehicleStatus.entries.forEach { st ->
                    FilterChip(
                        selected = state.status == st,
                        onClick = { viewModel.onStatusChange(st) },
                        label = { Text(st.rpcValue) },
                        enabled = !state.busy,
                    )
                }
            }
            OutlinedTextField(
                value = state.assignedDriverUserId,
                onValueChange = viewModel::onAssigneeChange,
                label = { Text("Driver user UUID (optional)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !state.busy,
            )
            OutlinedTextField(
                value = state.notes,
                onValueChange = viewModel::onNotesChange,
                label = { Text("Notes") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.busy,
            )
            ShopPrimaryButton(
                label = if (state.editId == null) "Create" else "Save",
                onClick = viewModel::save,
                enabled = !state.busy,
            )
            if (state.editId != null) {
                ShopSecondaryButton(
                    label = "Cancel edit",
                    onClick = viewModel::clearForm,
                    enabled = !state.busy,
                )
            }

            state.message?.let { Text(it) }
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}
