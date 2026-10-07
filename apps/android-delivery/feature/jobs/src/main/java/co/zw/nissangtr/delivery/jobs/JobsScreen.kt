package co.zw.nissangtr.delivery.jobs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import co.zw.nissangtr.bridges.location.GpsBridge
import co.zw.nissangtr.bridges.maps.DirectionsRouteFetcher
import co.zw.nissangtr.bridges.maps.MapLatLng
import co.zw.nissangtr.bridges.maps.MapStop
import co.zw.nissangtr.bridges.podcamera.PodCameraBridge
import co.zw.nissangtr.bridges.podsignature.PodSignatureBridge
import co.zw.nissangtr.delivery.design.Slopes
import co.zw.nissangtr.delivery.design.SlopesAction
import co.zw.nissangtr.delivery.design.SlopesActionTiles
import co.zw.nissangtr.delivery.design.SlopesBanner
import co.zw.nissangtr.delivery.design.SlopesCheckRow
import co.zw.nissangtr.delivery.design.SlopesDestructiveButton
import co.zw.nissangtr.delivery.design.SlopesDrawer
import co.zw.nissangtr.delivery.design.SlopesGroup
import co.zw.nissangtr.delivery.design.SlopesIconBadge
import co.zw.nissangtr.delivery.design.SlopesLargeTitle
import co.zw.nissangtr.delivery.design.SlopesLeadingCount
import co.zw.nissangtr.delivery.design.SlopesMapControlButton
import co.zw.nissangtr.delivery.design.SlopesMapControls
import co.zw.nissangtr.delivery.design.SlopesMapFab
import co.zw.nissangtr.delivery.design.SlopesMapSheetLayout
import co.zw.nissangtr.delivery.design.SlopesModalSheet
import co.zw.nissangtr.delivery.design.SlopesMode
import co.zw.nissangtr.delivery.design.SlopesPill
import co.zw.nissangtr.delivery.design.SlopesPrimaryButton
import co.zw.nissangtr.delivery.design.SlopesRoundButton
import co.zw.nissangtr.delivery.design.SlopesRow
import co.zw.nissangtr.delivery.design.SlopesSearchField
import co.zw.nissangtr.delivery.design.SlopesSectionHeader
import co.zw.nissangtr.delivery.design.SlopesSegment
import co.zw.nissangtr.delivery.design.SlopesStat
import co.zw.nissangtr.delivery.design.SlopesStatRow
import co.zw.nissangtr.delivery.design.SlopesStatusChip
import co.zw.nissangtr.delivery.design.SlopesTextField
import co.zw.nissangtr.delivery.design.SlopesTimeline
import co.zw.nissangtr.delivery.design.SlopesToggleRow
import co.zw.nissangtr.delivery.design.SlopesTone
import co.zw.nissangtr.delivery.design.neuRaised
import co.zw.nissangtr.delivery.pod.PodSection
import co.zw.nissangtr.delivery.rpc.DeliveryFailureReason
import co.zw.nissangtr.delivery.rpc.DeliveryJobSummary
import co.zw.nissangtr.delivery.rpc.DriverPresenceStatus
import co.zw.nissangtr.delivery.rpc.RpcClient
import co.zw.nissangtr.delivery.rpc.displayAmountDue
import co.zw.nissangtr.delivery.rpc.formatReceiptRow
import co.zw.nissangtr.delivery.tracking.TrackingUiState
import co.zw.nissangtr.delivery.tracking.TrackingViewModel
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

// =============================================================================================
// Today — the next stop up front; the rest of the day one tap away.
// =============================================================================================

/**
 * Today tab: map of the day's drops, and a sheet with the next stop, three numbers, the rest of
 * today's stops, and finished stops folded into a drawer. Status changes in a pop-up.
 */
@Composable
fun JobsListScreen(
    state: JobsUiState,
    vm: JobsViewModel,
    trackingVm: TrackingViewModel,
    modifier: Modifier = Modifier,
    tracking: TrackingUiState = TrackingUiState(),
) {
    val c = Slopes.colors
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var statusOpen by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    val active = remember(state.jobs) {
        state.jobs.filter { JobStatusGate.isActive(it.status) }.sortedBy { it.routeSequence ?: Int.MAX_VALUE }
    }
    val finished = remember(state.jobs) { state.jobs.filterNot { JobStatusGate.isActive(it.status) } }
    val distanceByJob = remember(state.optimizedStops) { state.optimizedStops.associate { it.deliveryJobId to it.distanceM } }
    fun matches(job: DeliveryJobSummary): Boolean {
        val q = query.trim()
        return q.isEmpty() || listOfNotNull(job.documentNumber, job.dropoffAddressText, job.notes).any { it.contains(q, ignoreCase = true) }
    }
    val stops = remember(active) { active.mapNotNull { it.toMapStop() } }
    val driver = tracking.driverPosition()
    val next = active.firstOrNull()

    SlopesMapSheetLayout(
        modifier = modifier,
        collapsedFraction = 0.55f,
        map = { obscured ->
            DeliveryMap(
                DeliveryMapSpec(
                    destination = stops.firstOrNull()?.position ?: driver,
                    driver = driver,
                    otherStops = stops.drop(1),
                    styleUrl = state.mapStyleUrl,
                    obscuredBottomPx = obscured,
                ),
            )
        },
        mapControls = {
            SlopesMapControls {
                SlopesMapControlButton(Icons.Filled.Refresh, "Refresh jobs", vm::refresh, enabled = !state.busy)
            }
        },
        sheetHeader = {
            SlopesLargeTitle(title = "Today", subtitle = todayLabel()) {
                SlopesStatusChip(state.presence.displayLabel(), presenceColor(state.presence), onClick = { statusOpen = true })
                SlopesRoundButton(Icons.Filled.Search, "Search stops", onClick = { searchOpen = !searchOpen })
            }
        },
    ) {
        if (searchOpen) {
            SlopesSearchField(value = query, onValueChange = { query = it }, placeholder = "Search stops or addresses")
            Spacer(Modifier.height(16.dp))
        }
        state.error?.let {
            SlopesBanner(it.trim(), tone = SlopesTone.Danger, icon = Icons.Filled.ReportProblem)
            Spacer(Modifier.height(16.dp))
        }

        if (next != null && query.isBlank()) {
            NextStopCard(next, distanceByJob[next.id]) { vm.selectJob(next.id) }
            Spacer(Modifier.height(18.dp))
        }
        SlopesStatRow(
            listOf(
                SlopesStat(active.size.toString(), "stops left"),
                SlopesStat(state.jobs.count { it.status == "completed" }.toString(), "delivered"),
                SlopesStat(codTotalLabel(active) ?: "—", "to collect"),
            ),
        )

        val later = active.filter { (query.isNotBlank() || it.id != next?.id) && matches(it) }
        if (later.isNotEmpty() || query.isNotBlank()) {
            SlopesSectionHeader(if (query.isBlank()) "Later today" else "Matching stops")
            SlopesGroup {
                if (later.isEmpty()) {
                    SlopesRow(title = "No stops match “$query”", leading = { SlopesIconBadge(Icons.Filled.Search) }, divider = false)
                }
                later.forEachIndexed { i, job ->
                    val meters = distanceByJob[job.id]
                    SlopesRow(
                        title = job.documentNumber ?: "Job ${job.id.take(8)}",
                        subtitle = job.dropoffAddressText ?: job.notes,
                        leading = {
                            if (meters != null) {
                                SlopesLeadingCount("%.1f".format(meters / 1000), "km")
                            } else {
                                SlopesLeadingCount("#${job.routeSequence ?: i + 2}", "stop")
                            }
                        },
                        divider = i < later.lastIndex,
                        onClick = { vm.selectJob(job.id) },
                    )
                }
            }
        } else if (next == null) {
            Spacer(Modifier.height(18.dp))
            SlopesGroup {
                SlopesRow(
                    title = "No deliveries waiting",
                    subtitle = "Go on duty and refresh",
                    leading = { SlopesIconBadge(Icons.Filled.LocalShipping) },
                    divider = false,
                )
            }
        }

        val done = finished.filter(::matches)
        if (done.isNotEmpty()) {
            Spacer(Modifier.height(20.dp))
            val delivered = done.count { it.status == "completed" }
            SlopesDrawer(
                title = "Finished",
                summary = listOfNotNull(
                    "$delivered delivered".takeIf { delivered > 0 },
                    "${done.size - delivered} failed".takeIf { done.size > delivered },
                ).joinToString(" · "),
                icon = Icons.Filled.TaskAlt,
                iconTint = c.success,
            ) {
                done.forEachIndexed { i, job ->
                    val ok = job.status == "completed"
                    SlopesRow(
                        title = job.documentNumber ?: job.id.take(8),
                        subtitle = if (ok) job.dropoffAddressText else job.failureReasonCode?.let(::failureLabel),
                        leading = {
                            SlopesIconBadge(
                                if (ok) Icons.Filled.CheckCircle else Icons.Filled.Cancel,
                                tint = if (ok) c.success else c.danger,
                                round = true,
                                size = 32.dp,
                            )
                        },
                        divider = i < done.lastIndex,
                        onClick = { vm.selectJob(job.id) },
                    )
                }
            }
        }
    }

    if (statusOpen) {
        SlopesModalSheet(title = "Your status", subtitle = "Dispatch sees this", onDismiss = { statusOpen = false }) {
            PresenceChooser(state.presence) { s ->
                applyPresence(s, state, vm, trackingVm)
                statusOpen = false
            }
        }
    }
}

/** Raised hero card for the stop the driver should do next. */
@Composable
private fun NextStopCard(job: DeliveryJobSummary, meters: Double?, onOpen: () -> Unit) {
    val c = Slopes.colors
    val cod = job.settlement?.displayAmountDue()?.takeIf { it > 0.0 }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .neuRaised(cornerRadius = 22.dp, distance = 6.dp, blur = 14.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(c.surface)
            .clickable(onClick = onOpen)
            .padding(18.dp),
    ) {
        Text("NEXT STOP", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = c.accent)
        Spacer(Modifier.height(6.dp))
        Text(job.documentNumber ?: "Job ${job.id.take(8)}", style = MaterialTheme.typography.headlineSmall, color = c.label)
        job.dropoffAddressText?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = c.secondaryLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            meters?.let { SlopesPill("%.1f km".format(it / 1000), c.secondaryLabel) }
            cod?.let { SlopesPill("Collect ${job.settlement!!.currency.rpcValue} %.2f".format(it), c.accent) }
            if (job.status == "pending") SlopesPill("Pending", c.warning)
        }
        Spacer(Modifier.height(16.dp))
        SlopesPrimaryButton("Go to stop", onOpen, icon = Icons.Filled.Navigation)
    }
}

/** Status choices (pop-up body; also rendered by screenshot tests). */
@Composable
internal fun PresenceChooser(current: DriverPresenceStatus, onPick: (DriverPresenceStatus) -> Unit) {
    SlopesGroup {
        DriverPresenceStatus.entries.forEachIndexed { i, s ->
            SlopesCheckRow(
                title = s.displayLabel(),
                subtitle = when (s) {
                    DriverPresenceStatus.AVAILABLE -> "Ready for new jobs"
                    DriverPresenceStatus.ON_DUTY -> "Driving — live location shared"
                    DriverPresenceStatus.BREAK -> "Paused — location sharing stops"
                    DriverPresenceStatus.OFFLINE -> "Off shift"
                },
                selected = s == current,
                onClick = { onPick(s) },
                divider = i < DriverPresenceStatus.entries.lastIndex,
                leading = {
                    Box(Modifier.size(38.dp), contentAlignment = Alignment.Center) {
                        Box(Modifier.size(12.dp).clip(CircleShape).background(presenceColor(s)))
                    }
                },
            )
        }
    }
}

@Composable
private fun presenceColor(s: DriverPresenceStatus): Color {
    val c = Slopes.colors
    return when (s) {
        DriverPresenceStatus.AVAILABLE -> c.success
        DriverPresenceStatus.ON_DUTY -> c.accent
        DriverPresenceStatus.BREAK -> c.warning
        DriverPresenceStatus.OFFLINE -> c.tertiaryLabel
    }
}

private fun applyPresence(
    status: DriverPresenceStatus,
    state: JobsUiState,
    vm: JobsViewModel,
    trackingVm: TrackingViewModel,
) {
    if (state.busy) return
    vm.setPresence(status)
    when (status) {
        DriverPresenceStatus.ON_DUTY ->
            state.jobs.firstOrNull { JobStatusGate.isActive(it.status) }?.let { trackingVm.startTracking(it.id) }
        DriverPresenceStatus.BREAK, DriverPresenceStatus.OFFLINE -> trackingVm.stopTracking()
        DriverPresenceStatus.AVAILABLE -> Unit
    }
}

// =============================================================================================
// Route — actions, progress and the stop order; GPS detail in a drawer.
// =============================================================================================

@Composable
fun DeliveryRouteTab(
    state: JobsUiState,
    tracking: TrackingUiState,
    vm: JobsViewModel,
    trackingVm: TrackingViewModel,
    modifier: Modifier = Modifier,
) {
    val c = Slopes.colors
    val ordered = remember(state.jobs, state.optimizedStops) { orderedStops(state) }
    val stops = remember(ordered) { ordered.filter { JobStatusGate.isActive(it.status) }.mapNotNull { it.toMapStop() } }
    val driver = tracking.driverPosition()
    val nextActive = ordered.firstOrNull { JobStatusGate.isActive(it.status) }
    val totalKm = state.optimizedStops.mapNotNull { it.distanceM }.sum() / 1000.0
    val hasActive = nextActive != null

    SlopesMapSheetLayout(
        modifier = modifier,
        collapsedFraction = 0.52f,
        map = { obscured ->
            DeliveryMap(
                DeliveryMapSpec(
                    destination = stops.firstOrNull()?.position ?: driver,
                    driver = driver,
                    otherStops = stops.drop(1),
                    styleUrl = state.mapStyleUrl,
                    obscuredBottomPx = obscured,
                ),
            )
        },
        mapControls = {
            SlopesMapControls {
                SlopesMapControlButton(Icons.Filled.Refresh, "Refresh jobs", vm::refresh, enabled = !state.busy)
            }
        },
        sheetHeader = {
            SlopesLargeTitle(
                title = "Route",
                subtitle = buildString {
                    append("${stops.size} ${if (stops.size == 1) "stop" else "stops"} left")
                    if (totalKm > 0) append(" · %.1f km".format(totalKm))
                },
            )
        },
    ) {
        SlopesActionTiles(
            listOf(
                if (tracking.tracking) {
                    SlopesAction("Stop GPS", Icons.Filled.Stop, trackingVm::stopTracking, primary = true)
                } else {
                    SlopesAction(
                        "Start GPS",
                        Icons.Filled.PlayArrow,
                        {
                            nextActive?.let { job ->
                                trackingVm.startTracking(job.id)
                                if (state.presence != DriverPresenceStatus.ON_DUTY) vm.setPresence(DriverPresenceStatus.ON_DUTY)
                            }
                        },
                        primary = true,
                        enabled = hasActive,
                    )
                },
                SlopesAction("Optimise", Icons.Filled.Route, vm::optimizeStops, enabled = !state.busy && hasActive),
                SlopesAction("Next stop", Icons.Filled.Navigation, { nextActive?.let { vm.selectJob(it.id) } }, enabled = hasActive),
            ),
        )
        state.error?.let {
            Spacer(Modifier.height(16.dp))
            SlopesBanner(it.trim(), tone = SlopesTone.Danger, icon = Icons.Filled.ReportProblem)
        }

        if (ordered.isNotEmpty()) {
            SlopesSectionHeader("Progress")
            SlopesTimeline(
                segments = ordered.mapIndexed { i, job ->
                    SlopesSegment(
                        weight = 1f,
                        color = when {
                            job.status == "completed" -> c.success
                            job.status == "failed" -> c.warning
                            job.id == nextActive?.id -> c.accent
                            else -> c.separator
                        },
                        label = (job.routeSequence ?: (i + 1)).toString(),
                    )
                },
                startLabel = "Depot",
                endLabel = "${ordered.count { !JobStatusGate.isActive(it.status) }} of ${ordered.size} done",
            )
        }

        SlopesSectionHeader("Stop order")
        SlopesGroup {
            if (ordered.isEmpty()) {
                SlopesRow(
                    title = "No stops yet",
                    subtitle = "Assigned deliveries appear here in driving order",
                    leading = { SlopesIconBadge(Icons.Filled.Route) },
                    divider = false,
                )
            }
            ordered.forEachIndexed { i, job ->
                val active = JobStatusGate.isActive(job.status)
                SlopesRow(
                    title = job.documentNumber ?: job.id.take(8),
                    subtitle = job.dropoffAddressText,
                    titleColor = if (active) null else c.tertiaryLabel,
                    leading = { SlopesLeadingCount("${job.routeSequence ?: i + 1}", "stop") },
                    trailing = {
                        when {
                            job.status == "completed" -> SlopesPill("Done", c.success)
                            job.status == "failed" -> SlopesPill("Failed", c.warning)
                            job.id == nextActive?.id -> SlopesPill("Next", c.accent)
                        }
                    },
                    divider = i < ordered.lastIndex,
                    onClick = { vm.selectJob(job.id) },
                )
            }
        }

        Spacer(Modifier.height(20.dp))
        SlopesDrawer(
            title = "Live GPS",
            summary = if (tracking.tracking) "On · ${tracking.ingestCount} updates sent" else "Off",
            icon = Icons.Filled.GpsFixed,
            iconTint = if (tracking.tracking) c.success else c.tertiaryLabel,
        ) {
            SlopesRow("Updates sent", trailing = { ValueText(tracking.ingestCount.toString()) })
            SlopesRow("Waiting to send", trailing = { ValueText(tracking.queuedCount.toString()) })
            SlopesRow("Last position", trailing = { ValueText(tracking.lastLatLng ?: "—") }, divider = false)
        }
    }
}

private fun orderedStops(state: JobsUiState): List<DeliveryJobSummary> {
    if (state.optimizedStops.isEmpty()) return state.jobs.sortedBy { it.routeSequence ?: Int.MAX_VALUE }
    val rank = state.optimizedStops.associate { it.deliveryJobId to it.routeSequence }
    return state.jobs.sortedWith(compareBy({ rank[it.id] ?: Int.MAX_VALUE }, { it.routeSequence ?: Int.MAX_VALUE }))
}

@Composable
private fun ValueText(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = Slopes.colors.secondaryLabel, maxLines = 1)
}

// =============================================================================================
// Account — a short settings list; choices open in pop-ups.
// =============================================================================================

@Composable
fun DeliveryMeTab(
    state: JobsUiState,
    vm: JobsViewModel,
    trackingVm: TrackingViewModel,
    signedInEmail: String?,
    onSignOut: (() -> Unit)?,
    modifier: Modifier = Modifier,
    appearance: SlopesMode = SlopesMode.System,
    onAppearanceChange: (SlopesMode) -> Unit = {},
    appVersion: String? = null,
) {
    val c = Slopes.colors
    var statusOpen by rememberSaveable { mutableStateOf(false) }
    var appearanceOpen by rememberSaveable { mutableStateOf(false) }
    var cashOpen by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier
            .fillMaxSize()
            .background(c.background)
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(top = 16.dp, bottom = 28.dp),
    ) {
        SlopesLargeTitle(title = "Account")
        Spacer(Modifier.height(4.dp))
        SlopesGroup {
            SlopesRow(
                title = signedInEmail ?: "Driver",
                subtitle = "Driver · Nissan GTR Auto",
                leading = {
                    Box(Modifier.size(52.dp).clip(CircleShape).background(c.accent), contentAlignment = Alignment.Center) {
                        Text(
                            initials(signedInEmail),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = c.onAccent,
                        )
                    }
                },
                divider = false,
            )
        }

        SlopesSectionHeader("Preferences")
        SlopesGroup {
            SlopesRow(
                "Status",
                leading = {
                    Box(Modifier.size(38.dp), contentAlignment = Alignment.Center) {
                        Box(Modifier.size(12.dp).clip(CircleShape).background(presenceColor(state.presence)))
                    }
                },
                trailing = { ValueText(state.presence.displayLabel()) },
                onClick = { statusOpen = true },
            )
            SlopesRow(
                "Appearance",
                leading = { SlopesIconBadge(Icons.Filled.Contrast) },
                trailing = { ValueText(appearance.label) },
                onClick = { appearanceOpen = true },
                divider = false,
            )
        }

        SlopesSectionHeader("Shift")
        SlopesGroup {
            SlopesRow(
                "Cash to hand in",
                leading = { SlopesIconBadge(Icons.Filled.Payments) },
                trailing = { ValueText(DriverCashGate.summary(state.driverCash)) },
                onClick = {
                    vm.loadDriverCash()
                    cashOpen = true
                },
            )
            SlopesRow("Refresh my jobs", leading = { SlopesIconBadge(Icons.Filled.Refresh) }, onClick = vm::refresh)
            SlopesRow(
                "Optimise today's route",
                leading = { SlopesIconBadge(Icons.Filled.Route) },
                onClick = vm::optimizeStops,
                divider = false,
            )
        }
        state.message?.let {
            Spacer(Modifier.height(14.dp))
            SlopesBanner(it, icon = Icons.Filled.CheckCircle)
        }

        SlopesSectionHeader("Help")
        SlopesGroup {
            EmergencyRow(state.supportPhone, vm::raisePanic, divider = true)
            SlopesRow(
                title = "Call dispatch",
                leading = { SlopesIconBadge(Icons.Filled.Phone) },
                onClick = vm::dialSupport,
                divider = false,
            )
        }

        if (onSignOut != null) {
            Spacer(Modifier.height(26.dp))
            SlopesDestructiveButton(
                "Sign out",
                onSignOut,
                icon = Icons.AutoMirrored.Filled.Logout,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        }
        appVersion?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = c.tertiaryLabel,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 18.dp),
            )
        }
    }

    if (statusOpen) {
        SlopesModalSheet(title = "Your status", subtitle = "Dispatch sees this", onDismiss = { statusOpen = false }) {
            PresenceChooser(state.presence) { s ->
                applyPresence(s, state, vm, trackingVm)
                statusOpen = false
            }
        }
    }
    if (cashOpen) {
        SlopesModalSheet(title = "Cash to hand in", subtitle = "Cash collected on delivery goes to the cashier", onDismiss = { cashOpen = false }) {
            DriverCashContent(state.driverCash, state.cashBusy, state.cashError, vm::handInCash)
        }
    }
    if (appearanceOpen) {
        SlopesModalSheet(title = "Appearance", onDismiss = { appearanceOpen = false }) {
            SlopesGroup {
                SlopesMode.entries.forEachIndexed { i, m ->
                    SlopesCheckRow(
                        title = m.label,
                        subtitle = when (m) {
                            SlopesMode.System -> "Match the phone"
                            SlopesMode.Light -> "Bright, for daylight"
                            SlopesMode.Dark -> "Easier at night"
                        },
                        selected = m == appearance,
                        onClick = {
                            onAppearanceChange(m)
                            appearanceOpen = false
                        },
                        divider = i < SlopesMode.entries.lastIndex,
                    )
                }
            }
        }
    }
}

/** Emergency row in the danger colour. */
@Composable
private fun EmergencyRow(supportPhone: String, onPanic: () -> Unit, divider: Boolean) {
    val c = Slopes.colors
    SlopesRow(
        title = "Emergency – alert dispatch",
        subtitle = supportPhone.ifBlank { "Sends your location and calls support" },
        titleColor = c.danger,
        leading = { SlopesIconBadge(Icons.Filled.Phone, tint = c.danger, round = true) },
        onClick = onPanic,
        divider = divider,
    )
}

// =============================================================================================
// Stop detail — four actions and three numbers; details in drawers; proof and issues pop up.
// =============================================================================================

/** Pop-ups a stop can open. */
enum class StopPopup { Proof, Issue }

@Composable
fun JobDetailScreen(
    job: DeliveryJobSummary,
    state: JobsUiState,
    tracking: TrackingUiState,
    vm: JobsViewModel,
    trackingVm: TrackingViewModel,
    rpc: RpcClient,
    camera: PodCameraBridge,
    signature: PodSignatureBridge,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var popup by remember(job.id) { mutableStateOf<StopPopup?>(null) }
    val otherStops = remember(state.jobs, job.id) {
        state.jobs.filter { it.id != job.id && JobStatusGate.isActive(it.status) }.mapNotNull { it.toMapStop() }
    }

    LaunchedEffect(job.id, tracking.lastLat, tracking.lastLng) {
        vm.refreshRouteGuidance(tracking.lastLat, tracking.lastLng)
    }

    SlopesMapSheetLayout(
        modifier = modifier,
        collapsedFraction = 0.58f,
        map = { obscured ->
            DeliveryMap(
                DeliveryMapSpec(
                    destination = job.dropoffPosition(),
                    driver = tracking.driverPosition(),
                    routePoints = state.routePoints,
                    otherStops = otherStops,
                    styleUrl = state.mapStyleUrl,
                    obscuredBottomPx = obscured,
                ),
            )
        },
        topStart = { SlopesMapFab(Icons.AutoMirrored.Filled.ArrowBack, "Back to jobs", onBack) },
        mapControls = {
            SlopesMapControls {
                SlopesMapControlButton(
                    Icons.Filled.Refresh,
                    "Refresh route",
                    { vm.refreshRouteGuidance(tracking.lastLat, tracking.lastLng) },
                    enabled = !state.routeBusy,
                )
            }
        },
        sheetHeader = {
            SlopesLargeTitle(
                title = job.documentNumber ?: "Job ${job.id.take(8)}",
                subtitle = job.dropoffAddressText?.takeIf { it.isNotBlank() } ?: job.routeSequence?.let { "Stop #$it" },
            ) {
                SlopesRoundButton(Icons.Filled.Close, "Close", onClick = onBack, tinted = false)
            }
        },
    ) {
        StopBody(job, state, tracking, vm, trackingVm, onPopup = { popup = it })
    }

    when (popup) {
        StopPopup.Proof -> SlopesModalSheet(
            title = "Proof of delivery",
            subtitle = job.documentNumber,
            onDismiss = { popup = null },
        ) {
            PodSection(
                rpc = rpc,
                camera = camera,
                signature = signature,
                jobId = job.id,
                onCompleted = {
                    popup = null
                    vm.refresh()
                    trackingVm.stopTracking()
                    vm.selectJob(null)
                },
            )
        }
        StopPopup.Issue -> SlopesModalSheet(
            title = "Report an issue",
            subtitle = job.documentNumber,
            onDismiss = { popup = null },
        ) {
            StopIssueContent(job, state, vm)
        }
        null -> Unit
    }
}

/** Sheet body of a stop (also rendered by screenshot tests). */
@Composable
internal fun StopBody(
    job: DeliveryJobSummary,
    state: JobsUiState,
    tracking: TrackingUiState,
    vm: JobsViewModel,
    trackingVm: TrackingViewModel,
    onPopup: (StopPopup) -> Unit,
) {
    val c = Slopes.colors
    val active = JobStatusGate.isActive(job.status)
    val trackingThis = tracking.tracking && tracking.trackingJobId == job.id

    if (active) {
        SlopesActionTiles(
            listOf(
                SlopesAction("Navigate", Icons.Filled.Navigation, vm::openNavigation, primary = true, enabled = job.dropoffPosition() != null),
                SlopesAction("Arrived", Icons.Filled.Flag, vm::checkGeofence, enabled = !state.busy),
                SlopesAction("Complete", Icons.Filled.Draw, { onPopup(StopPopup.Proof) }, enabled = JobStatusGate.canOpenCompleteFlow(job)),
                SlopesAction("Issue", Icons.Filled.ReportProblem, { onPopup(StopPopup.Issue) }),
            ),
        )
    } else if (job.status == "completed") {
        SlopesBanner("Delivered — proof of delivery is on file", tone = SlopesTone.Success, icon = Icons.Filled.CheckCircle)
    } else {
        SlopesBanner(
            "Failed" + (job.failureReasonCode?.let { " · ${failureLabel(it)}" } ?: ""),
            tone = SlopesTone.Danger,
            icon = Icons.Filled.Cancel,
        )
    }

    val cod = job.settlement?.displayAmountDue()?.takeIf { it > 0.0 }
    Spacer(Modifier.height(18.dp))
    SlopesStatRow(
        listOf(
            SlopesStat(
                value = state.routeDistanceMeters?.let { if (it >= 1000) "%.1f".format(it / 1000.0) else it.toString() } ?: "—",
                unit = state.routeDistanceMeters?.let { if (it >= 1000) "km" else "m" },
                label = if (state.routeEtaSource == RouteEtaSource.STRAIGHT_LINE) "straight line" else "away",
            ),
            SlopesStat(
                value = state.routeDurationSeconds?.let { formatMinutes(it) } ?: "—",
                unit = state.routeDurationSeconds?.let { if (it >= 3600) null else "min" },
                label = if (state.routeBusy) "routing…" else "drive",
            ),
            SlopesStat(
                value = cod?.let { "%.2f".format(it) } ?: "Paid",
                unit = cod?.let { job.settlement!!.currency.rpcValue },
                label = if (cod != null) "to collect" else "nothing due",
                tint = if (cod != null) c.accent else null,
            ),
        ),
    )

    val latest = state.error ?: tracking.error
    if (latest != null) {
        Spacer(Modifier.height(16.dp))
        SlopesBanner(latest.trim(), tone = SlopesTone.Danger, icon = Icons.Filled.ReportProblem)
    } else {
        state.message?.let {
            Spacer(Modifier.height(16.dp))
            SlopesBanner(it, icon = Icons.Filled.CheckCircle)
        }
    }

    Spacer(Modifier.height(20.dp))
    SlopesDrawer(title = "Items", summary = itemsSummary(job), icon = Icons.Filled.Inventory2) {
        if (job.lineItems.isEmpty()) SlopesRow("No line items on the delivery note", divider = false)
        job.lineItems.forEachIndexed { i, line ->
            val row = line.formatReceiptRow()
            val described = line.description?.takeIf { it.isNotBlank() }
            SlopesRow(
                title = described ?: line.oemPartNumber ?: "Line item",
                subtitle = listOfNotNull(
                    "Qty ${row.substringBefore(" × ")}",
                    line.oemPartNumber?.takeIf { it.isNotBlank() && described != null },
                ).joinToString(" · "),
                trailing = { ValueText(row.substringAfterLast(" · ", "")) },
                divider = i < job.lineItems.lastIndex,
            )
        }
    }
    Spacer(Modifier.height(16.dp))
    SlopesDrawer(
        title = "Drop-off",
        summary = JobStatusGate.receiptNotes(job) ?: job.etaAt?.let { "ETA ${formatEta(it)}" } ?: job.dropoffAddressText,
        icon = Icons.Filled.Place,
    ) {
        val lines = JobStatusGate.deliveryAddressLines(job)
        SlopesRow(
            title = lines.first(),
            subtitle = lines.drop(1).joinToString(" · ").ifBlank { null },
            onClick = if (job.dropoffPosition() != null) vm::openNavigation else null,
        )
        JobStatusGate.receiptNotes(job)?.let { SlopesRow(it, subtitle = "Note from dispatch") }
        job.etaAt?.let {
            SlopesRow("ETA ${formatEta(it)}", subtitle = job.etaSeconds?.let { s -> "About ${formatMinutes(s)} min from dispatch" })
        }
        SlopesRow("Delivery note", trailing = { ValueText(job.deliveryNoteId.take(8)) }, divider = false)
    }
    if (active) {
        Spacer(Modifier.height(16.dp))
        val g = state.geofence
        SlopesDrawer(
            title = "Live tracking",
            summary = when {
                g?.suggestComplete == true -> "At the drop-off"
                g?.distanceM != null -> "%.0f m from the drop-off".format(g.distanceM)
                trackingThis -> "Sharing · ${tracking.ingestCount} updates"
                else -> "Not sharing"
            },
            icon = Icons.Filled.GpsFixed,
            iconTint = if (trackingThis) c.success else c.tertiaryLabel,
            initiallyOpen = g?.suggestArrive == true || g?.suggestComplete == true,
        ) {
            SlopesToggleRow(
                title = "Share live location",
                subtitle = if (trackingThis) "${tracking.queuedCount} waiting to send" else "Dispatch sees you on the map",
                checked = trackingThis,
                onCheckedChange = { on ->
                    if (on) {
                        trackingVm.startTracking(job.id)
                        if (state.presence != DriverPresenceStatus.ON_DUTY) vm.setPresence(DriverPresenceStatus.ON_DUTY)
                    } else {
                        trackingVm.stopTracking()
                    }
                },
            )
            SlopesRow(
                title = "Check distance",
                subtitle = g?.distanceM?.let { "%.0f m away".format(it) },
                leading = { SlopesIconBadge(Icons.Filled.MyLocation, size = 32.dp) },
                onClick = vm::checkGeofence,
                divider = g?.suggestArrive == true || g?.suggestComplete == true,
            )
            if (g?.suggestArrive == true) {
                SlopesRow(
                    "Confirm arrival",
                    leading = { SlopesIconBadge(Icons.Filled.Flag, tint = c.success, size = 32.dp) },
                    onClick = vm::markArrived,
                    divider = g.suggestComplete,
                )
            }
            if (g?.suggestComplete == true) {
                SlopesRow(
                    "Complete this delivery",
                    titleColor = c.accent,
                    leading = { SlopesIconBadge(Icons.Filled.Draw, size = 32.dp) },
                    onClick = {
                        vm.acknowledgeCompleteSuggestion()
                        onPopup(StopPopup.Proof)
                    },
                    divider = false,
                )
            }
        }
    }
}

/** Issue pop-up body: reason, notes, reattempt, then the action; emergency at the end. */
@Composable
internal fun StopIssueContent(job: DeliveryJobSummary, state: JobsUiState, vm: JobsViewModel) {
    SlopesGroup {
        DeliveryFailureReason.entries.forEachIndexed { i, reason ->
            SlopesCheckRow(
                title = failureLabel(reason.rpcValue),
                selected = state.failReason == reason,
                onClick = { vm.onFailReason(reason) },
                divider = i < DeliveryFailureReason.entries.lastIndex,
            )
        }
    }
    Spacer(Modifier.height(18.dp))
    SlopesTextField(
        value = state.failNotes,
        onValueChange = vm::onFailNotes,
        label = "Notes for dispatch",
        placeholder = "Optional",
        singleLine = false,
        modifier = Modifier.padding(horizontal = 20.dp),
    )
    Spacer(Modifier.height(18.dp))
    SlopesGroup {
        SlopesToggleRow(
            title = "Book a reattempt",
            checked = state.createReattempt,
            onCheckedChange = vm::onCreateReattempt,
            divider = false,
        )
    }
    Spacer(Modifier.height(20.dp))
    SlopesPrimaryButton(
        label = "Mark as failed",
        onClick = vm::failSelectedJob,
        enabled = !state.busy && JobStatusGate.canMarkFailed(job),
        icon = Icons.Filled.Cancel,
        modifier = Modifier.padding(horizontal = 20.dp),
    )
    Spacer(Modifier.height(18.dp))
    SlopesGroup { EmergencyRow(state.supportPhone, vm::raisePanic, divider = false) }
}

// =============================================================================================
// Single-screen entry (list ↔ detail) for embedding hosts.
// =============================================================================================

@Composable
fun JobsScreen(
    rpc: RpcClient,
    gps: GpsBridge,
    camera: PodCameraBridge,
    signature: PodSignatureBridge,
    supportPhone: String,
    trackingVm: TrackingViewModel,
    modifier: Modifier = Modifier,
    routingBaseUrl: String = DirectionsRouteFetcher.DEFAULT_ROUTING_BASE_URL,
    mapStyleUrl: String = "",
) {
    val context = LocalContext.current
    val vm: JobsViewModel = viewModel(
        factory = JobsViewModel.factory(rpc, gps, context, supportPhone, routingBaseUrl = routingBaseUrl, mapStyleUrl = mapStyleUrl),
    )
    val state by vm.state.collectAsState()
    val tracking by trackingVm.state.collectAsState()
    val selected = resolveSelectedJob(state)
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
    } else {
        JobsListScreen(state = state, vm = vm, trackingVm = trackingVm, tracking = tracking, modifier = modifier)
    }
}

// =============================================================================================
// Helpers
// =============================================================================================

/** Optional map caption — the route label only (no scaffold / OSRM nags in UI). */
internal fun jobDetailMapCaption(state: JobsUiState): String = state.routeLabel.orEmpty()

internal fun failureLabel(code: String): String = when (code) {
    DeliveryFailureReason.CUSTOMER_ABSENT.rpcValue -> "Customer not available"
    DeliveryFailureReason.REFUSED.rpcValue -> "Customer refused delivery"
    DeliveryFailureReason.WRONG_ADDRESS.rpcValue -> "Wrong or unclear address"
    DeliveryFailureReason.DAMAGED.rpcValue -> "Goods damaged"
    DeliveryFailureReason.OTHER.rpcValue -> "Something else"
    else -> code.replace('_', ' ').replaceFirstChar { it.uppercase() }
}

/** Whole minutes (rounded up) for short trips, "1h 05" beyond an hour. */
internal fun formatMinutes(seconds: Int): String {
    val m = (seconds + 59) / 60
    return if (m >= 60) "%dh %02d".format(m / 60, m % 60) else m.toString()
}

/** COD total across stops when they share one currency; null when nothing is due. */
internal fun codTotalLabel(jobs: List<DeliveryJobSummary>): String? {
    val due = jobs.mapNotNull { j ->
        j.settlement?.let { s -> s.displayAmountDue()?.takeIf { it > 0.0 }?.let { s.currency.rpcValue to it } }
    }
    if (due.isEmpty()) return null
    val currencies = due.map { it.first }.distinct()
    return if (currencies.size == 1) "${currencies.single()} %.2f".format(due.sumOf { it.second }) else "${due.size} stops"
}

/** "2 items · USD 45.50 due" for the Items drawer header. */
private fun itemsSummary(job: DeliveryJobSummary): String {
    val n = job.lineItems.size
    val count = if (n == 1) "1 item" else "$n items"
    val due = job.settlement?.displayAmountDue()?.takeIf { it > 0.0 }
    return if (due != null) "$count · ${job.settlement!!.currency.rpcValue} %.2f due".format(due) else count
}

private fun formatEta(raw: String): String =
    runCatching {
        OffsetDateTime.parse(raw).atZoneSameInstant(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"))
    }.getOrDefault(raw)

private fun todayLabel(): String =
    LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.getDefault()))

private fun initials(email: String?): String =
    email?.substringBefore('@')
        ?.split('.', '_', '-')
        ?.filter { it.isNotBlank() }
        ?.take(2)
        ?.joinToString("") { it.first().uppercase() }
        ?.ifBlank { null }
        ?: "DR"

private fun DeliveryJobSummary.dropoffPosition(): MapLatLng? {
    val lat = dropoffLat ?: return null
    val lng = dropoffLng ?: return null
    return MapLatLng(lat, lng)
}

private fun DeliveryJobSummary.toMapStop(): MapStop? =
    dropoffPosition()?.let { MapStop(id = id, label = documentNumber ?: id.take(8), position = it, sequence = routeSequence) }

private fun TrackingUiState.driverPosition(): MapLatLng? {
    val lat = lastLat ?: return null
    val lng = lastLng ?: return null
    return MapLatLng(lat, lng)
}
