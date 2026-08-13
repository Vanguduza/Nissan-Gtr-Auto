package co.zw.nissangtr.management.rpc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/** H4 / B-MONEY-1 — dual-read helpers (parity with `@gtr/shared` money.ts). */
class MoneyDualReadTest {

    @Test
    fun toAmountMinor_converts_usd_majors_to_cents() {
        assertEquals(1234L, MoneyDualRead.toAmountMinor(12.34, CurrencyCode.USD))
        assertEquals(0L, MoneyDualRead.toAmountMinor(0.0, CurrencyCode.USD))
        assertEquals(-150L, MoneyDualRead.toAmountMinor(-1.5, CurrencyCode.ZIG))
    }

    @Test
    fun preferAmountMinor_minor_wins_over_divergent_major() {
        val minor = MoneyDualRead.preferAmountMinor(
            amountMinor = 1999L,
            amountMajor = 99.99,
            currency = CurrencyCode.USD,
        )
        assertEquals(1999L, minor)
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
        val minor = MoneyDualRead.preferAmountMinor(
            amountMinor = null,
            amountMajor = 12.34,
            currency = CurrencyCode.USD,
        )
        assertEquals(1234L, minor)
        assertEquals(
            12.34,
            MoneyDualRead.displayUnitPriceMajor(
                unitPrice = 12.34,
                unitPriceMinor = null,
                currency = CurrencyCode.USD,
            ),
            0.0001,
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
    fun displayLineTotalMajor_dual_read() {
        assertEquals(
            19.99,
            MoneyDualRead.displayLineTotalMajor(
                lineTotal = 1.0,
                lineTotalMinor = 1999L,
                currency = CurrencyCode.USD,
            ),
            0.0001,
        )
    }

    @Test
    fun sumPreferAmountMinor_mixes_minor_and_major_fallback() {
        val sum = MoneyDualRead.sumPreferAmountMinor(
            listOf(
                1000L to 9.99,
                null to 2.5,
            ),
            CurrencyCode.USD,
        )
        assertEquals(1250L, sum)
        assertEquals(12.5, MoneyDualRead.fromAmountMinor(sum, CurrencyCode.USD), 0.0001)
    }

    @Test
    fun posCartLineSummary_extensions_prefer_minor() {
        val line = PosCartLineSummary(
            id = "l1",
            stockItemId = "s1",
            oemPartNumber = "OEM",
            qty = 1.0,
            unitPrice = 1.0,
            lineTotal = 1.0,
            unitPriceMinor = 2550L,
            lineTotalMinor = 5100L,
        )
        assertEquals(25.50, line.displayUnitPrice(CurrencyCode.USD), 0.0001)
        assertEquals(51.00, line.displayLineTotal(CurrencyCode.USD), 0.0001)
    }
}
