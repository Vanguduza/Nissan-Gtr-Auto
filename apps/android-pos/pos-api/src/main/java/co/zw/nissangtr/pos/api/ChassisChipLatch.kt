package co.zw.nissangtr.pos.api

/**
 * Data-driven chassis shortcut chips — latch + shop-stock filter.
 * Production list comes from [PosClient.listChassisShortcuts] (vehicle_master /
 * catalog variants). Never hard-code an R35-only production roster.
 */
object ChassisChipLatch {
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
}

data class ChassisShortcut(
    val chassisCode: String,
    val label: String = chassisCode,
    val engineCode: String? = null,
    val modelVariant: String? = null,
)
