package co.zw.nissangtr.delivery.tracking

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import co.zw.nissangtr.bridges.location.GpsBridge
import co.zw.nissangtr.bridges.location.GpsWatchCadence
import co.zw.nissangtr.bridges.location.GpsWatchHandle
import co.zw.nissangtr.bridges.location.GpsWatchOptions
import co.zw.nissangtr.bridges.location.LocationPermissionStatus
import co.zw.nissangtr.bridges.location.toDeliveryLocationIngest
import co.zw.nissangtr.delivery.rpc.RpcClient
import co.zw.nissangtr.delivery.rpc.RpcNames
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class TrackingUiState(
    val trackingJobId: String? = null,
    val tracking: Boolean = false,
    val lastIngestId: String? = null,
    val lastLatLng: String? = null,
    val lastLat: Double? = null,
    val lastLng: Double? = null,
    val ingestCount: Int = 0,
    val queuedCount: Int = 0,
    val message: String? = null,
    val error: String? = null,
)

/**
 * Always-on Bridge-First GPS for an active delivery job.
 * FGS via [GpsBridge.watchPosition]; offline queue + flush on reconnect.
 */
class TrackingViewModel(
    private val rpc: RpcClient,
    private val gps: GpsBridge,
    private val appContext: Context,
    private val locationQueue: OfflineLocationQueue = OfflineLocationQueue(appContext),
) : ViewModel() {
    private val _state = MutableStateFlow(TrackingUiState(queuedCount = locationQueue.size()))
    val state: StateFlow<TrackingUiState> = _state.asStateFlow()

    private val throttle = DeliveryLocationIngestThrottle()
    private val ingestMutex = Mutex()
    private var watchHandle: GpsWatchHandle? = null

    init {
        viewModelScope.launch {
            while (true) {
                delay(15_000L)
                if (isOnline()) {
                    flushQueue()
                }
            }
        }
    }

    fun startTracking(jobId: String) {
        val id = jobId.trim()
        if (id.isEmpty()) {
            _state.update { it.copy(error = "Job id required to track") }
            return
        }
        if (_state.value.tracking && _state.value.trackingJobId == id) return

        viewModelScope.launch {
            stopTrackingInternal()
            _state.update { it.copy(error = null, message = null) }
            try {
                val status = gps.requestLocationPermission()
                if (status != LocationPermissionStatus.GRANTED &&
                    status != LocationPermissionStatus.APPROXIMATE
                ) {
                    _state.update {
                        it.copy(error = "Location permission required ($status)")
                    }
                    return@launch
                }

                throttle.reset()
                val handle = gps.watchPosition(
                    onUpdate = { coord ->
                        if (!throttle.tryAccept()) return@watchPosition
                        viewModelScope.launch {
                            ingestMutex.withLock {
                                val payload = toDeliveryLocationIngest(id, coord)
                                if (!isOnline()) {
                                    locationQueue.enqueue(
                                        QueuedLocationPing(
                                            deliveryJobId = payload.deliveryJobId,
                                            lat = payload.lat,
                                            lng = payload.lng,
                                            recordedAt = payload.recordedAt,
                                            accuracyM = payload.accuracyM,
                                        ),
                                    )
                                    _state.update {
                                        it.copy(
                                            queuedCount = locationQueue.size(),
                                            lastLatLng = "%.5f, %.5f".format(payload.lat, payload.lng),
                                            lastLat = payload.lat,
                                            lastLng = payload.lng,
                                            message = "Offline — queued GPS ping",
                                        )
                                    }
                                    return@withLock
                                }
                                try {
                                    val ingestId = rpc.ingestDeliveryLocation(
                                        deliveryJobId = payload.deliveryJobId,
                                        lat = payload.lat,
                                        lng = payload.lng,
                                        recordedAt = payload.recordedAt,
                                        accuracyM = payload.accuracyM,
                                    )
                                    _state.update {
                                        it.copy(
                                            lastIngestId = ingestId,
                                            lastLatLng = "%.5f, %.5f".format(payload.lat, payload.lng),
                                            lastLat = payload.lat,
                                            lastLng = payload.lng,
                                            ingestCount = it.ingestCount + 1,
                                            message = "${RpcNames.INGEST_DELIVERY_LOCATION} → $ingestId",
                                            error = null,
                                        )
                                    }
                                    flushQueue()
                                } catch (e: Exception) {
                                    locationQueue.enqueue(
                                        QueuedLocationPing(
                                            deliveryJobId = payload.deliveryJobId,
                                            lat = payload.lat,
                                            lng = payload.lng,
                                            recordedAt = payload.recordedAt,
                                            accuracyM = payload.accuracyM,
                                        ),
                                    )
                                    _state.update {
                                        it.copy(
                                            queuedCount = locationQueue.size(),
                                            error = e.message ?: "ingest failed — queued",
                                        )
                                    }
                                }
                            }
                        }
                    },
                    onError = { msg -> _state.update { it.copy(error = msg) } },
                    options = GpsWatchOptions(cadence = GpsWatchCadence.AUTO),
                )
                watchHandle = handle
                _state.update {
                    it.copy(
                        tracking = true,
                        trackingJobId = id,
                        message = "Tracking $id (FGS + battery cadence, ≥5s throttle)",
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(tracking = false, error = e.message ?: "start tracking failed")
                }
            }
        }
    }

    fun stopTracking() {
        viewModelScope.launch { stopTrackingInternal() }
    }

    fun flushNow() {
        viewModelScope.launch { flushQueue() }
    }

    private suspend fun stopTrackingInternal() {
        val handle = watchHandle
        watchHandle = null
        runCatching { handle?.stop() }
        _state.update {
            it.copy(tracking = false, trackingJobId = null, message = "Tracking stopped")
        }
    }

    private suspend fun flushQueue() {
        if (!isOnline()) return
        ingestMutex.withLock {
            val pending = locationQueue.peek()
            if (pending.isEmpty()) return
            var flushed = 0
            for (ping in pending) {
                if (!throttle.tryAccept()) {
                    delay(DeliveryLocationIngestThrottle.DEFAULT_MIN_INTERVAL_MS)
                    if (!throttle.tryAccept()) break
                }
                try {
                    rpc.ingestDeliveryLocation(
                        deliveryJobId = ping.deliveryJobId,
                        lat = ping.lat,
                        lng = ping.lng,
                        recordedAt = ping.recordedAt,
                        accuracyM = ping.accuracyM,
                    )
                    flushed++
                    _state.update { it.copy(ingestCount = it.ingestCount + 1) }
                } catch (_: Exception) {
                    break
                }
            }
            if (flushed > 0) {
                locationQueue.dropFirst(flushed)
                _state.update {
                    it.copy(
                        queuedCount = locationQueue.size(),
                        message = "Flushed $flushed queued GPS pings",
                        error = null,
                    )
                }
            }
        }
    }

    private fun isOnline(): Boolean {
        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    override fun onCleared() {
        val handle = watchHandle
        watchHandle = null
        if (handle != null) {
            kotlinx.coroutines.CoroutineScope(
                kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Main.immediate,
            ).launch {
                runCatching { handle.stop() }
            }
        }
        super.onCleared()
    }

    companion object {
        fun factory(
            rpc: RpcClient,
            gps: GpsBridge,
            appContext: Context,
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    TrackingViewModel(rpc, gps, appContext.applicationContext) as T
            }
    }
}
