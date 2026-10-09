package co.zw.nissangtr.management.kiosk

import android.app.Activity
import android.app.ActivityManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.UserManager
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
            reassertDedicatedKioskPolicy()
            if (!activity.isInLockTaskModeCompat()) activity.startLockTask()
        } catch (e: Exception) {
            Log.w(TAG, "Lock Task enter skipped: ${e.message}")
        }
    }

    /**
     * Device Owner hardening for a dedicated till. Makes this activity the persistent HOME,
     * disables keyguard/status-bar escape surfaces and reapplies Lock Task allowlisting.
     * Safe no-op when the tablet has not yet been provisioned as Device Owner.
     */
    fun reassertDedicatedKioskPolicy() {
        if (!tabletKiosk || !isDeviceOwner()) return
        val admin = adminComponent
        dpm.setLockTaskPackages(admin, arrayOf(activity.packageName))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            dpm.setLockTaskFeatures(admin, DevicePolicyManager.LOCK_TASK_FEATURE_NONE)
        }
        runCatching { dpm.setStatusBarDisabled(admin, true) }
        runCatching { dpm.setKeyguardDisabled(admin, true) }

        val homeFilter = IntentFilter(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            addCategory(Intent.CATEGORY_DEFAULT)
        }
        dpm.addPersistentPreferredActivity(
            admin,
            homeFilter,
            ComponentName(activity, activity::class.java),
        )

        listOf(
            UserManager.DISALLOW_SAFE_BOOT,
            UserManager.DISALLOW_FACTORY_RESET,
            UserManager.DISALLOW_ADD_USER,
            UserManager.DISALLOW_REMOVE_USER,
            UserManager.DISALLOW_MOUNT_PHYSICAL_MEDIA,
        ).forEach { restriction ->
            runCatching { dpm.addUserRestriction(admin, restriction) }
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
