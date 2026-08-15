package co.zw.nissangtr.bridges.escpos

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertArrayEquals
import org.junit.Test

/**
 * ESC/POS byte builders (no Bluetooth hardware required).
 * Device tests: pair printer → setPrinterAddress → connect → printInventoryLabel.
 */
class EscPosCommandsTest {

    @Test
    fun inventoryLabel_containsOemAndInit() {
        val bytes = EscPosCommands.inventoryLabel(
            EscPosPrintJob(
                qrPayload = "gtr://part/21410-JF00A?batch=RCV-1&valuation=FIFO",
                oemPartNumber = "21410-JF00A",
                batchCode = "RCV-1",
                valuation = InventoryQrValuation.FIFO,
                labelDate = "2026-07-25",
            ),
        )
        val asText = bytes.toString(Charsets.ISO_8859_1)
        assertTrue(asText.contains("21410-JF00A"))
        assertTrue(asText.contains("RCV-1"))
        assertTrue(bytes[0] == 0x1B.toByte() && bytes[1] == 0x40.toByte())
    }

    @Test
    fun receiptLines_containsInvoiceLine() {
        val bytes = EscPosCommands.receiptLines(
            listOf(
                EscPosReceiptLine("GTR Auto POS", emphasis = true),
                EscPosReceiptLine("Invoice: abc-123"),
            ),
        )
        val asText = bytes.toString(Charsets.ISO_8859_1)
        assertTrue(asText.contains("Invoice: abc-123"))
    }

    @Test
    fun qrCode_embedsPayloadUtf8() {
        val payload = "gtr://part/X?batch=Y&valuation=AVG"
        val bytes = EscPosCommands.qrCode(payload)
        val haystack = bytes.toString(Charsets.ISO_8859_1)
        assertTrue(haystack.contains(payload))
    }

    @Test
    fun binLabel_embedsCodeAndBinQr() {
        val bytes = EscPosCommands.binLabel(
            code = "A-01",
            name = "Fast movers",
            aisle = "A",
            rack = "1",
            shelf = "2",
            pickPathSeq = 10,
        )
        val asText = bytes.toString(Charsets.ISO_8859_1)
        assertTrue(asText.contains("A-01"))
        assertTrue(asText.contains("Fast movers"))
        assertTrue(asText.contains("gtr://bin/A-01") || bytes.toString(Charsets.UTF_8).contains("gtr://bin/A-01"))
        assertTrue(bytes[0] == 0x1B.toByte() && bytes[1] == 0x40.toByte())
    }

    @Test
    fun cashDrawerPulse_escP_pin2_defaultTiming() {
        // ESC p m t1 t2 — 50ms → t1=25 (0x19), 200ms → t2=100 (0x64)
        val bytes = EscPosCommands.cashDrawerPulse()
        assertEquals(5, bytes.size)
        assertEquals(0x1B.toByte(), bytes[0])
        assertEquals(0x70.toByte(), bytes[1])
        assertEquals(0x00.toByte(), bytes[2]) // PIN_2
        assertEquals(0x19.toByte(), bytes[3])
        assertEquals(0x64.toByte(), bytes[4])
    }

    @Test
    fun cashDrawerPulse_pin5_customTiming() {
        val bytes = EscPosCommands.cashDrawerPulse(
            pin = CashDrawerPin.PIN_5,
            onTimeMs = 100,
            offTimeMs = 500,
        )
        assertEquals(0x01.toByte(), bytes[2]) // PIN_5
        assertEquals(0x32.toByte(), bytes[3]) // 100ms / 2
        assertEquals(0xFA.toByte(), bytes[4]) // 500ms / 2
    }

    @Test
    fun cashDrawerPulseDleDc4_realTimeForm() {
        // DLE DC4 n=1 m t — 10 14 01 m t
        val bytes = EscPosCommands.cashDrawerPulseDleDc4(
            pin = CashDrawerPin.PIN_2,
            onTimeHundredMs = 2,
        )
        assertArrayEquals(
            byteArrayOf(0x10, 0x14, 0x01, 0x00, 0x02),
            bytes,
        )
    }
}
