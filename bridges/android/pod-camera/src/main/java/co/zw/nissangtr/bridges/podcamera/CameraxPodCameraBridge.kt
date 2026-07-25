package co.zw.nissangtr.bridges.podcamera

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.lang.ref.WeakReference
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Android [PodCameraBridge] via CameraX ImageCapture.
 *
 * Host must [attachActivity] before [requestCameraPermission] / [capturePhoto].
 * Emits local JPEG paths only — **no Supabase / network**.
 */
class CameraxPodCameraBridge(
    context: Context,
) : PodCameraBridge {

    private val appContext = context.applicationContext
    private var activityRef: WeakReference<Activity>? = null

    @Volatile
    private var captureContinuation: Continuation<PodCaptureResult>? = null

    fun attachActivity(activity: Activity) {
        activityRef = WeakReference(activity)
    }

    fun detachActivity() {
        activityRef = null
    }

    override suspend fun getCameraPermissionStatus(): CameraPermissionStatus =
        withContext(Dispatchers.Main) { resolvePermissionStatus() }

    override suspend fun requestCameraPermission(): CameraPermissionStatus =
        withContext(Dispatchers.Main) {
            val status = resolvePermissionStatus()
            if (status == CameraPermissionStatus.GRANTED) return@withContext status
            val activity = activityRef?.get()
                ?: return@withContext CameraPermissionStatus.NOT_DETERMINED
            suspendCancellableCoroutine { cont ->
                CameraPermissionRelay.arm(cont)
                cont.invokeOnCancellation { CameraPermissionRelay.cancel() }
                ActivityCompat.requestPermissions(
                    activity,
                    arrayOf(Manifest.permission.CAMERA),
                    REQUEST_CAMERA,
                )
            }
        }

    fun onPermissionResult() {
        CameraPermissionRelay.complete(resolvePermissionStatus())
    }

    /**
     * Deliver a capture from [PodPhotoCaptureActivity].
     * Called via [Activity.onActivityResult] when requestCode is [REQUEST_CAPTURE].
     */
    fun onCaptureActivityResult(resultCode: Int, data: Intent?) {
        val cont = captureContinuation ?: return
        captureContinuation = null
        if (resultCode != Activity.RESULT_OK || data == null) {
            cont.resumeWithException(CancellationException("POD photo cancelled"))
            return
        }
        val path = data.getStringExtra(PodPhotoCaptureActivity.EXTRA_LOCAL_PATH)
        val mime = data.getStringExtra(PodPhotoCaptureActivity.EXTRA_MIME_TYPE)
        val at = data.getStringExtra(PodPhotoCaptureActivity.EXTRA_CAPTURED_AT)
        if (path.isNullOrBlank() || mime.isNullOrBlank() || at.isNullOrBlank()) {
            cont.resumeWithException(IllegalStateException("POD photo returned empty path"))
            return
        }
        cont.resume(PodCaptureResult(localPath = path, mimeType = mime, capturedAt = at))
    }

    override suspend fun capturePhoto(): PodCaptureResult = withContext(Dispatchers.Main) {
        val perm = resolvePermissionStatus()
        if (perm != CameraPermissionStatus.GRANTED) {
            throw SecurityException("Camera permission not granted ($perm)")
        }
        val activity = activityRef?.get()
            ?: throw IllegalStateException("attachActivity() required before capturePhoto()")
        suspendCancellableCoroutine { cont ->
            captureContinuation?.resumeWithException(
                CancellationException("Superseded by a new capturePhoto()"),
            )
            captureContinuation = cont
            cont.invokeOnCancellation {
                if (captureContinuation === cont) captureContinuation = null
            }
            @Suppress("DEPRECATION")
            activity.startActivityForResult(
                Intent(activity, PodPhotoCaptureActivity::class.java),
                REQUEST_CAPTURE,
            )
        }
    }

    private fun resolvePermissionStatus(): CameraPermissionStatus {
        val granted = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.CAMERA,
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) return CameraPermissionStatus.GRANTED
        val activity = activityRef?.get()
        return if (activity != null &&
            ActivityCompat.shouldShowRequestPermissionRationale(
                activity,
                Manifest.permission.CAMERA,
            )
        ) {
            CameraPermissionStatus.DENIED
        } else {
            CameraPermissionStatus.NOT_DETERMINED
        }
    }

    companion object {
        const val REQUEST_CAMERA: Int = 0x50_43 // "PC"
        const val REQUEST_CAPTURE: Int = 0x50_50 // "PP"
    }
}
