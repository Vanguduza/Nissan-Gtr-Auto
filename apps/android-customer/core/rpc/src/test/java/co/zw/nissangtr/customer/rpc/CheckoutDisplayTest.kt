package co.zw.nissangtr.customer.rpc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

/** D-57 — checkout display parity with `@gtr/payments` buildCheckoutDisplay. */
class CheckoutDisplayTest {

    @Test
    fun paynow_keeps_usd_with_indicative_zig() {
        val d = CheckoutDisplayBuilder.build(
            usdMinor = 10_000L,
            payMethod = CheckoutPayMethod.PAYNOW,
            zigRatePerUsd = 27.5,
        )
        assertEquals(CurrencyCode.USD, d.payCurrency)
        assertEquals(10_000L, d.payable.amountMinor)
        assertEquals(CurrencyCode.USD, d.browseCurrency)
        assertEquals(275_000L, d.indicativeZigMinor)
        assertNull(d.fxRateId)
    }

    @Test
    fun ecocash_converts_to_zig_with_fx_rate_id() {
        val d = CheckoutDisplayBuilder.build(
            usdMinor = 10_000L,
            payMethod = CheckoutPayMethod.ECOCASH,
            zigRatePerUsd = 25.0,
            fxRateId = "rate-1",
        )
        assertEquals(CurrencyCode.ZIG, d.payCurrency)
        assertEquals(250_000L, d.payable.amountMinor)
        assertEquals("rate-1", d.fxRateId)
        assertEquals("rate-1", d.payable.fxRateId)
    }

    @Test
    fun ecocash_requires_positive_zig_rate() {
        assertThrows(IllegalArgumentException::class.java) {
            CheckoutDisplayBuilder.build(
                usdMinor = 10_000L,
                payMethod = CheckoutPayMethod.ECOCASH,
                zigRatePerUsd = null,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            CheckoutDisplayBuilder.buildZigSettlement(
                usdMinor = 10_000L,
                zigRatePerUsd = 0.0,
                fxRateId = null,
            )
        }
    }

    @Test
    fun zig_settlement_from_money_minor_not_float_invent() {
        // 42.00 USD → 4200 minor × 26.5 = 111300 ZiG minor
        val d = CheckoutDisplayBuilder.buildZigSettlement(
            usdMinor = 4_200L,
            zigRatePerUsd = 26.5,
            fxRateId = "seed-rate",
        )
        assertEquals(111_300L, d.payable.amountMinor)
        assertEquals(CurrencyCode.ZIG, d.payable.currency)
        assertEquals("seed-rate", d.fxRateId)
    }
}
