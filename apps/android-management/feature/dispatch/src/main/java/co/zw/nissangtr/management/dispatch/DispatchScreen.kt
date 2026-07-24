package co.zw.nissangtr.management.dispatch

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
import co.zw.nissangtr.bridges.location.GpsBridge
import co.zw.nissangtr.management.rpc.RpcClient
import co.zw.nissangtr.management.rpc.RpcNames

/**
 * Scaffold: list DNs / pick lists + create pick → confirm → create DN → submit,
 * plus active delivery job Start/Stop GPS (Bridge-First via [gps]).
 */
@Composable
fun DispatchScreen(
    rpc: RpcClient,
    gps: GpsBridge,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DispatchViewModel = viewModel(
        factory = DispatchViewModel.factory(rpc, gps),
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
        Text("Logistics — Pick / DN / Track", style = MaterialTheme.typography.headlineSmall)
        Text(
            "RPCs: ${RpcNames.CREATE_PICK_LIST}, ${RpcNames.CONFIRM_PICK_LINES}, " +
                "${RpcNames.CREATE_DELIVERY_NOTE}, ${RpcNames.SUBMIT_DELIVERY_NOTE}",
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            "GPS: ${RpcNames.INGEST_DELIVERY_LOCATION} via bridges/location-tracker only.",
            style = MaterialTheme.typography.bodySmall,
        )

        OutlinedTextField(
            value = state.salesInvoiceId,
            onValueChange = viewModel::onSalesInvoiceIdChange,
            label = { Text("Sales invoice UUID") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !state.busy && !state.tracking,
        )
        OutlinedTextField(
            value = state.invoiceLineId,
            onValueChange = viewModel::onInvoiceLineIdChange,
            label = { Text("Invoice line UUID") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !state.busy && !state.tracking,
        )
        OutlinedTextField(
            value = state.qty,
            onValueChange = viewModel::onQtyChange,
            label = { Text("Qty (pick / DN line)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !state.busy && !state.tracking,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = viewModel::createPickList,
                enabled = !state.busy && !state.tracking,
            ) { Text("Create pick") }
            Button(
                onClick = viewModel::confirmSelectedPick,
                enabled = !state.busy && !state.tracking,
            ) { Text("Confirm pick") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = viewModel::createDeliveryNote,
                enabled = !state.busy && !state.tracking,
            ) { Text("Create DN") }
            Button(
                onClick = viewModel::submitSelectedDn,
                enabled = !state.busy && !state.tracking,
            ) { Text("Submit DN") }
            OutlinedButton(
                onClick = viewModel::refresh,
                enabled = !state.busy,
            ) { Text("Refresh") }
        }

        HorizontalDivider()
        Text("Delivery tracking (driver)", style = MaterialTheme.typography.titleMedium)
        Text(
            "Create job from submitted DN → Mark dispatched → Start tracking. " +
                "Permission via bridge; ≥5s client throttle.",
            style = MaterialTheme.typography.bodySmall,
        )
        OutlinedTextField(
            value = state.deliveryJobId,
            onValueChange = viewModel::onDeliveryJobIdChange,
            label = { Text("Delivery job UUID") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !state.busy && !state.tracking,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = viewModel::createDeliveryJob,
                enabled = !state.busy && !state.tracking,
            ) { Text("Create job") }
            Button(
                onClick = viewModel::markJobDispatched,
                enabled = !state.busy && !state.tracking,
            ) { Text("Mark dispatched") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = viewModel::startTracking,
                enabled = !state.busy && !state.tracking,
            ) { Text("Start tracking") }
            OutlinedButton(
                onClick = viewModel::stopTracking,
                enabled = state.tracking,
            ) { Text("Stop tracking") }
        }
        if (state.tracking) {
            Text(
                "Tracking… ingests=${state.ingestCount}" +
                    (state.lastLatLng?.let { " last=$it" } ?: "") +
                    (state.lastIngestId?.let { " id=$it" } ?: ""),
                style = MaterialTheme.typography.bodySmall,
            )
        }

        state.message?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        state.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
        }

        HorizontalDivider()
        Text("Pick lists", style = MaterialTheme.typography.titleMedium)
        state.pickLists.forEach { pl ->
            val selected = pl.id == state.selectedPickListId
            Text(
                text = "${pl.documentNumber}  ${pl.status}" +
                    if (selected) "  ✓" else "",
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !state.tracking) { viewModel.selectPickList(pl.id) }
                    .padding(vertical = 4.dp),
                style = if (selected) {
                    MaterialTheme.typography.bodyLarge
                } else {
                    MaterialTheme.typography.bodyMedium
                },
            )
        }

        HorizontalDivider()
        Text("Delivery notes", style = MaterialTheme.typography.titleMedium)
        state.deliveryNotes.forEach { dn ->
            val selected = dn.id == state.selectedDnId
            Text(
                text = "${dn.documentNumber}  ${dn.status}" +
                    if (selected) "  ✓" else "",
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !state.tracking) { viewModel.selectDn(dn.id) }
                    .padding(vertical = 4.dp),
                style = if (selected) {
                    MaterialTheme.typography.bodyLarge
                } else {
                    MaterialTheme.typography.bodyMedium
                },
            )
        }

        OutlinedButton(
            onClick = {
                if (state.tracking) viewModel.stopTracking()
                onBack()
            },
        ) { Text("Back") }
    }
}
