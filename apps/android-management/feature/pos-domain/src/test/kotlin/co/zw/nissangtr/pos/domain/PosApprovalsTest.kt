package co.zw.nissangtr.pos.domain

import co.zw.nissangtr.pos.domain.model.ApprovalTarget
import co.zw.nissangtr.pos.domain.model.CurrencyCode
import co.zw.nissangtr.pos.domain.model.Money
import co.zw.nissangtr.pos.domain.model.WaitingApproval
import co.zw.nissangtr.pos.domain.state.ApprovalsEffect
import co.zw.nissangtr.pos.domain.state.ApprovalsEvent
import co.zw.nissangtr.pos.domain.state.ApprovalsIntent
import co.zw.nissangtr.pos.domain.state.PosState
import co.zw.nissangtr.pos.domain.state.reduce
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PosApprovalsTest {
    private fun item(kind: String, urgent: Boolean, since: String) =
        WaitingApproval(kind, "r-$kind", kind, null, urgent, since, Money.ofMajor(5.0, CurrencyCode("USD")))

    @Test fun openingLoadsAndShowsTheList() {
        val r = reduce(PosState(online = true), ApprovalsIntent.Open)
        assertTrue(r.state.approvalsOpen)
        assertEquals(listOf(ApprovalsEffect.Load), r.effects)
        assertFalse(reduce(r.state, ApprovalsIntent.Close).state.approvalsOpen)
    }

    @Test fun urgentFirstThenLongestWaiting() {
        val items = listOf(item("return", false, "2026-10-08T07:00:00Z"), item("till_variance", false, "2026-10-08T06:00:00Z"), item("card_unresolved", true, "2026-10-08T08:00:00Z"))
        val s = reduce(PosState(), ApprovalsEvent.Loaded(items)).state
        assertEquals(listOf("card_unresolved", "till_variance", "return"), s.approvals.map { it.kind })
    }

    @Test fun offlineRefreshKeepsTheLastListWithoutComplaining() {
        val start = PosState(online = false, approvals = listOf(item("return", false, "2026-10-08T07:00:00Z")))
        val r = reduce(start, ApprovalsIntent.Load)
        assertEquals(start.approvals, r.state.approvals)
        assertTrue(r.effects.isEmpty())
        assertEquals(start.feedback, r.state.feedback)
    }

    @Test fun eachKindOpensWhereTheTabletDecidesIt() {
        assertEquals(ApprovalTarget.Till, item("till_variance", false, "").target)
        assertEquals(ApprovalTarget.Recovery, item("card_unresolved", true, "").target)
        assertEquals(ApprovalTarget.Orders, item("transfer_send", true, "").target)
        assertEquals(ApprovalTarget.Web, item("requisition", false, "").target)
    }
}
