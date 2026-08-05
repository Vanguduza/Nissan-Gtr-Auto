package co.zw.nissangtr.customer.prefs

import android.content.Context

enum class ThemeMode {
    System,
    Light,
    Dark,
}

/**
 * App-local prefs (theme + push opt-in). No FCM wiring — push toggle is honest local state.
 */
class CustomerPrefs(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var themeMode: ThemeMode
        get() = when (prefs.getString(KEY_THEME, ThemeMode.System.name)) {
            ThemeMode.Light.name -> ThemeMode.Light
            ThemeMode.Dark.name -> ThemeMode.Dark
            else -> ThemeMode.System
        }
        set(value) {
            prefs.edit().putString(KEY_THEME, value.name).apply()
        }

    /** Local preference only — push delivery requires FCM backend (not wired). */
    var receivePush: Boolean
        get() = prefs.getBoolean(KEY_PUSH, false)
        set(value) {
            prefs.edit().putBoolean(KEY_PUSH, value).apply()
        }

    companion object {
        private const val PREFS = "gtr_customer_prefs"
        private const val KEY_THEME = "theme_mode"
        private const val KEY_PUSH = "receive_push"
    }
}
