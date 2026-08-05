package co.zw.nissangtr.management.kiosk

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Device Owner / Device Admin hook for the **tablet** APK only.
 * Provisioned via:
 * `adb shell dpm set-device-owner co.zw.nissangtr.management.tablet/co.zw.nissangtr.management.kiosk.KioskDeviceAdminReceiver`
 * (see docs/guides/android-management-kiosk-device-owner.md).
 *
 * Phone flavor must not register this receiver.
 */
class KioskDeviceAdminReceiver : DeviceAdminReceiver() {
    override fun onEnabled(context: Context, intent: Intent) {
        Log.i(TAG, "Device admin enabled")
    }

    override fun onDisabled(context: Context, intent: Intent) {
        Log.i(TAG, "Device admin disabled")
    }

    companion object {
        private const val TAG = "GtrKioskAdmin"
    }
}
