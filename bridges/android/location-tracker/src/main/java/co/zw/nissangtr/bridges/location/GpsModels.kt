package co.zw.nissangtr.bridges.location

/**
 * Mirrors bridges/contracts/gps.ts — native Kotlin surface for GpsBridge.
 * Bridge emits coordinates only; callers map via [toDeliveryLocationIngest]
 * and post with RPC ingest_delivery_location (never from this module).
 */

enum class LocationPermissionStatus {
    GRANTED,
    DENIED,
    RESTRICTED,
    NOT_DETERMINED,
    /** Approximate-only when fine was not granted. */
    APPROXIMATE,
}

/** One native position fix from the device. */
data class GpsCoordinate(
    val latitude: Double,
    val longitude: Double,
    /** Horizontal accuracy in meters when the OS provides it. */
    val accuracyMeters: Float? = null,
    /** ISO-8601 timestamp from the device (maps to p_recorded_at). */
    val capturedAt: String,
)

/**
 * Payload shape for ingest_delivery_location after a bridge fix.
 * Field names mirror RPC parameters for management-app / service callers.
 */
data class DeliveryLocationIngest(
    val deliveryJobId: String,
    val lat: Double,
    val lng: Double,
    /** ISO-8601; omit to let the RPC default to now(). */
    val recordedAt: String? = null,
    /** Meters; omit when accuracy unknown. */
    val accuracyM: Double? = null,
)

fun toDeliveryLocationIngest(
    deliveryJobId: String,
    coord: GpsCoordinate,
): DeliveryLocationIngest = DeliveryLocationIngest(
    deliveryJobId = deliveryJobId,
    lat = coord.latitude,
    lng = coord.longitude,
    recordedAt = coord.capturedAt,
    accuracyM = coord.accuracyMeters?.toDouble(),
)

interface GpsWatchHandle {
    suspend fun stop()
}

/**
 * Native GPS for delivery tracking.
 * Prefer [watchPosition] while a job is en route; throttle client-side (~5s)
 * before calling ingest_delivery_location. This bridge does not call Supabase.
 */
interface GpsBridge {
    suspend fun getLocationPermissionStatus(): LocationPermissionStatus
    suspend fun requestLocationPermission(): LocationPermissionStatus
    /** One-shot current position (permission-gated on device). */
    suspend fun getCurrentPosition(): GpsCoordinate
    /**
     * Stream updates for delivery tracking; caller must [GpsWatchHandle.stop].
     * Starts a location foreground service so tracking can continue when backgrounded.
     */
    suspend fun watchPosition(
        onUpdate: (GpsCoordinate) -> Unit,
        onError: ((String) -> Unit)? = null,
    ): GpsWatchHandle
}
