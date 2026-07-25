package co.zw.nissangtr.management.fleet

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import co.zw.nissangtr.management.rpc.FleetVehicleStatus
import co.zw.nissangtr.management.rpc.RpcClient
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

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Company fleet", style = MaterialTheme.typography.headlineSmall)

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
        OutlinedButton(
            onClick = viewModel::refresh,
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Refresh") }

        state.vehicles.forEach { v ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !state.busy) { viewModel.beginEdit(v) }
                    .padding(vertical = 4.dp),
            ) {
                Text(
                    "${v.plate}${v.label?.let { " — $it" } ?: ""}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "${v.status.rpcValue} · " +
                        (v.assignedDriverUserId?.take(8)?.plus("…") ?: "unassigned"),
                    style = MaterialTheme.typography.bodySmall,
                )
                if (v.status != FleetVehicleStatus.RETIRED) {
                    OutlinedButton(
                        onClick = { viewModel.retire(v.id) },
                        enabled = !state.busy,
                    ) { Text("Retire") }
                }
            }
        }

        HorizontalDivider()
        Text(
            if (state.editId == null) "Add vehicle" else "Edit vehicle",
            style = MaterialTheme.typography.titleMedium,
        )
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
        Button(
            onClick = viewModel::save,
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (state.editId == null) "Create" else "Save") }
        if (state.editId != null) {
            OutlinedButton(
                onClick = viewModel::clearForm,
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Cancel edit") }
        }

        state.message?.let { Text(it) }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Back") }
    }
}
