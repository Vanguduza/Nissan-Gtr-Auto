package co.zw.nissangtr.pos.kiosk

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Device Owner / Device Admin for the POS APK.
 * `adb shell dpm set-device-owner co.zw.nissangtr.pos/.kiosk.KioskDeviceAdminReceiver`
 */
class KioskDeviceAdminReceiver : DeviceAdminReceiver() {
    override fun onEnabled(context: Context, intent: Intent) {
        Log.i(TAG, "Device admin enabled")
    }

    override fun onDisabled(context: Context, intent: Intent) {
        Log.i(TAG, "Device admin disabled")
    }

    companion object {
        private const val TAG = "GtrPosKioskAdmin"
    }
}
