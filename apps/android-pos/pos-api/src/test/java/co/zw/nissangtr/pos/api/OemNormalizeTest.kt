package co.zw.nissangtr.pos.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OemNormalizeTest {
    @Test
    fun trimsAndUppercases() {
        assertEquals("40206-JF00A", OemNormalize.normalize(" 40206-jf00a "))
    }

    @Test
    fun collapsesWhitespace() {
        assertEquals("40206JF00A", OemNormalize.normalize("40206 JF00A"))
    }
}

class LivePosClientWireTest {
    @Test
    fun wiresRequiredRpcNames() {
        val live = LivePosClient()
        assertTrue(live.wiredRpcs.contains(PosRpcNames.SEARCH_CATALOG))
        assertTrue(live.wiredRpcs.contains(PosRpcNames.LIST_POS_TILL_ITEMS))
        assertTrue(live.wiredRpcs.contains(PosRpcNames.LIST_CATALOG_MAKERS))
        assertTrue(live.wiredRpcs.contains(PosRpcNames.CREATE_POS_QUOTATION_FROM_CART))
    }
}
