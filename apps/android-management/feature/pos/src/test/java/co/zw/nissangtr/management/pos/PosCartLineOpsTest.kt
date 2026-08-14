package co.zw.nissangtr.management.pos

import co.zw.nissangtr.management.pos.offline.LocalCartLine
import co.zw.nissangtr.management.rpc.CurrencyCode
import co.zw.nissangtr.management.rpc.PosCartLineSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PosCartLineOpsTest {

    @Test
    fun cartTotal_excludes_core_charge_lines() {
        val lines = listOf(
            summary(id = "l1", unitPrice = 10.0, qty = 2.0, lineTotal = 20.0),
            summary(id = "l2", unitPrice = 5.0, qty = 1.0, lineTotal = 5.0, isCoreCharge = true),
        )
        assertEquals(20.0, PosCartLineOps.cartTotal(lines), 0.0001)
    }

    @Test
    fun cartTotal_empty_is_zero() {
        assertEquals(0.0, PosCartLineOps.cartTotal(emptyList()), 0.0001)
    }

    @Test
    fun cartTotal_prefers_line_total_minor_when_present() {
        val lines = listOf(
            summary(
                id = "l1",
                unitPrice = 9.99,
                qty = 1.0,
                lineTotal = 9.99,
                lineTotalMinor = 1000L,
            ),
            summary(
                id = "l2",
                unitPrice = 2.5,
                qty = 1.0,
                lineTotal = 2.5,
                // major-only fallback
            ),
        )
        // 1000 + 250 = 1250 minor → 12.50 (not 9.99+2.5)
        assertEquals(
            12.5,
            PosCartLineOps.cartTotal(lines, CurrencyCode.USD),
            0.0001,
        )
    }

    @Test
    fun cartTotal_falls_back_to_major_when_minors_absent() {
        val lines = listOf(
            summary(id = "l1", unitPrice = 10.0, qty = 2.0, lineTotal = 20.0),
            summary(id = "l2", unitPrice = 3.0, qty = 1.0, lineTotal = 3.0),
        )
        assertEquals(23.0, PosCartLineOps.cartTotal(lines, CurrencyCode.USD), 0.0001)
    }

    @Test
    fun toSummaries_preserves_line_shape() {
        val local = listOf(
            LocalCartLine(
                id = "l1",
                stockItemId = "s1",
                oemPartNumber = "OEM-1",
                uomId = "u1",
                qty = 3.0,
                unitPrice = 4.0,
                lineTotal = 12.0,
                isCoreCharge = false,
            ),
        )
        val summaries = PosCartLineOps.toSummaries(local)
        assertEquals(1, summaries.size)
        assertEquals("OEM-1", summaries.first().oemPartNumber)
        assertEquals(12.0, summaries.first().lineTotal, 0.0001)
    }

    @Test
    fun applyOfflineQtyDelta_increments_line_and_linked_core_charge() {
        val lines = mutableListOf(
            local(id = "l1", stockItemId = "s1", qty = 1.0, unitPrice = 10.0),
            local(id = "core1", stockItemId = "s1", qty = 1.0, unitPrice = 2.0, isCoreCharge = true),
        )
        PosCartLineOps.applyOfflineQtyDelta(lines, targetId = "l1", targetStockItemId = "s1", delta = 1.0)

        val part = lines.first { it.id == "l1" }
        val core = lines.first { it.isCoreCharge }
        assertEquals(2.0, part.qty, 0.0001)
        assertEquals(20.0, part.lineTotal, 0.0001)
        assertEquals(2.0, core.qty, 0.0001)
        assertEquals(4.0, core.lineTotal, 0.0001)
    }

    @Test
    fun applyOfflineQtyDelta_removes_line_and_core_charge_at_zero() {
        val lines = mutableListOf(
            local(id = "l1", stockItemId = "s1", qty = 1.0, unitPrice = 10.0),
            local(id = "core1", stockItemId = "s1", qty = 1.0, unitPrice = 2.0, isCoreCharge = true),
        )
        PosCartLineOps.applyOfflineQtyDelta(lines, targetId = "l1", targetStockItemId = "s1", delta = -1.0)

        assertTrue(lines.isEmpty())
    }

    @Test
    fun applyOfflineQtyDelta_unknown_id_is_noop() {
        val lines = mutableListOf(local(id = "l1", stockItemId = "s1", qty = 1.0, unitPrice = 10.0))
        PosCartLineOps.applyOfflineQtyDelta(lines, targetId = "missing", targetStockItemId = "s1", delta = 1.0)
        assertEquals(1, lines.size)
        assertEquals(1.0, lines.first().qty, 0.0001)
    }

    private fun summary(
        id: String,
        unitPrice: Double,
        qty: Double,
        lineTotal: Double,
        isCoreCharge: Boolean = false,
        unitPriceMinor: Long? = null,
        lineTotalMinor: Long? = null,
    ) = PosCartLineSummary(
        id = id,
        stockItemId = "stock-$id",
        oemPartNumber = "OEM-$id",
        qty = qty,
        unitPrice = unitPrice,
        lineTotal = lineTotal,
        isCoreCharge = isCoreCharge,
        unitPriceMinor = unitPriceMinor,
        lineTotalMinor = lineTotalMinor,
    )

    private fun local(
        id: String,
        stockItemId: String,
        qty: Double,
        unitPrice: Double,
        isCoreCharge: Boolean = false,
    ) = LocalCartLine(
        id = id,
        stockItemId = stockItemId,
        oemPartNumber = "OEM-$id",
        uomId = "uom-1",
        qty = qty,
        unitPrice = unitPrice,
        lineTotal = unitPrice * qty,
        isCoreCharge = isCoreCharge,
    )
}
