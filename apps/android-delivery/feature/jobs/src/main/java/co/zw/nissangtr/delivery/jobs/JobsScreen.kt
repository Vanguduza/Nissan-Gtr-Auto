package co.zw.nissangtr.delivery.jobs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import co.zw.nissangtr.bridges.location.GpsBridge
import co.zw.nissangtr.bridges.maps.DeliveryRouteMap
import co.zw.nissangtr.bridges.maps.MapLatLng
import co.zw.nissangtr.bridges.maps.MapStop
import co.zw.nissangtr.bridges.podcamera.PodCameraBridge
import co.zw.nissangtr.bridges.podsignature.PodSignatureBridge
import co.zw.nissangtr.delivery.pod.PodSection
import co.zw.nissangtr.delivery.rpc.DeliveryFailureReason
import co.zw.nissangtr.delivery.rpc.DeliveryJobSummary
import co.zw.nissangtr.delivery.rpc.DriverPresenceStatus
import co.zw.nissangtr.delivery.rpc.RpcClient
import co.zw.nissangtr.delivery.rpc.formatAmountDueLabel
import co.zw.nissangtr.delivery.tracking.MapLibreJobMap
import co.zw.nissangtr.delivery.tracking.TrackingUiState
import co.zw.nissangtr.delivery.tracking.TrackingViewModel
import co.zw.nissangtr.ui.shop.ShopCircleIconButton
import co.zw.nissangtr.ui.shop.ShopDangerButton
import co.zw.nissangtr.ui.shop.ShopDefaultScreen
import co.zw.nissangtr.ui.shop.ShopHonestEmpty
import co.zw.nissangtr.ui.shop.ShopLocationRow
import co.zw.nissangtr.ui.shop.ShopMerchTitleRow
import co.zw.nissangtr.ui.shop.ShopOrderBox
import co.zw.nissangtr.ui.shop.ShopOutlinedActionRow
import co.zw.nissangtr.ui.shop.ShopPresenceBanner
import co.zw.nissangtr.ui.shop.ShopPrimaryButton
import co.zw.nissangtr.ui.shop.ShopProceedButtonBox
import co.zw.nissangtr.ui.shop.ShopProfileAvatar
import co.zw.nissangtr.ui.shop.ShopProfileItemBox
import co.zw.nissangtr.ui.shop.ShopSecondaryButton
import co.zw.nissangtr.ui.shop.ShopSectionHeader
import co.zw.nissangtr.ui.shop.ShopStatusChip
import co.zw.nissangtr.ui.shop.ShopTabBody
import co.zw.nissangtr.ui.theme.GtrColors
import co.zw.nissangtr.ui.theme.LocalGtrExtras

private enum class JobsFilterTab(val label: String) {
    Active("Active"),
    Done("Done"),
    Failed("Failed"),
}

/**
 * Jobs tab — Shopping-By-KMP [MyOrdersScreen] IA: status TabRow + bordered OrderBox cards.
 * Full ShopKit density (Standard), not staff compact / ShopStaff*.
 */
@Composable
fun JobsListScreen(
    state: JobsUiState,
    vm: JobsViewModel,
    trackingVm: TrackingViewModel,
    shellSubtitle: String?,
    modifier: Modifier = Modifier,
) {
    var filterTab by remember { mutableIntStateOf(0) }
    val extras = LocalGtrExtras.current
    val distanceByJobId = remember(state.optimizedStops) {
        state.optimizedStops.associate { it.deliveryJobId to it.distanceM }
    }
    val filtered = remember(state.jobs, filterTab) {
        when (JobsFilterTab.entries[filterTab]) {
            JobsFilterTab.Active -> state.jobs.filter { JobStatusGate.isActive(it.status) }
            JobsFilterTab.Done -> state.jobs.filter { it.status == "completed" }
            JobsFilterTab.Failed -> state.jobs.filter { it.status == "failed" }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = extras.screenPadding, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("My jobs", style = MaterialTheme.typography.titleLarge)
                if (shellSubtitle != null) {
                    Text(
                        shellSubtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            ShopCircleIconButton(
                imageVector = Icons.Filled.Refresh,
                onClick = vm::refresh,
                contentDescription = "Refresh",
            )
        }

        ShopPresenceBanner(
            label = state.presence.displayLabel(),
            detail = "",
            accent = state.presence.statusColor(),
            modifier = Modifier.padding(horizontal = extras.screenPadding),
        )

        Spacer(modifier = Modifier.height(extras.sectionGap))

        // KMP MyOrders status tabs — underline indicator, chalk body.
        TabRow(
            selectedTabIndex = filterTab,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            containerColor = Color.Transparent,
            contentColor = Color.Transparent,
            divider = {},
            indicator = { positions ->
                Box(
                    modifier = Modifier
                        .tabIndicatorOffset(positions[filterTab])
                        .height(4.dp)
                        .padding(horizontal = 28.dp)
                        .background(MaterialTheme.colorScheme.primary, MaterialTheme.shapes.medium),
                )
            },
        ) {
            JobsFilterTab.entries.forEachIndexed { index, tab ->
                Tab(
                    selected = filterTab == index,
                    onClick = { filterTab = index },
                    selectedContentColor = Color.Transparent,
                    unselectedContentColor = Color.Transparent,
                    text = {
                        Text(
                            tab.label,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (filterTab == index) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onBackground
                            },
                        )
                    },
                )
            }
        }

        state.message?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = extras.screenPadding, vertical = 4.dp),
            )
        }
        state.error?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = extras.screenPadding, vertical = 4.dp),
            )
        }

        if (filtered.isEmpty()) {
            ShopTabBody(scrollable = false) {
                ShopHonestEmpty(
                    title = "Nothing yet",
                    body = when (JobsFilterTab.entries[filterTab]) {
                        JobsFilterTab.Active -> "No active deliveries. Pull refresh or go On duty."
                        JobsFilterTab.Done -> "Completed stops will show here."
                        JobsFilterTab.Failed -> "Failed deliveries will show here."
                    },
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(extras.screenPadding),
                verticalArrangement = Arrangement.spacedBy(extras.sectionGap),
            ) {
                items(filtered, key = { it.id }) { job ->
                    JobOrderCard(
                        job = job,
                        distanceM = distanceByJobId[job.id],
                        onOpen = { vm.selectJob(job.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun JobOrderCard(
    job: DeliveryJobSummary,
    distanceM: Double?,
    onOpen: () -> Unit,
) {
    val title = job.documentNumber ?: "Job ${job.id.take(8)}"
    val subtitle = buildString {
        job.dropoffAddressText?.takeIf { it.isNotBlank() }?.let { append(it) }
        job.etaAt?.let {
            if (isNotEmpty()) append(" · ")
            append("ETA $it")
        }
        if (distanceM != null) {
            if (isNotEmpty()) append(" · ")
            append("%.1f km".format(distanceM / 1000))
        }
        if (isEmpty()) {
            if (job.dropoffLat != null && job.dropoffLng != null) {
                append("%.4f, %.4f".format(job.dropoffLat, job.dropoffLng))
            } else {
                append("Tap for receipt + address")
            }
        }
    }
    // Whole card opens job detail — no local expand that swallowed the expected navigation.
    ShopOrderBox(
        title = title,
        subtitle = subtitle,
        onClick = onOpen,
        metaLabel = "Stop",
        metaValue = job.routeSequence?.let { "#$it" } ?: "—",
        thumbLabel = job.routeSequence?.toString() ?: "–",
        badges = {
            StatusChip(status = job.status)
            if (job.reattemptOf != null) {
                ShopStatusChip(label = "REATTEMPT", background = GtrColors.Warning)
            }
            job.settlement?.formatAmountDueLabel()?.let { cod ->
                ShopStatusChip(label = cod, background = GtrColors.Warning)
            }
        },
    )
}

@Composable
fun DeliveryRouteTab(
    state: JobsUiState,
    tracking: TrackingUiState,
    vm: JobsViewModel,
    trackingVm: TrackingViewModel,
    modifier: Modifier = Modifier,
) {
    val routeStops = remember(state.jobs) {
        state.jobs
            .filter { it.dropoffLat != null && it.dropoffLng != null }
            .sortedBy { it.routeSequence ?: Int.MAX_VALUE }
            .map {
                MapStop(
                    id = it.id,
                    label = it.documentNumber ?: it.id.take(8),
                    position = MapLatLng(it.dropoffLat!!, it.dropoffLng!!),
                    sequence = it.routeSequence,
                )
            }
    }
    val driverPos = if (tracking.lastLat != null && tracking.lastLng != null) {
        MapLatLng(tracking.lastLat!!, tracking.lastLng!!)
    } else {
        null
    }
    val mapCenter = driverPos
        ?: routeStops.firstOrNull()?.position
        ?: MapLatLng(-17.8292, 31.0522)

    ShopTabBody(modifier = modifier, scrollable = true) {
        Text("Today's route", style = MaterialTheme.typography.titleLarge)
        ShopLocationRow(
            label = "Driver position",
            locationText = tracking.lastLatLng ?: "GPS off",
            onClick = {},
        )
        ShopPresenceBanner(
            label = state.presence.displayLabel(),
            detail = if (tracking.tracking) {
                "GPS on · ${tracking.ingestCount} pings · queue ${tracking.queuedCount}"
            } else {
                "GPS idle"
            },
            accent = state.presence.statusColor(),
        )

        ShopSectionHeader(title = "Live map", actionLabel = null)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp)
                .clip(MaterialTheme.shapes.medium)
                .border(1.dp, GtrColors.Mist, MaterialTheme.shapes.medium),
        ) {
            if (state.mapLibreEnabled) {
                MapLibreJobMap(
                    latitude = mapCenter.latitude,
                    longitude = mapCenter.longitude,
                    zoom = 12.0,
                    styleUrl = state.mapLibreStyleUrl,
                    stops = routeStops,
                    driver = driverPos,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                DeliveryRouteMap(
                    destination = routeStops.firstOrNull()?.position,
                    driver = driverPos,
                    routePoints = emptyList(),
                    otherStops = routeStops.drop(1),
                    mapsKeyPresent = state.mapsKeyPresent,
                    myLocationEnabled = tracking.tracking,
                )
            }
        }
        if (!tracking.tracking) {
            ShopPrimaryButton(
                label = "Start live GPS",
                onClick = {
                    val active = state.jobs.firstOrNull { JobStatusGate.isActive(it.status) }
                    if (active != null) {
                        trackingVm.startTracking(active.id)
                        if (state.presence != DriverPresenceStatus.ON_DUTY) {
                            vm.setPresence(DriverPresenceStatus.ON_DUTY)
                        }
                    }
                },
                enabled = state.jobs.any { JobStatusGate.isActive(it.status) },
            )
        }

        ShopOutlinedActionRow {
            ShopSecondaryButton(
                label = "Refresh jobs",
                onClick = vm::refresh,
                enabled = !state.busy,
                modifier = Modifier.weight(1f),
            )
            ShopPrimaryButton(
                label = "Optimize stops",
                onClick = vm::optimizeStops,
                enabled = !state.busy,
                modifier = Modifier.weight(1f),
            )
        }
        ShopMerchTitleRow(title = "Stop order", actionLabel = null)
        if (state.optimizedStops.isEmpty()) {
            ShopHonestEmpty(
                title = "No optimized order yet",
                body = "Tap Optimize stops to sequence today's active deliveries.",
            )
        } else {
            state.optimizedStops.forEach { stop ->
                val job = state.jobs.find { it.id == stop.deliveryJobId }
                ShopOrderBox(
                    title = job?.documentNumber ?: stop.deliveryJobId.take(8),
                    subtitle = stop.distanceM?.let { "%.1f km from previous".format(it / 1000) }
                        ?: "Sequence #${stop.routeSequence}",
                    onClick = { vm.selectJob(stop.deliveryJobId) },
                    metaLabel = "Seq",
                    metaValue = "#${stop.routeSequence}",
                    thumbLabel = stop.routeSequence.toString(),
                    badges = {
                        job?.let { StatusChip(status = it.status) }
                    },
                )
            }
        }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        state.message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    }
}

/**
 * Me tab — Shopping-By-KMP ProfileScreen IA: avatar + ProfileItemBox rows.
 */
@Composable
fun DeliveryMeTab(
    state: JobsUiState,
    vm: JobsViewModel,
    trackingVm: TrackingViewModel,
    signedInEmail: String?,
    onSignOut: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    ShopTabBody(modifier = modifier, scrollable = true) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Driver", style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(16.dp))
            ShopProfileAvatar(initials = (signedInEmail ?: "DR").take(2))
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                signedInEmail ?: "Guest driver",
                style = MaterialTheme.typography.headlineSmall,
            )
        }

        ShopSectionHeader(title = "Presence", actionLabel = null)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            DriverPresenceStatus.entries.forEach { status ->
                FilterChip(
                    selected = state.presence == status,
                    onClick = {
                        vm.setPresence(status)
                        when (status) {
                            DriverPresenceStatus.ON_DUTY -> {
                                val active = state.jobs.firstOrNull {
                                    JobStatusGate.isActive(it.status)
                                }
                                if (active != null) trackingVm.startTracking(active.id)
                            }
                            DriverPresenceStatus.BREAK,
                            DriverPresenceStatus.OFFLINE,
                            -> trackingVm.stopTracking()
                            else -> Unit
                        }
                    },
                    enabled = !state.busy,
                    label = {
                        Text(status.displayLabel(), style = MaterialTheme.typography.labelSmall)
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        Column(modifier = Modifier.fillMaxWidth()) {
            ShopProfileItemBox(
                title = "Refresh my jobs",
                icon = Icons.Filled.LocalShipping,
                onClick = vm::refresh,
            )
            ShopProfileItemBox(
                title = "Optimize today's route",
                icon = Icons.Filled.Map,
                onClick = vm::optimizeStops,
            )
            ShopProfileItemBox(
                title = "PANIC — alert dispatch",
                icon = Icons.Filled.Warning,
                onClick = vm::raisePanic,
                isLastItem = onSignOut == null,
            )
            if (onSignOut != null) {
                ShopProfileItemBox(
                    title = "Sign out",
                    icon = Icons.Filled.AccountCircle,
                    onClick = onSignOut,
                    isLastItem = true,
                )
            }
        }
        state.message?.let {
            Text(it, style = MaterialTheme.typography.bodySmall)
        }
        state.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
        }
    }
}

/**
 * Stop detail — receipt copy + delivery address; Complete → signature POD; Failed → ERP.
 * Done/failed stay readable (receipt + address) without complete actions.
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
) {
    val active = JobStatusGate.isActive(job.status)
    var completeOpen by remember(job.id) { mutableStateOf(false) }
    val dest = if (job.dropoffLat != null && job.dropoffLng != null) {
        MapLatLng(job.dropoffLat!!, job.dropoffLng!!)
    } else {
        null
    }
    val driverPos = if (tracking.lastLat != null && tracking.lastLng != null) {
        MapLatLng(tracking.lastLat!!, tracking.lastLng!!)
    } else {
        null
    }
    val otherStops = remember(state.jobs, job.id) {
        state.jobs
            .filter {
                it.id != job.id &&
                    JobStatusGate.isActive(it.status) &&
                    it.dropoffLat != null &&
                    it.dropoffLng != null
            }
            .map {
                MapStop(
                    id = it.id,
                    label = it.documentNumber ?: it.id.take(8),
                    position = MapLatLng(it.dropoffLat!!, it.dropoffLng!!),
                    sequence = it.routeSequence,
                )
            }
    }
    val detailStops = remember(job, otherStops) {
        buildList {
            if (dest != null) {
                add(
                    MapStop(
                        id = job.id,
                        label = job.documentNumber ?: job.id.take(8),
                        position = dest,
                        sequence = job.routeSequence,
                    ),
                )
            }
            addAll(otherStops)
        }
    }
    val receiptHeader = remember(job) { JobStatusGate.receiptHeaderLines(job) }
    val receiptItems = remember(job) { JobStatusGate.receiptItemLines(job) }
    val receiptNotes = remember(job) { JobStatusGate.receiptNotes(job) }
    val addressLines = remember(job) { JobStatusGate.deliveryAddressLines(job) }

    LaunchedEffect(job.id, tracking.lastLat, tracking.lastLng, state.mapsKeyPresent) {
        vm.refreshRouteGuidance(tracking.lastLat, tracking.lastLng)
    }

    ShopDefaultScreen(
        title = job.documentNumber ?: job.id.take(8),
        subtitle = job.routeSequence?.let { "Stop #$it" },
        onBack = onBack,
        scrollable = true,
        modifier = modifier,
        bottomBar = {
            if (dest != null) {
                ShopProceedButtonBox(
                    totalLabel = state.routeLabel ?: "Navigate",
                    ctaLabel = "Turn-by-turn",
                    onClick = vm::openNavigation,
                )
            }
        },
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatusChip(status = job.status)
            if (job.reattemptOf != null) {
                ShopStatusChip(label = "REATTEMPT", background = GtrColors.Warning)
            }
            job.settlement?.formatAmountDueLabel()?.let { cod ->
                ShopStatusChip(label = cod, background = GtrColors.Warning)
            }
        }

        ShopSectionHeader(title = "Receipt copy", actionLabel = null)
        ReceiptBanner(
            title = "Document",
            lines = receiptHeader,
            background = GtrColors.Mist,
        )
        Spacer(modifier = Modifier.height(8.dp))
        ReceiptBanner(
            title = "Items bought",
            lines = receiptItems,
            background = GtrColors.Mist,
        )
        receiptNotes?.let { notes ->
            Spacer(modifier = Modifier.height(8.dp))
            ReceiptBanner(
                title = "Notes",
                lines = listOf(notes),
                background = GtrColors.White,
            )
        }

        ShopSectionHeader(title = "Delivery address", actionLabel = null)
        addressLines.forEach { line ->
            Text(line, style = MaterialTheme.typography.bodyMedium)
        }
        job.etaAt?.let {
            Text(
                "ETA $it (${job.etaSeconds ?: "?"}s)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        ShopSectionHeader(title = "Live map", actionLabel = null)
        val mapLat = tracking.lastLat ?: job.dropoffLat
        val mapLng = tracking.lastLng ?: job.dropoffLng
        val hasCoords = mapLat != null && mapLng != null
        val showMapLibre = state.mapLibreEnabled && hasCoords
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp)
                .clip(MaterialTheme.shapes.medium)
                .border(1.dp, GtrColors.Mist, MaterialTheme.shapes.medium),
        ) {
            if (showMapLibre) {
                MapLibreJobMap(
                    latitude = mapLat!!,
                    longitude = mapLng!!,
                    zoom = 14.0,
                    styleUrl = state.mapLibreStyleUrl,
                    stops = detailStops,
                    driver = driverPos,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                DeliveryRouteMap(
                    destination = dest,
                    driver = driverPos,
                    routePoints = state.routePoints,
                    otherStops = otherStops,
                    mapsKeyPresent = state.mapsKeyPresent,
                    myLocationEnabled = tracking.tracking,
                )
            }
        }
        state.routeLabel?.let {
            Text(
                if (state.routeBusy) "Routing…" else it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        ShopSecondaryButton(
            label = "Refresh route",
            onClick = { vm.refreshRouteGuidance(tracking.lastLat, tracking.lastLng) },
            enabled = !state.routeBusy,
        )

        if (active) {
            ShopSectionHeader(title = "GPS tracking", actionLabel = null)
            if (tracking.tracking && tracking.trackingJobId == job.id) {
                Text(
                    "GPS on · ${tracking.ingestCount} pings",
                    style = MaterialTheme.typography.bodySmall,
                )
                tracking.lastLatLng?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall)
                }
                ShopSecondaryButton(label = "Stop GPS", onClick = trackingVm::stopTracking)
            } else {
                ShopPrimaryButton(
                    label = "Start GPS",
                    onClick = {
                        trackingVm.startTracking(job.id)
                        if (state.presence != DriverPresenceStatus.ON_DUTY) {
                            vm.setPresence(DriverPresenceStatus.ON_DUTY)
                        }
                    },
                )
            }

            ShopSecondaryButton(
                label = "Check geofence",
                onClick = vm::checkGeofence,
                enabled = !state.busy,
            )
            state.geofence?.let { g ->
                ShopSectionHeader(title = "Geofence", actionLabel = null)
                Text(
                    "Distance ${g.distanceM?.let { "%.0fm".format(it) } ?: "?"} — " +
                        "arrive=${g.suggestArrive}, complete=${g.suggestComplete}",
                    style = MaterialTheme.typography.bodySmall,
                )
                if (g.suggestArrive) {
                    ShopSecondaryButton(label = "Confirm arrive", onClick = vm::markArrived)
                }
                if (g.suggestComplete) {
                    ShopSecondaryButton(
                        label = "Acknowledge → Complete job",
                        onClick = {
                            vm.acknowledgeCompleteSuggestion()
                            completeOpen = true
                        },
                    )
                }
            }

            ShopSectionHeader(title = "Complete delivery", actionLabel = null)
            if (!completeOpen) {
                ShopPrimaryButton(
                    label = "Complete job",
                    onClick = { completeOpen = true },
                    enabled = JobStatusGate.canOpenCompleteFlow(job),
                )
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
                ShopSecondaryButton(
                    label = "Hide POD",
                    onClick = { completeOpen = false },
                )
            }

            ShopSectionHeader(title = "Mark failed", actionLabel = null)
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
                shape = MaterialTheme.shapes.small,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = state.createReattempt,
                    onCheckedChange = vm::onCreateReattempt,
                )
                Text("Create reattempt job")
            }
            ShopPrimaryButton(
                label = "Mark job Failed",
                onClick = vm::failSelectedJob,
                enabled = !state.busy && JobStatusGate.canMarkFailed(job),
            )
            ShopDangerButton(label = "PANIC", onClick = vm::raisePanic)
        } else {
            Text(
                JobStatusGate.TERMINAL_READONLY,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (job.failureReasonCode != null) {
                Text(
                    "Failure: ${job.failureReasonCode}",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        state.message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        tracking.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        tracking.message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
private fun ReceiptBanner(
    title: String,
    lines: List<String>,
    background: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, GtrColors.Mist, MaterialTheme.shapes.small),
        color = background,
        shape = MaterialTheme.shapes.small,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = GtrColors.Steel,
            )
            lines.forEach { line ->
                Text(line, style = MaterialTheme.typography.bodyMedium, color = GtrColors.Steel)
            }
        }
    }
}

@Composable
private fun StatusChip(status: String) {
    val (bg, label) = when (status) {
        "dispatched" -> GtrColors.Steel to "OUT"
        "pending" -> GtrColors.Warning to "PENDING"
        "completed" -> GtrColors.Accent to "DONE"
        "failed" -> GtrColors.Danger to "FAILED"
        else -> GtrColors.SilverDim to status.uppercase()
    }
    ShopStatusChip(label = label, background = bg)
}

private fun DriverPresenceStatus.displayLabel(): String = when (this) {
    DriverPresenceStatus.AVAILABLE -> "Available"
    DriverPresenceStatus.ON_DUTY -> "On duty"
    DriverPresenceStatus.BREAK -> "On break"
    DriverPresenceStatus.OFFLINE -> "Offline"
}

private fun DriverPresenceStatus.statusColor(): Color = when (this) {
    DriverPresenceStatus.AVAILABLE -> GtrColors.Accent
    DriverPresenceStatus.ON_DUTY -> GtrColors.Steel
    DriverPresenceStatus.BREAK -> GtrColors.Warning
    DriverPresenceStatus.OFFLINE -> GtrColors.SilverDim
}

@Composable
fun JobsScreen(
    rpc: RpcClient,
    gps: GpsBridge,
    camera: PodCameraBridge,
    signature: PodSignatureBridge,
    supportPhone: String,
    mapsApiKey: String,
    trackingVm: TrackingViewModel,
    shellTitle: String = "My jobs",
    shellSubtitle: String? = null,
    signedInEmail: String? = null,
    onSignOut: (() -> Unit)? = null,
    osrmUrl: String = "",
    useMapLibre: Boolean = true,
    mapLibreStyleUrl: String = "",
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val vm: JobsViewModel = viewModel(
        factory = JobsViewModel.factory(
            rpc,
            gps,
            context,
            supportPhone,
            mapsApiKey,
            osrmUrl = osrmUrl,
            useMapLibre = useMapLibre,
            mapLibreStyleUrl = mapLibreStyleUrl,
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
        JobsListScreen(
            state = state,
            vm = vm,
            trackingVm = trackingVm,
            shellSubtitle = shellSubtitle ?: shellTitle,
            modifier = modifier,
        )
    }
}

/** Optional map caption — kept blank (no scaffold / OSRM nags in UI). */
internal fun jobDetailMapCaption(
    state: JobsUiState,
    showingMapLibre: Boolean = state.mapLibreEnabled,
): String {
    // Prefer live route chip when present; never nag about missing OSRM_URL.
    return state.routeLabel.orEmpty()
}
