package co.zw.nissangtr.pos.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MoneyCentsAndRpcNamesTest {

    @Test
    fun majorToCents_halfUp() {
        assertEquals(21200L, MoneyCents.majorToCents(212.00))
        assertEquals(21201L, MoneyCents.majorToCents(212.005))
        assertEquals("212.00", MoneyCents.centsToMajorString(21200L))
    }

    @Test
    fun liveClient_wiresCheckoutAndParkRpcs() {
        val live = LivePosClient()
        assertTrue(live.wiredRpcs.contains(PosRpcNames.CHECKOUT_POS_CART_WITH_TENDERS))
        assertTrue(live.wiredRpcs.contains(PosRpcNames.PARK_POS_CART))
        assertTrue(live.wiredRpcs.contains(PosRpcNames.RESUME_POS_CART))
        assertTrue(live.wiredRpcs.contains(PosRpcNames.VOID_POS_CART))
        assertTrue(live.wiredRpcs.contains(PosRpcNames.APPLY_POS_CART_DISCOUNT))
        assertTrue(live.wiredRpcs.contains(PosRpcNames.CREATE_ECOCASH_INTENT))
    }
}
