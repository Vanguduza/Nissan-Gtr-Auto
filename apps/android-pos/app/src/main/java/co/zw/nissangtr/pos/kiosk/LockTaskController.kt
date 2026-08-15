package co.zw.nissangtr.pos.kiosk

import android.app.Activity
import android.app.ActivityManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.util.Log

/**
 * Lock Task enter/exit for counter POS tablets.
 * Adapted from android-management-legacy/feature/kiosk — same Device Owner SoR.
 * Provision: `adb shell dpm set-device-owner co.zw.nissangtr.pos/.kiosk.KioskDeviceAdminReceiver`
 * See docs/guides/android-management-kiosk-device-owner.md (ops pattern applies to this APK).
 */
class LockTaskController(
    private val activity: Activity,
    private val kioskEnabled: Boolean = true,
) {
    private val dpm: DevicePolicyManager
        get() = activity.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

    private val adminComponent: ComponentName
        get() = ComponentName(activity, KioskDeviceAdminReceiver::class.java)

    fun isDeviceOwner(): Boolean =
        kioskEnabled && dpm.isDeviceOwnerApp(activity.packageName)

    fun enterLockTaskIfAllowed() {
        if (!kioskEnabled) return
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
        if (!kioskEnabled) return
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
            kioskEnabled = kioskEnabled,
            isDeviceOwner = isDeviceOwner(),
        )

    private fun Activity.isInLockTaskModeCompat(): Boolean {
        val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        return am.lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE
    }

    companion object {
        private const val TAG = "GtrPosLockTask"
    }
}
