package co.zw.nissangtr.bridges.location

import android.Manifest
import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.Looper
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.lang.ref.WeakReference
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Android [GpsBridge] via FusedLocationProviderClient.
 *
 * Host must [attachActivity] before [requestLocationPermission] so the runtime
 * permission dialog can be shown. Continuous [watchPosition] starts
 * [DeliveryLocationTrackingService] (foreground, type=location) with a
 * persistent low-priority notification.
 *
 * Battery cadence: [GpsWatchCadence.AUTO] switches MOVING (~5s high accuracy)
 * ↔ IDLE (~30s balanced + distance) from speed heuristics.
 *
 * Does not perform network I/O — emit [GpsCoordinate] only.
 * Optional [pingBuffer] holds an ephemeral ring of fixes for app flush logic.
 */
class FusedLocationGpsBridge(
    context: Context,
    /** Optional in-memory buffer; app still owns durable offline queue. */
    val pingBuffer: GpsPingBuffer? = null,
) : GpsBridge {

    private val appContext = context.applicationContext
    private val fused = LocationServices.getFusedLocationProviderClient(appContext)
    private var activityRef: WeakReference<Activity>? = null

    /** Optional tap-target for the FGS notification (delivery job screen). */
    var notificationContentIntent: PendingIntent? = null

    /** Call from the driver Activity (e.g. onResume) so permission prompts work. */
    fun attachActivity(activity: Activity) {
        activityRef = WeakReference(activity)
    }

    fun detachActivity() {
        activityRef = null
    }

    override suspend fun getLocationPermissionStatus(): LocationPermissionStatus =
        withContext(Dispatchers.Main) {
            resolvePermissionStatus()
        }

    override suspend fun requestLocationPermission(): LocationPermissionStatus =
        withContext(Dispatchers.Main) {
            val status = resolvePermissionStatus()
            if (status == LocationPermissionStatus.GRANTED ||
                status == LocationPermissionStatus.APPROXIMATE
            ) {
                return@withContext status
            }
            val activity = activityRef?.get()
                ?: return@withContext LocationPermissionStatus.NOT_DETERMINED
            suspendCancellableCoroutine { cont ->
                LocationPermissionRelay.arm(cont)
                cont.invokeOnCancellation { LocationPermissionRelay.cancel() }
                val permissions = arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                )
                ActivityCompat.requestPermissions(
                    activity,
                    permissions,
                    REQUEST_LOCATION,
                )
            }
        }

    /**
     * Call from the host Activity when requestCode matches [REQUEST_LOCATION]
     * (or from Activity Result callback). Completes a pending
     * [requestLocationPermission] suspension.
     */
    fun onPermissionResult() {
        LocationPermissionRelay.complete(resolvePermissionStatus())
    }

    /**
     * Request background location after fine is granted (Android 10+).
     * Call from a dedicated settings / "Allow all the time" UX step — not the
     * same dialog as foreground permission (Play policy).
     */
    fun requestBackgroundLocationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        val activity = activityRef?.get() ?: return
        if (ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.ACCESS_FINE_LOCATION,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        if (ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        ActivityCompat.requestPermissions(
            activity,
            arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION),
            REQUEST_BACKGROUND_LOCATION,
        )
    }

    override suspend fun getCurrentPosition(): GpsCoordinate = withContext(Dispatchers.Main) {
        ensureForegroundLocationAllowed()
        suspendCancellableCoroutine { cont ->
            fused.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener { location ->
                    if (location == null) {
                        cont.resumeWithException(
                            IllegalStateException("No current location available"),
                        )
                    } else {
                        cont.resume(location.toGpsCoordinate())
                    }
                }
                .addOnFailureListener { e ->
                    cont.resumeWithException(e)
                }
        }
    }

    override suspend fun watchPosition(
        onUpdate: (GpsCoordinate) -> Unit,
        onError: ((String) -> Unit)?,
        options: GpsWatchOptions,
    ): GpsWatchHandle = withContext(Dispatchers.Main) {
        ensureForegroundLocationAllowed()

        val stopped = AtomicBoolean(false)
        val activeCadence = AtomicReference(
            when (options.cadence) {
                GpsWatchCadence.IDLE -> GpsWatchCadence.IDLE
                GpsWatchCadence.MOVING, GpsWatchCadence.AUTO -> GpsWatchCadence.MOVING
            },
        )

        lateinit var callback: LocationCallback

        fun buildRequest(cadence: GpsWatchCadence): LocationRequest {
            val (priority, intervalMs, defaultDistance) = when (cadence) {
                GpsWatchCadence.IDLE -> Triple(
                    Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                    IDLE_INTERVAL_MS,
                    IDLE_MIN_DISTANCE_M,
                )
                GpsWatchCadence.MOVING, GpsWatchCadence.AUTO -> Triple(
                    Priority.PRIORITY_HIGH_ACCURACY,
                    MOVING_INTERVAL_MS,
                    MOVING_MIN_DISTANCE_M,
                )
            }
            val distance = options.minDistanceMeters ?: defaultDistance
            return LocationRequest.Builder(priority, intervalMs)
                .setMinUpdateIntervalMillis(
                    when (cadence) {
                        GpsWatchCadence.IDLE -> IDLE_MIN_INTERVAL_MS
                        else -> MOVING_MIN_INTERVAL_MS
                    },
                )
                .setMinUpdateDistanceMeters(distance)
                .build()
        }

        fun applyCadence(next: GpsWatchCadence) {
            if (stopped.get()) return
            if (activeCadence.getAndSet(next) == next) return
            try {
                fused.removeLocationUpdates(callback)
                fused.requestLocationUpdates(
                    buildRequest(next),
                    callback,
                    Looper.getMainLooper(),
                )
                DeliveryLocationTrackingService.updateNotificationCadence(appContext, next)
            } catch (se: SecurityException) {
                onError?.invoke(se.message ?: "Location permission revoked")
            }
        }

        callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                if (stopped.get()) return
                val location = result.lastLocation ?: return
                val coord = location.toGpsCoordinate()
                pingBuffer?.offer(coord)
                try {
                    onUpdate(coord)
                } catch (t: Throwable) {
                    onError?.invoke(t.message ?: "onUpdate failed")
                }
                if (options.cadence == GpsWatchCadence.AUTO) {
                    val speed = coord.speedMetersPerSecond
                    val moving = speed != null && speed >= MOVING_SPEED_THRESHOLD_MPS
                    applyCadence(if (moving) GpsWatchCadence.MOVING else GpsWatchCadence.IDLE)
                }
            }
        }

        try {
            fused.requestLocationUpdates(
                buildRequest(activeCadence.get()),
                callback,
                Looper.getMainLooper(),
            )
        } catch (se: SecurityException) {
            onError?.invoke(se.message ?: "Location permission revoked")
            throw se
        }

        startForegroundTrackingService(activeCadence.get())

        object : GpsWatchHandle {
            override suspend fun stop() = withContext(Dispatchers.Main) {
                if (!stopped.compareAndSet(false, true)) return@withContext
                fused.removeLocationUpdates(callback)
                stopForegroundTrackingService()
            }
        }
    }

    private fun startForegroundTrackingService(cadence: GpsWatchCadence) {
        val intent = Intent(appContext, DeliveryLocationTrackingService::class.java).apply {
            putExtra(DeliveryLocationTrackingService.EXTRA_CADENCE, cadence.name)
            notificationContentIntent?.let {
                putExtra(DeliveryLocationTrackingService.EXTRA_CONTENT_INTENT, it)
            }
        }
        ContextCompat.startForegroundService(appContext, intent)
    }

    private fun stopForegroundTrackingService() {
        val intent = Intent(appContext, DeliveryLocationTrackingService::class.java)
        appContext.stopService(intent)
    }

    private fun ensureForegroundLocationAllowed() {
        val status = resolvePermissionStatus()
        if (status != LocationPermissionStatus.GRANTED &&
            status != LocationPermissionStatus.APPROXIMATE
        ) {
            throw SecurityException("Location permission not granted ($status)")
        }
        val lm = appContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val enabled = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            lm.isLocationEnabled
        } else {
            @Suppress("DEPRECATION")
            lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        }
        if (!enabled) {
            throw IllegalStateException("Device location is disabled")
        }
    }

    private fun resolvePermissionStatus(): LocationPermissionStatus {
        val fine = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED

        return when {
            fine -> LocationPermissionStatus.GRANTED
            coarse -> LocationPermissionStatus.APPROXIMATE
            else -> {
                val activity = activityRef?.get()
                if (activity != null &&
                    (
                        ActivityCompat.shouldShowRequestPermissionRationale(
                            activity,
                            Manifest.permission.ACCESS_FINE_LOCATION,
                        ) ||
                            ActivityCompat.shouldShowRequestPermissionRationale(
                                activity,
                                Manifest.permission.ACCESS_COARSE_LOCATION,
                            )
                        )
                ) {
                    LocationPermissionStatus.DENIED
                } else {
                    LocationPermissionStatus.NOT_DETERMINED
                }
            }
        }
    }

    companion object {
        const val REQUEST_LOCATION: Int = 0x47_52 // "GR"
        const val REQUEST_BACKGROUND_LOCATION: Int = 0x47_42 // "GB"

        /** Moving: align with server ~5s ingest rate. */
        const val MOVING_INTERVAL_MS: Long = 5_000L
        const val MOVING_MIN_INTERVAL_MS: Long = 3_000L
        const val MOVING_MIN_DISTANCE_M: Float = 0f

        /** Idle / parked: lower duty cycle. */
        const val IDLE_INTERVAL_MS: Long = 30_000L
        const val IDLE_MIN_INTERVAL_MS: Long = 15_000L
        const val IDLE_MIN_DISTANCE_M: Float = 25f

        /** ≥ ~3.6 km/h treated as moving for AUTO cadence. */
        const val MOVING_SPEED_THRESHOLD_MPS: Float = 1.0f

        @Deprecated("Use MOVING_INTERVAL_MS", ReplaceWith("MOVING_INTERVAL_MS"))
        const val DEFAULT_INTERVAL_MS: Long = MOVING_INTERVAL_MS

        @Deprecated("Use MOVING_MIN_INTERVAL_MS", ReplaceWith("MOVING_MIN_INTERVAL_MS"))
        const val MIN_INTERVAL_MS: Long = MOVING_MIN_INTERVAL_MS
    }
}

internal fun android.location.Location.toGpsCoordinate(): GpsCoordinate {
    val accuracy = if (hasAccuracy()) accuracy else null
    val speed = if (hasSpeed()) speed else null
    val instant = Instant.ofEpochMilli(time)
    return GpsCoordinate(
        latitude = latitude,
        longitude = longitude,
        accuracyMeters = accuracy,
        capturedAt = instant.toString(),
        speedMetersPerSecond = speed,
    )
}
