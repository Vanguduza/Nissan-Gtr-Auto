package co.zw.nissangtr.management.kiosk

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Tablet-only: after BOOT_COMPLETED, relaunch MainActivity so staff never sit on the stock launcher.
 * Registered only in the tablet source set / manifest merge.
 */
class KioskBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED &&
            intent?.action != Intent.ACTION_LOCKED_BOOT_COMPLETED
        ) {
            return
        }
        Log.i(TAG, "Boot completed — launching kiosk activity")
        val launch = Intent().apply {
            setClassName(
                context.packageName,
                "co.zw.nissangtr.management.MainActivity",
            )
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        runCatching { context.startActivity(launch) }
            .onFailure { Log.w(TAG, "Boot launch failed: ${it.message}") }
    }

    companion object {
        private const val TAG = "GtrKioskBoot"
    }
}
