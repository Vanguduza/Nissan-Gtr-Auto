package co.zw.nissangtr.bridges.podsignature

import android.app.Activity
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.lang.ref.WeakReference
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Android [PodSignatureBridge] via native [SignaturePadView] (no WebView).
 *
 * Host must [attachActivity] before [captureSignature].
 * Emits local PNG paths only — **no Supabase / network**.
 */
class CanvasPodSignatureBridge(
    @Suppress("UNUSED_PARAMETER") context: Context,
) : PodSignatureBridge {

    private var activityRef: WeakReference<Activity>? = null

    @Volatile
    private var captureContinuation: Continuation<PodCaptureResult>? = null

    fun attachActivity(activity: Activity) {
        activityRef = WeakReference(activity)
    }

    fun detachActivity() {
        activityRef = null
    }

    /**
     * Deliver a capture from [PodSignatureCaptureActivity].
     * Called via [Activity.onActivityResult] when requestCode is [REQUEST_SIGNATURE].
     */
    fun onSignatureActivityResult(resultCode: Int, data: Intent?) {
        val cont = captureContinuation ?: return
        captureContinuation = null
        if (resultCode != Activity.RESULT_OK || data == null) {
            cont.resumeWithException(CancellationException("POD signature cancelled"))
            return
        }
        val path = data.getStringExtra(PodSignatureCaptureActivity.EXTRA_LOCAL_PATH)
        val mime = data.getStringExtra(PodSignatureCaptureActivity.EXTRA_MIME_TYPE)
        val at = data.getStringExtra(PodSignatureCaptureActivity.EXTRA_CAPTURED_AT)
        if (path.isNullOrBlank() || mime.isNullOrBlank() || at.isNullOrBlank()) {
            cont.resumeWithException(IllegalStateException("POD signature returned empty path"))
            return
        }
        cont.resume(PodCaptureResult(localPath = path, mimeType = mime, capturedAt = at))
    }

    override suspend fun captureSignature(
        options: PodSignatureOptions,
    ): PodCaptureResult = withContext(Dispatchers.Main) {
        val activity = activityRef?.get()
            ?: throw IllegalStateException("attachActivity() required before captureSignature()")
        suspendCancellableCoroutine { cont ->
            captureContinuation?.resumeWithException(
                CancellationException("Superseded by a new captureSignature()"),
            )
            captureContinuation = cont
            cont.invokeOnCancellation {
                if (captureContinuation === cont) captureContinuation = null
            }
            val intent = Intent(activity, PodSignatureCaptureActivity::class.java).apply {
                options.title?.let { putExtra(PodSignatureCaptureActivity.EXTRA_TITLE, it) }
                options.strokeWidth?.let {
                    putExtra(PodSignatureCaptureActivity.EXTRA_STROKE_WIDTH_DP, it)
                }
            }
            @Suppress("DEPRECATION")
            activity.startActivityForResult(intent, REQUEST_SIGNATURE)
        }
    }

    companion object {
        const val REQUEST_SIGNATURE: Int = 0x50_53 // "PS"
    }
}
