package co.zw.nissangtr.bridges.escpos

import java.nio.charset.Charset
import java.time.LocalDate

/**
 * Minimal ESC/POS byte builder for inventory labels and plain receipts.
 * Targets common 58mm Bluetooth thermal printers (Epson-compatible QR).
 */
object EscPosCommands {
    private val CHARSET: Charset = Charsets.ISO_8859_1

    private val INIT = byteArrayOf(0x1B, 0x40) // ESC @
    private val ALIGN_CENTER = byteArrayOf(0x1B, 0x61, 0x01)
    private val ALIGN_LEFT = byteArrayOf(0x1B, 0x61, 0x00)
    private val EMPHASIS_ON = byteArrayOf(0x1B, 0x45, 0x01)
    private val EMPHASIS_OFF = byteArrayOf(0x1B, 0x45, 0x00)
    private val FEED_CUT = byteArrayOf(0x0A, 0x0A, 0x0A, 0x1D, 0x56, 0x00)

    fun inventoryLabel(job: EscPosPrintJob): ByteArray {
        val date = job.labelDate?.take(10) ?: LocalDate.now().toString()
        val out = ArrayList<Byte>(512)
        out += INIT
        out += ALIGN_CENTER
        out += EMPHASIS_ON
        out += "GTR Auto\n".toEscPos()
        out += EMPHASIS_OFF
        out += qrCode(job.qrPayload)
        out += "\n".toEscPos()
        out += ALIGN_LEFT
        out += "OEM: ${job.oemPartNumber}\n".toEscPos()
        out += "Batch: ${job.batchCode}\n".toEscPos()
        out += "Val: ${job.valuation.name}\n".toEscPos()
        out += "Date: $date\n".toEscPos()
        out += FEED_CUT
        return out.toByteArray()
    }

    /**
     * Warehouse bin location label with QR glyph (same Epson QR path as inventory).
     * Payload: `gtr://bin/{code}` — location scan, not inventory `gtr://part/…`.
     */
    fun binLabel(
        code: String,
        name: String,
        aisle: String? = null,
        rack: String? = null,
        shelf: String? = null,
        pickPathSeq: Int = 0,
    ): ByteArray {
        val payload = "gtr://bin/${code.trim()}"
        val loc = listOfNotNull(
            aisle?.let { "Aisle $it" },
            rack?.let { "Rack $it" },
            shelf?.let { "Shelf $it" },
        ).joinToString(" · ").ifBlank { "seq $pickPathSeq" }
        val out = ArrayList<Byte>(512)
        out += INIT
        out += ALIGN_CENTER
        out += EMPHASIS_ON
        out += "GTR BIN LABEL\n".toEscPos()
        out += EMPHASIS_OFF
        out += qrCode(payload)
        out += "\n".toEscPos()
        out += EMPHASIS_ON
        out += "${code.trim()}\n".toEscPos()
        out += EMPHASIS_OFF
        out += ALIGN_LEFT
        out += "${name.trim()}\n".toEscPos()
        out += "$loc\n".toEscPos()
        out += "Pick seq: $pickPathSeq\n".toEscPos()
        out += FEED_CUT
        return out.toByteArray()
    }

    fun receiptLines(lines: List<EscPosReceiptLine>): ByteArray {
        val out = ArrayList<Byte>(256)
        out += INIT
        out += ALIGN_LEFT
        for (line in lines) {
            if (line.emphasis) out += EMPHASIS_ON
            out += (line.text + "\n").toEscPos()
            if (line.emphasis) out += EMPHASIS_OFF
        }
        out += FEED_CUT
        return out.toByteArray()
    }

    /**
     * Epson QR model 2: GS ( k store + print.
     * @see https://reference.epson-biz.com/modules/ref_escpos/
     */
    fun qrCode(payload: String): ByteArray {
        val data = payload.toByteArray(Charsets.UTF_8)
        val storeLen = data.size + 3
        val pL = (storeLen and 0xFF).toByte()
        val pH = ((storeLen shr 8) and 0xFF).toByte()
        val out = ArrayList<Byte>(data.size + 32)
        // Function 165: QR size (module 4)
        out += byteArrayOf(0x1D, 0x28, 0x6B, 0x03, 0x00, 0x31, 0x43, 0x04)
        // Function 167: error correction M
        out += byteArrayOf(0x1D, 0x28, 0x6B, 0x03, 0x00, 0x31, 0x45, 0x31)
        // Function 180: store data
        out += byteArrayOf(0x1D, 0x28, 0x6B, pL, pH, 0x31, 0x50, 0x30)
        out += data
        // Function 181: print
        out += byteArrayOf(0x1D, 0x28, 0x6B, 0x03, 0x00, 0x31, 0x51, 0x30)
        return out.toByteArray()
    }

    private fun String.toEscPos(): ByteArray = toByteArray(CHARSET)

    private operator fun ArrayList<Byte>.plusAssign(bytes: ByteArray) {
        for (b in bytes) add(b)
    }
}
