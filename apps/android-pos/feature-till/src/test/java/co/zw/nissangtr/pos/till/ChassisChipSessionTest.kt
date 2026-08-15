package co.zw.nissangtr.pos.till

import co.zw.nissangtr.pos.api.ChassisShortcut
import co.zw.nissangtr.pos.api.FakePosClient
import co.zw.nissangtr.pos.lookup.FinderMode
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChassisChipSessionTest {
    @Test
    fun chipLatchesAndLoadsShopStock() = runBlocking {
        val client = FakePosClient()
        val session = TillSession(client, TillLayoutMode.Expanded)
        session.setChassisShortcuts(client.listChassisShortcuts())
        assertTrue(session.state.chassisShortcuts.size >= 2)

        val y62 = ChassisShortcut("Y62", label = "Y62")
        session.latchChassisChip(y62)
        assertEquals("Y62", session.state.latch?.chassisCode)
        assertEquals(FinderMode.SHOP_STOCK, session.state.finderMode)
        assertTrue(session.state.tiles.any { it.chassisCodes.contains("Y62") })
    }
}
