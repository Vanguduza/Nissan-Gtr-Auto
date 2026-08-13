package co.zw.nissangtr.delivery.rpc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

/** H4 / B-MONEY-1 — delivery COD/settlement dual-read helpers. */
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
    fun settlement_displayAmountDue_prefers_explicit_minor() {
        val settlement = DeliveryJobSettlement(
            currency = CurrencyCode.USD,
            invoiceTotal = 100.0,
            invoiceTotalMinor = 10000L,
            amountPaid = 0.0,
            amountPaidMinor = 0L,
            amountDue = 1.0,
            amountDueMinor = 4550L,
        )
        assertEquals(45.50, settlement.displayAmountDue()!!, 0.0001)
        assertEquals("COD USD 45.50", settlement.formatAmountDueLabel())
    }

    @Test
    fun settlement_displayAmountDue_derives_from_total_minus_paid() {
        val settlement = DeliveryJobSettlement(
            currency = CurrencyCode.USD,
            // Divergent majors — minors must win.
            invoiceTotal = 1.0,
            invoiceTotalMinor = 5000L,
            amountPaid = 99.0,
            amountPaidMinor = 1500L,
        )
        assertEquals(35.00, settlement.displayAmountDue()!!, 0.0001)
        assertEquals(50.00, settlement.displayInvoiceTotal()!!, 0.0001)
        assertEquals(15.00, settlement.displayAmountPaid()!!, 0.0001)
    }

    @Test
    fun settlement_formatAmountDueLabel_null_when_fully_paid() {
        val settlement = DeliveryJobSettlement(
            currency = CurrencyCode.ZIG,
            invoiceTotal = 10.0,
            invoiceTotalMinor = 1000L,
            amountPaid = 10.0,
            amountPaidMinor = 1000L,
        )
        assertEquals(0.0, settlement.displayAmountDue()!!, 0.0001)
        assertNull(settlement.formatAmountDueLabel())
    }

    @Test
    fun settlement_null_when_no_money_fields() {
        val settlement = DeliveryJobSettlement(currency = CurrencyCode.USD)
        assertNull(settlement.displayInvoiceTotal())
        assertNull(settlement.displayAmountPaid())
        assertNull(settlement.displayAmountDue())
        assertNull(settlement.formatAmountDueLabel())
    }
}
