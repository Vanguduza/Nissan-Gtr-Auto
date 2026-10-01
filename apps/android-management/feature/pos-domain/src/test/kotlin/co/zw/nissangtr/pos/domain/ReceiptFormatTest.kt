package co.zw.nissangtr.pos.domain

import co.zw.nissangtr.pos.domain.model.CartLine
import co.zw.nissangtr.pos.domain.model.CurrencyCode
import co.zw.nissangtr.pos.domain.model.Money
import co.zw.nissangtr.pos.domain.model.RECEIPT_FORMAT_VERSION
import co.zw.nissangtr.pos.domain.model.Receipt
import co.zw.nissangtr.pos.domain.model.ReceiptLabels
import co.zw.nissangtr.pos.domain.model.ReceiptRow
import co.zw.nissangtr.pos.domain.model.Tender
import co.zw.nissangtr.pos.domain.model.TenderLine
import co.zw.nissangtr.pos.domain.model.receiptRows
import co.zw.nissangtr.pos.domain.model.thermalText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.contentOrNull
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/** Conformance with the shared receipt spec: the tablet must print exactly what the fixture says. */
class ReceiptFormatTest {
    private val fixture: JsonObject = run {
        // Gradle runs module tests from the module directory; walk up to the repository root.
        var dir: File? = File("").absoluteFile
        while (dir != null && !File(dir, "packages/shared/fixtures/pos-receipt/v1.json").exists()) dir = dir.parentFile
        Json.parseToJsonElement(File(dir!!, "packages/shared/fixtures/pos-receipt/v1.json").readText()).jsonObject
    }

    private fun JsonObject.s(k: String) = this[k]?.jsonPrimitive?.contentOrNull

    private val labels = fixture["labels"]!!.jsonObject.let { l ->
        val t = l["tenders"]!!.jsonObject
        ReceiptLabels(
            l.s("brand")!!, l.s("strap")!!, l.s("invoice")!!, l.s("servedBy")!!, l.s("customer")!!, l.s("vehicle")!!,
            l.s("subtotal")!!, l.s("discount")!!, l.s("total")!!, l.s("cashGiven")!!, l.s("change")!!, l.s("offline")!!, l.s("thanks")!!,
            Tender.entries.associateWith { t.s(it.rpcValue)!! },
        )
    }

    private fun receipt(o: JsonObject): Receipt {
        val cur = CurrencyCode(o.s("currency")!!)
        fun m(k: String) = o[k]?.jsonPrimitive?.contentOrNull?.let { Money(it.toLong(), cur) }
        return Receipt(
            invoiceId = o.s("invoiceId")!!,
            documentNumber = o.s("documentNumber"),
            lines = o["lines"]!!.jsonArray.mapIndexed { i, e ->
                val l = e.jsonObject
                CartLine("l$i", "si$i", l.s("oemPartNumber")!!, l.s("name")!!, l["qty"]!!.jsonPrimitive.double,
                    Money(l["unitPriceMinor"]!!.jsonPrimitive.long, cur), Money(l["lineTotalMinor"]!!.jsonPrimitive.long, cur), false, null)
            },
            subtotal = m("subtotalMinor")!!,
            discount = m("discountMinor")!!,
            total = m("totalMinor")!!,
            tenders = o["tenders"]!!.jsonArray.map { e ->
                val t = e.jsonObject
                TenderLine(Tender.entries.first { it.rpcValue == t.s("tender") }, Money(t["amountMinor"]!!.jsonPrimitive.long, cur))
            },
            cashGiven = m("cashGivenMinor"),
            change = m("changeMinor"),
            customerName = o.s("customerName"),
            vehicleLabel = o.s("vehicleLabel"),
            operatorName = o.s("operatorName"),
            issuedAtIso = o.s("issuedAtLocal")!!,
            offline = o["offline"]!!.jsonPrimitive.boolean,
        )
    }

    @Test
    fun `fixture is format v1`() {
        assertEquals(RECEIPT_FORMAT_VERSION, fixture["version"]!!.jsonPrimitive.int)
    }

    @Test
    fun `tablet rows and 42-column text match the shared fixture`() {
        val width = fixture["thermalColumns"]!!.jsonPrimitive.int
        fixture["cases"]!!.jsonArray.forEach { e ->
            val c = e.jsonObject
            val name = c.s("name")
            val rows = receiptRows(receipt(c["receipt"]!!.jsonObject), labels)
            val expectedRows = c["rows"]!!.jsonArray.map { r ->
                val o = r.jsonObject
                ReceiptRow(o.s("left")!!, o.s("right") ?: "", o["strong"]!!.jsonPrimitive.boolean)
            }
            assertEquals("rows: $name", expectedRows, rows)
            assertEquals("thermal: $name", c["thermal42"]!!.jsonArray.map { it.jsonPrimitive.content }, thermalText(rows, width))
        }
    }
}
