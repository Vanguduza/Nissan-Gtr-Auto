package co.zw.nissangtr.delivery.jobs

import co.zw.nissangtr.delivery.rpc.DriverCash
import co.zw.nissangtr.delivery.rpc.DriverCashHandin
import co.zw.nissangtr.delivery.rpc.FakeRpcClient
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.Instant

class DriverCashGateTest {
    @Test
    fun declaredAmountIsTheDriversOwnCount() {
        assertEquals(120.5, DriverCashGate.parseDeclared("120,5")!!, 0.0001)
        assertEquals(0.0, DriverCashGate.parseDeclared("0")!!, 0.0001)
        assertNull(DriverCashGate.parseDeclared("-1"))
        assertNull(DriverCashGate.parseDeclared("abc"))
    }

    @Test
    fun handInClearsWhatIsHeldAndBlocksASecondUntilCounted() = runBlocking {
        val rpc = FakeRpcClient()
        val before = rpc.getMyDriverCash()
        assertEquals("USD 125.50", DriverCashGate.summary(before))
        assertNull(DriverCashGate.blockedReason(before, "USD"))
        assertEquals("for 2 h", DriverCashGate.age(before.holding.single(), Instant.parse("2026-10-05T10:20:00Z")))

        val h = rpc.submitDriverCashHandin("USD", 125.5, null)
        assertEquals(125.5, h.expectedAmount, 0.001)
        assertEquals(2, h.collectionCount)
        val after = rpc.getMyDriverCash()
        assertTrue(after.holding.isEmpty())
        assertEquals("Being counted", DriverCashGate.summary(after))
        assertEquals("Your last USD hand-in is still being counted.", DriverCashGate.blockedReason(after, "USD"))
        try {
            rpc.submitDriverCashHandin("USD", 1.0, null)
            fail("a second hand-in must wait for the first to be counted")
        } catch (e: IllegalStateException) {
            assertTrue(e.message!!.contains("still waiting"))
        }
    }

    @Test
    fun statusWordsForTheDriver() {
        fun h(status: String, received: Double? = null, variance: Double? = null) =
            DriverCashHandin("1", "DCH-00001", status, "USD", 100.0, 100.0, received, variance, 2, null, null, null)
        assertEquals("Waiting to be counted", DriverCashGate.statusLabel(h("submitted")))
        assertEquals("Counted — matches", DriverCashGate.statusLabel(h("received", 100.0, 0.0)))
        assertEquals("Counted USD 95.00 — manager to review the difference", DriverCashGate.statusLabel(h("variance_pending", 95.0, -5.0)))
        assertEquals("USD 5.00 short signed off by a manager", DriverCashGate.statusLabel(h("approved", 95.0, -5.0)))
        assertEquals("None", DriverCashGate.summary(DriverCash(emptyList(), emptyList())))
    }
}
