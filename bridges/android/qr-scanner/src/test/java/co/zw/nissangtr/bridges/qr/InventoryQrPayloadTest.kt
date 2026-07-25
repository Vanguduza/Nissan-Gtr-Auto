package co.zw.nissangtr.bridges.qr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Contract-aligned payload helpers (no device / CameraX required).
 * Device tests: grant camera → scanOnce on hardware with a gtr://part sticker.
 */
class InventoryQrPayloadTest {

    @Test
    fun buildAndParse_roundTrip() {
        val payload = buildInventoryQrPayload(
            oemPartNumber = "21410-JF00A",
            batchCode = "RCV-2024-001",
            valuation = InventoryQrValuation.FIFO,
        )
        assertEquals(
            "gtr://part/21410-JF00A?batch=RCV-2024-001&valuation=FIFO",
            payload,
        )
        val fields = parseInventoryQrPayload(payload)
        assertEquals("21410-JF00A", fields.oemPartNumber)
        assertEquals("RCV-2024-001", fields.batchCode)
        assertEquals(InventoryQrValuation.FIFO, fields.valuation)
    }

    @Test
    fun parse_rejectsFiscalOrNonInventory() {
        assertThrows(IllegalArgumentException::class.java) {
            parseInventoryQrPayload("https://zimra.example/fdms/qr")
        }
        assertThrows(IllegalArgumentException::class.java) {
            parseInventoryQrPayload("gtr://part/ONLY-OEM")
        }
    }
}
