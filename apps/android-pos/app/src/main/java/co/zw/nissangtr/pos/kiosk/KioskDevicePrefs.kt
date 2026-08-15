package co.zw.nissangtr.pos.kiosk

import android.content.Context
import android.content.SharedPreferences

/**
 * Per-device POS kiosk prefs. Idle minutes compose with [co.zw.nissangtr.pos.till.IdleLockController].
 */
class KioskDevicePrefs(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var idleMinutes: Int
        get() = clampIdleMinutes(prefs.getInt(KEY_IDLE, DEFAULT_IDLE_MINUTES))
        set(value) = prefs.edit().putInt(KEY_IDLE, clampIdleMinutes(value)).apply()

    var lockTaskOnLaunch: Boolean
        get() = prefs.getBoolean(KEY_LOCK_TASK, true)
        set(value) = prefs.edit().putBoolean(KEY_LOCK_TASK, value).apply()

    companion object {
        const val DEFAULT_IDLE_MINUTES: Int = 5
        const val MIN_IDLE_MINUTES: Int = 1
        const val MAX_IDLE_MINUTES: Int = 15

        private const val PREFS = "gtr_pos_kiosk_prefs"
        private const val KEY_IDLE = "idle_minutes"
        private const val KEY_LOCK_TASK = "lock_task_on_launch"

        fun clampIdleMinutes(minutes: Int): Int =
            minutes.coerceIn(MIN_IDLE_MINUTES, MAX_IDLE_MINUTES)
    }
}
