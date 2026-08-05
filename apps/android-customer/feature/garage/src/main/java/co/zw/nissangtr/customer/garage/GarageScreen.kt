package co.zw.nissangtr.customer.garage

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.lifecycle.viewmodel.compose.viewModel
import co.zw.nissangtr.customer.rpc.RpcClient
import co.zw.nissangtr.customer.rpc.RpcNames
import co.zw.nissangtr.customer.rpc.summaryLabel
import co.zw.nissangtr.ui.shop.ShopDefaultScreen
import co.zw.nissangtr.ui.shop.ShopSectionHeader

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
    val sharp = MaterialTheme.shapes.extraSmall

    ShopDefaultScreen(
        title = "My Garage",
        subtitle = "VIN · vehicles",
        onBack = onBack,
        modifier = modifier) {
        Text(
            "RPCs: ${RpcNames.UPSERT_CUSTOMER_GARAGE_VEHICLE}, ${RpcNames.DELETE_CUSTOMER_GARAGE_VEHICLE}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        ShopSectionHeader(title = "Vehicle", actionLabel = null)
        OutlinedTextField(
            value = state.make,
            onValueChange = viewModel::onMakeChange,
            label = { Text("Make") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !state.busy,
            shape = sharp,
        )
        OutlinedTextField(
            value = state.model,
            onValueChange = viewModel::onModelChange,
            label = { Text("Model") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !state.busy,
            shape = sharp,
        )
        OutlinedTextField(
            value = state.generation,
            onValueChange = viewModel::onGenerationChange,
            label = { Text("Generation") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !state.busy,
            shape = sharp,
        )
        OutlinedTextField(
            value = state.engine,
            onValueChange = viewModel::onEngineChange,
            label = { Text("Engine") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !state.busy,
            shape = sharp,
        )
        OutlinedTextField(
            value = state.vin,
            onValueChange = viewModel::onVinChange,
            label = { Text("VIN (optional)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !state.busy,
            shape = sharp,
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
            shape = sharp,
        ) { Text("Save vehicle") }

        ShopSectionHeader(title = "Saved vehicles", actionLabel = null)
        state.vehicles.forEach { v ->
            val label = v.summaryLabel()
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    if (v.isPrimary) "★ $label" else label,
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedButton(
                    onClick = { viewModel.delete(v.id) },
                    enabled = !state.busy,
                    shape = sharp,
                ) { Text("Delete") }
            }
            HorizontalDivider()
        }

        state.message?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        OutlinedButton(onClick = onBack, shape = sharp) { Text("Back") }
    }
}
