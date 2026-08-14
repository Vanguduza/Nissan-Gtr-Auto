package co.zw.nissangtr.catalogapk.worker

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import co.zw.nissangtr.catalogapk.data.prefs.AppPreferences
import kotlinx.coroutines.flow.first

data class CrawlGateResult(
    val allowed: Boolean,
    val reasons: List<String>,
    val effectiveMaxSlots: Int,
)

/**
 * Charge / unmetered Wi‑Fi / thermal gates before crawl (plan self-healing section).
 */
object CrawlGates {
    suspend fun evaluate(context: Context, prefs: AppPreferences): CrawlGateResult {
        val reasons = mutableListOf<String>()
        val requireCharge = prefs.requireCharging.first()
        val requireWifi = prefs.requireUnmeteredWifi.first()
        val override = prefs.debugBypassGates.first()
        var slots = prefs.maxConcurrentJobs.first().coerceIn(1, 2)

        if (!override) {
            if (requireCharge && !isCharging(context)) {
                reasons += "Not charging (plug in or enable debug bypass)"
            }
            if (requireWifi && !isUnmeteredWifi(context)) {
                reasons += "Need unmetered Wi‑Fi"
            }
        }

        val thermal = thermalStatus(context)
        if (thermal >= PowerManager.THERMAL_STATUS_SEVERE) {
            slots = 0
            reasons += "Thermal severe — crawl blocked"
        } else if (thermal >= PowerManager.THERMAL_STATUS_MODERATE) {
            slots = 1
            reasons += "Thermal elevated — slots capped to 1"
        }

        val allowed = reasons.none {
            it.startsWith("Not charging") ||
                it.startsWith("Need unmetered") ||
                it.startsWith("Thermal severe")
        }
        return CrawlGateResult(allowed = allowed || override, reasons = reasons, effectiveMaxSlots = if (override) slots.coerceAtLeast(1) else slots)
    }

    fun isCharging(context: Context): Boolean {
        val bm = context.getSystemService(BatteryManager::class.java) ?: return true
        return bm.isCharging ||
            bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_STATUS).let { status ->
                status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL
            }
    }

    fun isUnmeteredWifi(context: Context): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        val wifi = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        val unmetered = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) ||
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_TEMPORARILY_NOT_METERED)
        return wifi && unmetered && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    fun thermalStatus(context: Context): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return PowerManager.THERMAL_STATUS_NONE
        val pm = context.getSystemService(PowerManager::class.java) ?: return PowerManager.THERMAL_STATUS_NONE
        return pm.currentThermalStatus
    }
}
