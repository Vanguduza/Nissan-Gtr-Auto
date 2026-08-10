package co.zw.nissangtr.bridges.location

import kotlin.coroutines.Continuation
import kotlin.coroutines.resume

/**
 * Holds an in-flight [FusedLocationGpsBridge.requestLocationPermission] continuation
 * so the host Activity can complete it from onRequestPermissionsResult / Activity Result.
 */
internal object LocationPermissionRelay {
    @Volatile
    private var pending: Continuation<LocationPermissionStatus>? = null

    fun arm(continuation: Continuation<LocationPermissionStatus>) {
        pending?.resume(LocationPermissionStatus.DENIED)
        pending = continuation
    }

    fun complete(status: LocationPermissionStatus) {
        val c = pending ?: return
        pending = null
        c.resume(status)
    }

    fun cancel() {
        pending = null
    }
}
