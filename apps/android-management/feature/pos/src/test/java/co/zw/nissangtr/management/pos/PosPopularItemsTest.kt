package co.zw.nissangtr.management.pos

import co.zw.nissangtr.management.rpc.CurrencyCode
import co.zw.nissangtr.management.rpc.EpcDiagramPart
import co.zw.nissangtr.management.rpc.EpcMaker
import co.zw.nissangtr.management.rpc.EpcModel
import co.zw.nissangtr.management.rpc.EpcSection
import co.zw.nissangtr.management.rpc.PopularPosSpare
import co.zw.nissangtr.management.rpc.PosPopularItemKind
import co.zw.nissangtr.management.rpc.PosPopularPin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PosPopularItemsTest {

    @Test
    fun pins_are_first_and_pinned_part_dedupes_algorithmic_oem() {
        val pin = PosPopularPin(
            kind = PosPopularItemKind.PART,
            itemKey = "15208-65F0A",
            label = "Oil Filter",
            oemPartNumber = "15208-65F0A",
        )
        val rows = buildPopularRowItems(
            pins = listOf(pin),
            algorithmicSpares = listOf(
                spare("s1", "15208-65F0A"),
                spare("s2", "D1060-JF00A"),
            ),
        )

        assertEquals(2, rows.size)
        assertTrue(rows.first() is PosPopularRowItem.Pinned)
        val algorithmic = rows.filterIsInstance<PosPopularRowItem.AlgorithmicSpare>()
        assertEquals(listOf("D1060-JF00A"), algorithmic.map { it.spare.oemPartNumber })
    }

    @Test
    fun epc_part_long_press_candidates_cover_part_category_and_subcategory() {
        val candidates = epcPartPopularCandidates(
            maker = EpcMaker("nissan", "Nissan"),
            model = EpcModel("gtr-r35", "GT-R R35", sortKey = "gtr-r35"),
            section = EpcSection("engine", "Engine"),
            part = EpcDiagramPart(
                oemPartNumber = "15208-65F0A",
                pncCode = "15208",
                categoryName = "Engine",
                subcategoryName = "Filters",
                stockDescription = "Oil filter",
            ),
        )

        assertEquals(
            setOf(PosPopularItemKind.PART, PosPopularItemKind.CATEGORY, PosPopularItemKind.SUBCATEGORY),
            candidates.map { it.kind }.toSet(),
        )
        assertTrue(candidates.any { it.kind == PosPopularItemKind.PART && it.oemPartNumber == "15208-65F0A" })
        assertTrue(candidates.any { it.kind == PosPopularItemKind.SUBCATEGORY && it.label == "Filters" })
    }

    @Test
    fun section_pin_preserves_model_context_and_thumbnail() {
        val pin = epcSectionPopularPin(
            EpcMaker("nissan", "Nissan"),
            EpcModel("gtr-r35", "GT-R R35", sortKey = "gtr-r35"),
            EpcSection("brakes", "Brakes", thumbnailUrl = "https://example.test/brakes.png"),
        )
        assertEquals(PosPopularItemKind.CATEGORY, pin.kind)
        assertEquals("gtr-r35", pin.modelSlug)
        assertEquals("Brakes", pin.categoryName)
        assertFalse(pin.searchQuery.isBlank())
        assertEquals("https://example.test/brakes.png", pin.imageUrl)
    }

    private fun spare(id: String, oem: String) = PopularPosSpare(
        stockItemId = id,
        oemPartNumber = oem,
        description = oem,
        unitsSold = 10.0,
        saleableQty = 5.0,
        unitPrice = 10.0,
        currency = CurrencyCode.USD,
    )
}
