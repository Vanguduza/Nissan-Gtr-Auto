package co.zw.nissangtr.delivery.jobs

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import co.zw.nissangtr.bridges.location.GpsBridge
import co.zw.nissangtr.bridges.location.LocationPermissionStatus
import co.zw.nissangtr.bridges.maps.DirectionsRouteFetcher
import co.zw.nissangtr.bridges.maps.DrivingRoute
import co.zw.nissangtr.bridges.maps.ExternalNavigation
import co.zw.nissangtr.bridges.maps.MapLatLng
import co.zw.nissangtr.bridges.maps.RouteFetchResult
import co.zw.nissangtr.delivery.rpc.DeliveryFailureReason
import co.zw.nissangtr.delivery.rpc.DeliveryJobSummary
import co.zw.nissangtr.delivery.rpc.DriverPresenceStatus
import co.zw.nissangtr.delivery.rpc.GeofenceSuggestion
import co.zw.nissangtr.delivery.rpc.OptimizedStop
import co.zw.nissangtr.delivery.rpc.RpcClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Distance/ETA provider for in-app route guidance (D-44 / Epic B). */
enum class RouteEtaSource(val wire: String, val label: String) {
    OSRM("osrm", "Driving"),
    /** Haversine when the router is unreachable — no scary “unconfigured” UX. */
    STRAIGHT_LINE("straight_line", "Approx."),
}

data class JobsUiState(
    val jobs: List<DeliveryJobSummary> = emptyList(),
    val selectedJobId: String? = null,
    val presence: DriverPresenceStatus = DriverPresenceStatus.AVAILABLE,
    val optimizedStops: List<OptimizedStop> = emptyList(),
    val geofence: GeofenceSuggestion? = null,
    val failReason: DeliveryFailureReason = DeliveryFailureReason.CUSTOMER_ABSENT,
    val failNotes: String = "",
    val createReattempt: Boolean = true,
    val supportPhone: String = "",
    /**
     * Optional self-hosted MapLibre style (tileserver-gl, see infra/satellites/maptiles/).
     * Blank → the keyless maps-nav default (OpenFreeMap).
     */
    val mapStyleUrl: String = "",
    val routePoints: List<MapLatLng> = emptyList(),
    val routeLabel: String? = null,
    val routeEtaSource: RouteEtaSource? = null,
    val routeDistanceMeters: Int? = null,
    val routeDurationSeconds: Int? = null,
    val routeBusy: Boolean = false,
    val busy: Boolean = false,
    val message: String? = null,
    val error: String? = null,
)

/** Driver-facing presence label. */
fun DriverPresenceStatus.displayLabel(): String = when (this) {
    DriverPresenceStatus.AVAILABLE -> "Available"
    DriverPresenceStatus.ON_DUTY -> "On duty"
    DriverPresenceStatus.BREAK -> "On break"
    DriverPresenceStatus.OFFLINE -> "Offline"
}

/** Resolve job detail from list state — used by shell so Compose tracks [JobsUiState.selectedJobId]. */
fun resolveSelectedJob(state: JobsUiState): DeliveryJobSummary? {
    val id = state.selectedJobId ?: return null
    return state.jobs.find { it.id == id }
}

/** Pure label builder for route guidance — distance/ETA only (no scaffold nags). */
internal fun formatRouteGuidanceLabel(
    etaSource: RouteEtaSource,
    summary: String?,
    distanceMeters: Int?,
    durationSeconds: Int?,
): String {
    val dist = distanceMeters?.let { d ->
        if (d >= 1000) "%.1f km".format(d / 1000.0) else "${d}m"
    }
    val dur = durationSeconds?.let { s ->
        val m = s / 60
        if (m >= 60) "${m / 60}h ${m % 60}m" else "${m} min"
    }
    return listOfNotNull(
        etaSource.label.takeIf { it.isNotBlank() },
        summary?.takeIf { it.isNotBlank() && it != "OSRM" },
        dist,
        dur,
    ).joinToString(" · ").ifBlank { etaSource.label }
}

/** Straight-line fallback when OSRM/Google is unset or unreachable. */
internal fun straightLineRoute(origin: MapLatLng, destination: MapLatLng): DrivingRoute {
    val meters = haversineMeters(origin, destination)
    // ~30 km/h urban crawl estimate for a usable ETA chip.
    val seconds = ((meters / 8.33).toInt()).coerceAtLeast(60)
    return DrivingRoute(
        points = listOf(origin, destination),
        distanceMeters = meters.toInt(),
        durationSeconds = seconds,
        summary = null,
    )
}

internal fun haversineMeters(a: MapLatLng, b: MapLatLng): Double {
    val r = 6_371_000.0
    val dLat = Math.toRadians(b.latitude - a.latitude)
    val dLon = Math.toRadians(b.longitude - a.longitude)
    val lat1 = Math.toRadians(a.latitude)
    val lat2 = Math.toRadians(b.latitude)
    val h = Math.sin(dLat / 2).let { it * it } +
        Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLon / 2).let { it * it }
    return 2 * r * Math.asin(Math.sqrt(h))
}

class JobsViewModel(
    private val rpc: RpcClient,
    private val gps: GpsBridge,
    private val appContext: Context,
    supportPhone: String,
    private val routingBaseUrl: String = DirectionsRouteFetcher.DEFAULT_ROUTING_BASE_URL,
    mapStyleUrl: String = "",
) : ViewModel() {
    private val _state = MutableStateFlow(
        JobsUiState(
            supportPhone = supportPhone,
            mapStyleUrl = mapStyleUrl.trim(),
        ),
    )
    val state: StateFlow<JobsUiState> = _state.asStateFlow()

    private val router by lazy { DirectionsRouteFetcher(routingBaseUrl) }

    init {
        refresh()
        loadPresence()
    }

    fun selectedJob(): DeliveryJobSummary? = resolveSelectedJob(_state.value)

    fun selectJob(id: String?) = _state.update {
        it.copy(
            selectedJobId = id,
            geofence = null,
            routePoints = emptyList(),
            routeLabel = null,
            routeEtaSource = null,
            routeDistanceMeters = null,
            routeDurationSeconds = null,
            error = null,
            message = null,
        )
    }

    fun onFailReason(reason: DeliveryFailureReason) =
        _state.update { it.copy(failReason = reason) }

    fun onFailNotes(v: String) = _state.update { it.copy(failNotes = v) }

    fun onCreateReattempt(v: Boolean) = _state.update { it.copy(createReattempt = v) }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            try {
                val jobs = rpc.listMyDeliveryJobs()
                _state.update { it.copy(busy = false, jobs = jobs) }
            } catch (e: Exception) {
                _state.update {
                    it.copy(busy = false, error = e.message ?: "refresh failed")
                }
            }
        }
    }

    fun loadPresence() {
        viewModelScope.launch {
            try {
                val snap = rpc.getMyDriverPresence()
                if (snap != null) {
                    val status = DriverPresenceStatus.entries
                        .find { it.rpcValue == snap.status }
                        ?: DriverPresenceStatus.AVAILABLE
                    _state.update { it.copy(presence = status) }
                }
            } catch (_: Exception) {
                // presence row may not exist yet
            }
        }
    }

    fun setPresence(status: DriverPresenceStatus) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            try {
                var lat: Double? = null
                var lng: Double? = null
                runCatching {
                    val perm = gps.getLocationPermissionStatus()
                    if (perm == LocationPermissionStatus.GRANTED ||
                        perm == LocationPermissionStatus.APPROXIMATE
                    ) {
                        val coord = gps.getCurrentPosition()
                        lat = coord.latitude
                        lng = coord.longitude
                    }
                }
                rpc.setDriverPresence(status, lastLat = lat, lastLng = lng)
                _state.update {
                    it.copy(
                        busy = false,
                        presence = status,
                        message = "You are now ${status.displayLabel().lowercase()}",
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(busy = false, error = e.message ?: "presence update failed")
                }
            }
        }
    }

    /** Confirm geofence arrive suggestion only — never auto-mutates job status. */
    fun markArrived() {
        _state.update {
            it.copy(
                message = "Arrival confirmed — the job stays active until proof of delivery",
                geofence = it.geofence?.copy(suggestArrive = false),
            )
        }
    }

    fun checkGeofence() {
        val job = selectedJob() ?: run {
            _state.update { it.copy(error = "Select a job") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            try {
                val perm = gps.requestLocationPermission()
                if (perm != LocationPermissionStatus.GRANTED &&
                    perm != LocationPermissionStatus.APPROXIMATE
                ) {
                    _state.update {
                        it.copy(busy = false, error = "Location permission required")
                    }
                    return@launch
                }
                val coord = gps.getCurrentPosition()
                val suggestion = rpc.deliveryGeofenceSuggestion(
                    deliveryJobId = job.id,
                    lat = coord.latitude,
                    lng = coord.longitude,
                )
                _state.update {
                    it.copy(
                        busy = false,
                        geofence = suggestion,
                        message = suggestion.distanceM
                            ?.let { d -> "%.0f m from the drop-off".format(d) }
                            ?: "Distance to the drop-off unavailable",
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(busy = false, error = e.message ?: "geofence check failed")
                }
            }
        }
    }

    fun openNavigation() {
        val job = selectedJob()
        val lat = job?.dropoffLat
        val lng = job?.dropoffLng
        if (lat == null || lng == null) {
            _state.update { it.copy(error = "Dropoff coordinates missing") }
            return
        }
        val ok = ExternalNavigation.openTurnByTurn(
            appContext,
            MapLatLng(lat, lng),
        )
        if (!ok) {
            _state.update { it.copy(error = "Cannot open maps") }
        }
    }

    /**
     * Fetch driving polyline for in-app map guidance.
     * Origin: tracking last fix, else one-shot GPS, else destination-only markers.
     * Does not replace FGS ingest.
     */
    fun refreshRouteGuidance(
        driverLat: Double? = null,
        driverLng: Double? = null,
    ) {
        val job = selectedJob() ?: return
        val destLat = job.dropoffLat
        val destLng = job.dropoffLng
        if (destLat == null || destLng == null) {
            _state.update { it.copy(error = "Dropoff coordinates missing", routePoints = emptyList()) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(routeBusy = true, error = null) }
            var originLat = driverLat
            var originLng = driverLng
            if (originLat == null || originLng == null) {
                runCatching {
                    val perm = gps.getLocationPermissionStatus()
                    if (perm == LocationPermissionStatus.GRANTED ||
                        perm == LocationPermissionStatus.APPROXIMATE
                    ) {
                        val coord = gps.getCurrentPosition()
                        originLat = coord.latitude
                        originLng = coord.longitude
                    }
                }
            }
            if (originLat == null || originLng == null) {
                _state.update {
                    it.copy(
                        routeBusy = false,
                        routePoints = emptyList(),
                        routeLabel = "Waiting for GPS",
                        routeEtaSource = null,
                    )
                }
                return@launch
            }
            val origin = MapLatLng(originLat!!, originLng!!)
            val destination = MapLatLng(destLat, destLng)
            val otherWaypoints = _state.value.jobs
                .filter {
                    it.id != job.id &&
                        JobStatusGate.isActive(it.status) &&
                        it.dropoffLat != null &&
                        it.dropoffLng != null &&
                        (it.routeSequence ?: Int.MAX_VALUE) > (job.routeSequence ?: -1)
                }
                .sortedBy { it.routeSequence ?: Int.MAX_VALUE }
                .take(3)
                .map { MapLatLng(it.dropoffLat!!, it.dropoffLng!!) }

            fun applyRoute(etaSource: RouteEtaSource, route: DrivingRoute) {
                _state.update {
                    it.copy(
                        routeBusy = false,
                        routePoints = route.points,
                        routeEtaSource = etaSource,
                        routeDistanceMeters = route.distanceMeters,
                        routeDurationSeconds = route.durationSeconds,
                        routeLabel = formatRouteGuidanceLabel(
                            etaSource = etaSource,
                            summary = route.summary,
                            distanceMeters = route.distanceMeters,
                            durationSeconds = route.durationSeconds,
                        ),
                    )
                }
            }

            fun applyStraightLine() {
                applyRoute(RouteEtaSource.STRAIGHT_LINE, straightLineRoute(origin, destination))
            }

            if (routingBaseUrl.isBlank()) {
                applyStraightLine()
                return@launch
            }
            when (
                val result = router.fetchDrivingRoute(
                    origin = origin,
                    destination = destination,
                    waypoints = otherWaypoints,
                )
            ) {
                is RouteFetchResult.Ok -> applyRoute(RouteEtaSource.OSRM, result.route)
                is RouteFetchResult.Failed -> applyStraightLine()
            }
        }
    }

    fun failSelectedJob() {
        val jobId = _state.value.selectedJobId ?: return
        val reason = _state.value.failReason
        val notes = _state.value.failNotes.ifBlank { null }
        val reattempt = _state.value.createReattempt
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            try {
                rpc.failDeliveryJob(
                    deliveryJobId = jobId,
                    reason = reason,
                    notes = notes,
                    createReattempt = reattempt,
                )
                _state.update {
                    it.copy(
                        busy = false,
                        message = "Marked failed" + if (reattempt) " · reattempt created" else "",
                        selectedJobId = null,
                    )
                }
                refresh()
            } catch (e: Exception) {
                _state.update {
                    it.copy(busy = false, error = e.message ?: "fail job failed")
                }
            }
        }
    }

    fun optimizeStops() {
        val uid = rpc.currentUserId()
        if (uid.isNullOrBlank()) {
            _state.update { it.copy(error = "Signed-in driver required") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            try {
                val stops = rpc.optimizeDriverStops(uid)
                _state.update {
                    it.copy(
                        busy = false,
                        optimizedStops = stops,
                        message = "Route optimised · ${stops.size} stops",
                    )
                }
                refresh()
            } catch (e: Exception) {
                _state.update {
                    it.copy(busy = false, error = e.message ?: "optimize failed")
                }
            }
        }
    }

    fun raisePanic() {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            try {
                var lat: Double? = null
                var lng: Double? = null
                runCatching {
                    val coord = gps.getCurrentPosition()
                    lat = coord.latitude
                    lng = coord.longitude
                }
                rpc.raiseDeliveryPanic(
                    deliveryJobId = _state.value.selectedJobId,
                    lat = lat,
                    lng = lng,
                )
                _state.update {
                    it.copy(
                        busy = false,
                        message = "Dispatch alerted",
                    )
                }
                dialSupport()
            } catch (e: Exception) {
                _state.update {
                    it.copy(busy = false, error = e.message ?: "panic failed")
                }
                dialSupport()
            }
        }
    }

    fun dialSupport() {
        val phone = _state.value.supportPhone.trim()
        if (phone.isBlank()) {
            _state.update {
                it.copy(error = (it.error ?: "") + " Support phone not configured (SUPPORT_PHONE)")
            }
            return
        }
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { appContext.startActivity(intent) }
            .onFailure { e ->
                _state.update { it.copy(error = e.message ?: "Cannot dial support") }
            }
    }

    /** Confirm geofence complete suggestion — does not auto-complete; opens Complete → POD. */
    fun acknowledgeCompleteSuggestion() {
        _state.update {
            it.copy(
                message = "You are at the drop-off — collect proof of delivery to complete",
                geofence = it.geofence?.copy(suggestComplete = false),
            )
        }
    }

    companion object {
        fun factory(
            rpc: RpcClient,
            gps: GpsBridge,
            appContext: Context,
            supportPhone: String,
            routingBaseUrl: String = DirectionsRouteFetcher.DEFAULT_ROUTING_BASE_URL,
            mapStyleUrl: String = "",
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    JobsViewModel(
                        rpc,
                        gps,
                        appContext.applicationContext,
                        supportPhone,
                        routingBaseUrl,
                        mapStyleUrl,
                    ) as T
            }
    }
}
