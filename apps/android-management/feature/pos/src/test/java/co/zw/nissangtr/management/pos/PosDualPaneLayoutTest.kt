package co.zw.nissangtr.management.pos

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PosDualPaneLayoutTest {
    @Test
    fun useTwoPane_atBreakpoint() {
        assertTrue(PosDualPaneLayout.useTwoPane(700f))
        assertTrue(PosDualPaneLayout.useTwoPane(1280f))
        assertFalse(PosDualPaneLayout.useTwoPane(699f))
        assertFalse(PosDualPaneLayout.useTwoPane(390f))
    }
}
