package co.zw.nissangtr.bridges.escpos

import kotlin.coroutines.Continuation
import kotlin.coroutines.resume

/**
 * Holds an in-flight Bluetooth permission request continuation so the host
 * Activity can complete it from onRequestPermissionsResult.
 */
internal object BluetoothPermissionRelay {
    @Volatile
    private var pending: Continuation<BluetoothPermissionStatus>? = null

    fun arm(continuation: Continuation<BluetoothPermissionStatus>) {
        pending?.resume(BluetoothPermissionStatus.DENIED)
        pending = continuation
    }

    fun complete(status: BluetoothPermissionStatus) {
        val c = pending ?: return
        pending = null
        c.resume(status)
    }

    fun cancel() {
        pending = null
    }
}
