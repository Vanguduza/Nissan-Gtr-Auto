package co.zw.nissangtr.pos.api

/**
 * Data-driven chassis shortcut chips — latch + shop-stock filter.
 * Production list comes from [PosClient.listChassisShortcuts] (vehicle_master /
 * catalog variants). Never hard-code an R35-only production roster.
 *
 * Till chips are **vehicle chassis** (R35, Y62, …), not part OEMs / numeric junk
 * from a raw alphabetical dump of vehicle_master.
 */
object ChassisChipLatch {
    /** High-volume shop chassis bubbled to the front when present in the data set. */
    val PREFERRED_ORDER: List<String> = listOf(
        "R35", "Y62", "D40", "D22", "T30", "T31", "V37", "J10", "B17", "C11",
    )

    private val CHASSIS_PATTERN = Regex("^[A-Z]{1,3}[0-9]{1,3}[A-Z0-9]{0,2}$")

    fun latchFromChip(
        chassisCode: String,
        engineCode: String? = null,
        modelVariant: String? = null,
    ): VehicleLatch? {
        val chassis = chassisCode.trim().uppercase()
        if (chassis.isEmpty()) return null
        return VehicleLatch(
            chassisCode = chassis,
            engineCode = engineCode?.trim()?.uppercase()?.takeIf { it.isNotEmpty() },
            modelVariant = modelVariant?.trim()?.takeIf { it.isNotEmpty() },
        )
    }

    /** Distinct, trimmed, uppercased chassis codes — drop blanks. */
    fun normalizeChipList(raw: List<String>): List<String> =
        raw.map { it.trim().uppercase() }
            .filter { it.isNotEmpty() }
            .distinct()

    /**
     * Till shortcut filter: reject pure digits / overlong codes that look like
     * part numbers; keep short letter+digit chassis; prefer [PREFERRED_ORDER].
     */
    fun forTillShortcuts(raw: List<String>, limit: Int = 16): List<String> {
        val normalized = normalizeChipList(raw).filter { isPlausibleChassis(it) }
        if (normalized.isEmpty()) return emptyList()
        val preferred = PREFERRED_ORDER.filter { it in normalized }
        val rest = normalized.filterNot { it in preferred }.sorted()
        return (preferred + rest).take(limit.coerceAtLeast(1))
    }

    fun isPlausibleChassis(code: String): Boolean {
        val c = code.trim().uppercase()
        if (c.length !in 2..6) return false
        if (c.all { it.isDigit() }) return false
        if (c.all { it.isLetter() }) return false
        return CHASSIS_PATTERN.matches(c)
    }
}

data class ChassisShortcut(
    val chassisCode: String,
    val label: String = chassisCode,
    val engineCode: String? = null,
    val modelVariant: String? = null,
)
