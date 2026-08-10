package co.zw.nissangtr.bridges.qr

import kotlin.coroutines.Continuation
import kotlin.coroutines.resume

/**
 * Holds an in-flight [CameraxQrScannerBridge.requestCameraPermission] continuation
 * so the host Activity can complete it from onRequestPermissionsResult.
 */
internal object CameraPermissionRelay {
    @Volatile
    private var pending: Continuation<CameraPermissionStatus>? = null

    fun arm(continuation: Continuation<CameraPermissionStatus>) {
        pending?.resume(CameraPermissionStatus.DENIED)
        pending = continuation
    }

    fun complete(status: CameraPermissionStatus) {
        val c = pending ?: return
        pending = null
        c.resume(status)
    }

    fun cancel() {
        pending = null
    }
}
