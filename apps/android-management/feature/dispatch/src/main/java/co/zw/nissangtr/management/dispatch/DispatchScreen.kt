package co.zw.nissangtr.management.dispatch

import android.content.Intent
import android.net.Uri
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import co.zw.nissangtr.management.rpc.RpcClient
import co.zw.nissangtr.management.rpc.RpcNames

/**
 * Scaffold: pick/DN + create job + **assignment** (suggest/override) +
 * **route order** + staff **live view** (ETA) + **panic inbox**.
 *
 * Driver GPS FGS / [RpcNames.INGEST_DELIVERY_LOCATION] is **not** started here —
 * sole producer is `apps/android-delivery`.
 */
@Composable
fun DispatchScreen(
    rpc: RpcClient,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    supportPhone: String = "",
    viewModel: DispatchViewModel = viewModel(
        factory = DispatchViewModel.factory(rpc, supportPhone),
    ),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Logistics — Pick / DN / Dispatch", style = MaterialTheme.typography.headlineSmall)
        Text(
            "RPCs: ${RpcNames.CREATE_PICK_LIST}, ${RpcNames.CONFIRM_PICK_LINES}, " +
                "${RpcNames.CREATE_DELIVERY_NOTE}, ${RpcNames.SUBMIT_DELIVERY_NOTE}",
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            "GPS: view-only via ${RpcNames.GET_DELIVERY_TRACK_POINT}. " +
                "Producer gated — apps/android-delivery owns FGS → " +
                RpcNames.INGEST_DELIVERY_LOCATION +
                " (ALLOW_DRIVER_GPS_PRODUCER=${DispatchViewModel.ALLOW_DRIVER_GPS_PRODUCER}).",
            style = MaterialTheme.typography.bodySmall,
        )

        OutlinedTextField(
            value = state.salesInvoiceId,
            onValueChange = viewModel::onSalesInvoiceIdChange,
            label = { Text("Sales invoice UUID") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !state.busy,
        )
        OutlinedTextField(
            value = state.invoiceLineId,
            onValueChange = viewModel::onInvoiceLineIdChange,
            label = { Text("Invoice line UUID") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !state.busy,
        )
        OutlinedTextField(
            value = state.qty,
            onValueChange = viewModel::onQtyChange,
            label = { Text("Qty (pick / DN line)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !state.busy,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = viewModel::createPickList,
                enabled = !state.busy,
            ) { Text("Create pick") }
            Button(
                onClick = viewModel::confirmSelectedPick,
                enabled = !state.busy,
            ) { Text("Confirm pick") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = viewModel::createDeliveryNote,
                enabled = !state.busy,
            ) { Text("Create DN") }
            Button(
                onClick = viewModel::submitSelectedDn,
                enabled = !state.busy,
            ) { Text("Submit DN") }
            OutlinedButton(
                onClick = viewModel::refresh,
                enabled = !state.busy,
            ) { Text("Refresh") }
        }

        HorizontalDivider()
        Text("Delivery job", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = state.deliveryJobId,
            onValueChange = viewModel::onDeliveryJobIdChange,
            label = { Text("Delivery job UUID") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !state.busy,
        )
        Text(
            "Pickup / dropoff (for suggest ranking + ETA). Fake stores; Live needs " +
                "set_delivery_job_geo RPC (blocker).",
            style = MaterialTheme.typography.bodySmall,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = state.pickupLat,
                onValueChange = viewModel::onPickupLatChange,
                label = { Text("Pickup lat") },
                modifier = Modifier.weight(1f),
                singleLine = true,
                enabled = !state.busy,
            )
            OutlinedTextField(
                value = state.pickupLng,
                onValueChange = viewModel::onPickupLngChange,
                label = { Text("Pickup lng") },
                modifier = Modifier.weight(1f),
                singleLine = true,
                enabled = !state.busy,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = state.dropoffLat,
                onValueChange = viewModel::onDropoffLatChange,
                label = { Text("Dropoff lat") },
                modifier = Modifier.weight(1f),
                singleLine = true,
                enabled = !state.busy,
            )
            OutlinedTextField(
                value = state.dropoffLng,
                onValueChange = viewModel::onDropoffLngChange,
                label = { Text("Dropoff lng") },
                modifier = Modifier.weight(1f),
                singleLine = true,
                enabled = !state.busy,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = viewModel::createDeliveryJob,
                enabled = !state.busy,
            ) { Text("Create job") }
            OutlinedButton(
                onClick = viewModel::saveJobCoords,
                enabled = !state.busy,
            ) { Text("Save coords") }
            Button(
                onClick = viewModel::markJobDispatched,
                enabled = !state.busy,
            ) { Text("Mark dispatched") }
        }
        state.trackShareToken?.let { token ->
            Text(
                "Share track token (plaintext once):\n$token",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = viewModel::mintShareToken,
                enabled = !state.busy,
            ) { Text("Mint share token") }
            OutlinedButton(
                onClick = viewModel::generatePodOtp,
                enabled = !state.busy,
            ) { Text("Generate POD OTP") }
        }
        state.podOtp?.let { otp ->
            Text(
                "POD OTP (read to customer): $otp",
                style = MaterialTheme.typography.titleMedium,
            )
        }

        HorizontalDivider()
        Text("Assignment (suggest + override)", style = MaterialTheme.typography.titleMedium)
        Text(
            "${RpcNames.SUGGEST_DELIVERY_ASSIGNEES} → nearest / capacity / shift. " +
                "Manual override via ${RpcNames.ASSIGN_DELIVERY_JOB} (p_override=true).",
            style = MaterialTheme.typography.bodySmall,
        )
        OutlinedTextField(
            value = state.assigneeUserId,
            onValueChange = viewModel::onAssigneeUserIdChange,
            label = { Text("Assignee driver UUID") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !state.busy,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = viewModel::suggestAssignees,
                enabled = !state.busy,
            ) { Text("Suggest") }
            Button(
                onClick = { viewModel.assignJob(override = false) },
                enabled = !state.busy,
            ) { Text("Assign") }
            OutlinedButton(
                onClick = { viewModel.assignJob(override = true) },
                enabled = !state.busy,
            ) { Text("Override assign") }
        }
        state.assigneeSuggestions.forEachIndexed { index, s ->
            val selected = s.userId == state.assigneeUserId
            val dist = s.distanceM?.let { "%.0fm".format(it) } ?: "n/a"
            Text(
                text = "#${index + 1}  ${s.userId.take(8)}…  ${s.status}  dist=$dist  " +
                    "open=${s.openJobs}/${s.capacity}" +
                    if (selected) "  ✓" else "",
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !state.busy) {
                        viewModel.selectSuggestedAssignee(s.userId)
                    }
                    .padding(vertical = 4.dp),
                style = if (selected) {
                    MaterialTheme.typography.bodyLarge
                } else {
                    MaterialTheme.typography.bodyMedium
                },
            )
        }

        HorizontalDivider()
        Text("Route order", style = MaterialTheme.typography.titleMedium)
        Text(
            "${RpcNames.OPTIMIZE_DRIVER_STOPS} for driver’s open jobs (writes route_sequence).",
            style = MaterialTheme.typography.bodySmall,
        )
        Button(
            onClick = viewModel::optimizeStops,
            enabled = !state.busy,
        ) { Text("Optimize stops") }
        state.optimizedStops.forEach { stop ->
            val dist = stop.distanceM?.let { "%.0fm".format(it) } ?: "n/a"
            Text(
                "#${stop.routeSequence}  ${stop.deliveryJobId.take(8)}…  $dist",
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        HorizontalDivider()
        Text("Live location / ETA (view only)", style = MaterialTheme.typography.titleMedium)
        Text(
            "Staff subscribe via ${RpcNames.GET_DELIVERY_TRACK_POINT}. " +
                "No Start tracking — delivery app produces pings.",
            style = MaterialTheme.typography.bodySmall,
        )
        Button(
            onClick = viewModel::refreshLiveTrack,
            enabled = !state.busy,
        ) { Text("Refresh live track") }
        state.liveTrack?.let { t ->
            Text(
                "lat=%.5f lng=%.5f  recorded=${t.recordedAt}".format(t.lat, t.lng) +
                    (t.etaAt?.let { "  eta=$it" } ?: "") +
                    (t.etaSeconds?.let { "  (${it}s)" } ?: "") +
                    "  status=${t.status}",
                style = MaterialTheme.typography.bodySmall,
            )
        }

        HorizontalDivider()
        Text("Panic inbox", style = MaterialTheme.typography.titleMedium)
        Text(state.pollNote, style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = viewModel::refreshPanicInbox,
                enabled = !state.busy,
            ) { Text("Refresh panics") }
            val phone = state.supportPhone
            if (phone.isNotBlank()) {
                Button(
                    onClick = {
                        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))
                        context.startActivity(intent)
                    },
                    enabled = !state.busy,
                ) { Text("Dial support") }
            } else {
                Text(
                    "Set DELIVERY_SUPPORT_PHONE in local.properties to enable dial.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        if (state.panicEvents.isEmpty()) {
            Text("No open panic events", style = MaterialTheme.typography.bodyMedium)
        }
        state.panicEvents.forEach { p ->
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Text(
                    "driver=${p.driverUserId.take(8)}…  at=${p.createdAt}" +
                        (p.deliveryJobId?.let { "  job=${it.take(8)}…" } ?: "") +
                        (if (p.lat != null && p.lng != null) {
                            "  %.4f,%.4f".format(p.lat, p.lng)
                        } else {
                            ""
                        }),
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedButton(
                    onClick = { viewModel.acknowledgePanic(p.id) },
                    enabled = !state.busy,
                ) { Text("Mark handled") }
            }
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
                    .clickable(enabled = !state.busy) { viewModel.selectPickList(pl.id) }
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
                    .clickable(enabled = !state.busy) { viewModel.selectDn(dn.id) }
                    .padding(vertical = 4.dp),
                style = if (selected) {
                    MaterialTheme.typography.bodyLarge
                } else {
                    MaterialTheme.typography.bodyMedium
                },
            )
        }

        OutlinedButton(onClick = onBack) { Text("Back") }
    }
}
