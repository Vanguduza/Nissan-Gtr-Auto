package co.zw.nissangtr.pos.kiosk

import java.io.File

enum class HardeningPathKind {
    PathBPrimaryLikely,
    PathAFallbackActive,
    RootedNotProvisioned,
    NotProvisioned,
    Disabled,
}

data class HardeningPathStatus(
    val kind: HardeningPathKind,
    val label: String,
    val detail: String,
)

object HardeningPathDetector {
    fun detect(
        kioskEnabled: Boolean,
        isDeviceOwner: Boolean,
        rootedHeuristic: Boolean = looksRooted(),
    ): HardeningPathStatus = when {
        !kioskEnabled -> HardeningPathStatus(
            kind = HardeningPathKind.Disabled,
            label = "Kiosk off",
            detail = "Lock Task disabled for this build.",
        )
        rootedHeuristic && isDeviceOwner -> HardeningPathStatus(
            kind = HardeningPathKind.PathBPrimaryLikely,
            label = "Path B primary (likely)",
            detail = "Root indicators + Device Owner. Lock Task still required.",
        )
        isDeviceOwner -> HardeningPathStatus(
            kind = HardeningPathKind.PathAFallbackActive,
            label = "Path A (DO + Lock Task)",
            detail = "Device Owner active. See kiosk Device Owner runbook.",
        )
        rootedHeuristic -> HardeningPathStatus(
            kind = HardeningPathKind.RootedNotProvisioned,
            label = "Root detected — DO not set",
            detail = "Provision Device Owner on co.zw.nissangtr.pos before fleet lockdown.",
        )
        else -> HardeningPathStatus(
            kind = HardeningPathKind.NotProvisioned,
            label = "Not Device Owner",
            detail = "See docs/guides/android-management-kiosk-device-owner.md (same ops for POS APK).",
        )
    }

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
