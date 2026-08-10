package co.zw.nissangtr.bridges.podcamera

/**
 * Mirrors bridges/contracts/pod.ts — POD photo capture surface.
 * Bridge yields local file paths only; app uploads to Storage then calls
 * submit_delivery_pod. No Supabase inside this module.
 */

enum class CameraPermissionStatus {
    GRANTED,
    DENIED,
    RESTRICTED,
    NOT_DETERMINED,
}

/** Local capture artifact ready for app-side Storage upload. */
data class PodCaptureResult(
    /** Absolute filesystem path on device. */
    val localPath: String,
    /** e.g. image/jpeg */
    val mimeType: String,
    /** ISO-8601 from device clock. */
    val capturedAt: String,
)

/**
 * Native POD photo (delivery package / doorstep).
 * Returns a local JPEG path — no network inside the bridge.
 */
interface PodCameraBridge {
    suspend fun getCameraPermissionStatus(): CameraPermissionStatus
    suspend fun requestCameraPermission(): CameraPermissionStatus
    /** Opens native capture UI; resolves with a local image path. */
    suspend fun capturePhoto(): PodCaptureResult
}
