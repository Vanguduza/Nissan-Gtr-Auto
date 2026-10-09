package co.zw.nissangtr.pos.data

import co.zw.nissangtr.pos.domain.error.PosError
import org.junit.Assert.assertEquals
import org.junit.Test

class ClassifyTest {
    @Test
    fun `stock-ledger shortfalls never reach the cashier raw`() {
        val e = classify(RuntimeException("insufficient FIFO batch qty for item 7543f753-f77a-4fe7-a8d4-b6e6cfd17e8a (short 1.000)"))
        assertEquals(PosError.BusinessRule("insufficient_stock", ""), e)
    }

    @Test
    fun `operator-readable refusals keep their text`() {
        val e = classify(RuntimeException("ERROR: pairing code not found or not open")) as PosError.BusinessRule
        assertEquals("pairing code not found or not open", e.detail)
    }
}
