package co.zw.nissangtr.delivery.jobs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import co.zw.nissangtr.delivery.design.SlopesGroup
import co.zw.nissangtr.delivery.design.SlopesIconBadge
import co.zw.nissangtr.delivery.design.SlopesLargeTitle
import co.zw.nissangtr.delivery.design.SlopesLeadingCount
import co.zw.nissangtr.delivery.design.SlopesMapControlButton
import co.zw.nissangtr.delivery.design.SlopesMapControls
import co.zw.nissangtr.delivery.design.SlopesMapFab
import co.zw.nissangtr.delivery.design.SlopesMapSheetLayout
import co.zw.nissangtr.delivery.design.SlopesMode
import co.zw.nissangtr.delivery.design.SlopesPill
import co.zw.nissangtr.delivery.design.SlopesRoundButton
import co.zw.nissangtr.delivery.design.SlopesRow
import co.zw.nissangtr.delivery.design.SlopesSearchField
import co.zw.nissangtr.delivery.design.SlopesSectionHeader
import co.zw.nissangtr.delivery.design.SlopesSegment
import co.zw.nissangtr.delivery.design.SlopesSegmented
import co.zw.nissangtr.delivery.design.SlopesStat
import co.zw.nissangtr.delivery.design.SlopesStatRow
import co.zw.nissangtr.delivery.design.SlopesTextField
import co.zw.nissangtr.delivery.design.SlopesTimeline
import co.zw.nissangtr.delivery.design.SlopesToggleRow
import co.zw.nissangtr.delivery.design.SlopesTone
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
// Today — map of the day's stops with the job list in a sheet (Slopes "Logbook").
// =============================================================================================

/**
 * Today tab: full-bleed map of the day's drops and a draggable sheet with the day's numbers,
 * the driver's status, and the stops grouped as Up next / Delivered / Failed.
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
    var query by rememberSaveable { mutableStateOf("") }
    val active = remember(state.jobs) {
        state.jobs.filter { JobStatusGate.isActive(it.status) }.sortedBy { it.routeSequence ?: Int.MAX_VALUE }
    }
    val done = remember(state.jobs) { state.jobs.filter { it.status == "completed" } }
    val failed = remember(state.jobs) { state.jobs.filter { it.status == "failed" } }
    val distanceByJob = remember(state.optimizedStops) {
        state.optimizedStops.associate { it.deliveryJobId to it.distanceM }
    }
    fun matches(job: DeliveryJobSummary): Boolean {
        val q = query.trim()
        if (q.isEmpty()) return true
        return listOfNotNull(job.documentNumber, job.dropoffAddressText, job.notes)
            .any { it.contains(q, ignoreCase = true) }
    }
    val codDue = remember(active) { codTotalLabel(active) }
    val stops = remember(active) { active.mapNotNull { it.toMapStop() } }
    val driver = tracking.driverPosition()

    SlopesMapSheetLayout(
        modifier = modifier,
        collapsedFraction = 0.56f,
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
                SlopesMapControlButton(Icons.Filled.Route, "Optimise route", vm::optimizeStops, divider = true, enabled = !state.busy)
            }
        },
        sheetHeader = {
            SlopesLargeTitle(
                title = "Today",
                subtitle = "${todayLabel()} · ${state.presence.displayLabel()}",
            ) {
                SlopesRoundButton(Icons.Filled.Search, "Search stops", onClick = { searchOpen = !searchOpen })
            }
        },
    ) {
        SlopesStatRow(
            listOf(
                SlopesStat(active.size.toString(), "to deliver", unit = "stops", icon = Icons.Filled.Place),
                SlopesStat(done.size.toString(), "delivered", icon = Icons.Filled.CheckCircle),
                SlopesStat(failed.size.toString(), "failed", icon = Icons.Filled.Cancel),
                SlopesStat(codDue ?: "—", "to collect", icon = Icons.Filled.Payments),
            ),
        )
        Spacer(Modifier.height(16.dp))
        PresenceControl(state = state, vm = vm, trackingVm = trackingVm)
        StatusBanners(state.message, state.error)

        if (searchOpen) {
            Spacer(Modifier.height(14.dp))
            SlopesSearchField(value = query, onValueChange = { query = it }, placeholder = "Search stops, addresses, notes")
        }

        SlopesSectionHeader("Up next", action = if (active.size > 1) "Optimise" else null, onAction = vm::optimizeStops)
        val upNext = active.filter(::matches)
        SlopesGroup {
            if (upNext.isEmpty()) {
                SlopesRow(
                    title = if (query.isBlank()) "No deliveries waiting" else "No stops match “$query”",
                    subtitle = if (query.isBlank()) "Go on duty and refresh to pull new jobs." else null,
                    leading = { SlopesIconBadge(Icons.Filled.LocalShipping) },
                    divider = false,
                )
            }
            upNext.forEachIndexed { i, job ->
                JobRow(
                    job = job,
                    leading = {
                        val meters = distanceByJob[job.id]
                        if (meters != null) {
                            SlopesLeadingCount("%.1f".format(meters / 1000), "km")
                        } else {
                            SlopesLeadingCount("#${job.routeSequence ?: i + 1}", "stop")
                        }
                    },
                    divider = i < upNext.lastIndex,
                    onClick = { vm.selectJob(job.id) },
                )
            }
        }

        val delivered = done.filter(::matches)
        if (delivered.isNotEmpty()) {
            SlopesSectionHeader("Delivered")
            SlopesGroup {
                delivered.forEachIndexed { i, job ->
                    JobRow(
                        job = job,
                        leading = {
                            SlopesIconBadge(Icons.Filled.CheckCircle, tint = c.success, container = c.success.copy(alpha = 0.14f), round = true)
                        },
                        divider = i < delivered.lastIndex,
                        onClick = { vm.selectJob(job.id) },
                    )
                }
            }
        }
        val failedShown = failed.filter(::matches)
        if (failedShown.isNotEmpty()) {
            SlopesSectionHeader("Failed")
            SlopesGroup {
                failedShown.forEachIndexed { i, job ->
                    JobRow(
                        job = job,
                        subtitleOverride = job.failureReasonCode?.let { failureLabel(it) },
                        leading = { SlopesIconBadge(Icons.Filled.Cancel, tint = c.danger, container = c.dangerTint, round = true) },
                        divider = i < failedShown.lastIndex,
                        onClick = { vm.selectJob(job.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun JobRow(
    job: DeliveryJobSummary,
    leading: @Composable () -> Unit,
    divider: Boolean,
    onClick: () -> Unit,
    subtitleOverride: String? = null,
) {
    val c = Slopes.colors
    val cod = job.settlement?.displayAmountDue()?.takeIf { it > 0.0 }
    SlopesRow(
        title = job.documentNumber ?: "Job ${job.id.take(8)}",
        subtitle = subtitleOverride
            ?: job.dropoffAddressText?.takeIf { it.isNotBlank() }
            ?: job.notes?.takeIf { it.isNotBlank() }
            ?: "Tap for the drop-off and receipt",
        subtitleIcon = if (subtitleOverride == null) Icons.Filled.Place else null,
        leading = leading,
        divider = divider,
        onClick = onClick,
        trailing = {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (job.reattemptOf != null) SlopesPill("Reattempt", c.warning)
                if (cod != null) SlopesPill("${job.settlement!!.currency.rpcValue} %.2f".format(cod), c.accent)
                if (job.status == "pending") SlopesPill("Pending", c.secondaryLabel)
            }
        },
    )
}

/** Four-way driver status; going on duty starts live GPS, a break or offline stops it. */
@Composable
private fun PresenceControl(
    state: JobsUiState,
    vm: JobsViewModel,
    trackingVm: TrackingViewModel,
) {
    val options = DriverPresenceStatus.entries
    SlopesSegmented(
        options = options.map { it.displayLabel() },
        selected = options.indexOf(state.presence).coerceAtLeast(0),
        onSelect = { i -> applyPresence(options[i], state, vm, trackingVm) },
    )
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

@Composable
private fun StatusBanners(message: String?, error: String?) {
    if (message != null) {
        Spacer(Modifier.height(12.dp))
        SlopesBanner(message, tone = SlopesTone.Info, icon = Icons.Filled.CheckCircle)
    }
    if (error != null) {
        Spacer(Modifier.height(12.dp))
        SlopesBanner(error.trim(), tone = SlopesTone.Danger, icon = Icons.Filled.ReportProblem)
    }
}

// =============================================================================================
// Route — the day's run: map, live GPS, a progress timeline and the stop order.
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
    val stops = remember(ordered) {
        ordered.filter { JobStatusGate.isActive(it.status) }.mapNotNull { it.toMapStop() }
    }
    val driver = tracking.driverPosition()
    val nextActive = ordered.firstOrNull { JobStatusGate.isActive(it.status) }
    val totalKm = state.optimizedStops.mapNotNull { it.distanceM }.sum() / 1000.0

    SlopesMapSheetLayout(
        modifier = modifier,
        collapsedFraction = 0.54f,
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
                    if (totalKm > 0) append(" · %.1f km planned".format(totalKm))
                },
            )
        },
    ) {
        val hasActive = nextActive != null
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
        Spacer(Modifier.height(16.dp))
        SlopesStatRow(
            listOf(
                SlopesStat(
                    if (tracking.tracking) "On" else "Off",
                    "live GPS",
                    icon = Icons.Filled.GpsFixed,
                    tint = if (tracking.tracking) c.success else null,
                ),
                SlopesStat(tracking.ingestCount.toString(), "sent", unit = "pings", icon = Icons.Filled.CloudUpload),
                SlopesStat(tracking.queuedCount.toString(), "waiting", unit = "queued", icon = Icons.Filled.Schedule),
                SlopesStat(
                    if (totalKm > 0) "%.1f".format(totalKm) else "—",
                    "planned",
                    unit = if (totalKm > 0) "km" else null,
                    icon = Icons.Filled.Straighten,
                ),
            ),
        )
        StatusBanners(state.message, state.error ?: tracking.error)

        if (ordered.isNotEmpty()) {
            SlopesSectionHeader("Progress")
            SlopesTimeline(
                segments = ordered.mapIndexed { i, job ->
                    SlopesSegment(
                        weight = 1f,
                        color = when {
                            job.status == "completed" -> c.success
                            job.status == "failed" -> c.danger
                            job.id == nextActive?.id -> c.accent
                            else -> c.fill
                        },
                        label = (job.routeSequence ?: (i + 1)).toString(),
                    )
                },
                startLabel = "Depot",
                endLabel = "${ordered.count { !JobStatusGate.isActive(it.status) }} of ${ordered.size} done",
            )
        }

        SlopesSectionHeader("Stop order", action = if (hasActive) "Optimise" else null, onAction = vm::optimizeStops)
        SlopesGroup {
            if (ordered.isEmpty()) {
                SlopesRow(
                    title = "No stops yet",
                    subtitle = "Assigned deliveries appear here in driving order.",
                    leading = { SlopesIconBadge(Icons.Filled.Route) },
                    divider = false,
                )
            }
            ordered.forEachIndexed { i, job ->
                val meters = state.optimizedStops.firstOrNull { it.deliveryJobId == job.id }?.distanceM
                SlopesRow(
                    title = job.documentNumber ?: job.id.take(8),
                    subtitle = listOfNotNull(
                        meters?.let { "%.1f km from previous".format(it / 1000) },
                        job.dropoffAddressText,
                    ).joinToString(" · ").ifBlank { null },
                    leading = { SlopesLeadingCount("#${job.routeSequence ?: i + 1}", "stop") },
                    trailing = { StatusPill(job.status) },
                    divider = i < ordered.lastIndex,
                    onClick = { vm.selectJob(job.id) },
                )
            }
        }
    }
}

private fun orderedStops(state: JobsUiState): List<DeliveryJobSummary> {
    if (state.optimizedStops.isEmpty()) return state.jobs.sortedBy { it.routeSequence ?: Int.MAX_VALUE }
    val rank = state.optimizedStops.associate { it.deliveryJobId to it.routeSequence }
    return state.jobs.sortedWith(compareBy({ rank[it.id] ?: Int.MAX_VALUE }, { it.routeSequence ?: Int.MAX_VALUE }))
}

// =============================================================================================
// Account — profile, status, appearance, shift actions and safety.
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
    Column(
        modifier
            .fillMaxSize()
            .background(c.background)
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(top = 12.dp, bottom = 28.dp),
    ) {
        SlopesLargeTitle(title = "Account")
        SlopesGroup {
            SlopesRow(
                title = signedInEmail ?: "Driver",
                subtitle = "Driver · Nissan GTR Auto",
                leading = {
                    Box(
                        Modifier.size(48.dp).clip(CircleShape).background(c.accentTint),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            initials(signedInEmail),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = c.accent,
                        )
                    }
                },
                divider = false,
            )
        }
        Spacer(Modifier.height(16.dp))
        SlopesStatRow(
            listOf(
                SlopesStat(state.jobs.count { JobStatusGate.isActive(it.status) }.toString(), "to deliver", unit = "stops", icon = Icons.Filled.Place),
                SlopesStat(state.jobs.count { it.status == "completed" }.toString(), "delivered", icon = Icons.Filled.CheckCircle),
                SlopesStat(state.jobs.count { it.status == "failed" }.toString(), "failed", icon = Icons.Filled.Cancel),
            ),
        )

        SlopesSectionHeader("Status")
        PresenceControl(state = state, vm = vm, trackingVm = trackingVm)

        SlopesSectionHeader("Appearance")
        SlopesSegmented(
            options = SlopesMode.entries.map { it.label },
            selected = SlopesMode.entries.indexOf(appearance),
            onSelect = { onAppearanceChange(SlopesMode.entries[it]) },
        )

        SlopesSectionHeader("Shift")
        SlopesGroup {
            SlopesRow("Refresh my jobs", leading = { SlopesIconBadge(Icons.Filled.Refresh) }, onClick = vm::refresh)
            SlopesRow(
                "Optimise today's route",
                leading = { SlopesIconBadge(Icons.Filled.Route) },
                onClick = vm::optimizeStops,
                divider = false,
            )
        }
        StatusBanners(state.message, state.error)

        SlopesSectionHeader("Safety")
        SlopesGroup {
            EmergencyRow(state.supportPhone, vm::raisePanic, divider = true)
            SlopesRow(
                title = "Call dispatch",
                subtitle = state.supportPhone.ifBlank { "Support number not set" },
                leading = { SlopesIconBadge(Icons.Filled.Phone) },
                onClick = vm::dialSupport,
                divider = false,
            )
        }

        if (onSignOut != null) {
            Spacer(Modifier.height(24.dp))
            SlopesGroup {
                SlopesRow(
                    title = "Sign out",
                    titleColor = c.danger,
                    leading = { SlopesIconBadge(Icons.AutoMirrored.Filled.Logout, tint = c.danger, container = c.dangerTint) },
                    onClick = onSignOut,
                    chevron = false,
                    divider = false,
                )
            }
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
}

/** Red emergency row, the way Slopes shows "Emergency – Call Ski Patrol". */
@Composable
private fun EmergencyRow(supportPhone: String, onPanic: () -> Unit, divider: Boolean) {
    val c = Slopes.colors
    SlopesRow(
        title = "Emergency – alert dispatch",
        subtitle = supportPhone.ifBlank { "Sends your location and calls support" },
        titleColor = c.danger,
        leading = { SlopesIconBadge(Icons.Filled.Phone, tint = c.danger, container = c.dangerTint, round = true) },
        onClick = onPanic,
        divider = divider,
    )
}

// =============================================================================================
// Stop detail — map + route, then Overview / Proof / Issue (Slopes' Overview / Analyze / Vitals).
// =============================================================================================

private enum class DetailTab(val label: String) { Overview("Overview"), Proof("Proof"), Issue("Issue") }

/**
 * Stop detail: map with the driving route, actions (Navigate / Arrived / Complete / Issue),
 * trip numbers, then Overview (tracking, receipt, drop-off), Proof (photo, signature, code)
 * and Issue (failure reasons, reattempt, emergency). Done and failed stops stay read-only.
 */
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
    initialTab: Int = 0,
    proofContent: (@Composable () -> Unit)? = null,
) {
    val c = Slopes.colors
    val active = JobStatusGate.isActive(job.status)
    var tab by remember(job.id) { mutableIntStateOf(if (active) initialTab else 0) }
    var expandRequests by remember(job.id) { mutableIntStateOf(0) }
    fun openTab(t: DetailTab) {
        tab = t.ordinal
        expandRequests++
    }
    val dest = job.dropoffPosition()
    val driver = tracking.driverPosition()
    val otherStops = remember(state.jobs, job.id) {
        state.jobs
            .filter { it.id != job.id && JobStatusGate.isActive(it.status) }
            .mapNotNull { it.toMapStop() }
    }
    val tabs = if (active) DetailTab.entries else listOf(DetailTab.Overview)
    val trackingThis = tracking.tracking && tracking.trackingJobId == job.id

    LaunchedEffect(job.id, tracking.lastLat, tracking.lastLng) {
        vm.refreshRouteGuidance(tracking.lastLat, tracking.lastLng)
    }

    SlopesMapSheetLayout(
        modifier = modifier,
        collapsedFraction = 0.6f,
        startExpanded = active && initialTab != 0,
        expandRequests = expandRequests,
        map = { obscured ->
            DeliveryMap(
                DeliveryMapSpec(
                    destination = dest,
                    driver = driver,
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
                SlopesMapControlButton(
                    Icons.Filled.MyLocation,
                    "Distance to drop-off",
                    vm::checkGeofence,
                    divider = true,
                    enabled = active && !state.busy,
                )
            }
        },
        sheetHeader = {
            SlopesLargeTitle(
                title = job.documentNumber ?: "Job ${job.id.take(8)}",
                subtitle = job.dropoffAddressText?.takeIf { it.isNotBlank() }
                    ?: job.routeSequence?.let { "Stop #$it" },
            ) {
                StatusPill(job.status)
                SlopesRoundButton(Icons.Filled.Close, "Close", onClick = onBack, tinted = false)
            }
        },
    ) {
        SlopesActionTiles(
            listOf(
                SlopesAction("Navigate", Icons.Filled.Navigation, vm::openNavigation, primary = true, enabled = dest != null),
                SlopesAction("Arrived", Icons.Filled.Flag, vm::checkGeofence, enabled = active && !state.busy),
                SlopesAction(
                    "Complete",
                    Icons.Filled.Draw,
                    { openTab(DetailTab.Proof) },
                    enabled = active && JobStatusGate.canOpenCompleteFlow(job),
                ),
                SlopesAction("Issue", Icons.Filled.ReportProblem, { openTab(DetailTab.Issue) }, enabled = active),
            ),
        )
        Spacer(Modifier.height(16.dp))
        val cod = job.settlement?.displayAmountDue()?.takeIf { it > 0.0 }
        SlopesStatRow(
            listOf(
                SlopesStat(
                    value = state.routeDistanceMeters?.let { if (it >= 1000) "%.1f".format(it / 1000.0) else it.toString() } ?: "—",
                    unit = state.routeDistanceMeters?.let { if (it >= 1000) "km" else "m" },
                    label = if (state.routeEtaSource == RouteEtaSource.STRAIGHT_LINE) "straight line" else "to drive",
                    icon = Icons.Filled.Straighten,
                ),
                SlopesStat(
                    value = state.routeDurationSeconds?.let { formatMinutes(it) } ?: "—",
                    unit = state.routeDurationSeconds?.let { if (it >= 3600) null else "min" },
                    label = if (state.routeBusy) "routing…" else "drive time",
                    icon = Icons.Filled.Schedule,
                ),
                SlopesStat(job.routeSequence?.let { "#$it" } ?: "—", "stop", icon = Icons.Filled.Place),
                SlopesStat(
                    value = cod?.let { "%.2f".format(it) } ?: "Paid",
                    unit = cod?.let { job.settlement!!.currency.rpcValue },
                    label = if (cod != null) "to collect" else "nothing due",
                    icon = Icons.Filled.Payments,
                    tint = if (cod != null) c.accent else null,
                ),
            ),
        )
        if (!active) {
            Spacer(Modifier.height(14.dp))
            if (job.status == "completed") {
                SlopesBanner("Delivered — proof of delivery is on file.", tone = SlopesTone.Success, icon = Icons.Filled.CheckCircle)
            } else {
                SlopesBanner(
                    "Failed" + (job.failureReasonCode?.let { " · ${failureLabel(it)}" } ?: "") + ". This stop is read-only.",
                    tone = SlopesTone.Danger,
                    icon = Icons.Filled.Cancel,
                )
            }
        }
        StatusBanners(state.message, state.error ?: tracking.error)

        if (tabs.size > 1) {
            Spacer(Modifier.height(18.dp))
            SlopesSegmented(tabs.map { it.label }, tab, { tab = it })
        }

        when (tabs.getOrElse(tab) { DetailTab.Overview }) {
            DetailTab.Overview -> OverviewTab(
                job = job,
                state = state,
                tracking = tracking,
                trackingThis = trackingThis,
                vm = vm,
                trackingVm = trackingVm,
                onOpenProof = { openTab(DetailTab.Proof) },
            )
            DetailTab.Proof -> {
                Spacer(Modifier.height(4.dp))
                if (proofContent != null) {
                    proofContent()
                } else {
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
            }
            DetailTab.Issue -> IssueTab(job, state, vm)
        }
    }
}

@Composable
private fun OverviewTab(
    job: DeliveryJobSummary,
    state: JobsUiState,
    tracking: TrackingUiState,
    trackingThis: Boolean,
    vm: JobsViewModel,
    trackingVm: TrackingViewModel,
    onOpenProof: () -> Unit,
) {
    val c = Slopes.colors
    if (JobStatusGate.isActive(job.status)) {
        SlopesSectionHeader("Live tracking")
        SlopesGroup {
            SlopesToggleRow(
                title = "Share live location",
                subtitle = if (trackingThis) {
                    "${tracking.ingestCount} sent · ${tracking.queuedCount} queued"
                } else {
                    "Dispatch sees you on the map"
                },
                checked = trackingThis,
                onCheckedChange = { on ->
                    if (on) {
                        trackingVm.startTracking(job.id)
                        if (state.presence != DriverPresenceStatus.ON_DUTY) vm.setPresence(DriverPresenceStatus.ON_DUTY)
                    } else {
                        trackingVm.stopTracking()
                    }
                },
                leading = { SlopesIconBadge(Icons.Filled.GpsFixed, tint = c.success, container = c.success.copy(alpha = 0.14f)) },
            )
            val g = state.geofence
            SlopesRow(
                title = "Distance to drop-off",
                subtitle = g?.distanceM?.let { "%.0f m away".format(it) } ?: "Check how close you are",
                leading = { SlopesIconBadge(Icons.Filled.MyLocation) },
                onClick = vm::checkGeofence,
                divider = g != null && (g.suggestArrive || g.suggestComplete),
            )
            if (g?.suggestArrive == true) {
                SlopesRow(
                    title = "Confirm arrival",
                    subtitle = "You are close to the drop-off",
                    leading = { SlopesIconBadge(Icons.Filled.Flag, tint = c.success, container = c.success.copy(alpha = 0.14f)) },
                    onClick = vm::markArrived,
                    divider = g.suggestComplete,
                )
            }
            if (g?.suggestComplete == true) {
                SlopesRow(
                    title = "Complete this delivery",
                    subtitle = "Photo, signature and the customer's code",
                    titleColor = c.accent,
                    leading = { SlopesIconBadge(Icons.Filled.Draw) },
                    onClick = {
                        vm.acknowledgeCompleteSuggestion()
                        onOpenProof()
                    },
                    divider = false,
                )
            }
        }
    }

    SlopesSectionHeader("Receipt")
    val header = remember(job) { JobStatusGate.receiptHeaderLines(job) }
    val notes = remember(job) { JobStatusGate.receiptNotes(job) }
    SlopesGroup {
        if (job.lineItems.isEmpty()) {
            SlopesRow(
                title = "No line items on the delivery note",
                leading = { SlopesIconBadge(Icons.Filled.Inventory2) },
            )
        }
        job.lineItems.forEach { line ->
            val row = line.formatReceiptRow()
            val amount = row.substringAfterLast(" · ", "")
            val described = line.description?.takeIf { it.isNotBlank() }
            SlopesRow(
                title = described ?: line.oemPartNumber ?: "Line item",
                subtitle = listOfNotNull(
                    "Qty ${row.substringBefore(" × ")}",
                    line.oemPartNumber?.takeIf { it.isNotBlank() && described != null },
                    if (line.isCoreCharge) "core charge" else null,
                ).joinToString(" · "),
                leading = { SlopesIconBadge(Icons.Filled.Inventory2) },
                trailing = {
                    if (amount.isNotBlank()) {
                        Text(
                            amount,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = c.label,
                        )
                    }
                },
            )
        }
        header.forEachIndexed { i, line ->
            SlopesRow(
                title = line,
                leading = { SlopesIconBadge(Icons.Filled.Description, tint = c.secondaryLabel, container = c.fill) },
                divider = i < header.lastIndex || notes != null,
            )
        }
        notes?.let {
            SlopesRow(
                title = it,
                subtitle = "Note from dispatch",
                leading = { SlopesIconBadge(Icons.Filled.Description, tint = c.warning, container = c.warning.copy(alpha = 0.14f)) },
                divider = false,
            )
        }
    }

    SlopesSectionHeader("Drop-off")
    SlopesGroup {
        val lines = JobStatusGate.deliveryAddressLines(job)
        SlopesRow(
            title = lines.first(),
            subtitle = lines.drop(1).joinToString(" · ").ifBlank { null },
            leading = { SlopesIconBadge(Icons.Filled.Place, tint = c.route, container = c.route.copy(alpha = 0.12f)) },
            onClick = if (job.dropoffPosition() != null) vm::openNavigation else null,
            divider = job.etaAt != null,
        )
        job.etaAt?.let { eta ->
            SlopesRow(
                title = "ETA ${formatEta(eta)}",
                subtitle = job.etaSeconds?.let { "About ${formatMinutes(it)} min from dispatch" },
                leading = { SlopesIconBadge(Icons.Filled.Schedule) },
                divider = false,
            )
        }
    }
}

@Composable
private fun IssueTab(
    job: DeliveryJobSummary,
    state: JobsUiState,
    vm: JobsViewModel,
) {
    SlopesSectionHeader("What happened?")
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
    Spacer(Modifier.height(16.dp))
    SlopesTextField(
        value = state.failNotes,
        onValueChange = vm::onFailNotes,
        label = "Notes for dispatch",
        placeholder = "What did you find at the drop-off?",
        singleLine = false,
        modifier = Modifier.padding(horizontal = 18.dp),
    )
    Spacer(Modifier.height(16.dp))
    SlopesGroup {
        SlopesToggleRow(
            title = "Book a reattempt",
            subtitle = "Creates a new delivery for this note",
            checked = state.createReattempt,
            onCheckedChange = vm::onCreateReattempt,
            divider = false,
        )
    }
    Spacer(Modifier.height(16.dp))
    SlopesDestructiveButton(
        label = "Mark as failed",
        onClick = vm::failSelectedJob,
        enabled = !state.busy && JobStatusGate.canMarkFailed(job),
        icon = Icons.Filled.Cancel,
        modifier = Modifier.padding(horizontal = 18.dp),
    )
    SlopesSectionHeader("Emergency")
    SlopesGroup {
        EmergencyRow(state.supportPhone, vm::raisePanic, divider = false)
    }
}

@Composable
private fun StatusPill(status: String) {
    val c = Slopes.colors
    when (status) {
        "dispatched" -> SlopesPill("On the way", c.accent)
        "pending" -> SlopesPill("Pending", c.warning)
        "completed" -> SlopesPill("Delivered", c.success)
        "failed" -> SlopesPill("Failed", c.danger)
        else -> SlopesPill(status.replaceFirstChar { it.uppercase() }, c.secondaryLabel)
    }
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
        factory = JobsViewModel.factory(
            rpc,
            gps,
            context,
            supportPhone,
            routingBaseUrl = routingBaseUrl,
            mapStyleUrl = mapStyleUrl,
        ),
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
