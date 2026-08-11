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
import co.zw.nissangtr.bridges.maps.ExternalNavigation
import co.zw.nissangtr.bridges.maps.MapLatLng
import co.zw.nissangtr.bridges.maps.OsrmRouteFetcher
import co.zw.nissangtr.bridges.maps.RouteFetchResult
import co.zw.nissangtr.delivery.rpc.DeliveryFailureReason
import co.zw.nissangtr.delivery.rpc.DeliveryJobSummary
import co.zw.nissangtr.delivery.rpc.DriverPresenceStatus
import co.zw.nissangtr.delivery.rpc.GeofenceSuggestion
import co.zw.nissangtr.delivery.rpc.OptimizedStop
import co.zw.nissangtr.delivery.rpc.RpcClient
import co.zw.nissangtr.delivery.rpc.RpcNames
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
    val mapsKeyPresent: Boolean = false,
    val osrmConfigured: Boolean = false,
    val routePoints: List<MapLatLng> = emptyList(),
    val routeLabel: String? = null,
    val routeBusy: Boolean = false,
    val busy: Boolean = false,
    val message: String? = null,
    val error: String? = null,
)

class JobsViewModel(
    private val rpc: RpcClient,
    private val gps: GpsBridge,
    private val appContext: Context,
    supportPhone: String,
    private val mapsApiKey: String,
    private val osrmUrl: String = "",
) : ViewModel() {
    private val _state = MutableStateFlow(
        JobsUiState(
            supportPhone = supportPhone,
            mapsKeyPresent = mapsApiKey.isNotBlank(),
            osrmConfigured = osrmUrl.isNotBlank(),
        ),
    )
    val state: StateFlow<JobsUiState> = _state.asStateFlow()

    private val osrm by lazy { OsrmRouteFetcher(osrmUrl) }
    private val googleDirections by lazy { DirectionsRouteFetcher(mapsApiKey) }

    init {
        refresh()
        loadPresence()
    }

    fun selectedJob(): DeliveryJobSummary? {
        val id = _state.value.selectedJobId ?: return null
        return _state.value.jobs.find { it.id == id }
    }

    fun selectJob(id: String?) = _state.update {
        it.copy(
            selectedJobId = id,
            geofence = null,
            routePoints = emptyList(),
            routeLabel = null,
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
                        message = "${RpcNames.SET_DRIVER_PRESENCE} → ${status.rpcValue}",
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
                message = "Arrival confirmed (geofence suggestion accepted — status unchanged until POD)",
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
                        message = "Geofence: ${suggestion.distanceM?.let { d -> "%.0fm".format(d) } ?: "n/a"} " +
                            "arrive=${suggestion.suggestArrive} complete=${suggestion.suggestComplete}",
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
        if (osrmUrl.isBlank() && mapsApiKey.isBlank()) {
            _state.update {
                it.copy(
                    routeLabel = "Routing unconfigured — set OSRM_URL (preferred) or GOOGLE_MAPS_API_KEY",
                    routePoints = emptyList(),
                )
            }
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
                        routeLabel = "Waiting for GPS for route — destination marked",
                    )
                }
                return@launch
            }
            val otherWaypoints = _state.value.jobs
                .filter {
                    it.id != job.id &&
                        it.status != "completed" &&
                        it.status != "failed" &&
                        it.dropoffLat != null &&
                        it.dropoffLng != null &&
                        (it.routeSequence ?: Int.MAX_VALUE) > (job.routeSequence ?: -1)
                }
                .sortedBy { it.routeSequence ?: Int.MAX_VALUE }
                .take(3)
                .map { MapLatLng(it.dropoffLat!!, it.dropoffLng!!) }

            when (
                val result = if (osrmUrl.isNotBlank()) {
                    osrm.fetchDrivingRoute(
                        origin = MapLatLng(originLat!!, originLng!!),
                        destination = MapLatLng(destLat, destLng),
                        waypoints = otherWaypoints,
                    )
                } else {
                    googleDirections.fetchDrivingRoute(
                        origin = MapLatLng(originLat!!, originLng!!),
                        destination = MapLatLng(destLat, destLng),
                        waypoints = otherWaypoints,
                    )
                }
            ) {
                is RouteFetchResult.Ok -> {
                    val r = result.route
                    val dist = r.distanceMeters?.let { d ->
                        if (d >= 1000) "%.1f km".format(d / 1000.0) else "${d}m"
                    }
                    val dur = r.durationSeconds?.let { s ->
                        val m = s / 60
                        if (m >= 60) "${m / 60}h ${m % 60}m" else "${m} min"
                    }
                    _state.update {
                        it.copy(
                            routeBusy = false,
                            routePoints = r.points,
                            routeLabel = listOfNotNull(
                                r.summary,
                                dist,
                                dur,
                            ).joinToString(" · ").ifBlank { "Route ready" },
                        )
                    }
                }
                is RouteFetchResult.Failed -> {
                    _state.update {
                        it.copy(
                            routeBusy = false,
                            routePoints = emptyList(),
                            routeLabel = result.message,
                        )
                    }
                }
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
                val id = rpc.failDeliveryJob(
                    deliveryJobId = jobId,
                    reason = reason,
                    notes = notes,
                    createReattempt = reattempt,
                )
                _state.update {
                    it.copy(
                        busy = false,
                        message = "${RpcNames.FAIL_DELIVERY_JOB} → $id" +
                            if (reattempt) " (reattempt created)" else "",
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
                        message = "${RpcNames.OPTIMIZE_DRIVER_STOPS} → ${stops.size} stops",
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
                val id = rpc.raiseDeliveryPanic(
                    deliveryJobId = _state.value.selectedJobId,
                    lat = lat,
                    lng = lng,
                )
                _state.update {
                    it.copy(
                        busy = false,
                        message = "${RpcNames.RAISE_DELIVERY_PANIC} → $id",
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

    /** Confirm geofence complete suggestion — does not auto-complete; opens POD path. */
    fun acknowledgeCompleteSuggestion() {
        _state.update {
            it.copy(
                message = "Complete suggested — use POD section to finish (OTP required)",
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
            mapsApiKey: String,
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    JobsViewModel(
                        rpc,
                        gps,
                        appContext.applicationContext,
                        supportPhone,
                        mapsApiKey,
                    ) as T
            }
    }
}
