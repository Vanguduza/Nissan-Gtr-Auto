package co.zw.nissangtr.customer.prefs

import android.content.Context
import co.zw.nissangtr.customer.visual.CustomerStyle

/** Light / dark mode for whichever style is chosen. */
enum class ThemeMode {
    System,
    Light,
    Dark,
}

/**
 * App-local prefs (app style, light/dark mode, push opt-in). No FCM wiring — push toggle is honest local state.
 */
class CustomerPrefs(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Illustrated (premium dark, default) or the POS counter look. */
    var customerStyle: CustomerStyle
        get() = when (prefs.getString(KEY_STYLE, null)) {
            CustomerStyle.Pos.name -> CustomerStyle.Pos
            else -> CustomerStyle.Illustrated
        }
        set(value) {
            prefs.edit().putString(KEY_STYLE, value.name).apply()
        }

    /** Dark by default so a first launch shows the locked illustrated dark storefront. */
    var themeMode: ThemeMode
        get() = when (prefs.getString(KEY_THEME, ThemeMode.Dark.name)) {
            ThemeMode.Light.name -> ThemeMode.Light
            ThemeMode.System.name -> ThemeMode.System
            else -> ThemeMode.Dark
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
        private const val KEY_STYLE = "customer_style"
        private const val KEY_THEME = "theme_mode"
        private const val KEY_PUSH = "receive_push"
    }
}
