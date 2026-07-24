package co.zw.nissangtr.bridges.location

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat

/**
 * Foreground service required for continuous delivery GPS while the app may
 * background. Does not read location itself — [FusedLocationGpsBridge] owns
 * FusedLocationProvider callbacks; this service only satisfies OS FGS rules.
 *
 * Host app should provide a tap-target via [EXTRA_CONTENT_INTENT] if desired;
 * otherwise a silent channel notification is shown.
 */
class DeliveryLocationTrackingService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureChannel()
        val contentIntent = intent
            ?.getParcelableExtraCompat(EXTRA_CONTENT_INTENT, PendingIntent::class.java)

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.gtr_location_tracking_title))
            .setContentText(getString(R.string.gtr_location_tracking_body))
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentIntent)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
            )
        } else {
            @Suppress("DEPRECATION")
            startForeground(NOTIFICATION_ID, notification)
        }
        return START_STICKY
    }

    override fun onDestroy() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.gtr_location_tracking_channel),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.gtr_location_tracking_channel_desc)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    private fun <T> Intent.getParcelableExtraCompat(key: String, clazz: Class<T>): T? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(key, clazz)
        } else {
            @Suppress("DEPRECATION", "UNCHECKED_CAST")
            getParcelableExtra(key) as? T
        }
    }

    companion object {
        const val CHANNEL_ID: String = "gtr_delivery_location"
        const val NOTIFICATION_ID: Int = 0x47_54_52 // "GTR"
        const val EXTRA_CONTENT_INTENT: String =
            "co.zw.nissangtr.bridges.location.EXTRA_CONTENT_INTENT"
    }
}
