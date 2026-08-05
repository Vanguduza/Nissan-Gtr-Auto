package co.zw.nissangtr.management.kiosk

import android.content.Context
import java.io.File

/**
 * Path B (Magisk) is primary for rooted CN tablets; Path A is DO+Lock Task fallback.
 * Heuristic only — never auto-flashes Magisk.
 */
enum class HardeningPathKind {
    PhoneNonKiosk,
    PathBPrimaryLikely,
    PathAFallbackActive,
    RootedNotProvisioned,
    NotProvisioned,
}

data class HardeningPathStatus(
    val kind: HardeningPathKind,
    val label: String,
    val detail: String,
)

object HardeningPathDetector {
    fun detect(
        tabletKiosk: Boolean,
        isDeviceOwner: Boolean,
        rootedHeuristic: Boolean = looksRooted(),
    ): HardeningPathStatus = when {
        !tabletKiosk -> HardeningPathStatus(
            kind = HardeningPathKind.PhoneNonKiosk,
            label = "Phone APK",
            detail = "No Device Owner / Lock Task / Magisk ownership on portable management APK.",
        )
        rootedHeuristic && isDeviceOwner -> HardeningPathStatus(
            kind = HardeningPathKind.PathBPrimaryLikely,
            label = "Path B primary (likely)",
            detail = "Root indicators present + Device Owner active. Magisk boot branding is ops-managed; Lock Task still required.",
        )
        isDeviceOwner -> HardeningPathStatus(
            kind = HardeningPathKind.PathAFallbackActive,
            label = "Path A fallback (DO + Lock Task)",
            detail = "Device Owner active without root indicators. Firmware boot logo stays OEM; app splash still required.",
        )
        rootedHeuristic -> HardeningPathStatus(
            kind = HardeningPathKind.RootedNotProvisioned,
            label = "Path B root detected — DO not set",
            detail = "Provision Device Owner on the tablet package before fleet lockdown. See kiosk Device Owner runbook.",
        )
        else -> HardeningPathStatus(
            kind = HardeningPathKind.NotProvisioned,
            label = "Not Device Owner",
            detail = "Provision Path B (primary CN) or Path A (fallback) per docs/guides/android-management-kiosk-device-owner.md",
        )
    }

    /** Best-effort su/Magisk path probe — informational only. */
    fun looksRooted(): Boolean {
        val markers = listOf(
            "/system/bin/su",
            "/system/xbin/su",
            "/sbin/su",
            "/data/adb/magisk",
            "/sbin/.magisk",
        )
        return markers.any { File(it).exists() }
    }
}

/** Local append-only maintenance audit (device file — not server SoR). */
object KioskAuditLog {
    private const val FILE = "kiosk_maintenance_audit.log"

    fun append(context: Context, action: String) {
        runCatching {
            val line = "${System.currentTimeMillis()}\t$action\n"
            context.applicationContext.openFileOutput(FILE, Context.MODE_APPEND).use {
                it.write(line.toByteArray(Charsets.UTF_8))
            }
        }
    }

    fun readTail(context: Context, maxChars: Int = 2_000): String =
        runCatching {
            val f = File(context.applicationContext.filesDir, FILE)
            if (!f.exists()) return@runCatching "(no local audit entries)"
            val text = f.readText(Charsets.UTF_8)
            if (text.length <= maxChars) text else text.takeLast(maxChars)
        }.getOrDefault("(audit unavailable)")
}
