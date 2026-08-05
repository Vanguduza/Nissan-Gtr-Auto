package co.zw.nissangtr.management.kiosk

import android.app.Activity
import android.app.ActivityManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.util.Log

/**
 * Lock Task enter/exit for tablet kiosk. No-ops when not Device Owner or when
 * [tabletKiosk] is false (phone APK).
 */
class LockTaskController(
    private val activity: Activity,
    private val tabletKiosk: Boolean,
) {
    private val dpm: DevicePolicyManager
        get() = activity.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

    private val adminComponent: ComponentName
        get() = ComponentName(activity, KioskDeviceAdminReceiver::class.java)

    fun isDeviceOwner(): Boolean =
        tabletKiosk && dpm.isDeviceOwnerApp(activity.packageName)

    fun enterLockTaskIfAllowed() {
        if (!tabletKiosk) return
        try {
            if (isDeviceOwner()) {
                dpm.setLockTaskPackages(adminComponent, arrayOf(activity.packageName))
            }
            if (!activity.isInLockTaskModeCompat()) {
                activity.startLockTask()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Lock Task enter skipped: ${e.message}")
        }
    }

    fun exitLockTask() {
        if (!tabletKiosk) return
        try {
            if (activity.isInLockTaskModeCompat()) {
                activity.stopLockTask()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Lock Task exit failed: ${e.message}")
        }
    }

    fun hardeningStatus(): HardeningPathStatus =
        HardeningPathDetector.detect(
            tabletKiosk = tabletKiosk,
            isDeviceOwner = isDeviceOwner(),
        )

    fun hardeningPathLabel(): String {
        val s = hardeningStatus()
        return "${s.label} — ${s.detail}"
    }

    private fun Activity.isInLockTaskModeCompat(): Boolean {
        val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        return am.lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE
    }

    companion object {
        private const val TAG = "GtrLockTask"
    }
}
