package co.zw.nissangtr.bridges.qr

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
 * Android [QrScannerBridge] via CameraX + ML Kit barcode scanning.
 *
 * Host must [attachActivity] before [requestCameraPermission] / [scanOnce]
 * so permission dialogs and [QrScanActivity] can launch.
 *
 * Emits decoded payload strings only — **no Supabase / network**.
 */
class CameraxQrScannerBridge(
    context: Context,
) : QrScannerBridge {

    private val appContext = context.applicationContext
    private var activityRef: WeakReference<Activity>? = null

    @Volatile
    private var scanContinuation: Continuation<QrScanResult>? = null

    /** Call from the host Activity (e.g. onResume) so permission + scan UX work. */
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

    /**
     * Call from the host Activity when requestCode matches [REQUEST_CAMERA].
     * Completes a pending [requestCameraPermission] suspension.
     */
    fun onPermissionResult() {
        CameraPermissionRelay.complete(resolvePermissionStatus())
    }

    /**
     * Deliver a successful decode from [QrScanActivity].
     * Called via [onActivityResult] when requestCode is [REQUEST_SCAN].
     */
    fun onScanActivityResult(resultCode: Int, data: Intent?) {
        val cont = scanContinuation ?: return
        scanContinuation = null
        if (resultCode != Activity.RESULT_OK || data == null) {
            cont.resumeWithException(CancellationException("QR scan cancelled"))
            return
        }
        val raw = data.getStringExtra(QrScanActivity.EXTRA_RAW_VALUE)
        val at = data.getStringExtra(QrScanActivity.EXTRA_SCANNED_AT)
        if (raw.isNullOrBlank() || at.isNullOrBlank()) {
            cont.resumeWithException(IllegalStateException("QR scan returned empty payload"))
            return
        }
        cont.resume(QrScanResult(rawValue = raw, scannedAt = at))
    }

    override suspend fun scanOnce(): QrScanResult = withContext(Dispatchers.Main) {
        val perm = resolvePermissionStatus()
        if (perm != CameraPermissionStatus.GRANTED) {
            throw SecurityException("Camera permission not granted ($perm)")
        }
        val activity = activityRef?.get()
            ?: throw IllegalStateException("attachActivity() required before scanOnce()")
        suspendCancellableCoroutine { cont ->
            scanContinuation?.resumeWithException(
                CancellationException("Superseded by a new scanOnce()"),
            )
            scanContinuation = cont
            cont.invokeOnCancellation {
                if (scanContinuation === cont) scanContinuation = null
            }
            @Suppress("DEPRECATION")
            activity.startActivityForResult(
                Intent(activity, QrScanActivity::class.java),
                REQUEST_SCAN,
            )
        }
    }

    override suspend fun cancel() = withContext(Dispatchers.Main) {
        val cont = scanContinuation
        scanContinuation = null
        cont?.resumeWithException(CancellationException("QR scan cancelled"))
        Unit
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
        const val REQUEST_CAMERA: Int = 0x51_52 // "QR"
        const val REQUEST_SCAN: Int = 0x51_53 // "QS"
    }
}

/** Local alias so we do not depend on kotlinx.coroutines.CancellationException name clash. */
private typealias CancellationException = java.util.concurrent.CancellationException
