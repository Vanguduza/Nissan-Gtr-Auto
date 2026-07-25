package co.zw.nissangtr.customer.track

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import co.zw.nissangtr.customer.rpc.RpcClient
import co.zw.nissangtr.customer.rpc.RpcNames
import co.zw.nissangtr.customer.rpc.etaLabel

/**
 * Privacy-safe active delivery track: **last point + ETA only**.
 *
 * Uses [RpcNames.GET_DELIVERY_TRACK_POINT] (owner job id and/or share token).
 * No map SDK in this app — coords as text. No historical trail / Realtime GPS list.
 * Bridge-First: does not use browser/WebView geolocation.
 */
@Composable
fun DeliveryTrackScreen(
    rpc: RpcClient,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    initialToken: String? = null,
    initialJobId: String? = null,
    viewModel: DeliveryTrackViewModel = viewModel(
        key = "track|${initialToken.orEmpty()}|${initialJobId.orEmpty()}",
        factory = DeliveryTrackViewModel.factory(rpc, initialToken, initialJobId),
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
        Text("Live delivery", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Last known location and ETA while your order is out for delivery. " +
                "Historical GPS trail is never shown.",
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            "RPC: ${RpcNames.GET_DELIVERY_TRACK_POINT} (p_delivery_job_id / p_token)",
            style = MaterialTheme.typography.bodySmall,
        )

        if (!state.tracking) {
            OutlinedTextField(
                value = state.tokenDraft,
                onValueChange = viewModel::onTokenChange,
                label = { Text("Share token (from SMS link)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !state.busy,
            )
            OutlinedTextField(
                value = state.jobIdDraft,
                onValueChange = viewModel::onJobIdChange,
                label = { Text("Delivery job id (signed-in owner)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !state.busy,
            )
            OutlinedButton(
                onClick = viewModel::startFromDrafts,
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Start tracking") }
        } else {
            Text(
                buildString {
                    append("Tracking")
                    state.activeJobId?.let { append(" · job=$it") }
                    state.activeToken?.let { append(" · token=…${it.takeLast(6)}") }
                    if (state.polling) append(" · polling")
                },
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedButton(
                onClick = viewModel::refreshOnce,
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Refresh now") }
            OutlinedButton(
                onClick = viewModel::stopTracking,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Stop") }

            state.point?.let { p ->
                Text("Status", style = MaterialTheme.typography.titleSmall)
                Text(
                    if (p.status == "dispatched") "Out for delivery" else p.status,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text("ETA", style = MaterialTheme.typography.titleSmall)
                Text(p.etaLabel() ?: "Updating…", style = MaterialTheme.typography.bodyMedium)
                Text("Last update", style = MaterialTheme.typography.titleSmall)
                Text(p.recordedAt, style = MaterialTheme.typography.bodyMedium)
                Text("Last coordinates", style = MaterialTheme.typography.titleSmall)
                Text(
                    "${"%.5f".format(p.lat)}, ${"%.5f".format(p.lng)}",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    "Map tiles not bundled — coordinates only. No GPS trail is ever shared.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            state.emptyHint?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }
        }

        Text(
            "Privacy: last point + ETA only. Full GPS history is never exposed to customers. " +
                "Active (`dispatched`) jobs only.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        state.message?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        OutlinedButton(onClick = onBack) { Text("Back") }
    }
}
