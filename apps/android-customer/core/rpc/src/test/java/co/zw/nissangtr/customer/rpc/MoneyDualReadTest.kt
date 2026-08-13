package co.zw.nissangtr.customer.rpc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

/** H4 / B-MONEY-1 — customer cart dual-read helpers. */
class MoneyDualReadTest {

    @Test
    fun toAmountMinor_converts_usd_majors_to_cents() {
        assertEquals(1234L, MoneyDualRead.toAmountMinor(12.34, CurrencyCode.USD))
        assertEquals(0L, MoneyDualRead.toAmountMinor(0.0, CurrencyCode.USD))
        assertEquals(-150L, MoneyDualRead.toAmountMinor(-1.5, CurrencyCode.ZIG))
    }

    @Test
    fun preferAmountMinor_minor_wins_over_divergent_major() {
        assertEquals(
            1999L,
            MoneyDualRead.preferAmountMinor(
                amountMinor = 1999L,
                amountMajor = 99.99,
                currency = CurrencyCode.USD,
            ),
        )
        assertEquals(
            19.99,
            MoneyDualRead.displayMajorFromDual(
                amountMinor = 1999L,
                amountMajor = 1.0,
                currency = CurrencyCode.USD,
            ),
            0.0001,
        )
    }

    @Test
    fun preferAmountMinor_falls_back_to_major_when_minor_null() {
        assertEquals(
            1234L,
            MoneyDualRead.preferAmountMinor(
                amountMinor = null,
                amountMajor = 12.34,
                currency = CurrencyCode.USD,
            ),
        )
    }

    @Test
    fun preferAmountMinor_throws_when_both_absent() {
        assertThrows(IllegalArgumentException::class.java) {
            MoneyDualRead.preferAmountMinor(
                amountMinor = null,
                amountMajor = null,
                currency = CurrencyCode.USD,
            )
        }
    }

    @Test
    fun cartLine_displayUnitPrice_prefers_minor() {
        val line = CartLineSummary(
            id = "l1",
            stockItemId = "s1",
            uomId = "u1",
            qty = 2.0,
            unitPrice = 1.0,
            unitPriceMinor = 1850L,
            unitPriceUsd = 1.0,
        )
        assertEquals(18.50, line.displayUnitPrice(CurrencyCode.USD)!!, 0.0001)
        assertEquals(37.00, line.displayLineTotal(CurrencyCode.USD)!!, 0.0001)
    }

    @Test
    fun cartLine_displayUnitPrice_null_when_no_price() {
        val line = CartLineSummary(
            id = "l1",
            stockItemId = "s1",
            uomId = "u1",
            qty = 1.0,
        )
        assertNull(line.displayUnitPrice(CurrencyCode.USD))
    }

    @Test
    fun cartSummary_displaySubtotal_sums_minors() {
        val cart = CartSummary(
            id = "c1",
            currency = CurrencyCode.USD,
            fulfillmentMode = FulfillmentMode.IMMEDIATE,
            lines = listOf(
                CartLineSummary(
                    id = "a",
                    stockItemId = "s1",
                    uomId = "u1",
                    qty = 1.0,
                    unitPrice = 10.0,
                    lineTotal = 10.0,
                    lineTotalMinor = 1000L,
                ),
                CartLineSummary(
                    id = "b",
                    stockItemId = "s2",
                    uomId = "u1",
                    qty = 1.0,
                    unitPrice = 5.0,
                    lineTotal = 5.0,
                    lineTotalMinor = 500L,
                ),
            ),
        )
        assertEquals(15.0, cart.displaySubtotal(), 0.0001)
    }
}
