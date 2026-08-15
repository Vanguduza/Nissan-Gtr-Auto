package co.zw.nissangtr.pos.api

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AddLineRulesTest {

    private val latch = VehicleLatch(chassisCode = "R35", engineCode = "VR38DETT")

    private fun item(
        price: Double? = 100.0,
        qty: Int = 4,
        chassis: List<String> = listOf("R35"),
        engines: List<String> = listOf("VR38DETT"),
        supersededBy: String? = null,
    ) = TillItem(
        oemPartNumber = "40206-JF00A",
        description = "Pad",
        currency = "USD",
        unitPrice = price,
        saleableQty = qty,
        chassisCodes = chassis,
        engineCodes = engines,
        supersededBy = supersededBy,
        coreCharge = 22.0,
    )

    @Test
    fun sellable_whenFitsPricedInStock() {
        val d = AddLineRules.decide(item(), latch)
        assertTrue(d is AddDecision.Allow)
        assertEquals(LineClass.SELLABLE, (d as AddDecision.Allow).lineClass)
    }

    @Test
    fun quoteOnly_whenOos() {
        val d = AddLineRules.decide(item(qty = 0), latch)
        assertTrue(d is AddDecision.Allow)
        assertEquals(LineClass.QUOTE_ONLY, (d as AddDecision.Allow).lineClass)
    }

    @Test
    fun unpriced_rejected() {
        val d = AddLineRules.decide(item(price = null), latch)
        assertTrue(d is AddDecision.Reject)
        assertEquals(LineClass.REJECTED_UNPRICED, (d as AddDecision.Reject).reason)
    }

    @Test
    fun noFit_blocked() {
        val d = AddLineRules.decide(item(chassis = listOf("Y62")), latch)
        assertTrue(d is AddDecision.Reject)
        assertEquals(LineClass.REJECTED_BLOCKED, (d as AddDecision.Reject).reason)
    }

    @Test
    fun supersessionBanner() {
        val d = AddLineRules.decide(item(supersededBy = "40206-NEW"), latch)
        assertTrue(d is AddDecision.Allow)
        assertEquals("Use 40206-NEW instead", (d as AddDecision.Allow).supersessionBanner)
    }

    @Test
    fun cta_allSellable_pay() {
        val lines = listOf(
            TicketLine("1", "A", "a", 1, 10.0, "USD", isQuoteOnly = false),
            TicketLine("1c", "A", "Core", 1, 2.0, "USD", isCoreCharge = true),
        )
        assertEquals(TicketCta.PAY, TicketCtaResolver.resolve(lines))
    }

    @Test
    fun cta_anyQuoteOnly_quote() {
        val lines = listOf(
            TicketLine("1", "A", "a", 1, 10.0, "USD", isQuoteOnly = false),
            TicketLine("2", "B", "b", 1, 5.0, "USD", isQuoteOnly = true),
        )
        assertEquals(TicketCta.QUOTE, TicketCtaResolver.resolve(lines))
    }

    @Test
    fun tillItemJson_hasNoMeiliQtyOnHitShape() {
        val json = Json { ignoreUnknownKeys = true }
        val hit = json.decodeFromString(
            CatalogHitDto.serializer(),
            """{"type":"part","oem_part_number":"40206-JF00A","pnc_code":"40206"}""",
        )
        assertEquals("part", hit.type)
        assertEquals("40206-JF00A", hit.oemPartNumber)
        // CatalogHitDto has no saleable_qty / qty fields — compile-time + reflection check
        val fields = CatalogHitDto::class.java.declaredFields.map { it.name }
        assertTrue(fields.none { it.equals("saleableQty", true) || it.equals("qty", true) })
    }
}
