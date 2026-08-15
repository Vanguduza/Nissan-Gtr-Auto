package co.zw.nissangtr.pos.api

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * §16.4 / §16.7 FitmentRules matrix.
 * R35+VR38DETT FITS; Y62-only → NO_FIT; empty chassis → VERIFY; no latch → VERIFY.
 */
class FitmentRulesTest {

    private val r35Latch = VehicleLatch(
        chassisCode = "R35",
        engineCode = "VR38DETT",
    )

    private val padR35 = TillItem(
        oemPartNumber = "40206-JF00A",
        description = "Pad",
        currency = "USD",
        unitPrice = 190.0,
        saleableQty = 4,
        chassisCodes = listOf("R35"),
        engineCodes = listOf("VR38DETT"),
    )

    private val padY62 = TillItem(
        oemPartNumber = "41060-1LA0A",
        description = "Y62 pad",
        currency = "USD",
        unitPrice = 98.0,
        saleableQty = 6,
        chassisCodes = listOf("Y62"),
        engineCodes = emptyList(),
    )

    private val noFitment = TillItem(
        oemPartNumber = "16546-JF00A",
        description = "Filter",
        currency = "USD",
        unitPrice = 32.0,
        saleableQty = 12,
        chassisCodes = emptyList(),
        engineCodes = emptyList(),
    )

    @Test
    fun r35Vr38dett_fits() {
        assertEquals(FitmentBadge.FITS, FitmentRules.badge(padR35, r35Latch))
    }

    @Test
    fun y62Only_noFit() {
        assertEquals(FitmentBadge.NO_FIT, FitmentRules.badge(padY62, r35Latch))
    }

    @Test
    fun emptyChassisCodes_verify() {
        assertEquals(FitmentBadge.VERIFY, FitmentRules.badge(noFitment, r35Latch))
    }

    @Test
    fun noLatch_verify() {
        assertEquals(FitmentBadge.VERIFY, FitmentRules.badge(padR35, null))
    }

    @Test
    fun blankLatchChassis_verify() {
        assertEquals(
            FitmentBadge.VERIFY,
            FitmentRules.badge(padR35, VehicleLatch(chassisCode = "  ")),
        )
    }

    @Test
    fun normalize_trimUppercase() {
        val latch = VehicleLatch(chassisCode = " r35 ", engineCode = " vr38dett ")
        val item = padR35.copy(
            chassisCodes = listOf(" r35 "),
            engineCodes = listOf(" vr38dett "),
        )
        assertEquals(FitmentBadge.FITS, FitmentRules.badge(item, latch))
    }

    @Test
    fun chassisMatch_engineMismatch_noFit() {
        val item = padR35.copy(engineCodes = listOf("VQ35DE"))
        assertEquals(FitmentBadge.NO_FIT, FitmentRules.badge(item, r35Latch))
    }

    @Test
    fun chassisMatch_noEngineOnItem_fits() {
        val item = padR35.copy(engineCodes = emptyList())
        assertEquals(FitmentBadge.FITS, FitmentRules.badge(item, r35Latch))
    }
}
