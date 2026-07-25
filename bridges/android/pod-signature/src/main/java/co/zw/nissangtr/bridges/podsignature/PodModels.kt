package co.zw.nissangtr.bridges.podsignature

/**
 * Mirrors bridges/contracts/pod.ts — POD signature capture surface.
 * Bridge yields local PNG paths only; app uploads to Storage then calls
 * submit_delivery_pod. No Supabase / WebView inside this module.
 */

/** Local capture artifact ready for app-side Storage upload. */
data class PodCaptureResult(
    val localPath: String,
    val mimeType: String,
    val capturedAt: String,
)

data class PodSignatureOptions(
    val title: String? = null,
    /** Stroke width in density-independent pixels. */
    val strokeWidth: Float? = null,
)

/**
 * Native ink signature pad (Canvas View — no WebView).
 * Returns a local PNG path — no network inside the bridge.
 */
interface PodSignatureBridge {
    suspend fun captureSignature(options: PodSignatureOptions = PodSignatureOptions()): PodCaptureResult
}
