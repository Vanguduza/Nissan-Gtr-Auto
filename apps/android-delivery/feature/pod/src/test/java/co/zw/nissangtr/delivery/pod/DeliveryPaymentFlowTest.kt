package co.zw.nissangtr.delivery.pod

import co.zw.nissangtr.bridges.terminal.SimulatedCardTerminalBridge
import co.zw.nissangtr.delivery.rpc.FakeRpcClient
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class DeliveryPaymentFlowTest {
    private val rpc = FakeRpcClient()
    private val store = object : CardPairingStore {
        val paired = mutableMapOf<String, String>()
        var selected: String? = null
        override fun selectedTerminalId() = selected
        override fun selectTerminal(id: String) { selected = id }
        override fun pairedKeySha(terminalId: String) = paired[terminalId]
        override fun markPaired(terminalId: String, keySha: String) { paired[terminalId] = keySha }
    }
    private val signer = object : CardEvidenceSigner {
        override fun publicKeySpkiBase64() = "c3BraQ=="
        override fun publicKeySha256Hex() = "ab".repeat(32)
        override fun signBase64(message: ByteArray) = "c2ln"
    }
    private val flow = DeliveryPaymentFlow(rpc, SimulatedCardTerminalBridge(delayMs = 0), "device-1", signer, store)
    private val job = FakeRpcClient.JOB_1

    @Test
    fun codStopBlocksUntilCashAndCardCoverTheBalance() = runBlocking {
        val ctx = flow.context(job)!!
        assertEquals(45.50, ctx.amountDue, 0.001)
        assertEquals("Collect USD 45.50 before completing.", CodGate.blockingReason(ctx, null))

        flow.collectCash(job, 20.0, "cash-1", "  ")
        val afterCash = flow.context(job)!!
        assertEquals(25.50, afterCash.amountDue, 0.001)
        assertNotNull(CodGate.blockingReason(afterCash, null))

        val terminal = flow.selected(flow.terminals(ctx.warehouseId))!!
        flow.pair(terminal.id)
        assertTrue(flow.isPaired(terminal.id))
        val paid = flow.charge(job, terminal.id, 25.50, "card-1")
        assertEquals("settled", paid.status)
        assertEquals("4242", paid.cardLast4)
        assertNull(CodGate.blockingReason(flow.context(job), paid))
    }

    @Test
    fun cashRetryWithTheSameRequestIdIsRecordedOnce() = runBlocking {
        flow.collectCash(job, 10.0, "cash-same", null)
        flow.collectCash(job, 10.0, "cash-same", null)
        assertEquals(35.50, flow.context(job)!!.amountDue, 0.001)
    }

    @Test
    fun cardWithNoAnswerBlocksAndIsRecoveredByAskingTheMachine() = runBlocking {
        val terminal = flow.selected(flow.terminals(null))!!
        // Amounts ending in .98: the demo machine gives no answer.
        val unknown = flow.charge(job, terminal.id, 44.98, "card-u")
        assertEquals("unknown", unknown.status)
        assertTrue(CodGate.isUnresolved(unknown))
        assertEquals("Finish the card payment above before completing.", CodGate.blockingReason(flow.context(job), unknown))
        assertEquals(unknown.attemptId, flow.recovery(job)?.attemptId)
        try {
            flow.charge(job, terminal.id, 10.0, "card-u2")
            fail("a second charge must be refused while one is unresolved")
        } catch (e: IllegalStateException) {
            assertTrue(e.message!!.contains("reconcile"))
        }
        val settled = flow.askAgain(unknown)
        assertEquals("settled", settled.status)
        assertFalse(CodGate.isUnresolved(settled))
        assertEquals(0.52, flow.context(job)!!.amountDue, 0.001)
    }

    @Test
    fun declinedCardTakesNoMoneyAndKeepsTheStopDue() = runBlocking {
        val terminal = flow.selected(flow.terminals(null))!!
        val declined = flow.charge(job, terminal.id, 44.99, "card-d")
        assertEquals("declined", declined.status)
        assertFalse(CodGate.isUnresolved(declined))
        assertEquals(45.50, flow.context(job)!!.amountDue, 0.001)
    }

    @Test
    fun prepaidStopNeverBlocks() = runBlocking {
        val ctx = flow.context(FakeRpcClient.JOB_2)!!
        assertFalse(CodGate.collects(ctx))
        assertNull(CodGate.blockingReason(ctx, null))
        assertNull(CodGate.blockingReason(null, null))
    }

    @Test
    fun amountsAreCappedAtTheBalance() {
        assertEquals(12.5, CodGate.parseAmount("12,5", 45.5)!!, 0.0001)
        assertEquals(45.5, CodGate.parseAmount("45.50", 45.5)!!, 0.0001)
        assertNull(CodGate.parseAmount("45.60", 45.5))
        assertNull(CodGate.parseAmount("0", 45.5))
        assertNull(CodGate.parseAmount("abc", 45.5))
    }

    @Test
    fun partPaidBalanceWithinCreditIsLeftOnAccountAtOnce() = runBlocking {
        flow.collectCash(job, 20.0, "cash-part", null)
        val ctx = flow.context(job)!!
        assertEquals("Collect USD 25.50 before completing.", CodGate.blockingReason(ctx, null, null))
        val a = flow.requestBalanceOnAccount(job, "  paid what they had  ")
        assertEquals("auto_approved", a.status)
        assertEquals("credit_limit", a.basis)
        assertEquals("paid what they had", a.reason)
        assertTrue(CodGate.balanceOnAccount(ctx, a))
        assertNull(CodGate.blockingReason(ctx, null, a))
        // An unresolved card charge still holds completion.
        val unknown = DeliveryCardAttemptFixtures.unknown
        assertNotNull(CodGate.blockingReason(ctx, unknown, a))
    }

    @Test
    fun largerBalanceWaitsForDispatch() = runBlocking {
        val ctx = flow.context(job)!!
        val a = flow.requestBalanceOnAccount(job, "short of cash")
        assertEquals("pending", a.status)
        assertEquals("Waiting for dispatch to approve the balance on account.", CodGate.blockingReason(ctx, null, a))
        val decided = flow.balanceApproval(job)!!
        assertEquals("approved", decided.status)
        assertNull(CodGate.blockingReason(ctx, null, decided))
        // An approval only covers the balance it was for.
        assertFalse(CodGate.balanceOnAccount(ctx.copy(amountDue = ctx.amountDue + 5), decided))
    }

    @Test
    fun aReasonIsRequired() = runBlocking {
        try {
            flow.requestBalanceOnAccount(job, " ")
            fail("a reason is required")
        } catch (e: IllegalStateException) {
            assertTrue(e.message!!.contains("why"))
        }
    }
}

private object DeliveryCardAttemptFixtures {
    val unknown = co.zw.nissangtr.delivery.rpc.DeliveryCardAttempt("a", "unknown", 1.0, "USD", null, null, emptyMap(), null, null, null, null)
}
