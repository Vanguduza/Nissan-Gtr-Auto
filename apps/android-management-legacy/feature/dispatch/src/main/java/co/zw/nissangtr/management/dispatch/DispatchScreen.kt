package co.zw.nissangtr.management.dispatch

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import co.zw.nissangtr.management.rpc.RpcClient
import co.zw.nissangtr.ui.shop.ShopListCard
import co.zw.nissangtr.ui.shop.ShopPrimaryButton
import co.zw.nissangtr.ui.shop.ShopSecondaryButton
import co.zw.nissangtr.ui.shop.ShopStaffPanel
import co.zw.nissangtr.ui.shop.ShopStaffScreen
import co.zw.nissangtr.ui.shop.ShopStatusChip
import co.zw.nissangtr.ui.theme.GtrColors

/**
 * Pick/DN desk + create job + **exception override assign** (unassigned/stuck only) +
 * **route order** + staff **live view** (ETA) + track share token + POD OTP +
 * **panic inbox**.
 *
 * Auto-assign (`_try_auto_assign_delivery_job` + FIFO/offer) remains SoR —
 * staff do not pick drivers as the happy path.
 *
 * Driver GPS FGS / [RpcNames.INGEST_DELIVERY_LOCATION] is **not** started here —
 * sole producer is `apps/android-delivery`. Drivers: use the delivery app
 * (management Start Tracking removed / hard-gated).
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

    state.pendingOverrideAssigneeUserId?.let { pending ->
        AlertDialog(
            onDismissRequest = viewModel::dismissOverrideAssignConfirm,
            title = { Text("Confirm override assign") },
            text = {
                Text(
                    "Assign driver ${pending.take(8)}… to this unassigned/stuck job? " +
                        "Auto-assign remains SoR — this is an exception override " +
                        "(p_override=true).",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = viewModel::confirmOverrideAssign,
                    enabled = !state.busy,
                ) { Text("Confirm override") }
            },
            dismissButton = {
                TextButton(
                    onClick = viewModel::dismissOverrideAssignConfirm,
                    enabled = !state.busy,
                ) { Text("Cancel") }
            },
        )
    }

    ShopStaffScreen(
        title = "Dispatch",
        subtitle = "Pick · DN · assign · track",
        modifier = modifier,
        onBack = onBack,
    ) {
        ShopStaffPanel(title = "Dispatch invoices") {
            Text(
                "Posted dispatch invoices — tap to select for Create pick.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (state.dispatchInvoices.isEmpty()) {
                Text("No posted dispatch invoices", style = MaterialTheme.typography.bodyMedium)
            }
            state.dispatchInvoices.forEach { inv ->
                val selected = inv.id == state.salesInvoiceId
                ShopListCard(
                    title = inv.documentNumber,
                    subtitle = "${inv.status} · ${inv.fulfillmentMode} · ${inv.id.take(8)}…",
                    onClick = { viewModel.selectDispatchInvoice(inv.id) },
                    badges = {
                        if (selected) {
                            ShopStatusChip(label = "✓", background = GtrColors.Accent)
                        }
                    },
                )
            }
        }

        ShopStaffPanel(title = "Pick / delivery note") {
            OutlinedTextField(
                value = state.salesInvoiceId,
                onValueChange = viewModel::onSalesInvoiceIdChange,
                label = { Text("Sales invoice UUID") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !state.busy,
            )
            ShopPrimaryButton(
                label = "Create pick",
                onClick = viewModel::createPickList,
                enabled = !state.busy,
            )
            ShopSecondaryButton(
                label = "Confirm pick lines",
                onClick = viewModel::confirmSelectedPick,
                enabled = !state.busy,
            )
            ShopPrimaryButton(
                label = "Create DN from pick lines",
                onClick = viewModel::createDeliveryNote,
                enabled = !state.busy,
            )
            ShopPrimaryButton(
                label = "Submit DN",
                onClick = viewModel::submitSelectedDn,
                enabled = !state.busy,
            )
            ShopSecondaryButton(
                label = "Cancel DN",
                onClick = viewModel::cancelSelectedDn,
                enabled = !state.busy,
            )
            ShopSecondaryButton(
                label = "Refresh",
                onClick = viewModel::refresh,
                enabled = !state.busy,
            )
            Text(
                "Advanced: paste a single invoice line UUID when pick lines are empty.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = state.invoiceLineId,
                onValueChange = viewModel::onInvoiceLineIdChange,
                label = { Text("Invoice line UUID (fallback)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !state.busy,
            )
            OutlinedTextField(
                value = state.qty,
                onValueChange = viewModel::onQtyChange,
                label = { Text("Qty fallback") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !state.busy,
            )
        }

        ShopStaffPanel(title = "Pick lists") {
            state.pickLists.forEach { pl ->
                val selected = pl.id == state.selectedPickListId
                ShopListCard(
                    title = pl.documentNumber,
                    subtitle = "${pl.status} · inv=${pl.salesInvoiceId.take(8)}…",
                    onClick = { viewModel.selectPickList(pl.id) },
                    badges = {
                        if (selected) {
                            ShopStatusChip(label = "✓", background = GtrColors.Accent)
                        }
                    },
                )
            }
        }

        ShopStaffPanel(title = "Pick lines") {
            if (state.pickLines.isEmpty()) {
                Text(
                    "Select a pick list to load lines (OEM + qty).",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            state.pickLines.forEach { line ->
                val oem = line.oemPartNumber ?: line.stockItemId.take(8)
                val label = line.description?.let { "$oem · $it" } ?: oem
                val picked = line.qtyPicked?.let { "picked=$it" } ?: "open"
                ShopListCard(
                    title = label,
                    subtitle = "$picked · req=${line.qtyRequested} · ${line.salesInvoiceLineId.take(8)}…",
                    onClick = { viewModel.selectPickLine(line) },
                )
                OutlinedTextField(
                    value = state.pickQtyDraft[line.id] ?: "",
                    onValueChange = { viewModel.onPickQtyChange(line.id, it) },
                    label = { Text("Qty for $oem") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !state.busy,
                )
            }
        }

        ShopStaffPanel(title = "Delivery notes") {
            state.deliveryNotes.forEach { dn ->
                val selected = dn.id == state.selectedDnId
                ShopListCard(
                    title = dn.documentNumber,
                    subtitle = "${dn.status} · inv=${dn.salesInvoiceId.take(8)}…",
                    onClick = { viewModel.selectDn(dn.id) },
                    badges = {
                        if (selected) {
                            ShopStatusChip(label = "✓", background = GtrColors.Accent)
                        }
                    },
                )
            }
        }

        ShopStaffPanel(title = "Delivery jobs") {
            Text(
                "Status + DN link · stuck unassigned highlighted. Auto-assign remains SoR — " +
                    "Override assign only on unassigned/stuck jobs (not a pick-driver desk).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (state.deliveryJobs.isEmpty()) {
                Text("No delivery jobs", style = MaterialTheme.typography.bodyMedium)
            }
            state.deliveryJobs.forEach { job ->
                val selected = job.id == state.deliveryJobId
                val title = job.documentNumber ?: "${job.id.take(8)}…"
                val canOverride = DeliveryOverrideAssignGate.canOverrideAssign(
                    job.assigneeUserId,
                    job.status,
                )
                ShopListCard(
                    title = title,
                    subtitle = buildString {
                        append(job.status)
                        append(" · dn=")
                        append(job.deliveryNoteId.take(8))
                        append("…")
                        if (!job.isUnassigned) {
                            append(" · driver=")
                            append(job.assigneeUserId!!.take(8))
                            append("…")
                        }
                    },
                    onClick = { viewModel.selectDeliveryJob(job.id) },
                    badges = {
                        if (job.isUnassigned) {
                            ShopStatusChip(label = "unassigned", background = GtrColors.Warning)
                        }
                        if (selected) {
                            ShopStatusChip(label = "✓", background = GtrColors.Accent)
                        }
                    },
                    trailing = {
                        if (canOverride) {
                            TextButton(
                                onClick = { viewModel.beginOverrideAssign(job.id) },
                                enabled = !state.busy,
                            ) { Text("Override assign") }
                        }
                    },
                )
            }
        }

        ShopStaffPanel(title = "Delivery job") {
            OutlinedTextField(
                value = state.deliveryJobId,
                onValueChange = viewModel::onDeliveryJobIdChange,
                label = { Text("Delivery job UUID") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !state.busy,
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
            ShopPrimaryButton(
                label = "Create job",
                onClick = viewModel::createDeliveryJob,
                enabled = !state.busy,
            )
            ShopSecondaryButton(
                label = "Save coords",
                onClick = viewModel::saveJobCoords,
                enabled = !state.busy,
            )
            ShopPrimaryButton(
                label = "Mark dispatched",
                onClick = viewModel::markJobDispatched,
                enabled = !state.busy,
            )
            state.trackShareToken?.let { token ->
                Text(
                    "Share track token:\n$token",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            ShopSecondaryButton(
                label = "Rotate share token",
                onClick = viewModel::rotateShareToken,
                enabled = !state.busy,
            )
            ShopSecondaryButton(
                label = "Generate POD OTP",
                onClick = viewModel::generatePodOtp,
                enabled = !state.busy,
            )
            state.podOtp?.let { otp ->
                Text(
                    "POD OTP (read to customer): $otp",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }

        ShopStaffPanel(title = "Override assign (exception)") {
            Text(
                "Only for unassigned/stuck jobs after auto-assign failed. " +
                    "Calls assign_delivery_job with p_override=true. Happily assigned jobs: status only.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!state.canOverrideAssign) {
                Text(
                    if (state.deliveryJobId.isBlank()) {
                        "Select an unassigned job above to enable override."
                    } else {
                        "Selected job is assigned or terminal — no pick-driver (auto-assign SoR)."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                OutlinedTextField(
                    value = state.assigneeUserId,
                    onValueChange = viewModel::onAssigneeUserIdChange,
                    label = { Text("Assignee driver UUID") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !state.busy,
                )
                ShopSecondaryButton(
                    label = "Suggest drivers",
                    onClick = viewModel::suggestAssignees,
                    enabled = !state.busy,
                )
                ShopPrimaryButton(
                    label = "Confirm override assign",
                    onClick = { viewModel.requestOverrideAssignConfirm() },
                    enabled = !state.busy && state.assigneeUserId.isNotBlank(),
                )
                state.assigneeSuggestions.forEachIndexed { index, s ->
                    val selected = s.userId == state.assigneeUserId
                    val dist = s.distanceM?.let { "%.0fm".format(it) } ?: "n/a"
                    ShopListCard(
                        title = "#${index + 1}  ${s.userId.take(8)}…  ${s.status}",
                        subtitle = "dist=$dist  open=${s.openJobs}/${s.capacity}",
                        onClick = { viewModel.selectSuggestedAssignee(s.userId) },
                        badges = {
                            if (selected) {
                                ShopStatusChip(label = "✓", background = GtrColors.Accent)
                            }
                        },
                        trailing = {
                            TextButton(
                                onClick = { viewModel.requestOverrideAssignConfirm(s.userId) },
                                enabled = !state.busy,
                            ) { Text("Override") }
                        },
                    )
                }
            }
        }

        ShopStaffPanel(title = "Route order") {
            ShopSecondaryButton(
                label = "Optimize stops",
                onClick = viewModel::optimizeStops,
                enabled = !state.busy,
            )
            state.optimizedStops.forEach { stop ->
                val dist = stop.distanceM?.let { "%.0fm".format(it) } ?: "n/a"
                Text(
                    "#${stop.routeSequence}  ${stop.deliveryJobId.take(8)}…  $dist",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        ShopStaffPanel(title = "Live location / ETA") {
            ShopSecondaryButton(
                label = "Refresh live track",
                onClick = viewModel::refreshLiveTrack,
                enabled = !state.busy,
            )
            state.liveTrack?.let { t ->
                Text(
                    "lat=%.5f lng=%.5f  recorded=${t.recordedAt}".format(t.lat, t.lng) +
                        (t.etaAt?.let { "  eta=$it" } ?: "") +
                        (t.etaSeconds?.let { "  (${it}s)" } ?: "") +
                        "  status=${t.status}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        ShopStaffPanel(title = "Panic inbox") {
            Text(state.pollNote, style = MaterialTheme.typography.bodySmall)
            ShopSecondaryButton(
                label = "Refresh panics",
                onClick = viewModel::refreshPanicInbox,
                enabled = !state.busy,
            )
            val phone = state.supportPhone
            if (phone.isNotBlank()) {
                ShopPrimaryButton(
                    label = "Dial support",
                    onClick = {
                        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))
                        context.startActivity(intent)
                    },
                    enabled = !state.busy,
                )
            } else {
                Text(
                    "Set DELIVERY_SUPPORT_PHONE in local.properties to enable dial.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (state.panicEvents.isEmpty()) {
                Text("No open panic events", style = MaterialTheme.typography.bodyMedium)
            }
            state.panicEvents.forEach { p ->
                ShopListCard(
                    title = "driver=${p.driverUserId.take(8)}…  at=${p.createdAt}",
                    subtitle = buildString {
                        p.deliveryJobId?.let { append("job=${it.take(8)}…  ") }
                        if (p.lat != null && p.lng != null) {
                            append("%.4f,%.4f".format(p.lat, p.lng))
                        }
                    }.trim(),
                    onClick = { viewModel.acknowledgePanic(p.id) },
                    trailing = {
                        TextButton(
                            onClick = { viewModel.acknowledgePanic(p.id) },
                            enabled = !state.busy,
                        ) { Text("Handled") }
                    },
                )
            }
        }

        state.message?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        state.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
        }
    }
}
