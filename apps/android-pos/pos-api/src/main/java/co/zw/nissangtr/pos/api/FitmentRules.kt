package co.zw.nissangtr.pos.api

import co.zw.nissangtr.pos.api.FitmentBadge.FITS
import co.zw.nissangtr.pos.api.FitmentBadge.NO_FIT
import co.zw.nissangtr.pos.api.FitmentBadge.VERIFY

/**
 * Client fitment SoT (§16.4). Year not in v1.
 * Normalize: trim + uppercase. Empty chassis_codes or no/blank latch → VERIFY;
 * chassis mismatch → NO_FIT; chassis match (+ engine if both present) → FITS.
 */
object FitmentRules {

    fun normalize(code: String?): String? =
        code?.trim()?.uppercase()?.takeIf { it.isNotEmpty() }

    fun badge(item: TillItem, latch: VehicleLatch?): FitmentBadge {
        val latchChassis = normalize(latch?.chassisCode)
        if (latchChassis == null) return VERIFY

        val chassisCodes = item.chassisCodes.mapNotNull { normalize(it) }
        if (chassisCodes.isEmpty()) return VERIFY

        val chassisOk = chassisCodes.any { it == latchChassis }
        if (!chassisOk) return NO_FIT

        val latchEngine = normalize(latch?.engineCode)
        val engineCodes = item.engineCodes.mapNotNull { normalize(it) }
        if (latchEngine == null || engineCodes.isEmpty()) return FITS

        return if (engineCodes.any { it == latchEngine }) FITS else NO_FIT
    }
}
