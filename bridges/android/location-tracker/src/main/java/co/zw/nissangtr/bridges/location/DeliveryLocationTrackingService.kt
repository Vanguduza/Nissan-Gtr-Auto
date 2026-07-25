package co.zw.nissangtr.bridges.location

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat

/**
 * Foreground service required for continuous delivery GPS while the app may
 * background. Does not read location itself — [FusedLocationGpsBridge] owns
 * FusedLocationProvider callbacks; this service only satisfies OS FGS rules
 * and keeps a persistent low-priority notification.
 *
 * Host app should provide a tap-target via [EXTRA_CONTENT_INTENT] if desired.
 */
class DeliveryLocationTrackingService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureChannel()
        val contentIntent = intent
            ?.getParcelableExtraCompat(EXTRA_CONTENT_INTENT, PendingIntent::class.java)
        val cadenceName = intent?.getStringExtra(EXTRA_CADENCE)
        val cadence = cadenceName?.let {
            runCatching { GpsWatchCadence.valueOf(it) }.getOrNull()
        }

        val notification = buildNotification(this, contentIntent, cadence)

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
        ensureChannel(this)
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
        const val EXTRA_CADENCE: String =
            "co.zw.nissangtr.bridges.location.EXTRA_CADENCE"

        fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.gtr_location_tracking_channel),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = context.getString(R.string.gtr_location_tracking_channel_desc)
                setShowBadge(false)
                setSound(null, null)
            }
            manager.createNotificationChannel(channel)
        }

        fun buildNotification(
            context: Context,
            contentIntent: PendingIntent?,
            cadence: GpsWatchCadence?,
        ): Notification {
            val body = when (cadence) {
                GpsWatchCadence.IDLE ->
                    context.getString(R.string.gtr_location_tracking_body_idle)
                GpsWatchCadence.MOVING, GpsWatchCadence.AUTO, null ->
                    context.getString(R.string.gtr_location_tracking_body)
            }
            return NotificationCompat.Builder(context, CHANNEL_ID)
                .setContentTitle(context.getString(R.string.gtr_location_tracking_title))
                .setContentText(body)
                .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setContentIntent(contentIntent)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                .build()
        }

        /** Refresh notification text when AUTO cadence flips moving ↔ idle. */
        fun updateNotificationCadence(context: Context, cadence: GpsWatchCadence) {
            ensureChannel(context)
            val notification = buildNotification(context, contentIntent = null, cadence = cadence)
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        }
    }
}
