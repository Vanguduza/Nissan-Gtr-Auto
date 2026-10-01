package co.zw.nissangtr.pos.ui.sale

import co.zw.nissangtr.pos.domain.model.Tender
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/** The tablet's printed labels (string resources) must be the shared receipt spec's labels. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReceiptLabelsTest {
    @Test
    fun `resource labels match packages-shared fixtures pos-receipt v1`() {
        var dir: File? = File("").absoluteFile
        while (dir != null && !File(dir, "packages/shared/fixtures/pos-receipt/v1.json").exists()) dir = dir.parentFile
        val spec = Json.parseToJsonElement(File(dir!!, "packages/shared/fixtures/pos-receipt/v1.json").readText()).jsonObject["labels"]!!.jsonObject
        fun s(k: String) = spec[k]!!.jsonPrimitive.content
        val l = receiptLabels(RuntimeEnvironment.getApplication().resources)
        assertEquals(
            listOf(s("brand"), s("strap"), s("invoice"), s("servedBy"), s("customer"), s("vehicle"), s("subtotal"), s("discount"), s("total"), s("cashGiven"), s("change"), s("offline"), s("thanks")),
            listOf(l.brand, l.strap, l.invoice, l.servedBy, l.customer, l.vehicle, l.subtotal, l.discount, l.total, l.cashGiven, l.change, l.offline, l.thanks),
        )
        val tenders = spec["tenders"]!!.jsonObject
        Tender.entries.forEach { assertEquals(tenders[it.rpcValue]!!.jsonPrimitive.content, l.tenders.getValue(it)) }
    }
}
