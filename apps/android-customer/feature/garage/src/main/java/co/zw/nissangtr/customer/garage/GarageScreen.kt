package co.zw.nissangtr.customer.garage

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import co.zw.nissangtr.customer.catalog.VehicleSelectorSection
import co.zw.nissangtr.customer.rpc.RpcClient
import co.zw.nissangtr.customer.rpc.summaryLabel
import co.zw.nissangtr.ui.shop.ShopDefaultScreen
import co.zw.nissangtr.ui.shop.ShopSectionHeader

/**
 * My Garage — cascading maker → model → generation → engine via shared
 * [VehicleSelectorSection] / [co.zw.nissangtr.customer.rpc.VehicleCascade]
 * over live `vehicle_master` (same source as homepage Select Vehicle).
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
    val formBusy = state.busy || state.catalogBusy

    ShopDefaultScreen(
        title = "My Garage",
        subtitle = null,
        onBack = onBack,
        modifier = modifier,
    ) {
        ShopSectionHeader(title = "Vehicle", actionLabel = null)
        key(state.formEpoch) {
            VehicleSelectorSection(
                vehicleRows = state.vehicleRows,
                confirmedVehicle = null,
                busy = formBusy,
                error = state.error,
                onConfirmCascade = viewModel::saveFromCascade,
                onConfirmVin = viewModel::saveFromVin,
                onClear = viewModel::clearFormError,
                sectionTitle = "Add from catalog",
                confirmLabel = "Save vehicle",
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = state.isPrimary,
                onCheckedChange = viewModel::onPrimaryChange,
                enabled = !formBusy,
            )
            Text("Primary / sticky fitment")
        }

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
                    enabled = !formBusy,
                    shape = sharp,
                ) { Text("Delete") }
            }
            HorizontalDivider()
        }

        state.message?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        OutlinedButton(onClick = onBack, shape = sharp) { Text("Back") }
    }
}
