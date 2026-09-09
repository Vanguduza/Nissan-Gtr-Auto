package co.zw.nissangtr.management.kiosk

import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import co.zw.nissangtr.ui.shop.ShopPrimaryButton
import co.zw.nissangtr.ui.shop.ShopSecondaryButton
import co.zw.nissangtr.ui.shop.ShopStaffPanel
import co.zw.nissangtr.ui.shop.ShopStaffScreen
import kotlinx.coroutines.launch

/**
 * Elevated Device Admin console (tablet SoR).
 * Engine audio default OFF; idle minutes override 1–15; Path B/Magisk status (no auto-flash).
 */
@Composable
fun DeviceAdminConsoleScreen(
    prefs: KioskDevicePrefs,
    lockTask: LockTaskController,
    tabletKiosk: Boolean,
    printerDiagnostics: String = "Printer bridge attached — use POS/Bins for live print tests.",
    scannerDiagnostics: String = "QR scanner bridge attached — use POS/Warehouse receive for live scans.",
    diagnosticsBusy: Boolean = false,
    onRefreshDiagnostics: (() -> Unit)? = null,
    onBack: () -> Unit,
    onExitLockTask: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val idleMinutes by prefs.idleMinutes.collectAsState(
        initial = KioskDevicePrefs.DEFAULT_IDLE_MINUTES,
    )
    val path = lockTask.hardeningStatus()

    ShopStaffScreen(
        title = "Device Admin",
        subtitle = if (tabletKiosk) "Kiosk maintenance" else "Portable management",
        onBack = onBack,
    ) {
        ShopStaffPanel(title = "Idle lock (minutes)") {
            Text(
                "Default ${KioskDevicePrefs.DEFAULT_IDLE_MINUTES} min. Override " +
                    "${KioskDevicePrefs.MIN_IDLE_MINUTES}–${KioskDevicePrefs.MAX_IDLE_MINUTES}. " +
                    "In-app reauth only — never launcher.",
                style = MaterialTheme.typography.bodySmall,
            )
            Text("Current: $idleMinutes min", style = MaterialTheme.typography.bodyMedium)
            ShopSecondaryButton(
                label = "− 1 minute",
                onClick = { scope.launch { prefs.setIdleMinutes(idleMinutes - 1) } },
                enabled = idleMinutes > KioskDevicePrefs.MIN_IDLE_MINUTES,
            )
            ShopSecondaryButton(
                label = "+ 1 minute",
                onClick = { scope.launch { prefs.setIdleMinutes(idleMinutes + 1) } },
                enabled = idleMinutes < KioskDevicePrefs.MAX_IDLE_MINUTES,
            )
        }

        ShopStaffPanel(title = "Hardening path") {
            Text(path.label, style = MaterialTheme.typography.bodyMedium)
            Text(path.detail, style = MaterialTheme.typography.bodySmall)
            if (tabletKiosk) {
                ShopPrimaryButton(
                    label = "Re-assert dedicated kiosk policy",
                    onClick = {
                        KioskAuditLog.append(context, "reassert_kiosk_policy")
                        lockTask.reassertDedicatedKioskPolicy()
                        lockTask.enterLockTaskIfAllowed()
                    },
                )
            }
            Text(
                "Device Owner + Lock Task is authoritative. Root/Magisk may provide OEM-level boot branding only; " +
                    "the app itself never plays a startup splash before staff login. " +
                    "See docs/guides/android-management-kiosk-device-owner.md",
                style = MaterialTheme.typography.bodySmall,
            )
        }

        ShopStaffPanel(title = "Bridge diagnostics") {
            Text(printerDiagnostics, style = MaterialTheme.typography.bodySmall)
            Text(scannerDiagnostics, style = MaterialTheme.typography.bodySmall)
            if (onRefreshDiagnostics != null) {
                ShopSecondaryButton(
                    label = if (diagnosticsBusy) "Refreshing…" else "Refresh printer / scanner status",
                    onClick = {
                        KioskAuditLog.append(context, "refresh_bridge_diagnostics")
                        onRefreshDiagnostics()
                    },
                    enabled = !diagnosticsBusy,
                )
            }
        }

        if (tabletKiosk) {
            ShopStaffPanel(title = "Tablet settings") {
                ShopPrimaryButton(
                    label = "Wi‑Fi settings",
                    onClick = {
                        KioskAuditLog.append(context, "open_wifi_settings")
                        lockTask.exitLockTask()
                        context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
                    },
                )
                ShopPrimaryButton(
                    label = "Bluetooth settings",
                    onClick = {
                        KioskAuditLog.append(context, "open_bluetooth_settings")
                        lockTask.exitLockTask()
                        context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                    },
                )
                ShopPrimaryButton(
                    label = "Print services / vendor drivers",
                    onClick = {
                        KioskAuditLog.append(context, "open_print_services")
                        lockTask.exitLockTask()
                        context.startActivity(Intent(Settings.ACTION_PRINT_SETTINGS))
                    },
                )
                ShopSecondaryButton(
                    label = "Exit Lock Task (audited locally)",
                    onClick = {
                        KioskAuditLog.append(context, "exit_lock_task")
                        onExitLockTask()
                        lockTask.exitLockTask()
                    },
                )
                ShopSecondaryButton(
                    label = "Reboot device (if permitted)",
                    onClick = {
                        KioskAuditLog.append(context, "reboot_requested")
                        tryReboot(context)
                    },
                )
            }
        }

        ShopStaffPanel(title = "Local audit (tail)") {
            Text(
                KioskAuditLog.readTail(context),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

private fun tryReboot(context: Context) {
    try {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        pm.reboot(null)
    } catch (e: Exception) {
        Log.w("GtrDeviceAdmin", "Reboot not permitted: ${e.message}")
        KioskAuditLog.append(context, "reboot_denied:${e.message}")
    }
}
