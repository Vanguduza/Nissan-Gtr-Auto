package co.zw.nissangtr.bridges.location

import android.Manifest
import android.app.Activity
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
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Android [GpsBridge] via FusedLocationProviderClient.
 *
 * Host must [attachActivity] before [requestLocationPermission] so the runtime
 * permission dialog can be shown. Continuous [watchPosition] starts
 * [DeliveryLocationTrackingService] (foreground, type=location).
 *
 * Does not perform network I/O — emit [GpsCoordinate] only.
 */
class FusedLocationGpsBridge(
    context: Context,
) : GpsBridge {

    private val appContext = context.applicationContext
    private val fused = LocationServices.getFusedLocationProviderClient(appContext)
    private var activityRef: WeakReference<Activity>? = null

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
            val permissions = buildList {
                add(Manifest.permission.ACCESS_FINE_LOCATION)
                add(Manifest.permission.ACCESS_COARSE_LOCATION)
            }.toTypedArray()
            ActivityCompat.requestPermissions(
                activity,
                permissions,
                REQUEST_LOCATION,
            )
            // Result arrives asynchronously via Activity; re-check after host resumes.
            resolvePermissionStatus()
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
    ): GpsWatchHandle = withContext(Dispatchers.Main) {
        ensureForegroundLocationAllowed()

        val stopped = AtomicBoolean(false)
        val request = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            DEFAULT_INTERVAL_MS,
        )
            .setMinUpdateIntervalMillis(MIN_INTERVAL_MS)
            .setMinUpdateDistanceMeters(0f)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                if (stopped.get()) return
                val location = result.lastLocation ?: return
                try {
                    onUpdate(location.toGpsCoordinate())
                } catch (t: Throwable) {
                    onError?.invoke(t.message ?: "onUpdate failed")
                }
            }
        }

        try {
            fused.requestLocationUpdates(request, callback, Looper.getMainLooper())
        } catch (se: SecurityException) {
            onError?.invoke(se.message ?: "Location permission revoked")
            throw se
        }

        startForegroundTrackingService()

        object : GpsWatchHandle {
            override suspend fun stop() = withContext(Dispatchers.Main) {
                if (!stopped.compareAndSet(false, true)) return@withContext
                fused.removeLocationUpdates(callback)
                stopForegroundTrackingService()
            }
        }
    }

    private fun startForegroundTrackingService() {
        val intent = Intent(appContext, DeliveryLocationTrackingService::class.java)
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
        /** Align with server ~5s ingest rate; UI may throttle further. */
        const val DEFAULT_INTERVAL_MS: Long = 5_000L
        const val MIN_INTERVAL_MS: Long = 3_000L
    }
}

internal fun android.location.Location.toGpsCoordinate(): GpsCoordinate {
    val accuracy = if (hasAccuracy()) accuracy else null
    val instant = Instant.ofEpochMilli(time)
    return GpsCoordinate(
        latitude = latitude,
        longitude = longitude,
        accuracyMeters = accuracy,
        capturedAt = instant.toString(),
    )
}
