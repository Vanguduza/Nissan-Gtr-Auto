package co.zw.nissangtr.delivery.jobs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import co.zw.nissangtr.bridges.location.GpsBridge
import co.zw.nissangtr.bridges.podcamera.PodCameraBridge
import co.zw.nissangtr.bridges.podsignature.PodSignatureBridge
import co.zw.nissangtr.delivery.pod.PodSection
import co.zw.nissangtr.delivery.rpc.DeliveryFailureReason
import co.zw.nissangtr.delivery.rpc.DeliveryJobSummary
import co.zw.nissangtr.delivery.rpc.DriverPresenceStatus
import co.zw.nissangtr.delivery.rpc.RpcClient
import co.zw.nissangtr.delivery.tracking.TrackingViewModel

@Composable
fun JobsScreen(
    rpc: RpcClient,
    gps: GpsBridge,
    camera: PodCameraBridge,
    signature: PodSignatureBridge,
    supportPhone: String,
    trackingVm: TrackingViewModel,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val vm: JobsViewModel = viewModel(
        factory = JobsViewModel.factory(rpc, gps, context, supportPhone),
    )
    val state by vm.state.collectAsState()
    val tracking by trackingVm.state.collectAsState()
    val selected = vm.selectedJob()

    if (selected != null) {
        JobDetailScreen(
            job = selected,
            state = state,
            tracking = tracking,
            vm = vm,
            trackingVm = trackingVm,
            rpc = rpc,
            camera = camera,
            signature = signature,
            onBack = { vm.selectJob(null) },
            modifier = modifier,
        )
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("My deliveries", style = MaterialTheme.typography.headlineSmall)
        PresenceRow(
            current = state.presence,
            busy = state.busy,
            onSelect = vm::setPresence,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = vm::refresh,
                enabled = !state.busy,
                modifier = Modifier.weight(1f),
            ) { Text("Refresh") }
            OutlinedButton(
                onClick = vm::optimizeStops,
                enabled = !state.busy,
                modifier = Modifier.weight(1f),
            ) { Text("Optimize stops") }
        }
        Button(
            onClick = vm::raisePanic,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("PANIC — alert dispatch + call support") }
        if (state.optimizedStops.isNotEmpty()) {
            Text(
                "Route order: " + state.optimizedStops.joinToString(" → ") {
                    "#${it.routeSequence} ${it.deliveryJobId.take(8)}"
                },
                style = MaterialTheme.typography.bodySmall,
            )
        }
        state.message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        HorizontalDivider()
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(state.jobs, key = { it.id }) { job ->
                JobListRow(job = job, onClick = { vm.selectJob(job.id) })
            }
        }
        onBack?.let {
            OutlinedButton(onClick = it, modifier = Modifier.fillMaxWidth()) {
                Text("Back")
            }
        }
    }
}

@Composable
private fun PresenceRow(
    current: DriverPresenceStatus,
    busy: Boolean,
    onSelect: (DriverPresenceStatus) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Presence: ${current.rpcValue}", style = MaterialTheme.typography.titleSmall)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            DriverPresenceStatus.entries.forEach { status ->
                OutlinedButton(
                    onClick = { onSelect(status) },
                    enabled = !busy,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        status.rpcValue.replace('_', '\n'),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun JobListRow(job: DeliveryJobSummary, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
    ) {
        Text(
            "${job.documentNumber ?: job.id.take(8)} · ${job.status}",
            style = MaterialTheme.typography.titleSmall,
        )
        Text(
            buildString {
                job.routeSequence?.let { append("Stop #$it · ") }
                job.etaAt?.let { append("ETA $it · ") }
                if (job.dropoffLat != null && job.dropoffLng != null) {
                    append("%.4f, %.4f".format(job.dropoffLat, job.dropoffLng))
                } else {
                    append("no dropoff coords")
                }
                job.reattemptOf?.let { append(" · reattempt") }
            },
            style = MaterialTheme.typography.bodySmall,
        )
    }
    HorizontalDivider()
}

@Composable
private fun JobDetailScreen(
    job: DeliveryJobSummary,
    state: JobsUiState,
    tracking: co.zw.nissangtr.delivery.tracking.TrackingUiState,
    vm: JobsViewModel,
    trackingVm: TrackingViewModel,
    rpc: RpcClient,
    camera: PodCameraBridge,
    signature: PodSignatureBridge,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("← Job list")
        }
        Text(
            job.documentNumber ?: job.id,
            style = MaterialTheme.typography.headlineSmall,
        )
        Text("Status: ${job.status}", style = MaterialTheme.typography.bodyMedium)
        job.notes?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        job.etaAt?.let {
            Text("ETA: $it (${job.etaSeconds ?: "?"}s)", style = MaterialTheme.typography.bodySmall)
        }

        Button(onClick = vm::openNavigation, modifier = Modifier.fillMaxWidth()) {
            Text("Navigate to dropoff")
        }

        if (tracking.tracking && tracking.trackingJobId == job.id) {
            Text(
                "GPS on · ingested ${tracking.ingestCount} · queued ${tracking.queuedCount}",
                style = MaterialTheme.typography.bodySmall,
            )
            tracking.lastLatLng?.let {
                Text("Last: $it", style = MaterialTheme.typography.bodySmall)
            }
            OutlinedButton(
                onClick = trackingVm::stopTracking,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Stop GPS tracking") }
        } else {
            Button(
                onClick = { trackingVm.startTracking(job.id) },
                modifier = Modifier.fillMaxWidth(),
                enabled = job.status == "dispatched" || job.status == "pending",
            ) { Text("Start always-on GPS (FGS)") }
        }

        OutlinedButton(
            onClick = vm::checkGeofence,
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Check geofence suggestion") }
        state.geofence?.let { g ->
            Text(
                "Distance ${g.distanceM?.let { "%.0fm".format(it) } ?: "?"} — " +
                    "suggest arrive=${g.suggestArrive}, complete=${g.suggestComplete}",
                style = MaterialTheme.typography.bodySmall,
            )
            if (g.suggestArrive) {
                OutlinedButton(
                    onClick = vm::markArrived,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Confirm arrive (manual)") }
            }
            if (g.suggestComplete) {
                OutlinedButton(
                    onClick = vm::acknowledgeCompleteSuggestion,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Acknowledge complete suggestion → POD") }
            }
        }

        HorizontalDivider()
        if (job.status != "completed" && job.status != "failed") {
            PodSection(
                rpc = rpc,
                camera = camera,
                signature = signature,
                jobId = job.id,
                onCompleted = {
                    vm.refresh()
                    trackingVm.stopTracking()
                    vm.selectJob(null)
                },
            )
        }

        HorizontalDivider()
        Text("Fail delivery", style = MaterialTheme.typography.titleMedium)
        DeliveryFailureReason.entries.forEach { reason ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { vm.onFailReason(reason) },
            ) {
                Checkbox(
                    checked = state.failReason == reason,
                    onCheckedChange = { vm.onFailReason(reason) },
                )
                Text(reason.rpcValue)
            }
        }
        OutlinedTextField(
            value = state.failNotes,
            onValueChange = vm::onFailNotes,
            label = { Text("Fail notes") },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = state.createReattempt,
                onCheckedChange = vm::onCreateReattempt,
            )
            Text("Create reattempt job")
        }
        Button(
            onClick = vm::failSelectedJob,
            enabled = !state.busy && job.status != "completed" && job.status != "failed",
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Fail delivery") }

        Button(
            onClick = vm::raisePanic,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("PANIC") }

        state.message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        tracking.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        tracking.message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    }
}
