package co.zw.nissangtr.pos.pay

import co.zw.nissangtr.pos.api.MoneyCents
import co.zw.nissangtr.pos.api.TenderMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Integer-cents only — no Double in allocator assertions (§16.5).
 *
 * Equal-split policy (documented): floor(due/n) on each line; **remainder on last**
 * so 1000¢ / 3 → 333 + 333 + 334 (not 334+333+333).
 */
class TenderAllocatorTest {

    @Test
    fun equalSplit_1000_over_3_remainderOnLast() {
        val drafts = TenderAllocator.splitEqually(
            dueCents = 1000L,
            modes = listOf(TenderMode.CASH, TenderMode.BANK, TenderMode.ECOCASH),
        )
        assertEquals(listOf(333L, 333L, 334L), drafts.map { it.tenderedCents })
        val snap = TenderAllocator.allocate(1000L, drafts)
        assertEquals(0L, snap.remainingCents)
        assertEquals(1000L, snap.appliedSumCents)
        assertTrue(snap.confirmEnabled)
    }

    @Test
    fun cashTenderedOverDue_appliedEqualsDue_changeIsExcess_rpcAppliedOnly() {
        val due = 21200L
        val drafts = listOf(
            TenderDraft(mode = TenderMode.CASH, tenderedCents = 25000L),
        )
        val snap = TenderAllocator.allocate(due, drafts)
        assertEquals(due, snap.lines.single().appliedCents)
        assertEquals(3800L, snap.changeCents)
        assertEquals(0L, snap.remainingCents)
        assertTrue(snap.confirmEnabled)

        val rpc = TenderAllocator.toRpcLines(snap, "USD")
        assertEquals(1, rpc.size)
        assertEquals("cash", rpc.single().tender)
        assertEquals(MoneyCents.centsToMajorString(due), rpc.single().amount)
        // Change never posted — applied only.
        assertFalse(rpc.any { MoneyCents.majorStringToCents(it.amount) > due })
    }

    @Test
    fun remainingNonZero_confirmDisabled() {
        val drafts = listOf(
            TenderDraft(mode = TenderMode.CASH, tenderedCents = 500L),
            TenderDraft(mode = TenderMode.BANK, tenderedCents = 400L),
        )
        val snap = TenderAllocator.allocate(dueCents = 1000L, drafts = drafts)
        assertEquals(100L, snap.remainingCents)
        assertFalse(snap.confirmEnabled)
    }

    @Test
    fun fillRest_setsNextToRemaining() {
        val existing = listOf(TenderDraft(TenderMode.CASH, 600L))
        val fill = TenderAllocator.fillRest(1000L, existing, TenderMode.BANK)
        assertEquals(400L, fill.tenderedCents)
        val snap = TenderAllocator.allocate(1000L, existing + fill)
        assertEquals(0L, snap.remainingCents)
        assertTrue(snap.confirmEnabled)
    }
}
