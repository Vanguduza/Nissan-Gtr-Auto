package co.zw.nissangtr.customer.garage

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import co.zw.nissangtr.customer.rpc.RpcClient
import co.zw.nissangtr.customer.rpc.RpcNames

/**
 * Thin My Garage scaffold — upsert / delete via AuthZ RPCs; list via RLS.
 */
@Composable
fun GarageScreen(
    rpc: RpcClient,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: GarageViewModel = viewModel(factory = GarageViewModel.factory(rpc)),
) {
    val state by viewModel.state.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("My Garage", style = MaterialTheme.typography.headlineSmall)
        Text(
            "RPCs: ${RpcNames.UPSERT_CUSTOMER_GARAGE_VEHICLE}, ${RpcNames.DELETE_CUSTOMER_GARAGE_VEHICLE}",
            style = MaterialTheme.typography.bodySmall,
        )

        OutlinedTextField(
            value = state.make,
            onValueChange = viewModel::onMakeChange,
            label = { Text("Make") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !state.busy,
        )
        OutlinedTextField(
            value = state.model,
            onValueChange = viewModel::onModelChange,
            label = { Text("Model") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !state.busy,
        )
        OutlinedTextField(
            value = state.generation,
            onValueChange = viewModel::onGenerationChange,
            label = { Text("Generation") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !state.busy,
        )
        OutlinedTextField(
            value = state.engine,
            onValueChange = viewModel::onEngineChange,
            label = { Text("Engine") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !state.busy,
        )
        OutlinedTextField(
            value = state.vin,
            onValueChange = viewModel::onVinChange,
            label = { Text("VIN (optional)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !state.busy,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = state.isPrimary,
                onCheckedChange = viewModel::onPrimaryChange,
                enabled = !state.busy,
            )
            Text("Primary / sticky fitment")
        }
        Button(
            onClick = viewModel::upsert,
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Save vehicle") }

        Text("Saved vehicles", style = MaterialTheme.typography.titleSmall)
        state.vehicles.forEach { v ->
            val label = listOfNotNull(v.make, v.model, v.generation, v.engine)
                .joinToString(" · ")
                .ifBlank { v.vin?.let { "VIN $it" } ?: v.id }
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Text(
                    if (v.isPrimary) "★ $label" else label,
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedButton(
                    onClick = { viewModel.delete(v.id) },
                    enabled = !state.busy,
                ) { Text("Delete") }
            }
            HorizontalDivider()
        }

        state.message?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        OutlinedButton(onClick = onBack) { Text("Back") }
    }
}
