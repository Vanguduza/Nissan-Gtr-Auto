package co.zw.nissangtr.pos.domain

import co.zw.nissangtr.pos.domain.error.PosError
import co.zw.nissangtr.pos.domain.model.ApprovalPolicy
import co.zw.nissangtr.pos.domain.model.ApprovalRequest
import co.zw.nissangtr.pos.domain.model.CartLine
import co.zw.nissangtr.pos.domain.model.CartProjection
import co.zw.nissangtr.pos.domain.model.CurrencyCode
import co.zw.nissangtr.pos.domain.model.ManagerCredentials
import co.zw.nissangtr.pos.domain.model.Money
import co.zw.nissangtr.pos.domain.model.ReasonCode
import co.zw.nissangtr.pos.domain.state.GovernanceEffect
import co.zw.nissangtr.pos.domain.state.GovernanceEvent
import co.zw.nissangtr.pos.domain.state.GovernanceIntent
import co.zw.nissangtr.pos.domain.state.PosFeedback
import co.zw.nissangtr.pos.domain.state.PosSaleEffect
import co.zw.nissangtr.pos.domain.state.PosSaleIntent
import co.zw.nissangtr.pos.domain.state.PosState
import co.zw.nissangtr.pos.domain.state.reduce
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PosGovernanceTest {
    private fun usd(v: Double) = Money.ofMajor(v, CurrencyCode.USD)
    private val line = CartLine("l1", "si", "OEM-1", "Brake pads", 1.0, usd(80.0), usd(80.0), false, null)
    private val cart = CartProjection("cart-1", CurrencyCode.USD, listOf(line), usd(80.0), usd(0.0), usd(80.0))
    private val selling = PosState(cart = cart)
    private val match = ReasonCode("price_match", "Price match", false)
    private val pricing = ReasonCode("pricing_error", "Pricing error", true)

    private fun failure(s: PosState) = (s.feedback as PosFeedback.Failure).error

    @Test
    fun `a governed request loads its reasons and asks the policy with the action's value`() {
        val r = reduce(selling, PosSaleIntent.RequestApproval(ApprovalRequest.Discount(3.0)))
        assertNull(r.state.approvalReasons)
        assertTrue(r.state.approvalNeedsManager)
        assertEquals(GovernanceEffect.LoadContext(ApprovalRequest.Discount(3.0), "discount_percent", 3.0), r.effects.single())

        // Override from 80 to 60 is a 25% change.
        val o = reduce(selling, PosSaleIntent.RequestApproval(ApprovalRequest.PriceOverride("l1", 60.0)))
        assertEquals(25.0, (o.effects.single() as GovernanceEffect.LoadContext).value, 0.0001)
    }

    @Test
    fun `within policy the cashier confirms with a reason and no manager`() {
        val asked = reduce(selling, PosSaleIntent.RequestApproval(ApprovalRequest.Discount(3.0))).state
        val ready = reduce(asked, GovernanceEvent.ContextLoaded(ApprovalRequest.Discount(3.0), listOf(match), needsManager = false)).state
        assertFalse(ready.approvalNeedsManager)

        // No reason: refused before any call.
        val noReason = reduce(ready, PosSaleIntent.SubmitApproval(null))
        assertTrue(noReason.effects.isEmpty())
        assertEquals(PosError.Input("reason", "required"), failure(noReason.state))

        val ok = reduce(ready, PosSaleIntent.SubmitApproval(null, match, " "))
        val approve = ok.effects.single() as PosSaleEffect.Approve
        assertNull(approve.credentials)
        assertEquals(match, approve.reason)
        assertNull(approve.notes)
    }

    @Test
    fun `above policy a manager must sign in, and a reason that needs notes needs them`() {
        val asked = reduce(selling, PosSaleIntent.RequestApproval(ApprovalRequest.VoidSale)).state
        val ready = reduce(asked, GovernanceEvent.ContextLoaded(ApprovalRequest.VoidSale, listOf(pricing), needsManager = true)).state
        assertTrue(reduce(ready, PosSaleIntent.SubmitApproval(null, pricing, "shelf price")).effects.isEmpty())
        assertTrue(reduce(ready, PosSaleIntent.SubmitApproval(ManagerCredentials("mgr", "pw", null), pricing, null)).effects.isEmpty())
        val ok = reduce(ready, PosSaleIntent.SubmitApproval(ManagerCredentials("mgr", "pw", null), pricing, "shelf price"))
        val approve = ok.effects.single() as PosSaleEffect.Approve
        assertEquals("mgr", approve.credentials?.identifier)
        assertEquals("shelf price", approve.notes)
    }

    @Test
    fun `a late policy answer for a closed dialog is ignored`() {
        val asked = reduce(selling, PosSaleIntent.RequestApproval(ApprovalRequest.Discount(3.0))).state
        val cancelled = reduce(asked, PosSaleIntent.CancelApproval).state
        val late = reduce(cancelled, GovernanceEvent.ContextLoaded(ApprovalRequest.Discount(3.0), listOf(match), needsManager = false)).state
        assertNull(late.approvalReasons)
        assertTrue(late.approvalNeedsManager)
    }

    @Test
    fun `drawer approvals always need a manager and carry no reason list`() {
        val asked = reduce(selling, PosSaleIntent.RequestApproval(ApprovalRequest.VoidSale)).state
        assertTrue(asked.approvalNeedsManager)
        val tillOpen = reduce(
            PosState(till = co.zw.nissangtr.pos.domain.state.TillPanel(enforced = true, loaded = true, session = co.zw.nissangtr.pos.domain.model.TillSession(
                "t1", "w1", CurrencyCode.USD, "u1", usd(50.0), co.zw.nissangtr.pos.domain.model.TillStatus.Open, null, null, null, null, "", null,
            ))),
            co.zw.nissangtr.pos.domain.state.TillIntent.CashOut(usd(10.0), ReasonCode("petty_cash", "Petty cash", false), null),
        )
        assertTrue(tillOpen.effects.isEmpty())
        assertEquals(emptyList<ReasonCode>(), tillOpen.state.approvalReasons)
        assertTrue(tillOpen.state.approvalNeedsManager)
    }

    @Test
    fun `policies load and save online only`() {
        assertEquals(GovernanceEffect.LoadPolicies, reduce(PosState(), GovernanceIntent.LoadPolicies).effects.single())
        assertTrue(reduce(PosState(online = false), GovernanceIntent.LoadPolicies).effects.isEmpty())
        val p = ApprovalPolicy("discount_percent", 5.0, alwaysRequireManager = false, reasonRequired = true)
        assertEquals(GovernanceEffect.SavePolicy(p), reduce(PosState(), GovernanceIntent.SavePolicy(p)).effects.single())
        assertTrue(reduce(PosState(), GovernanceIntent.SavePolicy(p.copy(thresholdValue = -1.0))).effects.isEmpty())
        val saved = reduce(PosState(), GovernanceEvent.PolicySaved)
        assertEquals(GovernanceEffect.LoadPolicies, saved.effects.single())
    }
}
