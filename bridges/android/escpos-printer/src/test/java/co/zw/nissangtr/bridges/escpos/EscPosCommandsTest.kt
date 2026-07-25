package co.zw.nissangtr.bridges.escpos

import org.junit.Assert.assertTrue
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
}
