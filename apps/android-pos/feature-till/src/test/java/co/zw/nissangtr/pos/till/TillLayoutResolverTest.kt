package co.zw.nissangtr.pos.till

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TillLayoutResolverTest {

    @Test
    fun phoneWidth_isCompact() {
        assertFalse(TillLayoutResolver.isExpanded(412))
        assertEquals(TillLayoutMode.Compact, TillLayoutResolver.resolve(412))
    }

    @Test
    fun tabletExpandedWidth_isExpanded() {
        assertTrue(TillLayoutResolver.isExpanded(1280))
        assertEquals(TillLayoutMode.Expanded, TillLayoutResolver.resolve(1280))
    }

    @Test
    fun threshold_840dp() {
        assertFalse(TillLayoutResolver.isExpanded(839))
        assertTrue(TillLayoutResolver.isExpanded(840))
        assertEquals(TillLayoutMode.Compact, TillLayoutResolver.resolve(839))
        assertEquals(TillLayoutMode.Expanded, TillLayoutResolver.resolve(840))
    }
}
