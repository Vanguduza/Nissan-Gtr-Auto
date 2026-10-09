package co.zw.nissangtr.management.kiosk

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Tablet-only: after boot, relaunch the persistent-HOME MainActivity directly into the
 * staff authentication boundary so the stock launcher/Recents are never the operational UI.
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
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TASK or
                    Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED or
                    Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS,
            )
        }
        runCatching { context.startActivity(launch) }
            .onFailure { Log.w(TAG, "Boot launch failed: ${it.message}") }
    }

    companion object {
        private const val TAG = "GtrKioskBoot"
    }
}
